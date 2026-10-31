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
        val originalHome = activity.config.toolbarHomeActions.toList()
        val originalFolder = activity.config.toolbarFolderActions.toList()
        val dialog = ToolbarQuickActionsDialog.show(activity)
        val views = descendants(dialog.window!!.decorView)
        val folderTab = views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.quick_toolbar_folder) }
        val homeTab = views.filterIsInstance<TextView>().first { it.text == activity.getString(R.string.quick_toolbar_home) }
        (folderTab.parent as View).performClick()
        val unchecked = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().first { !it.isChecked && it.isShown }
        val actionLabel = unchecked.text.toString()
        unchecked.performClick()
        assertEquals(originalFolder, activity.config.toolbarFolderActions)
        (homeTab.parent as View).performClick()
        (folderTab.parent as View).performClick()
        val restoredChoice = descendants(dialog.window!!.decorView).filterIsInstance<CheckBox>().first { it.text.toString() == actionLabel }
        assertTrue(restoredChoice.isChecked)
        assertEquals(originalHome, activity.config.toolbarHomeActions)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertNotEquals(originalFolder, activity.config.toolbarFolderActions)
        assertEquals(originalHome, activity.config.toolbarHomeActions)
        activity.finish()
    }
}
