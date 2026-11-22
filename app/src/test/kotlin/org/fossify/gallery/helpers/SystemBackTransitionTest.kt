package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the transition-override lifecycle, not the gesture visuals: Robolectric
 * cannot reproduce WindowManager/Shell compositing. These tests assert the app never
 * leaves a persistent zero-animation override on a page the platform must animate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SystemBackTransitionTest {
    class RecordingActivity : Activity() {
        val cleared = mutableListOf<Int>()
        val overrides = mutableListOf<Triple<Int, Int, Int>>()
        val pendingOverrides = mutableListOf<Pair<Int, Int>>()
        val plainStarts = mutableListOf<Intent>()
        val optionStarts = mutableListOf<Pair<Intent, Bundle?>>()

        override fun clearOverrideActivityTransition(overrideType: Int) {
            cleared += overrideType
            super.clearOverrideActivityTransition(overrideType)
        }

        override fun overrideActivityTransition(overrideType: Int, enterAnim: Int, exitAnim: Int) {
            overrides += Triple(overrideType, enterAnim, exitAnim)
            super.overrideActivityTransition(overrideType, enterAnim, exitAnim)
        }

        @Suppress("DEPRECATION")
        override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
            pendingOverrides += enterAnim to exitAnim
        }

        override fun startActivity(intent: Intent) {
            plainStarts += intent
        }

        override fun startActivity(intent: Intent, options: Bundle?) {
            optionStarts += intent to options
        }
    }

    private fun recording(): RecordingActivity =
        Robolectric.buildActivity(RecordingActivity::class.java).setup().get()

    @Test
    @Config(sdk = [36])
    fun android16FreesBothOverrideSlotsForThePlatform() {
        val activity = recording()
        PredictiveBackSupport.clearPageTransitionOverrides(activity)
        assertEquals(listOf(Activity.OVERRIDE_TRANSITION_OPEN, Activity.OVERRIDE_TRANSITION_CLOSE), activity.cleared)
        assertTrue("clearing must not install another zero animation", activity.overrides.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun legacyPlatformDoesNotUseTheOverrideApi() {
        val activity = recording()
        PredictiveBackSupport.clearPageTransitionOverrides(activity)
        assertTrue(activity.cleared.isEmpty())
        assertTrue(activity.overrides.isEmpty())
    }

    @Test
    @Config(sdk = [36])
    fun ordinaryAndroid16PageLaunchHasNoCustomAnimationBundle() {
        val activity = recording()
        val intent = Intent(activity, RecordingActivity::class.java)
        PredictiveBackSupport.startPage(activity, intent, Bundle.EMPTY)
        assertEquals(listOf(intent), activity.plainStarts)
        assertTrue(activity.optionStarts.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun legacyPageLaunchKeepsItsSnapshotFadeSuppression() {
        val activity = recording()
        val intent = Intent(activity, RecordingActivity::class.java)
        val options = Bundle.EMPTY
        PredictiveBackSupport.startPage(activity, intent, options)
        assertTrue(activity.plainStarts.isEmpty())
        assertEquals(1, activity.optionStarts.size)
        assertTrue("legacy options must be preserved", activity.optionStarts.single().second === options)
    }

    @Test
    @Config(sdk = [36])
    fun settingsThemeHandoffDoesNotLeaveAnOpenOverrideOnAndroid16() {
        val activity = recording()
        ClassicSettingsActivityTransition.suppressWindowAnimation(activity)
        assertTrue("no persistent OPEN/CLOSE override may be installed", activity.overrides.isEmpty())
        assertEquals(listOf(Activity.OVERRIDE_TRANSITION_OPEN, Activity.OVERRIDE_TRANSITION_CLOSE), activity.cleared)
        assertTrue(activity.pendingOverrides.isEmpty())
    }

    @Test
    @Config(sdk = [33])
    fun settingsThemeHandoffUsesOneShotAnimationBelowAndroid14() {
        val activity = recording()
        ClassicSettingsActivityTransition.suppressWindowAnimation(activity)
        assertTrue(activity.overrides.isEmpty())
        assertEquals(listOf(0 to 0), activity.pendingOverrides)
    }

    @Test
    @Config(sdk = [36], manifest = Config.NONE)
    fun rootHandoffDoesNotHoldOverridesAfterFirstDrawOrResume() {
        val target = recording()
        val root = CoordinatorLayout(target)
        target.setContentView(root)
        GalleryBottomBar.prepareRootSurface(target, root)
        GalleryBottomBar.restoreRootWindow(target)
        assertEquals("root handoff must be transient on Android 16", 0, target.overrides.size)
        assertEquals(0, target.pendingOverrides.size)
    }
}
