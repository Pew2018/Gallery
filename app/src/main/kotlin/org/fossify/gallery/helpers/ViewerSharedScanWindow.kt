package org.fossify.gallery.helpers

/**
 * Partial results borrowed from the grid's folder scan.
 * They may extend the committed window around the anchor, and they stop at a
 * hole. They never grow with the number of files discovered so far.
 */
internal object ViewerSharedScanWindow {
    fun <T> extend(
        gridOrder: List<String>?,
        discovered: Map<String, T>,
        anchor: String,
    ): List<T> {
        if (gridOrder == null || discovered.isEmpty() || anchor.isEmpty()) return emptyList()
        val anchorIndex = gridOrder.indexOf(anchor)
        if (anchorIndex < 0 || anchor !in discovered) return emptyList()
        var first = anchorIndex
        var last = anchorIndex
        val radius = ViewerCommittedWindow.RADIUS
        while (first > 0 && anchorIndex - first < radius && gridOrder[first - 1] in discovered) first--
        while (last < gridOrder.lastIndex && last - anchorIndex < radius && gridOrder[last + 1] in discovered) last++
        return gridOrder.subList(first, last + 1).mapNotNull(discovered::get)
    }
}
