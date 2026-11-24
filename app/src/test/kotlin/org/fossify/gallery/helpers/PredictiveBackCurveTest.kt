package org.fossify.gallery.helpers

import android.app.Application
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import androidx.appcompat.app.AppCompatActivity
import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Samples the predictive-back animation resources the way the platform does.
 *
 * `AnimationSet#getTransformationAt(progress)` is the entry point
 * `CustomCrossActivityBackAnimation` uses for the post-commit phase, but it is not part
 * of the public SDK, so the test reaches it reflectively and falls back to the public
 * time-driven `getTransformation(elapsed)` when the runtime does not expose it. Both
 * paths must agree: the same resource also drives the plain `finish()` window
 * transition, which is time-driven.
 *
 * What is asserted is the *design contract* rather than an exact table: the foreground
 * starts fully opaque, ends fully transparent, never goes back up, has no frame-to-frame
 * jump big enough to read as a cut, and the fade completes inside the travelling part of
 * the clock so the page leaves instead of evaporating in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PredictiveBackCurveTest {
    private val samples = listOf(
        0f, 0.02f, 0.05f, 0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 0.95f, 0.99f, 1f,
    )

    private fun context() = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

    private fun Animation.sampleAt(progress: Float): Transformation {
        val transformation = Transformation()
        val method = Animation::class.java.methods.firstOrNull { it.name == "getTransformationAt" }
        if (method != null) {
            method.invoke(this, progress, transformation)
        } else {
            if (startTime == Long.MIN_VALUE) getTransformation(0L, Transformation())
            getTransformation((progress * duration).toLong(), transformation)
        }
        return transformation
    }

    private fun Animation.alphas(): List<Pair<Float, Float>> = samples.map { it to sampleAt(it).alpha }

    /** First progress at which the foreground is fully transparent. */
    private fun fadeCompleteProgress(animation: Animation): Float {
        var previous = 0f
        var step = 0
        while (step <= 1000) {
            val progress = step / 1000f
            if (animation.sampleAt(progress).alpha <= 0.002f) return previous.coerceAtLeast(progress)
            previous = progress
            step++
        }
        return 1f
    }

    /** Longest stretch in which the closing page is partially transparent over the parent. */
    private fun semiTransparentWindowMs(animation: Animation): Long {
        var first = -1
        var last = -1
        (0..1000).forEach { step ->
            val alpha = animation.sampleAt(step / 1000f).alpha
            if (alpha in 0.02f..0.98f) {
                if (first < 0) first = step
                last = step
            }
        }
        if (first < 0) return 0L
        return ((last - first) / 1000f * animation.duration).toLong()
    }

    private fun invisibleTailMs(animation: Animation): Long =
        ((1f - fadeCompleteProgress(animation)) * animation.duration).toLong()

    @Test
    fun theOrdinaryPageExitFadesOutInsideItsTravel() {
        val activity = context()
        val exit = AnimationUtils.loadAnimation(activity, R.anim.oxygen_classic_page_back_exit)
        val curve = exit.alphas()

        assertEquals(1f, curve.first().second, 0.001f)
        assertEquals(0f, curve.last().second, 0.001f)
        curve.zipWithNext().forEach { (a, b) ->
            assertTrue("page exit alpha rose from ${a.second} to ${b.second}",
                b.second <= a.second + 0.0001f)
        }
        curve.zipWithNext().forEach { (a, b) ->
            assertTrue("page exit alpha jumps ${a.first} -> ${b.first}", a.second - b.second <= 0.30f)
        }
        assertEquals("page exit clock", 210, exit.duration)
        val fade = fadeCompleteProgress(exit)
        assertTrue("the page is gone by 65% of its clock, was $fade", fade in 0.50f..0.65f)
        // Phase 1: 117 ms invisible tail on a 450 ms clock. Phase 2: 144 ms of 240.
        // This revision: ~84 ms of 210, i.e. the shortest tail so far without shortening
        // the fade itself below the ~120 ms the closing page needs to read as a fade.
        val tail = invisibleTailMs(exit)
        assertTrue("invisible tail was ${tail}ms", tail in 60..100)
        val fadeMs = (fade * exit.duration).toLong()
        assertTrue("the fade itself must stay readable at ${fadeMs}ms", fadeMs in 110..150)
    }

    @Test
    fun theFolderExitDissolvesFasterThanTheTwoGridsReadAsOneImage() {
        val activity = context()
        val exit = AnimationUtils.loadAnimation(activity, R.anim.oxygen_classic_folder_back_exit)
        val curve = exit.alphas()

        assertEquals(1f, curve.first().second, 0.001f)
        assertEquals(0f, curve.last().second, 0.001f)
        curve.zipWithNext().forEach { (a, b) ->
            assertTrue("folder exit alpha rose from ${a.second} to ${b.second}",
                b.second <= a.second + 0.0001f)
        }
        assertEquals("folder exit clock", 200, exit.duration)
        val fade = fadeCompleteProgress(exit)
        assertTrue("the grid is gone by 75% of its clock, was $fade", fade in 0.62f..0.75f)
        // 2026-11-24 phase 2: 220 ms clock, fade complete at 82% -> ~180 ms of two grids
        // at partial opacity. This revision: 200 ms clock, fade complete at ~0.72 -> ~144 ms,
        // while the slide still carries most of its travel on the last visible frame.
        val window = semiTransparentWindowMs(exit)
        assertTrue("semi-transparent overlap was ${window}ms", window in 90..150)
        assertEquals("nothing is left of the foreground", 0L, invisibleTailMs(exit).coerceAtMost(0L))
    }

    @Test
    fun bothEnterResourcesStayIdentitySoTheRealParentIsWhatShows() {
        listOf(R.anim.oxygen_classic_page_back_enter, R.anim.oxygen_classic_folder_back_enter).forEach { id ->
            val activity = context()
            val enter = AnimationUtils.loadAnimation(activity, id)
            samples.forEach { progress ->
                val transformation = enter.sampleAt(progress)
                assertEquals("$id alpha at $progress", 1f, transformation.alpha, 0.0001f)
            }
        }
    }

    /**
     * The same resources still drive the ordinary `finish()` window transition, which is
     * evaluated on elapsed time rather than on a 0..1 progress. Both paths must describe
     * the same curve, otherwise a toolbar back and a predictive-back commit would look
     * different for the same page.
     */
    @Test
    fun theTimeDrivenPathMatchesTheProgressDrivenPath() {
        val activity = context()
        listOf(R.anim.oxygen_classic_page_back_exit, R.anim.oxygen_classic_folder_back_exit).forEach { id ->
            val animation = AnimationUtils.loadAnimation(activity, id)
            val set = animation as AnimationSet
            // Discard the first call: Animation initialises mStartTime from it.
            animation.getTransformation(0L, Transformation())
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
                val timed = Transformation()
                animation.getTransformation((progress * set.duration).toLong(), timed)
                val expected = animation.getTransformationAtForTest(progress)
                assertEquals("$id alpha at $progress", expected, timed.alpha, 0.02f)
            }
        }
    }

    @Test
    fun theSlideDistanceStaysThePlatformReference() {
        val activity = context()
        val expected = (96 * activity.resources.displayMetrics.density + 0.5f).toInt()
        assertEquals(expected, activity.resources.getDimensionPixelSize(R.dimen.oxygen_classic_page_back_slide_distance))
    }

    private fun Animation.getTransformationAtForTest(progress: Float): Float = sampleAt(progress).alpha
}
