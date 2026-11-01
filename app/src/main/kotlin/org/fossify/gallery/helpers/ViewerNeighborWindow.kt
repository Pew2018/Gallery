package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

internal enum class ViewerSourceType { FOLDER, FAVORITES, PHOTO_PROFILE }

/** Candidate metadata only. Membership here never grants permission to display a medium. */
internal data class ViewerNeighborWindow(
    val selectedPath: String,
    val sourceType: ViewerSourceType,
    val sourceKey: String,
    val items: List<Medium>,
    val isCompleteSource: Boolean
) {
    init {
        require(items.size <= MAX_ITEMS)
        require(items.distinctBy { it.path }.size == items.size)
    }

    companion object {
        const val NEIGHBOR_RADIUS = 2
        const val MAX_ITEMS = NEIGHBOR_RADIUS * 2 + 1

        /** Walks the displayed order once while retaining only the selected item and nearby media. */
        fun captureAround(
            items: Iterable<ThumbnailItem>,
            selectedPath: String,
            radius: Int = NEIGHBOR_RADIUS
        ): List<Medium>? {
            if (radius < 0 || selectedPath.isEmpty()) return null
            val before = ArrayDeque<Medium>(radius)
            val after = ArrayList<Medium>(radius)
            val seen = HashSet<String>()
            var selectedFound = false
            for (item in items) {
                val medium = item as? Medium ?: continue
                if (!seen.add(medium.path)) continue
                if (!selectedFound) {
                    if (medium.path == selectedPath) {
                        selectedFound = true
                        after.add(medium)
                    } else if (radius > 0) {
                        if (before.size == radius) before.removeFirst()
                        before.addLast(medium)
                    }
                } else if (after.size <= radius) {
                    after.add(medium)
                    if (after.size == radius + 1) break
                }
            }
            if (!selectedFound) return null
            return (before.toList() + after).map { it.copy() }
        }
    }
}
