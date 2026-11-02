package org.fossify.gallery.helpers.backup

import org.fossify.gallery.helpers.FavoriteBackupCodec
import org.junit.Assert.*
import org.junit.Test

class FavoriteBackupValidationTest {
    @Test fun invalidFolderFieldsAndDeepRootPathsAreRejected() {
        for (record in listOf("{\"path\":\"/a\",\"addedAt\":null}", "{\"path\":3,\"addedAt\":1}",
            "{\"path\":\"/a\",\"addedAt\":9223372036854775808}", "{\"path\":\"/a/../\",\"addedAt\":1}",
            "{\"path\":\"/a\\tb\",\"addedAt\":1}")) {
            assertThrows(IllegalArgumentException::class.java) {
                FavoriteBackupCodec.decodeFolders("""{"format":"fossify-gallery-favorite-folders","version":1,"folders":[$record]}""")
            }
        }
    }
    @Test fun emptyBrokenAndMissingFolderArraysAreRejected() {
        for (text in listOf("", "{broken}", "{}", "[]", "{\"version\":1,\"format\":\"fossify-gallery-favorite-folders\",\"folders\":{}}"))
            assertThrows(IllegalArgumentException::class.java) { FavoriteBackupCodec.decodeFolders(text) }
    }
    @Test fun longFolderPathsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FavoriteBackupCodec.encodeFolders(listOf(FavoriteBackupCodec.FolderRecord("/" + "a".repeat(4096), 1)))
        }
    }
    @Test fun mediaPlanClassifiesInvalidAndDuplicateLinesWithoutLosingValidChinesePaths() {
        val plan = FavoriteBackupCodec.decodeMediaPlan("\uFEFF/Pictures/中文.JPG\n/Pictures/中文.JPG\nrelative\n/\n/Pictures/中文.jpg\n")
        assertEquals(5, plan.total); assertEquals(1, plan.duplicates); assertEquals(2, plan.invalid)
        assertEquals(listOf("/Pictures/中文.JPG", "/Pictures/中文.jpg"), plan.paths)
        val encoded = FavoriteBackupCodec.encodeMediaPaths(plan.paths)
        assertEquals(plan.paths, FavoriteBackupCodec.decodeMediaPaths(encoded))
        assertFalse(encoded.startsWith("{"))
    }
}
