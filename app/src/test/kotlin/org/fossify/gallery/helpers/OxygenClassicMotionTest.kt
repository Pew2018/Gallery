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
        assertTrue((OxygenClassicMotion.SETTINGS_EXIT_MS +
            OxygenClassicMotion.SETTINGS_ENTER_MS) in 160L..200L)
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
}
