package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class FavoriteBackupCodecTest {
    @Test fun originalPlainTextFavoritePathsRemainSupported() {
        val text = "/storage/emulated/0/DCIM/a.jpg\n/storage/emulated/0/Pictures/b.mp4\n"
        assertEquals(listOf("/storage/emulated/0/DCIM/a.jpg", "/storage/emulated/0/Pictures/b.mp4"),
            FavoriteBackupCodec.decodeMediaPaths(text))
    }

    @Test fun oldFileFormatKeepsCaseChinesePunctuationAndDuplicatesAreCollapsed() {
        val path = "/storage/emulated/0/Pictures/图 # A.JPG"
        assertEquals(listOf(path), FavoriteBackupCodec.decodeMediaPaths("$path\n$path\n"))
    }

    @Test fun folderBackupRoundTripPreservesPathsAndAddedTime() {
        val originals = listOf(
            FavoriteBackupCodec.FolderRecord("/storage/emulated/0/Pictures/旅行", 1782748800000),
            FavoriteBackupCodec.FolderRecord("/storage/emulated/0/DCIM/a", 0)
        )
        val saved = FavoriteBackupCodec.encodeFolders(originals)
        assertEquals(originals, FavoriteBackupCodec.decodeFolders(saved))
        assertTrue(saved.contains("fossify-gallery-favorite-folders"))
    }

    @Test fun documentUriFolderPathsSurviveBackup() {
        val input = listOf(FavoriteBackupCodec.FolderRecord(
            "content://com.android.externalstorage.documents/tree/primary%3APictures", 42))
        assertEquals(input, FavoriteBackupCodec.decodeFolders(FavoriteBackupCodec.encodeFolders(input)))
    }

    @Test fun folderJsonCannotBeReadAsLegacyFileFavorites() {
        val json = FavoriteBackupCodec.encodeFolders(
            listOf(FavoriteBackupCodec.FolderRecord("/storage/emulated/0/Pictures/a", 1))
        )
        assertThrows(IllegalArgumentException::class.java) {
            FavoriteBackupCodec.decodeMediaPaths(json)
        }
    }

    @Test fun legacyFileFavoritesCannotBeReadAsFolderJson() {
        assertThrows(IllegalArgumentException::class.java) {
            FavoriteBackupCodec.decodeFolders("/storage/emulated/0/a.jpg\n")
        }
    }

    @Test fun jsonWrongVersionAndMalformedRecordsNeverPassValidation() {
        val wrongVersion = """{"format":"fossify-gallery-favorite-folders","version":2,"folders":[]}"""
        val badPath = """{"format":"fossify-gallery-favorite-folders","version":1,"folders":[{"path":"relative","addedAt":1}]}"""
        val badTime = """{"format":"fossify-gallery-favorite-folders","version":1,"folders":[{"path":"/valid","addedAt":-1}]}"""
        val fractionalTime = """{"format":"fossify-gallery-favorite-folders","version":1,"folders":[{"path":"/valid","addedAt":1.5}]}"""
        for (broken in listOf(wrongVersion, badPath, badTime, fractionalTime)) {
            assertThrows(IllegalArgumentException::class.java) {
                FavoriteBackupCodec.decodeFolders(broken)
            }
        }
    }

    @Test fun duplicateFolderRecordsKeepFirstTimestampWithoutChangingCase() {
        val json = """{"format":"fossify-gallery-favorite-folders","version":1,"folders":[{"path":"/Pictures/A","addedAt":7},{"path":"/Pictures/A/","addedAt":9},{"path":"/Pictures/a","addedAt":11}]}"""
        assertEquals(listOf(
            FavoriteBackupCodec.FolderRecord("/Pictures/A", 7),
            FavoriteBackupCodec.FolderRecord("/Pictures/a", 11)
        ), FavoriteBackupCodec.decodeFolders(json))
    }

    @Test fun emptyFoldersAreValidAndDoNotMixWithFiles() {
        assertEquals(emptyList<FavoriteBackupCodec.FolderRecord>(),
            FavoriteBackupCodec.decodeFolders(FavoriteBackupCodec.encodeFolders(emptyList())))
    }
}
