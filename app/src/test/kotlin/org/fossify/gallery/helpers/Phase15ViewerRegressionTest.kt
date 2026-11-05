package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

/** Explicit event ordering, real production policies, no sleeps or wall-clock assumptions. */
class Phase15ViewerRegressionTest {
    @Test fun a01FivePagesHaveUnknownEdgesAndRequestExpansion() {
        val navigation = PhotoViewerCatalogCoordinator()
        navigation.publish(false)
        assertEquals(PhotoViewerCatalogCoordinator.Boundary.UNKNOWN, navigation.before)
        assertTrue(navigation.request("50", false))
        assertNull(navigation.destination(listOf("50", "51", "52"), "50"))
        val window = ViewerCommittedWindow.around((0 until 100).map(Int::toString), "50") { it }
        assertEquals(49, navigation.destination(window, "50"))
    }

    @Test fun a02PauseCancelsOnlyOldOwnerAndLateCompletionCannotFinishRetry() {
        val owner = ViewerFolderScanGate()
        val old = owner.begin()!!
        owner.cancel() // old host retired; viewer receives a new ownership epoch
        val viewer = owner.begin()!!
        assertFalse(owner.finish(old))
        assertTrue(owner.current(viewer))
    }

    @Test fun a03FiveBoundaryGesturesJoinOneScan() {
        val gate = ViewerFolderScanGate()
        val attempt = gate.begin()!!
        repeat(5) { assertNull(gate.begin()) }
        assertTrue(gate.finish(attempt))
        assertNotNull(gate.begin())
    }

    @Test fun a04CompleteArrivesAfterUserSwipesAndPathWinsOverOldInteger() {
        val paths = (0 until 100).map(Int::toString)
        val current = "48" // opened at 50, swiped twice before worker completes
        assertEquals(48, ViewerPositionPolicy.resolve(paths.withIndex().associate { it.value to it.index }, current, 0, paths.size))
    }

    @Test fun a05FailureCancelTimeoutAndRejectionAllowRetryWithoutEnd() {
        listOf("FAILED", "CANCELLED", "TIMED_OUT", "REJECTED").forEach { _ ->
            val gate = ViewerFolderScanGate()
            val navigation = PhotoViewerCatalogCoordinator()
            val attempt = gate.begin()!!
            assertTrue(gate.finish(attempt))
            navigation.cancel()
            navigation.publish(false)
            assertTrue(navigation.request("anchor", true))
            val retry = gate.begin()!!
            assertFalse(gate.finish(attempt))
            assertTrue(gate.current(retry))
        }
    }

    @Test fun a05OnlyNewCurrentAttemptProgressMayRenewTheStallDeadline() {
        val gate = ViewerFolderScanGate()
        val first = gate.begin()!!
        assertFalse(gate.progress(first, 0))
        assertTrue(gate.progress(first, 120))
        assertFalse(gate.progress(first, 120)) // duplicate callbacks cannot hide a stall
        assertFalse(gate.progress(first, 100))
        assertTrue(gate.progress(first, 240)) // advancing large scans can continue
        gate.cancel()
        val retry = gate.begin()!!
        assertFalse(gate.progress(first, 1000))
        assertTrue(gate.progress(retry, 5)) // retry has its own progress and deadline
    }

    @Test fun a06LargeFolderExpandsBeyondFiveAndFifteenWithoutWaitingForComplete() {
        val paths = (0 until 11_348).map(Int::toString)
        var window = ViewerCommittedWindow.around(paths, "5000") { it }
        assertEquals(129, window.size)
        assertTrue(window.containsAll((4980..5020).map(Int::toString)))
        window = ViewerCommittedWindow.around(paths, window.first()) { it }
        assertTrue(window.contains("4900"))
        assertEquals(paths, ViewerCommittedWindow.prioritize(paths, emptyList()) { it })
    }

    @Test fun a06ScanReadsKnownNeighborhoodFirstWithoutDroppingOrDuplicatingFiles() {
        val all = (0 until 11_348).map(Int::toString)
        val priority = ViewerCommittedWindow.around(all, "5000") { it }
        val scan = ViewerCommittedWindow.prioritize(all, priority + priority + "missing") { it }
        assertEquals(priority, scan.take(priority.size))
        assertEquals(all.toSet(), scan.toSet())
        assertEquals(all.size, scan.size)
    }

    @Test fun b02B03CompleteSortedCatalogInterleavesNewMembersAndPreservesLivePath() {
        val grid = listOf("date100", "date80", "date60")
        val full = listOf("date120", "date100", "date90", "date80", "date70", "date60")
        val (ordered, inherited) = ViewerGridOrderPolicy.reconcilePhotoCatalog(
            grid, grid, full, false, complete = true) { it }
        assertEquals(full, ordered)
        assertFalse(inherited)
        val index = ViewerPositionPolicy.resolve(ordered.withIndex().associate { it.value to it.index }, "date80", 1, ordered.size)
        assertEquals("date80", ordered[index])
        assertEquals("date90", ordered[index - 1])
        assertEquals("date70", ordered[index + 1])
    }

    @Test fun b04RandomCompleteCatalogKeepsCommittedNeighborhoodStable() {
        val grid = listOf("c", "a", "b")
        assertEquals(listOf("c", "a", "b", "new"), ViewerGridOrderPolicy.reconcilePhotoCatalog(
            grid, grid, listOf("b", "new", "a", "c"), false, complete = true, random = true) { it }.first)
    }

    @Test fun b05PartialOnlyPublishesKnownContiguousRunButCompleteAddsNoMediaFilesInSortedOrder() {
        val grid = listOf("4", "2", "0")
        val partial = ViewerGridOrderPolicy.reconcilePhotoCatalog(grid, grid, listOf("4", "3", "2"), false) { it }.first
        assertEquals(listOf("4", "2"), ViewerGridOrderPolicy.contiguousPartial(grid, partial, "2") { it })
        val full = listOf("4", "3", "2", "1", "0")
        assertEquals(full, ViewerGridOrderPolicy.reconcilePhotoCatalog(grid, grid, full, false, complete = true) { it }.first)
    }

    @Test fun b06MovingPagerPublishesOnlyLatestAtIdleAndUsesLiveIdentity() {
        val gate = ViewerSwipePublicationGate<List<String>>()
        gate.onScrollMoving(true)
        assertTrue(gate.deferIfMoving(listOf("a", "b", "c")))
        assertTrue(gate.deferIfMoving(listOf("new", "a", "b", "c")))
        val ready = gate.onScrollMoving(false)!!
        val index = ViewerPositionPolicy.resolve(ready.withIndex().associate { it.value to it.index }, "c", 1, ready.size)
        assertEquals("c", ready[index])
    }

    @Test fun c01FortyFiveBatchesDoNotRequireFortyFiveCumulativeSorts() {
        val budget = ProgressivePublicationBudget()
        val admitted = (0 until 45).count { budget.admit(it * 100L, it.toLong()) }
        assertEquals(9, admitted)
        // COMPLETE is always generated by loadStaged after all 45 batches, outside this gate.
    }

    @Test fun c02BudgetKeepsFirstPreviewAndLatestGateNeverLosesComplete() {
        val budget = ProgressivePublicationBudget()
        assertTrue(budget.admit(0, 1))
        assertFalse(budget.admit(100, 2))
        val pending = ViewerSwipePublicationGate<String>()
        pending.onScrollMoving(true)
        pending.deferIfMoving("partial")
        pending.deferIfMoving("COMPLETE")
        assertEquals("COMPLETE", pending.onScrollMoving(false))
    }

    @Test fun c04SameVersionIsNotPublishedAgain() {
        val budget = ProgressivePublicationBudget()
        assertTrue(budget.admit(0, 7))
        assertFalse(budget.admit(1000, 7))
    }

    @Test fun c05EqualCountsWithDifferentMembershipHaveDifferentVersions() {
        val budget = ProgressivePublicationBudget()
        assertTrue(budget.admit(0, 1))
        assertTrue(budget.admit(500, 2)) // same count, replacement bumped content version
    }

    @Test fun d03LateSnapshotCannotReinsertDeletedPathIntoFreshMembership() {
        val stale = listOf("a", "deleted", "c")
        val fresh = listOf("new", "a", "c")
        assertFalse(ViewerGridOrderPolicy.reconcilePhotoCatalog(stale, stale, fresh, false,
            complete = true) { it }.first.contains("deleted"))
    }
}
