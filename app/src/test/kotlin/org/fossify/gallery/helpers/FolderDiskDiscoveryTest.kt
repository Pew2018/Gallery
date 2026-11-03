package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class FolderDiskDiscoveryTest {
    @Test fun explicitlyAllowedNoMediaTreeKeepsItsChildren() {
        val root = Files.createTempDirectory("gallery-discovery-nomedia")
        try {
            Files.createFile(root.resolve(".nomedia"))
            val child = Files.createDirectories(root.resolve("one/two"))
            val found = FolderDiskDiscovery.find(listOf(root.toString()),
                allowed = { true }, cancelled = { false })
            assertTrue(found.folders.contains(root.toString()))
            assertTrue(found.folders.contains(child.toString()))
            assertTrue(found.complete)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun repeatsAndSymlinkCyclesDoNotEscapeTheSelectedRoot() {
        val root = Files.createTempDirectory("gallery-discovery-root")
        val outside = Files.createTempDirectory("gallery-discovery-outside")
        try {
            Files.createDirectories(root.resolve("a"))
            Files.createDirectories(outside.resolve("secret"))
            Files.createSymbolicLink(root.resolve("a/outside"), outside)
            Files.createSymbolicLink(root.resolve("a/parent"), root)
            val found = FolderDiskDiscovery.find(listOf(root.toString(), root.toString()),
                allowed = { true }, cancelled = { false })
            assertEquals(2, found.folders.size)
            assertFalse(found.folders.any { it.contains("outside") })
        } finally {
            // File.deleteRecursively can follow directory symlinks on some JDKs.
            // Remove links before walking the test fixture's real directories.
            Files.deleteIfExists(root.resolve("a/outside"))
            Files.deleteIfExists(root.resolve("a/parent"))
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    @Test fun cancellationStopsWalkingBeforeFurtherFilesystemReads() {
        val root = Files.createTempDirectory("gallery-discovery-cancel")
        try {
            Files.createDirectories(root.resolve("a/b"))
            var calls = 0
            try {
                FolderDiskDiscovery.find(listOf(root.toString()),
                    allowed = { true }, cancelled = { ++calls > 1 })
                fail("Cancellation returned success")
            } catch (_: java.util.concurrent.CancellationException) { }

        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun disallowedDirectoriesNeverExposeTheirDescendants() {
        val root = Files.createTempDirectory("gallery-discovery-locked")
        try {
            val locked = Files.createDirectories(root.resolve("protected/child"))
            val ok = Files.createDirectories(root.resolve("public"))
            val found = FolderDiskDiscovery.find(listOf(root.toString()), allowed = {
                !it.contains("/protected")
            }, cancelled = { false })
            assertTrue(found.folders.contains(ok.toString()))
            assertFalse(found.folders.contains(locked.toString()))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun unreadableChildIsReportedWhileOtherAlbumsAreDiscovered() {
        val root = Files.createTempDirectory("discovery-unreadable")
        try {
            val denied = Files.createDirectory(root.resolve("denied"))
            val good = Files.createDirectories(root.resolve("good/album"))
            val result = FolderDiskDiscovery.find(listOf(root.toString()), { true }, { false },
                listFiles = { if (it.path == denied.toString()) null else it.listFiles() })
            assertFalse(result.complete)
            assertEquals(setOf(denied.toString()), result.unreadablePaths)
            assertTrue(good.toString() in result.folders)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun budgetIsExplicitlyPartialAndNeverPretendsToVisitTheWholeTree() {
        val root = Files.createTempDirectory("discovery-budget")
        try {
            repeat(15) { Files.createDirectory(root.resolve("album$it")) }
            val result = FolderDiskDiscovery.find(listOf(root.toString()), { true }, { false }, maxFolders = 4)
            assertFalse(result.complete)
            assertTrue(result.truncated)
            assertTrue(result.folders.size <= 4)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun canonicalAliasCannotBypassProtectedDescendant() {
        val root = Files.createTempDirectory("discovery-alias")
        try {
            val secret = Files.createDirectories(root.resolve("secret/child"))
            Files.createSymbolicLink(root.resolve("alias"), root.resolve("secret"))
            val result = FolderDiskDiscovery.find(listOf(root.toString()),
                { !it.startsWith(root.resolve("secret").toString()) }, { false })
            assertTrue(result.complete) // policy skips are intentional
            assertFalse(result.folders.any { it.contains("alias") || it == secret.toString() })
        } finally {
            Files.deleteIfExists(root.resolve("alias"))
            root.toFile().deleteRecursively()
        }
    }

    @Test fun missingSelectedRootRemainsUncertainInsteadOfEmpty() {
        val missing = Files.createTempDirectory("discovery-missing").also { Files.delete(it) }
        val result = FolderDiskDiscovery.find(listOf(missing.toString()), { true }, { false })
        assertFalse(result.complete)
        assertEquals(setOf(missing.toString()), result.unreadablePaths)
    }
}
