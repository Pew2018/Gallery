package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GalleryDiagnosticLogger
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.models.Medium
import org.fossify.commons.views.MyRecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Synthetic, isolated media. Run with a debuggable FOSS APK and all-files access. */
@RunWith(AndroidJUnit4::class)
class MediaPageStartupTest {
    @Test fun largeUnindexedFolderPaintsAndCompletesWithAndWithoutRoomCache() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val granted = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= 30) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        granted.forEach { automation.grantRuntimePermission(context.packageName, it) }
        if (Build.VERSION.SDK_INT >= 30) {
            automation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow").let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { output -> output.readBytes() }
            }
            assertTrue("All-files access is required on the test device", Environment.isExternalStorageManager())
        }
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "GalleryStageA-${UUID.randomUUID()}")
        assertTrue(folder.mkdirs())
        val previousShowAll = context.config.showAll
        val previousFilter = context.config.filterMedia
        val prefs = context.getSharedPrefs()
        val loggingEnabled = GalleryDiagnosticLogger.isEnabled()
        val previousLogging = prefs.getBoolean(GalleryDiagnosticLogger.ENABLED_KEY, false)
        val previousExpiry = prefs.getLong(GalleryDiagnosticLogger.EXPIRES_KEY, 0)
        val records = File(context.filesDir, "diagnostics/diagnostic-0.log")
        try {
            context.config.showAll = false
            context.config.filterMedia = TYPE_IMAGES
            GalleryDiagnosticLogger.setEnabled(context, true)
            File(folder, ".nomedia").createNewFile()
            val bitmap = android.graphics.Bitmap.createBitmap(96, 64, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(63, 142, 220))
            val image = java.io.ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it)
            }.toByteArray()
            bitmap.recycle()
            repeat(5000) { File(folder, "photo-%04d.jpg".format(it)).writeBytes(image) }

            fun openAndWait(label: String) {
                val offset = records.length()
                val intent = Intent(context, MediaActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(DIRECTORY, folder.path)
                }
                val activity = instrumentation.startActivitySync(intent) as MediaActivity
                try {
                    val deadline = SystemClock.elapsedRealtime() + 50_000
                    var visible = false
                    var finished = false
                    while (SystemClock.elapsedRealtime() < deadline && !finished) {
                        instrumentation.runOnMainSync {
                            val grid = activity.findViewById<MyRecyclerView>(R.id.media_grid)
                            val adapter = grid.adapter as? MediaAdapter
                            visible = visible || (0 until grid.childCount).any { index ->
                                val imageView = grid.getChildAt(index)
                                    .findViewById<android.widget.ImageView>(R.id.medium_thumbnail)
                                imageView?.isShown == true && imageView.getTag(R.id.thumbnail_loaded_key) != null
                            }
                            finished = visible && adapter?.media?.count { it is Medium } == 5000 &&
                                activity.findViewById<View>(R.id.media_fastscroller).visibility == View.VISIBLE
                        }
                        if (!finished) SystemClock.sleep(25)
                    }
                    assertTrue("$label must show a real thumbnail", visible)
                    assertTrue("$label must publish every source item", finished)
                    val flushBy = SystemClock.elapsedRealtime() + 1500
                    var recent = ""
                    while (SystemClock.elapsedRealtime() < flushBy &&
                        (!recent.contains("media_final_commit") || !recent.contains("media_first_thumbnail_rendered"))) {
                        recent = if (records.isFile) records.readText().drop(offset.toInt()) else ""
                        if (!recent.contains("media_first_thumbnail_rendered")) SystemClock.sleep(25)
                    }
                    val events = recent.lineSequence().filter {
                        it.contains("module=FolderRefresh") || it.contains("module=MediaPipeline") ||
                            it.contains("event=adapter_diff_finished")
                    }.toList()
                    events.forEach { android.util.Log.i("GalleryStageA", "$label $it") }
                    val first = events.indexOfFirst { it.contains("event=media_first_adapter_commit") }
                    val thumbnail = events.indexOfFirst { it.contains("event=media_first_thumbnail_rendered") }
                    val complete = events.indexOfFirst { it.contains("event=media_scan_complete") }
                    val last = events.indexOfFirst { it.contains("event=media_final_commit") }
                    assertTrue("$label must commit preview before final catalog", first in 0 until last)
                    assertTrue("$label must paint a real thumbnail before full scan completion",
                        thumbnail in 0 until complete)
                } finally {
                    instrumentation.runOnMainSync { activity.finish() }
                    instrumentation.waitForIdleSync()
                }
            }

            openAndWait("empty-cache")
            val cacheDeadline = SystemClock.elapsedRealtime() + 5000
            while (context.mediaDB.getMediaFromPath(folder.path).size < 5000 &&
                SystemClock.elapsedRealtime() < cacheDeadline) SystemClock.sleep(25)
            assertEquals(5000, context.mediaDB.getMediaFromPath(folder.path).size)
            openAndWait("warm-cache")
        } finally {
            context.mediaDB.getMediaFromPath(folder.path).forEach { context.mediaDB.deleteMediumPath(it.path) }
            folder.deleteRecursively()
            context.config.showAll = previousShowAll
            context.config.filterMedia = previousFilter
            GalleryDiagnosticLogger.setEnabled(context, loggingEnabled)
            prefs.edit().putBoolean(GalleryDiagnosticLogger.ENABLED_KEY, previousLogging)
                .putLong(GalleryDiagnosticLogger.EXPIRES_KEY, previousExpiry).apply()
            GalleryDiagnosticLogger.syncEnabledFromPreferences()
        }
    }
}
