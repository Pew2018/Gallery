package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/**
 * Resolves a popup position against the adapter's current data. A section represents the first
 * following medium; a trailing/empty section falls back to the nearest preceding medium.
 */
internal object FastScrollerBubble {
    fun findMediaIndex(items: List<ThumbnailItem>, position: Int): Int? {
        if (position !in items.indices) return null
        if (items[position] is Medium) return position

        for (index in position + 1 until items.size) {
            if (items[index] is Medium) return index
        }
        for (index in position - 1 downTo 0) {
            if (items[index] is Medium) return index
        }
        return null
    }

    fun dateOrFallback(timestamp: Long, fallback: String, format: () -> String): String {
        if (timestamp == 0L) return fallback
        return runCatching(format).getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun displayName(name: String, path: String, unknown: String): String =
        name.takeIf { it.isNotBlank() }
            ?: path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: unknown
}
