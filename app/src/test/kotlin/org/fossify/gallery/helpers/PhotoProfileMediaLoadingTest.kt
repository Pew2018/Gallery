package org.fossify.gallery.helpers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.models.DateTaken
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowContentResolver
import java.nio.file.Files
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoProfileMediaLoadingTest {
    private lateinit var provider: IndexedMediaProvider
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        GalleryDatabase.destroyInstance()
        provider = Robolectric.buildContentProvider(IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun close() { provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun <T> background(block: () -> T): T {
        val worker = Executors.newSingleThreadExecutor()
        return try { worker.submit<T> { block() }.get() } finally { worker.shutdownNow() }
    }

    @Test fun oneCompleteQueryReusesMetadataAndCustomDatesAndSupplementsUnindexedFiles() {
        val root = Files.createTempDirectory("photo-loading")
        try {
            val indexed = Files.write(root.resolve("indexed.jpg"), byteArrayOf(1, 2, 3)).toString()
            val unindexed = Files.write(root.resolve("new.jpg"), byteArrayOf(4, 5, 6)).toString()
            val child = Files.createDirectory(root.resolve("child"))
            Files.write(child.resolve("nested.jpg"), byteArrayOf(1))
            provider.add(indexed, size = 321, taken = 4000)
            provider.add(child.resolve("nested.jpg").toString())
            val result = background {
                context.dateTakensDB.insertAll(listOf(DateTaken(null, indexed, "indexed.jpg", root.toString(), 9999, 0, 0)))
                MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(setOf(root.toString()), emptySet(), arrayListOf(indexed))
            }.associateBy { it.path }
            assertEquals(setOf(indexed, unindexed), result.keys)
            assertEquals(321L, result.getValue(indexed).size)
            assertEquals(9999L, result.getValue(indexed).taken)
            assertEquals(12000L, result.getValue(indexed).modified)
            assertTrue(result.getValue(indexed).isFavorite)
            assertEquals(3L, result.getValue(unindexed).size)
            assertEquals(1, provider.queries.size)
            assertEquals(7, provider.projections.single().size)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun favoritesUseExactFilesAndDoNotEnumerateTheirParents() {
        val root = Files.createTempDirectory("photo-favorites")
        try {
            val wanted = Files.write(root.resolve("favorite.jpg"), byteArrayOf(1)).toString()
            val missingIndex = Files.write(root.resolve("unindexed.jpg"), byteArrayOf(2)).toString()
            val other = Files.write(root.resolve("other.jpg"), byteArrayOf(3)).toString()
            provider.add(wanted)
            provider.add(other)
            val result = background { MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(
                emptySet(), setOf(wanted, missingIndex), arrayListOf(wanted, missingIndex)) }
            assertEquals(setOf(wanted, missingIndex), result.map { it.path }.toSet())
            assertTrue(result.all { it.isFavorite })
            assertEquals(1, provider.queries.size)
            assertTrue(provider.queries.single()!!.contains(" IN ("))
            assertFalse(provider.queries.single()!!.contains("LIKE"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test @Config(sdk = [31], shadows = [ScopedEnvironment::class]) fun scopedIndexSurvivesInaccessibleFileApiAndStillAddsUnindexedFiles() {
        val root = Files.createTempDirectory("photo-scoped")
        try {
            val indexedOnly = root.resolve("indexed-only.jpg").toString()
            val fileOnly = Files.write(root.resolve("file-only.jpg"), byteArrayOf(1)).toString()
            provider.add(indexedOnly)
            val result = background { MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(
                setOf(root.toString()), emptySet(), arrayListOf()) }
            assertEquals(setOf(indexedOnly, fileOnly), result.map { it.path }.toSet())
        } finally { root.toFile().deleteRecursively() }
    }

    /** Robolectric has no default volume for Environment's API-30 manager query. */
    @Implements(android.os.Environment::class)
    class ScopedEnvironment : ShadowEnvironment() {
        companion object {
            @JvmStatic @Implementation(minSdk = 30)
            fun isExternalStorageManager(): Boolean = false
        }
    }

    class IndexedMediaProvider : ContentProvider() {
        lateinit var db: SQLiteDatabase
        val queries = ArrayList<String?>()
        val projections = ArrayList<List<String>>()
        override fun onCreate(): Boolean {
            db = SQLiteDatabase.create(null)
            db.execSQL("CREATE TABLE media (_id INTEGER PRIMARY KEY, _display_name TEXT, _data TEXT, date_modified INTEGER, datetaken INTEGER, _size INTEGER, duration INTEGER)")
            return true
        }
        fun add(path: String, size: Long = 10, taken: Long = 4000) {
            db.execSQL("INSERT INTO media (_display_name, _data, date_modified, datetaken, _size, duration) VALUES (?, ?, 12, ?, ?, 0)",
                arrayOf(path.substringAfterLast('/'), path, taken, size))
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries.add(selection)
            projections.add(projection?.toList().orEmpty())
            return db.query("media", projection, selection, selectionArgs, null, null, sortOrder)
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
