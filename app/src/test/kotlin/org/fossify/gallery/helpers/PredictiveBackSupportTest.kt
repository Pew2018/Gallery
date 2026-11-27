package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page-transition handover is the difference between a folder return that reveals
 * its parent under the finger and a single-frame cut. Keep the API gate honest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class PredictiveBackSupportTest {
    @Test
    @Config(sdk = [33], manifest = Config.NONE)
    fun api33KeepsTheAppPageAnimation() {
        assertFalse(
            "Android 13 cannot animate a cross-Activity back, so the app window animation stays in charge",
            PredictiveBackSupport.systemDrivesPageTransition
        )
    }

    @Test
    @Config(sdk = [34], manifest = Config.NONE)
    fun api34HandsThePageTransitionToThePlatform() {
        assertTrue(
            "Android 14 animates the closing page itself and must not be shadowed by a legacy finish animation",
            PredictiveBackSupport.systemDrivesPageTransition
        )
    }

    @Test
    @Config(sdk = [35], manifest = Config.NONE)
    fun newerPlatformsKeepTheHandover() {
        assertTrue(PredictiveBackSupport.systemDrivesPageTransition)
    }
}
