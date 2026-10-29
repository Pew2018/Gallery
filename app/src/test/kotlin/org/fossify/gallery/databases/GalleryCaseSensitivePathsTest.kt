package org.fossify.gallery.databases

import android.app.Application
import androidx.room.Room
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as TestConfig

/** Only an in-memory test database is touched; never user media or app preferences. */
@RunWith(RobolectricTestRunner::class)
@TestConfig(sdk = [28], application = Application::class)
class GalleryCaseSensitivePathsTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), GalleryDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test fun overFourHundredFavoritesRemainIndependentAcrossCaseDistinctPaths() {
        val db = database()
        try {
            val dao = db.FavoritesDao()
            val upper = "/Pictures/Travel/A.jpg"
            val lower = "/Pictures/travel/A.jpg"
            val saved = (1..451).map { n ->
                Favorite(null, "/Pictures/旅行 #1/image_$n.jpg", "image_$n.jpg", "/Pictures/旅行 #1")
            } + listOf(
                Favorite(null, upper, "A.jpg", "/Pictures/Travel"),
                Favorite(null, lower, "A.jpg", "/Pictures/travel")
            )
            dao.insertAll(saved)
            assertEquals(453L, dao.getFavoritesCount())
            assertEquals(453, dao.getValidFavoritePaths().distinct().size)

            dao.deleteFavoritePath(upper)
            assertFalse(dao.isFavorite(upper))
            assertTrue(dao.isFavorite(lower))
            assertEquals(452L, dao.getFavoritesCount())

            dao.insert(Favorite(null, upper, "A.jpg", "/Pictures/Travel"))
            dao.updateFavorite("B.jpg", "/Pictures/Travel/B.jpg", "/Pictures/Travel", upper)
            assertTrue(dao.isFavorite("/Pictures/Travel/B.jpg"))
            assertTrue(dao.isFavorite(lower))
            assertFalse(dao.isFavorite(upper))
            assertEquals(453L, dao.getFavoritesCount())
        } finally {
            db.close()
        }
    }

    @Test fun mediaAndDirectoryUpdatesDoNotMutateOtherCaseVariant() {
        val db = database()
        try {
            val upper = "/Pictures/Travel/A.jpg"
            val lower = "/Pictures/travel/A.jpg"
            val media = db.MediumDao()
            for (path in listOf(upper, lower)) {
                media.insert(Medium().apply {
                    this.path = path
                    name = "A.jpg"
                    parentPath = path.substringBeforeLast('/')
                    size = 100L
                })
            }
            media.updateFavorite(upper, true)
            assertEquals(listOf(upper), media.getFavorites().map { it.path })
            media.deleteMediumPath(upper)
            assertEquals(listOf(lower), media.getAll().map { it.path })

            val directories = db.DirectoryDao()
            for (path in listOf("/Pictures/Travel", "/Pictures/travel")) {
                directories.insert(Directory().apply {
                    this.path = path
                    name = path.substringAfterLast('/')
                })
            }
            directories.deleteDirPath("/Pictures/Travel")
            assertEquals(listOf("/Pictures/travel"), directories.getAll().map { it.path })
        } finally {
            db.close()
        }
    }
}
