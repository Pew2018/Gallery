package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoViewerCatalogCoordinatorTest {
    @Test fun fivePreviewItemsNeverProveEitherEnd() {
        val state = PhotoViewerCatalogCoordinator()
        assertEquals(PhotoViewerCatalogCoordinator.State.LOADING, state.state)
        assertTrue(state.request("4", true))
        state.publish(false)
        assertNull(state.destination((0..4).map { "$it" }, "4"))
        assertNotNull(state.pending)
        assertEquals(PhotoViewerCatalogCoordinator.Boundary.UNKNOWN, state.after)
        state.publish(false)
        assertEquals(5, state.destination((0 until 120).map { "$it" }, "4"))
        assertNull(state.pending)
    }

    @Test fun completeUpgradeResolvesDirectionBeforeClearingWaitAndUsesLivePath() {
        val state = PhotoViewerCatalogCoordinator()
        assertTrue(state.request("2502", true))
        state.publish(true)
        assertEquals(2503, state.destination((0 until 5000).map { "$it" }, "2502"))
        assertFalse(state.request("4999", true))
        assertEquals(PhotoViewerCatalogCoordinator.State.COMPLETE, state.state)
    }

    @Test fun backwardsWaitDirectionChangeUserMovementAndInvalidation() {
        val state = PhotoViewerCatalogCoordinator()
        state.publish(false)
        assertTrue(state.request("2500", true))
        assertTrue(state.request("2500", false))
        assertEquals(19, state.destination((2480..2520).map { "$it" }, "2500"))
        assertTrue(state.request("2500", true))
        assertNull(state.destination((2480..2520).map { "$it" }, "2501"))
        assertNull(state.pending)
        state.invalidate()
        assertFalse(state.request("2500", true))
        assertEquals(PhotoViewerCatalogCoordinator.State.INVALID, state.state)
    }

    @Test fun actualEndStopsWaitingButOtherSideStillHasMore() {
        val state = PhotoViewerCatalogCoordinator()
        state.publish(false, after = PhotoViewerCatalogCoordinator.Boundary.HAS_MORE)
        assertTrue(state.request("4", true))
        state.publish(false, after = PhotoViewerCatalogCoordinator.Boundary.END)
        assertNull(state.destination((0..4).map { "$it" }, "4"))
        assertNull(state.pending)
        assertFalse(state.request("4", true))
        assertTrue(state.request("0", false))
    }
}
