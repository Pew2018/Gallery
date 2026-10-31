package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import java.util.Collections

/** A classic list dialog. Changes are transactional until Done is pressed. */
object BottomNavigationOrderDialog {
    private val defaults = NavigationOrder.defaults

    fun show(context: Context) {
        val activity = context as? Activity ?: return
        val pending = activity.config.bottomNavigationOrder.toMutableList()
        val colors = photoDialogColors(activity)
        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()

        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
        }
        class RowHolder(val row: LinearLayout, val label: TextView, val handle: ImageView) : RecyclerView.ViewHolder(row)
        lateinit var adapter: RecyclerView.Adapter<RowHolder>
        lateinit var touchHelper: ItemTouchHelper
        adapter = object : RecyclerView.Adapter<RowHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
                val wrapper = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    layoutParams = RecyclerView.LayoutParams(-1, dp(56))
                    background = OxygenClassicDialog.ripple(activity.getProperPrimaryColor(), density)
                }
                val handle = ImageView(activity).apply {
                    setImageResource(R.drawable.oxygen_classic_drag_handle)
                    imageTintList = ColorStateList.valueOf(colors.text)
                    contentDescription = activity.getString(R.string.navigation_order_reorder)
                    isClickable = true
                    isFocusable = true
                    background = OxygenClassicDialog.ripple(activity.getProperPrimaryColor(), density)
                }
                val title = TextView(activity).apply {
                    textSize = 16f
                    setTextColor(colors.text)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, 0, 0)
                }
                wrapper.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
                wrapper.addView(handle, LinearLayout.LayoutParams(dp(48), dp(48)))
                return RowHolder(wrapper, title, handle)
            }
            override fun getItemCount() = pending.size
            override fun onBindViewHolder(holder: RowHolder, position: Int) {
                val name = activity.getString(when (pending[position]) {
                    "ALL" -> R.string.gallery_nav_albums
                    "PHOTOS" -> R.string.gallery_nav_photos
                    else -> org.fossify.commons.R.string.favorites
                })
                holder.label.text = name
                holder.row.contentDescription = name
                holder.handle.setOnLongClickListener {
                    touchHelper.startDrag(holder)
                    true
                }
                holder.handle.setOnClickListener { anchor ->
                    val popup = PopupMenu(activity, anchor)
                    if (position > 0) popup.menu.add(0, 0, 0, R.string.navigation_order_move_up)
                    if (position < pending.lastIndex) popup.menu.add(0, 1, 1, R.string.navigation_order_move_down)
                    popup.setOnMenuItemClickListener { item ->
                        val from = holder.bindingAdapterPosition
                        val to = when (item.itemId) { 0 -> from - 1; else -> from + 1 }
                        if (from in pending.indices && to in pending.indices) {
                            Collections.swap(pending, from, to)
                            adapter.notifyItemMoved(from, to)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
        list.adapter = adapter
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
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
            override fun isLongPressDragEnabled() = false
        }).also { it.attachToRecyclerView(list) }

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val reset = TextView(activity).apply {
            setText(R.string.navigation_order_restore_default)
            textSize = 14f
            setTextColor(colors.accent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            minHeight = dp(48)
            background = OxygenClassicDialog.ripple(colors.accent, density)
            setOnClickListener { pending.clear(); pending.addAll(defaults); adapter.notifyDataSetChanged() }
        }
        content.addView(list)
        content.addView(reset)
        val builder = OxygenClassicDialog.builder(activity, activity.getString(R.string.oxygen_bottom_navigation_order))
            .setView(content)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.photo_profiles_save) { _, _ ->
                activity.config.bottomNavigationOrder = pending
                GalleryBottomBar.refreshAll()
            }
        OxygenClassicDialog.show(activity, builder, content)
    }
}
