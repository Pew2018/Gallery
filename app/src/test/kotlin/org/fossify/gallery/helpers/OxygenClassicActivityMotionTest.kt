package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class OxygenClassicActivityMotionTest {
    @Test fun activityWindowReceivesTheSharedDirectionalTransitionStyle() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        OxygenClassicActivityMotion.install(activity)
        assertEquals(R.style.OxygenClassicPageWindow, activity.window.attributes.windowAnimations)
        assertEquals(225, activity.resources.getInteger(R.integer.oxygen_classic_page_enter_duration_ms))
        assertEquals(195, activity.resources.getInteger(R.integer.oxygen_classic_page_exit_duration_ms))
        activity.finish()
    }
}
