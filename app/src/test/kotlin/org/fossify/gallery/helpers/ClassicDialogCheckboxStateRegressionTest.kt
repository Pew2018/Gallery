package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Looper
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ClassicDialogCheckboxStateRegressionTest {
    private fun bitmap(drawable: Drawable, width: Int = 24, height: Int = 24): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(out))
        return out
    }

    private fun assertSamePixels(message: String, expected: Bitmap, actual: Bitmap) {
        assertEquals(message, expected.width, actual.width)
        assertEquals(message, expected.height, actual.height)
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals("$message at ($x,$y)", expected.getPixel(x, y), actual.getPixel(x, y))
        }
    }

    private fun assertDifferentPixels(message: String, first: Bitmap, second: Bitmap) {
        assertEquals(message, first.width, second.width)
        assertEquals(message, first.height, second.height)
        assertTrue(message, (0 until first.height).any { y ->
            (0 until first.width).any { x -> first.getPixel(x, y) != second.getPixel(x, y) }
        })
    }

    private fun dialogTokens(activity: AppCompatActivity, dark: Boolean, seed: Int): OxygenThemeTokens {
        val surface = if (dark) 0xFF303030.toInt() else Color.WHITE
        val accent = OxygenClassicPalette.readableAccent(seed, surface)
        return OxygenThemeEngine.snapshot(activity, activity.config).copy(
            dark = dark,
            pageBackground = surface,
            rawAccent = accent,
            interactiveAccent = accent
        )
    }

    @Test fun checkboxPixelsHaveOnlyTheTwoStableEnabledStatesAcrossRepeatedReversals() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        try {
            for (dark in listOf(false, true)) for (seed in listOf(0xFFFFF59D.toInt(), 0xFF2196F3.toInt(), 0xFF311B92.toInt())) {
                val tokens = dialogTokens(activity, dark, seed)
                val drawable = OxygenClassicControls.selectionDrawable(
                    false, 1f, tokens, surfaceAccent = true, animationsEnabled = true
                )
                drawable.state = intArrayOf(android.R.attr.state_enabled)
                drawable.jumpToCurrentState()
                val unchecked = bitmap(drawable)
                assertEquals("unchecked interior is transparent", Color.TRANSPARENT, unchecked.getPixel(12, 12))
                assertEquals("unchecked has no check stroke", Color.TRANSPARENT, unchecked.getPixel(15, 8))

                repeat(10) {
                    drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                    drawable.state = intArrayOf(android.R.attr.state_enabled)
                }
                drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                val checked = bitmap(drawable)
                assertEquals("checked fill reaches the resolved accent", tokens.interactiveAccent, checked.getPixel(4, 4))
                assertNotEquals("checked check stroke is visible", Color.TRANSPARENT, checked.getPixel(16, 10))

                drawable.state = intArrayOf(android.R.attr.state_enabled)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                val returnedUnchecked = bitmap(drawable)
                assertEquals("unchecked after reversal has no fill", Color.TRANSPARENT, returnedUnchecked.getPixel(12, 12))
                assertEquals("unchecked after reversal has no check", Color.TRANSPARENT, returnedUnchecked.getPixel(16, 10))
                assertSamePixels("stable unchecked pixels match first render", unchecked, returnedUnchecked)

                drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                drawable.jumpToCurrentState()
                val jumped = bitmap(drawable)
                assertEquals(tokens.interactiveAccent, jumped.getPixel(4, 4))
                assertNotEquals(Color.TRANSPARENT, jumped.getPixel(16, 10))

                drawable.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                drawable.state = intArrayOf(android.R.attr.state_enabled)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                assertSamePixels("rapidly reversed state settles to unchecked", unchecked, bitmap(drawable))

                drawable.state = intArrayOf(android.R.attr.state_checked) // disabled + checked
                drawable.jumpToCurrentState()
                val disabledChecked = bitmap(drawable)
                assertDifferentPixels("disabled checked remains distinct from unchecked", unchecked, disabledChecked)
                drawable.state = intArrayOf() // disabled + unchecked
                drawable.jumpToCurrentState()
                assertDifferentPixels("disabled unchecked remains distinct from enabled unchecked", unchecked, bitmap(drawable))
            }
        } finally {
            activity.finish()
        }
    }

    @Test fun actualCheckboxAndInsetDrawableRenderTheFinalViewStateImmediately() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        try {
            val tokens = dialogTokens(activity, dark = false, seed = 0xFFFFF59D.toInt())
            val checkbox = CheckBox(activity)
            val wrapped = InsetDrawable(OxygenClassicControls.selectionDrawable(
                false, 1f, tokens, surfaceAccent = true, animationsEnabled = false
            ), 0, 0, 12, 0)
            checkbox.buttonDrawable = wrapped
            checkbox.isChecked = false
            val first = bitmap(wrapped, width = 36)
            assertEquals(Color.TRANSPARENT, first.getPixel(12, 12))

            repeat(10) {
                checkbox.performClick()
                val expected = checkbox.isChecked
                assertEquals("wrapped Drawable receives View checked state", expected,
                    wrapped.state.contains(android.R.attr.state_checked))
                val pixels = bitmap(wrapped, width = 36)
                if (expected) {
                    assertEquals(tokens.interactiveAccent, pixels.getPixel(4, 4))
                    assertNotEquals(Color.TRANSPARENT, pixels.getPixel(16, 10))
                } else {
                    assertEquals(Color.TRANSPARENT, pixels.getPixel(12, 12))
                    assertEquals(Color.TRANSPARENT, pixels.getPixel(16, 10))
                }
            }
        } finally {
            activity.finish()
        }
    }
}
