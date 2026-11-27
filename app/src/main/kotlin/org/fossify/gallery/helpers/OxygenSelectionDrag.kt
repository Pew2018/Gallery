package org.fossify.gallery.helpers

import android.view.MotionEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView

/**
 * Long-press drag selection settled as the rectangle from the anchor cell to the
 * current fingertip — not only the cells the finger path touched.
 *
 * Mode is fixed when the gesture starts from the ACTION_DOWN hit: unselected →
 * select the rectangle; already selected → clear the rectangle. Staying inside
 * the same cell does not jitter. Cells that leave a shrinking rectangle restore
 * their pre-gesture state. State is left as-is when the finger lifts.
 */
object OxygenSelectionDrag {
    private var swipeDepth = 0

    fun inSwipe(): Boolean = swipeDepth > 0

    private inline fun duringSwipe(block: () -> Unit) {
        swipeDepth++
        try {
            block()
        } finally {
            swipeDepth--
        }
    }

    fun install(
        recyclerView: MyRecyclerView,
        positionOffset: () -> Int,
        isSelectable: (position: Int) -> Boolean,
        isSelected: (position: Int) -> Boolean,
        toggle: (select: Boolean, position: Int) -> Unit,
    ) {
        val session = Session()
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    val child = rv.findChildViewUnder(e.x, e.y)
                    val raw = child?.let { rv.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                    if (raw == RecyclerView.NO_POSITION) {
                        session.downAdjusted = -1
                        session.downWasSelected = false
                    } else {
                        val adjusted = (raw - positionOffset()).coerceAtLeast(0)
                        session.downAdjusted = adjusted
                        session.downWasSelected = isSelectable(adjusted) && isSelected(adjusted)
                    }
                }
                return false
            }
        })
        recyclerView.setupDragListener(object : MyRecyclerView.MyDragListener {
            override fun selectItem(position: Int) {
                begin(session, position, isSelectable, isSelected, toggle, recyclerView, positionOffset)
            }

            override fun selectRange(
                initialSelection: Int,
                lastDraggedIndex: Int,
                minReached: Int,
                maxReached: Int,
            ) {
                if (!session.active) {
                    begin(
                        session,
                        initialSelection.coerceAtLeast(0),
                        isSelectable,
                        isSelected,
                        toggle,
                        recyclerView,
                        positionOffset,
                    )
                }
                val current = (lastDraggedIndex - positionOffset()).coerceAtLeast(0)
                if (current == session.lastCurrent) return
                duringSwipe {
                    settleRect(session, current, isSelectable, isSelected, toggle, recyclerView, positionOffset)
                }
                session.lastCurrent = current
            }
        })
    }

    private fun begin(
        session: Session,
        position: Int,
        isSelectable: (Int) -> Boolean,
        isSelected: (Int) -> Boolean,
        toggle: (Boolean, Int) -> Unit,
        recyclerView: MyRecyclerView,
        positionOffset: () -> Int,
    ) {
        session.active = true
        session.anchor = position
        session.lastCurrent = position
        // Prefer the ACTION_DOWN snapshot so Commons' force-select does not flip mode.
        val wasSelected = if (session.downAdjusted == position) session.downWasSelected
        else isSelected(position)
        session.selecting = !wasSelected
        session.baseline.clear()
        session.applied = emptySet()
        duringSwipe {
            settleRect(session, position, isSelectable, isSelected, toggle, recyclerView, positionOffset)
        }
    }

    private fun settleRect(
        session: Session,
        current: Int,
        isSelectable: (Int) -> Boolean,
        isSelected: (Int) -> Boolean,
        toggle: (Boolean, Int) -> Unit,
        recyclerView: MyRecyclerView,
        positionOffset: () -> Int,
    ) {
        val next = rectPositions(
            anchor = session.anchor,
            current = current,
            recyclerView = recyclerView,
            positionOffset = positionOffset(),
            isSelectable = isSelectable,
        )
        for (left in session.applied - next) {
            val was = session.baseline[left] ?: continue
            if (isSelected(left) != was) toggle(was, left)
        }
        for (pos in next) {
            if (pos !in session.baseline) {
                session.baseline[pos] = when {
                    pos == session.anchor && session.downAdjusted == session.anchor ->
                        session.downWasSelected
                    pos == session.anchor -> !session.selecting
                    else -> isSelected(pos)
                }
            }
            val want = session.selecting
            if (isSelected(pos) != want) toggle(want, pos)
        }
        session.applied = next
    }

    internal fun rectPositions(
        anchor: Int,
        current: Int,
        recyclerView: MyRecyclerView,
        positionOffset: Int,
        isSelectable: (Int) -> Boolean,
    ): Set<Int> {
        val adapter = recyclerView.adapter ?: return linkedSetOf(anchor, current).filter(isSelectable).toSet()
        val itemCount = (adapter.itemCount - positionOffset).coerceAtLeast(0)
        if (itemCount <= 0) return emptySet()
        val lm = recyclerView.layoutManager as? GridLayoutManager
        val spanCount = lm?.spanCount?.coerceAtLeast(1) ?: 1
        val lookup = lm?.spanSizeLookup
        fun row(adjusted: Int): Int {
            val raw = adjusted + positionOffset
            return lookup?.getSpanGroupIndex(raw, spanCount) ?: adjusted
        }
        fun col(adjusted: Int): Int {
            val raw = adjusted + positionOffset
            return lookup?.getSpanIndex(raw, spanCount) ?: 0
        }
        val minRow = minOf(row(anchor), row(current))
        val maxRow = maxOf(row(anchor), row(current))
        val minCol = minOf(col(anchor), col(current))
        val maxCol = maxOf(col(anchor), col(current))
        val result = linkedSetOf<Int>()
        for (pos in 0 until itemCount) {
            if (!isSelectable(pos)) continue
            val r = row(pos)
            val c = col(pos)
            if (r in minRow..maxRow && c in minCol..maxCol) result.add(pos)
        }
        return result
    }

    /** Pure geometry helper for unit tests (flat grid, every cell selectable). */
    internal fun rectPositions(
        anchor: Int,
        current: Int,
        spanCount: Int,
        itemCount: Int,
    ): Set<Int> {
        val spans = spanCount.coerceAtLeast(1)
        val aRow = anchor / spans
        val aCol = anchor % spans
        val cRow = current / spans
        val cCol = current % spans
        val minRow = minOf(aRow, cRow)
        val maxRow = maxOf(aRow, cRow)
        val minCol = minOf(aCol, cCol)
        val maxCol = maxOf(aCol, cCol)
        val result = linkedSetOf<Int>()
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                val pos = row * spans + col
                if (pos in 0 until itemCount) result.add(pos)
            }
        }
        return result
    }

    private class Session {
        var active = false
        var anchor = -1
        var lastCurrent = -1
        var selecting = true
        var downAdjusted = -1
        var downWasSelected = false
        val baseline = mutableMapOf<Int, Boolean>()
        var applied: Set<Int> = emptySet()
    }
}
