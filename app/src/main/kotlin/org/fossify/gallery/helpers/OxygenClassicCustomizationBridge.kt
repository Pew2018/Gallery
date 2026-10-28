package org.fossify.gallery.helpers

import android.app.Activity
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R

/**
 * A one-shot lifecycle adapter for Commons' non-subclassable appearance screen.
 * No focus polling, per-frame updates or changes to preference/save callbacks.
 */
object OxygenClassicCustomizationBridge {
    fun apply(activity: Activity) {
        if (activity !is CustomizationActivity) return
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            val toolbar = activity.findViewById<MaterialToolbar>(
                org.fossify.commons.R.id.customization_toolbar
            ) ?: return@post
            val surface = activity.getProperBackgroundColor()
            val text = activity.getProperTextColor()
            val light = ColorUtils.calculateLuminance(surface) >= 0.5
            activity.theme.applyStyle(
                if (light) R.style.OxygenClassicActivityOverlay_Light
                else R.style.OxygenClassicActivityOverlay_Dark,
                true
            )
            toolbar.setBackgroundColor(surface)
            toolbar.setTitleTextColor(text)
            toolbar.navigationIcon?.mutate()?.setTint(text)
            toolbar.overflowIcon?.mutate()?.setTint(text)
            for (i in 0 until toolbar.menu.size()) {
                toolbar.menu.getItem(i).icon?.mutate()?.setTint(text)
            }
            toolbar.setPopupTheme(
                if (light) R.style.OxygenClassicPopupTheme_Light
                else R.style.OxygenClassicPopupTheme_Dark
            )
            activity.findViewById<View>(org.fossify.commons.R.id.app_bar)
                ?.setBackgroundColor(surface)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars = light
        }
    }
}
