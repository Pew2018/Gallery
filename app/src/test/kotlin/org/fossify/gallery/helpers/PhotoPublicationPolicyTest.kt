package org.fossify.gallery.helpers

import android.app.Application
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLooper
import com.squareup.picasso.Picasso
import java.util.concurrent.Executor

/**
 * Phase 1.1 list publication.
 *
 * The device export shows only three genuine FILESYSTEM publications per load, each
 * costing a 133-278 ms *background* diff, because a growing, date-descending catalog
 * is not a prefix append. These tests pin the properties that matter for correctness:
 * intermediate snapshots may be merged, but the newest one always wins and no stale
 * diff may ever reach the adapter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    shadows = [PhotoPublicationPolicyTest.HostWebpFactory::class])
class PhotoPublicationPolicyTest {
    // Binding tests use nonexistent local paths; the bundled ARM WebP decoder cannot
    // load in a host JVM.
    @Implements(className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    private var testPicasso: Picasso? = null
    private var previousPicasso: Any? = null
    private val picassoSingleton
        get() = Picasso::class.java.getDeclaredField("singleton").apply { isAccessible = true }

    @org.junit.Before fun installHostImages() {
        // A plain Application has no Picasso startup ContentProvider.
        previousPicasso = picassoSingleton.get(null)
        testPicasso = Picasso.Builder(RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, testPicasso)
    }

    @org.junit.After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        testPicasso?.shutdown()
    }

    private fun item(index: Int) = Medium(
        index.toLong() + 1, "photo-$index.jpg", "/photos/photo-$index.jpg", "/photos",
        1_000L - index, 1_000L - index, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    /** Newest rows first, exactly like a date-descending scan. */
    private fun descending(count: Int, from: Int = 0): ArrayList<ThumbnailItem> =
        (from until from + count).map { item(it) }.toCollection(ArrayList())

    /** Adds fresher rows at the head, which is what the filesystem stage really does. */
    private fun prepend(existing: List<ThumbnailItem>, count: Int): ArrayList<ThumbnailItem> {
        val fresh = (0 until count).map { item(10_000 + it) }
        return (fresh + existing).toCollection(ArrayList())
    }

    // C01: the first published stage is never delayed by the progressive budget.
    @Test fun firstStageIsAdmittedImmediately() {
        val budget = ProgressivePublicationBudget()
        assertTrue(budget.admit(1_000L, version = 1L))
        assertFalse("an unchanged version is a no-op", budget.admit(1_010L, version = 1L))
    }

    // C02: identical visible content is a NO_OP, never a diff.
    @Test fun identicalVisibleContentIsANoOp() {
        val rows = descending(50) as List<ThumbnailItem>
        assertTrue(MediaUpdatePolicy.sameDisplayedContent(rows, rows))
        val changed = rows.map { (it as Medium).copy(videoDuration = 5) }
        assertFalse("a real content change must not be skipped", MediaUpdatePolicy.sameDisplayedContent(rows, changed))
    }

    // C03: an unchanged prefix plus a longer tail is a safe append.
    @Test fun realTailAppendIsDetected() {
        val before = descending(40) as List<ThumbnailItem>
        val after = (before + descending(10, from = 40)).toCollection(ArrayList<ThumbnailItem>())
        assertTrue(MediaUpdatePolicy.isContentAppend(before, after))
    }

    // C04: a date-descending head insertion is never misreported as an append.
    @Test fun descendingHeadInsertionIsNotAnAppend() {
        val before = descending(40, from = 40) as List<ThumbnailItem>
        val after = descending(50) as List<ThumbnailItem>
        assertFalse(MediaUpdatePolicy.isContentAppend(before, after))
    }

    // C03 at adapter level: a growing prefix is committed with no background diff.
    @Test fun tailAppendCommitsWithoutADiff() {
        harness { adapter, tasks, counters ->
            adapter.updateMedia(descending(40), preserveScroll = true)
            ShadowLooper.idleMainLooper()
            assertEquals("an append from empty needs no diff", 0, counters.diffRequests)
            assertEquals(40, adapter.media.size)

            val appended = (adapter.media + descending(10, from = 900)).toCollection(ArrayList<ThumbnailItem>())
            adapter.updateMedia(appended, preserveScroll = true)
            ShadowLooper.idleMainLooper()
            assertEquals(0, counters.diffRequests)
            assertEquals(paths(appended), paths(adapter.media))
        }
    }

    // C05/C06: three snapshots during one in-flight diff produce two diffs, and the
    // newest snapshot always wins. A superseded diff is never committed.
    @Test fun intermediateSnapshotsAreMergedAndStaleDiffsNeverCommit() {
        harness { adapter, tasks, counters ->
            // Base list, committed through the append fast path.
            adapter.updateMedia(descending(200), preserveScroll = true)
            ShadowLooper.idleMainLooper()
            assertEquals(200, adapter.media.size)
            val base = paths(adapter.media)

            val second = prepend(adapter.media, 50)
            val third = prepend(adapter.media, 120)
            val finalList = prepend(adapter.media, 300)

            adapter.updateMedia(second, preserveScroll = true)
            assertEquals("exactly one diff may be in flight", 1, tasks.size)
            adapter.updateMedia(third, preserveScroll = true)
            adapter.updateMedia(finalList, preserveScroll = true)
            assertEquals("later snapshots are merged, not queued", 1, tasks.size)

            // Finish the first diff. It is already superseded, so nothing may commit.
            tasks.removeFirst().run()
            ShadowLooper.idleMainLooper()
            assertEquals("the base list must survive a dropped diff", base, paths(adapter.media))

            // The surviving snapshot is diffed and committed.
            assertEquals("the newest snapshot must be diffed next", 1, tasks.size)
            tasks.removeFirst().run()
            ShadowLooper.idleMainLooper()
            assertEquals("only the newest snapshot may be applied", paths(finalList),
                paths(adapter.media))
            assertEquals("two diffs for three submissions", 2, counters.diffRequests)
        }
    }

    // C05: a snapshot submitted while a diff runs is not lost when the diff completes.
    @Test fun oneSnapshotDuringADiffIsStillCommitted() {
        harness { adapter, tasks, counters ->
            adapter.updateMedia(descending(200), preserveScroll = true)
            ShadowLooper.idleMainLooper()
            adapter.updateMedia(prepend(adapter.media, 20), preserveScroll = true)
            val late = prepend(adapter.media, 60)
            adapter.updateMedia(late, preserveScroll = true)
            assertEquals(1, tasks.size)
            tasks.removeFirst().run()
            ShadowLooper.idleMainLooper()
            tasks.removeFirst().run()
            ShadowLooper.idleMainLooper()
            assertEquals(paths(late), paths(adapter.media))
            assertEquals(2, counters.diffRequests)
        }
    }

    // C09: a final snapshot repeating the visible content still acknowledges its request.
    @Test fun repeatingTheVisibleContentStillCompletesThroughTheCallback() {
        harness { adapter, _, counters ->
            adapter.updateMedia(descending(30), preserveScroll = true)
            ShadowLooper.idleMainLooper()
            var committed = false
            adapter.updateMedia(descending(30), preserveScroll = true, onCommitted = { committed = true })
            assertTrue("an identical snapshot must still acknowledge its request", committed)
            assertEquals(0, counters.diffRequests)
            assertEquals(30, adapter.media.size)
        }
    }

    // C10: a submission that is no longer current is dropped without touching the list.
    @Test fun aStaleSubmissionNeverChangesTheList() {
        harness { adapter, tasks, counters ->
            adapter.updateMedia(descending(60), preserveScroll = true)
            ShadowLooper.idleMainLooper()
            val before = paths(adapter.media)
            adapter.updateMedia(prepend(adapter.media, 30), preserveScroll = true, isCurrent = { false })
            assertEquals("a stale submission must not start work", 0, tasks.size)
            assertEquals(before, paths(adapter.media))
            assertEquals(0, counters.diffRequests)
        }
    }

    private fun paths(items: List<ThumbnailItem>) = items.map { (it as Medium).path }

    private class Counters {
        var diffRequests = 0
        var appends = 0
        var inserts = 0
    }

    private fun harness(block: (MediaAdapter, ArrayDeque<Runnable>, Counters) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val tasks = ArrayDeque<Runnable>()
            val counters = Counters()
            val adapter = MediaAdapter(activity, arrayListOf(), null, false, false, "/photos", recycler,
                Executor { tasks.addLast(it); counters.diffRequests++ }) {}
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() { counters.appends++ }
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { counters.inserts++ }
            })
            recycler.adapter = adapter
            controller.visible()
            block(adapter, tasks, counters)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
