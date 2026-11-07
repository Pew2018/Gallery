package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OxygenClassicEdgeGlowTest {
    private fun activity() = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    private fun list(activity: AppCompatActivity) = RecyclerView(activity).apply {
        layoutManager = LinearLayoutManager(activity)
    }
    @Test fun refreshListsSuppressOnlyTheirTopVerticalEdge() {
        assertTrue(OxygenClassicEdgeGlow.suppressEdge(true, true, RecyclerView.EdgeEffectFactory.DIRECTION_TOP))
        assertFalse(OxygenClassicEdgeGlow.suppressEdge(true, true, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM))
        assertTrue(OxygenClassicEdgeGlow.suppressEdge(false, false, RecyclerView.EdgeEffectFactory.DIRECTION_LEFT))
        val activity = activity()
        val refresh = SwipeRefreshLayout(activity)
        val recycler = list(activity)
        refresh.addView(recycler)
        refresh.isEnabled = false
        assertTrue(OxygenClassicEdgeGlow.hasRefreshParent(recycler))
        activity.finish()
    }

    @Test fun pageInstallationIsIdempotentAndTracksOrientation() {
        val activity = activity()
        val recycler = list(activity)
        OxygenClassicEdgeGlow.install(recycler)
        val factory = recycler.edgeEffectFactory
        OxygenClassicEdgeGlow.install(recycler)
        assertSame(factory, recycler.edgeEffectFactory)
        (recycler.layoutManager as LinearLayoutManager).orientation = RecyclerView.HORIZONTAL
        OxygenClassicEdgeGlow.install(recycler)
        assertFalse((recycler.edgeEffectFactory as OxygenClassicEdgeGlow.ClassicFactory).vertical)
        activity.finish()
    }

    @Test fun dialogGlowCannotBeReenabledIncludingDelayedInstallAndDynamicChild() {
        val activity = activity()
        val root = FrameLayout(activity)
        val recycler = list(activity)
        root.addView(recycler)
        OxygenClassicEdgeGlow.install(recycler)
        val layout = recycler.layoutManager
        DialogOverscrollPolicy.protect(root) // Before attachment, also disposes installed state.
        assertNull(recycler.getTag(R.id.oxygen_edge_effect_factory))
        assertSame(layout, recycler.layoutManager)
        recycler.post { OxygenClassicEdgeGlow.install(recycler) }
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        OxygenClassicEdgeGlow.install(root)
        assertEquals(View.OVER_SCROLL_NEVER, recycler.overScrollMode)
        assertFalse(recycler.edgeEffectFactory is OxygenClassicEdgeGlow.ClassicFactory)
        val late = list(activity)
        OxygenClassicEdgeGlow.install(late) // Early install without any parent.
        root.addView(late)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        OxygenClassicEdgeGlow.install(late)
        assertEquals(View.OVER_SCROLL_NEVER, late.overScrollMode)
        assertNull(late.getTag(R.id.oxygen_edge_effect_factory))
        // Theme/layout/reparenting never clears the prohibition.
        root.removeView(late)
        root.addView(late)
        OxygenClassicEdgeGlow.install(late)
        assertEquals(View.OVER_SCROLL_NEVER, late.overScrollMode)
        activity.finish()
    }

    @Test fun api31PullDistanceReleaseAbsorbAndPolicyDisposalStayConsistent() {
        val activity = activity()
        val recycler = object : RecyclerView(activity) {
            override fun canScrollVertically(direction: Int) = true
        }.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 10
                override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) =
                    object : RecyclerView.ViewHolder(View(activity)) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
        }
        OxygenClassicEdgeGlow.install(recycler)
        val factory = recycler.edgeEffectFactory as OxygenClassicEdgeGlow.ClassicFactory
        val effect = factory.createEdgeEffect(recycler, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM)
        effect.setSize(360, 500)
        assertEquals(0.2f, effect.onPullDistance(0.2f, 0.5f), 0.001f)
        assertEquals(0.2f, effect.distance, 0.001f)
        assertEquals(-0.1f, effect.onPullDistance(-0.1f, 0.5f), 0.001f)
        assertEquals(0.1f, effect.distance, 0.001f)
        effect.onRelease()
        assertEquals(0f, effect.distance, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(effect.isFinished)
        activity.setContentView(recycler)
        effect.onAbsorb(12000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(effect.isFinished) // Attached fling absorption must decay without ACTION_UP.
        effect.onAbsorb(12000)
        DialogOverscrollPolicy.protect(recycler)
        assertTrue(effect.isFinished)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        effect.onAbsorb(12000) // Stale effect cannot restart after policy application.
        assertTrue(effect.isFinished)
        assertEquals(0f, effect.distance, 0f)
        assertNull(recycler.getTag(R.id.oxygen_edge_effect_factory))
        activity.finish()
    }

    @Test fun emptyOrShortRecyclerNeverAccumulatesPullOrAbsorb() {
        val activity = activity()
        val recycler = list(activity)
        OxygenClassicEdgeGlow.install(recycler)
        val effect = (recycler.edgeEffectFactory as OxygenClassicEdgeGlow.ClassicFactory)
            .createEdgeEffect(recycler, RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM)
        assertEquals(0f, effect.onPullDistance(0.8f, 0.5f), 0f)
        effect.onAbsorb(20000)
        assertTrue(effect.isFinished)
        activity.finish()
    }

    @Test fun dialogAlsoReplacesPlatformAndForeignFactoriesIdempotently() {
        val activity = activity()
        val recycler = list(activity)
        val platformFactory = recycler.edgeEffectFactory
        DialogOverscrollPolicy.protect(recycler)
        val none = recycler.edgeEffectFactory
        assertNotSame(platformFactory, none)
        DialogOverscrollPolicy.protect(recycler)
        assertSame(none, recycler.edgeEffectFactory)
        recycler.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {}
        recycler.viewTreeObserver.dispatchOnGlobalLayout()
        assertSame(none, recycler.edgeEffectFactory)
        assertEquals(View.OVER_SCROLL_NEVER, recycler.overScrollMode)
        activity.finish()
    }

    @Test fun painterEndsTransparentlyWithin48dpAtEveryViewportSizeAndDensity() {
        for (density in listOf(1f, 2f, 3f)) for (widthDp in listOf(24, 360, 1200)) {
            val width = (widthDp * density).toInt()
            val height = (400 * density).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            ClassicGlowPainter.draw(Canvas(bitmap), paint, Color.CYAN, 1f, 0.5f, density)
            var peak = 0
            var lowest = 0
            for (y in 0 until height) for (x in 0 until width) {
                val alpha = Color.alpha(bitmap.getPixel(x, y))
                peak = maxOf(peak, alpha)
                if (alpha > 0) lowest = y
            }
            assertTrue("must actually draw", peak > 0)
            assertTrue("peak=$peak", peak <= 32)
            assertTrue("lowest=$lowest density=$density", lowest < 48 * density)
            assertEquals(0, Color.alpha(bitmap.getPixel(width / 2, minOf(height - 1, (48 * density).toInt()))))
            assertNull(paint.shader)
            bitmap.recycle()
        }
    }

    @Test fun painterFormsAnArcAndMirrorsAtBottomWithoutHardCutoff() {
        val bitmap = Bitmap.createBitmap(360, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        ClassicGlowPainter.draw(canvas, paint, Color.RED, 1f, 0.5f, 1f)
        assertTrue(Color.alpha(bitmap.getPixel(180, 2)) > Color.alpha(bitmap.getPixel(4, 2)))
        assertTrue(Color.alpha(bitmap.getPixel(180, 2)) > Color.alpha(bitmap.getPixel(180, 24)))
        assertTrue(Color.alpha(bitmap.getPixel(180, 43)) <= 1)
        canvas.save()
        canvas.translate(360f, 500f)
        canvas.rotate(180f)
        ClassicGlowPainter.draw(canvas, paint, Color.RED, 1f, 0.5f, 1f)
        canvas.restore()
        for (y in 0 until 48) assertEquals(Color.alpha(bitmap.getPixel(180, y)),
            Color.alpha(bitmap.getPixel(179, 499 - y)))
        bitmap.recycle()
    }

    @Test fun pulseUsesCurrentAccentAndDisabledAnimationsReleaseImmediately() {
        val activity = activity()
        val view = View(activity)
        // View.postDelayed belongs to the attached window; an unattached fixture
        // keeps its decay in View's pending RunQueue instead of the main Looper.
        activity.setContentView(view, FrameLayout.LayoutParams(360, 500))
        view.layout(0, 0, 360, 500)
        var accent = Color.GREEN
        val pulse = ClassicGlowPulse(view, { accent }, animationsEnabled = { false })
        fun render(): Bitmap = Bitmap.createBitmap(360, 500, Bitmap.Config.ARGB_8888).also { pulse.draw(Canvas(it)) }
        pulse.pulse(false)
        val green = render()
        assertTrue(Color.green(green.getPixel(180, 1)) > Color.red(green.getPixel(180, 1)))
        accent = Color.MAGENTA
        val magenta = render()
        assertTrue(Color.red(magenta.getPixel(180, 1)) > Color.green(magenta.getPixel(180, 1)))
        pulse.release()
        val clear = render()
        assertEquals(0, clear.getPixel(180, 1))
        pulse.pulse(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val expired = render()
        assertEquals(0, expired.getPixel(180, 498))
        for (bitmap in listOf(green, magenta, clear, expired)) bitmap.recycle()
        pulse.finish()
        activity.finish()
    }

    @Test fun policyCancelsActivePulseAndRefreshTopWhileBottomStillWorks() {
        val activity = activity()
        val owner = View(activity).apply { layout(0, 0, 360, 500) }
        val refresh = SwipeRefreshLayout(activity)
        refresh.addView(owner)
        val pulse = ClassicGlowPulse(owner, { Color.RED }, animationsEnabled = { false })
        val bitmap = Bitmap.createBitmap(360, 500, Bitmap.Config.ARGB_8888)
        pulse.pulse(false)
        pulse.draw(Canvas(bitmap))
        assertEquals(0, bitmap.getPixel(180, 1))
        pulse.pulse(true)
        pulse.draw(Canvas(bitmap))
        assertTrue(Color.alpha(bitmap.getPixel(180, 498)) > 0)
        bitmap.eraseColor(Color.TRANSPARENT)
        DialogOverscrollPolicy.protect(owner)
        pulse.draw(Canvas(bitmap))
        pulse.pulse(false)
        pulse.draw(Canvas(bitmap))
        assertEquals(0, bitmap.getPixel(180, 498))
        bitmap.recycle()
        activity.finish()
    }
}
