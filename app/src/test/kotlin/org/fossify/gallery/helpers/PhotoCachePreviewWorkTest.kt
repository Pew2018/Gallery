package org.fossify.gallery.helpers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.MediaStore
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Phase 1.1 first-slice work budget.
 *
 * The production defect was not the sort (about 1 ms for 5000 rows) but the
 * existence filter in front of it (938-2416 ms on a Pixel 8 Pro). These tests
 * measure the *work* the first slice performs, not wall-clock time on a host JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoCachePreviewWorkTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun prepare() {
        GalleryDatabase.destroyInstance()
        PhotoProfileMediaCache.invalidate()
    }

    @After fun close() {
        PhotoProfileMediaCache.invalidate()
        GalleryDatabase.destroyInstance()
    }

    private fun item(index: Int, folder: String = "/photos") = Medium(
        index.toLong() + 1, "photo-$index.jpg", "$folder/photo-$index.jpg", folder,
        1_000L - index, 1_000L - index, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    private fun catalog(size: Int, folder: String = "/photos") = (0 until size).map { item(it, folder) }

    private class Counter { var checks = 0 }

    // B01: an ordered in-memory catalog only inspects the window it can show.
    @Test fun orderedCatalogChecksOnlyTheFirstSlice() {
        val cached = catalog(5_000)
        val counter = Counter()
        val slice = CachedMediaPreviewOrder.selectBounded(
            cached, SORT_BY_DATE_TAKEN, anchorPath = null, visible = { counter.checks++; true }
        )!!
        assertEquals(CachedMediaPreviewOrder.LIMIT, slice.items.size)
        assertEquals("a 160-row slice must not stat 5000 files", 160, slice.checks)
        assertEquals(160, counter.checks)
        assertTrue(slice.complete)
        assertEquals(cached.take(160).map { it.path }, slice.items.map { it.path })
    }

    // B02: Room rows are unordered; ordering is cheap, so the walk happens afterwards.
    @Test fun unorderedRoomRowsAreOrderedBeforeTheBoundedWalk() {
        val rows = catalog(5_000).shuffled(java.util.Random(7))
        val ordered = CachedMediaPreviewOrder.order(rows, SORT_BY_DATE_TAKEN, MediaFetcher(context))
        assertEquals(5_000, ordered.size)
        val counter = Counter()
        val slice = CachedMediaPreviewOrder.selectBounded(
            ordered, SORT_BY_DATE_TAKEN, anchorPath = null, visible = { counter.checks++; true }
        )!!
        assertEquals(160, slice.checks)
        assertEquals(ordered.take(160).map { it.path }, slice.items.map { it.path })
        assertEquals("descending date order must be preserved", ordered.first().path, slice.items.first().path)
    }

    // B03: deleted rows inside the window are skipped and replaced in order.
    @Test fun missingCandidatesAreSkippedAndBackfilledInOrder() {
        val cached = catalog(400)
        val missing = cached.filterIndexed { index, _ -> index in setOf(3, 17, 120) }.map { it.path }.toSet()
        val slice = CachedMediaPreviewOrder.selectBounded(
            cached, SORT_BY_DATE_TAKEN, anchorPath = null, visible = { it.path !in missing }
        )!!
        assertEquals(CachedMediaPreviewOrder.LIMIT, slice.items.size)
        assertTrue(slice.items.none { it.path in missing })
        val expected = cached.filter { it.path !in missing }.take(160).map { it.path }
        assertEquals(expected, slice.items.map { it.path })
        assertEquals(163, slice.checks)
    }

    // B04: a mostly-invalid window degrades to bounded work instead of scanning everything.
    @Test fun mostlyInvalidCatalogStopsAtTheWorkBudget() {
        val cached = catalog(12_000)
        val slice = CachedMediaPreviewOrder.selectBounded(
            cached, SORT_BY_DATE_TAKEN, anchorPath = null, visible = { false }
        )!!
        assertEquals(0, slice.items.size)
        assertFalse(slice.complete)
        assertTrue(
            "work must stay bounded by the budget, not by the catalog size (${slice.checks})",
            slice.checks <= CachedMediaPreviewOrder.LIMIT * CachedMediaPreviewOrder.CHECK_BUDGET_MULTIPLIER
        )
    }

    // B05: everything invalid is an empty, explicitly incomplete slice, never an exception.
    @Test fun fullyInvalidCatalogYieldsAnEmptyIncompleteSlice() {
        val slice = CachedMediaPreviewOrder.selectBounded(
            catalog(5_000), SORT_BY_DATE_TAKEN, anchorPath = null, visible = { false }
        )!!
        assertTrue(slice.items.isEmpty())
        assertFalse(slice.complete)
    }

    // B06: an unreadable parent keeps the conservative "still eligible" semantics.
    @Test fun unreadableParentKeepsTheRowEligible() {
        val missing = Medium(1, "a.jpg", "/does/not/exist/a.jpg", "/does/not/exist", 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
        assertTrue("an unverifiable row must not be treated as deleted", CachedMediaPreviewFilter.isVisible(missing))
        val real = Files.createTempDirectory("preview-readable").toFile()
        try {
            val file = Files.write(real.toPath().resolve("a.jpg"), byteArrayOf(1)).toFile()
            val present = Medium(1, "a.jpg", file.path, real.path, 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            assertTrue(CachedMediaPreviewFilter.isVisible(present))
            file.delete()
            assertFalse(CachedMediaPreviewFilter.isVisible(present))
        } finally {
            real.deleteRecursively()
        }
    }

    // B08: random sorting keeps the previous, provably-equivalent path.
    @Test fun randomSortingNeverUsesTheBoundedWalk() {
        assertNull(CachedMediaPreviewOrder.selectBounded(catalog(500), SORT_BY_RANDOM, anchorPath = null))
    }

    // B09: the anchor keeps a quarter window of leading context.
    @Test fun anchorKeepsLeadingContextInTheBoundedWalk() {
        val cached = catalog(1_000)
        val slice = CachedMediaPreviewOrder.selectBounded(
            cached, SORT_BY_DATE_TAKEN, anchorPath = cached[600].path, visible = { true }
        )!!
        assertEquals(cached[560].path, slice.items.first().path)
        assertEquals(CachedMediaPreviewOrder.LIMIT, slice.items.size)
    }

    // B09: an unknown anchor falls back to the head of the catalog, never to a random offset.
    @Test fun unknownAnchorFallsBackToTheHeadOfTheCatalog() {
        val cached = catalog(300)
        val slice = CachedMediaPreviewOrder.selectBounded(
            cached, SORT_BY_DATE_TAKEN, anchorPath = "/photos/absent.jpg", visible = { true }
        )!!
        assertEquals(cached.first().path, slice.items.first().path)
    }

    // B11: explicit refresh keeps the same first-slice path as a plain entry.
    @Test fun refreshAndEnterShareTheSameBoundedSelection() {
        val cached = catalog(2_000)
        val enter = CachedMediaPreviewOrder.selectBounded(cached, SORT_BY_DATE_TAKEN, null)!!
        val refresh = CachedMediaPreviewOrder.selectBounded(cached, SORT_BY_DATE_TAKEN, null)!!
        assertEquals(enter.items.map { it.path }, refresh.items.map { it.path })
        assertEquals(enter.checks, refresh.checks)
    }

    // The end-to-end cold path: 5000 Room rows must not be existence-checked in full.
    @Test @Config(sdk = [31], manifest = Config.NONE, application = Application::class,
        shadows = [PhotoProfileCacheEligibilityTest.FullAccessEnvironment::class])
    fun coldRoomPreviewValidatesABoundedWindowOnly() {
        val provider = Robolectric.buildContentProvider(IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val root = Files.createTempDirectory("preview-cold-room")
        try {
            val real = (0 until 4).map { index ->
                Files.write(root.resolve("real-$index.jpg"), byteArrayOf(index.toByte())).toString()
            }
            // 5000 cached rows that no longer exist, inserted so they sort after the real files.
            val ghostRows = (0 until 5_000).map { index ->
                Medium(null, "ghost-$index.jpg", "${root}/ghost-$index.jpg", root.toString(),
                    1L, 1L, 1L, TYPE_IMAGES, 0, false, 0, 0)
            }
            val realRows = real.mapIndexed { index, path ->
                Medium(null, java.io.File(path).name, path, root.toString(),
                    1L, 9_000L + index, 9_000L + index, TYPE_IMAGES, 0, false, 0, 0)
            }
            background { context.mediaDB.insertAll(realRows + ghostRows) }

            val id = UUID.randomUUID().toString()
            PhotoProfileRepository.of(context).save(
                PhotoProfile(id, "Test", listOf(PhotoSource(PhotoSourceType.FOLDER, root.toString())))
            )
            val diagnostics = pipelineDiagnostics()
            val stages = ArrayList<PhotoLoadResult>()
            val final = background {
                PhotoProfileMediaLoader(context, MediaFetcher(context), null, { false }, diagnostics)
                    .loadStaged(id, allowPreview = true) { stages.add(it) }
            }

            val metrics = diagnostics.snapshot()
            assertEquals("the cold path must use the bounded walk", 1L, metrics["cachePreviewBoundedCount"])
            assertNull("the full missing-file filter must no longer run", metrics["cachePreviewFullFilterCount"])
            val checks = metrics.getValue("cachePreviewExistenceChecks")
            assertTrue("cold preview checked $checks candidates", checks <= 640L)
            assertTrue("cold preview should have degraded here", metrics.getValue("cachePreviewDegradedCount") >= 1L)
            assertTrue("a degraded preview must not publish an empty stage", stages.none {
                it.stage == PhotoLoadStage.CACHE_PREVIEW && it.media.isEmpty()
            })
            assertEquals(PhotoLoadStage.COMPLETE, final.stage)
            assertEquals(real.toSet(), final.media.map { it.path }.toSet())
        } finally {
            root.toFile().deleteRecursively()
            provider.db.close()
        }
    }

    // The acceptance shape: in ALL_FILES a second entry reuses the ordered cache.
    @Test @Config(sdk = [31], manifest = Config.NONE, application = Application::class,
        shadows = [PhotoProfileCacheEligibilityTest.FullAccessEnvironment::class])
    fun fullAccessHotCachePublishesTheBoundedOrderedSliceWithoutReadingRoom() {
        val provider = Robolectric.buildContentProvider(IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val root = Files.createTempDirectory("preview-hot-allfiles")
        try {
            val paths = (0 until 40).map { index ->
                Files.write(root.resolve("photo-$index.jpg"), byteArrayOf(index.toByte())).toString()
            }
            paths.forEach { provider.add(it) }
            val id = UUID.randomUUID().toString()
            PhotoProfileRepository.of(context).save(
                PhotoProfile(id, "Test", listOf(PhotoSource(PhotoSourceType.FOLDER, root.toString())))
            )
            assertEquals(PhotoLoadStage.COMPLETE,
                background { PhotoProfileMediaLoader(context).loadStaged(id, allowPreview = false) }.stage)

            val diagnostics = pipelineDiagnostics()
            val stages = ArrayList<PhotoLoadResult>()
            var dbRowsReadBeforePreview = -1L
            val final = background {
                PhotoProfileMediaLoader(context, MediaFetcher(context), null, { false }, diagnostics)
                    .loadStaged(id, allowPreview = true) { stage ->
                        if (stages.isEmpty()) dbRowsReadBeforePreview = diagnostics.snapshot()["dbReadRows"] ?: 0L
                        stages.add(stage)
                    }
            }

            val metrics = diagnostics.snapshot()
            val preview = stages.firstOrNull { it.stage == PhotoLoadStage.CACHE_PREVIEW }
            assertNotNull("ALL_FILES must keep a usable ordered preview", preview)
            assertEquals(paths.size, preview!!.media.size)
            assertEquals("the ordered cache must be reused, not rebuilt", 1L, metrics["cachePreviewEligibleCount"])
            assertEquals(1L, metrics["cachePreviewBoundedCount"])
            assertEquals("completeness is still unproven without a checkpoint", 1L,
                metrics["cacheMissCheckpointUnavailableCount"])
            assertEquals("no preview entry may be evicted for missing completeness", 0L,
                metrics["cacheEvictionCount"] ?: 0L)
            assertEquals("the preview must not wait for a Room read", 0L, dbRowsReadBeforePreview)
            assertEquals(PhotoLoadStage.COMPLETE, final.stage)
            assertEquals(paths.toSet(), final.media.map { it.path }.toSet())
        } finally {
            root.toFile().deleteRecursively()
            provider.db.close()
        }
    }

    private fun pipelineDiagnostics() = MediaPipelineDiagnostics(
        "pp-test", "PHOTO_PROFILE", "INITIAL", "ALL_FILES",
        PhotoDiagnosticIdentity.scopeKey("test"), 1L, android.os.SystemClock.elapsedRealtime(),
        android.os.SystemClock::elapsedRealtime
    ) { _, _, _, _, _ -> }

    private fun <T> background(block: () -> T): T {
        val worker = Executors.newSingleThreadExecutor()
        return try { worker.submit<T> { block() }.get() } finally { worker.shutdownNow() }
    }

    class IndexedMediaProvider : ContentProvider() {
        lateinit var db: SQLiteDatabase
        override fun onCreate(): Boolean {
            db = SQLiteDatabase.create(null)
            db.execSQL("CREATE TABLE media (_id INTEGER PRIMARY KEY, _display_name TEXT, _data TEXT, date_modified INTEGER, datetaken INTEGER, _size INTEGER, duration INTEGER)")
            return true
        }
        fun add(path: String, size: Long = 10, taken: Long = 4000) {
            db.execSQL(
                "INSERT INTO media (_display_name, _data, date_modified, datetaken, _size, duration) VALUES (?, ?, 12, ?, ?, 0)",
                arrayOf(path.substringAfterLast('/'), path, taken, size)
            )
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            db.query("media", projection, selection, selectionArgs, null, null, sortOrder)
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
