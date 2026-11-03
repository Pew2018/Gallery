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
            assertTrue(found.contains(root.toString()))
            assertTrue(found.contains(child.toString()))
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
            assertEquals(2, found.size)
            assertFalse(found.any { it.contains("outside") })
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
            val found = FolderDiskDiscovery.find(listOf(root.toString()),
                allowed = { true }, cancelled = { ++calls > 1 })
            assertTrue(found.isEmpty())
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
            assertTrue(found.contains(ok.toString()))
            assertFalse(found.contains(locked.toString()))
        } finally { root.toFile().deleteRecursively() }
    }
}
