package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class OxygenClassicEdgeGlowTest {
    @Test fun refreshListsSuppressOnlyTheirTopVerticalEdge() {
        assertTrue(OxygenClassicEdgeGlow.suppressEdge(
            vertical = true, suppressTop = true,
            direction = RecyclerView.EdgeEffectFactory.DIRECTION_TOP
        ))
        assertFalse(OxygenClassicEdgeGlow.suppressEdge(
            vertical = true, suppressTop = true,
            direction = RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM
        ))
        assertTrue(OxygenClassicEdgeGlow.suppressEdge(
            vertical = false, suppressTop = false,
            direction = RecyclerView.EdgeEffectFactory.DIRECTION_LEFT
        ))
    }

    @Test fun refreshAncestorIsDetectedWithoutDependingOnItsEnabledState() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val refresh = SwipeRefreshLayout(activity)
        val recycler = RecyclerView(activity)
        refresh.addView(recycler, FrameLayout.LayoutParams(-1, -1))
        refresh.isEnabled = false
        assertTrue(OxygenClassicEdgeGlow.hasRefreshParent(recycler))
        activity.finish()
    }

    @Test fun glowUsesCurrentAccentAndAnimatorScaleZeroResolvesImmediately() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val view = View(activity).apply { measure(120, 100); layout(0, 0, 120, 100) }
        var currentAccent = Color.GREEN
        var resolvedAccent = Color.TRANSPARENT
        val pulse = ClassicGlowPulse(view, {
            currentAccent.also { resolvedAccent = it }
        }, animationsEnabled = { false })
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        pulse.pulse(atBottom = false)
        pulse.draw(canvas)
        assertEquals(Color.GREEN, resolvedAccent)

        currentAccent = Color.MAGENTA
        pulse.draw(canvas)
        assertEquals(Color.MAGENTA, resolvedAccent)

        pulse.release()
        pulse.draw(canvas)
        assertEquals(Color.MAGENTA, resolvedAccent)
        pulse.finish()
        bitmap.recycle()
        activity.finish()
    }
}
