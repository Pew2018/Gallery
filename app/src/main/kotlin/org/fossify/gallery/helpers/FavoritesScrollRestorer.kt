package org.fossify.gallery.helpers

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.BuildConfig

/** Anchors a grid refresh or viewer return to the last visible media. */
internal class FavoritesScrollRestorer {
    enum class RestoreResult { POSITIONED, NO_ANCHOR, EMPTY, UNCONFIRMED }

    data class Anchor(
        val path: String, val ordinal: Int, val offset: Int, val orientation: Int,
        val neighbors: List<String> = emptyList(),
    )

    var pending: Anchor? = null
        private set
    private var removeLayoutCallback: (() -> Unit)? = null
    private var watchedRecycler: RecyclerView? = null
    private val userScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING && recyclerView === watchedRecycler) {
                // A user's new gesture supersedes any saved position. Do not let a
                // delayed pre-draw callback pull the list back after that gesture.
                clear()
            }
        }
    }

    fun capture(
        recycler: RecyclerView, paths: List<String?>, keepNeighbors: Boolean = false, target: String? = null
    ): Anchor? {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return null
        val axis = OrientationHelper.createOrientationHelper(manager, manager.orientation)
        // Use attached, visible media and the adapter's actual rows (including search results).
        val position = (0 until recycler.childCount).map { recycler.getChildAt(it) }
            .filter { axis.getDecoratedEnd(it) > axis.startAfterPadding &&
                axis.getDecoratedStart(it) < axis.endAfterPadding }
            .map { recycler.getChildAdapterPosition(it) }
            .filter { it >= 0 && paths.getOrNull(it)?.let { path -> target == null || path == target } == true }
            .minOrNull() ?: return null
        val view = manager.findViewByPosition(position) ?: return null
        val preceding = paths.subList(0, position)
        val neighbors = if (keepNeighbors) {
            val after = paths.subList(position + 1, paths.size).asSequence().filterNotNull().take(2).toList()
            val before = preceding.asReversed().asSequence().filterNotNull().take(2).toList()
            after + before
        } else emptyList()
        return Anchor(paths[position]!!, preceding.count { it != null },
            offsetFromLayoutStart(view, axis, fromEnd(recycler, manager)), manager.orientation, neighbors)
    }

    private fun fromEnd(recycler: RecyclerView, manager: LinearLayoutManager): Boolean =
        manager.reverseLayout xor (manager.orientation == RecyclerView.HORIZONTAL &&
            recycler.layoutDirection == View.LAYOUT_DIRECTION_RTL)

    private fun offsetFromLayoutStart(view: View, axis: OrientationHelper, fromEnd: Boolean): Int =
        if (fromEnd) axis.endAfterPadding - axis.getDecoratedEnd(view)
        else axis.getDecoratedStart(view) - axis.startAfterPadding

    fun remember(
        recycler: RecyclerView, paths: List<String?>, keepNeighbors: Boolean = false, allowFling: Boolean = false,
        target: String? = null,
    ) {
        cancelLayout()
        if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE &&
            !(allowFling && recycler.scrollState == RecyclerView.SCROLL_STATE_SETTLING)) {
            clear()
            return
        }
        // Never overwrite the pre-viewer anchor with an empty or provisional return layout.
        if (pending == null || target != null) pending = capture(recycler, paths, keepNeighbors, target)
        watchUserScroll(recycler)
        if (BuildConfig.DEBUG) Log.d("GalleryFavorites", "Scroll anchor present=${pending != null}, rows=${paths.size}")
    }

    private fun watchUserScroll(recycler: RecyclerView) {
        if (watchedRecycler === recycler) return
        watchedRecycler?.removeOnScrollListener(userScrollListener)
        watchedRecycler = recycler
        recycler.addOnScrollListener(userScrollListener)
    }

    private fun stopWatchingUserScroll() {
        watchedRecycler?.removeOnScrollListener(userScrollListener)
        watchedRecycler = null
    }

    fun cancelLayout() {
        removeLayoutCallback?.invoke()
        removeLayoutCallback = null
    }

    fun clear() {
        cancelLayout()
        pending = null
        stopWatchingUserScroll()
    }

    fun save(out: Bundle) {
        pending?.let { anchor ->
            out.putBundle("favorites_scroll_anchor", Bundle().apply {
                putString("path", anchor.path)
                putInt("ordinal", anchor.ordinal)
                putInt("offset", anchor.offset)
                putInt("orientation", anchor.orientation)
            })
        }
    }

    fun read(saved: Bundle?) {
        pending = saved?.getBundle("favorites_scroll_anchor")?.let {
            val path = it.getString("path") ?: return@let null
            Anchor(path, it.getInt("ordinal"), it.getInt("offset"), it.getInt("orientation"))
        }
    }

    private fun Anchor.positionIn(paths: List<String?>, mediaPositions: List<Int>): Int =
        paths.indexOf(path).takeIf { it >= 0 }
            ?: neighbors.firstNotNullOfOrNull { neighbor -> paths.indexOf(neighbor).takeIf { it >= 0 } }
            ?: mediaPositions[ordinal.coerceIn(0, mediaPositions.lastIndex)]

    /** Set the next layout's anchor once. A moving viewport must never get a later pre-draw correction. */
    fun restoreDuringFling(recycler: RecyclerView, paths: List<String?>) {
        val anchor = pending ?: return
        clear()
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val mediaPositions = paths.indices.filter { paths[it] != null }
        if (mediaPositions.isNotEmpty()) {
            val offset = if (anchor.orientation == manager.orientation) anchor.offset else 0
            manager.scrollToPositionWithOffset(anchor.positionIn(paths, mediaPositions), offset)
        }
    }

    fun restore(recycler: RecyclerView, paths: List<String?>, finalResult: Boolean,
                onResult: (RestoreResult) -> Unit = {}, isCurrent: () -> Boolean) {
        cancelLayout()
        if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            clear()
            return
        }
        val anchor = pending
        if (!isCurrent()) return
        val mediaPositions = paths.indices.filter { paths[it] != null }
        if (mediaPositions.isEmpty()) {
            if (finalResult) { clear(); onResult(RestoreResult.EMPTY) }
            return
        }
        val position = anchor?.positionIn(paths, mediaPositions)
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val adapter = recycler.adapter ?: return
        if (anchor != null) watchUserScroll(recycler)
        val offset = if (anchor?.orientation == manager.orientation) anchor.offset else 0
        val axis = OrientationHelper.createOrientationHelper(manager, manager.orientation)
        val observer = recycler.viewTreeObserver
        var attempts = 0
        var positionRequested = false
        fun requestPosition() {
            position?.let {
                manager.scrollToPositionWithOffset(it, offset)
                positionRequested = true
            }
        }
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isCurrent() || pending !== anchor || recycler.adapter !== adapter ||
                    recycler.layoutManager !== manager) {
                    cancelLayout()
                    return true
                }
                if (recycler.isLayoutRequested || recycler.isComputingLayout) return true
                positionRequested = false
                if (anchor == null) {
                    if (recycler.childCount == 0) return true
                    cancelLayout()
                    onResult(RestoreResult.NO_ANCHOR)
                    return true
                }
                val view = position?.let(manager::findViewByPosition)
                val visible = view != null && axis.getDecoratedEnd(view) > axis.startAfterPadding &&
                    axis.getDecoratedStart(view) < axis.endAfterPadding
                val aligned = view != null && kotlin.math.abs(
                    offsetFromLayoutStart(view, axis, fromEnd(recycler, manager)) - offset) <= 1
                // At the dataset ends Android may clamp an otherwise valid requested offset.
                val atBoundary = if (manager.orientation == RecyclerView.HORIZONTAL) {
                    !recycler.canScrollHorizontally(-1) || !recycler.canScrollHorizontally(1)
                } else {
                    !recycler.canScrollVertically(-1) || !recycler.canScrollVertically(1)
                }
                if (visible && (aligned || atBoundary)) {
                    cancelLayout()
                    if (finalResult) {
                        pending = null
                        stopWatchingUserScroll()
                    }
                    onResult(RestoreResult.POSITIONED)
                    if (BuildConfig.DEBUG) Log.d("GalleryFavorites", "Scroll confirmed: final=$finalResult, position=$position")
                    return true
                }
                if (++attempts >= 2) {
                    cancelLayout()
                    // Keep the anchor for a later successful reload; never silently consume it.
                    Log.w("GalleryFavorites", "Scroll restore not confirmed; anchor retained")
                    onResult(RestoreResult.UNCONFIRMED)
                    return true
                }
                requestPosition()
                return false
            }
        }
        observer.addOnPreDrawListener(listener)
        removeLayoutCallback = {
            // Retire LinearLayoutManager's pending offset as well as the callback.
            // A thumb move can occur before the requested layout consumes it.
            if (positionRequested && recycler.layoutManager === manager && recycler.isLayoutRequested) {
                manager.scrollToPosition(RecyclerView.NO_POSITION)
            }
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            else recycler.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        // Set the pending position before the next layout, rather than posting after it.
        requestPosition()
        recycler.requestLayout()
    }
}
