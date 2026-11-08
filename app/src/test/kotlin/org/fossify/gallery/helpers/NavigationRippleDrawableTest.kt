package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class NavigationRippleDrawableTest {
    @Test fun handoffContinuesTheSameClockInsteadOfRestartingOrRetainingAView() {
        val source = NavigationRippleDrawable(Color.GREEN)
        val start = android.os.SystemClock.uptimeMillis()
        val pulse = NavigationRippleDrawable.Pulse(start, 0.25f, 0.6f, start + 50L)
        source.continuePulse(pulse)
        val destination = NavigationRippleDrawable(Color.GREEN)
        destination.continuePulse(pulse.copy())
        assertEquals(source.progress(start + 100), destination.progress(start + 100))
        assertNotEquals(source.progress(start + 100), destination.progress(start + 150))
        assertNull(destination.progress(start + 405))
    }

    @Test fun disabledAnimationsHaveImmediatePressedFeedbackAndNoReleaseTail() {
        val ripple = NavigationRippleDrawable(Color.GREEN, 0f)
        ripple.continuePulse(NavigationRippleDrawable.Pulse(100, 0.5f, 0.5f))
        assertEquals(1f to 1f, ripple.progress(100))
        ripple.continuePulse(NavigationRippleDrawable.Pulse(100, 0.5f, 0.5f, 110))
        assertNull(ripple.progress(110))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun fullyExpandedRippleCoversBoundsAndStaysClippedForDifferentHotspots() {
        val cases = listOf(
            Triple(120, 52, .5f to .5f),
            Triple(120, 52, 0f to .5f),
            Triple(120, 52, 1f to .5f),
            Triple(120, 52, .5f to 0f),
            Triple(120, 52, .5f to 1f),
            Triple(360, 52, .5f to .5f)
        )
        cases.forEach { (width, height, hotspot) ->
            val ripple = NavigationRippleDrawable(Color.argb(40, 0, 128, 128))
            ripple.setBounds(0, 0, width, height)
            ripple.continuePulse(NavigationRippleDrawable.Pulse(0, hotspot.first, hotspot.second))
            val bitmap = Bitmap.createBitmap(width + 8, height + 8, Bitmap.Config.ARGB_8888)
            ripple.drawAt(Canvas(bitmap), NavigationRippleDrawable.EXPAND_MS)
            assertTrue("hotspot=$hotspot width=$width", Color.alpha(bitmap.getPixel(0, 0)) > 0)
            assertTrue(Color.alpha(bitmap.getPixel(width - 1, height - 1)) > 0)
            assertEquals(0, Color.alpha(bitmap.getPixel(width + 2, height / 2)))
            assertEquals(0, Color.alpha(bitmap.getPixel(width / 2, height + 2)))
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun partialExpansionRemainsCircularAndReleasedRippleLeavesNoPixels() {
        val ripple = NavigationRippleDrawable(Color.argb(40, 0, 128, 128))
        ripple.setBounds(0, 0, 120, 52)
        ripple.continuePulse(NavigationRippleDrawable.Pulse(0, .5f, .5f, 0))
        val bitmap = Bitmap.createBitmap(120, 52, Bitmap.Config.ARGB_8888)
        ripple.drawAt(Canvas(bitmap), 50)
        assertTrue(Color.alpha(bitmap.getPixel(60, 26)) > 0)
        assertEquals(0, Color.alpha(bitmap.getPixel(10, 3)))
        bitmap.eraseColor(Color.TRANSPARENT)
        ripple.drawAt(Canvas(bitmap), NavigationRippleDrawable.EXPAND_MS + NavigationRippleDrawable.RELEASE_MS)
        assertTrue((0 until bitmap.height).all { y -> (0 until bitmap.width).all { x ->
            Color.alpha(bitmap.getPixel(x, y)) == 0
        } })
    }
    @Test fun shortReleaseUsesScaledExpansionAndZeroScaleLeavesNoSnapshot() {
        for (scale in listOf(0f, 1f, 2f)) {
            val ripple = NavigationRippleDrawable(Color.GREEN, scale)
            val now = android.os.SystemClock.uptimeMillis()
            ripple.continuePulse(NavigationRippleDrawable.Pulse(now, .2f, .6f, now + 20))
            if (scale == 0f) {
                assertNull(ripple.pulse)
            } else {
                val end = now + (NavigationRippleDrawable.EXPAND_MS * scale).toLong()
                assertEquals(1f to 1f, ripple.progress(end))
                assertNull(ripple.progress(end + (NavigationRippleDrawable.RELEASE_MS * scale).toLong()))
            }
        }
    }

}
