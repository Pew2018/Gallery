package org.fossify.gallery.helpers

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
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
        fun optionalEntries(): List<String> = current().filterNot { it == ToolbarQuickActions.SEARCH } +
            ToolbarQuickActions.options(scope)
                .filterNot { it == ToolbarQuickActions.SEARCH || it in current() }

        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        lateinit var render: () -> Unit

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val count = TextView(activity).apply {
            textSize = 13f
            setTextColor(palette.secondaryText)
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val guidance = TextView(activity).apply {
            textSize = 12f
            setTextColor(palette.secondaryText)
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val reset = TextView(activity).apply {
            setText(R.string.quick_toolbar_reset)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.interactiveAccent)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            setPadding(dp(8), 0, dp(8), 0)
            background = OxygenClassicDialog.ripple(palette.interactiveAccent, density)
            setOnClickListener {
                current().clear()
                current().addAll(ToolbarQuickActions.defaults)
                render()
            }
        }
        fun sectionLabel(textRes: Int) = TextView(activity).apply {
            setText(textRes)
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.interactiveAccent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        fun controlRow(): LinearLayout = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
        }

        fun createCheckBox(): CheckBox = CheckBox(activity).apply {
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

        val fixedRows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val searchRow = controlRow()
        val searchCheck = createCheckBox().apply {
            text = activity.getString(org.fossify.commons.R.string.search)
        }
        var searchBackground: android.graphics.drawable.Drawable? = searchCheck.background
        val searchClickable = searchCheck.isClickable
        val searchFocusable = searchCheck.isFocusable
        val searchFocusableInTouchMode = searchCheck.isFocusableInTouchMode
        searchRow.addView(searchCheck, LinearLayout.LayoutParams(-1, dp(56)))

        val browseRow = controlRow().apply {
            contentDescription = activity.getString(R.string.quick_toolbar_fixed_browse_accessibility)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val browseCheck = createCheckBox().apply {
            text = activity.getString(R.string.quick_toolbar_browse_view)
            isChecked = true
            isClickable = false
            isFocusable = false
            contentDescription = activity.getString(R.string.quick_toolbar_fixed_browse_accessibility)
        }
        browseRow.addView(browseCheck, LinearLayout.LayoutParams(0, dp(56), 1f))
        fixedRows.addView(searchRow)
        fixedRows.addView(browseRow)

        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            itemAnimator = null
            isNestedScrollingEnabled = false
        }
        class Holder(row: LinearLayout, val check: CheckBox, val drag: ImageView) :
            RecyclerView.ViewHolder(row)

        lateinit var adapter: RecyclerView.Adapter<Holder>
        lateinit var touchHelper: ItemTouchHelper
        lateinit var homeTabLabel: TextView
        lateinit var folderTabLabel: TextView
        lateinit var homeIndicator: View
        lateinit var folderIndicator: View

        fun updateSearch(checked: Boolean) {
            val selectedActions = current()
            val nextSize = selectedActions.size + if (checked) 1 else -1
            if (nextSize !in 1..5) {
                searchCheck.setOnCheckedChangeListener(null)
                searchCheck.isChecked = !checked
                searchCheck.setOnCheckedChangeListener { _, value -> updateSearch(value) }
                Toast.makeText(activity, R.string.quick_toolbar_limit, Toast.LENGTH_SHORT).show()
                return
            }
            if (checked) selectedActions.add(0, ToolbarQuickActions.SEARCH)
            else selectedActions.remove(ToolbarQuickActions.SEARCH)
            val normalized = ToolbarQuickActions.normalize(scope, selectedActions)
            selectedActions.clear()
            selectedActions.addAll(normalized)
            render()
        }

        fun updateAction(action: String, button: android.widget.CompoundButton, checked: Boolean) {
            val selectedActions = current()
            val nextSize = selectedActions.size + if (checked) 1 else -1
            if (nextSize !in 1..5) {
                button.setOnCheckedChangeListener(null)
                button.isChecked = !checked
                button.setOnCheckedChangeListener { checkbox, value ->
                    updateAction(action, checkbox, value)
                }
                Toast.makeText(activity, R.string.quick_toolbar_limit, Toast.LENGTH_SHORT).show()
                return
            }
            if (checked) selectedActions.add(action) else selectedActions.remove(action)
            val normalized = ToolbarQuickActions.normalize(scope, selectedActions)
            selectedActions.clear()
            selectedActions.addAll(normalized)
            render()
        }

        render = {
            val selected = current().size
            count.text = activity.getString(R.string.quick_toolbar_count, selected)
            guidance.text = activity.getString(R.string.quick_toolbar_guidance)
            val homeScope = scope == ToolbarQuickScope.HOME
            searchCheck.setOnCheckedChangeListener(null)
            searchCheck.isChecked = ToolbarQuickActions.SEARCH in current()
            searchCheck.isClickable = if (homeScope) false else searchClickable
            searchCheck.isFocusable = if (homeScope) false else searchFocusable
            searchCheck.isFocusableInTouchMode = if (homeScope) false else searchFocusableInTouchMode
            if (homeScope) {
                searchCheck.background = ColorDrawable(Color.TRANSPARENT)
            } else {
                searchBackground?.let { searchCheck.background = it }
                searchCheck.setOnCheckedChangeListener { _, checked -> updateSearch(checked) }
            }
            browseRow.visibility = if (homeScope) View.VISIBLE else View.GONE
            homeTabLabel.typeface = if (scope == ToolbarQuickScope.HOME)
                Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            folderTabLabel.typeface = if (scope == ToolbarQuickScope.FOLDER)
                Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            homeIndicator.alpha = if (scope == ToolbarQuickScope.HOME) 1f else 0f
            folderIndicator.alpha = if (scope == ToolbarQuickScope.FOLDER) 1f else 0f
            adapter.notifyDataSetChanged()
        }

        adapter = object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount(): Int = optionalEntries().size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val row = controlRow().apply {
                    layoutParams = RecyclerView.LayoutParams(-1, dp(56))
                }
                val check = createCheckBox()
                val drag = ImageView(activity).apply {
                    setImageResource(R.drawable.oxygen_classic_drag_handle)
                    imageTintList = ColorStateList.valueOf(palette.secondaryText)
                    // Keep a 48dp touch target while drawing the same 24dp glyph
                    // used by the bottom-navigation ordering dialog.
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = activity.getString(R.string.quick_toolbar_drag)
                    isClickable = true
                    isFocusable = true
                }
                row.addView(check, LinearLayout.LayoutParams(0, dp(56), 1f))
                row.addView(drag, LinearLayout.LayoutParams(dp(48), dp(48)))
                return Holder(row, check, drag)
            }

            override fun onBindViewHolder(holder: Holder, position: Int) {
                val action = optionalEntries()[position]
                val isChosen = action in current()
                holder.check.setOnCheckedChangeListener(null)
                holder.check.text = activity.getString(ToolbarQuickActions.titleRes(action))
                holder.check.isChecked = isChosen
                val canDrag = isChosen
                holder.drag.visibility = if (canDrag) View.VISIBLE else View.INVISIBLE
                holder.drag.contentDescription = activity.getString(
                    if (canDrag) R.string.quick_toolbar_drag_row else R.string.quick_toolbar_choose_row,
                    holder.check.text
                )
                holder.drag.setOnLongClickListener {
                    if (canDrag) {
                        touchHelper.startDrag(holder)
                        true
                    } else {
                        false
                    }
                }
                holder.drag.setOnClickListener { anchor ->
                    if (!canDrag) return@setOnClickListener
                    val selected = current().filterNot { it == ToolbarQuickActions.SEARCH }.toMutableList()
                    val selectedIndex = selected.indexOf(action)
                    if (selectedIndex < 0) return@setOnClickListener
                    val popup = PopupMenu(activity, anchor)
                    if (selectedIndex > 0) popup.menu.add(0, 0, 0, R.string.quick_toolbar_move_up)
                    if (selectedIndex < selected.lastIndex) popup.menu.add(0, 1, 1, R.string.quick_toolbar_move_down)
                    popup.setOnMenuItemClickListener { item ->
                        val target = if (item.itemId == 0) selectedIndex - 1 else selectedIndex + 1
                        if (target in selected.indices) {
                            selected.removeAt(selectedIndex)
                            selected.add(target, action)
                            val search = current().contains(ToolbarQuickActions.SEARCH)
                            current().clear()
                            if (search) current().add(ToolbarQuickActions.SEARCH)
                            current().addAll(selected)
                            render()
                        }
                        true
                    }
                    popup.show()
                }
                holder.check.setOnCheckedChangeListener { button, checked ->
                    updateAction(action, button, checked)
                }
            }
        }
        list.adapter = adapter
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val selectedActions = current().filterNot { it == ToolbarQuickActions.SEARCH }
                if (from !in selectedActions.indices || to !in selectedActions.indices) return false
                val searchOffset = if (ToolbarQuickActions.SEARCH in current()) 1 else 0
                Collections.swap(current(), from + searchOffset, to + searchOffset)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled() = false
        }).also { it.attachToRecyclerView(list) }

        fun addTab(title: Int, target: ToolbarQuickScope): Triple<ViewGroup, TextView, View> {
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.interactiveAccent, 26)),
                    null,
                    ColorDrawable(Color.WHITE)
                )
            }
            val label = TextView(activity).apply {
                text = activity.getString(title)
                textSize = 15f
                setTextColor(palette.primaryText)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, dp(46))
            }
            val indicator = View(activity).apply {
                setBackgroundColor(palette.interactiveAccent)
                layoutParams = LinearLayout.LayoutParams(-1, dp(2))
            }
            container.addView(label)
            container.addView(indicator)
            container.setOnClickListener {
                scope = target
                list.scrollToPosition(0)
                render()
            }
            return Triple(container, label, indicator)
        }

        val (homeTab, homeLabel, homeLine) = addTab(
            R.string.quick_toolbar_home, ToolbarQuickScope.HOME
        )
        val (folderTab, folderLabel, folderLine) = addTab(
            R.string.quick_toolbar_folder, ToolbarQuickScope.FOLDER
        )
        homeTabLabel = homeLabel
        folderTabLabel = folderLabel
        homeIndicator = homeLine
        folderIndicator = folderLine
        tabs.addView(homeTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(folderTab, LinearLayout.LayoutParams(0, dp(48), 1f))

        root.addView(tabs)
        root.addView(count)
        root.addView(guidance)
        root.addView(reset, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(sectionLabel(R.string.quick_toolbar_fixed_section))
        root.addView(fixedRows)
        root.addView(sectionLabel(R.string.quick_toolbar_optional_section))
        root.addView(list, LinearLayout.LayoutParams(-1, -2))
        render()

        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val windowHeight = OxygenClassicDialog.availableHeight(activity)
                // Leave the AlertDialog title and its fixed action row outside this scroll area.
                val actionAndTitleSpace = dp(112)
                val available = (windowHeight - actionAndTitleSpace).coerceAtLeast(1)
                val mode = MeasureSpec.getMode(heightMeasureSpec)
                val size = MeasureSpec.getSize(heightMeasureSpec)
                val limit = if (mode == MeasureSpec.UNSPECIFIED) available else minOf(size, available)
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            clipToPadding = false
            addView(root, ScrollView.LayoutParams(-1, -2))
        }

        val dialog = OxygenClassicDialog.show(
            activity,
            OxygenClassicDialog.builder(activity, activity.getString(R.string.quick_toolbar_title))
                .setView(scroll)
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .setPositiveButton(org.fossify.gallery.R.string.photo_profiles_save) { _, _ ->
                    activity.config.toolbarHomeActions = home
                    activity.config.toolbarFolderActions = folder
                },
            scroll
        )
        searchBackground = searchCheck.background
        render()
    }
}
