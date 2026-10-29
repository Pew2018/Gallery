package org.fossify.gallery.helpers

import org.fossify.gallery.models.ThumbnailSection
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FavoriteAccessPolicyTest {
    private fun session(): String {
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf("/allowed/a.jpg", "/allowed/v.mp4")) { null }
        return FavoriteAccessSessionManager.createSession("album", snapshot, emptySet()) { null }!!
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

    @Test fun authorizedGroupedAlbumKeepsOnlyNonMediaSectionHeadersWithoutPaths() {
        val token = session()
        val headers = listOf(ThumbnailSection("Today"))
        assertEquals(headers, FavoriteAccessPolicy.authorizedItems(token, headers) { null })
        assertTrue(FavoriteAccessPolicy.authorizedItems("forged", headers) { null }.isEmpty())
    }
}
