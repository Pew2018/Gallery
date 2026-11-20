package org.fossify.gallery.helpers

import android.app.Application
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import org.fossify.gallery.activities.SimpleActivity
import androidx.appcompat.widget.ActionBarContextView
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class SelectionToolbarHeightTest {
    @Test fun portraitRowsShareTheExistingContextHeight() = verifyRows(360, 720)

    @Test @Config(qualifiers = "land")
    fun landscapeRowsShareTheExistingContextHeight() = verifyRows(720, 360)

    @Test fun largeFontRowsGrowTogether() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try { verifyRows(360, 720) } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
    }

    private fun verifyRows(width: Int, height: Int) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java)
        // Theme before onCreate, as the real Gallery Activities do.
        val host = controller.get()
        host.setTheme(R.style.GalleryPageTransitionTheme)
        controller.setup()
        try {
            for (layout in listOf(R.layout.activity_main, R.layout.activity_media)) {
                val page = LayoutInflater.from(host).inflate(layout, null)
                host.setContentView(page)
                val menu = page.findViewById<MySearchMenu>(
                    if (layout == R.layout.activity_main) R.id.main_menu else R.id.media_menu)
                OxygenToolbarStyle.apply(host, menu, "Gallery")
                val density = host.resources.displayMetrics.density
                fun measure() {
                    host.window.decorView.measure(
                        View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                    host.window.decorView.layout(0, 0, (width * density).toInt(), (height * density).toInt())
                }
                measure()
                val normalHeight = menu.binding.searchBarContainer.height
                val initialBottom = menu.bottom
                val originalContextHeight = ActionBarContextView(host).contentHeight
                assertTrue("Normal row must retain the existing CAB minimum", normalHeight >= originalContextHeight)
                assertTrue("Default theme CAB is taller than the old 56dp row", originalContextHeight > 56 * density)
                var clicks = 0
                var destroyed = 0
                val callback = OxygenSelectionMode.wrap(host, object : MyActionModeCallback() {
                    override fun onCreateActionMode(mode: ActionMode, items: Menu): Boolean {
                        mode.customView = TextView(host).apply { text = "1" }
                        items.add(0, 123, 0, "Select all")
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        clicks++
                        return true
                    }
                    override fun onDestroyActionMode(mode: ActionMode) { destroyed++ }
                })
                repeat(3) {
                    val mode = host.startActionMode(callback)!!
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                        .idleFor(java.time.Duration.ofMillis(300))
                    measure()
                    val bar = host.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
                    assertEquals(normalHeight, bar.height)
                    assertEquals(initialBottom, menu.bottom)
                    assertEquals(View.VISIBLE, menu.visibility)
                    assertEquals(View.INVISIBLE, menu.binding.toolbarContainer.visibility)
                    assertTrue(callback.onActionItemClicked(mode, mode.menu.findItem(123)))
                    mode.finish()
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                        .idleFor(java.time.Duration.ofMillis(300))
                    measure()
                    menu.viewTreeObserver.dispatchOnPreDraw()
                    assertEquals(initialBottom, menu.bottom)
                    assertEquals(View.VISIBLE, menu.binding.toolbarContainer.visibility)
                }
                assertEquals(3, clicks)
                assertEquals(3, destroyed)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
