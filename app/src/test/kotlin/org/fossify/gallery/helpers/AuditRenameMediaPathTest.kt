package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuditRenameMediaPathTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dbExecutor = Executors.newSingleThreadExecutor()

    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.filesDir.resolve("gallery.db").delete()
        context.filesDir.resolve("gallery.db-shm").delete()
        context.filesDir.resolve("gallery.db-wal").delete()
    }

    @After fun cleanup() {
        dbExecutor.shutdownNow()
        GalleryDatabase.destroyInstance()
    }

    private fun <T> db(block: () -> T): T =
        dbExecutor.submit<T>(block).get(5, TimeUnit.SECONDS)

    @Test fun renameUpdatesMediaAndFavoriteRowsToTheNewPath() {
        val oldPath = "/storage/emulated/0/DCIM/old.jpg"
        val newPath = "/storage/emulated/0/Pictures/new.jpg"
        db {
            context.mediaDB.insert(Medium().apply {
                path = oldPath
                name = "old.jpg"
                parentPath = "/storage/emulated/0/DCIM"
                size = 10L
                isFavorite = true
            })
            context.favoritesDB.insert(Favorite(null, oldPath, "old.jpg", "/storage/emulated/0/DCIM"))
            context.updateDBMediaPath(oldPath, newPath)
        }

        assertTrue(db { context.mediaDB.getCachedMediaFromFiles(listOf(oldPath)) }.isEmpty())
        val media = db { context.mediaDB.getCachedMediaFromFiles(listOf(newPath)) }.single()
        assertEquals(newPath, media.path)
        assertEquals("/storage/emulated/0/Pictures", media.parentPath)
        assertEquals("new.jpg", media.name)

        assertFalse(db { context.favoritesDB.isFavorite(oldPath) })
        assertTrue(db { context.favoritesDB.isFavorite(newPath) })
        assertEquals(listOf(newPath), db { context.mediaDB.getCachedFavoriteMedia() }.map { it.path })
    }
}
