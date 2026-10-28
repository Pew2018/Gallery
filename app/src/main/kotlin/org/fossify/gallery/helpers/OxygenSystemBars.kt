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

/**
 * Only ordinary classic pages opt in. The system still owns the actual
 * status icons, gesture navigation, cutout and predictive-back behavior.
 *
 * API 35+ enforces edge-to-edge: a non-interactive inset underlay paints
 * behind the real system status icons; statusBarColor alone is insufficient.
 */
object OxygenSystemBars {
    private const val STATUS_UNDERLAY = "oxygen_classic_status_underlay"

    fun apply(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val existing = content.findViewWithTag<View>(STATUS_UNDERLAY)
        if (!activity.config.oxygenThemeActivated) {
            if (existing != null) content.removeView(existing)
            return // Commons/Fossify manages the non-classic system appearance.
        }

        val color = OxygenThemeEngine.statusBarSurface(activity, activity.config)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = color
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = OxygenThemeEngine.statusBarLightIcons(activity, activity.config)

        val underlay = existing ?: View(activity).also { view ->
            view.tag = STATUS_UNDERLAY
            view.isClickable = false
            view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            content.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        }
        underlay.setBackgroundColor(color)
        ViewCompat.setOnApplyWindowInsetsListener(underlay) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val lp = view.layoutParams as FrameLayout.LayoutParams
            if (lp.height != top) {
                lp.height = top
                view.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(underlay)
    }
}
