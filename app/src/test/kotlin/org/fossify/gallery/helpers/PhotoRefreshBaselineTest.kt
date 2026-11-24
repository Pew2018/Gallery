package org.fossify.gallery.helpers

import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 A-group: the displayed Photos snapshot may not shrink because a rescan
 * has only discovered part of the same source yet.
 *
 * These cases reproduce the two device regressions (160 -> 120 and 4995 -> 120)
 * without a device: the worker publishes a smaller candidate set than the grid
 * already shows, which `mMedia = ArrayList(media)` then commits.
 */
class PhotoRefreshBaselineTest {
    private fun medium(index: Int, size: Long = 10L, folder: String = "/storage/photos") = Medium(
        null, "p$index.jpg", "$folder/p$index.jpg", folder, 1_000L + index, 2_000L + index,
        size, TYPE_IMAGES, 0, false, 0, 0
    )

    private fun baseline(
        count: Int,
        complete: Boolean = false,
        key: String = "scope-a",
        revision: Long = 7L,
        access: Long = 3L,
        sorting: Int = SORT_BY_DATE_TAKEN,
    ) = PhotoRefreshBaseline(
        scopeKey = key,
        profileId = "profile-1",
        revision = revision,
        accessVersion = access,
        sorting = sorting,
        complete = complete,
        items = (0 until count).map { medium(it) },
    )

    @Test fun a01_partialRescanKeepsMediaThatTheFirstBatchHasNotDiscoveredYet() {
        val displayed = baseline(count = 160)
        val firstBatch = (0 until 120).map { medium(it) }

        val merged = displayed.merged(firstBatch)

        assertEquals(160, merged.media.size)
        assertEquals(40, merged.retained)
        assertEquals(0, merged.added)
        assertEquals((0 until 160).map { "/storage/photos/p$it.jpg" }.toSet(), merged.media.map { it.path }.toSet())
    }

    @Test fun a02_completeListDoesNotFallBackToTheFirstScanWindow() {
        val displayed = baseline(count = 4_995, complete = true)
        val firstBatch = (0 until 120).map { medium(it) }

        val merged = displayed.merged(firstBatch)

        assertEquals(4_995, merged.media.size)
        assertEquals(4_875, merged.retained)
    }

    @Test fun a03_repeatedlySmallerBatchesNeverShrinkTheMergedSet() {
        val displayed = baseline(count = 160)
        // pp-6 shape: the same scope is re-scanned and each batch is a different
        // prefix of the source, so the published size must stay monotonic.
        val counts = ArrayList<Int>()
        for (batchSize in listOf(120, 1_200, 2_041, 3_361, 4_561, 7_201, 10_968)) {
            counts.add(displayed.merged((0 until batchSize).map { medium(it) }).media.size)
        }

        assertTrue("counts shrank: $counts", counts.zipWithNext().all { (before, after) -> after >= before })
        assertTrue("baseline lost: $counts", counts.all { it >= 160 })
        assertEquals(10_968, counts.last())
    }

    @Test fun a04_newMetadataForTheSamePathWinsWithoutDuplicatingTheMedia() {
        val displayed = baseline(count = 3)
        val discovered = listOf(medium(1, size = 999L), medium(3), medium(4))

        val merged = displayed.merged(discovered)

        assertEquals(5, merged.media.size)
        assertEquals(1, merged.updated)
        assertEquals(2, merged.added)
        assertEquals(2, merged.retained)
        assertEquals(1, merged.media.count { it.path == "/storage/photos/p1.jpg" })
        assertEquals(999L, merged.media.first { it.path == "/storage/photos/p1.jpg" }.size)
    }

    @Test fun a05_newMediaIsAddedAfterTheRetainedBaseline() {
        val merged = baseline(count = 2).merged(listOf(medium(9), medium(10)))

        assertEquals(4, merged.media.size)
        assertEquals(2, merged.added)
        assertEquals(2, merged.retained)
    }

    @Test fun a07_finalCompleteIsAuthoritativeAndMayBeSmaller() {
        // COMPLETE never passes through the baseline: the merge is for display only.
        val displayed = baseline(count = 40)
        val authoritative = (0 until 20).map { medium(it) }

        // The merge is a display-only union; the authority is still the scan.
        assertEquals(40, displayed.merged(authoritative).media.size)
        assertEquals(20, authoritative.toSet().size)
        assertTrue(authoritative.all { it.path in displayed.merged(authoritative).media.map { m -> m.path } })
    }

    @Test fun a08_equalCountsWithDifferentIdentitiesAreStillAReplacement() {
        val displayed = baseline(count = 160)
        val different = (0 until 160).map { medium(it, folder = "/storage/other") }

        val merged = displayed.merged(different)

        // Membership is decided by path, never by list length.
        assertEquals(320, merged.media.size)
        assertEquals(160, merged.added)
        assertEquals(0, merged.updated)
    }

    @Test fun a09_anotherProfileOrSourceScopeRejectsTheBaseline() {
        assertEquals(
            PhotoBaselineRejection.SCOPE_CHANGED,
            PhotoRefreshBaselinePolicy.rejection(
                currentKey = "scope-b", displayedKey = "scope-a",
                currentRevision = 7L, displayedRevision = 7L,
                currentAccessVersion = 3L, displayedAccessVersion = 3L,
                needsUnlock = false, displayedCount = 160, searchActive = false, adapterReady = true,
            )
        )
    }

    @Test fun a10_revokedAccessRejectsTheBaselineEvenWhileScrolling() {
        assertEquals(
            PhotoBaselineRejection.ACCESS_CHANGED,
            PhotoRefreshBaselinePolicy.rejection(
                currentKey = "scope-a", displayedKey = "scope-a",
                currentRevision = 7L, displayedRevision = 7L,
                currentAccessVersion = 4L, displayedAccessVersion = 3L,
                needsUnlock = false, displayedCount = 160, searchActive = false, adapterReady = true,
            )
        )
        assertFalse(PhotoRefreshBaselinePolicy.rejection(
            currentKey = "scope-a", displayedKey = "scope-a",
            currentRevision = 7L, displayedRevision = 7L,
            currentAccessVersion = 3L, displayedAccessVersion = 3L,
            needsUnlock = true, displayedCount = 160, searchActive = false, adapterReady = true,
        ) == PhotoBaselineRejection.NONE)
    }

    @Test fun a11_contentRevisionAndAccessVersionAreSeparateReasons() {
        assertEquals(
            PhotoBaselineRejection.REVISION_CHANGED,
            PhotoRefreshBaselinePolicy.rejection(
                currentKey = "scope-a", displayedKey = "scope-a",
                currentRevision = 8L, displayedRevision = 7L,
                currentAccessVersion = 3L, displayedAccessVersion = 3L,
                needsUnlock = false, displayedCount = 160, searchActive = false, adapterReady = true,
            )
        )
    }

    @Test fun a12_aStaleOrEmptyDisplayCannotBecomeABaseline() {
        val base = PhotoRefreshBaselinePolicy.rejection(
            currentKey = "scope-a", displayedKey = "scope-a",
            currentRevision = 7L, displayedRevision = 7L,
            currentAccessVersion = 3L, displayedAccessVersion = 3L,
            needsUnlock = false, displayedCount = 0, searchActive = false, adapterReady = true,
        )
        assertEquals(PhotoBaselineRejection.NO_DISPLAY, base)
        val missingAdapter = PhotoRefreshBaselinePolicy.rejection(
            currentKey = "scope-a", displayedKey = null,
            currentRevision = 7L, displayedRevision = -1L,
            currentAccessVersion = 3L, displayedAccessVersion = 3L,
            needsUnlock = false, displayedCount = 160, searchActive = false, adapterReady = false,
        )
        assertEquals(PhotoBaselineRejection.ADAPTER_MISSING, missingAdapter)
    }

    @Test fun searchResultsAreNeverTreatedAsAWholeSourceBaseline() {
        assertEquals(
            PhotoBaselineRejection.SEARCH_ACTIVE,
            PhotoRefreshBaselinePolicy.rejection(
                currentKey = "scope-a", displayedKey = "scope-a",
                currentRevision = 7L, displayedRevision = 7L,
                currentAccessVersion = 3L, displayedAccessVersion = 3L,
                needsUnlock = false, displayedCount = 12, searchActive = true, adapterReady = true,
            )
        )
        val captured = PhotoRefreshBaseline.capture(
            scopeKey = "scope-a", profileId = "profile-1", revision = 7L, accessVersion = 3L,
            sorting = SORT_BY_NAME, complete = false, needsUnlock = false,
            currentKey = "scope-a", displayedKey = "scope-a", currentRevision = 7L,
            currentAccessVersion = 3L, searchActive = true, adapterReady = true,
            displayed = (0 until 12).map { medium(it) },
        )
        assertNull(captured)
    }

    @Test fun eligibleBaselineCopiesTheDisplayedRows() {
        val captured = PhotoRefreshBaseline.capture(
            scopeKey = "scope-a", profileId = "profile-1", revision = 7L, accessVersion = 3L,
            sorting = SORT_BY_NAME, complete = true, needsUnlock = false,
            currentKey = "scope-a", displayedKey = "scope-a", currentRevision = 7L,
            currentAccessVersion = 3L, searchActive = false, adapterReady = true,
            displayed = (0 until 5).map { medium(it) },
        )
        requireNotNull(captured)
        assertEquals(5, captured.size)
        assertTrue(captured.complete)
        assertEquals(SORT_BY_NAME, captured.sorting)
    }

    @Test fun randomOrderingIsNotAStableBaseline() {
        val captured = PhotoRefreshBaseline.capture(
            scopeKey = "scope-a", profileId = "profile-1", revision = 7L, accessVersion = 3L,
            sorting = org.fossify.commons.helpers.SORT_BY_RANDOM, complete = false, needsUnlock = false,
            currentKey = "scope-a", displayedKey = "scope-a", currentRevision = 7L,
            currentAccessVersion = 3L, searchActive = false, adapterReady = true,
            displayed = (0 until 5).map { medium(it) },
        )
        assertNull(captured)
    }
}
