package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.fossify.gallery.views.TiebaSettingsContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class ClassicSettingsPageTransitionTest {
    @Test fun renderedOutgoingSnapshotFadesContinuouslyOverOpaqueIncomingPage() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val host = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val page = View(activity).apply { setBackgroundColor(Color.RED) }
        host.addView(page, FrameLayout.LayoutParams(120, 120))
        activity.setContentView(host)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, 120, 120)
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        var titleCommitCount = 0
        ClassicSettingsPageTransition.run(host, page, 1, true, {
            page.setBackgroundColor(Color.BLUE)
        }, onTitleCommit = { titleCommitCount++ })
        assertEquals("Outgoing title/content stay in sync at transition start", 0, titleCommitCount)
        host.viewTreeObserver.dispatchOnPreDraw()

        val redLevels = mutableListOf<Int>()
        val blueLevels = mutableListOf<Int>()
        var elapsed = 0L
        for (timeMs in listOf(16L, 48L, 80L, 112L, 144L)) {
            clock.idleFor(timeMs - elapsed, java.util.concurrent.TimeUnit.MILLISECONDS)
            elapsed = timeMs
            if (timeMs == 16L) assertEquals("Do not update the toolbar while the outgoing frame is opaque", 0, titleCommitCount)
            val frame = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(frame))
            val pixel = frame.getPixel(60, 60)
            redLevels += Color.red(pixel)
            blueLevels += Color.blue(pixel)
            frame.recycle()
        }
        clock.idleFor(80, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue("Outgoing content should steadily reveal the target page: $redLevels", redLevels.zipWithNext().all { it.first >= it.second })
        assertTrue("Incoming page should become progressively visible: $blueLevels", blueLevels.zipWithNext().all { it.first <= it.second })
        assertTrue("Intermediate frames must contain both pages", redLevels.any { it in 1..254 } && blueLevels.any { it in 1..254 })
        assertEquals(Color.BLUE, drawCenter(host))
        assertEquals("Toolbar title is committed near the end of the outgoing frame", 1, titleCommitCount)
        ClassicSettingsPageTransition.cancel(host, page)
        assertEquals(1f, page.alpha, 0f)
        assertEquals(0f, page.translationX, 0f)
        controller.pause().stop().destroy()
    }

    private fun drawCenter(view: View): Int {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(60, 60).also { bitmap.recycle() }
    }

    @Test fun listDividerUsesInsetRowBoundaryAndSkipsSectionBoundaries() {
        val activity = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).setup().get()
        val list = TiebaSettingsContainer(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setSettingsDividerColor(0xFFE6E6E6.toInt())
        }
        fun row(tagValue: String? = null) = TextView(activity).apply {
            tag = tagValue
            setBackgroundColor(Color.WHITE)
        }
        val first = row()
        val hidden = row().apply { visibility = View.GONE }
        val second = row()
        val section = row("classic_section")
        list.addView(first, android.widget.LinearLayout.LayoutParams(-1, 56))
        list.addView(hidden, android.widget.LinearLayout.LayoutParams(-1, 56))
        list.addView(second, android.widget.LinearLayout.LayoutParams(-1, 56))
        list.addView(section, android.widget.LinearLayout.LayoutParams(-1, 48))
        list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.AT_MOST))
        list.layout(0, 0, 320, list.measuredHeight)

        assertTrue(list.shouldDrawDividerBetween(first, second))
        assertEquals(first.bottom - 0.5f, list.dividerCenterY(first), 0f)
        assertEquals(16f * activity.resources.displayMetrics.density, list.dividerStartX(), 0f)
        assertEquals(list.width - 16f * activity.resources.displayMetrics.density, list.dividerEndX(), 0f)
        assertFalse(list.shouldDrawDividerBetween(second, section))
        activity.finish()
    }
}
