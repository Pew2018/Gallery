package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
    @Test fun outgoingSnapshotFadeAndSourceOverRevealAreContinuous() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val host = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val page = View(activity).apply { setBackgroundColor(Color.RED) }
        host.addView(page, FrameLayout.LayoutParams(120, 120))
        activity.windowManager.addView(host, android.view.WindowManager.LayoutParams(120, 120))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            32, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue("transition fixture must be attached to a window", host.isAttachedToWindow)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, 120, 120)
        var titleCommitCount = 0
        ClassicSettingsPageTransition.run(host, page, 1, true, {
            page.setBackgroundColor(Color.BLUE)
        }, onTitleCommit = { titleCommitCount++ })
        assertEquals("Outgoing title/content stay in sync at transition start", 0, titleCommitCount)
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("Snapshot captures the outgoing page before target preparation", Color.RED,
            ClassicSettingsPageTransition.snapshotPixelForTest(host, 60, 60))

        val redLevels = mutableListOf<Int>()
        val blueLevels = mutableListOf<Int>()
        for (progress in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            ClassicSettingsPageTransition.applyProgressForTest(host, progress)
            assertEquals("Snapshot alpha should follow the same progress frame",
                ((1f - progress) * 255f).toInt(),
                ClassicSettingsPageTransition.snapshotAlphaForTest(host))
            // Robolectric View.draw() does not render ViewOverlay. Verify the actual
            // captured source pixel and its per-frame alpha, then calculate source-over
            // output against the known opaque target surface at the same sampled frames.
            val source = ClassicSettingsPageTransition.snapshotPixelForTest(host, 60, 60)
            assertEquals("The outgoing snapshot source remains the original page", Color.RED, source)
            val alpha = ClassicSettingsPageTransition.snapshotAlphaForTest(host)!! / 255f
            redLevels += (Color.red(source!!) * alpha).toInt()
            blueLevels += (Color.blue(Color.BLUE) * (1f - alpha)).toInt()
            if (progress < 0.78f) {
                assertEquals("Keep the toolbar title with the outgoing page until its exit is nearly complete",
                    0, titleCommitCount)
            }
        }
        assertTrue("Outgoing content should steadily reveal the target page: $redLevels", redLevels.zipWithNext().all { it.first >= it.second })
        assertTrue("Incoming page should become progressively visible: $blueLevels", blueLevels.zipWithNext().all { it.first <= it.second })
        assertTrue("Intermediate frames must contain both pages (red=$redLevels, blue=$blueLevels)",
            redLevels.any { it in 1..254 } && blueLevels.any { it in 1..254 })
        ClassicSettingsPageTransition.applyProgressForTest(host, 1f)
        assertEquals(Color.BLUE, (page.background as ColorDrawable).color)
        assertEquals("Toolbar title is committed near the end of the outgoing frame", 1, titleCommitCount)
        ClassicSettingsPageTransition.cancel(host, page)
        assertEquals(1f, page.alpha, 0f)
        assertEquals(0f, page.translationX, 0f)
        activity.windowManager.removeViewImmediate(host)
        controller.pause().stop().destroy()
    }

    @Test fun interactiveStepFollowsTheFingerAndCancelRestoresTheSamePage() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val host = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val page = View(activity).apply { setBackgroundColor(Color.RED) }
        host.addView(page, FrameLayout.LayoutParams(120, 120))
        activity.windowManager.addView(host, android.view.WindowManager.LayoutParams(120, 120))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            32, java.util.concurrent.TimeUnit.MILLISECONDS)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, 120, 120)

        var prepared = 0
        var restored = 0
        var titleCommits = 0
        val step = ClassicSettingsPageTransition.begin(
            host, page, 1, true,
            prepareTarget = { prepared++; page.setBackgroundColor(Color.BLUE) },
            onTitleCommit = { titleCommits++ }
        )
        assertTrue("An animated page must expose an interactive step", step != null)
        assertEquals("The destination is rendered as soon as the gesture starts", 1, prepared)
        assertEquals("The snapshot must be taken before the page is replaced", Color.RED,
            ClassicSettingsPageTransition.snapshotPixelForTest(host, 60, 60))

        for (progress in listOf(0.2f, 0.45f, 0.7f)) {
            step!!.render(progress)
            assertEquals("The page must fade exactly with the gesture progress",
                ((1f - progress) * 255f).toInt(),
                ClassicSettingsPageTransition.snapshotAlphaForTest(host))
        }
        assertTrue("The title must not commit while the gesture is still reversible", titleCommits == 0)

        step!!.render(0f)
        step.cancel { restored++ }
        assertEquals("Cancel must restore the caller's state", 1, restored)
        host.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("The snapshot must leave once the restored page is laid out", null,
            ClassicSettingsPageTransition.snapshotAlphaForTest(host))
        assertEquals(0f, page.translationX, 0f)
        assertEquals(1f, page.alpha, 0f)
        activity.windowManager.removeViewImmediate(host)
        controller.pause().stop().destroy()
    }

    @Test fun interactiveStepCommitsTheDestinationTitleAtRelease() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val host = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val page = View(activity).apply { setBackgroundColor(Color.RED) }
        host.addView(page, FrameLayout.LayoutParams(120, 120))
        activity.windowManager.addView(host, android.view.WindowManager.LayoutParams(120, 120))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
            32, java.util.concurrent.TimeUnit.MILLISECONDS)
        host.measure(
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, 120, 120)

        var finished = 0
        var titleCommits = 0
        val step = ClassicSettingsPageTransition.begin(
            host, page, 1, true,
            prepareTarget = { page.setBackgroundColor(Color.BLUE) },
            onTitleCommit = { titleCommits++ }
        )!!
        step.render(1f)
        step.commit { finished++ }
        assertEquals("Release must complete the step", 1, finished)
        assertEquals("The destination title commits with the step", 1, titleCommits)
        assertEquals("The snapshot must be gone after a full commit", null,
            ClassicSettingsPageTransition.snapshotAlphaForTest(host))
        assertEquals(Color.BLUE, (page.background as ColorDrawable).color)
        activity.windowManager.removeViewImmediate(host)
        controller.pause().stop().destroy()
    }

    @Test fun interactiveStepIsUnavailableWhenMotionIsOff() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val host = FrameLayout(activity)
        val page = View(activity)
        host.addView(page)
        val step = ClassicSettingsPageTransition.begin(
            host, page, 1, false, prepareTarget = { throw AssertionError("must not prepare") })
        assertEquals("Without motion the page must fall back to the plain path", null, step)
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
