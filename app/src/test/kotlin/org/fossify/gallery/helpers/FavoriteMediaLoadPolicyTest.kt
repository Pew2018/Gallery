package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class FavoriteMediaLoadPolicyTest {
    private data class Item(val path: String, val type: String, val source: String)

    @Test fun fourHundredSavedPathsAreNotTruncatedAndKeepOrdering() {
        val saved = (1..450).map { "/storage/emulated/0/DCIM/img_$it.jpg" }
        val paths = FavoriteMediaLoadPolicy.distinctSavedPaths(saved + saved[0])
        assertEquals(450, paths.size)
        assertEquals(saved, paths)
    }

    @Test fun pathsAreExactCaseSensitiveAndSpecialCharactersArePreserved() {
        val input = listOf("/Pictures/A.JPG", "/Pictures/a.jpg", "/Pictures/a b # 中.jpg", "/Pictures/A.JPG")
        assertEquals(input.dropLast(1), FavoriteMediaLoadPolicy.distinctSavedPaths(input))
    }

    @Test fun indexedAndDirectFileFavoritesAreUnionedWithoutLosingMediaTypes() {
        val files = listOf(Item("/p/a.jpg", "image", "file"), Item("/p/b.gif", "gif", "file"),
            Item("/p/c.raw", "raw", "file"), Item("/p/d.svg", "svg", "file"))
        val indexed = listOf(Item("/p/a.jpg", "image", "store"), Item("/p/e.mp4", "video", "store"))
        val loaded = FavoriteMediaLoadPolicy.mergeByPath(files, indexed) { it.path }
        assertEquals(5, loaded.size)
        assertEquals(setOf("image", "gif", "raw", "svg", "video"), loaded.map { it.type }.toSet())
        assertEquals("store", loaded.first().source)
    }

    @Test fun temporarilyUnavailablePathsRemainSaved() {
        val paths = listOf("/unmounted/card/a.jpg", "/revoked/p/b.jpg", "/missing/c.jpg")
        assertEquals(paths, FavoriteMediaLoadPolicy.distinctSavedPaths(paths))
        assertFalse(MediaCachePrunePolicy.canPrune(paths[0], emptySet(), true, false, false))
        assertFalse(MediaCachePrunePolicy.canPrune(paths[1], emptySet(), true, true, false))
    }

    @Test fun competingLoadsCannotPublishStaleCacheOrScan() {
        val requests = FavoriteMediaLoadRequests()
        val old = requests.begin()
        val current = requests.begin()
        assertFalse(requests.isCurrent(old))
        assertTrue(requests.isCurrent(current))
        requests.invalidate()
        assertFalse(requests.isCurrent(current))
    }
}
