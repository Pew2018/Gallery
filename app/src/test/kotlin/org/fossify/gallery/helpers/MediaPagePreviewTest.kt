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

    @Test fun acceptanceSizeCatalogsHaveBoundedPreviewPublicationWork() {
        for (count in listOf(100, 1_000, 5_000, 10_000)) {
            val cadence = MediaPreviewCadence(24, 0)
            val published = (1..count).filter { cadence.shouldPublish(it, it.toLong()) }
            assertEquals("first candidate must publish immediately at $count records", 1, published.first())
            assertEquals("first viewport batch must publish at $count records", 24, published[1])
            assertTrue("preview cadence must stay bounded at $count records", published.size < 14)
            assertTrue("published prefix must stay bounded at $count records", published.sum() < 20_000)
        }
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

    @Test fun incompleteCacheGrowsWithoutDuplicatesAtAcceptanceCatalogSizes() {
        for (recordCount in listOf(100, 1_000, 5_000, 10_000)) {
            val preview = MediaPagePreview()
            val first = medium(10)
            preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(first))
            val added = (0 until recordCount).filter { it != 10 }.map(::medium)
            var latest = arrayListOf(first)
            added.chunked(250).forEach { latest = preview.add(DisplayedSourceStage.SCAN_PARTIAL, it + first)!! }
            assertEquals("all discovered records must be retained at $recordCount", recordCount, latest.size)
            assertEquals("partial batches must not duplicate paths at $recordCount", recordCount,
                latest.map { it.path }.toSet().size)
            assertEquals("cached prefix must retain its order at $recordCount", first.path, latest.first().path)
        }
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
