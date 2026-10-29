package org.fossify.gallery.databases

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as TestConfig

/**
 * Missing migration paths must fail closed rather than silently destroying
 * durable favorite relationships. Uses an isolated Robolectric database only.
 */
@RunWith(RobolectricTestRunner::class)
@TestConfig(sdk = [28], application = Application::class)
class GalleryDatabaseMigrationSafetyTest {
    @Test fun unsupportedOldAndNewSchemasKeepSavedFavoritesIntact() {
        val context = RuntimeEnvironment.getApplication()
        val path = context.getDatabasePath("gallery.db")
        for (version in listOf(3, 12)) {
            GalleryDatabase.destroyInstance()
            context.deleteDatabase("gallery.db")
            path.parentFile?.mkdirs()

            SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
                database.execSQL(
                    "CREATE TABLE favorites (id INTEGER PRIMARY KEY, full_path TEXT NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO favorites (id, full_path) VALUES (1, '/Pictures/旅行 #1.jpg')"
                )
                database.version = version
            }

            try {
                val failure = assertThrows(IllegalStateException::class.java) {
                    GalleryDatabase.getInstance(context).openHelper.writableDatabase
                }
                assertTrue(failure.message.orEmpty().contains("migration", ignoreCase = true))

                SQLiteDatabase.openDatabase(
                    path.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                ).use { saved ->
                    saved.rawQuery("SELECT full_path FROM favorites", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("/Pictures/旅行 #1.jpg", cursor.getString(0))
                        assertEquals(1, cursor.count)
                    }
                }
            } finally {
                GalleryDatabase.destroyInstance()
                context.deleteDatabase("gallery.db")
            }
        }
    }
}
