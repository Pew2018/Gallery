package org.fossify.gallery.helpers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors

/**
 * End-to-end shape of the P0-A regression with the real loader: a displayed 40-item
 * snapshot is refreshed while the source now holds 20 items. Before Phase 1.2 the
 * first FILESYSTEM batch replaced the grid with the freshly discovered rows only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE, application = Application::class,
    shadows = [PhotoProfileCacheEligibilityTest.FullAccessEnvironment::class])
class PhotoRefreshBaselineLoaderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: EmptyMediaProvider

    @Before fun prepare() {
        GalleryDatabase.destroyInstance()
        PhotoProfileMediaCache.invalidate()
        PhotoViewerSnapshotStore.clear()
        provider = Robolectric.buildContentProvider(EmptyMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    @After fun close() {
        provider.db.close()
        GalleryDatabase.destroyInstance()
    }

    private fun <T> background(block: () -> T): T {
        val worker = Executors.newSingleThreadExecutor()
        return try { worker.submit<T> { block() }.get() } finally { worker.shutdownNow() }
    }

    private fun profile(folder: String): String {
        val id = UUID.randomUUID().toString()
        PhotoProfileRepository.of(context).save(
            PhotoProfile(id, "Test", listOf(PhotoSource(PhotoSourceType.FOLDER, folder)))
        )
        return id
    }

    private fun baselineOf(profileId: String, media: List<Medium>, complete: Boolean): PhotoRefreshBaseline {
        val request = requireNotNull(PhotoProfileLoadContext.capture(context, profileId, null))
        return PhotoRefreshBaseline(
            scopeKey = request.scope.toString(),
            profileId = profileId,
            revision = request.revision,
            accessVersion = request.scope.accessVersion,
            sorting = request.scope.sorting,
            complete = complete,
            items = media,
        )
    }

    @Test fun firstFilesystemBatchKeepsTheDisplayedRowsAndCompleteStaysAuthoritative() {
        val root = Files.createTempDirectory("photo-refresh-baseline")
        try {
            val paths = (0 until 40).map { index ->
                Files.write(root.resolve("photo-$index.jpg"), byteArrayOf(index.toByte())).toString()
            }
            val id = profile(root.toString())
            val displayed = background { PhotoProfileMediaLoader(context).loadStaged(id, allowPreview = false) }
            assertEquals(PhotoLoadStage.COMPLETE, displayed.stage)
            assertEquals(40, displayed.media.size)

            // Twenty files disappear while the grid still shows all forty.
            paths.take(20).forEach { Files.delete(java.nio.file.Paths.get(it)) }

            val stages = ArrayList<PhotoLoadResult>()
            val final = background {
                PhotoProfileMediaLoader(context,
                    refreshBaseline = baselineOf(id, displayed.media, complete = false),
                    displayedGridSize = 40, baselineComplete = false,
                    // The user is at the end of the 40 shown rows, so a publication
                    // is due: the batch must extend the grid, not replace it.
                    browseHeadroom = { 3 })
                    .loadStaged(id, allowPreview = false, forceScan = true) { stages.add(it) }
            }

            val partial = stages.filter { it.stage == PhotoLoadStage.FILESYSTEM }
            assertTrue("no partial stage was published", partial.isNotEmpty())
            assertTrue("a partial stage shrank the grid: ${partial.map { it.media.size }}",
                partial.all { it.media.size >= 40 })
            assertEquals(PhotoLoadStage.COMPLETE, final.stage)
            assertEquals("COMPLETE must be the authoritative scan", 20, final.media.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun aCompleteBaselineSuppressesIntermediatePublicationsEntirely() {
        val root = Files.createTempDirectory("photo-refresh-complete")
        try {
            val paths = (0 until 40).map { index ->
                Files.write(root.resolve("photo-$index.jpg"), byteArrayOf(index.toByte())).toString()
            }
            val id = profile(root.toString())
            val displayed = background { PhotoProfileMediaLoader(context).loadStaged(id, allowPreview = false) }
            paths.take(20).forEach { Files.delete(java.nio.file.Paths.get(it)) }

            val stages = ArrayList<PhotoLoadResult>()
            val final = background {
                PhotoProfileMediaLoader(context,
                    refreshBaseline = baselineOf(id, displayed.media, complete = true),
                    displayedGridSize = 40, baselineComplete = true)
                    .loadStaged(id, allowPreview = false, forceScan = true) { stages.add(it) }
            }

            assertEquals("a complete grid must not be replaced by scan progress", 0,
                stages.count { it.stage == PhotoLoadStage.FILESYSTEM })
            assertEquals(PhotoLoadStage.COMPLETE, final.stage)
            assertEquals(20, final.media.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun anUnchangedCompleteRefreshCommitsTheSameAuthoritativeSet() {
        val root = Files.createTempDirectory("photo-refresh-unchanged")
        try {
            (0 until 30).forEach { index ->
                Files.write(root.resolve("photo-$index.jpg"), byteArrayOf(index.toByte()))
            }
            val id = profile(root.toString())
            val displayed = background { PhotoProfileMediaLoader(context).loadStaged(id, allowPreview = false) }
            val stages = ArrayList<PhotoLoadResult>()
            val final = background {
                PhotoProfileMediaLoader(context,
                    refreshBaseline = baselineOf(id, displayed.media, complete = true),
                    displayedGridSize = 30, baselineComplete = true)
                    .loadStaged(id, allowPreview = false, forceScan = true) { stages.add(it) }
            }

            assertEquals(0, stages.count { it.stage == PhotoLoadStage.FILESYSTEM })
            assertEquals(displayed.media.map { it.path }.toSet(), final.media.map { it.path }.toSet())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    /** No MediaStore rows: these cases exercise the filesystem supplement path only. */
    class EmptyMediaProvider : ContentProvider() {
        lateinit var db: android.database.sqlite.SQLiteDatabase
        override fun onCreate(): Boolean {
            db = android.database.sqlite.SQLiteDatabase.create(null)
            return true
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: arrayOf(MediaStore.Files.FileColumns._ID)
            return android.database.MatrixCursor(columns)
        }
        override fun getType(uri: Uri) = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
                            selectionArgs: Array<out String>?) = 0
    }
}
