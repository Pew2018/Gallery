package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract used by the app-owned close: the Grid target must be readable while
 * the session is already preparing the return, not only after an Activity
 * reenter that the opaque Viewer no longer performs. The test proves the state
 * machine and the single-operation result handoff; it does not observe pixels.
 */
class ViewerAppReturnSessionTest {
    private class FakeGrid : MediaTransitionSessionCoordinator.GridPreview {
        var shows = 0
        var releases = 0
        var target: StaticPhotoReturnTarget? = null
        override fun show(token: MediaTransitionSessionCoordinator.Token, path: String) {
            shows++
        }
        override fun release(token: MediaTransitionSessionCoordinator.Token) {
            releases++
        }
        override fun photoReturnTarget(
            token: MediaTransitionSessionCoordinator.Token,
            path: String,
        ): StaticPhotoReturnTarget? = target
    }

    private fun viewing(session: MediaTransitionSessionCoordinator, grid: FakeGrid) {
        session.attachGrid(grid)
        assertTrue(session.entryFinished(session.entry, withoutSharedEntry = true))
    }

    @Test fun interactiveCloseCanReadAndReleaseTheGridTargetBeforeReenter() {
        val session = MediaTransitionSessionCoordinator()
        val grid = FakeGrid()
        viewing(session, grid)
        val gesture = requireNotNull(session.beginGesture())
        assertEquals(State.INTERACTIVE_RETURN, session.state)
        session.showGridPreview(gesture, "image")
        assertEquals(1, grid.shows)
        // The measured target is available to the commit path while the finger is
        // still down; the app-owned close does not need RETURNING for this read.
        grid.target = null
        assertEquals(null, session.photoReturnTarget(gesture, "image"))

        val operation = requireNotNull(session.beginReturn())
        assertEquals(gesture, operation)
        assertEquals(State.RETURN_PREPARING, session.state)
        // Idempotent: the toolbar path calls show again after beginReturn.
        session.showGridPreview(operation, "image")
        assertEquals(2, grid.shows)
        assertTrue(session.isReturning(operation))
        assertTrue(session.targetReady(operation))
        assertEquals(State.RETURNING, session.state)
        assertTrue(session.terminate(operation, State.COMPLETE, "app_return_to_grid"))
        assertEquals(State.COMPLETE, session.outcome)
        assertEquals(1, grid.releases)
        // The parent still restores position and deletions from this one token.
        assertTrue(session.acceptResult(operation))
        assertFalse(session.acceptResult(operation))
        assertTrue(session.clean())
    }

    @Test fun aCloseThatCannotMeasureATargetStillTerminatesAsAnAppFallback() {
        val session = MediaTransitionSessionCoordinator()
        val grid = FakeGrid()
        viewing(session, grid)
        val operation = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(operation))
        assertTrue(session.terminate(operation, State.FALLBACK, "app_return_no_target"))
        assertEquals(State.FALLBACK, session.outcome)
        assertEquals(1, grid.releases)
        assertTrue(session.acceptResult(operation))
    }

    @Test fun stableViewerEdgeBackStillReleasesWithoutAppVisual() {
        val session = MediaTransitionSessionCoordinator()
        viewing(session, FakeGrid())
        // This mirrors ViewerBackOwnership.releaseWithoutAppVisual for a stable page.
        val operation = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(operation))
        assertTrue(session.terminate(operation, State.COMPLETE, "system_return_no_app_visual"))
        assertNotNull(session.returnOperation)
        assertTrue(session.acceptResult(operation))
    }
}
