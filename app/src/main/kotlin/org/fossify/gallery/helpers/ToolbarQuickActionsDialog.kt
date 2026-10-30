package org.fossify.gallery.helpers

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import java.util.Collections

/** One classic dialog; home and folder layouts are edited transactionally. */
object ToolbarQuickActionsDialog {
    fun show(activity: Activity) {
        val palette = OxygenThemeEngine.snapshot(activity, activity.config)
        val home = activity.config.toolbarHomeActions.toMutableList()
        val folder = activity.config.toolbarFolderActions.toMutableList()
        var scope = ToolbarQuickScope.HOME
        fun current(): MutableList<String> = if (scope == ToolbarQuickScope.HOME) home else folder
        fun entries(): List<String> = current() + ToolbarQuickActions.options(scope)
            .filterNot { it in current() }
        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val homeTab = TextView(activity)
        val folderTab = TextView(activity)
        val count = TextView(activity).apply {
            textSize = 13f
            setTextColor(palette.secondaryText)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            itemAnimator = null
        }
        class Holder(row: LinearLayout, val check: CheckBox, val drag: ImageView) :
            RecyclerView.ViewHolder(row)

        lateinit var adapter: RecyclerView.Adapter<Holder>
        fun render() {
            val selected = current().size
            count.text = activity.getString(R.string.quick_toolbar_count, selected)
            homeTab.typeface = if (scope == ToolbarQuickScope.HOME) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            folderTab.typeface = if (scope == ToolbarQuickScope.FOLDER) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            adapter.notifyDataSetChanged()
        }

        adapter = object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount(): Int = ToolbarQuickActions.options(scope).size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val row = LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(8), 0, dp(8), 0)
                    layoutParams = RecyclerView.LayoutParams(-1, dp(56))
                }
                val check = CheckBox(activity).apply {
                    textSize = 16f
                    minHeight = dp(56)
                    setTextColor(palette.primaryText)
                    buttonDrawable = AppCompatResources.getDrawable(
                        activity, androidx.appcompat.R.drawable.abc_btn_check_material
                    )
                    CompoundButtonCompat.setButtonTintList(
                        this, ColorStateList.valueOf(palette.interactiveAccent)
                    )
                }
                val drag = ImageView(activity).apply {
                    setImageResource(R.drawable.oxygen_classic_drag_handle)
                    imageTintList = ColorStateList.valueOf(palette.secondaryText)
                    contentDescription = activity.getString(R.string.quick_toolbar_drag)
                    setPadding(0, 0, 0, 0)
                }
                row.addView(check, LinearLayout.LayoutParams(0, dp(56), 1f))
                row.addView(drag, LinearLayout.LayoutParams(dp(24), dp(24)))
                return Holder(row, check, drag)
            }

            override fun onBindViewHolder(holder: Holder, position: Int) {
                val action = entries()[position]
                val isChosen = action in current()
                holder.check.setOnCheckedChangeListener(null)
                holder.check.text = activity.getString(ToolbarQuickActions.titleRes(action))
                holder.check.isChecked = isChosen
                val canDrag = isChosen && action != ToolbarQuickActions.SEARCH
                holder.drag.visibility = if (canDrag) View.VISIBLE else View.INVISIBLE
                holder.itemView.contentDescription = activity.getString(
                    if (canDrag) R.string.quick_toolbar_drag_row else R.string.quick_toolbar_choose_row,
                    holder.check.text
                )
                holder.check.setOnCheckedChangeListener { button, checked ->
                    val selected = current()
                    val nextSize = selected.size + if (checked) 1 else -1
                    if (nextSize !in 1..5) {
                        button.setOnCheckedChangeListener(null)
                        button.isChecked = !checked
                        Toast.makeText(activity, R.string.quick_toolbar_limit, Toast.LENGTH_SHORT).show()
                    } else {
                        if (checked) selected.add(action) else selected.remove(action)
                        val normalized = ToolbarQuickActions.normalize(scope, selected)
                        selected.clear()
                        selected.addAll(normalized)
                        render()
                    }
                }
            }
        }
        list.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val selected = current()
                if (from !in selected.indices || to !in selected.indices) return false
                if (selected[from] == ToolbarQuickActions.SEARCH ||
                    selected[to] == ToolbarQuickActions.SEARCH) return false
                Collections.swap(selected, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = true
        }).attachToRecyclerView(list)

        fun addTab(textView: TextView, title: Int, target: ToolbarQuickScope) {
            textView.text = activity.getString(title)
            textView.textSize = 15f
            textView.setTextColor(palette.primaryText)
            textView.gravity = Gravity.CENTER
            textView.minHeight = dp(48)
            tabs.addView(textView, LinearLayout.LayoutParams(0, dp(48), 1f))
            textView.setOnClickListener {
                scope = target
                list.scrollToPosition(0)
                render()
            }
        }
        addTab(homeTab, R.string.quick_toolbar_home, ToolbarQuickScope.HOME)
        addTab(folderTab, R.string.quick_toolbar_folder, ToolbarQuickScope.FOLDER)
        root.addView(tabs)
        root.addView(count)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val maxDp = (activity.resources.displayMetrics.heightPixels / density).toInt() - 180
        root.layoutParams = ViewGroup.LayoutParams(-1, dp(minOf(520, maxDp.coerceAtLeast(260))))
        render()

        val dialog = OxygenClassicDialog.show(
            activity,
            OxygenClassicDialog.builder(activity, activity.getString(R.string.quick_toolbar_title))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.quick_toolbar_done) { _, _ ->
                    activity.config.toolbarHomeActions = home
                    activity.config.toolbarFolderActions = folder
                }
                .setNeutralButton(R.string.quick_toolbar_reset, null),
            root
        )
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            current().clear()
            current().addAll(ToolbarQuickActions.defaults)
            render()
        }
    }
}
