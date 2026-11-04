package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Point
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.dialogs.ResizeMultipleImagesDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GalleryRefreshRegressionTest {
    private fun directory(name: String) = Directory().apply { path = "/Pictures/$name"; this.name = name }
    private fun medium(name: String) = Medium().apply { path = "/Pictures/$name.jpg"; this.name = name; type = TYPE_IMAGES }
    private fun activity() = Robolectric.buildActivity(SimpleActivity::class.java).setup()
    private fun recycler(activity: SimpleActivity): MyRecyclerView {
        val root = activity.layoutInflater.inflate(R.layout.activity_main, null)
        activity.setContentView(root)
        return root.findViewById(R.id.directories_grid)
    }
    private class ManualExecutor : Executor {
        private val tasks = ArrayList<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun drain() { val pending = tasks.toList(); tasks.clear(); pending.forEach { it.run() } }
    }

    @Test fun randomAlbumOrderSurvivesRefreshAndNewFoldersWithoutReshufflingSurvivors() {
        val controller = activity()
        val activity = controller.get()
        activity.config.directorySorting = SORT_BY_RANDOM
        val ranks = HashMap<String, Long>()
        val original = arrayListOf(directory("a"), directory("b"), directory("c"))
        val first = activity.getSortedDirectories(original, ranks).map { it.path }
        repeat(8) {
            assertEquals(first, activity.getSortedDirectories(ArrayList(original.reversed()), ranks).map { it.path })
        }
        val expanded = activity.getSortedDirectories(ArrayList(original + directory("new")), ranks)
        assertEquals(first, expanded.map { it.path }.filter { it in first })
        controller.pause().stop().destroy()
    }

    @Test fun noOpDirectorySubmissionCancelsPendingDifferentListAndRetainsOriginalSnapshot() {
        val controller = activity()
        val activity = controller.get()
        val view = recycler(activity)
        val executor = ManualExecutor()
        val a = directory("a")
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, view, false,
            diffExecutor = executor) { }
        view.adapter = adapter
        adapter.updateDirs(arrayListOf(directory("b")))
        adapter.updateDirs(arrayListOf(a.copy()))
        executor.drain()
        assertEquals(listOf(a.path), adapter.dirs.map { it.path })
        val incoming = directory("c")
        adapter.updateDirs(arrayListOf(incoming))
        incoming.name = "mutated after submission"
        executor.drain()
        assertEquals("c", adapter.dirs.single().name)
        view.adapter = null
        controller.pause().stop().destroy()
    }

    @Test fun mediaRefreshUsesGranularUpdatesAndDetachedOrRevokedRequestsCannotCommit() {
        val controller = activity()
        val activity = controller.get()
        val view = recycler(activity)
        val executor = ManualExecutor()
        val a = medium("a")
        val adapter = MediaAdapter(activity, arrayListOf<ThumbnailItem>(a), null, false, false,
            "/Pictures", view, diffExecutor = executor) { }
        view.adapter = adapter
        var fullInvalidations = 0
        var insertions = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { fullInvalidations++ }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { insertions += itemCount }
        })
        adapter.updateMedia(arrayListOf<ThumbnailItem>(a.copy(), medium("b")))
        executor.drain()
        assertEquals(2, adapter.itemCount)
        assertEquals(1, insertions)
        assertEquals(0, fullInvalidations)
        adapter.updateMedia(arrayListOf<ThumbnailItem>(medium("stale")))
        adapter.updateMedia(arrayListOf<ThumbnailItem>(a.copy(), medium("b")))
        executor.drain()
        assertEquals(listOf("a", "b"), adapter.media.filterIsInstance<Medium>().map { it.name })
        var allowed = true
        adapter.updateMedia(arrayListOf<ThumbnailItem>(medium("revoked")), isCurrent = { allowed })
        allowed = false
        executor.drain()
        assertEquals(2, adapter.itemCount)
        adapter.updateMedia(arrayListOf<ThumbnailItem>(medium("detached")))
        view.adapter = null
        executor.drain()
        assertEquals(2, adapter.itemCount)
        controller.pause().stop().destroy()
    }

    @Test fun batchResizeRejectsOverflowWithoutCrashingOrStartingWork() {
        val controller = activity()
        val activity = controller.get()
        var completed = false
        ResizeMultipleImagesDialog(activity, listOf("/Pictures/a.jpg"), listOf(Point(100, 100))) { completed = true }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val input = dialog.findViewById<android.widget.EditText>(R.id.resize_factor_edit_text)!!
        // Verify the parser too, independently of the convenience input length bound.
        input.filters = emptyArray()
        input.setText("999999999999")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertFalse(completed)
        assertEquals(activity.getString(R.string.resize_factor_error), ShadowToast.getTextOfLatestToast())
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun metadataPathsRemainActiveUntilTheDiffReallyCommits() {
        val controller = activity()
        val activity = controller.get()
        val view = recycler(activity)
        val executor = ManualExecutor()
        val gate = DirectoryMetadataRebindGate()
        val a = directory("a")
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, view, false, diffExecutor = executor) { }
        view.adapter = adapter
        gate.enqueue(listOf(a.path))
        val request = gate.begin()!!
        adapter.updateDirs(arrayListOf(a.copy(mediaCnt = 9)),
            onCommitted = { gate.finish(request.id, true) }, onDiscarded = { gate.finish(request.id, false) })
        assertTrue(gate.isActive())
        gate.enqueue(listOf("/another-change"))
        executor.drain()
        assertFalse(gate.isActive())
        assertEquals(9, adapter.dirs.single().mediaCnt)
        assertEquals(setOf("/another-change"), gate.begin()!!.paths)
        view.adapter = null
        controller.pause().stop().destroy()
    }
    @Test fun replacingOrSupersedingAnAdapterRetainsUncommittedMetadata() {
        val controller = activity()
        val activity = controller.get()
        val view = recycler(activity)
        val executor = ManualExecutor()
        val gate = DirectoryMetadataRebindGate()
        val a = directory("a")
        val adapter = DirectoryAdapter(activity, arrayListOf(a), null, view, false, diffExecutor = executor) { }
        view.adapter = adapter
        gate.enqueue(listOf(a.path))
        var request = gate.begin()!!
        val firstId = request.id
        adapter.updateDirs(arrayListOf(a.copy(mediaCnt = 9)),
            onCommitted = { gate.finish(firstId, true) }, onDiscarded = { gate.finish(firstId, false) })
        adapter.updateDirs(arrayListOf(directory("other")))
        assertTrue(gate.hasPending())
        request = gate.begin()!!
        val secondId = request.id
        adapter.updateDirs(arrayListOf(a.copy(mediaCnt = 10)),
            onCommitted = { gate.finish(secondId, true) }, onDiscarded = { gate.finish(secondId, false) })
        view.adapter = null
        executor.drain()
        assertTrue(gate.hasPending())
        assertFalse(gate.isActive())
        controller.pause().stop().destroy()
    }
    @Test fun everyProgressiveSnapshotUsesCurrentAlbumSortWithoutChangingFolderMediaSort() {
        val controller = activity()
        val activity = controller.get()
        try {
            activity.config.groupDirectSubfolders = false
            activity.config.saveCustomSorting("/Pictures/private-order", org.fossify.commons.helpers.SORT_BY_SIZE)
            val source = arrayListOf(directory("20"), directory("3"), directory("1"))
            source.forEachIndexed { index, item ->
                item.size = (index + 1) * 10L
                item.mediaCnt = index + 1
                item.modified = (index + 1) * 100L
                item.taken = (index + 1) * 1000L
            }
            val modes = listOf(org.fossify.commons.helpers.SORT_BY_NAME,
                org.fossify.commons.helpers.SORT_BY_PATH, org.fossify.commons.helpers.SORT_BY_SIZE,
                org.fossify.commons.helpers.SORT_BY_COUNT, org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED,
                org.fossify.commons.helpers.SORT_BY_DATE_TAKEN)
            modes.forEach { mode ->
                for (descending in listOf(0, org.fossify.commons.helpers.SORT_DESCENDING)) {
                    activity.config.directorySorting = mode or descending
                    val home = activity.getSortedDirectories(source)
                    val favorites = activity.getSortedDirectories(ArrayList(source.reversed()))
                    assertEquals(home.map { it.path }, favorites.map { it.path })
                    val values = home.map { when(mode) {
                        org.fossify.commons.helpers.SORT_BY_SIZE -> it.size
                        org.fossify.commons.helpers.SORT_BY_COUNT -> it.mediaCnt.toLong()
                        org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED -> it.modified
                        org.fossify.commons.helpers.SORT_BY_DATE_TAKEN -> it.taken
                        else -> 0L
                    } }
                    if (values.any { it != 0L }) assertEquals(
                        if (descending == 0) values.sorted() else values.sortedDescending(), values)
                    else assertEquals(if (descending == 0) listOf("1", "20", "3") else listOf("3", "20", "1"), home.map { it.name })
                    assertEquals(org.fossify.commons.helpers.SORT_BY_SIZE,
                        activity.config.getFolderSorting("/Pictures/private-order"))
                }
            }
            activity.config.directorySorting = org.fossify.commons.helpers.SORT_BY_NAME or org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
            assertEquals(listOf("1", "3", "20"), activity.getSortedDirectories(source).map { it.name })
        } finally { controller.pause().stop().destroy() }
    }
}
