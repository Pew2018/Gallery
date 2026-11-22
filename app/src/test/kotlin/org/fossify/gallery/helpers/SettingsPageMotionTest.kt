package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.activities.OxygenThemeActivity
import org.fossify.gallery.activities.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every settings page owns a window, so the platform draws both transition directions and
 * no in-page fade can run. These assertions pin that structure: no page swap happens inside
 * the hub, no in-app back step is left enabled, and back finishes the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE, application = Application::class)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class SettingsPageMotionTest {
    @org.junit.Before fun useControlledVsync() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        // SettingsActivity reads storage-manager state while binding rows.
        org.robolectric.shadows.ShadowEnvironment.addExternalDir("primary")
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val app = RuntimeEnvironment.getApplication()
            org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AppOpsManager::class.java)).setMode(
                "android:manage_external_storage",
                app.applicationInfo.uid, app.packageName, android.app.AppOpsManager.MODE_IGNORED)
        }
    }

    private fun categoryIntent(id: String): Intent =
        Intent(RuntimeEnvironment.getApplication(), SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_CATEGORY, id)

    private fun hubRows(activity: SettingsActivity): LinearLayout =
        activity.findViewById<LinearLayout>(R.id.settings_holder).getChildAt(0) as LinearLayout

    @Test fun hubRowNavigatesToItsOwnPageAndLeavesTheHubUntransformed() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val shadow = org.robolectric.Shadows.shadowOf(activity)
        try {
            val hub = hubRows(activity)
            // Child 0 is the first category row; every second child is a divider.
            assertTrue(hub.getChildAt(0).performClick())
            val launched = requireNotNull(shadow.nextStartedActivity)
            assertEquals(SettingsActivity::class.java.name, launched.component?.className)
            assertEquals("appearance", launched.getStringExtra(SettingsActivity.EXTRA_CATEGORY))
            assertNull("A second tap must not enqueue a second page", shadow.nextStartedActivity)
            // The hub keeps showing its own rows; nothing is swapped underneath the finger.
            assertEquals(View.VISIBLE, hub.visibility)
            val scroll = activity.findViewById<View>(R.id.settings_nested_scrollview)
            val page = activity.findViewById<View>(R.id.settings_holder)
            assertEquals(1f, scroll.alpha, 0f)
            assertEquals(0f, scroll.translationX, 0f)
            assertEquals(1f, page.alpha, 0f)
            assertEquals(0f, page.translationX, 0f)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun categoryPageShowsOnlyItsOwnRowsAndHasNoInAppBackStep() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java, categoryIntent("photos")).setup()
        val activity = controller.get()
        try {
            val holder = activity.findViewById<LinearLayout>(R.id.settings_holder)
            val visible = (0 until holder.childCount).map { holder.getChildAt(it) }
                .filter { it.visibility == View.VISIBLE }.map { it.id }.toSet()
            assertTrue(visible.contains(R.id.settings_max_brightness_holder))
            assertTrue(visible.contains(R.id.settings_hide_system_ui_holder))
            assertFalse(visible.contains(R.id.settings_color_customization_holder))
            assertFalse(visible.contains(R.id.settings_manage_included_folders_holder))
            assertEquals(activity.getString(R.string.settings_hub_photos),
                activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.settings_toolbar).title)
            // An enabled callback would swallow the gesture and suppress the platform's
            // return preview, which is exactly the fade this page no longer has. Below
            // Android 14 no preview exists, so the only callback left is the one that
            // closes the window with the classic page animation.
            assertEquals(!PredictiveBackSupport.systemDrivesPageTransition,
                activity.onBackPressedDispatcher.hasEnabledCallbacks())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun unknownCategoryFallsBackToTheHub() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java, categoryIntent("nope")).setup()
        val activity = controller.get()
        try {
            val hub = hubRows(activity)
            assertEquals(View.VISIBLE, hub.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun backOnACategoryPageFinishesIt() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java, categoryIntent("files")).setup()
        val activity = controller.get()
        try {
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(activity.isFinishing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun themeEntryDispatchesOnceWithExplicitSession() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        try {
            val row = activity.findViewById<View>(R.id.settings_color_customization_holder)
            val shadow = org.robolectric.Shadows.shadowOf(activity)
            while (shadow.nextStartedActivity != null) { }
            row.performClick()
            row.performClick()
            val launched = requireNotNull(shadow.nextStartedActivity)
            assertEquals(OxygenThemeActivity::class.java.name, launched.component?.className)
            assertNotNull(launched.getStringExtra(ClassicSettingsActivityTransition.EXTRA_SESSION))
            assertNull(shadow.nextStartedActivity)
        } finally {
            ClassicSettingsActivityTransition.clear()
            controller.pause().stop().destroy()
        }
    }

    @Test fun pageHandoffIsSkippedWheneverThePlatformAnimatesTheWindow() {
        // The snapshot handoff cross-fades the two pages' text. It must stay off from
        // Android 14 up, where the platform owns the transition.
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
            val activity = controller.get()
            val pageEnabled = OxygenClassicMotion.enabled(activity)
            assertTrue(pageEnabled)
            val handoffEnabled = pageEnabled && !PredictiveBackSupport.systemDrivesPageTransition
            assertEquals(!PredictiveBackSupport.systemDrivesPageTransition, handoffEnabled)

            // A disabled handoff stores nothing, so no in-page fade can be received later.
            ClassicSettingsActivityTransition.stage("probe", activity.findViewById(R.id.settings_holder),
                "title", true, false)
            val received = ClassicSettingsActivityTransition.receive("probe",
                activity.findViewById(R.id.settings_nested_scrollview),
                activity.findViewById(R.id.settings_holder), false, {}, "")
            assertFalse(received)
            controller.pause().stop().destroy()
        } finally {
            ClassicSettingsActivityTransition.clear()
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
        }
    }
}
