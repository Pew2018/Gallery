package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.FolderViewerSnapshotStore
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FolderCatalogReuseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun completePublicFolderCanBeReopenedAndTracksExternalChanges() {
        assumeTrue("Requires the modern all-files access path", Build.VERSION.SDK_INT >= 30)
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        (permissions + Manifest.permission.ACCESS_MEDIA_LOCATION).forEach {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it)
        }
        setAllFilesAccess(true)
        assertTrue(Environment.isExternalStorageManager())

        val count = 3080
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GalleryCatalogR-${UUID.randomUUID()}")
        assertTrue(folder.mkdirs())
        val previousShowAll = context.config.showAll
        val previousFilter = context.config.filterMedia
        try {
            context.config.showAll = false
            context.config.filterMedia = TYPE_IMAGES
            val jpeg = jpeg()
            repeat(count) { index -> File(folder, "$index.jpg").writeBytes(jpeg) }
            val source = instrumentation.startActivitySync(Intent(context, MediaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DIRECTORY, folder.path)) as MediaActivity
            try {
                val grid = source.findViewById<MyRecyclerView>(R.id.media_grid)
                await("Complete source catalog", 30_000) {
                    (grid.adapter as? MediaAdapter)?.media?.count { it is Medium } == count &&
                        FolderViewerSnapshotStore.handoffId(context, folder.path) != null
                }
                val adapter = grid.adapter as MediaAdapter
                val orderedPaths = adapter.media.filterIsInstance<Medium>().map { it.path }
                val version = adapter.committedVersion
                val sourceRequest = MediaActivity::class.java.getDeclaredField("mediaRefreshRequest").apply { isAccessible = true }
                val initialRequest = sourceRequest.getLong(source)
                val pendingLaunch = MediaActivity::class.java.getDeclaredField("viewerLaunchInProgress").apply { isAccessible = true }
                val open = MediaActivity::class.java.getDeclaredMethod("openInViewPager", String::class.java).apply { isAccessible = true }
                val catalog = ViewPagerActivity::class.java.getDeclaredMethod("getCurrentMedia").apply { isAccessible = true }
                val currentPath = ViewPagerActivity::class.java.getDeclaredMethod("getCurrentPath").apply { isAccessible = true }
                val viewerWorker = ViewPagerActivity::class.java.getDeclaredField("viewerLoadTask").apply { isAccessible = true }
                val complete = ViewPagerActivity::class.java.getDeclaredField("appliedViewerCatalogComplete").apply { isAccessible = true }

                fun visiblePath(position: Int, mediaPath: String) = await("Visible source thumbnail at $position") {
                    val image = grid.findViewHolderForAdapterPosition(position)?.itemView
                        ?.findViewById<ImageView>(R.id.medium_thumbnail)
                    image?.getTag(R.id.media_bound_path) == mediaPath &&
                        image?.getTag(R.id.thumbnail_loaded_key) != null
                }

                fun withViewer(path: String, action: (ViewPagerActivity) -> Unit) {
                    val position = adapter.media.indexOfFirst { it is Medium && it.path == path }
                    instrumentation.runOnMainSync { grid.scrollToPosition(position) }
                    visiblePath(position, path)
                    val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
                    var opened: ViewPagerActivity? = null
                    try {
                        instrumentation.runOnMainSync { open.invoke(source, path) }
                        opened = instrumentation.waitForMonitorWithTimeout(monitor, 5_000) as? ViewPagerActivity
                            ?: error("Viewer did not open")
                        val viewer = opened
                        await("Complete Viewer catalog without a worker") {
                            complete.getBoolean(viewer) && (catalog.invoke(viewer) as List<*>).size == count
                        }
                        instrumentation.runOnMainSync {
                            assertEquals(orderedPaths, (catalog.invoke(viewer) as List<*>).filterIsInstance<Medium>().map { it.path })
                            assertEquals(path, currentPath.invoke(viewer))
                            assertNull("Valid catalog must avoid starting a scan", viewerWorker.get(viewer))
                        }
                        action(viewer)
                    } finally {
                        opened?.let { viewer -> instrumentation.runOnMainSync { viewer.finish() } }
                        instrumentation.removeMonitor(monitor)
                    }
                    await("Viewer return cleanup") {
                        source.hasWindowFocus() && !pendingLaunch.getBoolean(source)
                    }
                }

                repeat(50) { cycle ->
                    val start = if (cycle == 0) orderedPaths.first() else orderedPaths.last()
                    withViewer(start) { viewer ->
                        if (cycle == 0) {
                            instrumentation.runOnMainSync { viewer.goToPrevItem() }
                            assertEquals(start, currentPath.invoke(viewer))
                            repeat(50) { index ->
                                instrumentation.runOnMainSync { viewer.goToNextItem() }
                                await("Viewer swipe ${index + 1}") { currentPath.invoke(viewer) == orderedPaths[index + 1] }
                            }
                        } else {
                            instrumentation.runOnMainSync { viewer.goToNextItem() }
                            assertEquals(start, currentPath.invoke(viewer))
                        }
                    }
                    val expectedReturn = if (cycle == 0) orderedPaths[50] else start
                    visiblePath(adapter.media.indexOfFirst { it is Medium && it.path == expectedReturn }, expectedReturn)
                    assertEquals(initialRequest, sourceRequest.getLong(source))
                    assertEquals(version, adapter.committedVersion)
                }

                withViewer(orderedPaths.first()) { viewer ->
                    val added = File(folder, "new.jpg")
                    val removed = File(orderedPaths.last())
                    added.writeBytes(jpeg)
                    assertTrue(removed.delete())
                    await("Reconcile source changes while Viewer is open", 20_000) {
                        val rows = (catalog.invoke(viewer) as List<*>).filterIsInstance<Medium>()
                        complete.getBoolean(viewer) && rows.any { it.path == added.path } && rows.none { it.path == removed.path }
                    }
                    instrumentation.runOnMainSync { assertNotNull(viewerWorker.get(viewer)) }
                }
            } finally {
                instrumentation.runOnMainSync { source.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            context.config.showAll = previousShowAll
            context.config.filterMedia = previousFilter
            context.mediaDB.getMediaFromPath(folder.path).forEach { context.mediaDB.deleteMediumPath(it.path) }
            folder.deleteRecursively()
        }
    }

    private fun setAllFilesAccess(granted: Boolean) {
        val mode = if (granted) "allow" else "default"
        val command = "appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE $mode"
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    private fun await(label: String, timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail(label)
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bitmap.recycle()
        }.toByteArray()
    }
}
