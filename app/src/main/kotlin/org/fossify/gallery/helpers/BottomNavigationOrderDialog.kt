package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import java.util.Collections

/** A classic list dialog. Changes are transactional until Done is pressed. */
object BottomNavigationOrderDialog {
    private val defaults = listOf("ALL", "FAVORITE_FOLDERS", "PRIVATE")
    private val labels = mapOf("ALL" to "所有图片", "FAVORITE_FOLDERS" to "收藏", "PRIVATE" to "隐私")

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val pending = activity.config.bottomNavigationOrder.toMutableList()
        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()

        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
        }
        class RowHolder(val row: LinearLayout, val label: TextView) : RecyclerView.ViewHolder(row)
        val adapter = object : RecyclerView.Adapter<RowHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
                val wrapper = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    layoutParams = RecyclerView.LayoutParams(-1, dp(56))
                }
                val handle = ImageView(activity).apply {
                    setImageResource(R.drawable.oxygen_classic_drag_handle)
                    imageTintList = ColorStateList.valueOf(activity.getProperTextColor())
                    contentDescription = "拖动调整顺序"
                }
                val title = TextView(activity).apply {
                    textSize = 16f
                    setTextColor(activity.getProperTextColor())
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, 0, 0)
                }
                wrapper.addView(handle, LinearLayout.LayoutParams(dp(24), dp(24)))
                wrapper.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
                return RowHolder(wrapper, title)
            }
            override fun getItemCount() = pending.size
            override fun onBindViewHolder(holder: RowHolder, position: Int) {
                val name = labels[pending[position]].orEmpty()
                holder.label.text = name
                holder.row.contentDescription = "${name}，长按并拖动调整顺序"
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
                Collections.swap(pending, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = true
        }).attachToRecyclerView(list)

        val builder = OxygenClassicDialog.builder(activity, activity.getString(R.string.oxygen_bottom_navigation_order))
            .setView(list)
            .setNegativeButton("取消", null)
            .setPositiveButton("完成") { _, _ ->
                activity.config.bottomNavigationOrder = pending
            }
            .setNeutralButton("恢复默认", null)
        val dialog = OxygenClassicDialog.show(activity, builder, list)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            pending.clear()
            pending.addAll(defaults)
            adapter.notifyDataSetChanged()
        }
    }
}
