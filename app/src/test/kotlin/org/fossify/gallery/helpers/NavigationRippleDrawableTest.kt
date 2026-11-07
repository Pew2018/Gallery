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
        val pulse = NavigationRippleDrawable.Pulse(100L, 0.25f, 0.6f, 150L)
        source.continuePulse(pulse)
        val destination = NavigationRippleDrawable(Color.GREEN, 28f)
        destination.continuePulse(pulse.copy())
        assertEquals(source.progress(200), destination.progress(200))
        assertNotEquals(source.progress(200), destination.progress(250))
        assertNull(destination.progress(330))
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
}
