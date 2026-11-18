package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Resource
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.*
import org.junit.Test

class MediaTransitionSessionCoordinatorTest {
    @Test fun entryReturnAndCleanupHaveOneOutcome() {
        val session = MediaTransitionSessionCoordinator()
        val entry = session.entry
        assertFalse(session.beginMotion(entry))
        assertTrue(session.capture(entry))
        assertFalse(session.capture(entry))
        assertTrue(session.beginMotion(entry))
        assertTrue(session.entryFinished(entry))
        assertFalse(session.entryFinished(entry))
        assertFalse(session.leases().acquire(entry, Resource.PROXY) { fail("late entry") })
        val returning = requireNotNull(session.beginReturn())
        assertNotEquals(entry.operationId, returning.operationId)
        assertFalse(session.acceptResult(entry))
        assertTrue(session.targetReady(returning))
        assertTrue(session.acceptResult(returning))
        assertFalse(session.acceptResult(returning))
        assertTrue(session.terminate(returning, State.COMPLETE, "target_drawn"))
        assertFalse(session.terminate(returning, State.CANCELLED, "destroyed"))
        assertEquals(State.COMPLETE, session.outcome)
        assertTrue(session.clean())
        assertFalse(session.clean())
        assertEquals(State.CLEANED, session.state)
    }

    @Test fun staleEntryAndOtherGenerationsCannotTouchResources() {
        val old = MediaTransitionSessionCoordinator()
        val current = MediaTransitionSessionCoordinator()
        val leases = current.leases()
        var released = 0
        assertTrue(leases.acquire(current.entry, Resource.INPUT) { released++ })
        assertFalse(leases.acquire(old.entry, Resource.PROXY) { released++ })
        assertFalse(leases.release(old.entry, Resource.INPUT))
        assertTrue(old.terminate(old.entry, State.CANCELLED, "early_close"))
        assertFalse(old.capture(old.entry))
        assertTrue(current.terminate(current.entry, State.CANCELLED, "destroyed"))
        assertFalse(current.capture(current.entry))
        assertFalse(leases.acquire(current.entry, Resource.PROXY) { released++ })
        assertFalse(current.clean())
        leases.releaseAll()
        leases.releaseAll()
        assertEquals(1, released)
        assertTrue(current.clean())
    }

    @Test fun gestureCancellationAndCommitInvalidateOnlyThePreviousOperation() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val first = requireNotNull(session.beginGesture())
        assertNull(session.beginGesture())
        assertTrue(session.cancelGesture(first))
        assertEquals(State.VIEWING, session.state)
        val next = requireNotNull(session.beginGesture())
        assertFalse(session.cancelGesture(first))
        assertEquals(next, session.beginReturn())
        assertFalse(session.cancelGesture(next))
        assertTrue(session.targetReady(next))
    }

    @Test fun entryFallbackCanStillViewAndReturnTimeoutReleasesEveryLease() {
        val session = MediaTransitionSessionCoordinator()
        val entry = session.entry
        assertTrue(session.entryFallback(entry, "timeout"))
        assertFalse(session.beginMotion(entry))
        assertTrue(session.entryFinished(entry))
        val returning = requireNotNull(session.beginReturn())
        val leases = session.leases()
        val released = mutableListOf<Resource>()
        val resources = listOf(Resource.INPUT, Resource.PROXY, Resource.PUBLICATION, Resource.TARGET)
        resources.forEach { assertTrue(leases.acquire(returning, it) { released += it }) }
        assertFalse(leases.acquire(returning, Resource.INPUT) { fail("duplicate lease") })
        assertTrue(session.targetReady(returning))
        assertTrue(session.terminate(returning, State.FALLBACK, "target_changed"))
        assertFalse(session.targetReady(returning))
        leases.releaseAll()
        leases.releaseAll()
        assertEquals(resources.reversed(), released)
        assertTrue(session.clean())
    }

    @Test fun resultBeforeReenterCannotAcquireAStaleTarget() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        assertTrue(session.acceptResult(returning))
        assertFalse(session.acceptReenter(returning))
        assertTrue(session.terminate(returning, State.FALLBACK, "result_without_reenter"))
        assertFalse(session.leases().acquire(returning, Resource.TARGET) { fail("late target") })
        assertTrue(session.clean())
    }

    @Test fun cancelledEntryInvalidatesItsGateAndStillAllowsOneFrameworkResult() {
        val events = mutableListOf<String>()
        val session = MediaTransitionSessionCoordinator { event, _, _ -> events += event }
        val entry = session.entry
        assertTrue(session.attachViewer())
        assertTrue(session.entryGate.requestRelease())
        assertTrue(session.terminate(entry, State.CANCELLED, "destroyed"))
        assertFalse(session.entryGate.release())
        assertFalse(session.acceptFrameworkCancellation())
        assertTrue(session.clean())
        assertTrue(session.acceptResult(entry))
        assertFalse(session.acceptResult(entry))
        assertEquals(1, events.count { it == "transition_terminal" })
        assertEquals(1, events.count { it == "transition_cleanup_completed" })
    }

    @Test fun returnCallbacksRejectWrongGenerationAndDuplicatePlatformEnd() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        assertFalse(session.acceptReenter(returning.copy(generation = returning.generation - 1)))
        assertTrue(session.acceptReenter(returning))
        assertFalse(session.acceptReenter(returning))
        assertTrue(session.targetReady(returning))
        assertTrue(session.finishViewerMotion(returning, true))
        assertFalse(session.finishViewerMotion(returning, false))
        assertEquals(State.CANCELLED, session.viewerMotionOutcome)
        assertTrue(session.terminate(returning, State.CANCELLED, "platform_cancelled"))
        assertFalse(session.finishViewerMotion(returning, false))
    }

    @Test fun cancelledLaunchWithoutViewerAdmitsOnlyOneFrameworkResult() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.acceptFrameworkCancellation())
        assertFalse(session.acceptFrameworkCancellation())
        assertFalse(session.attachViewer())
        assertTrue(session.clean())
    }

    @Test fun gridDetachDuringGestureLeavesViewerAbleToSettleAndClose() {
        val session = MediaTransitionSessionCoordinator()
        session.attachGrid()
        assertTrue(session.attachViewer())
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val gesture = requireNotNull(session.beginGesture())
        session.detachGrid()
        assertTrue(session.cancelGesture(gesture))
        val returning = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(returning))
        assertTrue(session.terminate(returning, State.COMPLETE, "standalone_return"))
        assertTrue(session.clean())
    }

    @Test fun destroyedUncommittedGestureAcceptsOnlyTheDefaultEntryResult() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.attachViewer())
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val gesture = requireNotNull(session.beginGesture())
        assertTrue(session.terminate(gesture, State.CANCELLED, "viewer_recreated"))
        assertFalse(session.acceptResult(gesture))
        assertTrue(session.acceptResult(session.entry))
        assertFalse(session.acceptResult(session.entry))
        assertTrue(session.clean())
    }
}
