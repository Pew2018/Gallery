package org.fossify.gallery.helpers

import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView

/**
 * Long-press drag selection is the closed reading-order span from the anchor
 * cell to the cell under the finger. Every selectable index in between is
 * included, including cells the finger never touched.
 *
 * Mode is fixed when the gesture starts from the ACTION_DOWN hit: unselected →
 * select the span; already selected → clear the span. Staying inside the same
 * cell does not jitter. Cells that leave a shrinking span restore their
 * pre-gesture state. State is left as-is when the finger lifts.
 */
object OxygenSelectionDrag {
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
                settleSpan(session, current, isSelectable, isSelected, toggle, recyclerView, positionOffset)
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
        settleSpan(session, position, isSelectable, isSelected, toggle, recyclerView, positionOffset)
    }

    private fun settleSpan(
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
        val adapter = recyclerView.adapter
            ?: return linkedSetOf(anchor, current).filter { it >= 0 }.filter(isSelectable).toSet()
        val itemCount = (adapter.itemCount - positionOffset).coerceAtLeast(0)
        return rectPositions(anchor, current, itemCount, isSelectable)
    }

    /**
     * Closed index span from the anchor to the current cell. Column geometry is
     * ignored: a finger that stays in one column still covers the other column.
     */
    internal fun rectPositions(
        anchor: Int,
        current: Int,
        itemCount: Int,
        isSelectable: (Int) -> Boolean = { true },
    ): Set<Int> {
        if (itemCount <= 0) return emptySet()
        val start = minOf(anchor, current)
        val end = maxOf(anchor, current)
        val result = linkedSetOf<Int>()
        for (pos in 0 until itemCount) {
            if (pos in start..end && isSelectable(pos)) result.add(pos)
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
