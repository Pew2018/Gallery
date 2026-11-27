package org.fossify.gallery.helpers

import android.app.Application
import android.util.TypedValue
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies the real theme-application contract behind the light-page back fix,
 * not just a boolean. Commons re-applies its own Material3-Dark themes in
 * onCreate/onResume via setTheme(getThemeId()); SimpleActivity's
 * onApplyThemeResource override must layer OxygenClassicTaskBackground so that
 * android:colorBackground - the attribute Android resolves into the Activity
 * TaskDescription and the Shell back animation reads - is the page surface,
 * after Commons' theme, for every theme application.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskBackgroundThemeInjectionTest {

    private fun resolvedColor(theme: android.content.res.Resources.Theme, attr: Int): Int? {
        val value = TypedValue()
        if (!theme.resolveAttribute(attr, value, true)) return null
        if (value.type < TypedValue.TYPE_FIRST_COLOR_INT || value.type > TypedValue.TYPE_LAST_COLOR_INT) return null
        return value.data
    }

    @Test
    fun commonThemeReapplicationStillResolvesPageColorBackground() {
        val activity = Robolectric.buildActivity(TestThemeActivity::class.java).setup().get()
        val expected = OxygenThemeColors.resolve(
            classic = true,
            mode = "light",
            systemDark = false,
            rawAccent = 0xFF2196F3.toInt(),
            legacyBackground = 0,
            legacyText = 0,
        ).pageBackground

        // Simulate Commons' onCreate/onResume setTheme(getThemeId()) with its own
        // Material3-based theme, then read the effective theme the same way Android
        // does in onApplyThemeResource.
        activity.setTheme(R.style.AppTheme)
        // Robolectric's setTheme does not run the framework's onApplyThemeResource
        // callback, so invoke it explicitly - this is exactly what Android calls on
        // every setTheme (attach, Commons onCreate/onResume) on a real device.
        (activity as TestThemeActivity).applyThemeForTest()
        val background = resolvedColor(activity.theme, android.R.attr.colorBackground)
        assertEquals(
            "colorBackground must resolve to the classic page after Commons setTheme",
            expected,
            background,
        )
    }

    @Test
    fun windowBackgroundAndColorBackgroundAgreeOnPageSurface() {
        val activity = Robolectric.buildActivity(TestThemeActivity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        (activity as TestThemeActivity).applyThemeForTest()
        val colorBg = resolvedColor(activity.theme, android.R.attr.colorBackground)
        val windowBg = resolvedColor(activity.theme, android.R.attr.windowBackground)
        assertTrue("colorBackground must resolve to a color", colorBg != null)
        // Both must be the same page surface so the task background and the
        // window preview never disagree during a cross-Activity reveal.
        assertEquals("windowBackground must match colorBackground", colorBg, windowBg)
    }

    /** Minimal SimpleActivity subclass; the override under test lives in SimpleActivity. */
    class TestThemeActivity : org.fossify.gallery.activities.SimpleActivity() {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
        }

        /** Exposes the framework callback so the test can simulate setTheme. */
        fun applyThemeForTest() = onApplyThemeResource(theme, R.style.AppTheme, false)
    }
}
