package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFastScrollerPolicyTest {
    @Test
    fun rangeUsesTheCurrentScrollOrientation() {
        assertTrue(MediaFastScrollerPolicy.hasScrollableRange(
            vertical = true, verticalRange = 1_200, verticalExtent = 800,
            horizontalRange = 600, horizontalExtent = 600,
        ))
        assertTrue(MediaFastScrollerPolicy.hasScrollableRange(
            vertical = false, verticalRange = 600, verticalExtent = 600,
            horizontalRange = 1_200, horizontalExtent = 800,
        ))
        assertFalse(MediaFastScrollerPolicy.hasScrollableRange(
            vertical = false, verticalRange = 1_200, verticalExtent = 800,
            horizontalRange = 600, horizontalExtent = 600,
        ))
    }

    @Test
    fun partialSnapshotExposesItsRealScrollableRangeImmediately() {
        assertTrue(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            hasScrollableRange = true,
            emptyStateVisible = false,
        ))
    }

    @Test
    fun partialListThatFitsTheViewportDoesNotExposeADragHandle() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            hasScrollableRange = false,
            emptyStateVisible = false,
        ))
    }

    @Test
    fun aCompletedListAlsoNeedsAnActualScrollableRange() {
        assertTrue(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            hasScrollableRange = true,
            emptyStateVisible = false,
        ))
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            hasScrollableRange = false,
            emptyStateVisible = false,
        ))
    }

    @Test
    fun emptyOrEmptyStateNeverExposesTheScroller() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = false,
            hasScrollableRange = true,
            emptyStateVisible = false,
        ))
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            hasScrollableRange = true,
            emptyStateVisible = true,
        ))
    }
}
