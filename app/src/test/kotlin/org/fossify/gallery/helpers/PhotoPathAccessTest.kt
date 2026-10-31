package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PhotoPathAccessTest {
    @Test fun preservesRawAndResolvedNestedLocksWithoutRequiringFileMetadata() {
        val root = Files.createTempDirectory("photo-path-access")
        try {
            val public = Files.createDirectory(root.resolve("public"))
            val private = Files.createDirectory(root.resolve("private"))
            val nested = Files.createDirectory(private.resolve("nested"))
            val target = Files.write(nested.resolve("secret.jpg"), byteArrayOf(1))
            val link = Files.createSymbolicLink(public.resolve("alias.jpg"), target)
            val lock = FavoriteAccessSessionManager.Protection(1, "test")
            val protected = setOf(public.toString(), private.toString(), nested.toString())
            val calls = HashMap<String, Int>()
            val entries = PhotoPathAccess.capture(listOf(link.toString(), public.resolve("other.jpg").toString()), {
                calls[it] = (calls[it] ?: 0) + 1
                if (it in protected) lock else null
            })
            val actual = entries.getValue(link.toString())
            assertEquals(target.toString(), actual.canonicalPath)
            assertEquals(protected, actual.locks.keys)
            assertTrue(calls.values.all { it == 1 })
            assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(actual.locks, emptyMap(), true))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun cancellationAndRelativePathsFailClosed() {
        assertThrows(InterruptedException::class.java) {
            PhotoPathAccess.capture(listOf("/Camera/a.jpg"), { null }, { true })
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoPathAccess.capture(listOf("Camera/a.jpg"), { null })
        }
    }
}
