package org.fossify.gallery.helpers

import org.fossify.commons.helpers.*
import org.fossify.gallery.models.Medium
import android.app.Application
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
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

    @Test fun lateCacheCannotReplaceIndexAndScanCanCorrectExistingMetadata() {
        val preview = MediaPagePreview()
        val first = medium(2)
        val second = medium(1)
        preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(second))
        assertEquals(setOf(first, second), preview.add(DisplayedSourceStage.INDEX_PREVIEW, listOf(first, second))!!.toSet())
        assertNull(preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(medium(4))))
        val corrected = second.copy(taken = 5)
        val third = medium(3)
        assertEquals(setOf(first, corrected, third),
            preview.add(DisplayedSourceStage.SCAN_PARTIAL, listOf(corrected, third))!!.toSet())
        assertNull(preview.add(DisplayedSourceStage.SCAN_PARTIAL, listOf(third, corrected)))
    }

    @Test fun datePreviewsInsertAheadInBothDirectionsAndKeepPathTieBreak() {
        for (descending in listOf(false, true)) {
            val sorting = SORT_BY_DATE_TAKEN or if (descending) SORT_DESCENDING else 0
            val preview = MediaPagePreview()
            val order = StagedMediaOrder(24)
            val fetcher = MediaFetcher(RuntimeEnvironment.getApplication())
            preview.add(DisplayedSourceStage.CACHE_PREVIEW, listOf(medium(2), medium(3)))
            val first = if (descending) medium(5) else medium(1)
            val tied = medium(4).copy(taken = first.taken)
            val known = preview.add(DisplayedSourceStage.SCAN_PARTIAL, listOf(first, tied))!!
            order.sort(known, sorting, fetcher)
            assertEquals(if (descending) listOf(first, tied, medium(3), medium(2))
                else listOf(first, tied, medium(2), medium(3)), known)
        }
    }

    @Test fun stagedOrderUsesExistingMetadataSortModesAndKeepsRandomOrderThroughCompletion() {
        val fetcher = MediaFetcher(RuntimeEnvironment.getApplication())
        val order = StagedMediaOrder(24)
        val cache = arrayListOf(medium(2), medium(10))
        val complete = arrayListOf(medium(8), medium(10), medium(2), medium(1))
        val modes = listOf(SORT_BY_NAME or SORT_USE_NUMERIC_VALUE, SORT_BY_PATH, SORT_BY_SIZE, SORT_BY_DATE_MODIFIED)
        for (sorting in modes) {
            val expected = ArrayList(complete).also { fetcher.sortMedia(it, sorting) }
            assertEquals(expected, ArrayList(complete).also { order.sort(it, sorting, fetcher) })
        }
        order.sort(cache, SORT_BY_RANDOM, fetcher)
        order.sort(complete, SORT_BY_RANDOM, fetcher)
        assertEquals(cache, complete.filter { it in cache })
        val reorderedInput = ArrayList(complete.reversed())
        order.sort(reorderedInput, SORT_BY_RANDOM, fetcher)
        assertEquals(complete, reorderedInput)
    }

    @Test fun cachedPreviewSelectsTheConfiguredPrefixFromTheWholeCatalog() {
        val fetcher = MediaFetcher(RuntimeEnvironment.getApplication())
        val rows = (1..5_000).map { id ->
            Medium().apply {
                name = "IMG_${(5_001 - id) % 113}.jpg"
                path = "/album/segment${id % 17}/$id-$name"
                modified = ((id * 37) % 5_000).toLong()
                taken = if (id % 11 == 0) 0L else ((id * 97) % 4_997).toLong()
                size = ((id * 53) % 4_999 + 1).toLong()
            }
        }
        val modes = listOf(
            SORT_BY_DATE_TAKEN, SORT_BY_DATE_TAKEN or SORT_DESCENDING,
            SORT_BY_DATE_MODIFIED, SORT_BY_DATE_MODIFIED or SORT_DESCENDING,
            SORT_BY_NAME, SORT_BY_NAME or SORT_DESCENDING,
            SORT_BY_NAME or SORT_USE_NUMERIC_VALUE,
            SORT_BY_PATH, SORT_BY_PATH or SORT_DESCENDING,
            SORT_BY_PATH or SORT_USE_NUMERIC_VALUE,
            SORT_BY_SIZE, SORT_BY_SIZE or SORT_DESCENDING
        )
        for (sorting in modes) {
            val expected = ArrayList(rows.map { it.copy(taken = if (it.taken > 0L) it.taken else it.modified) })
            fetcher.sortMedia(expected, sorting)
            val actual = CachedMediaPreviewOrder.order(rows, sorting, fetcher).take(48)
            assertEquals("cache preview must be the configured prefix for $sorting", expected.take(48), actual)
        }
        val randomA = CachedMediaPreviewOrder.order(rows, SORT_BY_RANDOM, fetcher).take(48)
        val randomB = CachedMediaPreviewOrder.order(rows.reversed(), SORT_BY_RANDOM, fetcher).take(48)
        assertEquals("random cache candidates must not inherit Room row order", randomA, randomB)
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

    private fun medium(id: Int) = Medium().apply {
        name = "$id.jpg"
        path = "/album/$name"
        taken = id.toLong()
        modified = (20 - id).toLong()
        size = id.toLong()
    }
}
