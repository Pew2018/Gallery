package org.fossify.gallery.helpers

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AuditFavoriteRevalidationTest {
    private val locks = linkedMapOf<String, FavoriteAccessSessionManager.Protection>()

    @After fun cleanup() {
        FavoriteAccessSessionManager.revokeAll()
        Thread.interrupted()
    }

    private fun session(vararg paths: String): String {
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(paths.toList(), locks::get)
        return FavoriteAccessSessionManager.createSession(
            "owner", snapshot, snapshot.entries.values.flatMap { it.locks.keys }.toSet(), locks::get
        )!!
    }

    @Test fun retainFavoritesEmptySetIsSuccessfulEmptyAuthorization() {
        val token = session("/public/a.jpg")
        FavoriteAccessSessionManager.revokePath("/public/a.jpg")
        assertEquals(emptySet<String>(), FavoriteAccessSessionManager.retainFavorites(token, emptySet()))
        assertTrue(FavoriteAccessSessionManager.isActive(token))
    }

    @Test fun retainFavoritesInterruptedReturnsNullAndLeavesSession() {
        val root = Files.createTempDirectory("fav-interrupt")
        try {
            val path = Files.write(root.resolve("a.jpg"), byteArrayOf(1)).toString()
            val token = session(path)
            Thread.currentThread().interrupt()
            assertNull(FavoriteAccessSessionManager.retainFavorites(token, setOf(path)))
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(FavoriteAccessSessionManager.isActive(token))
        } finally {
            Thread.interrupted()
            root.toFile().deleteRecursively()
        }
    }

    @Test fun retainFavoritesRevokedSessionReturnsNullWithoutActiveToken() {
        val token = session("/public/a.jpg")
        FavoriteAccessSessionManager.revokeSession(token)
        assertNull(FavoriteAccessSessionManager.retainFavorites(token, setOf("/public/a.jpg")))
        assertFalse(FavoriteAccessSessionManager.isActive(token))
    }

    @Test fun retainFavoritesReadFailureKeepsSessionCurrent() {
        val live = Files.createTempDirectory("fav-read-fail")
        try {
            val real = Files.write(live.resolve("a.jpg"), byteArrayOf(1)).toString()
            var fail = false
            val flaky: (String) -> FavoriteAccessSessionManager.Protection? = {
                if (fail) throw IllegalStateException("read failed")
                null
            }
            val snap = FavoriteAccessSessionManager.captureSnapshot(listOf(real), flaky)
            val current = FavoriteAccessSessionManager.createSession("owner", snap, emptySet(), flaky)!!
            fail = true
            assertNull(FavoriteAccessSessionManager.retainFavorites(current, setOf(real)))
            assertFalse(Thread.currentThread().isInterrupted)
            assertTrue(
                "transient read failure must leave the session installed",
                FavoriteAccessSessionManager.isActive(current),
            )
            assertNotNull(FavoriteAccessSessionManager.getCapturedPaths(current))
        } finally {
            live.toFile().deleteRecursively()
        }
    }
}
