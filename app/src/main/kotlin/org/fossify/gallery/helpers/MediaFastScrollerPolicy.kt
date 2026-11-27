package org.fossify.gallery.helpers

/** Only expose the handle when the currently laid-out RecyclerView has a real scroll range. */
internal object MediaFastScrollerPolicy {
    fun hasScrollableRange(
        vertical: Boolean,
        verticalRange: Int,
        verticalExtent: Int,
        horizontalRange: Int,
        horizontalExtent: Int,
    ): Boolean = if (vertical) {
        verticalRange > verticalExtent
    } else {
        horizontalRange > horizontalExtent
    }

    fun isReady(
        hasContent: Boolean,
        hasScrollableRange: Boolean,
        emptyStateVisible: Boolean,
    ): Boolean {
        return hasContent && hasScrollableRange && !emptyStateVisible
    }
}
