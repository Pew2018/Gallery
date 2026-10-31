package org.fossify.gallery.helpers

/** Stable section IDs with safe migration from the retired folder-only identifier. */
object NavigationOrder {
    val defaults = listOf("ALL", "FAVORITES", "PHOTOS")
    fun normalize(saved: List<String>): List<String> {
        val migrated = saved.map { if (it == "FAVORITE_FOLDERS") "FAVORITES" else it }
        return (migrated.filter { it in defaults }.distinct() + defaults).distinct()
    }
}
