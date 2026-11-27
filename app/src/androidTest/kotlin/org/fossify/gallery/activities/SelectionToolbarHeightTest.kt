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
        var frameObserver: android.view.ViewTreeObserver? = null
        var frameListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
        try {
            settle()
            val arguments = InstrumentationRegistry.getArguments()
            arguments.getString("expectedOrientation")?.toInt()?.let {
                assertEquals("Gallery must actually use the requested orientation", it, activity.resources.configuration.orientation)
            }
            arguments.getString("expectedFontScale")?.toFloat()?.let {
                assertEquals("Gallery must actually use the requested font scale", it, activity.resources.configuration.fontScale, .01f)
            }
            var mode: ActionMode? = null
            var normalBottom = 0
            var gridTop = 0
            var rowHeight = 0
            var actions = 0
            val frameFailures = java.util.Collections.synchronizedList(mutableListOf<String>())
            var sampledFrames = 0
            fun effectiveAlpha(view: View): Float {
                var alpha = view.alpha
                var parent = view.parent as? View
                while (parent != null) {
                    alpha *= parent.alpha
                    parent = parent.parent as? View
                }
                return alpha
            }
            fun bottom(view: View): Int {
                val point = IntArray(2)
                view.getLocationOnScreen(point)
                return point[1] + view.height
            }
            val observer = activity.window.decorView.viewTreeObserver
            val frames = android.view.ViewTreeObserver.OnPreDrawListener {
                val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                val normal = menu.binding.toolbarContainer
                val bar = activity.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
                val normalDrawn = normal.isShown && effectiveAlpha(normal) > .99f
                val contextualAlpha = if (bar?.isShown == true) effectiveAlpha(bar) else 0f
                sampledFrames++
                if ((!normalDrawn && contextualAlpha < .99f) || (normalDrawn && contextualAlpha > .01f)) {
                    frameFailures += "frame=$sampledFrames normal=$normalDrawn CAB=$contextualAlpha"
                }
                if (bottom(menu.binding.searchBarContainer) != normalBottom) {
                    frameFailures += "toolbar bottom changed at frame=$sampledFrames"
                }
                val grid = activity.findViewById<View>(R.id.directories_grid)
                if (bottom(grid) - grid.height != gridTop) {
                    frameFailures += "grid top changed at frame=$sampledFrames"
                }
                // Keep observing actual frames after the swap too, to catch late
                // cancelled-animation callbacks during rapid reversal.
                activity.window.decorView.postInvalidateOnAnimation()
                true
            }
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
            frameObserver = observer
            frameListener = frames
            instrumentation.runOnMainSync {
                val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                normalBottom = bottom(menu.binding.searchBarContainer)
                rowHeight = menu.binding.searchBarContainer.height
                val grid = activity.findViewById<View>(R.id.directories_grid)
                gridTop = bottom(grid) - grid.height
                observer.addOnPreDrawListener(frames)
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
                    assertEquals(View.VISIBLE, menu.visibility)
                    assertEquals(View.INVISIBLE, menu.binding.toolbarContainer.visibility)
                    assertTrue(callback.onActionItemClicked(mode!!, mode!!.menu.findItem(123)))
                    mode!!.finish()
                    // Reverse before the previous CAB exit animation can restore normal chrome.
                    if (round < 2) mode = activity.startActionMode(callback)
                }
            }
            settle()
            instrumentation.runOnMainSync {
                val menu = activity.findViewById<MySearchMenu>(R.id.main_menu)
                assertEquals(View.VISIBLE, menu.binding.toolbarContainer.visibility)
                assertEquals(normalBottom, bottom(menu.binding.searchBarContainer))
                assertEquals(3, actions)
                observer.takeIf { it.isAlive }?.removeOnPreDrawListener(frames)
                assertTrue("real start/middle/end frames were sampled", sampledFrames >= 6)
                android.util.Log.i("GalleryPhase35", "CAB frames=$sampledFrames failures=${frameFailures.size} bottom=$normalBottom gridTop=$gridTop height=$rowHeight orientation=${activity.resources.configuration.orientation} fontScale=${activity.resources.configuration.fontScale}")
                assertTrue("Every frame needs exactly one opaque foreground: $frameFailures", frameFailures.isEmpty())
                // The same AppCompat view can later host an unrelated mode. Its
                // framework fade must remain available outside our managed wrapper.
                val unrelated = activity.startActionMode(object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        mode.customView = TextView(activity).apply { text = "Unmanaged" }
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                    override fun onDestroyActionMode(mode: ActionMode) = Unit
                })!!
                val bar = activity.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
                assertEquals(View.VISIBLE, menu.binding.toolbarContainer.visibility)
                assertTrue("unmanaged AppCompat opacity must not be overridden", bar.alpha < .99f)
                unrelated.finish()
            }
        } finally {
            instrumentation.runOnMainSync {
                frameObserver?.takeIf { it.isAlive }?.let { current ->
                    frameListener?.let(current::removeOnPreDrawListener)
                }
                activity.finish()
            }
        }
    }
}
