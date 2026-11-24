package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 C-group: a worker stage is not a reason to commit an Adapter snapshot.
 *
 * The device log for pp-6 shows twelve FILESYSTEM publications while the same 10 968
 * media were re-scanned; every one of them was a full REPLACE of the grid. The scan
 * must keep discovering, but the grid only has to change when the user can see a
 * difference or needs more content to keep browsing.
 */
class PhotoSnapshotAdmissionTest {
    private fun medium(index: Int) = Medium(
        null, "p$index.jpg", "/storage/photos/p$index.jpg", "/storage/photos",
        1_000L + index, 2_000L + index, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    private fun decide(
        displayed: Int,
        merged: Int,
        complete: Boolean = false,
        headroom: Int = 10_000,
    ) = PhotoSnapshotAdmission.decide(
        stage = PhotoLoadStage.FILESYSTEM, displayedCount = displayed, mergedCount = merged,
        baselineComplete = complete, browseHeadroom = headroom,
    )

    @Test fun c01_theFirstVisibleSliceIsNeverBlocked() {
        assertEquals(
            SnapshotDecision.PUBLISH,
            PhotoSnapshotAdmission.decide(PhotoLoadStage.CACHE_PREVIEW, 0, 160, false, Int.MAX_VALUE)
        )
        assertEquals(
            SnapshotDecision.PUBLISH,
            PhotoSnapshotAdmission.decide(PhotoLoadStage.FILESYSTEM, 0, 160, false, Int.MAX_VALUE)
        )
    }

    @Test fun c02_anUnchangedCompleteListIsNotReplacedTwelveTimes() {
        val counts = listOf(1_200, 3_361, 5_401, 7_201, 9_842, 12_000)
        val publications = counts.count { decide(displayed = 12_000, merged = it, complete = true) == SnapshotDecision.PUBLISH }

        assertEquals(0, publications)
        assertEquals(
            SnapshotDecision.SUPPRESS_COMPLETE_BASELINE,
            decide(displayed = 12_000, merged = 1_200, complete = true)
        )
    }

    @Test fun c03_aCompleteListRefreshNeverShrinksToTheScanWindow() {
        assertFalse(decide(displayed = 5_000, merged = 120, complete = true) == SnapshotDecision.PUBLISH)
        assertFalse(decide(displayed = 5_000, merged = 120) == SnapshotDecision.PUBLISH)
    }

    @Test fun c04_smallGrowthOfAPartialListIsMergedNotPublished() {
        assertEquals(
            SnapshotDecision.SUPPRESS_GROWTH,
            decide(displayed = 160, merged = 200)
        )
    }

    @Test fun c05_aUserNearTheEndOfAPartialListGetsTheNewMedia() {
        assertEquals(
            SnapshotDecision.PUBLISH,
            decide(displayed = 160, merged = 200, headroom = 4)
        )
    }

    @Test fun c06_finalCompleteIsAlwaysAdmittedEvenWhileBrowsing() {
        assertEquals(
            SnapshotDecision.PUBLISH,
            PhotoSnapshotAdmission.decide(PhotoLoadStage.COMPLETE, 160, 10_968, false, 0)
        )
        assertEquals(
            SnapshotDecision.PUBLISH,
            PhotoSnapshotAdmission.decide(PhotoLoadStage.COMPLETE, 160, 10_968, true, 0)
        )
    }

    @Test fun c07_aSnapshotThatWouldRemoveDisplayedMediaIsNeverAdmitted() {
        assertEquals(
            SnapshotDecision.SUPPRESS_SHRINK,
            decide(displayed = 4_995, merged = 120)
        )
    }

    @Test fun c08_headInsertionIsNotAnAppend() {
        val before = (0 until 20).map { medium(it + 10) }
        val after = (0 until 10).map { medium(it) } + before

        assertFalse(MediaUpdatePolicy.isContentAppend(before, after))
        assertTrue(MediaUpdatePolicy.sameDisplayedContent(before, after.subList(10, after.size)))
    }

    @Test fun c09_aRealTailAppendKeepsTheFastPath() {
        val before = (0 until 20).map { medium(it) }
        val after = before + (20 until 30).map { medium(it) }

        assertTrue(MediaUpdatePolicy.isContentAppend(before, after))
    }

    @Test fun c14_theSameMembersInANewOrderStayOnTheReorderPath() {
        val before = (0 until 30).map { medium(it) }
        val after = before.reversed()

        assertTrue(MediaUpdatePolicy.isPureMediaReorder(before, after))
    }

    /**
     * Deterministic work comparison for the pp-6 shape. The pre-fix counter is the
     * publication count the device log recorded (12 FILESYSTEM + 1 COMPLETE for the
     * same candidate sequence); the post-fix count comes from the real policy.
     */
    @Test fun scenarioTwo_partialListGrowthCutsLargeReplacementsByAtLeastHalf() {
        val discovered = listOf(
            160, 1_200, 2_041, 3_361, 4_561, 5_401, 6_241, 7_201, 8_041, 8_988, 9_842, 10_821, 10_968
        )
        var displayed = 160
        var publications = 0
        val sizes = ArrayList<Int>()
        for (merged in discovered) {
            if (decide(displayed, merged) == SnapshotDecision.PUBLISH) {
                publications++
                displayed = merged
                sizes.add(merged)
            }
        }
        // COMPLETE is always committed and is counted on top of the intermediates.
        val finalPublications = publications + 1
        val preFix = discovered.size // 12 FILESYSTEM + 1 COMPLETE == 13 commits in the log

        assertTrue("published $finalPublications vs $preFix", finalPublications * 2 <= preFix + 1)
        assertTrue("sizes not monotonic: $sizes", sizes.zipWithNext().all { (a, b) -> b >= a })
        // The authoritative count is what COMPLETE commits, not what the last
        // intermediate publication showed.
        assertEquals(10_968, discovered.last())
        assertTrue("intermediate publications grew: $sizes", displayed >= 7_201)
    }

    @Test fun scenarioOne_unchangedCompleteListPublishesOnlyTheFinalCommit() {
        val batches = listOf(1_200, 3_361, 5_401, 7_201, 9_842, 12_000)
        val intermediates = batches.count {
            decide(displayed = 12_000, merged = it, complete = true) == SnapshotDecision.PUBLISH
        }

        assertEquals(0, intermediates)
    }

    @Test fun scenarioThree_realChangesAreStillPublished() {
        // A deletion is only confirmable by COMPLETE; the intermediate batch keeps the
        // grid, and the authoritative result is always admitted.
        assertEquals(SnapshotDecision.SUPPRESS_SHRINK, decide(displayed = 4_000, merged = 3_960, complete = false))
        assertEquals(
            SnapshotDecision.PUBLISH,
            PhotoSnapshotAdmission.decide(PhotoLoadStage.COMPLETE, 4_000, 3_960, false, 0)
        )
    }
}
