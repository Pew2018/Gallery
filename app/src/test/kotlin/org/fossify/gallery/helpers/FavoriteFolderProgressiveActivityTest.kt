package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoriteFoldersDB
import org.fossify.gallery.models.FavoriteFolder
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FavoriteFolderProgressiveActivityTest {
    class BlockingProvider : ContentProvider() {
        lateinit var delegate: PhotoProfileMediaLoadingTest.IndexedMediaProvider
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor {
            entered.countDown()
            check(release.await(15, TimeUnit.SECONDS)) { "test provider timed out" }
            return delegate.query(uri, projection, selection, args, order)
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    @Test fun safeManifestIsClickableBeforeBlockedMetadataAndStatisticsFillSameAdapter() {
        val app = RuntimeEnvironment.getApplication()
        GalleryDatabase.destroyInstance()
        app.getSharedPrefs().edit().clear().commit()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        app.config.filterMedia = TYPE_IMAGES
        app.config.groupDirectSubfolders = false
        app.config.directorySorting = SORT_BY_NAME
        val root = Files.createTempDirectory("favorite-manifest").toFile()
        val folder = File(root, "album").apply { mkdir() }
        File(folder, "one.jpg").writeBytes(byteArrayOf(1, 2))
        val hidden = File(root, ".private").apply { mkdir() }
        val unavailable = File(root, "offline")
        val executor = Executors.newSingleThreadExecutor()
        val index = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        val provider = Robolectric.buildContentProvider(BlockingProvider::class.java).create().get().also { it.delegate = index }
        ShadowContentResolver.registerProviderInternal("media", provider)
        executor.submit { listOf(folder, hidden, unavailable).forEach { app.favoriteFoldersDB.insert(FavoriteFolder(it.path)) } }.get()
        val intent = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, true)
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create(Bundle()).start().resume()
        val activity = controller.get()
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
            while (!condition() && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("Favorite Activity publication did not settle", condition())
        }
        fun adapter() = activity.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter
        val coordinator = MainActivity::class.java.getDeclaredField("favoriteLoad").apply { isAccessible = true }
            .get(activity) as FavoriteFolderLoadCoordinator
        try {
            await { coordinator.hasVisibleManifest && adapter()?.dirs?.size == 2 }
            val first = adapter()!!
            assertTrue(first.dirs.any { it.path == folder.path })
            assertFalse(first.dirs.any { it.path == hidden.path })
            assertEquals(FavoriteFolderLoadStatus.Loading, coordinator.status)
            assertEquals(0, first.dirs.first { it.path == folder.path }.mediaCnt)
            // Change sort while the older metadata worker is still blocked.
            activity.config.directorySorting = SORT_BY_NAME or org.fossify.commons.helpers.SORT_DESCENDING
            MainActivity::class.java.getDeclaredMethod("refreshDirectoryPresentation").apply { isAccessible = true }.invoke(activity)
            await { coordinator.hasVisibleManifest && adapter()?.dirs?.map { it.path } == listOf(unavailable.path, folder.path) }
            provider.release.countDown()
            await { coordinator.status != FavoriteFolderLoadStatus.Loading && adapter()?.dirs?.any { it.path == folder.path && it.mediaCnt == 1 } == true }
            assertSame(first, adapter())
            assertEquals(listOf(unavailable.path, folder.path), adapter()!!.dirs.map { it.path })
            assertTrue("Unavailable storage must retain its favorite relationship", executor.submit<Boolean> { app.favoriteFoldersDB.contains(unavailable.path) > 0 }.get())
        } finally {
            provider.release.countDown()
            controller.pause().stop().destroy()
            executor.shutdownNow()
            root.deleteRecursively()
            index.db.close()
            GalleryDatabase.destroyInstance()
        }
    }
}
