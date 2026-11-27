package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Host filesystem + real MainActivity/adapter. Timings are NOT Android device measurements. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HomeSortingWorkRegressionTest {
    class BlockingDateProvider : android.content.ContentProvider() {
        lateinit var delegate: PhotoProfileMediaLoadingTest.IndexedMediaProvider
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        override fun onCreate() = true
        override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): android.database.Cursor {
            if (projection?.contains(android.provider.MediaStore.Images.Media.DATE_TAKEN) == true) {
                entered.countDown()
                check(release.await(20, TimeUnit.SECONDS)) { "test date query timed out" }
            }
            return delegate.query(uri, projection, selection, args, order)
        }
        override fun getType(uri: android.net.Uri): String? = null
        override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
        override fun delete(uri: android.net.Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("home publication did not settle", condition())
    }
    @Test fun warmAndRapidSortingDoNotEnumerateReadMetadataQueryOrRestartTheLiveScan() {
        val app = RuntimeEnvironment.getApplication()
        GalleryDatabase.destroyInstance()
        app.getSharedPrefs().edit().clear().commit()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        app.config.filterMedia = TYPE_IMAGES
        app.config.groupDirectSubfolders = false
        app.config.showAll = false
        app.config.directorySorting = SORT_BY_NAME
        val root = Files.createTempDirectory("home-sort-work").toFile()
        val albums = (0..2).map { File(root, "Album $it").apply { mkdirs() } }
        albums.forEachIndexed { album, folder ->
            File(folder, ".nomedia").createNewFile()
            repeat(120) { item -> File(folder, "$item.jpg").apply {
                writeBytes(ByteArray(item + 1)); setLastModified(1000L * (item + 1 + album * 10))
            } }
        }
        app.config.includedFolders = albums.mapTo(mutableSetOf()) { it.path }
        app.config.everShownFolders = albums.mapTo(mutableSetOf()) { it.path }
        val dbReads = AtomicInteger()
        val dbWrites = AtomicInteger()
        val measuredDb = androidx.room.Room.databaseBuilder(app, GalleryDatabase::class.java, "home-sort-measured.db")
            .setQueryCallback({ sql, _ ->
                val statement = sql.trimStart().uppercase(java.util.Locale.ROOT)
                if (statement.startsWith("SELECT")) dbReads.incrementAndGet()
                if (statement.startsWith("INSERT") || statement.startsWith("UPDATE") || statement.startsWith("DELETE")) dbWrites.incrementAndGet()
            }, java.util.concurrent.Executor { it.run() }).build()
        GalleryDatabase::class.java.getDeclaredField("db").apply { isAccessible = true }.set(null, measuredDb)
        val index = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        val provider = Robolectric.buildContentProvider(BlockingDateProvider::class.java)
            .create().get().also { it.delegate = index }
        ShadowContentResolver.registerProviderInternal("media", provider)
        val home = Robolectric.buildActivity(MainActivity::class.java,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)).create(Bundle()).start()
        val activity = home.get()
        val cacheReads = AtomicInteger()
        activity.directoryCacheReader = { cacheReads.incrementAndGet(); app.directoryDB.getAll() }
        var coldStarted = System.nanoTime()
        fun adapter() = activity.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter
        fun fetcher() = MainActivity::class.java.getDeclaredField("mLastMediaFetcher").apply { isAccessible = true }.get(activity) as? MediaFetcher
        val applySort = MainActivity::class.java.getDeclaredMethod("applyDirectorySorting").apply { isAccessible = true }
        val epoch = MainActivity::class.java.getDeclaredField("directoryScanEpoch").apply { isAccessible = true }.get(activity) as DirectoryScanEpoch
        val enumerations = AtomicInteger()
        val metadata = AtomicInteger()
        try {
            home.resume()
            await { provider.entered.count == 0L && fetcher() != null }
            val worker = fetcher()!!
            val original = worker.folderFileAccess
            worker.folderFileAccess = object : FolderFileAccess {
                override fun enumerate(folder: String): FolderEnumeration { enumerations.incrementAndGet(); return original.enumerate(folder) }
                override fun metadata(file: File): FolderFileMetadata { metadata.incrementAndGet(); return original.metadata(file) }
            }
            val clock = { System.nanoTime() / 1_000_000 }
            val diagnostics = MediaPipelineDiagnostics("test", "HOME", "INITIAL", "LEGACY", "test", 1, clock(), clock,
                eventSink = { _, _, _, _, _ -> })
            worker.pipelineDiagnostics = diagnostics
            val scanVersion = epoch.currentVersion()
            // Sort changes while the same worker is blocked must neither cancel it nor start another query.
            listOf(SORT_BY_DATE_MODIFIED, SORT_BY_SIZE, SORT_BY_DATE_TAKEN or SORT_DESCENDING).forEach {
                app.config.directorySorting = it; applySort.invoke(activity)
            }
            assertSame(worker, fetcher())
            assertFalse(worker.shouldStop)
            assertEquals(scanVersion, epoch.currentVersion())
            coldStarted = System.nanoTime()
            provider.release.countDown()
            await { adapter()?.dirs?.count { it.path in albums.map(File::getPath) && it.mediaCnt == 120 } == 3 }
            val firstMs = (System.nanoTime() - coldStarted) / 1_000_000.0
            await { !MainActivity::class.java.getDeclaredField("mIsGettingDirs").apply { isAccessible = true }.getBoolean(activity) }
            val readyMs = (System.nanoTime() - coldStarted) / 1_000_000.0
            // Let post-scan cache-pruning callbacks settle before measuring the sort-only window.
            repeat(10) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
            val before = listOf(enumerations.get(), metadata.get(), index.queries.size, cacheReads.get(), dbReads.get(), dbWrites.get())
            val beforeStats = diagnostics.snapshot()
            val modes = listOf(SORT_BY_NAME, SORT_BY_COUNT, SORT_BY_SIZE, SORT_BY_DATE_MODIFIED,
                SORT_BY_DATE_MODIFIED or SORT_DESCENDING, SORT_BY_DATE_TAKEN, SORT_BY_DATE_TAKEN or SORT_DESCENDING)
            for (mode in modes) {
                app.config.directorySorting = mode
                val started = System.nanoTime()
                applySort.invoke(activity)
                await { adapter()?.directorySorting == mode &&
                    MainActivity::class.java.getDeclaredField("directoryPresentationDiscard").apply { isAccessible = true }.get(activity) == null }
                println("HomeSortingWork host mode=$mode choiceToCommitMs=${(System.nanoTime()-started)/1_000_000.0}")
            }
            repeat(20) { app.config.directorySorting = modes[it % modes.size]; applySort.invoke(activity) }
            app.config.directorySorting = SORT_BY_SIZE or SORT_DESCENDING
            applySort.invoke(activity)
            await { adapter()?.directorySorting == app.config.directorySorting &&
                MainActivity::class.java.getDeclaredField("directoryPresentationDiscard").apply { isAccessible = true }.get(activity) == null }
            val regular = adapter()!!.dirs.filter { it.path in albums.map(File::getPath) }
            assertEquals(360, regular.sumOf { it.mediaCnt })
            assertTrue(regular.all { it.stats.has(org.fossify.gallery.models.DirectoryStats.ALL) })
            assertEquals(before, listOf(enumerations.get(), metadata.get(), index.queries.size, cacheReads.get(), dbReads.get(), dbWrites.get()))
            assertEquals(beforeStats, diagnostics.snapshot())
            assertSame(worker, fetcher())
            assertEquals(scanVersion, epoch.currentVersion())
            println("HomeSortingWork host albums=3 media=360 firstUsableMs=$firstMs fullStatsMs=$readyMs " +
                "coldCounters=$before warmAndRapidDelta=[0,0,0,0,0,0] scanner=${diagnostics.snapshot()}")
            val dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val unaffected = dbExecutor.submit<List<org.fossify.gallery.models.Directory>> {
                    app.directoryDB.getByPaths(albums.drop(1).map(File::getPath))
                }.get()
                val beforeQueries = index.queries.size
                val beforeWrites = dbWrites.get()
                File(albums.first(), "added.jpg").writeBytes(byteArrayOf(1, 2, 3))
                val changedAt = System.nanoTime()
                dbExecutor.submit { app.updateDirectoryPath(albums.first().path) }.get(10, TimeUnit.SECONDS)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                await { adapter()?.dirs?.any { it.path == albums.first().path && it.mediaCnt == 121 } == true }
                assertEquals(unaffected, dbExecutor.submit<List<org.fossify.gallery.models.Directory>> {
                    app.directoryDB.getByPaths(albums.drop(1).map(File::getPath))
                }.get())
                assertSame(worker, fetcher())
                assertEquals(scanVersion, epoch.currentVersion())
                assertEquals(1, dbWrites.get() - beforeWrites)
                println("HomeSortingWork singleAlbum changedCount=121 untouchedRows=2 homeScanRestart=false " +
                    "updateToCommitMs=${(System.nanoTime()-changedAt)/1_000_000.0} providerQueriesDelta=${index.queries.size-beforeQueries} dbWritesDelta=${dbWrites.get()-beforeWrites}")
            } finally { dbExecutor.shutdownNow() }
        } finally {
            provider.release.countDown(); home.pause().stop().destroy(); root.deleteRecursively()
            index.db.close(); GalleryDatabase.destroyInstance(); app.deleteDatabase("home-sort-measured.db")
        }
    }
}
