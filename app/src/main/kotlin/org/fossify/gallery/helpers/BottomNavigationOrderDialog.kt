package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.extensions.config
import org.fossify.commons.extensions.getProperTextColor
import java.util.Collections

/** Long-press and drag a row; persist immutable tab IDs on each successful move. */
object BottomNavigationOrderDialog {
    private val labels = mapOf("ALL" to "所有图片", "FAVORITE_FOLDERS" to "收藏", "PRIVATE" to "隐私")
    fun show(context: Context) {
        val items = context.config.bottomNavigationOrder.toMutableList()
        val density = context.resources.displayMetrics.density
        fun dp(x: Int) = (x * density + 0.5f).toInt()
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val label = TextView(parent.context).apply {
                    textSize = 16f
                    setTextColor(context.getProperTextColor())
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    isLongClickable = true
                }
                return object : RecyclerView.ViewHolder(label) {}
            }
            override fun getItemCount() = items.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = "☰    " + labels[items[position]]
                holder.itemView.contentDescription = labels[items[position]] + "，长按并拖动调整顺序"
                holder.itemView.layoutParams = RecyclerView.LayoutParams(-1, dp(56))
            }
        }
        list.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                context.config.bottomNavigationOrder = items
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = true
        }).attachToRecyclerView(list)

        AlertDialog.Builder(context)
            .setTitle("底部导航栏顺序")
            .setView(list)
            .setNeutralButton("恢复默认") { _, _ ->
                context.config.bottomNavigationOrder = listOf("ALL", "FAVORITE_FOLDERS", "PRIVATE")
            }
            .setPositiveButton("完成", null)
            .show()
    }
}
