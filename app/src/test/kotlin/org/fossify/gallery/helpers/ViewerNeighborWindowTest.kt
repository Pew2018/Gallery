package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ViewerNeighborWindowTest {
    private fun medium(index: Int) = Medium(
        null, "$index.jpg", "/gallery/$index.jpg", "/gallery", index.toLong(), index.toLong(),
        1L, 1, 0, false, 0L, index.toLong()
    )

    @Test
    fun extractsOnlyFiveItemsFromLargeDisplayedOrder() {
        val input = (0 until 10_000).map(::medium)
        val result = ViewerNeighborWindow.captureAround(input, "/gallery/5000.jpg")!!
        assertEquals(5, result.size)
        assertEquals(listOf(4998, 4999, 5000, 5001, 5002), result.map { it.name.removeSuffix(".jpg").toInt() })
    }

    @Test
    fun skipsSectionsAndPreservesRandomizedDisplayedOrder() {
        val input: List<ThumbnailItem> = listOf(medium(8), ThumbnailSection("today"), medium(3), medium(8), medium(5))
        val result = ViewerNeighborWindow.captureAround(input, "/gallery/3.jpg")!!
        assertEquals(listOf("8.jpg", "3.jpg", "5.jpg"), result.map { it.name })
    }

    @Test
    fun firstAndLastItemsProduceOneSidedWindows() {
        val input: List<ThumbnailItem> = (0..3).map(::medium)
        assertEquals(listOf("0.jpg", "1.jpg", "2.jpg"),
            ViewerNeighborWindow.captureAround(input, "/gallery/0.jpg")!!.map { it.name })
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg"),
            ViewerNeighborWindow.captureAround(input, "/gallery/3.jpg")!!.map { it.name })
    }

    @Test
    fun absentSelectionFallsBackWithoutGuessingNeighbors() {
        assertNull(ViewerNeighborWindow.captureAround(listOf(medium(1), medium(2)), "/gallery/missing.jpg"))
    }

    @Test fun openingPromotionIsTheNeighborRadiusNotTheCommittedWindow() {
        val input = (0 until 10_000).map(::medium)
        val selected = "/gallery/5000.jpg"
        val promoted = ViewerNeighborWindow.captureAround(input, selected)!!.map { it.path }.filter { it != selected }
        assertEquals(listOf("/gallery/4998.jpg", "/gallery/4999.jpg", "/gallery/5001.jpg", "/gallery/5002.jpg"), promoted)
        assertTrue(promoted.size <= ViewerNeighborWindow.NEIGHBOR_RADIUS * 2)
        assertFalse(promoted.contains("/gallery/${5000 - ViewerCommittedWindow.RADIUS}.jpg"))
        val adapter = File("src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt").readText()
        val promote = adapter.substringAfter("internal fun promoteQueuedThumbnail").substringBefore("private fun setupThumbnail")
        assertTrue(promote.contains("load.pending == null"))
        assertTrue(promote.contains("thumbnail_request_key"))
        assertTrue(adapter.contains("THUMB_VIEWPORT_QUIET_MS = 48L"))
        assertTrue(adapter.contains("if (!fastScrollActive && (mediumThumbnail.isAttachedToWindow || thumbnailPriority == Priority.HIGH))"))
        val activity = File("src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt").readText()
        assertTrue(activity.substringAfter("private fun openOrdinaryViewer(").substringBefore("private fun viewerLaunchBlocked")
            .contains("promoteQueuedThumbnail"))
        assertTrue(activity.substringAfter("private fun openInViewPager(").contains("promoteQueuedThumbnail"))
    }

    @Test fun localExtractionDoesNotCopyMediumObjectsOrAllocateGallerySizedDeduplicationState() {
        val selected = medium(2)
        val after = medium(3)
        val input = (0 until 10_000).filter { it != 2 }.map(::medium) + listOf(selected, after)
        val result = ViewerNeighborWindow.captureAround(input, selected.path)!!
        assertEquals(listOf("9998.jpg", "9999.jpg", "2.jpg", "3.jpg"), result.map { it.name })
        assertSame(selected, result[2])
    }
}
