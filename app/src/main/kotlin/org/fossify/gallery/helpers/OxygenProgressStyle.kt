package org.fossify.gallery.helpers

import android.content.Context
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Shared 2017-style Material linear loading indicator for folder and media screens.
 * The 3dp strip uses the page surface, with an accent-colored moving segment and
 * an independent neutral track; the colored toolbar remains untouched.
 */
object OxygenProgressStyle {
    private const val TRACK_THICKNESS_DP = 3

    fun thicknessPx(context: Context): Int =
        (TRACK_THICKNESS_DP * context.resources.displayMetrics.density + 0.5f).toInt()

    fun apply(context: Context, config: Config, indicator: LinearProgressIndicator) {
        val colors = OxygenThemeEngine.snapshot(context, config)
        val palette = OxygenThemeColors.progressPalette(colors)
        indicator.setBackgroundColor(palette.surface)
        indicator.setIndicatorColor(palette.indicator)
        indicator.setTrackColor(palette.track)
        indicator.trackThickness = thicknessPx(context)
        indicator.trackCornerRadius = 0
    }
}
