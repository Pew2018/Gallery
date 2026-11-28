package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.*
import org.junit.Test

class AuditSharedReturnCancelTest {
    @Test fun cancelledSharedReturnRestoresViewingAndAllowsAnotherClose() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        val leases = session.leases()
        var unlocked = false
        assertTrue(leases.acquire(returning, MediaTransitionSessionCoordinator.Resource.INPUT) { unlocked = true })
        assertTrue(session.finishViewerMotion(returning, cancelled = true))
        assertEquals(State.CANCELLED, session.viewerMotionOutcome)
        assertTrue(session.isReturning(returning))

        leases.releaseAll()
        assertTrue(unlocked)
        assertTrue(session.abortCancelledReturn(returning))
        assertEquals(State.VIEWING, session.state)
        assertNull(session.outcome)
        assertNull(session.viewerMotionOutcome)
        assertNull(session.returnOperation)

        val again = requireNotNull(session.beginReturn())
        assertTrue(session.terminate(again, State.FALLBACK, "viewer_no_proxy"))
        assertEquals(State.FALLBACK, session.outcome)
        assertTrue(session.clean())
    }

    @Test fun completedReturnStillTerminatesOnce() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(returning))
        assertTrue(session.finishViewerMotion(returning, cancelled = false))
        assertFalse(session.abortCancelledReturn(returning))
        assertTrue(session.terminate(returning, State.COMPLETE, "target_drawn"))
        assertEquals(State.COMPLETE, session.outcome)
        assertTrue(session.clean())
    }

    @Test fun cancelStayOpenReleasesClipCleanupButDiscardsProxyTeardown() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        val leases = session.leases()
        var unlocked = false
        var clipRestored = false
        var proxyTornDown = false
        assertTrue(leases.acquire(returning, MediaTransitionSessionCoordinator.Resource.INPUT) { unlocked = true })
        assertTrue(leases.acquire(returning, MediaTransitionSessionCoordinator.Resource.RETURN_TOOLBAR_CLIP) {
            clipRestored = true
        })
        assertTrue(leases.acquire(returning, MediaTransitionSessionCoordinator.Resource.PROXY) {
            proxyTornDown = true
        })
        assertTrue(session.finishViewerMotion(returning, cancelled = true))

        assertTrue(leases.release(returning, MediaTransitionSessionCoordinator.Resource.INPUT))
        assertTrue(leases.release(returning, MediaTransitionSessionCoordinator.Resource.RETURN_TOOLBAR_CLIP))
        leases.discardRemaining()
        assertTrue(unlocked)
        assertTrue(clipRestored)
        assertFalse("PROXY finish-time teardown must not run while Viewer stays open", proxyTornDown)
        assertTrue(session.abortCancelledReturn(returning))
        assertEquals(State.VIEWING, session.state)
        assertNull(session.returnOperation)
    }
}
