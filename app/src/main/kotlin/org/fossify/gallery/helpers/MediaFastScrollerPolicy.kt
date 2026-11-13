package org.fossify.gallery.helpers

/** A loaded prefix is not a trustworthy scroll range for a progressively published media set. */
internal object MediaFastScrollerPolicy {
    fun isReady(
        hasContent: Boolean,
        complete: Boolean,
        emptyStateVisible: Boolean,
        knownTotalCount: Int? = null,
        displayedMediaCount: Int = if (hasContent) 1 else 0,
        adapterMatchesPresentation: Boolean = true,
    ): Boolean {
        val rangeIsComplete = complete ||
            (knownTotalCount != null && knownTotalCount > 0 && displayedMediaCount == knownTotalCount &&
                adapterMatchesPresentation)
        return hasContent && rangeIsComplete && !emptyStateVisible
    }
}
