package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.updateDBDirectory
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.DirectoryStats
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
class AuditVirtualDirectoryStatsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dbExecutor = Executors.newSingleThreadExecutor()

    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.filesDir.resolve("gallery.db").delete()
    }

    @After fun cleanup() {
        dbExecutor.shutdownNow()
        GalleryDatabase.destroyInstance()
    }

    private fun <T> db(block: () -> T): T =
        dbExecutor.submit<T>(block).get(5, TimeUnit.SECONDS)

    @Test fun favoritesUpdatePersistsMinAndMaxStatsForAscendingSort() {
        val stats = DirectoryStats(
            count = 2,
            bytes = 30,
            minModified = 100,
            maxModified = 500,
            minTaken = 200,
            maxTaken = 800,
            types = 1,
            known = DirectoryStats.ALL,
            scope = "virtual",
            scannedAt = 1_000L,
        )
        val directory = Directory(
            id = null,
            path = FAVORITES,
            tmb = "/tmb.jpg",
            name = "Favorites",
            mediaCnt = 2,
            modified = stats.maxModified,
            taken = stats.maxTaken,
            size = stats.bytes,
            location = 0,
            types = 1,
            sortValue = "",
            stats = stats,
        )
        db {
            context.directoryDB.insert(directory)
            val storedId = context.directoryDB.getByPath(FAVORITES)!!.id
            context.updateDBDirectory(directory.copy(id = storedId, name = "Favorites"))
        }

        val stored = db { context.directoryDB.getByPath(FAVORITES)!! }
        assertTrue(stored.stats.has(DirectoryStats.MODIFIED or DirectoryStats.TAKEN))
        assertEquals(100L, stored.modifiedForSort(descending = false))
        assertEquals(500L, stored.modifiedForSort(descending = true))
        assertEquals(200L, stored.takenForSort(descending = false))
        assertEquals(800L, stored.takenForSort(descending = true))
        assertEquals(1_000L, stored.stats.scannedAt)
    }

    @Test fun physicalDirectoryUpdateStillReturnsEarly() {
        val path = "/storage/emulated/0/DCIM"
        db {
            context.directoryDB.insert(
                Directory(
                    id = null, path = path, tmb = "", name = "DCIM", mediaCnt = 1,
                    modified = 1, taken = 1, size = 1, location = 0, types = 1, sortValue = "",
                    stats = DirectoryStats(known = DirectoryStats.ALL, minModified = 1, maxModified = 9,
                        scope = "physical", scannedAt = 42),
                )
            )
            context.updateDBDirectory(
                Directory(
                    id = null, path = path, tmb = "x", name = "DCIM", mediaCnt = 99,
                    modified = 99, taken = 99, size = 99, location = 0, types = 1, sortValue = "",
                    stats = DirectoryStats(known = DirectoryStats.ALL, minModified = 2, maxModified = 8,
                        scope = "physical", scannedAt = 99),
                )
            )
        }
        val stored = db { context.directoryDB.getByPath(path)!! }
        assertEquals(1, stored.mediaCnt)
        assertEquals(42L, stored.stats.scannedAt)
    }
}
