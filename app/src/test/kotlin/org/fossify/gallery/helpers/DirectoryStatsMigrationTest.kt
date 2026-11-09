package org.fossify.gallery.helpers

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.DirectoryStats
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DirectoryStatsMigrationTest {
    @Test fun realV11DatabaseOpensWithRoomValidationAndPreservesAllTables() {
        val context = RuntimeEnvironment.getApplication()
        val name = "stats-v11-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            listOf(
                "CREATE TABLE IF NOT EXISTS `directories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `path` TEXT NOT NULL, `thumbnail` TEXT NOT NULL, `filename` TEXT NOT NULL, `media_count` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL, `date_taken` INTEGER NOT NULL, `size` INTEGER NOT NULL, `location` INTEGER NOT NULL, `media_types` INTEGER NOT NULL, `sort_value` TEXT NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_directories_path` ON `directories` (`path`)",
                "CREATE TABLE IF NOT EXISTS `media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `filename` TEXT NOT NULL, `full_path` TEXT NOT NULL, `parent_path` TEXT NOT NULL, `last_modified` INTEGER NOT NULL, `date_taken` INTEGER NOT NULL, `size` INTEGER NOT NULL, `type` INTEGER NOT NULL, `video_duration` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `deleted_ts` INTEGER NOT NULL, `media_store_id` INTEGER NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_full_path` ON `media` (`full_path`)",
                "CREATE TABLE IF NOT EXISTS `widgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `widget_id` INTEGER NOT NULL, `folder_path` TEXT NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_widgets_widget_id` ON `widgets` (`widget_id`)",
                "CREATE TABLE IF NOT EXISTS `date_takens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL, `date_taken` INTEGER NOT NULL, `last_fixed` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_date_takens_full_path` ON `date_takens` (`full_path`)",
                "CREATE TABLE IF NOT EXISTS `favorites` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_full_path` ON `favorites` (`full_path`)",
                "CREATE TABLE IF NOT EXISTS `favorite_folders` (`path` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`path`))"
            ).forEach(old::execSQL)
            old.execSQL("INSERT INTO directories VALUES(17, '/A', '/A/cover.jpg', 'Custom', 2, 100, 200, 9, 1, 1, 'old-key')")
            old.execSQL("INSERT INTO media VALUES(3, 'one.jpg', '/A/one.jpg', '/A', 1, 2, 3, 1, 0, 1, 0, 9)")
            old.execSQL("INSERT INTO favorites VALUES(4, '/A/one.jpg', 'one.jpg', '/A')")
            old.execSQL("INSERT INTO favorite_folders VALUES('/A', 123)")
            old.execSQL("INSERT INTO date_takens VALUES(5, '/A/one.jpg', 'one.jpg', '/A', 456, 1, 2)")
            old.execSQL("INSERT INTO widgets VALUES(6, 7, '/A')")
            old.version = 11
        }
        val db = Room.databaseBuilder(context, GalleryDatabase::class.java, name)
            .addMigrations(GalleryDatabase.MIGRATION_11_12).allowMainThreadQueries().build()
        try {
            val row = db.DirectoryDao().getByPath("/A")!!
            assertEquals(17L, row.id)
            assertEquals("/A/cover.jpg", row.tmb)
            assertEquals("old-key", row.sortValue)
            assertEquals(100L, row.modified)
            assertEquals(200L, row.taken)
            assertEquals(9L, row.size)
            assertFalse(row.stats.has(DirectoryStats.ALL))
            assertEquals(0L, row.stats.minModified)
            assertEquals(0L, row.stats.maxModified)
            assertEquals("", row.stats.scope)
            val sqlite = db.openHelper.writableDatabase
            listOf("media", "favorites", "favorite_folders", "date_takens", "widgets").forEach { table ->
                sqlite.query("SELECT count(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            }
            sqlite.query("SELECT date_taken FROM date_takens").use { assertTrue(it.moveToFirst()); assertEquals(456L, it.getLong(0)) }
            assertEquals(12, sqlite.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
