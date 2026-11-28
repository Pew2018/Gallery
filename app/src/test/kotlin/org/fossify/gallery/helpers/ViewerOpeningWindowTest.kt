package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.*
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ViewerOpeningWindowTest {
    private fun paths(count: Int) = (0 until count).map { "/gallery/$it.jpg" }
    private fun medium(index: Int) = Medium(
        null, "$index.jpg", "/gallery/$index.jpg", "/gallery", 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)

    @Test fun folderWindowStaysBoundedForTwoHundredAndTenThousand() {
        listOf(200, 10_000).forEach { count ->
            val items = paths(count)
            val anchor = items[count / 2]
            val window = ViewerOpeningWindow.committed(items, anchor) { it }
            assertTrue(window.size <= ViewerCommittedWindow.RADIUS * 2 + 1)
            assertEquals(ViewerCommittedWindow.RADIUS * 2 + 1, window.size)
            assertEquals(anchor, window[ViewerCommittedWindow.RADIUS])
            assertFalse(window.size == count)
        }
    }

    @Test fun holeInsideTheWindowStopsTheContiguousRun() {
        val grid = paths(200)
        val anchor = grid[100]
        val window = ViewerOpeningWindow.committed(grid, anchor) { it }
        val gap = grid[110]
        val loaded = window.filterNot { it == gap }
        val published = ViewerGridOrderPolicy.contiguousPartial(grid, loaded, anchor) { it }
        assertEquals(grid.subList(100 - ViewerCommittedWindow.RADIUS, 110), published)
        assertFalse(published.contains(grid[111]))
        assertFalse(published.contains(gap))
    }

    @Test fun missingGridEvidenceKeepsAtMostFiveNeighbors() {
        val neighbors = ViewerNeighborWindow.captureAround(
            (0 until 10_000).map { medium(it) }, "/gallery/5000.jpg")!!
        assertTrue(neighbors.size <= ViewerNeighborWindow.MAX_ITEMS)
        assertEquals(ViewerNeighborWindow.MAX_ITEMS, neighbors.size)
        assertNull(ViewerOpeningWindow.photoPreview(emptyList<String>(), "/gallery/1.jpg") { it })
        assertTrue(ViewerOpeningWindow.committed(emptyList<String>(), "/gallery/1.jpg") { it }.isEmpty())
    }

    @Test fun photoPreviewCapsAtTheValidatedCacheWindow() {
        val preview = paths(CachedMediaPreviewOrder.LIMIT)
        val anchor = preview[40]
        assertEquals(preview, ViewerOpeningWindow.photoPreview(preview, anchor) { it })
        assertNull(ViewerOpeningWindow.photoPreview(paths(10_000), paths(10_000)[5_000]) { it })
        assertNull(ViewerOpeningWindow.photoPreview(preview, "/missing.jpg") { it })
    }

    @Test fun favoritesHandoffCopiesOnlyTheCommittedWindow() {
        val media = (0 until 10_000).map { medium(it) }
        val anchor = media[4_000].path
        val id = ViewerGridOrderStore.offer(
            "favorites", anchor, media, committedWindowOnly = true, revision = 0)!!
        val evidence = ViewerGridOrderStore.takeEvidence(id, "favorites", anchor)!!
        assertEquals(10_000, evidence.paths.size)
        assertEquals(ViewerCommittedWindow.RADIUS * 2 + 1, evidence.candidates.size)
        assertEquals(anchor, evidence.candidates[ViewerCommittedWindow.RADIUS].path)
        assertTrue(evidence.candidates.none { it.path == media[0].path })
    }

    @Test fun overLimitPhotoCandidatesAreNotStored() {
        val media = (0 until 200).map { medium(it) }
        val anchor = media[10].path
        val id = ViewerGridOrderStore.offer(
            "photos", anchor, media, boundedCandidates = media, revision = 0)!!
        val evidence = ViewerGridOrderStore.takeEvidence(id, "photos", anchor)!!
        assertEquals(200, evidence.paths.size)
        assertTrue(evidence.candidates.isEmpty())
    }
}
