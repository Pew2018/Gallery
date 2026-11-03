package org.fossify.gallery.helpers

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.models.Directory
import org.junit.Assert.*
import org.junit.Test

class GalleryRefreshRecoveryTest {
    @Test fun failedAndTimedOutScansDoNotConsumeOrRestartChanges() {
        val gate = MediaChangeRetryGate()
        gate.scanStarted()
        gate.mediaChangedDuringScan()
        assertFalse(gate.scanFinished(success = false))
        assertTrue(gate.hasPending())
        gate.scanStarted() // explicit retry covers previously deferred changes
        assertFalse(gate.hasPending())
        gate.mediaChangedDuringScan()
        assertTrue(gate.scanFinished(success = true))
        assertFalse(gate.scanFinished(success = true))
    }

    @Test fun metadataChangesCoalesceAndLateResultsCannotApply() {
        val gate = DirectoryMetadataRebindGate()
        gate.enqueue(listOf("/a", "/a", "/b"))
        val first = gate.begin()!!
        assertEquals(setOf("/a", "/b"), first.paths)
        assertNull(gate.begin())
        gate.enqueue(listOf("/c"))
        assertTrue(gate.finish(first.id, applied = true))
        val second = gate.begin()!!
        assertEquals(setOf("/c"), second.paths)
        assertFalse(gate.finish(first.id, applied = true))
        gate.cancel()
        assertFalse(gate.isCurrent(second.id))
        assertFalse(gate.isLatest(second.id))
        assertEquals(setOf("/c"), gate.begin()!!.paths)
    }

    @Test fun metadataFailureAndTimeoutRetainPathsWithoutImmediateRetry() {
        val gate = DirectoryMetadataRebindGate()
        gate.enqueue(listOf("/hidden", "/normal"))
        val first = gate.begin()!!
        assertTrue(gate.finish(first.id, applied = false))
        assertTrue(gate.hasPending())
        val second = gate.begin()!!
        assertEquals(first.paths, second.paths)
        gate.cancel()
        assertFalse(gate.finish(second.id, applied = true))
    }

    private fun album(path: String, cover: String = "", count: Int = 5) =
        Directory().apply { this.path = path; name = "album"; tmb = cover; mediaCnt = count }

    private fun reconcile(cached: List<Directory>, fav: Long? = 1, deleted: Long? = 1,
                          showBin: Boolean = true, locked: Boolean = false) =
        RootVirtualAlbumPolicy.reconcile(
            cached, fav, deleted, showBin, locked, false, "Favorites", "Recycle", LOCATION_INTERNAL
        )

    @Test fun disabledRecycleBinIsNeverReaddedFromPreviousRows() {
        val output = reconcile(listOf(album("/a"), album(RECYCLE_BIN)), showBin = false)
        assertFalse(output.any { it.path == RECYCLE_BIN })
        assertTrue(output.any { it.path == FAVORITES })
    }

    @Test fun deletedFavoritesDisappearEvenIfCachedRowStillExists() {
        assertFalse(reconcile(listOf(album(FAVORITES, "/old.jpg")), fav = 0)
            .any { it.path == FAVORITES })
    }

    @Test fun protectedFavoriteCannotLeakOldThumbnailOrCount() {
        val entry = reconcile(listOf(album(FAVORITES, "/private.jpg", 17)), locked = true)
            .single { it.path == FAVORITES }
        assertEquals("", entry.tmb)
        assertEquals(0, entry.mediaCnt)
    }

    @Test fun hiddenFavoriteCoverIsNotReusedWhileHiddenMediaIsDisabled() {
        val entry = reconcile(listOf(album(FAVORITES, "/abc/.secret.jpg")))
            .single { it.path == FAVORITES }
        assertEquals("", entry.tmb)
    }
}
