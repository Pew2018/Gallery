package org.fossify.gallery.helpers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.view.View
import android.widget.TextView
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.getCachedMedia
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MediaReadFailureTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: FailingProvider
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        provider = Robolectric.buildContentProvider(FailingProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
        context.config.filterMedia = TYPE_IMAGES
    }
    @After fun close() { GalleryDatabase.destroyInstance() }

    @Test fun providerPermissionFailureReachesTheCallerInsteadOfBecomingEmptyContent() {
        provider.failure = SecurityException("revoked")
        try {
            MediaFetcher(context, reportReadFailures = true).getFoldersToScan()
            fail("Permission failure was swallowed")
        } catch (error: SecurityException) {
            assertEquals(R.string.media_load_permission_error, MediaLoadFailure.message(error))
        }
    }

    @Test fun nullCursorAndSuccessfulEmptyCursorHaveDifferentOutcomes() {
        provider.nullCursor = true
        try {
            MediaFetcher(context, reportReadFailures = true).getFoldersToScan()
            fail("Null cursor was treated as success")
        } catch (error: IOException) {
            assertEquals(R.string.media_load_storage_error, MediaLoadFailure.message(error))
        }
        provider.nullCursor = false
        // An empty, functioning provider remains a successful scan.
        MediaFetcher(context, reportReadFailures = true).getFoldersToScan()
    }

    @Test @Config(sdk = [31], shadows = [PhotoProfileMediaLoadingTest.ScopedEnvironment::class])
    fun scopedPrimaryIndexDoesNotSwallowPermissionOrNullCursorFailures() {
        val fetcher = MediaFetcher(context, reportReadFailures = true)
        provider.failure = SecurityException("revoked")
        try {
            fetcher.getAndroid11FolderMedia(false, false, arrayListOf(), false, false, hashMapOf())
            fail("Scoped permission failure was swallowed")
        } catch (_: SecurityException) { }
        provider.failure = null
        provider.nullCursor = true
        try {
            fetcher.getAndroid11FolderMedia(false, false, arrayListOf(), false, false, hashMapOf())
            fail("Scoped null cursor was swallowed")
        } catch (_: IOException) { }
        provider.nullCursor = false
        assertTrue(fetcher.getAndroid11FolderMedia(false, false, arrayListOf(), false, false, hashMapOf()).isEmpty())
    }

    @Test fun missingPhysicalFolderReportsUnavailableRatherThanAnEmptyAlbum() {
        try {
            MediaFetcher(context, reportReadFailures = true).getFilesFrom(
                "/missing-gallery-test-volume/photos", false, false, false, false, false,
                arrayListOf(), false, hashMapOf(), hashMapOf(), null
            )
            fail("Unavailable folder was treated as empty")
        } catch (_: IOException) { }
    }

    @Test fun authoritativeCategoryCacheFailureReportsExactlyOneFailure() {
        GalleryDatabase.getInstance(context).close()
        val done = CountDownLatch(1)
        val successes = AtomicInteger()
        val failures = AtomicInteger()
        context.getCachedDirectories(onFailure = { failures.incrementAndGet(); done.countDown() }) {
            successes.incrementAndGet(); done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(0, successes.get())
        assertEquals(1, failures.get())
    }

    @Test fun unreadableOptionalFavoriteCacheStillStartsTheSourceScanCallback() {
        GalleryDatabase.getInstance(context).close()
        val done = CountDownLatch(1)
        val callbacks = AtomicInteger()
        context.getCachedMedia(org.fossify.commons.helpers.FAVORITES) {
            assertTrue(it.isEmpty())
            callbacks.incrementAndGet(); done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, callbacks.get())
    }

    @Test fun failedEmptyLoadOffersRetryWhileFailedRefreshKeepsExistingUi() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val title = TextView(activity)
            val description = TextView(activity)
            val action = TextView(activity)
            var retries = 0
            MediaLoadFailure.present(activity, title, description, action, false, SecurityException()) { retries++ }
            assertEquals(View.VISIBLE, action.visibility)
            assertEquals(activity.getString(R.string.media_load_permission_error), description.text.toString())
            action.performClick()
            assertEquals(1, retries)
            title.text = "existing content"
            action.visibility = View.GONE
            MediaLoadFailure.present(activity, title, description, action, true, IOException()) { fail("Unexpected retry") }
            assertEquals("existing content", title.text.toString())
            assertEquals(View.GONE, action.visibility)
            assertEquals(activity.getString(R.string.media_load_storage_error), ShadowToast.getTextOfLatestToast())
        } finally { controller.pause().stop().destroy() }
    }

    class FailingProvider : ContentProvider() {
        var failure: RuntimeException? = null
        var nullCursor = false
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
            failure?.let { throw it }
            return if (nullCursor) null else MatrixCursor(projection ?: emptyArray())
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
}
