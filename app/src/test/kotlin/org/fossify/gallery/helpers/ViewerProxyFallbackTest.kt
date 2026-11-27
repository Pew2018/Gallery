package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Phase 2.2 predictive-back behaviour when the shared proxy is unavailable.
 *
 * The 2026-11-25 device sample went VIEWING -> INTERACTIVE_RETURN -> VIEWING ->
 * RETURN_PREPARING -> FALLBACK(viewer_no_proxy). The state machine must keep that
 * path single-terminal and side-effect free (B05/B06/B07), and every rejection must
 * now carry a semantic name instead of a bare integer (B-diagnostics).
 */
class ViewerProxyFallbackTest {
    @Test
    fun b05GestureStartWithUnavailableProxyCancelsBackToViewingWithNoResidue() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.attachViewer())
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        // beginGesture succeeded, then getCurrentSharedElementView returned null:
        // the activity cancels the gesture instead of mutating any surface.
        val gesture = requireNotNull(session.beginGesture())
        assertEquals(State.INTERACTIVE_RETURN, session.state)
        assertTrue(session.cancelGesture(gesture))
        assertEquals("the viewer stays open after a cancelled gesture", State.VIEWING, session.state)
        assertNull("no outcome may be recorded for a cancelled gesture", session.outcome)
        // And the viewer can immediately try again (B06 pre-condition).
        val retry = requireNotNull(session.beginGesture())
        assertTrue(session.cancelGesture(retry))
        assertEquals(State.VIEWING, session.state)
    }

    @Test
    fun b06CommittedBackWithoutAProxyEndsInOneFallbackTerminalAndReleasesEverything() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val returning = requireNotNull(session.beginReturn())
        val released = mutableListOf<MediaTransitionSessionCoordinator.Resource>()
        val leases = session.leases()
        listOf(MediaTransitionSessionCoordinator.Resource.INPUT,
            MediaTransitionSessionCoordinator.Resource.PROXY,
            MediaTransitionSessionCoordinator.Resource.TARGET,
        ).forEach { assertTrue(leases.acquire(returning, it) { released += it }) }
        // The commit path with no proxy: one terminal, every lease released once,
        // cleanup idempotent - exactly the FALLBACK(viewer_no_proxy) walk.
        assertTrue(session.terminate(returning, State.FALLBACK, "viewer_no_proxy"))
        assertFalse(session.terminate(returning, State.FALLBACK, "viewer_no_proxy"))
        assertEquals(State.FALLBACK, session.outcome)
        leases.releaseAll()
        leases.releaseAll()
        // releaseAll unwinds in reverse acquisition order, exactly once per lease.
        assertEquals(listOf(MediaTransitionSessionCoordinator.Resource.TARGET,
            MediaTransitionSessionCoordinator.Resource.PROXY,
            MediaTransitionSessionCoordinator.Resource.INPUT), released)
        assertTrue(session.clean())
        assertFalse(session.clean())
        assertEquals(State.CLEANED, session.state)
    }

    @Test
    fun b07CancelledGestureThenCommitProducesExactlyOneOutcome() {
        val session = MediaTransitionSessionCoordinator()
        assertTrue(session.attachViewer())
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
        val gesture = requireNotNull(session.beginGesture())
        assertTrue(session.cancelGesture(gesture))
        // The user's cancelled gesture is followed by a committed system back with
        // no proxy: the session must not mix the two operations.
        val returning = requireNotNull(session.beginReturn())
        assertFalse("the stale gesture operation is dead", session.cancelGesture(gesture))
        assertTrue(session.terminate(returning, State.FALLBACK, "viewer_no_proxy"))
        assertEquals(State.FALLBACK, session.outcome)
        assertTrue(session.clean())
        assertEquals(State.CLEANED, session.state)
    }

    @Test
    fun everyLegacyPhotoRejectionCodeHasASemanticNameAndBack() {
        (1..9).forEach { code ->
            val name = ViewerProxyRejection.name(code)
            assertEquals(code, ViewerProxyRejection.legacyCode(name))
        }
        assertEquals(ViewerProxyRejection.NOT_AT_FIT, ViewerProxyRejection.name(4))
        assertEquals(ViewerProxyRejection.PROXY_PREPARE_REJECTED, ViewerProxyRejection.name(0))
    }

    @Test
    fun theDeviceSampleCount4MapsToNotAtFit() {
        // viewer_transition_photo_unavailable count=4 must now be readable as the
        // zoom-baseline state it came from, not an opaque integer.
        assertEquals("NOT_AT_FIT", ViewerProxyRejection.name(4))
    }
}
