package org.fossify.gallery.helpers

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/** User accent stays unchanged in settings; readable text uses its contrast-safe variant. */
object OxygenClassicPalette {
    fun readableAccent(accent: Int, surface: Int): Int {
        val background = ColorUtils.setAlphaComponent(surface, 255)
        val target = if (ColorUtils.calculateLuminance(background) >= 0.5)
            Color.BLACK else Color.WHITE
        var color = ColorUtils.setAlphaComponent(accent, 255)
        repeat(16) {
            if (ColorUtils.calculateContrast(color, background) >= 4.5) return color
            color = ColorUtils.blendARGB(color, target, 0.16f)
        }
        return color
    }
}
