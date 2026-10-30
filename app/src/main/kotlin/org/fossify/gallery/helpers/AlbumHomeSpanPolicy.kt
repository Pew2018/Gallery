package org.fossify.gallery.helpers

/** Pure position-to-span mapping for the five ConcatAdapter home sections. */
internal object AlbumHomeSpanPolicy {
    fun spanSize(
        position: Int,
        spanCount: Int,
        favoritesCount: Int,
        pinnedHeaderCount: Int,
        expandedPinnedCount: Int,
        otherTitleCount: Int,
        listMode: Boolean
    ): Int {
        val fullSpan = spanCount.coerceAtLeast(1)
        if (position < 0) return fullSpan
        var sectionPosition = position
        if (sectionPosition < favoritesCount) return fullSpan
        sectionPosition -= favoritesCount
        if (sectionPosition < pinnedHeaderCount) return fullSpan
        sectionPosition -= pinnedHeaderCount
        if (sectionPosition < expandedPinnedCount) return if (listMode) fullSpan else 1
        sectionPosition -= expandedPinnedCount
        if (sectionPosition < otherTitleCount) return fullSpan
        return if (listMode) fullSpan else 1
    }
}

internal data class HomeCardSpec(
    val nameSize: Float,
    val countSize: Float,
    val showCount: Boolean,
    val topGap: Int,
    val countGap: Int
)

internal object HomeCardLayoutPolicy {
    fun forWidthDp(widthDp: Float): HomeCardSpec = when {
        widthDp >= 120f -> HomeCardSpec(14f, 12f, true, 6, 2)
        widthDp >= 76f -> HomeCardSpec(12f, 10f, true, 4, 1)
        widthDp >= 48f -> HomeCardSpec(10f, 9f, true, 3, 0)
        else -> HomeCardSpec(9f, 8f, false, 2, 0)
    }
}

internal data class AlbumStripVisualState(
    val height: Int,
    val alpha: Float,
    val visible: Boolean
)

internal object AlbumStripStatePolicy {
    fun settled(expanded: Boolean, targetHeight: Int) = if (expanded) {
        AlbumStripVisualState(height = 0, alpha = 0f, visible = false)
    } else {
        AlbumStripVisualState(height = targetHeight.coerceAtLeast(1), alpha = 1f, visible = true)
    }
}
