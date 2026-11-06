package org.fossify.gallery.helpers

import android.app.Application
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ClassicSettingsPageTransitionTest {
    @Test fun transitionKeepsOldFrameUntilNewPageIsPreparedThenNormalizesAtCompletion() {
        val activity = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).setup().get()
        val host = FrameLayout(activity)
        val content = FrameLayout(activity)
        val old = TextView(activity).apply { text = "old" }
        content.addView(old)
        host.addView(content)
        activity.setContentView(host)
        host.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 320, 480)

        var prepared = false
        ClassicSettingsPageTransition.run(host, content, 1, true, prepareTarget = {
            old.text = "new"
            prepared = true
        })
        assertTrue(prepared)
        assertTrue(content.translationX > 0f)
        assertEquals(ClassicSettingsPageTransition.PAGE_ALPHA_FLOOR, content.alpha, 0.001f)

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            ClassicSettingsPageTransition.DURATION_MS + 32, TimeUnit.MILLISECONDS)
        assertEquals(0f, content.translationX, 0f)
        assertEquals(1f, content.alpha, 0f)
        activity.finish()
    }

    @Test fun listDividerUsesInsetRowBoundaryAndSkipsSectionBoundaries() {
        val activity = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java).setup().get()
        val list = TiebaSettingsContainer(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setSettingsDividerColor(0xFFE6E6E6.toInt())
        }
        fun row(tagValue: String? = null) = TextView(activity).apply {
            tag = tagValue
            setBackgroundColor(android.graphics.Color.WHITE)
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
