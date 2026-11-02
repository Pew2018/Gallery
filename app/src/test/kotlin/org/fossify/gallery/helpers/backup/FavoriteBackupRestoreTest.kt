package org.fossify.gallery.helpers.backup

import android.app.Application
import androidx.room.Room
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.FavoriteBackupCodec
import org.fossify.gallery.helpers.PhotoContentRevision
import org.fossify.gallery.models.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FavoriteBackupRestoreTest {
    private fun favorite(path: String) = Favorite(null, path, path.substringAfterLast('/'), path.substringBeforeLast('/'))
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GalleryDatabase::class.java)
        .allowMainThreadQueries().build()

    @Test fun exclusiveStatisticsIncludeProtectionChangesAndInvalidPaths() {
        val db = database()
        try {
            db.FavoritesDao().insert(favorite("/existing"))
            val plan = FavoriteBackupCodec.decodeMediaPlan("/new\n/existing\n/missing\n/protected\n/changed\nrelative\n/new\n")
            val calls = hashMapOf<String, Int>()
            val result = FavoriteBackupRestore(db).importMedia(plan, { it != "/missing" }, { path ->
                val count = (calls[path] ?: 0) + 1
                calls[path] = count
                path != "/protected" && (path != "/changed" || count == 1)
            }, ::favorite)
            assertEquals(BackupImportResult(7, 1, 1, 1, 1, 1, 1, 1), result)
            assertEquals(result.total, result.imported + result.existing + result.skipped)
            assertEquals(setOf("/existing", "/new"), db.FavoritesDao().getValidFavoritePaths().toSet())
        } finally { db.close() }
    }
    @Test fun repeatedImportIsIdempotentAndCachedFlagsAgree() {
        val db = database()
        try {
            db.MediumDao().insert(Medium().apply { path = "/Pictures/A.JPG"; parentPath = "/Pictures"; name = "A.JPG" })
            val plan = FavoriteBackupCodec.decodeMediaPlan("/Pictures/A.JPG\n/Pictures/中文.jpg\n")
            val restore = FavoriteBackupRestore(db)
            val revision = PhotoContentRevision.current()
            assertEquals(2, restore.importMedia(plan, { true }, { true }, ::favorite).imported)
            assertTrue(PhotoContentRevision.current() > revision)
            val after = PhotoContentRevision.current()
            val second = restore.importMedia(plan, { true }, { true }, ::favorite)
            assertEquals(0, second.imported); assertEquals(2, second.existing)
            assertEquals(after, PhotoContentRevision.current())
            assertTrue(db.MediumDao().getCachedMediaFromFiles(listOf("/Pictures/A.JPG")).single().isFavorite)
            assertEquals(2L, db.FavoritesDao().getFavoritesCount())
        } finally { db.close() }
    }
    @Test fun persistenceFailureRollsBackBothTablesAndDoesNotPublishRevision() {
        val db = database()
        try {
            db.MediumDao().insert(Medium().apply { path = "/first" })
            val before = PhotoContentRevision.current()
            assertThrows(IllegalStateException::class.java) {
                FavoriteBackupRestore(db).importMedia(FavoriteBackupCodec.decodeMediaPlan("/first\n/second"), { true }, { true }) {
                    if (it == "/second") error("simulated database preparation failure") else favorite(it)
                }
            }
            assertEquals(0L, db.FavoritesDao().getFavoritesCount())
            assertFalse(db.MediumDao().getCachedMediaFromFiles(listOf("/first")).single().isFavorite)
            assertEquals(before, PhotoContentRevision.current())
        } finally { db.close() }
    }
    @Test fun protectionCheckErrorsFailClosedAndDoNotCreateSessions() {
        val db = database()
        try {
            val result = FavoriteBackupRestore(db).importMedia(FavoriteBackupCodec.decodeMediaPlan("/private"), { true }, { error("unavailable policy") }, ::favorite)
            assertEquals(1, result.protectedSkipped); assertEquals(0, result.imported)
        } finally { db.close() }
    }
    @Test fun offlineSdAndSafFoldersRemainSavedWithOriginalTimestampsAndMediaUntouched() {
        val db = database()
        try {
            db.FavoritesDao().insert(favorite("/Pictures/photo.jpg"))
            db.FavoriteFoldersDao().insert(FavoriteFolder("/storage/ABCD/Photos", 7))
            val records = listOf(
                FavoriteBackupCodec.FolderRecord("/storage/ABCD/Photos", 99),
                FavoriteBackupCodec.FolderRecord("/storage/OFFLINE/中文", 42),
                FavoriteBackupCodec.FolderRecord("content://storage/tree/ABCD%3APictures", 55),
                FavoriteBackupCodec.FolderRecord("/storage/offline/中文", 56))
            val restore = FavoriteBackupRestore(db)
            assertEquals(3, restore.importFolders(records) { false }.imported)
            assertEquals(0, restore.importFolders(records) { false }.imported)
            assertEquals(7L, db.FavoriteFoldersDao().getAll().first { it.path == "/storage/ABCD/Photos" }.addedAt)
            assertEquals(4, db.FavoriteFoldersDao().getAll().size)
            assertEquals(listOf("/Pictures/photo.jpg"), db.FavoritesDao().getValidFavoritePaths())
        } finally { db.close() }
    }
    @Test fun actualFilesAreRejectedAsFoldersButUnavailablePathsAreNot() {
        val db = database()
        try {
            val result = FavoriteBackupRestore(db).importFolders(listOf(
                FavoriteBackupCodec.FolderRecord("/actual-file.jpg", 1), FavoriteBackupCodec.FolderRecord("/offline", 2))) { it.endsWith(".jpg") }
            assertEquals(1, result.invalid); assertEquals(1, result.imported)
        } finally { db.close() }
    }
}
