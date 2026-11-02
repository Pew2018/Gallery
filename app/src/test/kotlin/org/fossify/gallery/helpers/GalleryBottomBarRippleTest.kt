package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class GalleryBottomBarRippleTest {

    private fun fixture(active: GalleryBottomBar.Tab = GalleryBottomBar.Tab.ALL,
                        onNavigate: (GalleryBottomBar.Tab) -> Unit = {}):
        Pair<android.app.Activity, LinearLayout> {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.config.oxygenThemeActivated = true
        activity.config.oxygenThemePresetId = "custom"
        activity.config.oxygenAppearanceMode = "light"
        activity.config.oxygenCustomAccent = Color.rgb(0, 113, 205)
        activity.config.bottomNavigationOrder = listOf("ALL", "FAVORITES", "PHOTOS")
        val root = CoordinatorLayout(activity)
        activity.setContentView(root)
        GalleryBottomBar.attach(activity, root, active, navigate = onNavigate)
        return activity to root.findViewWithTag("oxygen_classic_bottom_bar")
    }

    private fun items(bar: LinearLayout) = (0 until bar.childCount)
        .map { bar.getChildAt(it) as LinearLayout }

    @Test fun recolorPreservesAllThreeRippleInstancesAcrossIdenticalAndChangedThemes() {
        val (activity, bar) = fixture()
        val tabs = items(bar)
        val ripples = tabs.map { it.background as RippleDrawable }
        val firstColor = GalleryBottomBar.currentRippleColor(tabs[0])
        tabs[1].isPressed = true
        repeat(4) { GalleryBottomBar.recolor(activity) }
        assertTrue(tabs.indices.all { ripples[it] === tabs[it].background })
        activity.config.oxygenCustomAccent = Color.rgb(215, 55, 95)
        GalleryBottomBar.recolor(activity)
        assertTrue(tabs.indices.all { ripples[it] === tabs[it].background })
        val secondColor = GalleryBottomBar.currentRippleColor(tabs[0])
        assertNotEquals(firstColor, secondColor)
        assertTrue(tabs.all { GalleryBottomBar.currentRippleColor(it) == secondColor })
        assertTrue(tabs[1].isPressed)
        tabs[1].isPressed = false
        GalleryBottomBar.recolor(activity)
        assertSame(ripples[1], tabs[1].background)
        activity.finish()
    }

    @Test fun rapidTapsOnlyNavigateOnceAndSelectionUsesClassicColorAndTypeface() {
        val visited = mutableListOf<GalleryBottomBar.Tab>()
        val (activity, bar) = fixture(onNavigate = { visited.add(it) })
        val tabs = items(bar)
        assertEquals(3, tabs.size)
        assertTrue(tabs[0].isSelected)
        val density = activity.resources.displayMetrics.density
        assertEquals(56f,
            activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height) / density, 0.1f)
        assertEquals(24f,
            activity.resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_icon_size) / density, 0.1f)
        assertEquals(12f, (tabs[0].getChildAt(1) as TextView).textSize /
            activity.resources.displayMetrics.scaledDensity, 0.01f)
        tabs[1].performClick()
        tabs[2].performClick()
        tabs[1].performClick()
        assertEquals(listOf(GalleryBottomBar.Tab.FAVORITES), visited)
        assertFalse(tabs[0].isSelected)
        assertTrue(tabs[1].isSelected)
        assertFalse(tabs[2].isSelected)
        // Medium (500) is not Typeface.BOLD (700); test the actual family.
        assertEquals(Typeface.create("sans-serif-medium", Typeface.NORMAL),
            (tabs[1].getChildAt(1) as TextView).typeface)
        assertEquals(Typeface.create("sans-serif", Typeface.NORMAL),
            (tabs[0].getChildAt(1) as TextView).typeface)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        assertEquals(colors.interactiveAccent, (tabs[1].getChildAt(0) as ImageView).imageTintList!!.defaultColor)
        assertEquals(colors.secondaryText, (tabs[0].getChildAt(1) as TextView).currentTextColor)
        activity.finish()
    }

    @Test fun navigationStartsNowButFinishesOutgoingActivityAfterWindowHold() {
        val (activity, _) = fixture()
        GalleryBottomBar.open(activity, Intent(activity, android.app.Activity::class.java), finishCurrent = true)
        assertNotNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertFalse("navigation must not finish the ripple host on the click frame", activity.isFinishing)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(160, TimeUnit.MILLISECONDS)
        assertFalse("no first-frame acknowledgement yet", activity.isFinishing)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            GalleryBottomBar.ROOT_FINISH_FALLBACK_MS - 160L, TimeUnit.MILLISECONDS)
        assertTrue("unresponsive target must not retain source indefinitely", activity.isFinishing)
    }

    @Test fun unchangedOrderRefreshDoesNotDetachItemsOrResetRipple() {
        val (activity, bar) = fixture()
        val tabs = items(bar)
        val backgrounds = tabs.map { it.background }
        GalleryBottomBar.refreshAll()
        assertEquals(tabs, items(bar))
        assertTrue(items(bar).indices.all { items(bar)[it].background === backgrounds[it] })
        activity.finish()
    }

    @Test fun actualDownUpKeepsRippleAndAnimationsOffSnapsSelectedTint() {
        var visits = 0
        val (activity, bar) = fixture(onNavigate = { visits++ })
        val tabs = items(bar)
        val item = tabs[1]
        val ripple = item.background
        val resolver = activity.contentResolver
        val prior = android.provider.Settings.Global.getFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        android.provider.Settings.Global.putFloat(resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            // setContentView/addView after Activity.setup needs a traversal before
            // delayed touch callbacks have an attached View handler.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("touch host must be attached", item.isAttachedToWindow)
            item.layout(0, 0, 100, 56)
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 50f, 28f, 0)
            item.dispatchTouchEvent(down)
            down.recycle()
            // ViewGroup may defer pressed feedback until tap timeout.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                android.view.ViewConfiguration.getTapTimeout().toLong() + 1, TimeUnit.MILLISECONDS)
            assertTrue(item.isPressed)
            assertSame(ripple, item.background)
            val up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, 50f, 28f, 0)
            item.dispatchTouchEvent(up)
            up.recycle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, visits)
            assertSame(ripple, item.background)
            assertTrue(item.isSelected)
            val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
            assertEquals(tokens.interactiveAccent, (item.getChildAt(1) as TextView).currentTextColor)
        } finally {
            GalleryBottomBar.dispose(activity)
            android.provider.Settings.Global.putFloat(resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            activity.finish()
        }
    }

}
