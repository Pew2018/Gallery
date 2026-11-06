package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ClassicSettingsActivityTransitionTest {
    private fun fixture(check: (FrameLayout, View) -> Unit) {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val viewport = FrameLayout(activity).apply { setBackgroundColor(Color.WHITE) }
        val content = View(activity).apply { setBackgroundColor(Color.RED) }
        viewport.addView(content, FrameLayout.LayoutParams(120, 120))
        activity.windowManager.addView(viewport, android.view.WindowManager.LayoutParams(120, 120))
        shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
        viewport.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY))
        viewport.layout(0, 0, 120, 120)
        assertTrue(viewport.isAttachedToWindow)
        try { check(viewport, content) } finally {
            ClassicSettingsActivityTransition.clear()
            ClassicSettingsPageTransition.cancel(viewport, content)
            activity.windowManager.removeViewImmediate(viewport)
            controller.pause().stop().destroy()
        }
    }

    @Test fun transferredViewportUsesSameForwardBackRtlFramesAndDelayedTitle() = fixture { viewport, content ->
        for (entry in listOf("theme", "icon")) for (rtl in listOf(false, true)) for (forward in listOf(true, false)) {
            viewport.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            content.setBackgroundColor(Color.RED)
            ClassicSettingsActivityTransition.stage(entry, viewport, "old", forward, true)
            content.setBackgroundColor(Color.BLUE)
            var title: CharSequence = "new"
            assertTrue(ClassicSettingsActivityTransition.receive(entry, viewport, content, true, { title = it }, "new"))
            assertEquals("old", title)
            viewport.viewTreeObserver.dispatchOnPreDraw()
            ClassicSettingsPageTransition.applyProgressForTest(viewport, 0.25f)
            val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
            assertTrue("$entry rtl=$rtl forward=$forward", content.translationX * sign > 0f)
            assertEquals(Color.RED, ClassicSettingsPageTransition.snapshotPixelForTest(viewport, 60, 60))
            assertEquals(1f, content.alpha, 0f)
            assertEquals("old", title)
            ClassicSettingsPageTransition.applyProgressForTest(viewport, 0.9f)
            assertEquals("new", title)
            ClassicSettingsPageTransition.cancel(viewport, content)
            assertEquals(0f, content.translationX, 0f)
            assertNull(ClassicSettingsPageTransition.snapshotAlphaForTest(viewport))
            assertFalse("handoff consumed once", ClassicSettingsActivityTransition.receive(
                entry, viewport, content, true, {}, "new"))
        }
    }

    @Test fun disabledAnimationReplacementExpiryAndDetachReleaseFrames() = fixture { viewport, content ->
        ClassicSettingsActivityTransition.stage("theme", viewport, "old", true, true)
        var title: CharSequence = "old"
        assertTrue(ClassicSettingsActivityTransition.receive("theme", viewport, content, false, { title = it }, "new"))
        assertEquals("new", title)
        assertEquals(1f, content.alpha, 0f)
        assertEquals(0f, content.translationX, 0f)
        assertNull(ClassicSettingsPageTransition.snapshotAlphaForTest(viewport))
        ClassicSettingsActivityTransition.stage("theme", viewport, "old", true, true)
        ClassicSettingsActivityTransition.stage("icon", viewport, "old", true, true)
        assertFalse(ClassicSettingsActivityTransition.receive("theme", viewport, content, true, {}, "new"))
        assertTrue(ClassicSettingsActivityTransition.receive("icon", viewport, content, true, {}, "new"))
        viewport.viewTreeObserver.dispatchOnPreDraw()
        ClassicSettingsPageTransition.applyProgressForTest(viewport, 0.25f)
        // Interrupt a running frame with another navigation, then interrupt before pre-draw.
        ClassicSettingsPageTransition.run(viewport, content, -1, true, {})
        ClassicSettingsPageTransition.cancel(viewport, content)
        viewport.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1f, content.alpha, 0f)
        assertEquals(0f, content.translationX, 0f)
        assertNull(ClassicSettingsPageTransition.snapshotAlphaForTest(viewport))
        ClassicSettingsActivityTransition.stage("theme", viewport, "old", true, true)
        shadowOf(Looper.getMainLooper()).idleFor(5001, TimeUnit.MILLISECONDS)
        assertFalse(ClassicSettingsActivityTransition.receive("theme", viewport, content, true, {}, "new"))
        ClassicSettingsActivityTransition.stage("theme", viewport, "old", true, false)
        assertFalse(ClassicSettingsActivityTransition.receive("theme", viewport, content, true, {}, "new"))
        ClassicSettingsPageTransition.run(viewport, content, 1, true, {})
        viewport.dispatchDetachedFromWindowForTest()
        assertEquals(1f, content.alpha, 0f)
        assertEquals(0f, content.translationX, 0f)
        assertNull(ClassicSettingsPageTransition.snapshotAlphaForTest(viewport))
    }

    private fun View.dispatchDetachedFromWindowForTest() {
        // Use the real attach listener boundary, without removing the fixture twice.
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.removeViewImmediate(this)
        wm.addView(this, android.view.WindowManager.LayoutParams(120, 120))
    }
}
