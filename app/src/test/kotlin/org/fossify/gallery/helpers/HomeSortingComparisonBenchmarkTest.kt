package org.fossify.gallery.helpers

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.RadioGroup
import androidx.room.Room
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.*
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Portable to audit HEAD: same actual sorting dialog, dataset and host runtime on both revisions.
 * Do not infer phone latency or a speedup percentage from this host benchmark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HomeSortingComparisonBenchmarkTest {
    @Test fun measureActualDialogColdWarmDirectionsAndGrouping() {
        val app = RuntimeEnvironment.getApplication()
        GalleryDatabase.destroyInstance()
        app.getSharedPrefs().edit().clear().commit()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        app.config.filterMedia = TYPE_IMAGES
        app.config.groupDirectSubfolders = false
        app.config.showAll = false
        app.config.directorySorting = SORT_BY_NAME
        GalleryDiagnosticLogger.setEnabled(app, true)
        val root = Files.createTempDirectory("home-sort-comparison").toFile()
        val albums = (0..2).map { File(root, "Album $it").apply { mkdirs() } }
        albums.forEachIndexed { album, folder ->
            File(folder, ".nomedia").createNewFile()
            repeat(120) { item -> File(folder, "$item.jpg").apply {
                writeBytes(ByteArray(item + 1)); setLastModified(1000L * (item + 1 + album * 10))
            } }
        }
        app.config.includedFolders = albums.mapTo(mutableSetOf()) { it.path }
        app.config.everShownFolders = albums.mapTo(mutableSetOf()) { it.path }
        val reads = AtomicInteger()
        val writes = AtomicInteger()
        app.deleteDatabase("sort-comparison.db")
        val db = Room.databaseBuilder(app, GalleryDatabase::class.java, "sort-comparison.db")
            .setQueryCallback({ sql, _ ->
                val statement = sql.trimStart().uppercase(java.util.Locale.ROOT)
                if (statement.startsWith("SELECT")) reads.incrementAndGet()
                if (statement.startsWith("INSERT") || statement.startsWith("UPDATE") || statement.startsWith("DELETE")) writes.incrementAndGet()
            }, java.util.concurrent.Executor { it.run() }).build()
        GalleryDatabase::class.java.getDeclaredField("db").apply { isAccessible = true }.set(null, db)
        val provider = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val controller = Robolectric.buildActivity(MainActivity::class.java,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)).create(Bundle()).start()
        val activity = controller.get()
        fun adapter() = activity.findViewById<MyRecyclerView>(R.id.directories_grid).adapter as? DirectoryAdapter
        fun loading() = MainActivity::class.java.getDeclaredField("mIsGettingDirs").apply { isAccessible = true }.getBoolean(activity)
        fun settled() = !loading() && MainActivity::class.java.getDeclaredField("directoryPresentationDiscard").apply { isAccessible = true }.get(activity) == null
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < until) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertTrue("benchmark did not settle", condition())
        }
        val workers = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MediaFetcher, Boolean>())
        fun capture() {
            (MainActivity::class.java.getDeclaredField("mLastMediaFetcher").apply { isAccessible = true }.get(activity) as? MediaFetcher)?.let(workers::add)
        }
        fun counters(): List<Long> {
            capture()
            fun sum(key: String) = workers.sumOf { it.pipelineDiagnostics?.snapshot()?.get(key) ?: 0L }
            return listOf(provider.queries.size.toLong(), sum("diskEnumerateCount"), sum("metadataReadCount"),
                sum("sortCount"), sum("sortInputItems"), reads.get().toLong(), writes.get().toLong())
        }
        fun sort(mode: Int) {
            MainActivity::class.java.getDeclaredMethod("showSortingDialog").apply { isAccessible = true }.invoke(activity)
            val dialog = ShadowDialog.getLatestDialog()
            val id = when {
                mode and SORT_BY_SIZE != 0 -> R.id.sorting_dialog_radio_size
                mode and SORT_BY_DATE_MODIFIED != 0 -> R.id.sorting_dialog_radio_last_modified
                mode and SORT_BY_DATE_TAKEN != 0 -> R.id.sorting_dialog_radio_date_taken
                mode and SORT_BY_COUNT != 0 -> R.id.sorting_dialog_radio_number_of_items
                else -> R.id.sorting_dialog_radio_name
            }
            dialog.findViewById<RadioGroup>(R.id.sorting_dialog_radio_sorting).check(id)
            dialog.findViewById<RadioGroup>(R.id.sorting_dialog_radio_order).check(
                if (mode and SORT_DESCENDING != 0) R.id.sorting_dialog_radio_descending else R.id.sorting_dialog_radio_ascending)
            dialog.findViewById<android.widget.CheckBox>(R.id.sorting_dialog_numeric_sorting).isChecked = false
            dialog.findViewById<android.view.View>(android.R.id.button1).performClick()
            // Dialog buttons dispatch through a Handler. Do not mistake the old idle state for completion.
            await { app.config.directorySorting == mode }
        }
        try {
            val cold = System.nanoTime()
            controller.resume()
            await { adapter()?.dirs?.count { it.path in albums.map(File::getPath) && it.mediaCnt == 120 } == 3 }
            val firstMs = (System.nanoTime() - cold) / 1_000_000.0
            await(::settled)
            repeat(10) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
            println("HomeSortComparison cold firstUsableMs=$firstMs fullStatsMs=${(System.nanoTime()-cold)/1_000_000.0} counters=${counters()}")
            for (mode in listOf(SORT_BY_SIZE, SORT_BY_DATE_MODIFIED, SORT_BY_DATE_MODIFIED or SORT_DESCENDING,
                SORT_BY_DATE_TAKEN, SORT_BY_DATE_TAKEN or SORT_DESCENDING, SORT_BY_COUNT, SORT_BY_NAME)) {
                val before = counters()
                val started = System.nanoTime()
                sort(mode)
                await(::settled)
                val latency = (System.nanoTime()-started)/1_000_000.0
                val after = counters()
                assertEquals(360, adapter()!!.dirs.filter { it.path in albums.map(File::getPath) }.sumOf { it.mediaCnt })
                println("HomeSortComparison mode=$mode dialogToCommitMs=$latency delta=${after.zip(before) { a, b -> a-b }}")
            }
            val beforeGroup = counters()
            app.config.groupDirectSubfolders = true
            val started = System.nanoTime()
            MainActivity::class.java.getDeclaredMethod("refreshDirectoryPresentation").apply { isAccessible = true }.invoke(activity)
            await(::settled)
            assertEquals(360, adapter()!!.dirs.sumOf { it.subfoldersMediaCount })
            println("HomeSortComparison grouped commitMs=${(System.nanoTime()-started)/1_000_000.0} delta=${counters().zip(beforeGroup) { a,b -> a-b }}")
        } finally {
            controller.pause().stop().destroy(); GalleryDiagnosticLogger.setEnabled(app, false)
            root.deleteRecursively(); provider.db.close(); GalleryDatabase.destroyInstance(); app.deleteDatabase("sort-comparison.db")
        }
    }
}
