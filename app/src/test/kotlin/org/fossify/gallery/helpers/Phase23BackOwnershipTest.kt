package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.3 C/D: deterministic back-state and ownership contracts.
 *
 * These tests exercise the real production coordinator that ViewPagerActivity
 * uses. They do NOT claim to verify Android 16 shell compositing or the native
 * predictive animation - that remains a device-acceptance item.
 */
class Phase23BackOwnershipTest {

    // --- C: Viewer return state machine -------------------------------------------------

    private fun viewingSession(): MediaTransitionSessionCoordinator =
        MediaTransitionSessionCoordinator().also { session ->
            val entry = checkNotNull(session.entry)
            check(session.capture(entry))
            check(session.beginMotion(entry))
            check(session.entryFinished(entry))
        }

    @Test
    fun cCancelThenRetryRestoresViewingAndYieldsANewOperation() {
        val session = viewingSession()
        val first = checkNotNull(session.beginGesture())
        assertTrue(session.cancelGesture(first))
        assertEquals(MediaTransitionSessionCoordinator.State.VIEWING, session.state)

        val second = checkNotNull(session.beginGesture())
        assertFalse("a cancelled operation must not be reused", first == second)
        assertTrue(session.beginReturn() != null)
    }

    @Test
    fun cRepeatedCloseAttemptsHaveExactlyOneTerminal() {
        val session = viewingSession()
        val first = checkNotNull(session.beginReturn())
        assertTrue(session.terminate(first, MediaTransitionSessionCoordinator.State.FALLBACK, "no_proxy"))
        assertFalse("a second terminal for the same operation must be rejected",
            session.terminate(first, MediaTransitionSessionCoordinator.State.COMPLETE, "late"))
        // A late close while RETURNING is merged in the Activity and logged; the
        // coordinator itself must never adopt a different operation after a terminal.
        assertFalse(session.beginGesture() != null)
    }

    @Test
    fun cLateGenerationCannotMoveANewerOperation() {
        val session = viewingSession()
        val old = checkNotNull(session.beginGesture())
        assertTrue(session.cancelGesture(old))
        val current = checkNotNull(session.beginReturn())
        assertFalse("an old token cannot cancel the new return", session.cancelGesture(old))
        assertTrue(session.isReturning(current))
    }

    @Test
    fun cACommittedExitAlwaysReachesATerminalState() {
        val session = viewingSession()
        val operation = checkNotNull(session.beginReturn())
        assertTrue(session.targetReady(operation))
        assertTrue(session.terminate(operation, MediaTransitionSessionCoordinator.State.COMPLETE, "target_drawn"))
        assertEquals(MediaTransitionSessionCoordinator.State.COMPLETE, session.state)
        assertTrue(session.clean())
    }

    @Test
    fun cCleanupWaitsForLeaseRelease() {
        val session = viewingSession()
        val operation = checkNotNull(session.beginReturn())
        val leases = session.leases()
        assertTrue(leases.acquire(operation, MediaTransitionSessionCoordinator.Resource.INTERACTIVE_ANIMATOR) {})
        assertTrue(session.terminate(operation, MediaTransitionSessionCoordinator.State.FALLBACK, "no_proxy"))
        assertFalse("an outstanding animation lease must keep the session unclean", session.clean())
        assertTrue(leases.release(operation, MediaTransitionSessionCoordinator.Resource.INTERACTIVE_ANIMATOR))
        assertTrue(session.clean())
    }

    // --- D: system back ownership -------------------------------------------------------

    @Test
    fun dRootPagesNeverKeepAnAppBackCallbackEnabled() {
        assertFalse(BackStepPolicy.albumsOwnsBackStep(
            searchOpen = false, groupedSubfolders = false, openedSubfolders = 1))
        assertFalse(BackStepPolicy.albumsOwnsBackStep(
            searchOpen = false, groupedSubfolders = true, openedSubfolders = 1))
        assertFalse(BackStepPolicy.mediaOwnsBackStep(searchOpen = false))
        assertFalse(BackStepPolicy.settingsOwnsBackStep(categoryOpen = false))
    }

    @Test
    fun dOnlyGenuineInAppStepsTakeBackOwnership() {
        assertTrue(BackStepPolicy.albumsOwnsBackStep(false, true, 2))
        assertTrue(BackStepPolicy.albumsOwnsBackStep(true, false, 1))
        assertTrue(BackStepPolicy.mediaOwnsBackStep(true))
        assertTrue(BackStepPolicy.settingsOwnsBackStep(true))
    }

    @Test
    fun dDiagnosticsNeverClaimShellInternalFacts() {
        val fields = AppBackOwnershipDiagnostics.fields(
            activityId = 1, taskId = 2, isTaskRoot = true, section = "albums",
            searchOpen = false, actionModeActive = false, imeVisible = false,
            appBackCallbackEnabled = false, inAppBackSteps = 0, navigationToken = "nav-1",
            windowAnimations = 0, lifecycle = "RESUMED", rootRetiring = false,
        )
        assertEquals(true, fields["platformOwnsBack"])
        assertEquals(false, fields["appBackCallbackEnabled"])
        assertEquals(0, fields["inAppBackSteps"])
        assertFalse("Shell-internal gesture progress must not be invented",
            fields.keys.any { it.contains("progress", ignoreCase = true) })
    }
}
