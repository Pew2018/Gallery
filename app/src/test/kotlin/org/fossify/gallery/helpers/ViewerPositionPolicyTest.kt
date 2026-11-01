package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerPositionPolicyTest {
    private fun medium(name: String) = Medium(null, name, "/gallery/$name", "/gallery", 1, 1, 1, 1, 0, false, 0, 0)

    @Test fun asynchronousFullListKeepsTheCurrentlyViewedPathInsteadOfTheInitialClick() {
        val media = listOf(medium("first.jpg"), medium("clicked.jpg"), medium("current.jpg"), medium("last.jpg"))
        assertEquals(2, ViewerPositionPolicy.resolve(media, "/gallery/current.jpg", 1))
    }

    @Test fun removedCurrentPathFallsBackByPreviousIndexWithoutJumpingToInitialClick() {
        val media = listOf(medium("first.jpg"), medium("clicked.jpg"), medium("last.jpg"))
        assertEquals(1, ViewerPositionPolicy.resolve(media, "/gallery/removed.jpg", 1))
    }

    @Test fun initialSelectionUsesExistingPositionLookupFallback() {
        assertEquals(-1, ViewerPositionPolicy.resolve(listOf(medium("other.jpg")), "/gallery/clicked.jpg", -1))
    }
}
