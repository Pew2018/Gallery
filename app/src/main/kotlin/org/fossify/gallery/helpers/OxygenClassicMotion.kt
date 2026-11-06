package org.fossify.gallery.helpers

import android.content.Context
import android.provider.Settings
import android.view.animation.PathInterpolator

/**
 * OxygenOS 7.1.1 Classic motion tokens. Android owns RippleDrawable's lifecycle;
 * other motion is brief, unscaled, and observes the global animator switch.
 */
object OxygenClassicMotion {
    // Viewer timings are application choices, not measured ROM resource values.
    const val VIEWER_CHROME_MS = 170L
    const val VIEWER_LOADING_DELAY_MS = 120L
    const val VIEWER_LOADING_MIN_VISIBLE_MS = 180L
    const val VIEWER_LOADING_FADE_MS = 140L

    const val CHECKBOX_MS = 140L
    const val TAB_MS = 150L
    const val SWITCH_MS = 180L
    const val TOGGLE_CHECKBOX_MS = 200L
    const val LIST_MOVE_MS = 170L
    const val SETTINGS_ENTER_MS = 150L
    const val SETTINGS_DISTANCE_DP = 8

    fun enabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
    }.getOrDefault(true)

    fun easing() = PathInterpolator(0.4f, 0f, 0.2f, 1f)
}
