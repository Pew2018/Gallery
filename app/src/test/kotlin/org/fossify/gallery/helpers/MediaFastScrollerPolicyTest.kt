package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFastScrollerPolicyTest {
    @Test
    fun partialPrefixNeverAdvertisesItsLoadedCountAsTheWholeScrollRange() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = false,
            emptyStateVisible = false,
        ))
    }

    @Test
    fun completeIndexedSnapshotCanExposeAStableRangeBeforePhysicalReconciliationFinishes() {
        assertTrue(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = false,
            emptyStateVisible = false,
            knownTotalCount = 500,
            displayedMediaCount = 500,
        ))
    }

    @Test
    fun indexRangeIsHiddenWhenPhysicalScanFindsAdditionalRows() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = false,
            emptyStateVisible = false,
            knownTotalCount = 500,
            displayedMediaCount = 501,
        ))
    }

    @Test
    fun indexedRangeIsHiddenUntilThatSnapshotIsCommittedToTheAdapter() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = false,
            emptyStateVisible = false,
            knownTotalCount = 500,
            displayedMediaCount = 500,
            adapterMatchesPresentation = false,
        ))
    }

    @Test
    fun completeNonEmptyResultCanExposeTheScroller() {
        assertTrue(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = true,
            emptyStateVisible = false,
        ))
    }

    @Test
    fun emptyOrEmptyStateNeverExposesTheScroller() {
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = false,
            complete = true,
            emptyStateVisible = false,
        ))
        assertFalse(MediaFastScrollerPolicy.isReady(
            hasContent = true,
            complete = true,
            emptyStateVisible = true,
        ))
    }
}
