package org.fossify.gallery.helpers

import android.app.Application
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.models.Directory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DirectoryAdapterRefreshTest {
    private var previousPicasso: Any? = null
    private var hostPicasso: com.squareup.picasso.Picasso? = null
    private val picassoSingleton get() = com.squareup.picasso.Picasso::class.java.getDeclaredField("singleton").apply { isAccessible = true }

    @org.junit.Before fun installHostImages() {
        // Plain Application has no startup provider. This fixture must also run
        // alone, without depending on another test having initialized Picasso.
        previousPicasso = picassoSingleton.get(null)
        hostPicasso = com.squareup.picasso.Picasso.Builder(org.robolectric.RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, hostPicasso)
    }

    @org.junit.After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        hostPicasso?.shutdown()
    }

    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
    }

    private fun directory(path: String) = Directory().apply { this.path = path; name = path }

    private fun withAdapter(block: (DirectoryAdapter, MyRecyclerView) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity)
            recycler.layoutManager = LinearLayoutManager(activity)
            activity.setContentView(recycler)
            val adapter = DirectoryAdapter(activity, arrayListOf(directory("/a"), directory("/b")),
                null, recycler, false) {}
            recycler.adapter = adapter
            controller.visible()
            block(adapter, recycler)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    // Commons exposes selection to subclasses, not to callers. Exercise its actual
    // selected-key set without opening a CAB that depends on a host toolbar.
    @Suppress("UNCHECKED_CAST")
    private fun keys(adapter: DirectoryAdapter): MutableSet<Int> {
        var type: Class<*>? = adapter.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == "selectedKeys" }
            if (field != null) {
                field.isAccessible = true
                return field.get(adapter) as MutableSet<Int>
            }
            type = type.superclass
        }
        error("Commons selected-key set missing")
    }

    private fun awaitList(adapter: DirectoryAdapter, expected: List<String>) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (adapter.dirs.map { it.path } != expected && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertEquals(expected, adapter.dirs.map { it.path })
    }

    @Test fun selectionSurvivesRefreshAndOnlyLatestSnapshotAppliesAfterExit() = withAdapter { adapter, _ ->
        val selected = keys(adapter)
        selected.add("/a".hashCode())
        adapter.onActionModeCreated()
        val incoming = arrayListOf(directory("/c"), directory("/a"))
        adapter.updateDirs(incoming)
        incoming[0].path = "/mutated-after-submission"
        adapter.updateDirs(arrayListOf(directory("/d"), directory("/a")))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf("/a".hashCode()), selected)
        assertEquals(listOf("/a", "/b"), adapter.dirs.map { it.path })
        selected.clear()
        adapter.onActionModeDestroyed()
        awaitList(adapter, listOf("/d", "/a"))
    }

    @Test fun retiredRefreshCannotReplaceTheListAfterSelectionEnds() = withAdapter { adapter, _ ->
        val selected = keys(adapter)
        selected.add("/a".hashCode())
        adapter.onActionModeCreated()
        var current = true
        adapter.updateDirs(arrayListOf(directory("/old-request")), isCurrent = { current })
        current = false
        selected.clear()
        adapter.onActionModeDestroyed()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/a", "/b"), adapter.dirs.map { it.path })
    }

    @Test fun submittedDirectoriesAreIndependentOfTheirProducer() = withAdapter { adapter, _ ->
        val incoming = arrayListOf(directory("/c"), directory("/a"))
        adapter.updateDirs(incoming)
        incoming[0].path = "/producer-mutation"
        incoming[1].subfoldersMediaCount = 999
        awaitList(adapter, listOf("/c", "/a"))
        assertEquals(0, adapter.dirs[1].subfoldersMediaCount)
    }

    @Test fun identicalDirectorySnapshotDoesNotQueueDiffOrNotifyRows() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val executor = ManualExecutor()
            val adapter = DirectoryAdapter(activity, arrayListOf(directory("/a"), directory("/b")),
                null, recycler, false, diffExecutor = executor) {}
            recycler.adapter = adapter
            var notifications = 0
            var commits = 0
            adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                override fun onChanged() { notifications++ }
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { notifications++ }
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { notifications++ }
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { notifications++ }
                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { notifications++ }
            })

            adapter.updateDirs(arrayListOf(
                directory("/a").apply { id = 90 },
                directory("/b").apply { id = 91 }
            ), onCommitted = { commits++ })

            assertTrue(executor.tasks.isEmpty())
            assertEquals(0, notifications)
            assertEquals(1, commits)
            assertEquals(90L, adapter.dirs.first().id)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun directoryCountAndCoverChangesStillQueueVisibleUpdate() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val executor = ManualExecutor()
            val adapter = DirectoryAdapter(activity, arrayListOf(directory("/a")), null, recycler, false,
                diffExecutor = executor) {}
            recycler.adapter = adapter

            adapter.updateDirs(arrayListOf(directory("/a").apply { mediaCnt = 3; tmb = "/a/cover.jpg" }))

            assertEquals(1, executor.tasks.size)
            executor.tasks.removeFirst().run()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (adapter.dirs.single().mediaCnt != 3 && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertEquals(3, adapter.dirs.single().mediaCnt)
            assertEquals("/a/cover.jpg", adapter.dirs.single().tmb)
        } finally {
            controller.pause().stop().destroy()
        }
    }
    @Test fun intermediateBatchesKeepOrderAndTheFinalBatchCanSortOnce() = withAdapter { adapter, _ ->
        val incoming = arrayListOf(directory("/b"), directory("/a"), directory("/c"))
        adapter.updateDirs(incoming, preserveOrder = true)
        awaitList(adapter, listOf("/a", "/b", "/c"))
        adapter.updateDirs(incoming)
        awaitList(adapter, listOf("/b", "/a", "/c"))
    }

    @Test fun refreshDuringReorderCannotUndoTheUsersSavedOrder() = withAdapter { adapter, _ ->
        val selected = keys(adapter)
        selected.add("/a".hashCode())
        adapter.onActionModeCreated()
        adapter.actionItemPressed(org.fossify.gallery.R.id.cab_change_order)
        adapter.onRowMoved(0, 1)
        adapter.updateDirs(arrayListOf(directory("/a"), directory("/b"), directory("/c")))
        assertEquals(listOf("/b", "/a"), adapter.dirs.map { it.path })
        selected.clear()
        adapter.onActionModeDestroyed()
        awaitList(adapter, listOf("/b", "/a", "/c"))
    }

    @Test fun refreshWaitsForScrollIdleBeforeChangingTheVisibleList() = withAdapter { adapter, recycler ->
        val scrollState = androidx.recyclerview.widget.RecyclerView::class.java
            .getDeclaredMethod("setScrollState", Int::class.javaPrimitiveType).apply { isAccessible = true }
        scrollState.invoke(recycler, androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING)
        adapter.updateDirs(arrayListOf(directory("/c"), directory("/a"), directory("/b")))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/a", "/b"), adapter.dirs.map { it.path })
        scrollState.invoke(recycler, androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE)
        awaitList(adapter, listOf("/c", "/a", "/b"))
    }

    @Test fun localMutationDiscardsQueuedRefreshInsteadOfResurrectingRemovedAlbum() = withAdapter { adapter, _ ->
        val selected = keys(adapter)
        selected.add("/a".hashCode())
        adapter.onActionModeCreated()
        adapter.updateDirs(arrayListOf(directory("/a"), directory("/b"), directory("/new")))
        adapter.discardPendingUpdates()
        adapter.updateDirs(arrayListOf(directory("/b")))
        selected.clear()
        adapter.onActionModeDestroyed()
        awaitList(adapter, listOf("/b"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/b"), adapter.dirs.map { it.path })
    }

}
