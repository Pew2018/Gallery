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
class SettingsPageMotionTest {
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
}
