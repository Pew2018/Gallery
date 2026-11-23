package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A predictive-back gesture restarts its normalised progress at zero. When it takes
 * over a settle that is already part-way through the return envelope, the inherited
 * frame must remain exact and only the remaining interval may be consumed.
 */
class ViewerReturnProgressTest {
    @Test
    fun firstProgressedFrameEqualsTheInheritedFrame() {
        listOf(0f, .25f, .6f, .87f, 1f).forEach { base ->
            assertEquals(base, ViewerReturnProgress.resume(base, 0f), 0.0001f)
        }
    }

    @Test
    fun takeoverConsumesTheRemainingIntervalInsteadOfSaturatingEarly() {
        // The previous implementation added raw progress: 0.6 + 0.5 clamped to 1.0,
        // so the envelope finished while the finger still had half the screen to go.
        assertEquals(0.8f, ViewerReturnProgress.resume(.6f, .5f), 0.0001f)
        assertEquals(1f, ViewerReturnProgress.resume(.6f, 1f), 0.0001f)
        assertTrue(ViewerReturnProgress.resume(.6f, .5f) < 1f)
    }

    @Test
    fun progressIsMonotonicAndBoundedForEveryBase() {
        listOf(0f, .1f, .4f, .75f, 1f).forEach { base ->
            var previous = ViewerReturnProgress.resume(base, 0f)
            for (step in 1..20) {
                val current = ViewerReturnProgress.resume(base, step / 20f)
                assertTrue("base=$base step=$step", current >= previous)
                assertTrue("base=$base step=$step", current in 0f..1f)
                previous = current
            }
            assertEquals(1f, previous, 0.0001f)
        }
    }

    @Test
    fun invalidValuesCannotMoveTheEnvelopeBackwards() {
        assertEquals(.4f, ViewerReturnProgress.resume(.4f, Float.NaN), 0f)
        assertEquals(1f, ViewerReturnProgress.resume(Float.NaN, .5f), 0f)
        assertEquals(.5f, ViewerReturnProgress.resume(-2f, .5f), 0f)
        assertEquals(1f, ViewerReturnProgress.resume(2f, 0f), 0f)
        assertEquals(1f, ViewerReturnProgress.resume(.5f, 3f), 0f)
    }

    @Test
    fun settleDurationGrowsFromTheLiveProgressAndStaysBounded() {
        assertEquals(140L, ViewerReturnProgress.settleDurationMs(0f))
        assertEquals(217L, ViewerReturnProgress.settleDurationMs(.35f))
        assertEquals(360L, ViewerReturnProgress.settleDurationMs(1f))
        var previous = 0L
        for (step in 0..20) {
            val duration = ViewerReturnProgress.settleDurationMs(step / 20f)
            assertTrue(duration >= previous)
            assertTrue(duration in 140L..360L)
            previous = duration
        }
        assertEquals(140L, ViewerReturnProgress.settleDurationMs(Float.NaN))
    }
}
