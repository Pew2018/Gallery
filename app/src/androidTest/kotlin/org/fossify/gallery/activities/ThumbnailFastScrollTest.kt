package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.widget.CheckBox
import android.widget.ImageView
import android.view.View
import android.view.inspector.WindowInspector
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ThumbnailFastScrollTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun grantMediaAccess() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val required = if (Build.VERSION.SDK_INT >= 30) permissions + Manifest.permission.ACCESS_MEDIA_LOCATION else permissions
        required.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
        if (Build.VERSION.SDK_INT >= 30) {
            val command = "appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
            assertTrue(Environment.isExternalStorageManager())
        }
    }

    @Test fun stableGridsRecoverAfterColdJumpsAndWarmReversals() {
        grantMediaAccess()
        val previousShowAll = context.config.showAll
        val previousFilter = context.config.filterMedia
        try {
            context.config.showAll = false
            context.config.filterMedia = TYPE_IMAGES
            val colors = listOf(jpeg(Color.RED), jpeg(Color.BLUE))
            for (count in listOf(1500, 3080, 5000, 10023)) exercise(count, colors)
        } finally {
            context.config.showAll = previousShowAll
            context.config.filterMedia = previousFilter
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun filtering3080To288KeepsTheVideoGridAndReloadsAuthority() {
        grantMediaAccess()
        val previousShowAll = context.config.showAll
        val previousFilter = context.config.filterMedia
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GalleryFilterStageR-${UUID.randomUUID()}")
        assertTrue(folder.mkdirs())
        try {
            context.config.showAll = false
            context.config.filterMedia = TYPE_IMAGES or TYPE_VIDEOS
            File(folder, ".nomedia").createNewFile()
            val photo = jpeg(Color.RED)
            val video = instrumentation.context.assets.open("gallery-landscape.mp4").use { it.readBytes() }
            repeat(2792) { File(folder, "image-$it.jpg").writeBytes(photo) }
            repeat(288) { File(folder, "video-$it.mp4").writeBytes(video) }

            val activity = instrumentation.startActivitySync(Intent(context, MediaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DIRECTORY, folder.path)) as MediaActivity
            try {
                val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
                val complete = MediaActivity::class.java.getDeclaredField("displayedMediaIsFinal").apply { isAccessible = true }
                val showFilter = MediaActivity::class.java.getDeclaredMethod("showFilterMediaDialog").apply { isAccessible = true }
                assertTrue("Initial complete mixed folder", await(30_000) {
                    complete.getBoolean(activity) && (grid.adapter as? MediaAdapter)
                        ?.media?.count { it is Medium } == 3080
                })
                val adapter = grid.adapter as MediaAdapter
                val firstVideo = adapter.media.indexOfFirst { it is Medium && it.type == TYPE_VIDEOS }
                assertTrue(firstVideo >= 0)
                instrumentation.runOnMainSync {
                    (grid.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(firstVideo, 0)
                }
                assertTrue("A video thumbnail is loaded before filtering", await(4_000) {
                    grid.findViewHolderForAdapterPosition(firstVideo)?.itemView
                        ?.findViewById<ImageView>(R.id.medium_thumbnail)?.getTag(R.id.thumbnail_loaded_key) != null
                })

                fun filterDialog(): View? = WindowInspector.getGlobalWindowViews().lastOrNull {
                    it.isShown && it.findViewById<View>(R.id.filter_media_dialog_holder) != null
                }

                fun toggleImages(): Long {
                    instrumentation.runOnMainSync { showFilter.invoke(activity) }
                    assertTrue("Filter dialog is visible", await(3_000) { filterDialog() != null })
                    var confirmedAt = 0L
                    instrumentation.runOnMainSync {
                        val dialog = requireNotNull(filterDialog())
                        val images = dialog.findViewById<CheckBox>(R.id.filter_media_images)
                        val previous = images.isChecked
                        assertTrue((images.parent as View).performClick())
                        assertEquals(!previous, images.isChecked)
                        confirmedAt = SystemClock.elapsedRealtime()
                        assertTrue(dialog.findViewById<View>(android.R.id.button1).performClick())
                    }
                    return confirmedAt
                }

                val started = toggleImages()
                assertTrue("Filtered view contains only the 288 surviving videos and retains the adapter", await(1_000) {
                    assertSame(adapter, grid.adapter)
                    val rows = adapter.media.filterIsInstance<Medium>()
                    rows.size == 288 && rows.all { it.type == TYPE_VIDEOS }
                })
                val projectedMs = SystemClock.elapsedRealtime() - started
                var visibleCount = 0
                var loadedCount = 0
                var halfReadyMs: Long? = null
                val recovered = await(3_000) {
                    val visible = (0 until grid.childCount).mapNotNull { index ->
                        val child = grid.getChildAt(index)
                        val image = child.findViewById<ImageView>(R.id.medium_thumbnail)
                        if (image == null || !image.isShown || !image.getLocalVisibleRect(Rect())) null
                        else adapter.media.getOrNull(grid.getChildAdapterPosition(child)) as? Medium to image
                    }
                    visibleCount = visible.size
                    loadedCount = visible.count { (row, image) ->
                        val loaded = image.getTag(R.id.thumbnail_loaded_key)
                        row?.type == TYPE_VIDEOS && image.getTag(R.id.media_bound_path) == row.path &&
                            loaded != null && loaded === image.getTag(R.id.thumbnail_request_key)
                    }
                    if (visibleCount > 0 && loadedCount * 2 >= visibleCount && halfReadyMs == null) {
                        halfReadyMs = SystemClock.elapsedRealtime() - started
                    }
                    visibleCount > 0 && loadedCount == visibleCount
                }
                val recoveredMs = SystemClock.elapsedRealtime() - started
                Log.i("GalleryStageR", "filter3080To288 projectedMs=$projectedMs halfReadyMs=$halfReadyMs " +
                    "viewportMs=$recoveredMs loadedCount=$loadedCount visibleCount=$visibleCount")
                assertTrue("Filtered viewport has only matching, loaded thumbnails ($loadedCount/$visibleCount)", recovered)
                assertTrue("Narrowed scan reaches authoritative completion", await(15_000) {
                    complete.getBoolean(activity) && adapter.media.count { it is Medium } == 288
                })

                toggleImages()
                assertTrue("Broader filter discovers previously excluded rows", await(15_000) {
                    assertSame(adapter, grid.adapter)
                    complete.getBoolean(activity) && adapter.media.count { it is Medium } == 3080
                })
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            context.config.showAll = previousShowAll
            context.config.filterMedia = previousFilter
            context.mediaDB.getMediaFromPath(folder.path).forEach { context.mediaDB.deleteMediumPath(it.path) }
            folder.deleteRecursively()
        }
    }

    private fun exercise(count: Int, colors: List<ByteArray>) {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GalleryStageR-${UUID.randomUUID()}")
        assertTrue(folder.mkdirs())
        try {
            File(folder, ".nomedia").createNewFile()
            repeat(count) { index -> File(folder, "$index.jpg").writeBytes(colors[index % 2]) }
            val activity = instrumentation.startActivitySync(Intent(context, MediaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DIRECTORY, folder.path)) as MediaActivity
            try {
                val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
                assertTrue("Complete list of $count items", await(30_000) {
                    (grid.adapter as? MediaAdapter)?.media?.count { it is Medium } == count
                })
                val adapter = grid.adapter as MediaAdapter
                val positions = adapter.media.indices.filter { adapter.media[it] is Medium }
                val version = adapter.committedVersion

                fun jump(position: Int) = instrumentation.runOnMainSync {
                    (grid.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
                }

                fun jumpAndMeasure(position: Int): Long {
                    instrumentation.runOnMainSync { adapter.setFastScrollActive(true) }
                    jump(positions[count / 8])
                    assertTrue(await(1_000) { grid.findViewHolderForAdapterPosition(positions[count / 8]) != null })
                    jump(position)
                    assertTrue(await(1_000) { grid.findViewHolderForAdapterPosition(position) != null })
                    val start = SystemClock.elapsedRealtime()
                    instrumentation.runOnMainSync { adapter.setFastScrollActive(false) }
                    assertTrue("Visible thumbnails after jump to $position/$count", await(3_000) {
                        assertSame(adapter, grid.adapter)
                        assertEquals(version, adapter.committedVersion)
                        if (grid.findViewHolderForAdapterPosition(position) == null) return@await false
                        var visible = 0
                        var ready = 0
                        for (index in 0 until grid.childCount) {
                            val child = grid.getChildAt(index)
                            val image = child.findViewById<ImageView>(R.id.medium_thumbnail) ?: continue
                            if (!image.isShown || !image.getLocalVisibleRect(Rect())) continue
                            visible++
                            val medium = adapter.media.getOrNull(grid.getChildAdapterPosition(child)) as? Medium
                                ?: continue
                            assertEquals(medium.path, image.getTag(R.id.media_bound_path))
                            assertTrue(image.width > 0 && image.height > 0)
                            val loaded = image.getTag(R.id.thumbnail_loaded_key)
                            if (loaded == null || loaded !== image.getTag(R.id.thumbnail_request_key)) continue
                            val bitmap = (image.drawable as? BitmapDrawable)?.bitmap ?: continue
                            val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
                                bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            } else bitmap
                            val color = readable.getPixel(readable.width / 2, readable.height / 2)
                            if (readable !== bitmap) readable.recycle()
                            val expectedRed = medium.name.substringBefore('.').toInt() % 2 == 0
                            assertEquals("Pixel color for ${medium.name}", expectedRed, Color.red(color) > Color.blue(color))
                            ready++
                        }
                        visible > 0 && ready == visible
                    })
                    return SystemClock.elapsedRealtime() - start
                }

                val cold = mutableListOf<Long>()
                val warm = mutableListOf<Long>()
                repeat(5) { iteration ->
                    val destination = positions[count / 2 + iteration * (count / 12)]
                    jump(positions[count / 4])
                    // Leave time for layout so each jump actually recycles a viewport.
                    assertTrue(await(1_000) { grid.findViewHolderForAdapterPosition(positions[count / 4]) != null })
                    cold += jumpAndMeasure(destination)
                    jump(positions[count / 4])
                    assertTrue(await(1_000) { grid.findViewHolderForAdapterPosition(positions[count / 4]) != null })
                    warm += jumpAndMeasure(destination)
                }
                Log.i("GalleryStageR", "count=$count stableVersion=$version coldMs=$cold warmMs=$warm")
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            context.mediaDB.getMediaFromPath(folder.path).forEach { context.mediaDB.deleteMediumPath(it.path) }
            folder.deleteRecursively()
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            var succeeded = false
            instrumentation.runOnMainSync { succeeded = condition() }
            if (succeeded) return true
            SystemClock.sleep(16)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().also { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bitmap.recycle()
        }.toByteArray()
    }
}
