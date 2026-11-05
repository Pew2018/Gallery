package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FolderMediaScanTest {
    private fun medium(path: String) = Medium().apply { this.path = path }

    @Test fun failedFolderDoesNotPreventLaterFoldersAndKeepsOnlyItsOwnCache() {
        val visited = ArrayList<String>()
        val fallback = listOf(medium("/failed/old.jpg"), medium("/good/removed.jpg"), medium("/excluded/private.jpg"))
        val result = FolderMediaScan.load(listOf("/failed", "/good"), fallback, { false }) { folder ->
            visited.add(folder)
            if (folder == "/failed") throw IOException("unmounted")
            arrayListOf(medium("/good/new.jpg"))
        }
        assertEquals(listOf("/failed", "/good"), visited)
        assertEquals(setOf("/failed/old.jpg", "/good/new.jpg"), result.media.map { it.path }.toSet())
        assertEquals(setOf("/failed"), result.failures.keys)
        assertEquals(GalleryScanState.PARTIAL, result.outcome.state)
        assertFalse("a partial folder scan cannot authorize complete-only cache pruning", result.outcome.complete)
        assertNotSame(fallback.first(), result.media.first { it.path == "/failed/old.jpg" })
    }

    @Test fun unreadableSourcesCannotBePublishedAsAnEmptySuccess() {
        try {
            FolderMediaScan.load(listOf("/empty", "/failed"), emptyList(), { false }) {
                if (it == "/failed") throw SecurityException("denied")
                arrayListOf()
            }
            fail("Incomplete empty scan replaced existing content")
        } catch (error: IOException) {
            assertTrue(error.cause is SecurityException)
        }
    }

    @Test fun successfulEmptyLibraryRemainsASuccess() {
        val result = FolderMediaScan.load(listOf("/empty"), listOf(medium("/empty/deleted.jpg")), { false }) { arrayListOf() }
        assertTrue(result.media.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test fun canceledScanCannotPublishItsPartialContent() {
        var canceled = false
        try {
            FolderMediaScan.load(listOf("/a", "/b"), emptyList(), { canceled }) {
                canceled = true
                arrayListOf(medium("/a/photo.jpg"))
            }
            fail("Canceled result was published")
        } catch (_: java.util.concurrent.CancellationException) { }
    }
}
