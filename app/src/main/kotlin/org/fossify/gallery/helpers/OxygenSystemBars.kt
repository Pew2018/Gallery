package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * Owns Gallery's status surfaces and system bar icon appearance.
 *
 * One window can be painted by two different roles: the ordinary page path
 * ([apply], driven by the theme tokens) and the Viewer path ([applyViewer], driven by
 * the entry/return clock). They used to be interchangeable writers on the same
 * DecorView, which is how a page write landed between two Viewer frames and made the
 * status icons flip `LIGHT -> DARK -> LIGHT` inside a single monotonic entry.
 *
 * [WindowBars] records which role owns the window and what the Viewer last asked for,
 * so [apply] on a Viewer-owned window replays the Viewer state instead of repainting
 * the page. Icon polarity is decided by [IconPolarityLatch] per window, never by
 * reading back a flag another writer may have changed.
 */
object OxygenSystemBars {
    internal const val STATUS_UNDERLAY = "oxygen_classic_status_underlay"
    internal const val NAV_UNDERLAY = "oxygen_classic_nav_underlay"

    private enum class Owner { PAGE, VIEWER }

    private class WindowBars {
        var owner = Owner.PAGE
        var viewerProgress = 1f
        var viewerVisible = true
        var viewerTokens: OxygenThemeTokens? = null
        val statusPolarity = IconPolarityLatch()
        val navigationPolarity = IconPolarityLatch()
        var lastStatusSurface = Int.MIN_VALUE
        var lastNavigationSurface = Int.MIN_VALUE
    }

    private val windows = java.util.WeakHashMap<View, WindowBars>()

    private fun bars(host: View) = windows.getOrPut(host) { WindowBars() }

    fun apply(activity: Activity) {
        // Decor coordinates start at the physical window edge even if content is inset.
        val host = activity.window.decorView as? FrameLayout ?: return
        val state = bars(host)
        if (state.owner == Owner.VIEWER) {
            // A page-level write (onPostResume, selection mode, toolbar refresh) must not
            // repaint a window the Viewer is currently driving. Replaying the recorded
            // Viewer state keeps the surface and the icons on the Viewer's own curve.
            val tokens = state.viewerTokens ?: OxygenThemeEngine.snapshot(activity, activity.config)
            applyViewer(activity, tokens, state.viewerProgress, state.viewerVisible)
            return
        }
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = colors.statusBarBackground
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = colors.navigationBarBackground
        }
        val bars = WindowCompat.getInsetsController(activity.window, host)
        val darkStatus = !colors.lightStatusBarIcons
        state.statusPolarity.adopt(darkStatus)
        state.navigationPolarity.adopt(colors.darkNavigationBarIcons)
        state.lastStatusSurface = colors.statusBarBackground
        state.lastNavigationSurface = colors.navigationBarBackground
        bars.isAppearanceLightStatusBars = darkStatus
        bars.isAppearanceLightNavigationBars = colors.darkNavigationBarIcons

        statusUnderlay(activity, host).apply {
            setBackgroundColor(colors.statusBarBackground)
            ViewCompat.getRootWindowInsets(host)?.let { insets -> resize(this, insets, stableInsets = false) }
            bringToFront()
            ViewCompat.requestApplyInsets(this)
        }
    }

    /**
     * Paints the Viewer surfaces for a blend [progress] (0 = page colours, 1 = viewer
     * surfaces) and returns true only when a window property actually changed: the entry
     * and return clocks call this on every frame and must not rewrite identical values.
     */
    @Suppress("DEPRECATION")
    fun applyViewer(activity: Activity, colors: OxygenThemeTokens, progress: Float, statusVisible: Boolean): Boolean {
        val host = activity.window.decorView as? FrameLayout ?: return false
        val state = bars(host)
        state.owner = Owner.VIEWER
        state.viewerProgress = progress
        state.viewerVisible = statusVisible
        state.viewerTokens = colors
        val status = blendOpaqueSurface(colors.statusBarBackground,
            activity.getColor(R.color.viewer_status_surface), progress)
        val nav = blendOpaqueSurface(colors.navigationBarBackground, Color.BLACK, progress)
        val bars = WindowCompat.getInsetsController(activity.window, host)
        var changed = false

        if (state.lastStatusSurface != status) {
            state.lastStatusSurface = status
            val dark = state.statusPolarity.decide(status, SystemClock.uptimeMillis())
            if (bars.isAppearanceLightStatusBars != dark) {
                bars.isAppearanceLightStatusBars = dark
                changed = true
            }
        }
        if (state.lastNavigationSurface != nav) {
            state.lastNavigationSurface = nav
            val dark = state.navigationPolarity.decide(nav, SystemClock.uptimeMillis())
            if (bars.isAppearanceLightNavigationBars != dark) {
                bars.isAppearanceLightNavigationBars = dark
                changed = true
            }
        }

        if (Build.VERSION.SDK_INT < 35) {
            if (activity.window.statusBarColor != status) {
                activity.window.statusBarColor = status
                changed = true
            }
            if (activity.window.navigationBarColor != nav) {
                activity.window.navigationBarColor = nav
                changed = true
            }
        } else {
            val underlay = statusUnderlay(activity, host, stableInsets = true)
            if ((underlay.background as? android.graphics.drawable.ColorDrawable)?.color != status) {
                underlay.setBackgroundColor(status)
                changed = true
            }
            val visibility = if (statusVisible) View.VISIBLE else View.GONE
            if (underlay.visibility != visibility) {
                underlay.visibility = visibility
                changed = true
            }
            // Enforced edge-to-edge (API 35+) makes navigationBarColor a no-op: the bar
            // area is transparent and shows whatever the window paints behind it. The
            // Viewer blends its navigation surface to black on the same clock, so the
            // band has to exist as a view or the icons would be chosen for a colour the
            // window never draws.
            val navUnderlay = navigationUnderlay(activity, host)
            if ((navUnderlay.background as? android.graphics.drawable.ColorDrawable)?.color != nav) {
                navUnderlay.setBackgroundColor(nav)
                changed = true
            }
            if (navUnderlay.visibility != visibility) {
                navUnderlay.visibility = visibility
                changed = true
            }
        }
        return changed
    }

    /** Drops a window's recorded ownership, e.g. when a Viewer window finishes. */
    fun forget(activity: Activity) {
        (activity.window.decorView as? FrameLayout)?.let { windows.remove(it) }
    }

    // Float interpolation can truncate 255 to 254 even when both endpoints are opaque.
    private fun blendOpaqueSurface(start: Int, end: Int, progress: Float) =
        OxygenThemeColors.opaque(ColorUtils.blendARGB(start, end, progress))

    private fun statusUnderlay(activity: Activity, host: FrameLayout, stableInsets: Boolean = false): View {
        host.findViewWithTag<View>(STATUS_UNDERLAY)?.let { return it }
        return View(activity).apply {
            tag = STATUS_UNDERLAY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host.addView(this, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
            ViewCompat.setOnApplyWindowInsetsListener(this) { target, insets ->
                resize(target, insets, stableInsets)
                insets
            }
            ViewCompat.getRootWindowInsets(host)?.let { resize(this, it, stableInsets) }
            ViewCompat.requestApplyInsets(this)
        }
    }

    private fun navigationUnderlay(activity: Activity, host: FrameLayout): View {
        host.findViewWithTag<View>(NAV_UNDERLAY)?.let { return it }
        return View(activity).apply {
            tag = NAV_UNDERLAY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host.addView(this, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
            ViewCompat.setOnApplyWindowInsetsListener(this) { target, insets ->
                resizeNavigation(target, insets)
                insets
            }
            ViewCompat.getRootWindowInsets(host)?.let { resizeNavigation(this, it) }
            ViewCompat.requestApplyInsets(this)
            bringToFront()
        }
    }

    private fun resize(view: View, insets: WindowInsetsCompat, stableInsets: Boolean) {
        val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        val top = if (stableInsets) insets.getInsetsIgnoringVisibility(types).top else insets.getInsets(types).top
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.height != top) {
            params.height = top
            view.layoutParams = params
        }
    }

    private fun resizeNavigation(view: View, insets: WindowInsetsCompat) {
        // Ignoring visibility keeps the strip height stable while the immersive
        // visibility is animated; the Viewer hides it explicitly instead.
        val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.height != bottom) {
            params.height = bottom
            view.layoutParams = params
        }
    }
}
