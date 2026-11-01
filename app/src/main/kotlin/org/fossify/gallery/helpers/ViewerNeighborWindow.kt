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
            items: List<ThumbnailItem>,
            selectedPath: String,
            radius: Int = NEIGHBOR_RADIUS
        ): List<Medium>? {
            if (radius < 0 || selectedPath.isEmpty()) return null
            val windowRadius = radius.coerceAtMost(NEIGHBOR_RADIUS)
            // Adapter.media is the committed display snapshot. Locate the selected
            // position without allocating a set proportional to the whole gallery.
            val selectedIndex = items.indexOfFirst { it is Medium && it.path == selectedPath }
            if (selectedIndex < 0) return null
            val before = ArrayList<Medium>(windowRadius)
            val after = ArrayList<Medium>(windowRadius + 1)
            val seen = HashSet<String>(MAX_ITEMS)
            val selected = items[selectedIndex] as Medium
            seen.add(selected.path)
            after.add(selected)
            var index = selectedIndex - 1
            while (index >= 0 && before.size < windowRadius) {
                val medium = items[index--] as? Medium ?: continue
                if (seen.add(medium.path)) before.add(medium)
            }
            before.reverse()
            index = selectedIndex + 1
            while (index < items.size && after.size <= windowRadius) {
                val medium = items[index++] as? Medium ?: continue
                if (seen.add(medium.path)) after.add(medium)
            }
            return before + after
        }
    }
}
