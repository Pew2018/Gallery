package org.fossify.gallery.helpers

/** Stable IDs survive removal of retired tabs and preserve the user's relative order. */
object NavigationOrder {
    val defaults = listOf("ALL", "FAVORITE_FOLDERS")
    fun normalize(saved: List<String>): List<String> =
        (saved.filter { it in defaults }.distinct() + defaults).distinct()
}
