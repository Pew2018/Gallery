package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoProfileCatalogCheckpointTest {
    private fun medium(path: String, mediaStoreId: Long) =
        Medium(null, path.substringAfterLast('/'), path, path.substringBeforeLast('/'), 1, 1, 1, 1, 0, false, 0, mediaStoreId)

    @Test fun onlyFullyIndexedCatalogsCanSkipSupplementRescan() {
        assertTrue(PhotoProfileCatalogCheckpoint.hasReliableMediaStoreIdentity(listOf(medium("/a.jpg", 1), medium("/b.jpg", 2))))
        assertFalse(PhotoProfileCatalogCheckpoint.hasReliableMediaStoreIdentity(listOf(medium("/a.jpg", 1), medium("/unindexed.jpg", 0))))
    }
}
