package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import org.fossify.commons.interfaces.MyActionModeCallback
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.databinding.PhotoItemGridBinding
import org.fossify.gallery.databinding.PhotoItemListBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun selectionChromeUsesFixedGreyAndRestoresTheLiveTheme() {
        val fixture = host()
        try {
            val themeToolbar = OxygenThemeEngine.toolbarSurface(fixture.activity, fixture.activity.config)
            val themeStatus = OxygenThemeEngine.statusBarSurface(fixture.activity, fixture.activity.config)
            val targets = OxygenSelectionChrome.targets(fixture.activity)
            assertEquals(OxygenSelectionChrome.TOOLBAR_COLOR, targets.toolbar)
            assertEquals(OxygenSelectionChrome.STATUS_COLOR, targets.status)
            assertNotEquals(targets.toolbar, themeToolbar)
            assertNotEquals(targets.toolbar, ACCENT)
            OxygenSystemBars.apply(fixture.activity)
            val navigation = fixture.activity.window.navigationBarColor
            val menuHeight = fixture.menu.binding.searchBarContainer.height
            val contentHeight = OxygenToolbarStyle.selectionContentHeight(fixture.activity)

            val mode = fixture.activity.startActionMode(fixture.callback)!!
            val entering = OxygenSelectionChrome.animator(fixture.activity)!!
            entering.setCurrentFraction(1f)
            val bar = fixture.activity.findViewById<ActionBarContextView>(androidx.appcompat.R.id.action_mode_bar)
            assertEquals(OxygenSelectionChrome.TOOLBAR_COLOR, (bar.background as ColorDrawable).color)
            assertEquals(OxygenSelectionChrome.TOOLBAR_COLOR, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(OxygenSelectionChrome.FOREGROUND_COLOR, (mode.customView as TextView).currentTextColor)
            assertEquals(OxygenSelectionChrome.STATUS_COLOR, statusColor(fixture.activity))
            assertEquals(navigation, fixture.activity.window.navigationBarColor)
            assertEquals(menuHeight, fixture.menu.binding.searchBarContainer.height)
            assertEquals(contentHeight, bar.contentHeight)
            assertEquals(ACCENT, fixture.activity.config.oxygenCustomAccent)

            val midAnimator = OxygenSelectionChrome.animator(fixture.activity)!!
            midAnimator.setCurrentFraction(0.4f)
            val mid = OxygenSelectionChrome.progress(fixture.activity)
            (mode.customView as TextView).text = "4 / 9"
            assertTrue(fixture.callback.onPrepareActionMode(mode, mode.menu))
            assertSame(midAnimator, OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(mid, OxygenSelectionChrome.progress(fixture.activity), 0f)
            assertNotEquals(themeToolbar, (bar.background as ColorDrawable).color)

            mode.finish()
            val leaving = OxygenSelectionChrome.animator(fixture.activity)!!
            assertNotEquals(midAnimator, leaving)
            assertEquals(mid, OxygenSelectionChrome.progress(fixture.activity), 0.02f)
            leaving.setCurrentFraction(1f)
            assertEquals(0f, OxygenSelectionChrome.progress(fixture.activity), 0.001f)
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
    fun disabledAnimationsLandOnTheSelectionAndThemeEndpoints() {
        val fixture = host()
        val prior = animatorScale(fixture.activity)
        Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val themeToolbar = OxygenThemeEngine.toolbarSurface(fixture.activity, fixture.activity.config)
            val themeStatus = OxygenThemeEngine.statusBarSurface(fixture.activity, fixture.activity.config)
            val mode = fixture.activity.startActionMode(fixture.callback)!!
            assertNull(OxygenSelectionChrome.animator(fixture.activity))
            assertEquals(1f, OxygenSelectionChrome.progress(fixture.activity), 0f)
            val bar = fixture.activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            assertEquals(OxygenSelectionChrome.TOOLBAR_COLOR, (bar.background as ColorDrawable).color)
            assertEquals(OxygenSelectionChrome.TOOLBAR_COLOR, colorOf(fixture.menu.binding.toolbarContainer))
            assertEquals(OxygenSelectionChrome.STATUS_COLOR, statusColor(fixture.activity))
            assertEquals(OxygenSelectionChrome.FOREGROUND_COLOR, (mode.customView as TextView).currentTextColor)
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
    fun selectionStatusUsesTheExistingUnderlayOnModernApis() {
        val fixture = host()
        val prior = animatorScale(fixture.activity)
        Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            fixture.activity.startActionMode(fixture.callback)
            val underlay = fixture.activity.window.decorView.findViewWithTag<View>(OxygenSystemBars.STATUS_UNDERLAY)
            assertEquals(OxygenSelectionChrome.STATUS_COLOR, (underlay.background as ColorDrawable).color)
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

    @Test
    fun gridShrinksTheThumbnailAndKeepsTheCellStill() {
        val fixture = host()
        try {
            val binding = PhotoItemGridBinding.inflate(fixture.activity.layoutInflater)
            layout(binding.root, 240, 240)
            val thumb = binding.mediumThumbnail
            val token = Any()
            val drawable = ColorDrawable(Color.RED)
            thumb.setImageDrawable(drawable)
            thumb.setTag(R.id.thumbnail_request_key, token)
            val nameHeight = binding.mediumName.layoutParams.height
            OxygenSelectionMotion.bind(
                listOf(thumb), binding.mediumCheck, "a.jpg", false, false,
                inSelectionMode = true, backdrop = binding.root,
                accent = ACCENT, contrast = Color.WHITE,
            )
            assertEquals(View.VISIBLE, binding.mediumCheck.visibility)
            assertTrue(binding.mediumCheck.alpha in 0.2f..0.6f)
            OxygenSelectionMotion.bind(
                listOf(thumb), binding.mediumCheck, "a.jpg", true, true,
                inSelectionMode = true, backdrop = binding.root,
                accent = ACCENT, contrast = Color.WHITE,
            )
            val running = OxygenSelectionMotion.animator(thumb)!!
            assertEquals(OxygenClassicMotion.SELECTION_CONTENT_MS, running.duration)
            running.setCurrentFraction(0.08f)
            val scale = thumb.scaleX
            assertTrue(scale > OxygenSelectionMotion.CONTENT_SCALE)
            assertEquals(1f, binding.root.scaleX, 0f)
            assertEquals(1f, binding.mediumName.scaleX, 0f)
            assertTrue(binding.mediumCheck.alpha > 0f)
            OxygenSelectionMotion.bind(
                listOf(thumb), binding.mediumCheck, "a.jpg", true, true,
                inSelectionMode = true, backdrop = binding.root,
            )
            assertSame(running, OxygenSelectionMotion.animator(thumb))
            assertEquals(scale, thumb.scaleX, 0f)
            running.setCurrentFraction(1f)
            assertEquals(OxygenSelectionMotion.CONTENT_SCALE, thumb.scaleX, 0.001f)
            assertEquals(thumb.scaleX, thumb.scaleY, 0f)
            assertEquals(View.VISIBLE, binding.mediumCheck.visibility)
            assertEquals(1f, binding.mediumCheck.alpha, 0.001f)
            assertEquals(1f, binding.mediumCheck.scaleX, 0.001f)
            val inset = thumb.width * (1f - OxygenSelectionMotion.CONTENT_SCALE) / 2f
            assertEquals(inset, binding.mediumCheck.translationX, 0.5f)
            assertEquals(inset, binding.mediumCheck.translationY, 0.5f)
            assertEquals(OxygenSelectionMotion.CELL_BACKDROP, (binding.root.background as ColorDrawable).color)
            assertSame(token, thumb.getTag(R.id.thumbnail_request_key))
            assertSame(drawable, thumb.drawable)
            assertEquals(nameHeight, binding.mediumName.layoutParams.height)
            assertEquals(1f, binding.root.scaleX, 0f)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun listScalesOnlyTheThumbnail() {
        val fixture = host()
        try {
            val binding = PhotoItemListBinding.inflate(fixture.activity.layoutInflater)
            layout(binding.root, 600, 0)
            val row = binding.root
            OxygenSelectionMotion.bind(
                listOf(binding.mediumThumbnail), binding.mediumCheck, "a.jpg", false, false,
                inSelectionMode = true,
            )
            OxygenSelectionMotion.bind(
                listOf(binding.mediumThumbnail), binding.mediumCheck, "a.jpg", true, true,
                inSelectionMode = true,
            )
            val running = OxygenSelectionMotion.animator(binding.mediumThumbnail)!!
            running.setCurrentFraction(1f)
            assertEquals(OxygenSelectionMotion.CONTENT_SCALE, binding.mediumThumbnail.scaleX, 0.001f)
            assertEquals(1f, row.scaleX, 0f)
            assertEquals(1f, row.scaleY, 0f)
            assertEquals(1f, binding.mediumName.scaleX, 0f)
            assertEquals(View.VISIBLE, binding.mediumCheck.visibility)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun hiddenAndRecycledCellsSnapAndSettleRestoresSourceScale() {
        val fixture = host()
        try {
            val binding = PhotoItemGridBinding.inflate(fixture.activity.layoutInflater)
            fixture.activity.setContentView(binding.root)
            layout(binding.root, 240, 240)
            val thumb = binding.mediumThumbnail
            thumb.setImageDrawable(ColorDrawable(Color.BLUE))
            OxygenSelectionMotion.bind(listOf(thumb), binding.mediumCheck, "a.jpg", false, false)
            OxygenSelectionMotion.bind(listOf(thumb), binding.mediumCheck, "a.jpg", true, false)
            assertNull(OxygenSelectionMotion.animator(thumb))
            assertEquals(OxygenSelectionMotion.CONTENT_SCALE, thumb.scaleX, 0.001f)

            OxygenSelectionMotion.release(thumb)
            assertEquals(1f, thumb.scaleX, 0f)
            assertEquals(View.GONE, binding.mediumCheck.visibility)
            OxygenSelectionMotion.bind(listOf(thumb), binding.mediumCheck, "b.jpg", true, true)
            assertNull(OxygenSelectionMotion.animator(thumb))
            assertEquals(OxygenSelectionMotion.CONTENT_SCALE, thumb.scaleX, 0.001f)

            OxygenSelectionMotion.bind(listOf(thumb), binding.mediumCheck, "b.jpg", false, true)
            val leaving = OxygenSelectionMotion.animator(thumb)!!
            leaving.setCurrentFraction(0.4f)
            assertTrue(scaleOf(thumb) < 1f)
            OxygenSelectionMotion.settleForSource(thumb)
            assertEquals(1f, thumb.scaleX, 0f)
            assertEquals(1f, thumb.scaleY, 0f)
            assertEquals(1f, scaleOf(thumb), 0.001f)
            assertNull(OxygenSelectionMotion.animator(thumb))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun disabledSelectionMotionSnapsTheThumbnail() {
        val fixture = host()
        val prior = animatorScale(fixture.activity)
        Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val thumb = ImageView(fixture.activity)
            val check = ImageView(fixture.activity)
            OxygenSelectionMotion.bind(listOf(thumb), check, "a.jpg", false, true)
            OxygenSelectionMotion.bind(listOf(thumb), check, "a.jpg", true, true)
            assertNull(OxygenSelectionMotion.animator(thumb))
            assertEquals(OxygenSelectionMotion.CONTENT_SCALE, thumb.scaleX, 0f)
            assertEquals(View.VISIBLE, check.visibility)
        } finally {
            Settings.Global.putFloat(fixture.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            fixture.close()
        }
    }

    @Test
    fun onScreenRequiresAVisibleLaidOutCell() {
        val fixture = host()
        try {
            val image = ImageView(fixture.activity)
            image.layoutParams = FrameLayout.LayoutParams(80, 80)
            fixture.activity.setContentView(image)
            layout(fixture.activity.window.decorView, 400, 800)
            assertTrue(OxygenSelectionMotion.onScreen(image))
            image.visibility = View.INVISIBLE
            assertTrue(!OxygenSelectionMotion.onScreen(image))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun swipeBindUsesTheShorterDuration() {
        val fixture = host()
        try {
            val thumb = ImageView(fixture.activity)
            val check = ImageView(fixture.activity)
            layout(thumb, 120, 120)
            layout(check, 24, 24)
            OxygenSelectionMotion.bind(listOf(thumb), check, "a.jpg", false, false)
            OxygenSelectionMotion.bind(listOf(thumb), check, "a.jpg", true, true, brief = true)
            val running = OxygenSelectionMotion.animator(thumb)!!
            assertEquals(OxygenClassicMotion.SELECTION_SWIPE_MS, running.duration)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun dragRectangleSelectsTheBoundingBoxNotJustThePath() {
        // 2-column grid: from 0 through 1 to 3 must include 2 as well.
        assertEquals(setOf(0, 1, 2, 3), OxygenSelectionDrag.rectPositions(0, 3, spanCount = 2, itemCount = 8))
        assertEquals(setOf(0, 1), OxygenSelectionDrag.rectPositions(0, 1, spanCount = 2, itemCount = 8))
        assertEquals(setOf(1, 3), OxygenSelectionDrag.rectPositions(1, 3, spanCount = 2, itemCount = 8))
        assertEquals(setOf(0, 1, 2, 3, 4, 5), OxygenSelectionDrag.rectPositions(5, 0, spanCount = 2, itemCount = 8))
    }

    @Test
    fun dragGestureUsesFixedModeAndIgnoresAStationaryFinger() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.GalleryPageTransitionTheme)
        controller.setup()
        try {
            val selected = linkedSetOf<Int>()
            val recycler = org.fossify.commons.views.MyRecyclerView(activity)
            recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(activity, 2)
            recycler.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
                    object : androidx.recyclerview.widget.RecyclerView.ViewHolder(View(parent.context).also {
                        it.layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(40, 40)
                    }) {}
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) = Unit
                override fun getItemCount() = 8
            }
            layout(recycler, 200, 400)
            var lastToggle: Pair<Boolean, Int>? = null
            OxygenSelectionDrag.install(
                recyclerView = recycler,
                positionOffset = { 0 },
                isSelectable = { true },
                isSelected = { selected.contains(it) },
                toggle = { select, pos ->
                    lastToggle = select to pos
                    if (select) selected.add(pos) else selected.remove(pos)
                },
            )
            val listenerField = org.fossify.commons.views.MyRecyclerView::class.java
                .getDeclaredField("dragListener").apply { isAccessible = true }
            val listener = listenerField.get(recycler) as org.fossify.commons.views.MyRecyclerView.MyDragListener

            // ACTION_DOWN on unselected cell 0 → select mode.
            recycler.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
            listener.selectItem(0)
            assertTrue(selected.contains(0))
            listener.selectRange(0, 3, 0, 3)
            assertTrue(selected.containsAll(listOf(0, 1, 2, 3)))
            listener.selectRange(0, 3, 0, 3)
            assertEquals(true to 3, lastToggle)
            // Shrink back toward 1: 2 and 3 leave the rect and restore unselected.
            listener.selectRange(0, 1, 0, 3)
            assertTrue(selected.containsAll(listOf(0, 1)))
            assertFalse(selected.contains(2))
            assertFalse(selected.contains(3))

            // New gesture on an already-selected cell → deselect mode.
            selected.addAll(listOf(0, 1, 2, 3))
            recycler.dispatchTouchEvent(MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
            // findChildViewUnder may miss without real children laid out; seed down via selectItem path
            // by pretending down snapshot through selecting a selected cell with empty down.
            // Force deselect by having selected state at selectItem when down missed:
            listener.selectItem(0)
            // Without a matching down snapshot, begin uses isSelected(0)=true → deselect mode.
            assertFalse(selected.contains(0))
            listener.selectRange(0, 3, 0, 3)
            assertTrue(selected.none { it in 0..3 })
        } finally {
            controller.pause().stop().destroy()
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

    private fun scaleOf(view: View): Float {
        val matrix = StaticPhotoViewGeometry.localToScreen(view)
        return FloatArray(9).also(matrix::getValues)[Matrix.MSCALE_X]
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
