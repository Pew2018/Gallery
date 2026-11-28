package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.recycleBin
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuditRecycleBinEmptyTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dbExecutor = Executors.newSingleThreadExecutor()

    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.filesDir.resolve("gallery.db").delete()
    }

    @After fun cleanup() {
        dbExecutor.shutdownNow()
        GalleryDatabase.destroyInstance()
    }

    private fun <T> db(block: () -> T): T =
        dbExecutor.submit<T>(block).get(5, TimeUnit.SECONDS)

    @Test fun emptyingRecycleBinKeepsDiagnosticsAndRestoreCheckpoint() {
        val filesDir = context.filesDir
        val recycled = File(context.recycleBinPath, "storage/emulated/0/DCIM/gone.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val diagnostics = File(filesDir, "diagnostics").apply { mkdirs() }
        File(diagnostics, "events.log").writeText("keep")
        val checkpoint = File(filesDir, "gallery-restore-checkpoint.json").apply { writeText("{}") }
        val checkpointBak = File(filesDir, "gallery-restore-checkpoint.json.bak").apply { writeText("{}") }

        db {
            context.mediaDB.insert(Medium().apply {
                path = "${RECYCLE_BIN}/storage/emulated/0/DCIM/gone.jpg"
                name = "gone.jpg"
                parentPath = RECYCLE_BIN
                deletedTS = 1L
            })
            context.directoryDB.insert(
                Directory(
                    id = null, path = RECYCLE_BIN, tmb = "", name = "Bin", mediaCnt = 1,
                    modified = 1, taken = 1, size = 1, location = 0, types = 1, sortValue = "",
                )
            )
        }

        val activity = Robolectric.buildActivity(SimpleActivity::class.java).setup().get()
        val done = CountDownLatch(1)
        activity.emptyTheRecycleBin { done.countDown() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done.await(50, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertTrue("emptyTheRecycleBin should finish", done.count == 0L || !recycled.exists())
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(recycled.exists())
        assertTrue(filesDir.isDirectory)
        assertTrue(diagnostics.isDirectory)
        assertTrue(File(diagnostics, "events.log").exists())
        assertTrue(checkpoint.exists())
        assertTrue(checkpointBak.exists())
        assertEquals(0L, db { context.mediaDB.getDeletedMediaCount() })
        assertNull(db { context.directoryDB.getByPath(RECYCLE_BIN) })
        assertSame(filesDir, context.recycleBin)
    }
}
