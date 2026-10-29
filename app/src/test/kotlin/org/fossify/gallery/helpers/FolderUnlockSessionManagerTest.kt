package org.fossify.gallery.helpers

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderUnlockSessionManagerTest {
    private val root = "/storage/emulated/0/Pictures/PrivateA"

    @After fun tearDown() {
        FolderUnlockSessionManager.revokeAll()
    }

    @Test fun sessionAllowsSameFolderMediaAndSlideshowItems() {
        val token = FolderUnlockSessionManager.createSession(root)
        val locked = { p: String -> p == root }
        assertTrue(FolderUnlockSessionManager.isFolderAuthorized(token, root))
        assertTrue(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/a.jpg", locked))
        assertTrue(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/movie.mp4", locked))
        assertTrue(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/child/b.png", locked))
    }

    @Test fun sessionDoesNotAuthorizeOtherFoldersOrNestedLocks() {
        val token = FolderUnlockSessionManager.createSession(root)
        val locked = { p: String -> p == root || p == "$root/child" }
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(
            token, "/storage/emulated/0/Pictures/PrivateA2/a.jpg", locked))
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(
            token, "/storage/emulated/0/Pictures/PrivateB/a.jpg", locked))
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/child/a.jpg", locked))
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/../PrivateB/a.jpg", locked))
    }

    @Test fun revokedAndForgedSessionsNeverAuthorizeMedia() {
        val token = FolderUnlockSessionManager.createSession(root)
        val locked = { p: String -> p == root }
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized("forged", "$root/a.jpg", locked))
        FolderUnlockSessionManager.revokeSession(token)
        assertFalse(FolderUnlockSessionManager.isFolderAuthorized(token, root))
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(token, "$root/a.jpg", locked))
    }

    @Test fun twoUnlocksHaveIndependentLifetime() {
        val first = FolderUnlockSessionManager.createSession(root)
        val second = FolderUnlockSessionManager.createSession(root)
        assertNotEquals(first, second)
        FolderUnlockSessionManager.revokeSession(first)
        assertFalse(FolderUnlockSessionManager.isFolderAuthorized(first, root))
        assertTrue(FolderUnlockSessionManager.isFolderAuthorized(second, root))
    }

    @Test fun batchAuthorizationMatchesIndividualChecksAndKeepsNestedBoundaries() {
        val token = FolderUnlockSessionManager.createSession(root)
        val protected = setOf(root, "$root/child")
        val paths = listOf(
            "$root/a.jpg",
            "$root/child/b.png",
            "/storage/emulated/0/Pictures/PrivateA2/other.jpg"
        )
        val batch = FolderUnlockSessionManager.authorizedPaths(token, paths) { it in protected }
        assertEquals(setOf("$root/a.jpg"), batch)
        val individual = paths.filter { path ->
            FolderUnlockSessionManager.isMediaAuthorized(token, path) { candidate -> candidate in protected }
        }.toSet()
        assertEquals(individual, batch)
    }
}
