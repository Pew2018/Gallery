package org.fossify.gallery.helpers

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/** Keeps background folder-list refreshes from animating every card into a new slot. */
class DirectoryGridItemAnimator : DefaultItemAnimator() {
    private val userReorderMoveIds = HashSet<Long>()

    init {
        supportsChangeAnimations = false
    }

    fun allowUserReorderMoves(itemIds: Collection<Long>) {
        userReorderMoveIds.addAll(itemIds)
    }

    fun clearUserReorderMoves() {
        userReorderMoveIds.clear()
    }

    internal fun consumeUserReorderMove(itemId: Long) = userReorderMoveIds.remove(itemId)

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        if (!consumeUserReorderMove(holder.itemId)) {
            holder.itemView.translationX = 0f
            holder.itemView.translationY = 0f
            dispatchMoveFinished(holder)
            return false
        }
        return super.animateMove(holder, fromX, fromY, toX, toY)
    }
}
