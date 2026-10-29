package org.fossify.gallery.databases

import android.app.Application
import androidx.room.Room
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.FavoriteFolder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FavoriteBackupDatabaseTest {
    @Test fun folderImportDoesNotChangeMediaFavoritesOrOverwriteExistingFolderDates() {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val files = database.FavoritesDao()
            val folders = database.FavoriteFoldersDao()
            files.insert(Favorite(null, "/Pictures/a.jpg", "a.jpg", "/Pictures"))
            folders.insert(FavoriteFolder("/Pictures/travel", 10))
            val imported = listOf(
                FavoriteFolder("/Pictures/travel", 90),
                FavoriteFolder("/Pictures/new", 50)
            )
            database.runInTransaction {
                val outcomes = folders.insertAllIgnore(imported)
                assertEquals(1, outcomes.count { it >= 0L })
            }
            assertEquals(1L, files.getFavoritesCount())
            assertEquals(listOf("/Pictures/a.jpg"), files.getValidFavoritePaths())
            assertEquals(2, folders.getAll().size)
            assertEquals(10L, folders.getAll().first { it.path == "/Pictures/travel" }.addedAt)
            assertEquals(50L, folders.getAll().first { it.path == "/Pictures/new" }.addedAt)
        } finally {
            database.close()
        }
    }

    @Test fun legacyMediaImportDoesNotChangeSavedFolderFavorites() {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val files = database.FavoritesDao()
            val folders = database.FavoriteFoldersDao()
            folders.insert(FavoriteFolder("/Pictures/private", 1234))
            files.insert(Favorite(null, "/Pictures/a.jpg", "a.jpg", "/Pictures"))
            database.runInTransaction {
                assertEquals(1, files.insertAllIgnore(listOf(
                    Favorite(null, "/Pictures/a.jpg", "a.jpg", "/Pictures"),
                    Favorite(null, "/Pictures/b.jpg", "b.jpg", "/Pictures")
                )).count { it >= 0L })
            }
            assertEquals(2L, files.getFavoritesCount())
            assertEquals(1, folders.getAll().size)
            assertEquals(1234L, folders.getAll().single().addedAt)
        } finally {
            database.close()
        }
    }
}
