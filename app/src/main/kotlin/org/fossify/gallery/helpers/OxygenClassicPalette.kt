package org.fossify.gallery.helpers

import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor

/** User seeds remain intact; labels use a deterministic WCAG-safe variant. */
object OxygenClassicPalette {
    fun readableAccent(accent: Int, surface: Int): Int =
        OxygenThemeColors.readableAccent(accent, surface)
}

/** Colors for the actual classic dialog surface, independent of custom page text. */
internal data class PhotoDialogColors(val surface: Int, val text: Int, val secondary: Int, val accent: Int, val error: Int)
internal fun photoDialogColors(activity: android.app.Activity): PhotoDialogColors {
    val light = androidx.core.graphics.ColorUtils.calculateLuminance(activity.getProperBackgroundColor()) >= .5
    val surface = if (light) android.graphics.Color.WHITE else android.graphics.Color.rgb(48, 48, 48)
    val text = if (light) android.graphics.Color.rgb(33, 33, 33) else android.graphics.Color.WHITE
    return PhotoDialogColors(surface, text,
        androidx.core.graphics.ColorUtils.blendARGB(surface, text, .70f),
        OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), surface),
        OxygenClassicPalette.readableAccent(android.graphics.Color.RED, surface))
}
