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
    @Suppress("DEPRECATION")
    fun applyViewer(activity: Activity, colors: OxygenThemeTokens, progress: Float, statusVisible: Boolean) {
        val host = activity.window.decorView as? FrameLayout ?: return
        val status = ColorUtils.blendARGB(colors.statusBarBackground,
            activity.getColor(R.color.viewer_status_surface), progress)
        val nav = ColorUtils.blendARGB(colors.navigationBarBackground, Color.BLACK, progress)
        val bars = WindowCompat.getInsetsController(activity.window, host)
        bars.isAppearanceLightStatusBars = useDarkIcons(status)
        bars.isAppearanceLightNavigationBars = useDarkIcons(nav)

        if (Build.VERSION.SDK_INT < 35) {
            activity.window.statusBarColor = status
            activity.window.navigationBarColor = nav
        } else {
            statusUnderlay(activity, host, stableInsets = true).apply {
                setBackgroundColor(status)
                visibility = if (statusVisible) View.VISIBLE else View.GONE
            }
        }
    }

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
