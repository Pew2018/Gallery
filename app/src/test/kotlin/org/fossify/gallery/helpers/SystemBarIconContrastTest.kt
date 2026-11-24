package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Icon polarity hysteresis for the system bars.
 *
 * The Viewer blends its bar surfaces on the transition clock, so the contrast between
 * black and white icons passes through a point where neither is clearly better.
 * Deciding exactly there makes one progress threshold flip the icons; any jitter around
 * that frame (a restarting gesture, a cancel that rewinds the same frames) flips them
 * again, which is visible as the status bar blinking.
 *
 * Pure JVM: no Android types, so the contract is deterministic.
 */
class SystemBarIconContrastTest {
    private val pageSurface = 0xFFFAFAFA.toInt()
    private val viewerSurface = 0xFF1E1E1E.toInt()
    /** Relative luminance ~0.18: just past the point where neither icon colour wins. */
    private val midGrey = 0xFF767676.toInt()

    private fun blend(from: Int, to: Int, fraction: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * fraction)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    @Test
    fun steadyStatesResolveToTheContrastingPolarity() {
        assertTrue("a light page surface needs dark icons",
            SystemBarIconContrast.useDarkIcons(pageSurface, previous = null))
        assertFalse("the near-black viewer surface needs light icons",
            SystemBarIconContrast.useDarkIcons(viewerSurface, previous = null))
    }

    @Test
    fun steadyStatesIgnoreThePreviousPolarity() {
        // The alternative must never win at the endpoints, otherwise the hysteresis
        // would leave unreadable icons behind.
        assertTrue(SystemBarIconContrast.useDarkIcons(pageSurface, previous = true))
        assertTrue(SystemBarIconContrast.useDarkIcons(pageSurface, previous = false))
        assertFalse(SystemBarIconContrast.useDarkIcons(viewerSurface, previous = true))
        assertFalse(SystemBarIconContrast.useDarkIcons(viewerSurface, previous = false))
    }

    @Test
    fun theAmbiguousBandHoldsThePolarityThatIsAlreadyOnScreen() {
        assertTrue("dark icons stay while the background is near the crossing point",
            SystemBarIconContrast.useDarkIcons(midGrey, previous = true))
        assertFalse("light icons stay while the background is near the crossing point",
            SystemBarIconContrast.useDarkIcons(midGrey, previous = false))
    }

    @Test
    fun bothPolaritiesStillReadInTheHeldBand() {
        // Holding is only acceptable while the held polarity keeps a usable contrast.
        assertTrue(SystemBarIconContrast.contrast(0xFF000000.toInt(), midGrey) >= 3.0)
        assertTrue(SystemBarIconContrast.contrast(0xFFFFFFFF.toInt(), midGrey) >= 3.0)
    }

    @Test
    fun aMonotoneBlendChangesThePolarityExactlyOnce() {
        listOf(true, false).forEach { seed ->
            var previous: Boolean? = seed
            val decisions = ArrayList<Boolean>()
            for (step in 0..200) {
                val next = SystemBarIconContrast.useDarkIcons(
                    blend(pageSurface, viewerSurface, step / 200f), previous)
                decisions += next
                previous = next
            }
            // The seed only chooses which polarity is on screen at the page surface; the
            // physical blend crosses the threshold once either way.
            val flips = decisions.zipWithNext().count { (a, b) -> a != b }
            assertEquals("seed=$seed", 1, flips)
            assertEquals("seed=$seed starts on the page polarity", true, decisions.first())
            assertEquals("seed=$seed ends on the viewer polarity", false, decisions.last())
        }
    }

    @Test
    fun aBlendThatWobblesAroundTheCrossingPointDoesNotFlip() {
        // This is the frame-jitter case: the progress oscillates inside the band, i.e.
        // the region where both icon colours are within SWITCH_MARGIN of each other.
        var previous: Boolean? = SystemBarIconContrast.useDarkIcons(pageSurface, null)
        val wobble = listOf(0.58f, 0.62f, 0.59f, 0.61f, 0.58f, 0.63f, 0.60f)
        wobble.forEach { fraction ->
            val next = SystemBarIconContrast.useDarkIcons(blend(pageSurface, viewerSurface, fraction), previous)
            assertEquals("fraction=$fraction must hold the on-screen polarity",
                previous as Boolean, next)
            previous = next
        }
    }

    /**
     * The exported log of the 2026-11-24 run shows one monotonic Viewer entry flipping the
     * status icons `LIGHT -> DARK -> LIGHT` (progress 0.0 / 0.010795873 / 0.1496258). The
     * decision had been taken against the InsetsController flag, which a page-level writer
     * had rewritten between two Viewer frames. The latch takes its decision from the
     * surface only, so replaying the same surface can never produce a second answer.
     */
    @Test
    fun theSameSurfaceAlwaysKeepsItsPolarity() {
        val latch = IconPolarityLatch()
        val first = latch.decide(blend(pageSurface, viewerSurface, 0.02f), 0L)
        // A page write, a theme apply and a teardown restore all land on the same frame.
        listOf(1L, 20L, 40L, 1_000L).forEach { now ->
            assertEquals(first, latch.decide(blend(pageSurface, viewerSurface, 0.02f), now))
        }
        assertEquals(first, latch.current)
    }

    @Test
    fun aDecisiveChangeIsAppliedImmediately() {
        val latch = IconPolarityLatch(holdMs = 48L)
        assertTrue(latch.decide(pageSurface, 0L))
        // A jump to the viewer surface is not ambiguous: holding dark icons on a near-black
        // bar for a hold window would be a visible mistake, so the change lands at once.
        assertFalse(latch.decide(viewerSurface, 1L))
        assertTrue("and back again, still without waiting", latch.decide(pageSurface, 2L))
    }

    @Test
    fun aMonotonicBlendFlipsThePolarityAtMostOnce() {
        // The entry clock walks the same blend the device log recorded. Whichever frame the
        // crossing falls on, a monotonic pass may produce exactly one change and never a
        // second one on the way back out.
        val latch = IconPolarityLatch(holdMs = 48L)
        var changes = 0
        var previous: Boolean? = null
        var now = 0L
        (0..100).forEach { step ->
            val surface = blend(pageSurface, viewerSurface, step / 100f)
            val decision = latch.decide(surface, now)
            if (previous != null && decision != previous) changes++
            previous = decision
            now += 8L
        }
        assertEquals("a single monotonic blend flipped the icons $changes times", 1, changes)
        assertEquals("the pass must end on the viewer polarity", false, previous)
    }

    @Test
    fun aGradualChangeIsHeldWhileBothOptionsStayClose() {
        // The band where the alternative wins but not decisively: a change is allowed, but
        // only after it has survived the hold window. This is the narrow region a blend
        // crosses, and holding there is what stops one threshold from chattering.
        val partial = (0..60).map { 0xFF000000.toInt() or (0x50 + it * 3) * 0x010101 }
            .first {
                SystemBarIconContrast.advantage(it) in 1.2..2.8 &&
                    SystemBarIconContrast.useDarkIcons(it, previous = true) == false
            }
        val latch = IconPolarityLatch(holdMs = 48L)
        assertTrue("the page surface must start with dark icons", latch.decide(pageSurface, 0L))
        assertEquals("no flip on the frame the candidate appears", true, latch.decide(partial, 10L))
        assertEquals("still no flip inside the hold window", true, latch.decide(partial, 40L))
        assertFalse("after the hold window the better option wins", latch.decide(partial, 70L))
    }

    @Test
    fun adoptingThePagePolarityAvoidsTheFirstFlip() {
        // `OxygenSystemBars.apply` records the polarity it just painted; the Viewer's first
        // frame at progress 0 then has the page surface *and* the page polarity, so the
        // entry starts without an artificial LIGHT -> DARK step.
        val latch = IconPolarityLatch()
        latch.adopt(polarity = true)
        assertTrue(latch.decide(pageSurface, 0L))
        assertFalse(latch.decide(viewerSurface, 100L))
    }

    @Test
    fun resetForgetsEverythingSoARecreatedWindowStartsClean() {
        val latch = IconPolarityLatch()
        latch.decide(viewerSurface, 0L)
        latch.reset()
        assertEquals(null, latch.current)
        assertTrue(latch.decide(pageSurface, 5L))
    }

    @Test
    fun contrastIsSymmetricAndNormalised() {
        assertTrue(SystemBarIconContrast.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) in 20.9..21.1)
        assertTrue(SystemBarIconContrast.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()) in 20.9..21.1)
        assertTrue(SystemBarIconContrast.contrast(viewerSurface, viewerSurface) in 0.99..1.01)
    }
}
