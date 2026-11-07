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
        val pulse = ClassicGlowPulse(view, { currentAccent }, animationsEnabled = { false })
        val bitmap = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        pulse.pulse(atBottom = false)
        pulse.draw(canvas)
        val visible = bitmap.getPixel(60, 0)
        assertTrue(Color.green(visible) > Color.red(visible))

        currentAccent = Color.MAGENTA
        bitmap.eraseColor(Color.TRANSPARENT)
        pulse.draw(canvas)
        val recolored = bitmap.getPixel(60, 0)
        assertTrue(Color.red(recolored) > Color.green(recolored))

        pulse.release()
        bitmap.eraseColor(Color.TRANSPARENT)
        pulse.draw(canvas)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(60, 0))
        pulse.finish()
        bitmap.recycle()
        activity.finish()
    }
}
