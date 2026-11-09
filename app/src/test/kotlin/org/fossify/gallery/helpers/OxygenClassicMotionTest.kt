package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OxygenClassicMotionTest {

    @Test fun allClassicDurationsStayBriefAndUseOneSettingsTransition() {
        assertTrue(OxygenClassicMotion.CHECKBOX_MS in 120L..160L)
        assertTrue(OxygenClassicMotion.TAB_MS in 120L..160L)
        assertTrue(OxygenClassicMotion.SWITCH_MS in 180L..200L)
        assertTrue(OxygenClassicMotion.TOGGLE_CHECKBOX_MS in 180L..200L)
        assertTrue(OxygenClassicMotion.LIST_MOVE_MS in 160L..180L)
        assertEquals(ClassicSettingsPageTransition.DURATION_MS, OxygenClassicMotion.SETTINGS_ENTER_MS)
        assertEquals(ClassicSettingsPageTransition.DISTANCE_DP, OxygenClassicMotion.SETTINGS_DISTANCE_DP)
    }

    private fun image(drawable: Drawable): IntArray {
        val image = Bitmap.createBitmap(48, 28, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 48, 28)
        drawable.draw(Canvas(image))
        return IntArray(48 * 28) { index -> image.getPixel(index % 48, index / 48) }
    }

    @Test fun disabledSystemAnimationsSnapDateTimeToggleAndClassicCheckboxToCorrectInk() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val resolver = activity.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            assertFalse(OxygenClassicMotion.enabled(activity))
            val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
            val toggle = OxygenClassicControls.dateTimeSwitchDrawable(
                1f, tokens, animationsEnabled = OxygenClassicMotion.enabled(activity))
            toggle.state = intArrayOf(android.R.attr.state_enabled)
            toggle.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            val immediate = image(toggle)
            toggle.jumpToCurrentState()
            assertArrayEquals(immediate, image(toggle))
            val checkbox = OxygenClassicControls.selectionDrawable(
                false, 1f, tokens, animationsEnabled = OxygenClassicMotion.enabled(activity))
            checkbox.state = intArrayOf(android.R.attr.state_enabled)
            checkbox.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            val immediateCheck = image(checkbox)
            checkbox.jumpToCurrentState()
            assertArrayEquals(immediateCheck, image(checkbox))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
            activity.finish()
        }
    }

    @Test fun transitionAnimationScaleAlsoDisablesClassicMotion() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val resolver = activity.contentResolver
        val originalAnimator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val originalTransition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
            assertFalse(OxygenClassicMotion.enabled(activity))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, originalAnimator)
            Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, originalTransition)
            activity.finish()
        }
    }

    @Test fun shortLoadingDoesNotFlashAndLongLoadingKeepsMinimumVisibility() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val resolver = activity.contentResolver
        val original = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val host = FrameLayout(activity)
            activity.setContentView(host)
            val progress = LinearProgressIndicator(activity).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            host.addView(progress, FrameLayout.LayoutParams(200, 3))
            OxygenProgressStyle.setLoading(progress, true)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            OxygenProgressStyle.setLoading(progress, false)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            assertEquals(View.GONE, progress.visibility)
            OxygenProgressStyle.setLoading(progress, true)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                OxygenProgressStyle.SHOW_DELAY_MS + 5, TimeUnit.MILLISECONDS)
            assertEquals(View.VISIBLE, progress.visibility)
            OxygenProgressStyle.setLoading(progress, false)
            assertEquals(View.VISIBLE, progress.visibility)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                OxygenProgressStyle.MIN_VISIBLE_MS + 5, TimeUnit.MILLISECONDS)
            assertEquals(View.GONE, progress.visibility)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
            activity.finish()
        }
    }

    @Test fun repeatedLoadUpdatesKeepDeadlineAndNewLoadCancelsHideUntilDisposed() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val resolver = activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val progress = LinearProgressIndicator(activity).apply { visibility = View.GONE; isIndeterminate = true }
        host.addView(progress)
        val clock = Shadows.shadowOf(Looper.getMainLooper())
        try {
            OxygenProgressStyle.setLoading(progress, true)
            clock.idleFor(80, TimeUnit.MILLISECONDS)
            OxygenProgressStyle.setLoading(progress, true)
            clock.idleFor(45, TimeUnit.MILLISECONDS)
            assertEquals(View.VISIBLE, progress.visibility)
            OxygenProgressStyle.setLoading(progress, false)
            clock.idleFor(40, TimeUnit.MILLISECONDS)
            OxygenProgressStyle.setLoading(progress, true)
            clock.idleFor(200, TimeUnit.MILLISECONDS)
            assertEquals(View.VISIBLE, progress.visibility)
            OxygenProgressStyle.dispose(progress)
            clock.idleFor(500, TimeUnit.MILLISECONDS)
            assertEquals(View.GONE, progress.visibility)
            OxygenProgressStyle.setLoading(progress, true)
            OxygenProgressStyle.dispose(progress)
            clock.idleFor(500, TimeUnit.MILLISECONDS)
            assertEquals(View.GONE, progress.visibility)
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            OxygenProgressStyle.setLoading(progress, true)
            assertEquals(View.VISIBLE, progress.visibility)
            OxygenProgressStyle.setLoading(progress, false)
            assertEquals(View.GONE, progress.visibility)
        } finally {
            OxygenProgressStyle.dispose(progress)
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior)
            activity.finish()
        }
    }

    @Test fun checkedTextViewRetainsDrawableThroughPressFocusAndRapidReversal() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val row = android.widget.CheckedTextView(activity)
        activity.setContentView(row)
        val mark = OxygenClassicControls.selectionDrawable(false, 1f,
            OxygenThemeEngine.snapshot(activity, activity.config), animationsEnabled = true)
        row.checkMarkTintList = null
        row.checkMarkDrawable = mark
        row.isChecked = false
        mark.state = intArrayOf(android.R.attr.state_enabled)
        row.isChecked = true
        mark.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        val clock = Shadows.shadowOf(Looper.getMainLooper())
        clock.idleFor(48, TimeUnit.MILLISECONDS)
        val before = image(mark)
        mark.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked,
            android.R.attr.state_pressed)
        assertArrayEquals("press must not snap checked animation", before, image(mark))
        mark.state = intArrayOf(android.R.attr.state_enabled)
        mark.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked,
            android.R.attr.state_focused)
        clock.idleFor(200, TimeUnit.MILLISECONDS)
        val finalImage = image(mark)
        mark.jumpToCurrentState()
        assertArrayEquals(finalImage, image(mark))
        assertSame(mark, row.checkMarkDrawable)
        row.isChecked = false
        clock.idleFor(200, TimeUnit.MILLISECONDS)
        val unchecked = image(mark)
        mark.jumpToCurrentState()
        assertArrayEquals(unchecked, image(mark))
        activity.finish()
    }

}
