package org.fossify.gallery.helpers

import android.content.Context
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Shared classic Material progress style for folder and media screens.
 * The toolbar token, not a second black/white heuristic, owns foreground color.
 */
object OxygenProgressStyle {
    private const val TRACK_THICKNESS_DP = 3

    fun thicknessPx(context: Context): Int =
        (TRACK_THICKNESS_DP * context.resources.displayMetrics.density + 0.5f).toInt()

    fun apply(context: Context, config: Config, indicator: LinearProgressIndicator) {
        val colors = OxygenThemeEngine.snapshot(context, config)
        indicator.setIndicatorColor(colors.toolbarForeground)
        indicator.setTrackColor(OxygenThemeColors.progressTrackColor(colors.toolbarForeground))
        indicator.trackThickness = thicknessPx(context)
        indicator.trackCornerRadius = 0
    }
}
