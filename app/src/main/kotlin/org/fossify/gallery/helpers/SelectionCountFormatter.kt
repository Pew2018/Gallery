package org.fossify.gallery.helpers

/** Commons 7.1.3 supplies "selected / total"; expose only the selected count. */
internal object SelectionCountFormatter {
    private val commonsCount = Regex("^\\s*(\\d+)\\s*/\\s*\\d+\\s*$")
    fun selectedCount(title: CharSequence?): Int? =
        title?.toString()?.let { commonsCount.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
}
