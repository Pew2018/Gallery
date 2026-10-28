package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.extensions.config

/** Ordinary pages opt in; fullscreen media retains its own system UI policy. */
object OxygenSystemBars {
    private const val STATUS_UNDERLAY = "oxygen_classic_status_underlay"

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

        val underlay = host.findViewWithTag<View>(STATUS_UNDERLAY) ?: View(activity).also { view ->
            view.tag = STATUS_UNDERLAY
            view.isClickable = false
            view.isFocusable = false
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            host.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
            ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
                resize(target, insets)
                insets // Never consume or replace the page/IME/gesture insets.
            }
        }
        underlay.setBackgroundColor(colors.statusBarBackground)
        ViewCompat.getRootWindowInsets(host)?.let { resize(underlay, it) }
        underlay.bringToFront()
        ViewCompat.requestApplyInsets(underlay)
    }

    private fun resize(view: View, insets: WindowInsetsCompat) {
        val top = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        ).top
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.height != top) {
            params.height = top
            view.layoutParams = params
        }
    }
}
