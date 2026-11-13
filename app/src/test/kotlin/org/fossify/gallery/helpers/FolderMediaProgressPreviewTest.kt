package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMediaProgressPreviewTest {
    private fun medium(path: String) = Medium(
        null, path.substringAfterLast('/'), path, path.substringBeforeLast('/'),
        1L, 1L, 12L, TYPE_IMAGES, 0, false, 0L, 0L
    )

    @Test fun emptyScopedIndexDoesNotPublishAnEmptyEarlyPreview() {
        assertNull(FolderMediaProgressPreview.fromScopedIndex(emptyList()) { true })
    }

    @Test fun indexedRowsAreCopiedAndRemainExplicitlyPartial() {
        val source = arrayListOf(medium("/camera/a.jpg"))
        val preview = FolderMediaProgressPreview.fromScopedIndex(source) { true }!!

        source.single().path = "/mutated.jpg"

        assertEquals(FolderReadStatus.PARTIAL, preview.status)
        assertEquals("mediastore_index_preview", preview.method)
        assertEquals(mapOf("scan_in_progress" to 1), preview.reasons)
        assertEquals(0, preview.enumerated)
        assertEquals("/camera/a.jpg", preview.media.single().path)
        assertNotSame(source.single(), preview.media.single())
    }

    @Test fun previewAccessGateRemainsLiveUntilDelivery() {
        var authorized = true
        val preview = FolderMediaProgressPreview.fromScopedIndex(listOf(medium("/camera/a.jpg"))) { authorized }!!

        assertTrue(preview.accessCurrent())
        authorized = false
        assertFalse(preview.accessCurrent())
    }

    @Test fun diskProgressRetainsIndexedRowsAndAddsNewlyDiscoveredMediaOnce() {
        val indexedA = medium("/camera/a.jpg")
        val indexedB = medium("/camera/b.jpg")
        val freshA = medium("/camera/a.jpg").apply { modified = 9L }
        val discovered = medium("/camera/c.jpg")
        val progress = FolderMediaReadResult(
            media = arrayListOf(freshA, discovered),
            status = FolderReadStatus.PARTIAL,
            method = "direct",
            enumerated = 3,
            reasons = mapOf("scan_in_progress" to 1),
        )

        val merged = FolderMediaProgressPreview.retainIndexedSnapshot(progress, listOf(indexedA, indexedB))

        assertEquals(listOf("/camera/a.jpg", "/camera/b.jpg", "/camera/c.jpg"), merged.media.map { it.path })
        assertEquals(9L, merged.media.first().modified)
    }
}
