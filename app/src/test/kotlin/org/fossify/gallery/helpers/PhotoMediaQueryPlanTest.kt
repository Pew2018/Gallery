package org.fossify.gallery.helpers

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoMediaQueryPlanTest {
    @Test fun sqlExcludesNestedFilesAndEscapesLiteralWildcardsAndBackslashes() {
        val folder = "/Pictures/a_%\\holiday"
        val paths = listOf("$folder/direct.jpg", "$folder/child/nested.jpg", "/Pictures/abXholiday/wrong.jpg")
        val db = SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE media (_data TEXT)")
            paths.forEach { db.execSQL("INSERT INTO media VALUES (?)", arrayOf(it)) }
            val query = PhotoMediaQueryPlan.folderQueries(setOf(folder), "_data").single()
            db.query("media", arrayOf("_data"), query.selection, query.args, null, null, null).use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(paths.first(), cursor.getString(0))
            }
        } finally { db.close() }
    }

    @Test fun directChildrenOnlyAndCaseSensitive() {
        val folders = setOf("/storage/emulated/0/DCIM/Camera")
        assertTrue(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/Camera/a.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/Camera/Child/b.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/camera/a.jpg", folders))
    }

    @Test fun largeSelectionsRemainBoundedAndEmptyScopeCannotQueryTheLibrary() {
        val folders = (0..100).map { "/Pictures/$it" }.toSet()
        val queries = PhotoMediaQueryPlan.folderQueries(folders, "_data")
        assertEquals(3, queries.size)
        assertTrue(queries.all { !it.selection.isNullOrBlank() && it.args!!.size <= 80 })
        assertEquals(202, queries.sumOf { it.args!!.size })
        assertTrue(PhotoMediaQueryPlan.folderQueries(emptySet(), "_data").isEmpty())
        assertTrue(PhotoMediaQueryPlan.pathQueries(emptySet(), "_data").isEmpty())
        val paths = (0..900).map { "/Camera/$it.jpg" }.toSet()
        val exact = PhotoMediaQueryPlan.pathQueries(paths, "_data")
        assertEquals(3, exact.size)
        assertEquals(paths, exact.flatMap { it.args!!.toList() }.toSet())
        assertTrue(exact.all { it.args!!.size <= 400 && "LIKE" !in it.selection!! })
    }
}
