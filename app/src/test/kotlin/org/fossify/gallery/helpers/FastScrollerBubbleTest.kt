package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollerBubbleTest {
    @Test
    fun pixelOffsetSelectsVisibleSnapshotPositionForEitherScrollAxis() {
        assertEquals(0, FastScrollerBubble.adapterPositionForOffset(0, 2_000, 500, 100))
        assertEquals(49, FastScrollerBubble.adapterPositionForOffset(750, 2_000, 500, 100))
        assertEquals(99, FastScrollerBubble.adapterPositionForOffset(9_000, 2_000, 500, 100))
        assertEquals(0, FastScrollerBubble.adapterPositionForOffset(250, 500, 500, 100))
        assertEquals(null, FastScrollerBubble.adapterPositionForOffset(0, 0, 0, 0))
        // The mapping consumes the active adapter count, so a filtered search labels
        // its twelve visible results instead of positions in the full catalog.
        assertEquals(11, FastScrollerBubble.adapterPositionForOffset(1_500, 2_000, 500, 12))
    }

    @Test
    fun popupPositionContinuesToResolveDateSectionRowsToMedia() {
        val items = listOf(section("May"), medium(), section("April"), medium())
        val firstDrag = FastScrollerBubble.adapterPositionForOffset(0, 300, 100, items.size)!!
        val laterDrag = FastScrollerBubble.adapterPositionForOffset(200, 300, 100, items.size)!!

        assertEquals(1, FastScrollerBubble.findMediaIndex(items, firstDrag))
        assertEquals(3, FastScrollerBubble.findMediaIndex(items, laterDrag))
    }

    @Test
    fun sectionUsesFirstFollowingMedia() {
        val items = listOf(section("2024"), medium(), medium())

        assertEquals(1, FastScrollerBubble.findMediaIndex(items, 0))
        assertEquals(2, FastScrollerBubble.findMediaIndex(items, 2))
    }

    @Test
    fun trailingOrConsecutiveSectionsUseNearestPreviousMedia() {
        val items = listOf(medium(), section("empty"), section("trailing"))

        assertEquals(0, FastScrollerBubble.findMediaIndex(items, 1))
        assertEquals(0, FastScrollerBubble.findMediaIndex(items, 2))
    }

    @Test
    fun emptyListsAndInvalidPositionsHaveNoMedia() {
        val items: List<ThumbnailItem> = listOf(section("empty"))

        assertEquals(null, FastScrollerBubble.findMediaIndex(emptyList(), 0))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, -1))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, 1))
        assertEquals(null, FastScrollerBubble.findMediaIndex(items, 0))
    }

    @Test
    fun zeroDateFallsBackAndHistoricalNegativeDatesRemainValid() {
        var formatterCalled = false
        val zero = FastScrollerBubble.dateOrFallback(0L, "photo.jpg") {
            formatterCalled = true
            "1970"
        }
        assertEquals("photo.jpg", zero)
        assertFalse(formatterCalled)

        val historical = FastScrollerBubble.dateOrFallback(-315619200000L, "photo.jpg") {
            formatterCalled = true
            "1960"
        }
        assertEquals("1960", historical)
        assertTrue(formatterCalled)
        assertEquals("photo.jpg", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") { "" })
        assertEquals("photo.jpg", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") {
            throw IllegalArgumentException("invalid date")
        })
        assertEquals("2026", FastScrollerBubble.dateOrFallback(1L, "photo.jpg") { "2026" })
    }

    @Test
    fun blankNameUsesFileNameFromPathThenUnknownLabel() {
        assertEquals("photo.jpg", FastScrollerBubble.displayName("", "/DCIM/photo.jpg", "Unknown"))
        assertEquals("Unknown", FastScrollerBubble.displayName("", "/", "Unknown"))
    }

    private fun medium() = Medium()
    private fun section(title: String) = ThumbnailSection(title)
}
