package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Preserves the gallery search contract: matching names first, stable source order within each rank. */
internal object ViewerSearchPolicy {
    const val EXTRA_QUERY = "org.fossify.gallery.VIEWER_SEARCH_QUERY"

    fun filter(media: List<Medium>, query: String?): List<Medium> {
        if (query.isNullOrBlank()) return media
        val matches = media.filter { it.name.contains(query, ignoreCase = true) }
        return matches.sortedBy { !it.name.startsWith(query, ignoreCase = true) }
    }
}
