package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test

class MediaPagePreviewTest {
    @Test fun firstPublicationFillsViewportOrMeetsWaitBudget() {
        val cadence = MediaPreviewCadence(24, 0)
        assertTrue(cadence.shouldPublish(1, 1))
        assertFalse(cadence.shouldPublish(23, 99))
        assertTrue(cadence.shouldPublish(24, 100))
        assertFalse(cadence.shouldPublish(25, 101))
        val sparse = MediaPreviewCadence(24, 0)
        assertTrue(sparse.shouldPublish(1, 0))
        assertTrue(sparse.shouldPublish(2, 150))
    }

    @Test fun fiveThousandCandidatesHaveBoundedPublicationWork() {
        val cadence = MediaPreviewCadence(24, 0)
        val published = (1..5000).filter { cadence.shouldPublish(it, it.toLong()) }
        assertEquals(listOf(1, 24), published.take(2))
        assertTrue(published.size < 10)
        assertTrue(published.sum() < 10_000)
    }

    @Test fun lateCacheCannotReplaceIndexAndScanAppendsOnlyNewIdentities() {
        val preview = MediaPagePreview()
        val first = medium(2)
        val second = medium(1)
        assertEquals(listOf(first, second), preview.add(DisplayedSourceStage.INDEX_PREVIEW, listOf(first, second)))
        assertNull(preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(medium(4))))
        val third = medium(3)
        assertEquals(listOf(first, second, third), preview.add(DisplayedSourceStage.SCAN_PARTIAL, listOf(second, third)))
        assertNull(preview.add(DisplayedSourceStage.SCAN_PARTIAL, listOf(third, second)))
    }

    @Test fun smallCacheGrowsAndKeepsExistingOrderAcrossDiscovery() {
        val preview = MediaPagePreview()
        val first = medium(10)
        preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(first))
        val added = (0..4999).filter { it != 10 }.map(::medium)
        var latest = arrayListOf(first)
        added.chunked(250).forEach { latest = preview.add(DisplayedSourceStage.SCAN_PARTIAL, it + first)!! }
        assertEquals(listOf(first.path) + added.map { it.path }, latest.map { it.path })
    }

    @Test fun onlyAnUnchangedPrefixCanBypassDiff() {
        val first = medium(1)
        val second = medium(2)
        assertTrue(MediaUpdatePolicy.isContentAppend(listOf(first), listOf(first.copy(mediaStoreId = 20), second)))
        assertFalse(MediaUpdatePolicy.isContentAppend(listOf(first), listOf(first.copy(size = 3), second)))
        assertFalse(MediaUpdatePolicy.isContentAppend(listOf(first), listOf(second, first)))
        assertFalse(MediaUpdatePolicy.isContentAppend(listOf(first), listOf(first)))
    }

    private fun medium(id: Int) = Medium().apply { path = "/album/$id.jpg"; taken = id.toLong() }
}
