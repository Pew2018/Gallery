package org.fossify.gallery.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Every bottom bar root must leave the app on back instead of walking back through the
 * tabs, and no root may keep an enabled callback: an enabled callback swallows the
 * gesture and suppresses the system's predictive exit preview.
 */
@RunWith(AndroidJUnit4::class)
class AppBackNavigationTest {

    @Test
    fun albumsRootExitsWithoutKeepingABackCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val previousViewAll = context.config.lastMainViewAll
        var home: MainActivity? = null
        try {
            context.config.lastMainViewAll = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            launch.putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            instrumentation.startActivitySync(launch)
            home = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? MainActivity
                ?: error("Gallery did not finish launching MainActivity")
            instrumentation.removeMonitor(monitor)
            val activity = home!!

            assertFalse(
                "the Albums root must not hold an enabled back callback",
                activity.onBackPressedDispatcher.hasEnabledCallbacks()
            )

            // A real key back reaches the same dispatcher the predictive gesture uses.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            assertTrue("back on the Albums root must leave the app", activity.isFinishing)
        } finally {
            context.config.lastMainViewAll = previousViewAll
            home?.let { if (!it.isFinishing) it.finish() }
        }
    }

    @Test
    fun folderRootExitsInsteadOfWalkingBackThroughTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryBackNavigation-${UUID.randomUUID()}"
        val fixtures = List(3) { createFixture(context, album) }
        val folder = File(fixtures.first()).parent
        val previousShowAll = context.config.showAll
        val previousFilter = context.config.filterMedia
        var activity: MediaActivity? = null
        try {
            context.config.showAll = false
            context.config.filterMedia = TYPE_IMAGES
            val opened = instrumentation.startActivitySync(Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, folder)
            }) as MediaActivity
            activity = opened

            assertFalse(
                "a folder root must not hold an enabled back callback",
                opened.onBackPressedDispatcher.hasEnabledCallbacks()
            )

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            assertTrue("back in a folder must return to its parent page", opened.isFinishing)
        } finally {
            activity?.let { if (!it.isFinishing) it.finish() }
            context.config.showAll = previousShowAll
            context.config.filterMedia = previousFilter
            fixtures.mapNotNull { File(it).parentFile }.distinct().forEach { dir ->
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        }
    }

    private fun grantMediaPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun createFixture(context: Context, album: String): String {
        val dir = File(Environment.getExternalStorageDirectory(), album)
        dir.mkdirs()
        val file = File(dir, "back-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(60, 90, 160))
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        return file.absolutePath
    }
}
