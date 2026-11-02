package org.fossify.gallery.helpers

import android.app.Application
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
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
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
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
        var obsolete = 0
        var current = 0
        val old: () -> Unit = { obsolete++ }
        val next: () -> Unit = { current++ }
        try {
            transition.invoke(activity, true, old)
            repeat(2) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            transition.invoke(activity, false, next)
            repeat(20) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            assertEquals(0, obsolete)
            assertEquals(1, current)
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            transition.invoke(activity, true, old)
            controller.pause().stop().destroy()
            repeat(20) { clock.idleFor(16, java.util.concurrent.TimeUnit.MILLISECONDS) }
            assertEquals(0, obsolete)
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }

}
