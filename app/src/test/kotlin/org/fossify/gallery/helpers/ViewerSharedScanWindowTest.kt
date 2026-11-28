package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ViewerSharedScanWindowTest {
    @Test fun partialWindowStaysBoundedAndStopsAtAHole() {
        val order = (0 until 10_000).map { it.toString() }
        val discovered = order.associateWith { it }.toMutableMap()
        discovered.remove("5010")
        val window = ViewerSharedScanWindow.extend(order, discovered, "5000")
        assertTrue(window.size <= ViewerCommittedWindow.RADIUS * 2 + 1)
        assertEquals((5000 - ViewerCommittedWindow.RADIUS until 5010).map { it.toString() }, window)
        assertFalse(window.contains("5011"))
    }

    @Test fun fillingTenThousandFilesDoesNotRepublishOncePerFile() {
        val order = (0 until 10_000).map { it.toString() }
        val discovered = LinkedHashMap<String, String>()
        var publishes = 0
        var last = emptyList<String>()
        order.forEach { item ->
            discovered[item] = item
            val next = ViewerSharedScanWindow.extend(order, discovered, "5000")
            if (next.isNotEmpty() && next != last) {
                publishes++
                last = next
                assertTrue(next.size <= ViewerCommittedWindow.RADIUS * 2 + 1)
            }
        }
        assertEquals(ViewerCommittedWindow.RADIUS + 1, publishes)
        assertEquals(ViewerCommittedWindow.RADIUS * 2 + 1, last.size)
        assertFalse(last.contains("0"))
        assertFalse(last.contains("9999"))
    }

    @Test fun subscriptionDoesNotReplaceTheGridProducerOrTheExecutorBounds() {
        val viewer = File("src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt").readText()
        val scan = viewer.substringAfter("fun scanRemaining()").substringBefore("var openingWindowInstalled")
        assertTrue(scan.contains("RunningFolderScan.current"))
        assertTrue(scan.contains("addFolderSubscriber"))
        assertTrue(scan.contains("executeOnExecutor(org.fossify.gallery.helpers.GalleryMediaScanExecutor)"))
        assertTrue(scan.contains("RejectedExecutionException"))
        val favorites = viewer.substringAfter("fun loadCompleteFavoritesFromScan").substringBefore("retryViewerScan = { loadCompleteFavoritesFromScan")
        assertTrue(favorites.contains("THREAD_POOL_EXECUTOR"))
        assertFalse(favorites.contains("RunningFolderScan"))
        val executor = File("src/main/kotlin/org/fossify/gallery/helpers/GalleryMediaScanExecutor.kt").readText()
        assertTrue(executor.contains("workers: Int = 2, queued: Int = 4"))
        val task = File("src/main/kotlin/org/fossify/gallery/asynctasks/GetMediaAsynctask.kt").readText()
        assertTrue(task.contains("GalleryScanState.CANCELLED"))
        assertTrue(task.contains("Cancellation is an expected scan outcome. Do not rethrow it from"))
    }
}
