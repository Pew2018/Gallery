package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
