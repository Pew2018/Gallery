package org.fossify.gallery.databases

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Synthetic Room v11 database only; this does not inspect a user's gallery.db. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GalleryDatabaseQueryPlanTest {
    @Test fun representativeV11QueryPlansAndMembershipHoldAtRequestedScales() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val thresholds = listOf(100, 5_000, 30_000, 100_000)
            val mediaDao = db.MediumDao()
            val favoriteDao = db.FavoritesDao()
            val directoryDao = db.DirectoryDao()
            directoryDao.insertAll((0 until 100).map { index ->
                Directory(null, "/synthetic/folder-$index", "", "folder-$index", 1,
                    index.toLong(), index.toLong(), index.toLong(), 0, 1, "")
            })
            var inserted = 0
            for (target in thresholds) {
                while (inserted < target) {
                    val end = minOf(inserted + 1_000, target)
                    val rows = (inserted until end).map { index ->
                        val folder = "/synthetic/folder-${index % 100}"
                        Medium(null, "image-$index.jpg", "$folder/image-$index.jpg", folder,
                            index.toLong(), index.toLong(), 1024L + index, 1, 0,
                            index % 10 == 0, if (index % 20 == 0) 1L else 0L, index.toLong())
                    }
                    mediaDao.insertAll(rows)
                    val favorites = rows.filter { it.id == null && it.path.substringAfterLast('/').substringBefore('.')
                        .removePrefix("image-").toInt() % 10 == 0 }.map {
                        Favorite(null, it.path, it.name, it.parentPath)
                    }
                    if (favorites.isNotEmpty()) favoriteDao.insertAll(favorites)
                    inserted = end
                }
                // Match the actual Room v11 entity/table layout and let SQLite
                // choose plans after seeing representative cardinality statistics.
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL("ANALYZE")
                val byPath = explain(sqlite,
                    "SELECT * FROM media WHERE deleted_ts = 0 AND full_path IN (?, ?, ?)",
                    arrayOf("/synthetic/folder-0/image-0.jpg", "/synthetic/folder-1/image-1.jpg", "/missing"))
                val byParent = explain(sqlite,
                    "SELECT * FROM media WHERE deleted_ts = 0 AND parent_path = ?",
                    arrayOf("/synthetic/folder-1"))
                val favoriteJoin = explain(sqlite,
                    "SELECT media.* FROM media INNER JOIN favorites ON media.full_path = favorites.full_path WHERE media.deleted_ts = 0",
                    emptyArray())
                assertTrue("full_path lookup must use its v11 unique index: $byPath",
                    byPath.any { it.contains("index_media_full_path") })
                assertTrue("parent_path has no v11 index and should be scanned: $byParent",
                    byParent.any { it.startsWith("SCAN") && it.contains("media") })
                assertTrue("favorite relationship join must use full_path lookup: $favoriteJoin",
                    favoriteJoin.any { it.contains("index_media_full_path") ||
                        it.contains("index_favorites_full_path") })

                val selectedFolders = listOf("/synthetic/folder-0", "/synthetic/folder-1")
                val multiFolderRows = mediaDao.getCachedMediaFromFolders(selectedFolders)
                assertEquals(multiFolderRows.map { it.path }.toSet().size, multiFolderRows.size)
                assertEquals(target - target / 20, mediaDao.getAllMedia().size)
                assertEquals(target / 20, mediaDao.getCachedFavoriteMedia().size)
                assertEquals(target / 20, mediaDao.getDeletedMediaCount().toInt())
                assertEquals(target / 10, favoriteDao.getFavoritesCount().toInt())
                val samplePaths = listOf("/synthetic/folder-0/image-0.jpg", "/synthetic/folder-1/image-1.jpg")
                val timings = listOf(
                    "parent_eq" to { mediaDao.getMediaFromPath("/synthetic/folder-1") },
                    "parent_in" to { mediaDao.getCachedMediaFromFolders(selectedFolders) },
                    "path_in" to { mediaDao.getCachedMediaFromFiles(samplePaths) },
                    "favorite_join" to { mediaDao.getCachedFavoriteMedia() },
                    "deleted" to { mediaDao.getDeletedMedia() },
                    "directory_all" to { directoryDao.getAll() }
                ).map { (name, query) ->
                    val started = System.nanoTime()
                    val result = query()
                    name to ((System.nanoTime() - started) / 1_000.0 to result.size)
                }
                println("GalleryDbPlan syntheticRows=$target byPath=${byPath.joinToString(" | ")} " +
                        "byParent=${byParent.joinToString(" | ")} favoriteJoin=${favoriteJoin.joinToString(" | ")} " +
                        "singleCallUsAndRows=${timings.joinToString { "${it.first}:${it.second.first.toLong()}us/${it.second.second}" }}")
            }
            assertEquals(2, directoryDao.getByPaths(listOf("/synthetic/folder-0", "/synthetic/folder-1")).size)
        } finally {
            db.close()
        }
    }

    private fun explain(db: SupportSQLiteDatabase, sql: String, args: Array<String>): List<String> {
        val cursor = db.query("EXPLAIN QUERY PLAN $sql", args)
        return cursor.use { result ->
            buildList {
                while (result.moveToNext()) add(result.getString(3))
            }
        }
    }
}
