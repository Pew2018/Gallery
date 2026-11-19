package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.helpers.OxygenSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Native window/CAB layout, including its real fade-out and a reversal during that fade. */
@RunWith(AndroidJUnit4::class)
class SelectionToolbarHeightTest {
    @Test fun homeSelectionPreservesRowAndGridThroughReversal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO
        ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun settle() {
            android.os.SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
        }
        try {
            settle()
            var mode: ActionMode? = null
            var normalBottom = 0
            var gridTop = 0
            var rowHeight = 0
            var actions = 0
            val callback = OxygenSelectionMode.wrap(activity, object : MyActionModeCallback() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    mode.customView = TextView(activity).apply { text = "1" }
                    menu.add(0, 123, 0, "Select all")
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    actions++
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) = Unit
            })
            fun bottom(view: View): Int {
                val point = IntArray(2)
                view.getLocationOnScreen(point)
                return point[1] + view.height
            }
            instrumentation.runOnMainSync {
                val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                normalBottom = bottom(menu.binding.searchBarContainer)
                rowHeight = menu.binding.searchBarContainer.height
                val grid = activity.findViewById<View>(R.id.directories_grid)
                gridTop = bottom(grid) - grid.height
                mode = activity.startActionMode(callback)
                assertNotNull(mode)
            }
            repeat(3) { round ->
                settle()
                instrumentation.runOnMainSync {
                    val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                    val bar = activity.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
                    assertEquals(rowHeight, bar.height)
                    assertEquals(normalBottom, bottom(bar))
                    val grid = activity.findViewById<View>(R.id.directories_grid)
                    assertEquals(gridTop, bottom(grid) - grid.height)
                    assertEquals(View.INVISIBLE, menu.visibility)
                    assertTrue(callback.onActionItemClicked(mode!!, mode!!.menu.findItem(123)))
                    mode!!.finish()
                    // Reverse before the previous CAB exit animation can restore normal chrome.
                    if (round < 2) mode = activity.startActionMode(callback)
                }
            }
            settle()
            instrumentation.runOnMainSync {
                val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                assertEquals(View.VISIBLE, menu.visibility)
                assertEquals(normalBottom, bottom(menu.binding.searchBarContainer))
                assertEquals(3, actions)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
