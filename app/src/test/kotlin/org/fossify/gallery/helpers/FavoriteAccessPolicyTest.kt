package org.fossify.gallery.helpers

import org.fossify.gallery.models.ThumbnailSection
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FavoriteAccessPolicyTest {
    private val locks = linkedMapOf<String, FavoriteAccessSessionManager.Protection>()
    private val pinA = FavoriteAccessSessionManager.Protection(1, "hash-A")
    private fun snapshot(path: String) = FavoriteAccessSessionManager.captureSnapshot(listOf(path), locks::get)

    private fun session(protection: (String) -> FavoriteAccessSessionManager.Protection? = { null }): String {
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf("/allowed/a.jpg", "/allowed/v.mp4"), protection)
        return FavoriteAccessSessionManager.createSession("album", snapshot, emptySet(), protection)!!
    }
    @After fun cleanup() { FavoriteAccessSessionManager.revokeAll() }

    @Test fun cacheScanPagerAndSlideshowUseTheSameScope() {
        val token = session()
        val cachedOrScanned = listOf("/allowed/a.jpg", "/other/secret.jpg", "/allowed/v.mp4")
        repeat(4) {
            assertEquals(listOf("/allowed/a.jpg", "/allowed/v.mp4"),
                FavoriteAccessPolicy.authorizedItems(token, cachedOrScanned) { it })
        }
    }

    @Test fun deferredScanAfterExitPublishesNoMediaEvenWithAnOldRequestAndToken() {
        val token = session()
        val requests = FavoriteMediaLoadRequests()
        val request = requests.begin()
        val published = mutableListOf<String>()
        val callback = {
            if (requests.isCurrent(request)) published.addAll(
                FavoriteAccessPolicy.authorizedItems(token, listOf("/allowed/a.jpg", "/other/secret.jpg")) { it })
        }
        FavoriteAccessSessionManager.revokeSession(token)
        callback()
        assertTrue(published.isEmpty())
        assertNull(FavoriteAccessSessionManager.getAllowedPaths(token))
    }

    @Test fun supersededRequestCannotPublishOrResurrectAnAlbum() {
        val token = session()
        val requests = FavoriteMediaLoadRequests()
        val old = requests.begin()
        requests.invalidate()
        val published = mutableListOf<String>()
        if (requests.isCurrent(old)) published.addAll(FavoriteAccessPolicy.authorizedItems(token, listOf("/allowed/a.jpg")) { it })
        assertTrue(published.isEmpty())
    }

    @Test fun canceledOrForgedAuthorizationCannotPublishEvenPublicCachedItemsAsAnAlbum() {
        for (token in listOf(null, "forged")) {
            assertTrue(FavoriteAccessPolicy.authorizedItems(token, listOf("/allowed/a.jpg", "/other/secret.jpg")) { it }.isEmpty())
        }
    }

    @Test fun batchValidationKeepsOnlyCapturedPathsAndCanRequireTheClickedMedia() {
        val token = session()
        val batch = FavoriteAccessPolicy.validateItems(
            token,
            listOf("/allowed/a.jpg", "/allowed/v.mp4", "/added/later.jpg"),
            { it },
            requiredPaths = listOf("/allowed/v.mp4")
        )
        assertEquals(listOf("/allowed/a.jpg", "/allowed/v.mp4"), batch?.items)
        assertTrue(FavoriteAccessSessionManager.isValidationCurrent(batch?.validation))
    }

    @Test fun narrowingOrChangingTheProtectionInvalidatesAnEarlierBatch() {
        val token = session()
        val batch = FavoriteAccessPolicy.validateItems(token, listOf("/allowed/a.jpg"), { it })!!
        FavoriteAccessSessionManager.revokePath("/allowed/a.jpg")
        assertFalse(FavoriteAccessSessionManager.isValidationCurrent(batch.validation))

        val changed = session(locks::get)
        val changedBatch = FavoriteAccessPolicy.validateItems(changed, listOf("/allowed/a.jpg"), { it })!!
        locks["/allowed"] = pinA
        assertTrue(FavoriteAccessPolicy.authorizedItems(changed, listOf("/allowed/a.jpg"), { it }).isEmpty())
        assertFalse(FavoriteAccessSessionManager.isValidationCurrent(changedBatch.validation))
    }

    @Test fun disabledPasswordShowsOnlyUnprotectedFavoritesAndKeepsSavedRows() {
        locks["/private"] = pinA
        val paths = listOf("/public/photo.jpg", "/private/secret.jpg")
        val all = FavoriteAccessSessionManager.captureSnapshot(paths, locks::get)
        val visible = FavoriteAccessPolicy.unprotectedSnapshot(all)
        assertEquals(setOf("/public/photo.jpg"), visible.entries.keys)
        assertEquals(setOf("/public/photo.jpg", "/private/secret.jpg"), all.entries.keys)
        assertTrue(FavoriteAccessPolicy.hasProtectedFavorites(all))
        val token = FavoriteAccessSessionManager.createSession("owner", visible, emptySet(), locks::get)!!
        val published = FavoriteAccessPolicy.authorizedItems(token, paths) { it }
        assertEquals(listOf("/public/photo.jpg"), published)
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, "/private/secret.jpg"))
    }

    @Test fun removingSourceProtectionAllowsFreshNoPasswordSnapshotToRestoreFavorite() {
        val path = "/source/photo.jpg"
        locks["/source"] = pinA
        assertTrue(FavoriteAccessPolicy.unprotectedSnapshot(snapshot(path)).entries.isEmpty())
        locks.clear()
        assertEquals(setOf(path), FavoriteAccessPolicy.unprotectedSnapshot(snapshot(path)).entries.keys)
    }

    @Test fun authorizedGroupedAlbumKeepsOnlyNonMediaSectionHeadersWithoutPaths() {
        val token = session()
        val headers = listOf(ThumbnailSection("Today"))
        assertEquals(headers, FavoriteAccessPolicy.authorizedItems(token, headers) { null })
        assertTrue(FavoriteAccessPolicy.authorizedItems("forged", headers) { null }.isEmpty())
    }
    @Test fun viewerBatchCapturesOnceAndPublicationPerformsNoFilesystemChecks() {
        val calls = ArrayList<String>()
        val protection: (String) -> FavoriteAccessSessionManager.Protection? = { calls.add(it); null }
        val token = session(protection)
        calls.clear()
        val batch = FavoriteAccessPolicy.validateViewerItems(token,
            listOf("/allowed/a.jpg", "/allowed/v.mp4", "/outside/secret.jpg"), { it }, "/allowed/a.jpg")!!
        assertEquals(listOf("/allowed/a.jpg", "/allowed/v.mp4"), batch.items)
        assertEquals(1, calls.count { it == "/allowed" })
        val count = calls.size
        assertTrue(FavoriteAccessSessionManager.isPublicationCurrent(batch.validation))
        assertEquals(count, calls.size)
        FavoriteAccessSessionManager.revokeSession(token)
        assertFalse(FavoriteAccessSessionManager.isPublicationCurrent(batch.validation))
    }

    @Test fun viewerBatchRequiresTheSelectedMemberAndStillRejectsChangedLocks() {
        val token = session(locks::get)
        assertNull(FavoriteAccessPolicy.validateViewerItems(token, listOf("/allowed/a.jpg"), { it }, "/other/a.jpg"))
        locks["/allowed"] = pinA
        assertNull(FavoriteAccessPolicy.validateViewerItems(token, listOf("/allowed/a.jpg"), { it }, "/allowed/a.jpg"))
        assertFalse(FavoriteAccessSessionManager.isActive(token))
    }

}

