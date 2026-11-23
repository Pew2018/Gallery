package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Terminal replay must be idempotent: the transition's last progress frame already
 * lands on the destination, so the terminal callback may only fill a missing frame
 * and never force the same state again. A cancelled terminal must preserve the live
 * frame for the recovery path.
 */
class ViewerReturnVisualClockTest {
    @Test
    fun progressMapsTheTransitionOntoTheVisualRemainder() {
        val clock = ViewerReturnVisualClock()
        clock.begin()
        assertEquals(1f, clock.remainder, 0f)
        assertEquals(.75f, clock.progress(.25f), 0.0001f)
        assertEquals(.25f, clock.progress(.75f), 0.0001f)
        assertEquals(0f, clock.progress(1f), 0.0001f)
    }

    @Test
    fun terminalIsAppliedAtMostOnceAndSkippedWhenTheLastFrameAlreadyLanded() {
        val clock = ViewerReturnVisualClock()
        clock.begin()
        clock.progress(.4f)
        assertTrue(clock.terminal(cancelled = false))
        assertTrue(clock.terminalApplied)
        assertEquals(0f, clock.remainder, 0f)
        assertFalse(clock.terminal(cancelled = false))

        val complete = ViewerReturnVisualClock()
        complete.begin()
        complete.progress(1f)
        assertFalse("progress 1 already committed the final frame", complete.terminal(cancelled = false))
        assertTrue(complete.terminalApplied)
    }

    @Test
    fun cancelledTerminalKeepsTheLiveFrame() {
        val clock = ViewerReturnVisualClock()
        clock.begin()
        clock.progress(.6f)
        assertFalse(clock.terminal(cancelled = true))
        assertEquals(.4f, clock.remainder, 0.0001f)
        // The recovery path still owns the frame; a repeated terminal cannot rewrite it.
        assertFalse(clock.terminal(cancelled = false))
        assertEquals(.4f, clock.remainder, 0.0001f)
    }

    @Test
    fun lateProgressCannotResurrectATerminatedFrame() {
        val clock = ViewerReturnVisualClock()
        clock.begin()
        clock.progress(.5f)
        clock.terminal(cancelled = false)
        assertEquals(0f, clock.progress(0f), 0f)
        assertEquals(0f, clock.progress(.1f), 0f)
    }

    @Test
    fun aNewReturnRearmsTheClock() {
        val clock = ViewerReturnVisualClock()
        clock.begin()
        clock.progress(.5f)
        clock.terminal(cancelled = true)
        clock.begin()
        assertEquals(1f, clock.remainder, 0f)
        assertFalse(clock.terminalApplied)
        assertEquals(.8f, clock.progress(.2f), 0.0001f)
        assertTrue(clock.terminal(cancelled = false))
    }
}
