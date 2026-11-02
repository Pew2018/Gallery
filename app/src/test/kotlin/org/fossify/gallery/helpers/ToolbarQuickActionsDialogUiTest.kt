package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ToolbarQuickActionsDialogUiTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(R.style.OxygenClassicAlertDialog_Light)
        baseConfig.isSystemThemeEnabled = false
        baseConfig.backgroundColor = Color.WHITE
        baseConfig.textColor = Color.BLACK
        baseConfig.primaryColor = Color.BLUE
    }

    private fun descendants(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(descendants(root.getChildAt(index)))
    }

    private fun isDescendant(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    @Test fun tabsStayOutsideTheScrollableMiddleAndDialogActionsStayReachable() {
        val activity = activity()
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val views = descendants(dialog.window!!.decorView)
        val scroll = views.filterIsInstance<ScrollView>().firstOrNull()
        assertNotNull("middle content has a scroll viewport", scroll)
        val tabs = views.filterIsInstance<TextView>().filter {
            it.text == activity.getString(R.string.quick_toolbar_home) ||
                it.text == activity.getString(R.string.quick_toolbar_folder)
        }
        assertEquals(2, tabs.size)
        tabs.forEach { assertFalse("tab must not scroll with the options", isDescendant(it, scroll!!)) }
        val optional = views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.quick_toolbar_optional_section) }
        assertTrue(isDescendant(optional, scroll!!))
        val list = views.filterIsInstance<RecyclerView>().single()
        assertTrue("optional rows are present", list.adapter!!.itemCount > 0)
        assertEquals("nested list measures wrapping rows instead of assuming 56dp for every font scale",
            ViewGroup.LayoutParams.WRAP_CONTENT, list.layoutParams.height)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(cancel.isShown && cancel.isEnabled)
        assertTrue(save.isShown && save.isEnabled)
        assertFalse(isDescendant(cancel, scroll))
        assertFalse(isDescendant(save, scroll))

        scroll.scrollTo(0, 10_000)
        assertTrue("fixed tabs remain attached and visible while middle content scrolls", tabs.all { it.isShown })
        tabs.first { it.text == activity.getString(R.string.quick_toolbar_folder) }.performClick()
        assertTrue(tabs.all { it.isShown })
        dialog.dismiss()
        activity.finish()
    }

    @Test fun cancelKeepsPersistedToolbarPreferencesUnchanged() {
        val activity = activity()
        val originalHome = activity.config.toolbarHomeActions.toList()
        val originalFolder = activity.config.toolbarFolderActions.toList()
        val dialog = ToolbarQuickActionsDialog.show(activity)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(originalHome, activity.config.toolbarHomeActions)
        assertEquals(originalFolder, activity.config.toolbarFolderActions)
        activity.finish()
    }

    @Test fun tabSwitchKeepsUncommittedFolderChoicesUntilExplicitSave() {
        val activity = activity()
        activity.config.toolbarHomeActions = ToolbarQuickActions.defaults
        activity.config.toolbarFolderActions = ToolbarQuickActions.defaults
        val originalHome = activity.config.toolbarHomeActions.toList()
        val originalFolder = activity.config.toolbarFolderActions.toList()
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val views = descendants(dialog.window!!.decorView)
        val folderTab = views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.quick_toolbar_folder) }
        val homeTab = views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.quick_toolbar_home) }
        (folderTab.parent as View).performClick()
        val search = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().first {
            it.text == activity.getString(org.fossify.commons.R.string.search)
        }
        assertTrue(search.isChecked)
        search.performClick()
        assertFalse(search.isChecked)
        assertEquals(originalFolder, activity.config.toolbarFolderActions)
        (homeTab.parent as View).performClick()
        (folderTab.parent as View).performClick()
        val restoredSearch = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().first {
            it.text == activity.getString(org.fossify.commons.R.string.search)
        }
        assertFalse(restoredSearch.isChecked)
        assertEquals(originalHome, activity.config.toolbarHomeActions)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotEquals(originalFolder, activity.config.toolbarFolderActions)
        assertEquals(originalHome, activity.config.toolbarHomeActions)
        activity.finish()
    }
    @Test fun checkboxOwnsTheOnlyRowRippleAndFixedOptionsHaveNoPressEffect() {
        val activity = activity()
        activity.config.toolbarHomeActions = ToolbarQuickActions.defaults
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val views = descendants(dialog.window!!.decorView)
        val list = views.filterIsInstance<RecyclerView>().single()
        @Suppress("UNCHECKED_CAST")
        val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        val holder = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView as ViewGroup
        val checkbox = row.getChildAt(0) as CheckBox
        val drag = row.getChildAt(1)
        assertNull("row itself must not own a duplicate ripple", row.background)
        assertTrue(checkbox.background is android.graphics.drawable.RippleDrawable)
        assertFalse("checkbox click must not include the drag handle", checkbox === drag)
        assertTrue(checkbox.isClickable)
        assertTrue(checkbox.isFocusable)
        val fixed = views.filterIsInstance<CheckBox>().first {
            it.text == activity.getString(R.string.quick_toolbar_browse_view)
        }
        assertFalse(fixed.isClickable)
        assertNull(fixed.background)
        dialog.dismiss()
        activity.finish()
    }

    @Test fun selectingAnOutOfOrderActionMovesItUsingStableIdsWithoutFade() {
        val activity = activity()
        activity.config.toolbarHomeActions = ToolbarQuickActions.defaults
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val list = descendants(dialog.window!!.decorView).filterIsInstance<RecyclerView>().single()
        @Suppress("UNCHECKED_CAST")
        val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        assertTrue(adapter.hasStableIds())
        val animator = list.itemAnimator as androidx.recyclerview.widget.DefaultItemAnimator
        assertFalse(animator.supportsChangeAnimations)
        assertEquals(170L, animator.moveDuration)
        val last = adapter.itemCount - 1
        val chosenId = adapter.getItemId(last)
        val holder = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(holder, last)
        val check = (holder.itemView as ViewGroup).getChildAt(0) as CheckBox
        assertFalse(check.isChecked)
        check.performClick()
        assertTrue(check.isChecked)
        assertEquals(chosenId, adapter.getItemId(2))
        assertEquals("unchecked rows never become duplicates", adapter.itemCount,
            (0 until adapter.itemCount).map { adapter.getItemId(it) }.toSet().size)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(4, activity.config.toolbarHomeActions.size)
        activity.finish()
    }

}
