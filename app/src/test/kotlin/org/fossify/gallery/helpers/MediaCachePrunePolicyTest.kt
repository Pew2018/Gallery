package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCachePrunePolicyTest {
    private val previous = "/storage/emulated/0/Pictures/image.jpg"

    private fun mayPrune(
        latest: Set<String> = emptySet(),
        favorites: Boolean = false,
        readable: Boolean = true,
        exists: Boolean = false,
    ) = MediaCachePrunePolicy.canPrune(previous, latest, favorites, readable, exists)

    @Test fun onlyConfirmedAbsentNonFavoriteCanBePruned() {
        assertTrue(mayPrune())
        assertFalse(mayPrune(latest = setOf(previous)))
        assertFalse(mayPrune(exists = true))
    }

    @Test fun temporaryPermissionLossNeverPurgesCache() {
        assertFalse(mayPrune(readable = false))
        assertFalse(mayPrune(readable = false, exists = true))
    }

    @Test fun favoriteRelationshipNeverDependsOnScanCompleteness() {
        assertFalse(mayPrune(favorites = true))
        assertFalse(mayPrune(favorites = true, latest = setOf(previous)))
    }

    @Test fun directoryMetadataSurvivesEmptyAndInaccessibleScans() {
        assertFalse(MediaCachePrunePolicy.canPruneDirectory(
            "/Pictures/empty", parentReadable = true, directoryExists = true))
        assertFalse(MediaCachePrunePolicy.canPruneDirectory(
            "/mnt/removed/album", parentReadable = false, directoryExists = false))
        assertTrue(MediaCachePrunePolicy.canPruneDirectory(
            "/Pictures/gone", parentReadable = true, directoryExists = false))
    }

    @Test fun emptyOrMalformedPathIsNotPruned() {
        assertFalse(MediaCachePrunePolicy.canPrune(
            "", emptySet(), false, true, false))
    }
}
