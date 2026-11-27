package org.fossify.gallery.helpers

import org.fossify.commons.views.MyRecyclerView

/**
 * Long-press drag selection that toggles on cell enter.
 * Sliding onto an unselected cell selects it; sliding onto a selected cell clears it.
 * Staying inside the same cell does not flip again — leave and re-enter to reverse.
 * State is left as-is when the finger lifts.
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
        recyclerView.setupDragListener(object : MyRecyclerView.MyDragListener {
            override fun selectItem(position: Int) {
                session.lastEntered = position
                // Long-press already selected this cell before drag starts.
                if (isSelectable(position) && !isSelected(position)) {
                    duringSwipe { toggle(true, position) }
                }
            }

            override fun selectRange(
                initialSelection: Int,
                lastDraggedIndex: Int,
                minReached: Int,
                maxReached: Int,
            ) {
                val entered = (lastDraggedIndex - positionOffset()).coerceAtLeast(0)
                // Finger still inside the same cell: do not jitter-toggle.
                if (entered == session.lastEntered) return
                duringSwipe {
                    settleEnter(entered, isSelectable, isSelected, toggle)
                }
                session.lastEntered = entered
            }
        })
    }

    private fun settleEnter(
        position: Int,
        isSelectable: (Int) -> Boolean,
        isSelected: (Int) -> Boolean,
        toggle: (Boolean, Int) -> Unit,
    ) {
        if (!isSelectable(position)) return
        toggle(!isSelected(position), position)
    }

    private class Session {
        var lastEntered = -1
    }
}
