package org.fossify.gallery.helpers

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FavoriteAccessSessionManagerTest {
    private val locks = linkedMapOf<String, FavoriteAccessSessionManager.Protection>()
    private val pinA = FavoriteAccessSessionManager.Protection(1, "hash-A")
    private val pinB = FavoriteAccessSessionManager.Protection(1, "hash-B")
    private fun snapshot(vararg paths: String) = FavoriteAccessSessionManager.captureSnapshot(paths.toList(), locks::get)
    private fun session(vararg paths: String): String {
        val snapshot = snapshot(*paths)
        return FavoriteAccessSessionManager.createSession("owner", snapshot,
            snapshot.entries.values.flatMap { it.locks.keys }.toSet(), locks::get)!!
    }

    @After fun cleanup() { FavoriteAccessSessionManager.revokeAll() }

    @Test fun ordinaryFavoritesNeedNoChallengesAndHaveExactScope() {
        val snapshot = snapshot("/public/a.jpg", "/public/b.mp4")
        assertTrue(FavoriteAccessSessionManager.challenges(snapshot).isEmpty())
        val token = session(*snapshot.entries.keys.toTypedArray())
        assertEquals(snapshot.entries.keys, FavoriteAccessSessionManager.getAllowedPaths(token))
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, "/public/other.jpg"))
    }

    @Test fun protectedSourceRequiresSuccessBeforeCreatingAnyCapability() {
        locks["/private"] = pinA
        val snapshot = snapshot("/private/a.jpg", "/public/a.jpg")
        assertEquals(listOf(setOf("/private")), FavoriteAccessSessionManager.challenges(snapshot))
        assertNull(FavoriteAccessSessionManager.createSession("owner", snapshot, emptySet(), locks::get))
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(null, "/private/a.jpg"))
    }

    @Test fun onlyEqualNonemptySameTypeSecretsDeduplicate() {
        locks["/a"] = pinA
        locks["/b"] = pinA
        locks["/c"] = pinB
        locks["/d"] = FavoriteAccessSessionManager.Protection(0, pinA.hash)
        val snapshot = snapshot("/a/1.jpg", "/b/2.jpg", "/c/3.jpg", "/d/4.jpg")
        assertEquals(listOf(setOf("/a", "/b"), setOf("/c"), setOf("/d")),
            FavoriteAccessSessionManager.challenges(snapshot))
        assertNull(FavoriteAccessSessionManager.createSession("owner", snapshot, setOf("/a", "/b"), locks::get))
    }

    @Test fun oneFavoriteCredentialAuthorizesSeveralDifferentlyLockedSources() {
        locks["/private-a"] = pinA
        locks["/private-b"] = pinB
        locks["/private-c"] = FavoriteAccessSessionManager.Protection(0, "pattern-C")
        val paths = listOf("/private-a/one.jpg", "/private-b/two.mp4", "/private-c/three.jpg")
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(paths, locks::get)

        val generation = FavoriteAccessSessionManager.currentGeneration()
        assertNull(FavoriteAccessSessionManager.createFavoritesSession("owner", snapshot, false, generation, { true }, locks::get))
        val token = FavoriteAccessSessionManager.createFavoritesSession("owner", snapshot, true, generation, { true }, locks::get)!!
        assertEquals(paths.toSet(), FavoriteAccessSessionManager.getAllowedPaths(token))
        paths.forEach { assertTrue(FavoriteAccessSessionManager.isMediaAuthorized(token, it)) }
    }

    @Test fun favoriteCapabilityCannotExpandWhenAnotherPathIsAddedLater() {
        locks["/private-a"] = pinA
        locks["/private-b"] = pinB
        val first = "/private-a/one.jpg"
        val addedLater = "/private-b/two.jpg"
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf(first), locks::get)
        val generation = FavoriteAccessSessionManager.currentGeneration()
        val token = FavoriteAccessSessionManager.createFavoritesSession("owner", snapshot, true, generation, { true }, locks::get)!!

        assertEquals(setOf(first), FavoriteAccessSessionManager.retainFavorites(token, setOf(first, addedLater)))
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, addedLater))
    }

    @Test fun credentialChangedDuringAuthenticationCannotCreateOrRetainCapability() {
        var credentialCurrent = true
        val path = "/private-a/one.jpg"
        locks["/private-a"] = pinA
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf(path), locks::get)
        val generation = FavoriteAccessSessionManager.currentGeneration()
        val token = FavoriteAccessSessionManager.createFavoritesSession(
            "owner", snapshot, true, generation, { credentialCurrent }, locks::get
        )!!

        credentialCurrent = false
        assertNull(FavoriteAccessSessionManager.getAllowedPaths(token))
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, path))
        assertNull(FavoriteAccessSessionManager.createFavoritesSession(
            "owner", snapshot, true, generation, { credentialCurrent }, locks::get
        ))
    }

    @Test fun staleCredentialGenerationCannotCreateSessionAfterPasswordUpdate() {
        val path = "/a/photo.jpg"
        val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf(path), locks::get)
        val authenticatedGeneration = FavoriteAccessSessionManager.currentGeneration()
        FavoriteAccessSessionManager.revokeAll()

        assertNull(FavoriteAccessSessionManager.createFavoritesSession(
            "owner", snapshot, true, authenticatedGeneration, { true }, locks::get
        ))
    }

    @Test fun biometricsAndEmptyHashesNeverDeduplicate() {
        listOf("/a", "/b").forEach { locks[it] = FavoriteAccessSessionManager.Protection(2, "") }
        listOf("/c", "/d").forEach { locks[it] = FavoriteAccessSessionManager.Protection(1, "") }
        assertEquals(4, FavoriteAccessSessionManager.challenges(snapshot("/a/1", "/b/2", "/c/3", "/d/4")).size)
    }

    @Test fun repeatedImagesVideosAndSlidesShareOneAlbumLifetime() {
        locks["/a"] = pinA
        val paths = arrayOf("/a/photo.jpg", "/a/movie.mp4", "/public/slide.png")
        val token = session(*paths)
        repeat(4) { paths.forEach { assertTrue(FavoriteAccessSessionManager.isMediaAuthorized(token, it)) } }
        assertTrue(FavoriteAccessSessionManager.isOwnedBy(token, "owner")) // recreation/return
        assertFalse(FavoriteAccessSessionManager.isOwnedBy(token, "another-owner"))
        FavoriteAccessSessionManager.revokeSession(token) // true exit
        paths.forEach { assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, it)) }
        assertNotEquals(token, session(*paths))
    }

    @Test fun forgedTokensAndProcessRestartCannotReviveCapabilities() {
        val token = session("/public/a.jpg")
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized("IS_FROM_GALLERY=true", "/public/a.jpg"))
        assertNull(FavoriteAccessSessionManager.getAllowedPaths("forged"))
        FavoriteAccessSessionManager.revokeAll()
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, "/public/a.jpg"))
    }

    @Test fun nestedLocksRequireAllAncestorsIncludingDifferentCredentials() {
        locks["/a"] = pinA
        locks["/a/nested"] = pinB
        val snapshot = snapshot("/a/nested/photo.jpg")
        assertEquals(setOf("/a", "/a/nested"), snapshot.entries.values.single().locks.keys)
        assertNull(FavoriteAccessSessionManager.createSession("owner", snapshot, setOf("/a/nested"), locks::get))
        assertTrue(FavoriteAccessSessionManager.isMediaAuthorized(session("/a/nested/photo.jpg"), "/a/nested/photo.jpg"))
    }

    @Test fun traversalPrefixAndAlternateSpellingCannotExpandScope() {
        locks["/a"] = pinA
        val token = session("/a/photo.jpg")
        listOf("/a2/photo.jpg", "/b/photo.jpg", "/a/../b/photo.jpg", "/a/./photo.jpg")
            .forEach { assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, it)) }
    }

    @Test fun protectionAdditionRemovalOrHashChangeInvalidatesOldSession() {
        for (change in 0..2) {
            locks.clear()
            if (change != 0) locks["/a"] = pinA
            val token = session("/a/photo.jpg")
            if (change == 1) locks.clear() else locks["/a"] = pinB
            assertNull(FavoriteAccessSessionManager.getAllowedPaths(token))
        }
    }

    @Test fun configurationChangedDuringDialogCannotCreateStaleSession() {
        locks["/a"] = pinA
        val snapshot = snapshot("/a/photo.jpg")
        locks["/a"] = pinB
        assertNull(FavoriteAccessSessionManager.createSession("owner", snapshot, setOf("/a"), locks::get))
    }

    @Test fun symlinkChecksBothSourceAndTargetAndRetargetingRevokes() {
        val dir = Files.createTempDirectory("favorite-session")
        try {
            val a = Files.createDirectory(dir.resolve("a"))
            val b = Files.createDirectory(dir.resolve("b"))
            val c = Files.createDirectory(dir.resolve("c"))
            val link = a.resolve("alias")
            Files.createSymbolicLink(link, b)
            locks[a.toString()] = pinA
            locks[b.toString()] = pinB
            val path = link.resolve("image.jpg").toString()
            assertEquals(setOf(a.toString(), b.toString()), snapshot(path).entries[path]!!.locks.keys)
            val token = session(path)
            Files.delete(link)
            Files.createSymbolicLink(link, c)
            assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, path))
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test fun movedAndUnfavoritedPathsRevokeActiveConsumers() {
        var notifications = 0
        FavoriteAccessSessionManager.addRevocationListener("test") { notifications++ }
        try {
            val token = session("/a/photo.jpg", "/a/movie.mp4")
            FavoriteAccessSessionManager.revokePath("/a/photo.jpg")
            assertEquals(setOf("/a/movie.mp4"), FavoriteAccessSessionManager.getAllowedPaths(token))
            assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, "/a/photo.jpg"))
            assertEquals(1, notifications)
            val next = session("/a/photo.jpg", "/a/movie.mp4")
            assertEquals(setOf("/a/movie.mp4"), FavoriteAccessSessionManager.retainFavorites(next, setOf("/a/movie.mp4")))
            assertEquals(2, notifications)
        } finally { FavoriteAccessSessionManager.removeRevocationListener("test") }
    }
    @Test fun concurrentUnfavoriteOnlyShrinksAndCannotLoseEitherRemoval() {
        val paths = (1..40).map { "/a/$it.jpg" }
        val token = session(*paths.toTypedArray())
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = paths.map { path -> Thread { start.await(); FavoriteAccessSessionManager.revokePath(path) } }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(emptySet<String>(), FavoriteAccessSessionManager.getAllowedPaths(token))
        assertTrue(FavoriteAccessSessionManager.isActive(token))
        paths.forEach { assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, it)) }
    }

    @Test fun addingFavoritesDoesNotExpandAnExistingCapability() {
        val token = session("/a/old.jpg")
        val allowed = FavoriteAccessSessionManager.retainFavorites(token, setOf("/a/old.jpg", "/a/new.jpg"))
        assertEquals(setOf("/a/old.jpg"), allowed)
        assertFalse(FavoriteAccessSessionManager.isMediaAuthorized(token, "/a/new.jpg"))
        FavoriteAccessSessionManager.revokePath("/a/old.jpg")
        assertEquals(emptySet<String>(), FavoriteAccessSessionManager.retainFavorites(token, setOf("/a/old.jpg")))
    }

    @Test fun liveSessionCanBeReattachedByOwnerWithoutPersistingItsToken() {
        val token = FavoriteAccessSessionManager.createSession(
            "rotating-owner",
            snapshot("/public/photo.jpg"),
            emptySet(),
            locks::get
        )!!
        assertEquals(token, FavoriteAccessSessionManager.tokenForOwner("rotating-owner"))
        assertNull(FavoriteAccessSessionManager.tokenForOwner("other-owner"))
        FavoriteAccessSessionManager.revokeSession(token)
        assertNull(FavoriteAccessSessionManager.tokenForOwner("rotating-owner"))
    }

    @Test fun tokensAreIndependentRandomCapabilities() {
        val first = session("/a/a.jpg")
        val second = session("/a/a.jpg")
        assertEquals(64, first.length)
        assertNotEquals(first, second)
        FavoriteAccessSessionManager.revokeSession(first)
        assertFalse(FavoriteAccessSessionManager.isActive(first))
        assertTrue(FavoriteAccessSessionManager.isActive(second))
    }

    @Test fun selectedFavoriteDoesNotInspectOtherMembersAndFullCheckStillRevokesOnTheirChange() {
        val root = Files.createTempDirectory("favorite-first")
        try {
            val aRoot = Files.createDirectory(root.resolve("a"))
            val bRoot = Files.createDirectory(root.resolve("b"))
            val a = Files.write(aRoot.resolve("a.jpg"), byteArrayOf(1)).toString()
            val b = Files.write(bRoot.resolve("b.jpg"), byteArrayOf(2)).toString()
            val calls = ArrayList<String>()
            val protection: (String) -> FavoriteAccessSessionManager.Protection? = { calls.add(it); null }
            val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf(a, b), protection)
            val token = FavoriteAccessSessionManager.createSession("first", snapshot, emptySet(), protection)!!
            calls.clear()
            val first = FavoriteAccessSessionManager.validateSelectedPath(token, a)!!
            assertEquals(setOf(a), first.paths)
            assertFalse(calls.contains(bRoot.toString()))
            val count = calls.size
            assertTrue(FavoriteAccessSessionManager.isPublicationCurrent(first))
            assertEquals(count, calls.size)
            Files.delete(java.nio.file.Paths.get(b))
            assertNotNull(FavoriteAccessSessionManager.validateSelectedPath(token, a))
            assertNull(FavoriteAccessSessionManager.validatePaths(token, listOf(a, b)))
            assertFalse(FavoriteAccessSessionManager.isPublicationCurrent(first))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun selectedFavoriteRejectsRetargetedSymlinkAndPublicationCannotSurviveRevocation() {
        val root = Files.createTempDirectory("favorite-selected-link")
        try {
            val a = Files.write(root.resolve("a.jpg"), byteArrayOf(1))
            val b = Files.write(root.resolve("b.jpg"), byteArrayOf(2, 3))
            val alias = Files.createSymbolicLink(root.resolve("alias.jpg"), a)
            val token = session(alias.toString())
            val checked = FavoriteAccessSessionManager.validateSelectedPath(token, alias.toString())!!
            FavoriteAccessSessionManager.revokeSession(token)
            assertFalse(FavoriteAccessSessionManager.isPublicationCurrent(checked))
            val fresh = session(alias.toString())
            Files.delete(alias)
            Files.createSymbolicLink(alias, b)
            assertNull(FavoriteAccessSessionManager.validateSelectedPath(fresh, alias.toString()))
            assertFalse(FavoriteAccessSessionManager.isActive(fresh))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun selectedFavoriteNeverGrantsAnUncapturedOrForgedPath() {
        val token = session("/public/a.jpg")
        assertNull(FavoriteAccessSessionManager.validateSelectedPath(token, "/public/other.jpg"))
        assertNull(FavoriteAccessSessionManager.validateSelectedPath("forged", "/public/a.jpg"))
        assertNull(FavoriteAccessSessionManager.validateSelectedPath(null, "/public/a.jpg"))
    }

}

