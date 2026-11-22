package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the session sequence used by [ViewPagerActivity]'s platform-owned return:
 * a single operation is prepared, completed, and cleaned, and a later reenter or
 * result cannot start a second return. This is a Kotlin state-machine proof only —
 * it does not observe any system-composited frame.
 */
class PlatformViewerReturnTest {

    private fun viewingSession(): MediaTransitionSessionCoordinator {
        val session = MediaTransitionSessionCoordinator()
        check(session.entryFinished(session.entry, withoutSharedEntry = true))
        return session
    }

    @Test
    fun platformReturnTerminatesWithoutAnyAppMotion() {
        val session = viewingSession()
        val operation = requireNotNull(session.beginReturn())
        assertEquals(State.RETURN_PREPARING, session.state)
        // No shared-element animation runs, so the caller advances the state directly.
        assertTrue(session.targetReady(operation))
        assertTrue(session.isReturning(operation))
        assertTrue(session.terminate(operation, State.COMPLETE, "platform_cross_activity_return"))
        assertEquals(State.COMPLETE, session.outcome)
        // The parent's result is still accepted even though no app animation existed.
        assertTrue(session.acceptResult(operation))
        assertFalse(session.acceptResult(operation))
        assertTrue(session.clean())
        assertEquals(State.CLEANED, session.state)
    }

    @Test
    fun reenterCannotReopenAReturnAlreadyCompletedByThePlatform() {
        val session = viewingSession()
        val operation = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(operation))
        assertTrue(session.acceptReenter(operation))
        // The viewer path completes the motion and finishes without a second animation.
        assertTrue(session.finishViewerMotion(operation, cancelled = false))
        assertTrue(session.terminate(operation, State.COMPLETE, "platform_cross_activity_return"))
        // Reenter is closed once the motion completed; the single result is still
        // accepted so the parent restores the media position, then cannot repeat.
        assertFalse(session.acceptReenter(operation))
        assertTrue(session.acceptResult(operation))
        assertFalse(session.acceptResult(operation))
    }

    @Test
    fun cancelledPlatformGestureNeverReachesReturnState() {
        val session = viewingSession()
        val gesture = requireNotNull(session.beginGesture())
        assertEquals(State.INTERACTIVE_RETURN, session.state)
        // A cancelled system gesture never commits: the in-app step is released and
        // the session is back in VIEWING with no return operation or result.
        assertTrue(session.cancelGesture(gesture))
        assertEquals(State.VIEWING, session.state)
        assertEquals(null, session.returnOperation)
        assertFalse(session.acceptResult(gesture))
    }

    @Test
    fun committedReturnCarriesOneOperationTokenToTheParent() {
        val session = viewingSession()
        val first = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(first))
        val second = session.beginReturn()
        // A second beginReturn while a return is in flight is refused, so the result
        // can only carry the first operation token.
        assertEquals(null, second)
        assertNotNull(session.returnOperation)
        assertEquals(first, session.returnOperation)
    }
}
