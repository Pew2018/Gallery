package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
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
}
