package org.fossify.gallery.helpers

import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.BuildConfig

/** Retains only navigation metadata while the album's thumbnail adapter is discarded. */
internal class FavoritesScrollRestorer {
    data class Anchor(val path: String, val ordinal: Int, val offset: Int, val orientation: Int)

    var pending: Anchor? = null
        private set
    private var removeLayoutCallback: (() -> Unit)? = null

    fun capture(recycler: RecyclerView, paths: List<String?>): Anchor? {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return null
        val axis = OrientationHelper.createOrientationHelper(manager, manager.orientation)
        // Use attached, visible media and the adapter's actual rows (including search results).
        val position = (0 until recycler.childCount).map { recycler.getChildAt(it) }
            .filter { axis.getDecoratedEnd(it) > axis.startAfterPadding &&
                axis.getDecoratedStart(it) < axis.endAfterPadding }
            .map { recycler.getChildAdapterPosition(it) }
            .filter { it >= 0 && paths.getOrNull(it) != null }
            .minOrNull() ?: return null
        val view = manager.findViewByPosition(position) ?: return null
        return Anchor(paths[position]!!, paths.take(position).count { it != null },
            axis.getDecoratedStart(view) - axis.startAfterPadding, manager.orientation)
    }

    fun remember(recycler: RecyclerView, paths: List<String?>) {
        cancelLayout()
        // Never overwrite the pre-viewer anchor with an empty or provisional return layout.
        if (pending == null) pending = capture(recycler, paths)
        if (BuildConfig.DEBUG) Log.d("GalleryFavorites", "Scroll anchor present=${pending != null}, rows=${paths.size}")
    }

    fun cancelLayout() {
        removeLayoutCallback?.invoke()
        removeLayoutCallback = null
    }

    fun clear() {
        cancelLayout()
        pending = null
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

    fun restore(recycler: RecyclerView, paths: List<String?>, finalResult: Boolean,
                isCurrent: () -> Boolean) {
        cancelLayout()
        val anchor = pending ?: return
        if (!isCurrent()) return
        val mediaPositions = paths.indices.filter { paths[it] != null }
        if (mediaPositions.isEmpty()) {
            if (finalResult) pending = null
            return
        }
        val position = paths.indexOf(anchor.path).takeIf { it >= 0 }
            ?: mediaPositions[anchor.ordinal.coerceIn(0, mediaPositions.lastIndex)]
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val adapter = recycler.adapter ?: return
        val offset = if (anchor.orientation == manager.orientation) anchor.offset else 0
        val axis = OrientationHelper.createOrientationHelper(manager, manager.orientation)
        val observer = recycler.viewTreeObserver
        var attempts = 0
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isCurrent() || pending !== anchor || recycler.adapter !== adapter ||
                    recycler.layoutManager !== manager) {
                    cancelLayout()
                    return true
                }
                if (recycler.isLayoutRequested || recycler.isComputingLayout) return true
                val view = manager.findViewByPosition(position)
                val visible = view != null && axis.getDecoratedEnd(view) > axis.startAfterPadding &&
                    axis.getDecoratedStart(view) < axis.endAfterPadding
                val aligned = view != null && kotlin.math.abs(
                    axis.getDecoratedStart(view) - axis.startAfterPadding - offset) <= 1
                // At the dataset ends Android may clamp an otherwise valid requested offset.
                val atBoundary = if (manager.orientation == RecyclerView.HORIZONTAL) {
                    !recycler.canScrollHorizontally(-1) || !recycler.canScrollHorizontally(1)
                } else {
                    !recycler.canScrollVertically(-1) || !recycler.canScrollVertically(1)
                }
                if (visible && (aligned || atBoundary)) {
                    cancelLayout()
                    if (finalResult) pending = null
                    if (BuildConfig.DEBUG) Log.d("GalleryFavorites", "Scroll confirmed: final=$finalResult, position=$position")
                    return true
                }
                if (++attempts >= 2) {
                    cancelLayout()
                    // Keep the anchor for a later successful reload; never silently consume it.
                    Log.w("GalleryFavorites", "Scroll restore not confirmed; anchor retained")
                    return true
                }
                manager.scrollToPositionWithOffset(position, offset)
                return false
            }
        }
        observer.addOnPreDrawListener(listener)
        removeLayoutCallback = {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            else recycler.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        // Set the pending position before the next layout, rather than posting after it.
        manager.scrollToPositionWithOffset(position, offset)
        recycler.requestLayout()
    }
}
