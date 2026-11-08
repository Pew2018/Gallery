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
        val source = NavigationRippleDrawable(Color.GREEN, 28f)
        val start = android.os.SystemClock.uptimeMillis()
        val pulse = NavigationRippleDrawable.Pulse(start, 0.25f, 0.6f, start + 50L)
        source.continuePulse(pulse)
        val destination = NavigationRippleDrawable(Color.GREEN, 28f)
        destination.continuePulse(pulse.copy())
        assertEquals(source.progress(start + 100), destination.progress(start + 100))
        assertNotEquals(source.progress(start + 100), destination.progress(start + 150))
        assertNull(destination.progress(start + 405))
    }

    @Test fun disabledAnimationsHaveImmediatePressedFeedbackAndNoReleaseTail() {
        val ripple = NavigationRippleDrawable(Color.GREEN, 28f, 0f)
        ripple.continuePulse(NavigationRippleDrawable.Pulse(100, 0.5f, 0.5f))
        assertEquals(1f to 1f, ripple.progress(100))
        ripple.continuePulse(NavigationRippleDrawable.Pulse(100, 0.5f, 0.5f, 110))
        assertNull(ripple.progress(110))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun fullyExpandedFeedbackIsACircleWithoutRectangularStateWash() {
        val ripple = NavigationRippleDrawable(Color.argb(40, 0, 128, 128), 28f)
        ripple.setBounds(0, 0, 120, 60)
        ripple.continuePulse(NavigationRippleDrawable.Pulse(
            android.os.SystemClock.uptimeMillis() - 1000, 0.5f, 0.5f))
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        ripple.draw(Canvas(bitmap))
        assertTrue(Color.alpha(bitmap.getPixel(60, 30)) > 0)
        assertEquals(0, Color.alpha(bitmap.getPixel(34, 5)))
        assertEquals(0, Color.alpha(bitmap.getPixel(5, 30)))
        assertEquals(0, Color.alpha(bitmap.getPixel(115, 30)))
    }
    @Test fun shortReleaseUsesScaledExpansionAndZeroScaleLeavesNoSnapshot() {
        for (scale in listOf(0f, 1f, 2f)) {
            val ripple = NavigationRippleDrawable(Color.GREEN, 28f, scale)
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
