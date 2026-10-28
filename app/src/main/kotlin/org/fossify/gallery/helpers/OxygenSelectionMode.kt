package org.fossify.gallery.helpers

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * Wraps Commons' action callback instead of replacing selection behavior.
 * Commons keeps selection keys, select-all interaction, and file operations.
 */
object OxygenSelectionMode {
    fun wrap(activity: Activity, delegate: MyActionModeCallback): MyActionModeCallback =
        object : MyActionModeCallback() {
            private var active = false
            private var oldStatusBarColor: Int? = null
            private var oldLightStatusIcons: Boolean? = null

            override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
                val created = delegate.onCreateActionMode(mode, menu)
                isSelectable = delegate.isSelectable
                if (!created) return false
                active = true
                oldStatusBarColor = activity.window.statusBarColor
                oldLightStatusIcons = WindowCompat.getInsetsController(
                    activity.window, activity.window.decorView
                ).isAppearanceLightStatusBars
                style(mode, menu)
                mode.customView?.post {
                    if (active && !activity.isFinishing && !activity.isDestroyed) style(mode, mode.menu)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = delegate.onPrepareActionMode(mode, menu)
                if (active) style(mode, menu)
                return result
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                delegate.onActionItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) {
                active = false
                delegate.onDestroyActionMode(mode)
                isSelectable = delegate.isSelectable
                if (Build.VERSION.SDK_INT < 35) {
                    oldStatusBarColor?.let { activity.window.statusBarColor = it }
                }
                oldLightStatusIcons?.let {
                    WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .isAppearanceLightStatusBars = it
                }
                oldStatusBarColor = null
                oldLightStatusIcons = null
            }

            private fun style(mode: ActionMode, menu: Menu?) {
                val surface = OxygenThemeEngine.toolbarSurface(activity, activity.config)
                val foreground = OxygenThemeEngine.toolbarForeground(activity, activity.config)
                val title = mode.customView as? TextView
                title?.setTextColor(foreground)
                SelectionCountFormatter.selectedCount(title?.text)?.let { count ->
                    title?.text = activity.getString(R.string.oxygen_selected_count, count)
                }

                // ActionBarContextView is separate from the normal search toolbar.
                var parent: View? = title
                while (parent != null) {
                    if (parent.javaClass.simpleName.contains("ActionBarContextView")) {
                        parent.setBackgroundColor(surface)
                        tintClose(parent, foreground)
                        break
                    }
                    parent = parent.parent as? View
                }
                activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
                    ?.takeIf { it.isShown }?.let {
                        it.setBackgroundColor(surface)
                        tintClose(it, foreground)
                    }
                tintMenu(menu, foreground)
                if (Build.VERSION.SDK_INT < 35) activity.window.statusBarColor = surface
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars =
                    ColorUtils.calculateContrast(android.graphics.Color.BLACK, surface) >= 4.5
            }

            private fun tintMenu(menu: Menu?, foreground: Int) {
                if (menu == null) return
                for (i in 0 until menu.size()) {
                    val item = menu.getItem(i)
                    val tint = if (item.isEnabled) foreground else ColorUtils.setAlphaComponent(foreground, 110)
                    item.icon?.mutate()?.setTintList(ColorStateList.valueOf(tint))
                    item.subMenu?.let { tintMenu(it, foreground) }
                }
            }

            private fun tintClose(root: View, foreground: Int) {
                val close = root.findViewById<View>(androidx.appcompat.R.id.action_mode_close_button)
                fun tintImages(view: View) {
                    if (view is ImageView) view.imageTintList = ColorStateList.valueOf(foreground)
                    if (view is ViewGroup) {
                        for (i in 0 until view.childCount) tintImages(view.getChildAt(i))
                    }
                }
                close?.let(::tintImages)
            }
        }
}
