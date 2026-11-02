package org.fossify.gallery.helpers

import android.app.Application
import android.os.OperationCanceledException
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, manifest = Config.NONE,
    shadows = [PhotoProfileMediaLoadingTest.ScopedEnvironment::class, PhotoProfileMediaLoadingTest.ScopedMediaStore::class])
class RefreshRepairTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: PhotoProfileMediaLoadingTest.IndexedMediaProvider
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        provider = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun cleanup() { provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun <T> background(block: () -> T): T {
        val pool = Executors.newSingleThreadExecutor()
        return try { pool.submit<T> { block() }.get(10, TimeUnit.SECONDS) } finally { pool.shutdownNow() }
    }
    private fun scan(folder: String) = MediaFetcher(context, reportReadFailures = true).getFilesFrom(
        folder, false, false, true, true, true, arrayListOf(), false, hashMapOf(), hashMapOf(), null)

    @Test fun ordinaryFolderRefreshFindsNewFilesEvenWithANonemptyStaleIndex() {
        val root = Files.createTempDirectory("refresh-stale-index")
        try {
            val old = Files.write(root.resolve("old.jpg"), byteArrayOf(1)).toString()
            val indexedOnly = root.resolve("provider-only.jpg").toString()
            provider.add(old, size = 123)
            provider.add(indexedOnly)
            assertEquals(setOf(old, indexedOnly), background { scan(root.toString()) }.map { it.path }.toSet())
            val added = Files.write(root.resolve("new.jpg"), byteArrayOf(2, 3)).toString()
            val nested = Files.createDirectory(root.resolve("child"))
            Files.write(nested.resolve("not-a-direct-child.jpg"), byteArrayOf(4))
            Files.write(root.resolve(".hidden.jpg"), byteArrayOf(5))
            val refreshed = background { scan(root.toString()) }
            assertEquals(setOf(old, indexedOnly, added), refreshed.map { it.path }.toSet())
            assertEquals(3, refreshed.size)
            assertEquals(123L, refreshed.single { it.path == old }.size)
            assertEquals(2L, refreshed.single { it.path == added }.size)
            assertTrue("all source queries remain folder bounded", provider.queries.all { it?.contains("LIKE") == true })
            val controller = Robolectric.buildActivity(org.fossify.gallery.activities.SimpleActivity::class.java).setup()
            try {
                val activity = controller.get()
                val view = org.fossify.commons.views.MyRecyclerView(activity)
                activity.setContentView(view)
                val adapter = org.fossify.gallery.adapters.MediaAdapter(activity,
                    arrayListOf<org.fossify.gallery.models.ThumbnailItem>(refreshed.first { it.path == old }),
                    null, false, false, root.toString(), view,
                    diffExecutor = java.util.concurrent.Executor { it.run() }) { }
                view.adapter = adapter
                adapter.updateMedia(ArrayList(refreshed))
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(setOf(old, indexedOnly, added), adapter.media.filterIsInstance<Medium>().map { it.path }.toSet())
                view.adapter = null
            } finally { controller.pause().stop().destroy() }

        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun discoveryFindsNewChildAlbumsButDoesNotTraverseDeniedRootsOrSymlinkEscapes() {
        val root = Files.createTempDirectory("refresh-discovery")
        val outside = Files.createTempDirectory("refresh-outside")
        try {
            val album = Files.createDirectories(root.resolve("new/album"))
            val denied = Files.createDirectories(root.resolve("protected/child"))
            Files.createSymbolicLink(root.resolve("escape"), outside)
            val deniedRoot = root.resolve("protected").toString()
            val result = FolderDiskDiscovery.find(listOf(root.toString(), deniedRoot),
                allowed = { it != deniedRoot && !it.startsWith("$deniedRoot/") }, cancelled = { false })
            assertTrue(album.toString() in result)
            assertFalse(denied.toString() in result)
            assertFalse(result.any { it.contains("escape") || it == outside.toString() })
            assertTrue(FolderDiskDiscovery.find(listOf(root.toString()), { true }, { true }).isEmpty())
        } finally { root.toFile().deleteRecursively(); outside.toFile().deleteRecursively() }
    }

    @Test fun explicitAlbumRefreshDiscoversAnUnindexedNewFolderBelowAKnownAlbum() {
        val root = Files.createTempDirectory("refresh-new-album")
        try {
            provider.add(Files.write(root.resolve("old.jpg"), byteArrayOf(1)).toString())
            context.config.everShownFolders = setOf(root.toString())
            val fresh = Files.createDirectories(root.resolve("new-album"))
            Files.write(fresh.resolve("new.jpg"), byteArrayOf(2))
            val ordinary = background { MediaFetcher(context, reportReadFailures = true).getFoldersToScan() }
            assertFalse(fresh.toString() in ordinary)
            val manual = background { MediaFetcher(context, reportReadFailures = true,
                discoverUnindexedFolders = true).getFoldersToScan() }
            assertTrue(fresh.toString() in manual)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun canceledFetcherNeverReturnsAnOldProviderResult() {
        val fetcher = MediaFetcher(context, reportReadFailures = true)
        fetcher.shouldStop = true
        try {
            background { fetcher.getAndroid11FolderMedia(false, false, arrayListOf(), false, false, hashMapOf()) }
            fail("Canceled provider query returned success")
        } catch (error: java.util.concurrent.ExecutionException) {
            assertTrue(error.cause is OperationCanceledException)
        }
    }

    @Test fun supersededQueuedAlbumRefreshDoesNotReadCacheOrDeliverCallbacks() {
        val queue = mutableListOf<Runnable>()
        var current = true
        GalleryDatabase.getInstance(context).close()
        context.getCachedDirectories(executor = java.util.concurrent.Executor { queue.add(it) },
            isCurrent = { current }, onFailure = { fail("stale request accessed the closed cache") }) {
            fail("stale request delivered a result")
        }
        assertEquals(1, queue.size)
        current = false
        queue.single().run()
    }

    @Test fun independentBoundedWorkerAllowsRefreshPastOneBlockedOldTask() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fresh = CountDownLatch(1)
        val ended = CountDownLatch(1)
        try {
            GalleryMediaScanExecutor.execute { started.countDown(); release.await(5, TimeUnit.SECONDS); ended.countDown() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            GalleryMediaScanExecutor.execute { fresh.countDown() }
            assertTrue("new folder scan must not use AsyncTask's serial queue", fresh.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); assertTrue(ended.await(2, TimeUnit.SECONDS)) }
    }

    @Test fun snapshotPreparationCannotBlockPhotoTapOrPublishAfterARequestBecomesStale() {
        val root = Files.createTempDirectory("refresh-snapshot")
        val pool = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var current = true
        try {
            val path = Files.write(root.resolve("one.jpg"), byteArrayOf(1)).toString()
            provider.add(path)
            val checkpoint = background { FolderReturnCheckpoint.capture(context, root.toString()) }
            val item = Medium().apply { this.path = path; parentPath = root.toString(); mediaStoreId = 1L }
            var calls = 0
            val work = pool.submit<String?> {
                FolderViewerSnapshotStore.prepare(context, root.toString(), checkpoint, checkpoint, listOf(item)) {
                    calls++
                    if (calls == 2) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
                    current
                }
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val tap = Executors.newSingleThreadExecutor()
            try { assertNull(tap.submit<String?> { FolderViewerSnapshotStore.handoffId(context, root.toString()) }.get(1, TimeUnit.SECONDS)) }
            finally { tap.shutdownNow() }
            current = false
            release.countDown()
            assertNull(work.get(5, TimeUnit.SECONDS))
            assertNull(FolderViewerSnapshotStore.handoffId(context, root.toString()))
        } finally { release.countDown(); pool.shutdownNow(); root.toFile().deleteRecursively() }
    }
}
