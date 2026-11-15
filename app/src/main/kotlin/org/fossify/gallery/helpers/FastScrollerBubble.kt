package org.fossify.gallery.helpers

import android.graphics.Rect
import android.view.View
import android.widget.TextView
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/**
 * Resolves a popup position against the adapter's current data. A section represents the first
 * following medium; a trailing/empty section falls back to the nearest preceding medium.
 */
internal object FastScrollerBubble {
    fun show(popup: View) {
        popup.animate().cancel()
        popup.scaleX = 1f
        popup.scaleY = 1f
        popup.alpha = 1f
        popup.visibility = View.VISIBLE
    }

    fun hide(popup: View) {
        popup.animate().cancel()
        popup.visibility = View.INVISIBLE
    }

    fun position(popup: TextView, thumb: View, scroller: View, vertical: Boolean) {
        val bounds = Rect()
        if (!scroller.getLocalVisibleRect(bounds)) return
        val origin = IntArray(2)
        val handle = IntArray(2)
        scroller.getLocationOnScreen(origin)
        thumb.getLocationOnScreen(handle)
        val x = handle[0] - origin[0]
        val y = handle[1] - origin[1]
        val gap = 4f * scroller.resources.displayMetrics.density
        val leftSpace = x - gap - bounds.left
        val rightSpace = bounds.right - x - thumb.width - gap
        val placeOnLeft = leftSpace >= gap || leftSpace >= rightSpace
        val maxWidth = (if (!vertical) bounds.width() else if (placeOnLeft) leftSpace.toInt() else rightSpace.toInt())
            .coerceAtLeast(1)
        if (popup.maxWidth != maxWidth) popup.maxWidth = maxWidth
        val left = when {
            !vertical -> x + (thumb.width - popup.width) / 2f
            placeOnLeft -> x - popup.width - gap
            else -> x + thumb.width + gap
        }
        val top = if (vertical) y + (thumb.height - popup.height) / 2f else y - popup.height - gap
        popup.x = left.coerceIn(bounds.left.toFloat(), (bounds.right - popup.width).coerceAtLeast(bounds.left).toFloat())
        popup.y = top.coerceIn(bounds.top.toFloat(), (bounds.bottom - popup.height).coerceAtLeast(bounds.top).toFloat())
    }

    fun findMediaIndex(items: List<ThumbnailItem>, position: Int): Int? {
        if (position !in items.indices) return null
        if (items[position] is Medium) return position

        for (index in position + 1 until items.size) {
            if (items[index] is Medium) return index
        }
        for (index in position - 1 downTo 0) {
            if (items[index] is Medium) return index
        }
        return null
    }

    fun dateOrFallback(timestamp: Long, fallback: String, format: () -> String): String {
        if (timestamp == 0L) return fallback
        return runCatching(format).getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun displayName(name: String, path: String, unknown: String): String =
        name.takeIf { it.isNotBlank() }
            ?: path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: unknown
}
