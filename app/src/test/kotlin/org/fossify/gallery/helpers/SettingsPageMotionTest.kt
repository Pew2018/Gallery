package org.fossify.gallery.helpers

import android.app.Application
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.activities.OxygenThemeActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class SettingsPageMotionTest {
    @org.junit.Before fun useControlledVsync() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }

    @Test fun animationsOffAppliesPageAndBackImmediatelyWithoutResidualTransforms() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val resolver = app.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
            val activity = controller.get()
            val holder = activity.findViewById<LinearLayout>(R.id.settings_holder)
            val hub = holder.getChildAt(0) as LinearLayout
            val first = hub.getChildAt(0)
            assertTrue(first.performClick())
            val scroll = activity.findViewById<View>(R.id.settings_nested_scrollview)
            val page = activity.findViewById<View>(R.id.settings_holder)
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            assertEquals(1f, page.alpha, 0f)
            assertEquals(0f, page.translationX, 0f)
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            assertEquals(1f, page.alpha, 0f)
            assertEquals(0f, page.translationX, 0f)
            assertEquals(View.VISIBLE, hub.visibility)
            controller.pause().stop().destroy()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }

    @Test fun newerNavigationAndDestroyCancelOldPageCallbacks() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val transition = activity.javaClass.getDeclaredMethod("animateSettingsPageChange",
            Boolean::class.javaPrimitiveType, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
        val scroll = activity.findViewById<View>(R.id.settings_nested_scrollview)
        val page = activity.findViewById<View>(R.id.settings_holder)
        var obsolete = 0
        var current = 0
        val old: () -> Unit = { obsolete++ }
        val next: () -> Unit = { current++ }
        try {
            transition.invoke(activity, true, old)
            repeat(2) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            transition.invoke(activity, false, next)
            repeat(20) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            assertEquals(1, obsolete) // The first page swap is committed synchronously.
            assertEquals(1, current)
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            assertEquals(1f, page.alpha, 0f)
            assertEquals(0f, page.translationX, 0f)
            transition.invoke(activity, true, old)
            controller.pause().stop().destroy()
            repeat(20) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            assertEquals(2, obsolete) // Destroy cancels motion after the synchronous page swap.
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            assertEquals(1f, page.alpha, 0f)
            assertEquals(0f, page.translationX, 0f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }

    @Test fun bothDirectionsAndRtlStayVisibleAndFinishNormalized() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val transition = activity.javaClass.getDeclaredMethod("animateSettingsPageChange",
            Boolean::class.javaPrimitiveType, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
        val scroll = activity.findViewById<View>(R.id.settings_nested_scrollview)
        val page = activity.findViewById<View>(R.id.settings_holder)
        try {
            for (rtl in listOf(false, true)) for (forward in listOf(false, true)) {
                scroll.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                var commits = 0
                val commit: () -> Unit = { commits++ }
                transition.invoke(activity, forward, commit)
                repeat(3) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
                val enterSign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
                assertTrue(page.translationX * enterSign >= 0f)
                assertEquals(1f, page.alpha, 0f)
                repeat(20) {
                    clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS)
                    assertEquals("Page should remain fully visible", 1f, scroll.alpha, 0f)
                }
                assertEquals(1, commits)
                assertEquals(1f, scroll.alpha, 0f)
                assertEquals(0f, scroll.translationX, 0f)
                assertEquals(1f, page.alpha, 0f)
                assertEquals(0f, page.translationX, 0f)
            }
        } finally {
            controller.pause().stop().destroy()
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }

    @Test fun themeColorSubpageKeepsItsIndependentNavigationAndScrollMotion() {
        val controller = Robolectric.buildActivity(OxygenThemeActivity::class.java).setup().visible()
        val activity = controller.get()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val bodyField = activity.javaClass.getDeclaredField("body").apply { isAccessible = true }
        val body = bodyField.get(activity) as View
        val navigate = activity.javaClass.getDeclaredMethod("navigateColors", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        try {
            navigate.invoke(activity, true)
            val scroll = activity.javaClass.getDeclaredField("scroll").apply { isAccessible = true }
                .get(activity) as View
            assertTrue("theme scroll must be attached", scroll.isAttachedToWindow)
            assertTrue("theme scroll must have a laid out viewport", scroll.width > 0 && scroll.height > 0)
            scroll.viewTreeObserver.dispatchOnPreDraw()
            clock.idleFor(48, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue(activity.javaClass.getDeclaredField("moreColors").apply { isAccessible = true }
                .getBoolean(activity))
            assertEquals(1f, body.alpha, 0f)
            assertTrue(body.translationX != 0f)
            clock.idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(0f, body.translationX, 0f)
            assertEquals(1f, body.alpha, 0f)
            navigate.invoke(activity, false)
            clock.idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertFalse(activity.javaClass.getDeclaredField("moreColors").apply { isAccessible = true }
                .getBoolean(activity))
            assertEquals(1f, body.alpha, 0f)
            assertEquals(0f, body.translationX, 0f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            controller.pause().stop().destroy()
        }
    }

}
