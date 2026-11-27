package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class OxygenSelectionPhase5cTest {
    @Test
    fun selectionChromeStaysOnTheLiveTheme() {
        val fixture = host()
        try {
            val themeToolbar = OxygenThemeEngine.toolbarSurface(fixture.activity, fixture.activity.config)
            val themeStatus = OxygenThemeEngine.statusBarSurface(fixture.activity, fixture.activity.config)
            val themeForeground = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config).toolbarForeground
            val targets = OxygenSelectionChrome.targets(fixture.activity)
            assertEquals(themeToolbar, targets.toolbar)
            assertEquals(themeStatus, targets.status)
            assertEquals(themeForeground, targets.foreground)
            assertNotEquals(0xFF757575.toInt(), targets.toolbar)
            assertNotEquals(0xFF616161.toInt(), targets.status)
            OxygenSystemBars.apply(fixture.activity)
            val navigation = fixture.activity.window.navigationBarColor
            val menuHeight = fixture.menu.binding.searchBarContainer.height
            val contentHeight = OxygenToolbarStyle.selectionContentHeight(fixture.activity)

            val mode = fixture.activity.startActionMode(fixture.callback)!!
            assertNull(OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            val bar = fixture.activity.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
            assertEquals(themeToolbar, (bar.background as ColorDrawable).color)
            assertEquals(themeToolbar, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(themeForeground, (mode.customView as TextView).currentTextColor)
            assertEquals(themeStatus, statusColor(fixture.activity))
            assertEquals(navigation, fixture.activity.window.navigationBarColor)
            assertEquals(menuHeight, fixture.menu.binding.searchBarContainer.height)
            assertEquals(contentHeight, bar.contentHeight)
            assertEquals(ACCENT, fixture.activity.config.oxygenCustomAccent)

            (mode.customView as TextView).text = "4 / 9"
            assertTrue(fixture.callback.onPrepareActionMode(mode, mode.menu))
            assertNull(OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            assertEquals(themeToolbar, (bar.background as ColorDrawable).color)
            assertEquals(themeStatus, statusColor(fixture.activity))

            // Drag-style restyle must not introduce a grey interim.
            OxygenSelectionChrome.move(fixture.activity, 1f)
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            assertEquals(themeToolbar, (bar.background as ColorDrawable).color)
            assertEquals(themeStatus, statusColor(fixture.activity))

            mode.finish()
            assertNull(OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            assertEquals(themeToolbar, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(themeStatus, statusColor(fixture.activity))
            assertEquals(ACCENT, fixture.activity.config.oxygenCustomAccent)
            assertEquals("custom", fixture.activity.config.oxygenThemePresetId)
            assertEquals(menuHeight, fixture.menu.binding.searchBarContainer.height)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun disabledAnimationsStillKeepThemeChrome() {
        val fixture = host()
        val prior = animatorScale(fixture.activity)
        Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val themeToolbar = OxygenThemeEngine.toolbarSurface(fixture.activity, fixture.activity.config)
            val themeStatus = OxygenThemeEngine.statusBarSurface(fixture.activity, fixture.activity.config)
            val themeForeground = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config).toolbarForeground
            val mode = fixture.activity.startActionMode(fixture.callback)!!
            assertNull(OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            val bar = fixture.activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            assertEquals(themeToolbar, (bar.background as ColorDrawable).color)
            assertEquals(themeToolbar, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(themeStatus, statusColor(fixture.activity))
            assertEquals(themeForeground, (mode.customView as TextView).currentTextColor)
            mode.finish()
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            assertEquals(themeToolbar, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(themeStatus, statusColor(fixture.activity))
            assertEquals(ACCENT, fixture.activity.config.oxygenCustomAccent)
        } finally {
            Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            fixture.close()
        }
    }

    @Test
    @Config(sdk = [36])
    fun selectionStatusKeepsThemeUnderlayOnModernApis() {
        val fixture = host()
        val prior = animatorScale(fixture.activity)
        Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val themeStatus = OxygenThemeEngine.statusBarSurface(fixture.activity, fixture.activity.config)
            fixture.activity.startActionMode(fixture.callback)
            val underlay = fixture.activity.window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            assertEquals(themeStatus, (underlay.background as ColorDrawable).color)
        } finally {
            Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            fixture.close()
        }
    }

    @Test
    fun viewerOwnedBarsStayOnTheViewerCurve() {
        val fixture = host()
        try {
            val tokens = OxygenThemeEngine.snapshot(fixture.activity, fixture.activity.config).copy(
                statusBarBackground = Color.WHITE,
            )
            OxygenSystemBars.applyViewer(fixture.activity, tokens, 1f, true)
            val viewerStatus = fixture.activity.window.statusBarColor
            OxygenSelectionChrome.move(fixture.activity, 1f)
            assertEquals(viewerStatus, fixture.activity.window.statusBarColor)
            assertEquals(fixture.activity.getColor(R.color.viewer_status_surface), viewerStatus)
        } finally {
            OxygenSystemBars.forget(fixture.activity)
            fixture.close()
        }
    }

    private fun colorOf(view: View): Int = when (val background = view.background) {
        is ColorDrawable -> background.color
        is com.google.android.material.shape.MaterialShapeDrawable ->
            background.fillColor?.defaultColor ?: error("toolbar shape has no fill")
        else -> error("unexpected toolbar background ${background?.javaClass?.name}")
    }

    private fun statusColor(activity: SimpleActivity): Int {
        val underlay = activity.window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
        return if (android.os.Build.VERSION.SDK_INT < 35) activity.window.statusBarColor
        else (underlay.background as ColorDrawable).color
    }

    private fun animatorScale(activity: SimpleActivity) =
        Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    private fun layout(view: View, width: Int, height: Int) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = if (height == 0) View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        else View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun host(): Fixture {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.GalleryPageTransitionTheme)
        controller.setup()
        activity.config.oxygenThemeActivated = true
        activity.config.oxygenThemePresetId = "custom"
        activity.config.oxygenCustomAccent = ACCENT
        val page = activity.layoutInflater.inflate(R.layout.activity_main, null)
        activity.setContentView(page)
        val menu = page.findViewById<MySearchMenu>(R.id.main_menu)
        OxygenToolbarStyle.apply(activity, menu, "Gallery")
        layout(activity.window.decorView, 1080, 1920)
        val callback = OxygenSelectionMode.wrap(activity, object : MyActionModeCallback() {
            override fun onCreateActionMode(mode: ActionMode, items: Menu): Boolean {
                mode.customView = TextView(activity).apply { text = "1" }
                items.add(0, 7, 0, "Select all")
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
            override fun onDestroyActionMode(mode: ActionMode) = Unit
        })
        return Fixture(activity, menu, callback, controller)
    }

    private class Fixture(
        val activity: SimpleActivity,
        val menu: MySearchMenu,
        val callback: MyActionModeCallback,
        private val controller: org.robolectric.android.controller.ActivityController<SimpleActivity>,
    ) {
        fun close() {
            controller.pause().stop().destroy()
        }
    }

    private companion object {
        const val ACCENT = 0xFF145C20.toInt()
    }
}
