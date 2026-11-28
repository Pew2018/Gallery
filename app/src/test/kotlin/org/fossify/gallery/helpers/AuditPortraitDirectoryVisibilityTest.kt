package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AuditPortraitDirectoryVisibilityTest {
    @Test fun portraitSubdirectoryIsResolvedAgainstTheAlbumFolder() {
        val folder = Files.createTempDirectory("portrait-album").toFile()
        try {
            val portrait = File(folder, "img_burst").apply { mkdirs() }
            assertTrue(portrait.isDirectory)
            // list() returns bare names. Resolving against cwd is wrong.
            assertFalse(
                "cwd-relative File(child) must not look like the portrait folder",
                File(portrait.name).isDirectory && File(portrait.name).canonicalPath == portrait.canonicalPath,
            )
            assertTrue(File(folder, portrait.name).isDirectory)

            val children = folder.list()?.toList().orEmpty()
            val hasPortrait = children.any { child ->
                child.startsWith("img_", true) && File(folder, child).isDirectory
            }
            assertTrue(hasPortrait)

            val empty = Files.createTempDirectory("empty-album").toFile()
            try {
                File(empty, "readme.txt").writeText("no media")
                val emptyChildren = empty.list()?.toList().orEmpty()
                val hide = emptyChildren.none { child ->
                    child.endsWith(".jpg", true) ||
                        (child.startsWith("img_", true) && File(empty, child).isDirectory)
                }
                assertTrue(hide)
            } finally {
                empty.deleteRecursively()
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
