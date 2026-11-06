package org.fossify.gallery.helpers

/** IME overlap relative to the navigation inset already applied to the FAB margin. */
object PhotoProfileFabImePosition {
    fun translationY(imeVisible: Boolean, imeBottom: Int, systemBarsBottom: Int): Int {
        if (!imeVisible) return 0
        return -(imeBottom - systemBarsBottom).coerceAtLeast(0)
    }
}
