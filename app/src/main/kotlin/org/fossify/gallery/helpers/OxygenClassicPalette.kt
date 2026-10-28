package org.fossify.gallery.helpers

/** User seeds remain intact; labels use a deterministic WCAG-safe variant. */
object OxygenClassicPalette {
    fun readableAccent(accent: Int, surface: Int): Int =
        OxygenThemeColors.readableAccent(accent, surface)
}
