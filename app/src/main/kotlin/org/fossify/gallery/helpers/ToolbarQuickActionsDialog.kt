package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/** Classic two-scope chooser. Changes are committed together only on Done. */
object ToolbarQuickActionsDialog {
    fun show(activity: Activity) {
        val palette = OxygenThemeEngine.snapshot(activity, activity.config)
        var shown: AlertDialog? = null
        val home = activity.config.toolbarHomeActions.toMutableList()
        val folder = activity.config.toolbarFolderActions.toMutableList()
        var currentScope = ToolbarQuickScope.HOME
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
            setPadding(dp(8), dp(10), dp(8), dp(6))
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(list)
        }
        fun active(): MutableList<String> = if (currentScope == ToolbarQuickScope.HOME) home else folder

        fun tab(label: TextView, title: Int) {
            label.text = activity.getString(title)
            label.textSize = 15f
            label.setTextColor(palette.primaryText)
            label.gravity = Gravity.CENTER
            label.minHeight = dp(48)
            label.setPadding(dp(6), 0, dp(6), 0)
            tabs.addView(label, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        tab(homeTab, R.string.quick_toolbar_home)
        tab(folderTab, R.string.quick_toolbar_folder)
        root.addView(tabs)
        root.addView(count)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        // Limit dialog content height without modifying the classic dialog window.
        root.layoutParams = ViewGroup.LayoutParams(-1, dp(470))

        fun render() {
            homeTab.typeface = if (currentScope == ToolbarQuickScope.HOME) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            folderTab.typeface = if (currentScope == ToolbarQuickScope.FOLDER) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val actions = active()
            count.text = activity.getString(R.string.quick_toolbar_count, actions.size)
            list.removeAllViews()
            ToolbarQuickActions.options(currentScope).forEach { action ->
                val checkbox = CheckBox(activity).apply {
                    text = activity.getString(ToolbarQuickActions.titleRes(action))
                    textSize = 16f
                    setTextColor(palette.primaryText)
                    isChecked = action in actions
                    minHeight = dp(52)
                    setPadding(dp(8), 0, dp(8), 0)
                }
                checkbox.setOnCheckedChangeListener { button, checked ->
                    val pending = active()
                    val nextCount = pending.size + if (checked) 1 else -1
                    if (nextCount !in 1..5) {
                        button.setOnCheckedChangeListener(null)
                        button.isChecked = !checked
                        Toast.makeText(activity, R.string.quick_toolbar_limit, Toast.LENGTH_SHORT).show()
                        render()
                    } else {
                        if (checked) pending.add(action) else pending.remove(action)
                        render()
                    }
                }
                list.addView(checkbox, LinearLayout.LayoutParams(-1, dp(52)))
            }
            shown?.let { OxygenClassicDialog.apply(activity, it, root) }
        }
        homeTab.setOnClickListener { currentScope = ToolbarQuickScope.HOME; render() }
        folderTab.setOnClickListener { currentScope = ToolbarQuickScope.FOLDER; render() }
        render()
        val dialog = OxygenClassicDialog.show(activity,
            OxygenClassicDialog.builder(activity, activity.getString(R.string.quick_toolbar_title))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.quick_toolbar_done) { _, _ ->
                    activity.config.toolbarHomeActions = home
                    activity.config.toolbarFolderActions = folder
                }
                .setNeutralButton(R.string.quick_toolbar_reset, null), root)
        shown = dialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            if (currentScope == ToolbarQuickScope.HOME) {
                home.clear(); home.addAll(ToolbarQuickActions.defaults)
            } else {
                folder.clear(); folder.addAll(ToolbarQuickActions.defaults)
            }
            render()
        }
    }
}
