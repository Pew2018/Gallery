package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class FavoritesScrollRestorerTest {
    private lateinit var recycler: RecyclerView
    private lateinit var manager: GridLayoutManager
    private lateinit var restorer: FavoritesScrollRestorer
    private var paths = (0 until 450).map { "/pictures/$it.jpg" as String? }

    private class Rows(private val paths: List<String?>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = paths.size
        override fun getItemViewType(position: Int) = if (paths[position] == null) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = View(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(120, if (viewType == 1) 40 else 120)
            }
            return object : RecyclerView.ViewHolder(view) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        recycler = RecyclerView(activity)
        manager = GridLayoutManager(activity, 3)
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (paths[position] == null) 3 else 1
        }
        recycler.layoutManager = manager
        recycler.itemAnimator = null
        recycler.setPadding(8, 16, 8, 16)
        activity.setContentView(recycler)
        restorer = FavoritesScrollRestorer()
        replace(paths)
        frame()
    }

    private fun replace(rows: List<String?>) {
        paths = rows
        recycler.adapter = Rows(rows)
    }

    private fun frame() {
        val exact = View.MeasureSpec.EXACTLY
        recycler.measure(View.MeasureSpec.makeMeasureSpec(600, exact), View.MeasureSpec.makeMeasureSpec(600, exact))
        recycler.layout(0, 0, 600, 600)
        recycler.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun remember(position: Int = 240): FavoritesScrollRestorer.Anchor {
        manager.scrollToPositionWithOffset(position, -27)
        frame()
        restorer.remember(recycler, paths)
        return requireNotNull(restorer.pending)
    }

    private fun assertRestored(anchor: FavoritesScrollRestorer.Anchor, targetPath: String = anchor.path) {
        val position = paths.indexOf(targetPath)
        val view = manager.findViewByPosition(position)
        assertNotNull("Expected media at $position to be attached", view)
        val axis = OrientationHelper.createOrientationHelper(manager, manager.orientation)
        assertEquals(anchor.offset, axis.getDecoratedStart(view!!) - axis.startAfterPadding)
        assertNull(restorer.pending)
    }

    @Test fun restoresAfterThumbnailAdapterAndAllRowsAreDiscarded() {
        val anchor = remember()
        val savedRows = paths
        recycler.adapter = null
        frame()
        restorer.remember(recycler, emptyList())
        assertEquals(anchor, restorer.pending)
        replace(savedRows)
        restorer.restore(recycler, paths, true) { true }
        assertNotNull(restorer.pending) // Scroll request alone must not consume the anchor.
        frame()
        assertRestored(anchor)
    }

    @Test fun cacheCannotConsumeAnchorAndFinalScanCanMoveItsAdapterPosition() {
        val anchor = remember()
        replace(paths.toList())
        restorer.restore(recycler, paths, false) { true }
        frame()
        assertEquals(anchor, restorer.pending)
        replace(listOf("/new.jpg") + paths)
        restorer.restore(recycler, paths, true) { true }
        frame()
        assertRestored(anchor)
    }

    @Test fun missingOrUnauthorizedAnchorFallsBackByMediaOrdinalPastHeaders() {
        val anchor = remember()
        val media = paths.filterNotNull().filter { it != anchor.path }
        val target = media[anchor.ordinal]
        replace(listOf(null) + media.take(90) + listOf(null) + media.drop(90))
        restorer.restore(recycler, paths, true) { true }
        frame()
        assertRestored(anchor, target)
    }

    @Test fun capturesFilteredAdapterInsteadOfSharedUnfilteredIndices() {
        replace(paths.filterIndexed { index, _ -> index % 2 == 0 })
        val anchor = remember(120)
        assertEquals(paths[120], anchor.path)
        assertEquals(120, anchor.ordinal)
    }

    @Test fun staleRequestCannotConsumeAnchorAfterNewAdapterIsInstalled() {
        val anchor = remember()
        var current = true
        restorer.restore(recycler, paths, true) { current }
        current = false
        replace(paths.toList())
        frame()
        assertEquals(anchor, restorer.pending)
        restorer.restore(recycler, paths, true) { true }
        frame()
        assertRestored(anchor)
    }

    @Test fun lightweightAnchorSurvivesRecreationAndOrientationChange() {
        val anchor = remember()
        val saved = Bundle()
        restorer.save(saved)
        restorer.cancelLayout()
        restorer = FavoritesScrollRestorer().apply { read(saved) }
        assertEquals(anchor, restorer.pending)
        manager.orientation = RecyclerView.HORIZONTAL
        replace(paths.toList())
        restorer.restore(recycler, paths, true) { true }
        frame()
        assertRestored(anchor.copy(offset = 0))
    }

    @Test fun finalEmptyAlbumClearsAnchorButEmptyCacheDoesNot() {
        val anchor = remember()
        replace(emptyList())
        restorer.restore(recycler, paths, false) { true }
        assertEquals(anchor, restorer.pending)
        restorer.restore(recycler, paths, true) { true }
        assertNull(restorer.pending)
    }

    @Test fun userNavigationCancelsPendingLayoutRestoration() {
        remember()
        restorer.restore(recycler, paths, true) { true }
        restorer.clear()
        manager.scrollToPositionWithOffset(0, 0)
        frame()
        assertNull(restorer.pending)
        assertEquals(0, manager.findFirstVisibleItemPosition())
    }
}
