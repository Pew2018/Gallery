package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.Favorite
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
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

/** Real Activity refresh listener -> AsyncTask -> publication -> MediaAdapter.
 * Host filesystem only: these tests make no Android storage permission completeness claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FolderRefreshActivityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var root: File
    private lateinit var provider: PhotoProfileMediaLoadingTest.IndexedMediaProvider
    private val dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private fun databaseCount(): Int? = dbExecutor.submit<Int?> {
        context.directoryDB.getByPath(root.path)?.mediaCnt
    }.get(5, TimeUnit.SECONDS)
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        context.config.filterMedia = TYPE_IMAGES
        root = Files.createTempDirectory("activity-nomedia").toFile()
        File(root, ".nomedia").createNewFile()
        provider = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun cleanup() { dbExecutor.shutdownNow(); root.deleteRecursively(); provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun photo(name: String) = File(root, name).apply { writeBytes(byteArrayOf(1, 2)) }
    private fun field(activity: MediaActivity, name: String) = MediaActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun paths(activity: MediaActivity) = (activity.findViewById<MyRecyclerView>(R.id.media_grid).adapter as? MediaAdapter)
        ?.media.orEmpty().filterIsInstance<Medium>().map { it.path }.toSet()
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Activity/Adapter did not reach the expected state", condition())
    }
    private fun refresh(activity: MediaActivity) {
        val swipe = activity.findViewById<SwipeRefreshLayout>(R.id.media_refresh_layout)
        val listener = SwipeRefreshLayout::class.java.getDeclaredField("mListener").apply { isAccessible = true }
            .get(swipe) as SwipeRefreshLayout.OnRefreshListener
        listener.onRefresh()
    }
    private fun withActivity(block: (MediaActivity) -> Unit) {
        val intent = Intent(context, MediaActivity::class.java).putExtra(DIRECTORY, root.path)
        val controller = Robolectric.buildActivity(MediaActivity::class.java, intent).create(Bundle()).start()
        try {
            val activity = controller.get()
            // Skip the unrelated startup Room preview; the refresh itself runs its real background scanner.
            field(activity, "mLoadedInitialPhotos").setBoolean(activity, true)
            block(activity)
            (field(activity, "mCurrAsyncTask").get(activity) as? org.fossify.gallery.asynctasks.GetMediaAsynctask)?.stopFetching()
        } finally { controller.stop().destroy() }
    }

    @Test fun initialFolderLoadingTracksPublishedMediaRatherThanAdapterExistence() {
        val controller = Robolectric.buildActivity(MediaActivity::class.java,
            Intent(context, MediaActivity::class.java).putExtra(DIRECTORY, root.path)).create(Bundle())
        try {
            val activity = controller.get()
            val resolver = activity.contentResolver
            val oldAnimatorScale = android.provider.Settings.Global.getFloat(
                resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            try {
                val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
                val adapter = MediaAdapter(activity, arrayListOf(), activity, false, false, root.path, grid) {}
                grid.adapter = adapter
                val setLoading = MediaActivity::class.java.getDeclaredMethod(
                    "setMediaLoading", Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }
                val central = activity.findViewById<View>(R.id.loading_indicator)
                val header = field(activity, "mediaHeaderProgress").get(activity) as View
                assertNull("a normal folder has no root navigation section", field(activity, "mGallerySection").get(activity))
                assertSame(adapter, grid.adapter)
                assertFalse(activity.isFinishing)
                OxygenProgressStyle.dispose(central as com.google.android.material.progressindicator.BaseProgressIndicator<*>)
                OxygenProgressStyle.dispose(header as com.google.android.material.progressindicator.BaseProgressIndicator<*>)

                setLoading.invoke(activity, true)
                assertEquals("an empty adapter is still waiting for real content", View.VISIBLE, central.visibility)
                assertEquals(View.GONE, header.visibility)

                adapter.media.add(Medium().apply {
                    path = File(root, "first.jpg").path
                    parentPath = root.path
                    name = "first.jpg"
                    type = TYPE_IMAGES
                })
                setLoading.invoke(activity, true)
                assertEquals("published media immediately retires the central spinner", View.GONE, central.visibility)
                assertEquals("background discovery stays quiet after media is visible", View.GONE, header.visibility)

                setLoading.invoke(activity, false)
                assertEquals(View.GONE, central.visibility)
                assertEquals(View.GONE, header.visibility)
            } finally {
                android.provider.Settings.Global.putFloat(resolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, oldAnimatorScale)
            }
        } finally { controller.destroy() }
    }

    @Test fun pullRefreshAddsCopiedNoMediaImageToTheActualAdapterAndHomeCard() {
        val old = photo("old.jpg")
        context.config.includedFolders = mutableSetOf(root.path)
        context.config.showAll = false
        context.config.groupDirectSubfolders = false
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
        val home = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create(Bundle()).start().resume()
        try {
            home.pause()
            withActivity { activity ->
                refresh(activity)
                await { old.path in paths(activity) }
                val fresh = photo("NEW.JPG")
                refresh(activity)
                await { paths(activity) == setOf(old.path, fresh.path) }
                assertFalse(activity.isFinishing)
                // The adapter can be ahead of the Room transaction.
                await { databaseCount() == 2 }
            }
            home.resume()
            val main = home.get()
            await {
                (main.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter)
                    ?.dirs?.any { it.path == root.path && it.mediaCnt == 2 } == true
            }
            assertEquals(2, databaseCount())
        } finally {
            home.pause().stop().destroy()
        }
    }
    @Test fun emptyFilteredRefreshKeepsFolderAndDoesNotFinish() {
        photo("movie.mp4")
        context.config.deleteEmptyFolders = true
        withActivity { activity ->
            refresh(activity)
            await { !field(activity, "mIsGettingMedia").getBoolean(activity) }
            assertTrue(root.isDirectory)
            assertTrue(File(root, "movie.mp4").isFile)
            assertFalse(activity.isFinishing)
            assertTrue(paths(activity).isEmpty())
        }
    }
    @Test fun unavailableDirectoryKeepsOldAdapterAndDoesNotFinish() {
        val old = photo("old.jpg")
        withActivity { activity ->
            refresh(activity)
            await { old.path in paths(activity) }
            val parked = File(root.parentFile, root.name + "-parked")
            assertTrue(root.renameTo(parked))
            try {
                refresh(activity)
                await { !field(activity, "mIsGettingMedia").getBoolean(activity) }
                assertEquals(setOf(old.path), paths(activity))
                assertFalse(activity.isFinishing)
            } finally { parked.renameTo(root) }
        }
    }
    @Test fun lateOlderPublicationCannotReplaceLatestAdapter() {
        val fresh = photo("fresh.jpg")
        withActivity { activity ->
            refresh(activity)
            await { fresh.path in paths(activity) }
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val stale = requests.begin()
            requests.begin()
            MediaActivity::class.java.getDeclaredMethod("publishMedia", ArrayList::class.java,
                Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, arrayListOf<org.fossify.gallery.models.ThumbnailItem>(Medium().apply { path = "${root.path}/stale.jpg" }), false, stale)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(setOf(fresh.path), paths(activity))
        }
    }
    @Test fun slowFilePublishesNewImageBeforeTimeoutAndLateScanCannotOverwriteAdapter() {
        val fresh = photo("new.jpg")
        val late = photo("late.jpg")
        val occupied = java.util.concurrent.CountDownLatch(2)
        val start = java.util.concurrent.CountDownLatch(1)
        val workersReleased = java.util.concurrent.CountDownLatch(2)
        val blocked = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val returned = java.util.concurrent.CountDownLatch(1)
        try {
            repeat(2) { GalleryMediaScanExecutor.execute {
                occupied.countDown(); start.await(5, TimeUnit.SECONDS); workersReleased.countDown()
            } }
            assertTrue(occupied.await(2, TimeUnit.SECONDS))
            MediaActivity.mMedia = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(Medium().apply {
                path = "/unrelated-album/old.jpg"; parentPath = "/unrelated-album"; name = "old.jpg"; type = TYPE_IMAGES
            })
            withActivity { activity ->
                refresh(activity)
                val task = field(activity, "mCurrAsyncTask").get(activity) as org.fossify.gallery.asynctasks.GetMediaAsynctask
                val fetcher = task.javaClass.getDeclaredField("mediaFetcher").apply { isAccessible = true }.get(task) as MediaFetcher
                fetcher.folderFileAccess = object : FolderFileAccess {
                    override fun enumerate(folder: String) = FolderEnumeration(listOf(fresh, late))
                    override fun metadata(file: File): FolderFileMetadata {
                        if (file == late) {
                            blocked.countDown()
                            // Model a native read that ignores interruption, but release it deterministically in the test.
                            while (release.count > 0) try { release.await(1, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
                            returned.countDown()
                        }
                        return FolderFileMetadata(2, 1, true, true)
                    }
                }
                start.countDown()
                assertTrue(blocked.await(5, TimeUnit.SECONDS))
                await { fresh.path in paths(activity) }
                assertTrue("preview incorrectly marked the full scan complete", field(activity, "mIsGettingMedia").getBoolean(activity))
                assertEquals("a committed first batch must not keep the central ring visible",
                    View.GONE, activity.findViewById<View>(R.id.loading_indicator).visibility)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(46, TimeUnit.SECONDS)
                assertFalse(field(activity, "mIsGettingMedia").getBoolean(activity))
                assertFalse(activity.isFinishing)
                assertEquals(setOf(fresh.path), paths(activity))
                val indicator = field(activity, "mediaHeaderProgress").get(activity) as android.view.View
                assertEquals(android.view.View.GONE, indicator.visibility)
                release.countDown()
                assertTrue(returned.await(2, TimeUnit.SECONDS))
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(setOf(fresh.path), paths(activity))
                assertTrue(root.isDirectory)
            }
        } finally {
            start.countDown(); release.countDown()
            assertTrue(workersReleased.await(5, TimeUnit.SECONDS))
        }
    }


    @Test fun homeCacheQueryFailureStillRunsFormalScanAndCommitsTheAlbum() {
        val photo = photo("new.jpg")
        context.config.includedFolders = mutableSetOf(root.path)
        context.config.showAll = false
        context.config.groupDirectSubfolders = false
        val home = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true))
            .create(Bundle()).start()
        val queries = java.util.concurrent.atomic.AtomicInteger()
        home.get().directoryCacheReader = { queries.incrementAndGet(); throw java.io.IOException("injected Room query failure") }
        try {
            home.resume()
            val main = home.get()
            MainActivity::class.java.getDeclaredMethod("getDirectories", Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(main, false, true)
            await {
                (main.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter)
                    ?.dirs?.any { it.path == root.path && it.mediaCnt == 1 } == true
            }
            await { !MainActivity::class.java.getDeclaredField("mIsGettingDirs").apply { isAccessible = true }.getBoolean(main) }
            assertTrue(queries.get() >= 1)
            assertTrue(queries.get() <= 2) // startup/resume, no cache retry loop
            assertEquals(1, databaseCount())
            assertTrue(photo.isFile)
        } finally { home.pause().stop().destroy() }
    }

    @Test fun categoryCacheQueryFailureFallsBackToFormalFilesystemScan() {
        val photo = photo("categorized.jpg")
        context.config.includedFolders = mutableSetOf(root.path)
        context.config.everShownFolders = setOf(root.path)
        val home = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true))
            .create(Bundle()).start()
        val queries = java.util.concurrent.atomic.AtomicInteger()
        try {
            val main = home.get()
            MainActivity::class.java.getDeclaredField("mFolderVisibility").apply { isAccessible = true }
                .set(main, booleanArrayOf(false, false, true, true))
            main.directoryCacheReader = { queries.incrementAndGet(); throw java.io.IOException("injected category cache failure") }
            MainActivity::class.java.getDeclaredMethod("loadCategorizedFolders").apply { isAccessible = true }.invoke(main)
            await {
                (main.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter)
                    ?.dirs?.any { it.path == root.path && it.mediaCnt == 1 } == true
            }
            await { !MainActivity::class.java.getDeclaredField("mIsGettingDirs").apply { isAccessible = true }.getBoolean(main) }
            assertEquals(1, queries.get())
            assertTrue(photo.isFile)
        } finally { home.pause().stop().destroy() }
    }

    @Test fun roomPreviewPaintsWhileBothScanWorkersAreOccupied() {
        val cached = photo("cached-first.jpg")
        val later = photo("disk-second.jpg")
        dbExecutor.submit {
            context.mediaDB.insert(Medium().apply {
                name = cached.name
                path = cached.path
                parentPath = root.path
                size = cached.length()
                type = TYPE_IMAGES
            })
        }.get(5, TimeUnit.SECONDS)
        val occupied = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            repeat(2) { GalleryMediaScanExecutor.execute {
                occupied.countDown()
                release.await(10, TimeUnit.SECONDS)
            } }
            assertTrue(occupied.await(3, TimeUnit.SECONDS))
            withActivity { activity ->
                refresh(activity)
                await { paths(activity) == setOf(cached.path) }
                assertTrue(field(activity, "mIsGettingMedia").getBoolean(activity))
                assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
                release.countDown()
                await { paths(activity) == setOf(cached.path, later.path) &&
                    field(activity, "displayedMediaIsFinal").getBoolean(activity) }
            }
        } finally { release.countDown() }
    }

    @Test fun allFilesIndexPublishesBeforeBlockedDiskReadAndExcludesHiddenAndProtectedRows() {
        File(root, ".nomedia").delete()
        val indexed = photo("indexed.jpg")
        val hidden = photo(".hidden.jpg")
        val protected = File(root, "protected").apply { mkdirs() }
        val protectedPhoto = File(protected, "locked.jpg").apply { writeBytes(byteArrayOf(1)) }
        context.config.addFolderProtection(protected.path, "test", org.fossify.commons.helpers.PROTECTION_PIN)
        val unindexed = photo("unindexed.jpg")
        listOf(indexed, hidden, protectedPhoto).forEach { provider.add(it.path) }
        val blocked = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val previews = arrayListOf<MediaPageBatch>()
        var final: List<Medium>? = null
        val task = org.fossify.gallery.asynctasks.GetMediaAsynctask(context, "", showAll = true,
            pagePreviewTarget = 1, onPagePreview = { previews.add(it) }) { final = it.filterIsInstance<Medium>() }
        val fetcher = task.javaClass.getDeclaredField("mediaFetcher").apply { isAccessible = true }.get(task) as MediaFetcher
        fetcher.folderFileAccess = object : FolderFileAccess {
            override fun enumerate(folder: String): FolderEnumeration {
                if (folder != root.path) return FolderEnumeration(emptyList())
                blocked.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return FolderEnumeration(listOf(indexed, unindexed, hidden))
            }
            override fun metadata(file: File) = FolderFileMetadata(2, 1, true, true)
        }
        try {
            task.executeOnExecutor(GalleryMediaScanExecutor)
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            await { previews.isNotEmpty() }
            assertEquals(DisplayedSourceStage.INDEX_PREVIEW, previews.first().stage)
            assertEquals(listOf(indexed.path), previews.first().rows.map { it.path })
            assertNull(final)
            release.countDown()
            await { final != null }
            assertEquals(setOf(indexed.path, unindexed.path), final!!.map { it.path }.toSet())
            assertTrue(task.scanResult.complete)
        } finally { release.countDown(); task.stopFetching() }
    }

    @Test fun allFilesRetainsFailedFolderCacheAndNeverClaimsACompleteViewerCatalog() {
        val fresh = photo("good.jpg")
        val failed = File(root, "failed").apply { mkdirs() }
        val old = File(failed, "old.jpg").apply { writeBytes(byteArrayOf(1)) }
        context.config.includedFolders = mutableSetOf(root.path)
        context.config.showAll = true
        val retained = Medium().apply { path = old.path; parentPath = failed.path; name = old.name; type = TYPE_IMAGES }
        var published: ArrayList<org.fossify.gallery.models.ThumbnailItem>? = null
        var failure: Exception? = null
        val task = org.fossify.gallery.asynctasks.GetMediaAsynctask(context, "", showAll = true,
            cachedMediaFallback = listOf(retained), onFailure = { failure = it }) { published = it }
        val fetcher = task.javaClass.getDeclaredField("mediaFetcher").apply { isAccessible = true }.get(task) as MediaFetcher
        fetcher.folderFileAccess = object : FolderFileAccess {
            override fun enumerate(folder: String): FolderEnumeration {
                if (folder == failed.path) throw java.io.IOException("injected offline folder")
                return FolderEnumeration(if (folder == root.path) listOf(fresh) else emptyList())
            }
            override fun metadata(file: File) = FolderFileMetadata(2, 1, true, true)
        }
        try {
            task.executeOnExecutor(GalleryMediaScanExecutor)
            await { published != null || failure != null }
            assertNull(failure)
            assertEquals(setOf(fresh.path, old.path), published!!.filterIsInstance<Medium>().map { it.path }.toSet())
            assertEquals(GalleryScanState.PARTIAL, task.scanResult.state)
            assertTrue(failed.path in task.scanResult.failedPaths)
        } finally { task.stopFetching() }
    }

    @Test fun partialAllFilesPublicationAndSearchDoNotSetDisplayedMediaFinal() {
        val photo = photo("visible.jpg")
        withActivity { activity ->
            refresh(activity)
            await { photo.path in paths(activity) }
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            (field(activity, "mediaScanTerminals").get(activity) as GalleryScanTerminalGate).begin(request)
            field(activity, "mShowAll").setBoolean(activity, true)
            field(activity, "mediaScanOutcomeRequest").setLong(activity, request)
            field(activity, "mediaScanOutcome").set(activity,
                GalleryScanResult(GalleryScanState.PARTIAL, setOf("/offline")))
            val media = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(Medium().apply {
                path = photo.path; parentPath = root.path; name = photo.name; type = TYPE_IMAGES
            })
            MediaActivity::class.java.getDeclaredMethod("publishMedia", ArrayList::class.java,
                Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, media, false, request)
            await { !field(activity, "mIsGettingMedia").getBoolean(activity) }
            assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
            assertEquals(setOf(photo.path), paths(activity))
        }
    }

    @Test fun ordinaryCachePreviewSortsBeforeAnyReadResultAndDoesNotRestartScan() {
        File(root, ".nomedia").delete()
        context.config.saveCustomSorting(root.path, org.fossify.commons.helpers.SORT_BY_NAME)
        withActivity { activity ->
            MediaActivity.mMedia.clear()
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            (field(activity, "mediaScanTerminals").get(activity) as GalleryScanTerminalGate).begin(request)
            field(activity, "mediaRefreshRequest").setLong(activity, request)
            field(activity, "mIsGettingMedia").setBoolean(activity, true)
            field(activity, "folderReadResult").set(activity, null)
            val media = ArrayList<org.fossify.gallery.models.ThumbnailItem>((0 until 11023).map {
                Medium(null, "image-%05d.jpg".format(it), "${root.path}/image-%05d.jpg".format(it), root.path,
                    10, 10, 100, TYPE_IMAGES, 0, false, 0, 0)
            })
            MediaActivity::class.java.getDeclaredMethod("gotMedia", ArrayList::class.java,
                Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, media, true, request)
            await { paths(activity).size == 11023 }
            val snapshot = field(activity, "displayedFolderSnapshot").get(activity) as DisplayedFolderSnapshot
            assertEquals(DisplayedSourceStage.CACHE_PREVIEW, snapshot.stage)
            assertNull(field(activity, "folderReadResult").get(activity))
            context.config.saveCustomSorting(root.path, org.fossify.commons.helpers.SORT_BY_NAME or
                org.fossify.commons.helpers.SORT_DESCENDING)
            val gate = field(activity, "mediaSortOperations").get(activity) as MediaSortOperationGate
            val accepted = MediaActivity::class.java.getDeclaredMethod("previewSortDuringFolderScan",
                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, gate.begin(), android.os.SystemClock.elapsedRealtime()) as Boolean
            assertTrue(accepted)
            await {
                val adapter = activity.findViewById<MyRecyclerView>(R.id.media_grid).adapter as? MediaAdapter
                (adapter?.media?.firstOrNull() as? Medium)?.name == "image-11022.jpg"
            }
            assertEquals(request, field(activity, "mediaRefreshRequest").getLong(activity))
            assertTrue(field(activity, "mIsGettingMedia").getBoolean(activity))
            assertNull(field(activity, "mCurrAsyncTask").get(activity))
            assertEquals(11023, paths(activity).size)
        }
    }

    @Test fun recycleBinCompletedSnapshotUsesVirtualSortPathAndKeepsAdapterAndRequest() {
        val bin = File(context.recycleBinPath, "virtual-sort-regression").apply { mkdirs() }
        val alpha = File(bin, "alpha.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val zeta = File(bin, "zeta.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        dbExecutor.submit {
            context.mediaDB.clearRecycleBin()
            context.mediaDB.insertAll(listOf(alpha, zeta).map {
                Medium(null, it.name, RECYCLE_BIN + "/virtual-sort-regression/" + it.name, bin.path,
                    100, 200, 2, TYPE_IMAGES, 0, false, 10, 0)
            })
        }.get(5, TimeUnit.SECONDS)
        context.config.saveCustomSorting(RECYCLE_BIN, org.fossify.commons.helpers.SORT_BY_NAME)
        val controller = Robolectric.buildActivity(MediaActivity::class.java,
            Intent(context, MediaActivity::class.java).putExtra(DIRECTORY, RECYCLE_BIN)).create(Bundle()).start()
        try {
            val activity = controller.get()
            val previousTask = field(activity, "mCurrAsyncTask").get(activity) as? org.fossify.gallery.asynctasks.GetMediaAsynctask
            previousTask?.stopFetching()
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            field(activity, "mediaRefreshRequest").setLong(activity, request)
            field(activity, "displayedMediaRequest").setLong(activity, request)
            field(activity, "completedMediaRequest").setLong(activity, request)
            val recycleRevision = RecycleBinMembershipRevision.current()
            field(activity, "recycleBinLoadRevision").setLong(activity, recycleRevision)
            field(activity, "displayedRecycleBinRevision").setLong(activity, recycleRevision)
            field(activity, "mediaScanOutcomeRequest").setLong(activity, request)
            field(activity, "mediaScanOutcome").set(activity,
                GalleryScanResult(GalleryScanState.COMPLETE))
            field(activity, "displayedMediaIsFinal").setBoolean(activity, true)
            field(activity, "mIsGettingMedia").setBoolean(activity, false)
            val source = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(
                Medium(null, zeta.name, File(context.recycleBinPath, "virtual-sort-regression/zeta.jpg").path,
                    bin.path, 100, 200, 2, TYPE_IMAGES, 0, false, 10, 0),
                Medium(null, alpha.name, File(context.recycleBinPath, "virtual-sort-regression/alpha.jpg").path,
                    bin.path, 100, 200, 2, TYPE_IMAGES, 0, false, 10, 0)
            )
            MediaActivity.mMedia = ArrayList(source)
            val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
            val adapter = MediaAdapter(activity, ArrayList(source), activity, false, false, RECYCLE_BIN, grid) {}
            grid.adapter = adapter
            val operationGate = field(activity, "mediaSortOperations").get(activity) as MediaSortOperationGate
            val method = MediaActivity::class.java.getDeclaredMethod("tryResortVirtualMediaSnapshot",
                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
            assertTrue(method.invoke(activity, operationGate.begin(), android.os.SystemClock.elapsedRealtime()) as Boolean)
            await {
                (grid.adapter as? MediaAdapter)?.media?.filterIsInstance<Medium>()?.firstOrNull()?.name == "alpha.jpg" &&
                    MediaActivity.mMedia.filterIsInstance<Medium>().firstOrNull()?.name == "alpha.jpg"
            }
            assertSame(adapter, grid.adapter)
            assertEquals(request, field(activity, "mediaRefreshRequest").getLong(activity))
            assertEquals(setOf(alpha.path, zeta.path), paths(activity))
            val searchCurrent = MediaActivity::class.java.getDeclaredMethod(
                "isSearchSnapshotCurrent", Long::class.javaPrimitiveType
            ).apply { isAccessible = true }
            assertTrue(searchCurrent.invoke(activity, request) as Boolean)
            RecycleBinMembershipRevision.changed()
            assertFalse(searchCurrent.invoke(activity, request) as Boolean)
        } finally {
            controller.stop().destroy()
            bin.deleteRecursively()
        }
    }

    @Test fun favoritesCompletedSnapshotUsesVirtualSortPathWithActiveSession() {
        val alpha = photo("favorite-alpha.jpg")
        val zeta = photo("favorite-zeta.jpg")
        val favoritePaths = setOf(alpha.path, zeta.path)
        dbExecutor.submit {
            context.favoritesDB.clearFavorites()
            context.favoritesDB.insertAll(favoritePaths.map {
                Favorite(null, it, File(it).name, root.path)
            }.toList())
        }.get(5, TimeUnit.SECONDS)
        context.config.saveCustomSorting(FAVORITES, org.fossify.commons.helpers.SORT_BY_NAME)
        var token: String? = null
        val sessionSnapshot = FavoriteAccessSessionManager.captureSnapshot(favoritePaths, context::favoriteProtection)
        token = FavoriteAccessSessionManager.createSession(
            "virtual-sort-test", sessionSnapshot, emptySet(), context::favoriteProtection
        )
        assertNotNull(token)
        val controller = Robolectric.buildActivity(MediaActivity::class.java,
            Intent(context, MediaActivity::class.java).putExtra(DIRECTORY, FAVORITES))
            .create(Bundle().apply { putString("favoriteOwnerId", "virtual-sort-test") }).start()
        try {
            val activity = controller.get()
            (field(activity, "mCurrAsyncTask").get(activity) as? org.fossify.gallery.asynctasks.GetMediaAsynctask)
                ?.stopFetching()
            field(activity, "favoriteSessionId").set(activity, token)
            field(activity, "favoriteAccessPaths").set(activity, favoritePaths)
            field(activity, "favoriteCatalogRelationshipPaths").set(activity, favoritePaths)
            val favoriteRevision = (field(activity, "favoriteRelationshipRevision").get(activity)
                as java.util.concurrent.atomic.AtomicLong).get()
            field(activity, "favoriteLoadRevision").setLong(activity, favoriteRevision)
            field(activity, "displayedFavoriteRelationshipRevision").setLong(activity, favoriteRevision)
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            field(activity, "mediaRefreshRequest").setLong(activity, request)
            field(activity, "displayedMediaRequest").setLong(activity, request)
            field(activity, "completedMediaRequest").setLong(activity, request)
            field(activity, "mediaScanOutcomeRequest").setLong(activity, request)
            field(activity, "mediaScanOutcome").set(activity, GalleryScanResult(GalleryScanState.COMPLETE))
            field(activity, "displayedMediaIsFinal").setBoolean(activity, true)
            field(activity, "mIsGettingMedia").setBoolean(activity, false)
            val source = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(
                Medium(null, zeta.name, zeta.path, root.path, 100, 200, 2, TYPE_IMAGES, 0, true, 0, 0),
                Medium(null, alpha.name, alpha.path, root.path, 100, 200, 2, TYPE_IMAGES, 0, true, 0, 0)
            )
            MediaActivity.mMedia = ArrayList(source)
            val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
            val adapter = MediaAdapter(activity, ArrayList(source), activity, false, false, FAVORITES, grid) {}
            grid.adapter = adapter
            val operationGate = field(activity, "mediaSortOperations").get(activity) as MediaSortOperationGate
            val method = MediaActivity::class.java.getDeclaredMethod("tryResortVirtualMediaSnapshot",
                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
            assertTrue(method.invoke(activity, operationGate.begin(), android.os.SystemClock.elapsedRealtime()) as Boolean)
            await {
                (grid.adapter as? MediaAdapter)?.media?.filterIsInstance<Medium>()?.firstOrNull()?.name ==
                    "favorite-alpha.jpg" &&
                    MediaActivity.mMedia.filterIsInstance<Medium>().firstOrNull()?.name == "favorite-alpha.jpg"
            }
            assertSame(adapter, grid.adapter)
            assertEquals(request, field(activity, "mediaRefreshRequest").getLong(activity))
            assertEquals(favoritePaths, paths(activity))
        } finally {
            controller.stop().destroy()
            token?.let(FavoriteAccessSessionManager::revokeSession)
        }
    }

    /** Production gotMedia(cache) -> validation -> publishMedia -> real RecyclerView.
     * No scan worker is scheduled: the in-flight request is held deterministically.
     */
    private fun withFavoriteCache(block: (MediaActivity, MediaAdapter, Long, String) -> Unit) {
        val files = (0 until 382).map { photo("cache-%03d.jpg".format(it)) }
        val members = files.mapTo(LinkedHashSet()) { it.path }
        dbExecutor.submit {
            context.favoritesDB.clearFavorites()
            context.favoritesDB.insertAll(files.map { Favorite(null, it.path, it.name, root.path) })
        }.get(5, TimeUnit.SECONDS)
        context.config.saveCustomSorting(FAVORITES, org.fossify.commons.helpers.SORT_BY_NAME or
            org.fossify.commons.helpers.SORT_DESCENDING)
        val owner = "interactive-cache-test"
        val token = FavoriteAccessSessionManager.createSession(owner,
            FavoriteAccessSessionManager.captureSnapshot(members, context::favoriteProtection),
            emptySet(), context::favoriteProtection)!!
        val controller = Robolectric.buildActivity(MediaActivity::class.java,
            Intent(context, MediaActivity::class.java).putExtra(DIRECTORY, FAVORITES))
            .create(Bundle().apply { putString("favoriteOwnerId", owner) }).start()
        try {
            val activity = controller.get()
            field(activity, "favoriteSessionId").set(activity, token)
            field(activity, "favoriteAccessPaths").set(activity, members)
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            field(activity, "mediaRefreshRequest").setLong(activity, request)
            (field(activity, "mediaScanTerminals").get(activity) as GalleryScanTerminalGate).begin(request)
            field(activity, "favoriteLoadRevision").setLong(activity,
                (field(activity, "favoriteRelationshipRevision").get(activity) as java.util.concurrent.atomic.AtomicLong).get())
            field(activity, "mIsGettingMedia").setBoolean(activity, true)
            val source = ArrayList<org.fossify.gallery.models.ThumbnailItem>(files.asReversed().map {
                // Room metadata can be unrequested even for a nonzero fallback date.
                Medium(null, it.name, it.path, root.path, 0, 7, 0, TYPE_IMAGES, 0, true, 0, 0)
            })
            publishFavorite(activity, source, true, request)
            await { paths(activity).size == 382 }
            val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
            val adapter = grid.adapter as MediaAdapter
            assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
            assertEquals(-1L, field(activity, "completedMediaRequest").getLong(activity))
            block(activity, adapter, request, token)
        } finally {
            controller.stop().destroy()
            FavoriteAccessSessionManager.revokeSession(token)
        }
    }

    private fun publishFavorite(activity: MediaActivity, rows: ArrayList<org.fossify.gallery.models.ThumbnailItem>,
                                cache: Boolean, request: Long) {
        MediaActivity::class.java.getDeclaredMethod("gotMedia", ArrayList::class.java,
            Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(activity, rows, cache, request)
    }

    private fun sortFavorite(activity: MediaActivity, sorting: Int) {
        context.config.saveCustomSorting(FAVORITES, sorting)
        val gate = field(activity, "mediaSortOperations").get(activity) as MediaSortOperationGate
        MediaActivity::class.java.getDeclaredMethod("tryResortVirtualMediaSnapshot",
            Long::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(activity, gate.begin(), android.os.SystemClock.elapsedRealtime())
    }

    private fun searchFavorite(activity: MediaActivity, text: String) {
        field(activity, "mLastSearchedText").set(activity, text)
        val method = MediaActivity::class.java.declaredMethods.first { it.name == "searchQueryChanged" }
            .apply { isAccessible = true }
        method.invoke(activity, text, {}, method.parameterTypes[2].enumConstants.first())
    }

    @Test fun favoriteCache382SortsBeforeScanCompletesAndKeepsTheRequest() = withFavoriteCache { activity, adapter, request, _ ->
        assertEquals("cache-381.jpg", adapter.media.filterIsInstance<Medium>().first().name)
        val version = adapter.committedVersion
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_NAME)
        await { adapter.committedVersion > version &&
            adapter.media.filterIsInstance<Medium>().firstOrNull()?.name == "cache-000.jpg" }
        assertSame(adapter, activity.findViewById<MyRecyclerView>(R.id.media_grid).adapter)
        assertEquals(request, field(activity, "mediaRefreshRequest").getLong(activity))
        assertTrue(field(activity, "mIsGettingMedia").getBoolean(activity))
        assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
        assertEquals(-1L, field(activity, "completedMediaRequest").getLong(activity))
    }

    @Test fun favoriteCacheSearchAndClearCommitBeforeScanCompletes() = withFavoriteCache { activity, adapter, request, _ ->
        // Baseline cache has displayedFavoriteRelationshipRevision=-1 and drops this.
        searchFavorite(activity, "cache-00")
        await { adapter.media.filterIsInstance<Medium>().size == 10 }
        searchFavorite(activity, "")
        await { adapter.media.filterIsInstance<Medium>().size == 382 }
        assertEquals(request, field(activity, "mediaRefreshRequest").getLong(activity))
        assertTrue(field(activity, "mIsGettingMedia").getBoolean(activity))
        assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
    }

    @Test fun favoriteCacheRepairsSizeAndDateAndLastSortWins() = withFavoriteCache { activity, adapter, _, _ ->
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_NAME)
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_SIZE)
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED)
        await { MediaActivity.mMedia.filterIsInstance<Medium>().all { it.modified > 0 } }
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_SIZE)
        await { adapter.media.filterIsInstance<Medium>().all { it.size == 2L } }
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_DATE_TAKEN)
        await { adapter.media.filterIsInstance<Medium>().all { it.taken == it.modified && it.taken > 7L } }
        val version = adapter.committedVersion
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_RANDOM)
        await { adapter.committedVersion > version }
        assertEquals(382, adapter.media.filterIsInstance<Medium>().map { it.path }.toSet().size)
    }

    @Test fun favoriteCacheFinalPublicationKeepsLatestQueryAndSort() = withFavoriteCache { activity, adapter, request, _ ->
        searchFavorite(activity, "cache-00")
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_SIZE)
        searchFavorite(activity, "cache-0")
        val rows = ArrayList(MediaActivity.mMedia)
        field(activity, "mediaScanOutcomeRequest").setLong(activity, request)
        field(activity, "mediaScanOutcome").set(activity, GalleryScanResult(GalleryScanState.COMPLETE))
        publishFavorite(activity, rows, false, request)
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_NAME)
        await { field(activity, "displayedMediaIsFinal").getBoolean(activity) &&
            adapter.media.filterIsInstance<Medium>().size == 100 &&
            adapter.media.filterIsInstance<Medium>().firstOrNull()?.name == "cache-000.jpg" }
        assertEquals("cache-0", field(activity, "mLastSearchedText").get(activity))
        assertFalse(field(activity, "mIsGettingMedia").getBoolean(activity))
    }

    @Test fun favoriteUserSortReplacingPendingFinalDiffStillCommitsComplete() = withFavoriteCache { activity, _, request, _ ->
        val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
        val queued = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val adapter = MediaAdapter(activity, ArrayList(MediaActivity.mMedia), activity, false, false,
            FAVORITES, grid, java.util.concurrent.Executor { queued.add(it) }) {}
        grid.adapter = adapter
        val rows = ArrayList(MediaActivity.mMedia.map { (it as Medium).copy(size = 2L) })
        field(activity, "mediaScanOutcomeRequest").setLong(activity, request)
        field(activity, "mediaScanOutcome").set(activity, GalleryScanResult(GalleryScanState.COMPLETE))
        publishFavorite(activity, ArrayList<org.fossify.gallery.models.ThumbnailItem>(rows), false, request)
        await { queued.isNotEmpty() && field(activity, "completedMediaRequest").getLong(activity) == request }
        assertFalse(field(activity, "displayedMediaIsFinal").getBoolean(activity))
        sortFavorite(activity, org.fossify.commons.helpers.SORT_BY_NAME)
        await { adapter.media.filterIsInstance<Medium>().firstOrNull()?.name == "cache-000.jpg" &&
            field(activity, "displayedMediaIsFinal").getBoolean(activity) }
        // Deliver the superseded SCAN Diff after USER_SORT's fast commit.
        while (queued.isNotEmpty()) {
            queued.poll()?.run()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals("cache-000.jpg", adapter.media.filterIsInstance<Medium>().first().name)
        assertFalse(field(activity, "mIsGettingMedia").getBoolean(activity))
        assertSame(adapter, grid.adapter)
    }

    @Test fun favoriteCacheRejectsChangedRelationshipsAndRevocation() = withFavoriteCache { activity, _, request, token ->
        val current = MediaActivity::class.java.getDeclaredMethod("isSearchSnapshotCurrent", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
        assertTrue(current.invoke(activity, request) as Boolean)
        val revision = field(activity, "favoriteRelationshipRevision").get(activity) as java.util.concurrent.atomic.AtomicLong
        revision.incrementAndGet()
        assertFalse(current.invoke(activity, request) as Boolean)
        revision.decrementAndGet()
        assertTrue(current.invoke(activity, request) as Boolean)
        FavoriteAccessSessionManager.revokeSession(token)
        assertFalse(current.invoke(activity, request) as Boolean)
    }

    @Test fun favoriteCacheDoesNotPublishUnauthorizedCandidateOrOldRequest() = withFavoriteCache { activity, adapter, request, _ ->
        val source = ArrayList(MediaActivity.mMedia)
        val outsider = photo("protected-outsider.jpg")
        source.add(Medium(null, outsider.name, outsider.path, root.path, 1, 1, 2, TYPE_IMAGES, 0, true, 0, 0))
        val before = field(activity, "displayedSourceVersion").getLong(activity)
        publishFavorite(activity, source, true, request)
        await { field(activity, "displayedSourceVersion").getLong(activity) > before }
        assertFalse(outsider.path in paths(activity))
        val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
        requests.begin()
        publishFavorite(activity, arrayListOf(), true, request)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(382, adapter.media.filterIsInstance<Medium>().size)
        activity.finish()
        val current = MediaActivity::class.java.getDeclaredMethod("isSearchSnapshotCurrent", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
        assertFalse(current.invoke(activity, request) as Boolean)
    }

    @Test fun cachedPreviewKeepsTheRefreshDeadlineActiveUntilFormalCompletion() {
        val old = photo("cached.jpg")
        withActivity { activity ->
            val requests = field(activity, "mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
            val request = requests.begin()
            (field(activity, "mediaScanTerminals").get(activity) as GalleryScanTerminalGate).begin(request)
            field(activity, "mIsGettingMedia").setBoolean(activity, true)
            val media = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(Medium().apply {
                path = old.path; parentPath = root.path; name = old.name; type = TYPE_IMAGES
            })
            MediaActivity::class.java.getDeclaredMethod("armMediaRefreshDeadline", Long::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, request)
            MediaActivity::class.java.getDeclaredMethod("publishMedia", ArrayList::class.java,
                Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, media, true, request)
            assertTrue(field(activity, "mIsGettingMedia").getBoolean(activity))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(46, TimeUnit.SECONDS)
            assertFalse(field(activity, "mIsGettingMedia").getBoolean(activity))
            assertEquals(setOf(old.path), paths(activity))
        }
    }

    @Test fun periodicHomeDiscoveryUsesCanonicalBoundariesAndTheCurrentEpoch() {
        photo("new.jpg")
        context.config.includedFolders = mutableSetOf(root.path)
        val link = File(root, "cycle")
        Files.createSymbolicLink(link.toPath(), root.toPath())
        val home = Robolectric.buildActivity(MainActivity::class.java,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true))
            .create(Bundle()).start()
        try {
            val main = home.get()
            val epoch = MainActivity::class.java.getDeclaredField("directoryScanEpoch").apply { isAccessible = true }
                .get(main) as DirectoryScanEpoch
            val request = epoch.begin()
            MainActivity::class.java.getDeclaredField("mShouldStopFetching").apply { isAccessible = true }.setBoolean(main, false)
            val method = MainActivity::class.java.getDeclaredMethod("getFoldersWithMedia", String::class.java,
                Long::class.javaPrimitiveType).apply { isAccessible = true }
            val result = method.invoke(main, root.path, request) as FolderDiscoveryResult
            assertTrue(result.complete)
            assertEquals(setOf(root.path), result.folders)
            epoch.retire()
            try {
                method.invoke(main, root.path, request)
                fail("retired periodic discovery continued")
            } catch (error: java.lang.reflect.InvocationTargetException) {
                assertTrue(error.cause is java.util.concurrent.CancellationException)
            }
        } finally {
            Files.deleteIfExists(link.toPath())
            home.stop().destroy()
        }
    }
}
