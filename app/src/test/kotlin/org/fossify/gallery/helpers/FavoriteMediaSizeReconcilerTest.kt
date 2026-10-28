package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteMediaSizeReconcilerTest {
    private fun medium(path: String, size: Long) = Medium(
        null, path.substringAfterLast('/'), path, path.substringBeforeLast('/'),
        1000L, 1000L, size, 0, 0, false, 0L, 0L
    )

    @Test
    fun updatesMatchingFavoritesAcrossFoldersWithoutChangingMissingItems() {
        val first = medium("/Pictures/A.jpg", 1L)
        val second = medium("/Movies/B.mp4", 2L)
        val missing = medium("/Downloads/missing.png", 3L)
        val indexed = hashMapOf(
            "/Pictures" to arrayListOf(medium(first.path, 123L)),
            "/Movies" to arrayListOf(medium(second.path, 456L))
        )

        FavoriteMediaSizeReconciler.apply(listOf(first, second, missing), indexed)

        assertEquals(123L, first.size)
        assertEquals(456L, second.size)
        assertEquals(3L, missing.size)
    }

    @Test
    fun respectsCaseSensitivePathsAndDoesNotAlterUnmatchedFavorite() {
        val original = medium("/Pictures/Photo.JPG", 7L)
        val indexed = hashMapOf(
            "/Pictures" to arrayListOf(medium("/Pictures/photo.JPG", 999L))
        )
        FavoriteMediaSizeReconciler.apply(listOf(original), indexed)
        assertEquals(7L, original.size)
    }

    @Test
    fun emptyIndexesDoNotChangeSizes() {
        val original = medium("/Pictures/Keep.jpg", 8L)
        FavoriteMediaSizeReconciler.apply(listOf(original), emptyMap())
        assertEquals(8L, original.size)
    }
}
