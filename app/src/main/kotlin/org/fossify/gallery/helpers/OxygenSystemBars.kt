package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/** Owns Gallery’s status surfaces and system bar icon appearance. */
object OxygenSystemBars {
    internal const val STATUS_UNDERLAY = "oxygen_classic_status_underlay"

    fun apply(activity: Activity) {
        // Decor coordinates start at the physical window edge even if content is inset.
        val host = activity.window.decorView as? FrameLayout ?: return
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = colors.statusBarBackground
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = colors.navigationBarBackground
        }
        val bars = WindowCompat.getInsetsController(activity.window, host)
        bars.isAppearanceLightStatusBars = !colors.lightStatusBarIcons
        bars.isAppearanceLightNavigationBars = colors.darkNavigationBarIcons

        statusUnderlay(activity, host).apply {
            setBackgroundColor(colors.statusBarBackground)
            ViewCompat.getRootWindowInsets(host)?.let { resize(this, it, stableInsets = false) }
            bringToFront()
            ViewCompat.requestApplyInsets(this)
        }
    }

    // Paint behind the status icons in decor coordinates on enforced edge-to-edge.
    // The inset height stays stable while immersive visibility is changing.
    // Returns true only when a window property actually changed: the Viewer return
    // drives this on every progress frame and must not rewrite identical values.
    @Suppress("DEPRECATION")
    fun applyViewer(activity: Activity, colors: OxygenThemeTokens, progress: Float, statusVisible: Boolean): Boolean {
        val host = activity.window.decorView as? FrameLayout ?: return false
        val status = blendOpaqueSurface(colors.statusBarBackground,
            activity.getColor(R.color.viewer_status_surface), progress)
        val nav = blendOpaqueSurface(colors.navigationBarBackground, Color.BLACK, progress)
        val lightStatus = useDarkIcons(status)
        val lightNavigation = useDarkIcons(nav)
        val bars = WindowCompat.getInsetsController(activity.window, host)
        var changed = false
        if (bars.isAppearanceLightStatusBars != lightStatus) {
            bars.isAppearanceLightStatusBars = lightStatus
            changed = true
        }
        if (bars.isAppearanceLightNavigationBars != lightNavigation) {
            bars.isAppearanceLightNavigationBars = lightNavigation
            changed = true
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
            val existing = host.findViewWithTag<View>(STATUS_UNDERLAY)
            val underlay = existing ?: statusUnderlay(activity, host, stableInsets = true).also { changed = true }
            if ((underlay.background as? android.graphics.drawable.ColorDrawable)?.color != status) {
                underlay.setBackgroundColor(status)
                changed = true
            }
            val visibility = if (statusVisible) View.VISIBLE else View.GONE
            if (underlay.visibility != visibility) {
                underlay.visibility = visibility
                changed = true
            }
        }
        return changed
    }

    // Float interpolation can truncate 255 to 254 even when both endpoints are opaque.
    private fun blendOpaqueSurface(start: Int, end: Int, progress: Float) =
        OxygenThemeColors.opaque(ColorUtils.blendARGB(start, end, progress))

    private fun useDarkIcons(background: Int) =
        ColorUtils.calculateContrast(Color.BLACK, background) >
            ColorUtils.calculateContrast(Color.WHITE, background)

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

    private fun resize(view: View, insets: WindowInsetsCompat, stableInsets: Boolean) {
        val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        val top = if (stableInsets) insets.getInsetsIgnoringVisibility(types).top else insets.getInsets(types).top
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.height != top) {
            params.height = top
            view.layoutParams = params
        }
    }
}
