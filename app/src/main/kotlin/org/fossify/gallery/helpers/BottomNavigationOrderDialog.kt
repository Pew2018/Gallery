package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import java.util.Collections

internal fun moveNavigationItem(items: MutableList<String>, from: Int, to: Int): Boolean {
    if (from !in items.indices || to !in items.indices) return false
    Collections.swap(items, from, to)
    return true
}

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
            setPadding(0, dp(8), 0, dp(8))
            addItemDecoration(ClassicReorderDecoration(
                androidx.core.graphics.ColorUtils.setAlphaComponent(colors.text, 28), dp(24)))
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
                    setPadding(dp(24), 0, dp(16), 0)
                    minimumHeight = dp(56)
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    background = OxygenClassicDialog.ripple(activity.getProperPrimaryColor(), density)
                }
                val handle = ImageView(activity).apply {
                    setImageResource(R.drawable.oxygen_classic_drag_handle)
                    imageTintList = ColorStateList.valueOf(
                        colors.secondary)
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = activity.getString(R.string.navigation_order_reorder)
                    isClickable = true
                    isFocusable = true
                    background = OxygenClassicDialog.ripple(activity.getProperPrimaryColor(), density)
                }
                val title = TextView(activity).apply {
                    textSize = 16f
                    setTextColor(colors.text)
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(56)
                    setPadding(0, dp(8), dp(8), dp(8))
                }
                wrapper.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
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
                    if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                    touchHelper.startDrag(holder)
                    true
                }
                holder.handle.setOnClickListener { anchor ->
                    val boundPosition = holder.bindingAdapterPosition
                    if (boundPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                    val boundItem = pending.getOrNull(boundPosition) ?: return@setOnClickListener
                    val popup = OxygenClassicMenus.createPopup(activity, anchor)
                    if (boundPosition > 0) popup.menu.add(0, 0, 0, R.string.navigation_order_move_up)
                    if (boundPosition < pending.lastIndex) popup.menu.add(0, 1, 1, R.string.navigation_order_move_down)
                    popup.setOnMenuItemClickListener { item ->
                        val from = holder.bindingAdapterPosition
                        if (from == RecyclerView.NO_POSITION || pending.getOrNull(from) != boundItem) return@setOnMenuItemClickListener true
                        val to = when (item.itemId) { 0 -> from - 1; else -> from + 1 }
                        if (from in pending.indices && to in pending.indices && pending[from] == boundItem && moveNavigationItem(pending, from, to)) {
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
                if (!moveNavigationItem(pending, from, to)) return false
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(holder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) holder?.itemView?.isPressed = true
            }
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                holder.itemView.isPressed = false
            }
            override fun isLongPressDragEnabled() = false
        }).also { it.attachToRecyclerView(list) }

        val content = LinearLayout(activity).apply {
            id = R.id.classic_dialog_navigation_content
            orientation = LinearLayout.VERTICAL
        }
        val reset = TextView(activity).apply {
            setText(R.string.navigation_order_restore_default)
            textSize = 14f
            setTextColor(colors.accent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            minHeight = dp(48)
            background = OxygenClassicDialog.ripple(colors.accent, density)
            setOnClickListener {
                pending.clear()
                pending.addAll(defaults)
                // The legacy navigation order may have two entries. Restore all
                // three without clipping the newly added Photos row.
                val params = list.layoutParams
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                list.layoutParams = params
                adapter.notifyDataSetChanged()
            }
        }
        // Natural row heights can grow with font scale; only the middle scrolls.
        val scroll = ClassicDialogScrollView(activity).apply {
            addView(list, ViewGroup.LayoutParams(-1, -2))
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2))
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
