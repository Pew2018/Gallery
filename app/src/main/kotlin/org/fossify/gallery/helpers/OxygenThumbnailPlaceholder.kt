package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor

/** Theme-aware solid placeholder; transparent Glide loading images expose this surface. */
object OxygenThumbnailPlaceholder {
    fun apply(context: Context, target: ImageView, corners: Int) {
        val light = ColorUtils.calculateLuminance(context.getProperBackgroundColor()) >= 0.5
        val shade = if (light) Color.rgb(235, 235, 235) else Color.rgb(57, 57, 57)
        val radius = when (corners) {
            ROUNDED_CORNERS_SMALL ->
                context.resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_small)
            ROUNDED_CORNERS_BIG ->
                context.resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_big)
            else -> 0f
        }
        target.background = GradientDrawable().apply {
            setColor(shade)
            cornerRadius = radius
        }
    }
}
