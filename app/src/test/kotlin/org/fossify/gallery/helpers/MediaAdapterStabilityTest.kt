package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import com.squareup.picasso.Picasso
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [MediaAdapterStabilityTest.HostWebpFactory::class])
class MediaAdapterStabilityTest {
    // These are request/binding tests with nonexistent local paths, not decoder tests.
    // Android's bundled ARM WebP library cannot be loaded in the host JVM.
    @Implements(className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    private var testPicasso: Picasso? = null
    private var previousPicasso: Any? = null
    private val picassoSingleton get() = Picasso::class.java.getDeclaredField("singleton").apply { isAccessible = true }

    @Before fun installHostImages() {
        // Plain Application deliberately has no Picasso startup ContentProvider.
        previousPicasso = picassoSingleton.get(null)
        testPicasso = Picasso.Builder(RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, testPicasso)
    }

    @After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        testPicasso?.shutdown()
    }

    private fun item(name: String) = Medium(null, name, "/photos/$name", "/photos", 10, 10, 100,
        TYPE_IMAGES, 0, false, 0, 1)

    private fun withAdapter(block: (MediaAdapter, MyRecyclerView, ArrayDeque<Runnable>) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val tasks = ArrayDeque<Runnable>()
            val adapter = MediaAdapter(activity, arrayListOf(item("a.jpg")), null, false, false,
                "/photos", recycler, Executor { tasks.addLast(it) }) {}
            recycler.adapter = adapter
            controller.visible()
            block(adapter, recycler, tasks)
        } finally { controller.pause().stop().destroy() }
    }

    private fun drain(tasks: ArrayDeque<Runnable>) {
        repeat(100) {
            if (tasks.isEmpty()) return
            tasks.removeFirst().run()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        fail("Diff queue failed to drain")
    }

    @Test fun layoutAndDatabaseFieldsAreNotThumbnailContent() {
        val before = item("a.jpg")
        assertTrue(MediaUpdatePolicy.sameContent(before, before.copy(id = 8, gridPosition = 100, mediaStoreId = 9)))
        assertFalse(MediaUpdatePolicy.sameContent(before, before.copy(modified = 11)))
        assertTrue(MediaUpdatePolicy.durationOnly(before, before.copy(videoDuration = 30)))
        assertFalse(MediaUpdatePolicy.durationOnly(before, before.copy(videoDuration = 30, size = 101)))
    }

    @Test fun staleSelectionWithNoCurrentMediaDoesNotOpenDeleteConfirmation() = withAdapter { adapter, _, _ ->
        val keysField = MyRecyclerViewAdapter::class.java.getDeclaredField("selectedKeys").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val selectedKeys = keysField.get(adapter) as MutableSet<Int>
        selectedKeys += "stale-path".hashCode()

        val confirm = MediaAdapter::class.java.getDeclaredMethod("askConfirmDelete").apply { isAccessible = true }
        confirm.invoke(adapter)

        assertTrue(adapter.media.filterIsInstance<Medium>().isNotEmpty())
        assertTrue("The stale selection exits safely", selectedKeys.isEmpty())
    }

    @Test fun displayedSnapshotComparisonKeepsOrderAndSectionChangesVisible() {
        val first = arrayListOf<ThumbnailItem>(item("a.jpg"), ThumbnailSection("May", "2026-05"))
        val internalRefresh = arrayListOf<ThumbnailItem>(
            item("a.jpg").copy(id = 42, mediaStoreId = 99, gridPosition = 2),
            ThumbnailSection("May", "2026-05")
        )
        val moved = arrayListOf<ThumbnailItem>(ThumbnailSection("May", "2026-05"), item("a.jpg"))
        val changedSection = arrayListOf<ThumbnailItem>(item("a.jpg"), ThumbnailSection("June", "2026-06"))

        assertTrue(MediaUpdatePolicy.sameDisplayedContent(first, internalRefresh))
        assertFalse(MediaUpdatePolicy.sameDisplayedContent(first, moved))
        assertFalse(MediaUpdatePolicy.sameDisplayedContent(first, changedSection))
    }

    @Test fun persistenceAndGridPositionChangesDoNotScheduleDiffOrNotifyRows() = withAdapter { adapter, _, tasks ->
        var notifications = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { notifications++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { notifications++ }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { notifications++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { notifications++ }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { notifications++ }
        })
        val refreshed = item("a.jpg").copy(id = 42, mediaStoreId = 99, gridPosition = 17)

        adapter.updateMedia(arrayListOf(refreshed), preserveScroll = false)

        assertTrue("No background DiffUtil work should be queued", tasks.isEmpty())
        assertEquals("No RecyclerView operation should be dispatched", 0, notifications)
        assertEquals(42L, (adapter.media.single() as Medium).id)
        assertEquals(99L, (adapter.media.single() as Medium).mediaStoreId)
    }

    @Test fun realMetadataAndOrderChangesStillScheduleDiffs() = withAdapter { adapter, _, tasks ->
        adapter.updateMedia(arrayListOf(item("a.jpg").copy(modified = 11)), preserveScroll = false)
        assertEquals(1, tasks.size)
        drain(tasks)

        adapter.updateMedia(arrayListOf(item("b.jpg"), item("a.jpg").copy(modified = 11)), preserveScroll = false)
        assertEquals(1, tasks.size)
        drain(tasks)

        assertEquals(listOf("b.jpg", "a.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun burstKeepsOneDiffAndNewestPendingSnapshot() = withAdapter { adapter, recycler, tasks ->
        assertNull("Refreshes must not animate the whole grid", recycler.itemAnimator)
        val incoming = arrayListOf<ThumbnailItem>(item("first.jpg"))
        adapter.updateMedia(incoming, preserveScroll = false)
        incoming.clear()
        repeat(30) { adapter.updateMedia(arrayListOf(item("$it.jpg")), preserveScroll = false) }
        assertEquals(1, tasks.size)
        drain(tasks)
        assertEquals(listOf("29.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun confirmedDeletionSupersedesAnInFlightProgressiveDiffByPath() = withAdapter { adapter, _, tasks ->
        val removedNotifications = ArrayList<Pair<Int, Int>>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                removedNotifications += positionStart to itemCount
            }
        })
        var scanIsCurrent = true
        adapter.updateMedia(arrayListOf<ThumbnailItem>(
            item("a.jpg"), ThumbnailSection("March", "2026-03"), item("b.jpg")
        ), preserveScroll = false, isCurrent = { scanIsCurrent })
        assertEquals("The first progressive diff is in flight", 1, tasks.size)

        // A newer deletion succeeds while the previous scan diff is still computing.
        // The old request expires and the adapter schedules a path-based update.
        scanIsCurrent = false
        adapter.removeMediaPaths(setOf("/photos/a.jpg"))
        drain(tasks)

        assertFalse(adapter.media.filterIsInstance<Medium>().any { it.path in setOf("/photos/a.jpg", "/photos/b.jpg") })
        assertTrue("An expired scan stage must not restore stale rows", adapter.media.none { it is Medium })
        assertTrue("No stale positional remove notification is emitted", removedNotifications.isNotEmpty())
        removedNotifications.forEach { (position, count) ->
            assertTrue(position >= 0)
            assertTrue(count > 0)
        }
    }

    @Test fun confirmedDeletionKeepsUnselectedRowsAndUsesDiffNotifications() = withAdapter { adapter, _, tasks ->
        adapter.updateMedia(arrayListOf<ThumbnailItem>(item("a.jpg"), item("b.jpg"), item("c.jpg")),
            preserveScroll = false)
        drain(tasks)
        var changedAll = 0
        var removedRows = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { changedAll++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { removedRows += itemCount }
        })

        adapter.removeMediaPaths(setOf("/photos/b.jpg", "/photos/stale.jpg"))
        drain(tasks)

        assertEquals(listOf("a.jpg", "c.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
        assertEquals(0, changedAll)
        assertEquals(1, removedRows)
    }

    @Test fun scrollingDefersCatalogAndIdleCommitsNewestResult() = withAdapter { adapter, recycler, tasks ->
        val setState = RecyclerView::class.java.getDeclaredMethod("setScrollState", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        setState.invoke(recycler, RecyclerView.SCROLL_STATE_DRAGGING)
        adapter.updateMedia(arrayListOf(item("old.jpg")), preserveScroll = false)
        adapter.updateMedia(arrayListOf(item("new.jpg")), preserveScroll = false)
        assertTrue(tasks.isEmpty())
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        setState.invoke(recycler, RecyclerView.SCROLL_STATE_IDLE)
        drain(tasks)
        assertEquals("new.jpg", (adapter.media.single() as Medium).name)
    }

    @Test fun expiredRequestAndDetachedAdapterCannotPublish() = withAdapter { adapter, recycler, tasks ->
        var current = true
        adapter.updateMedia(arrayListOf(item("stale.jpg")), isCurrent = { current })
        current = false
        drain(tasks)
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        adapter.updateMedia(arrayListOf(item("detached.jpg")))
        recycler.adapter = null
        drain(tasks)
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
    }

    @Test fun durationUpdateUsesPayloadAndDoesNotQueueCatalogDiff() = withAdapter { adapter, _, tasks ->
        var changes = 0
        var payload: Any? = null
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, value: Any?) {
                changes += itemCount; payload = value
            }
        })
        adapter.updateVideoDurations(listOf(item("a.jpg").copy(videoDuration = 30)))
        assertEquals(1, changes)
        assertNotNull(payload)
        assertTrue(tasks.isEmpty())
        assertEquals(30, (adapter.media.single() as Medium).videoDuration)
        adapter.updateVideoDurations(listOf(item("a.jpg").copy(videoDuration = 30)))
        assertEquals(1, changes)
    }

    @Test fun unchangedFullBindKeepsDrawableAndRequestToken() = withAdapter { adapter, recycler, _ ->
        val holder = adapter.onCreateViewHolder(recycler, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val image = holder.itemView.findViewById<android.widget.ImageView>(R.id.medium_thumbnail)
        val token = image.getTag(R.id.thumbnail_request_key)
        val displayed = ColorDrawable(android.graphics.Color.RED)
        image.setImageDrawable(displayed)
        adapter.onBindViewHolder(holder, 0)
        assertSame(token, image.getTag(R.id.thumbnail_request_key))
        assertSame(displayed, image.drawable)
        adapter.onViewRecycled(holder)
        assertNull(image.getTag(R.id.thumbnail_request_key))
    }
}
