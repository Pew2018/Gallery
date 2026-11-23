package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Viewer derives every visual layer from one progress clock. These assertions pin
 * the pure mapping: exact endpoints, monotonicity, no instant toolbar pop, and a side
 * bias that starts at zero and can always recover it.
 */
class ViewerVisualMotionTest {
    @Test
    fun entryChromeStartsAfterTheImageAndStillEndsFullyVisible() {
        assertEquals(0f, ViewerVisualMotion.entryChrome(0f), 0f)
        assertEquals(1f, ViewerVisualMotion.entryChrome(1f), 0f)
        // The image owns the first part of the expansion; controls must not appear
        // on the same frame as the thumbnail.
        assertEquals(0f, ViewerVisualMotion.entryChrome(0.05f), 0f)
        assertEquals(0f, ViewerVisualMotion.entryChrome(ViewerVisualMotion.ENTRY_CHROME_START), 0f)
        assertTrue(ViewerVisualMotion.entryChrome(0.5f) > 0.2f)
        assertTrue(ViewerVisualMotion.entryChrome(0.5f) < 0.8f)

        var previous = -1f
        for (step in 0..100) {
            val alpha = ViewerVisualMotion.entryChrome(step / 100f)
            assertTrue("monotonic at $step", alpha >= previous - 0.0001f)
            assertTrue("bounded at $step", alpha in -0.0001f..1.0001f)
            previous = alpha
        }
    }

    @Test
    fun returnChromeClearsBeforeTheMediaLandsAndKeepsExactEndpoints() {
        assertEquals(1f, ViewerVisualMotion.returnChrome(1f), 0f)
        assertEquals(0f, ViewerVisualMotion.returnChrome(0f), 0f)
        // Fully gone while a quarter of the return still remains.
        assertEquals(0f, ViewerVisualMotion.returnChrome(ViewerVisualMotion.RETURN_CHROME_TAIL), 0f)
        assertEquals(0f, ViewerVisualMotion.returnChrome(0.1f), 0f)
        // Leaves earlier than the scrim, but never at the same time as the thumb.
        assertTrue(ViewerVisualMotion.returnChrome(0.5f) < 0.5f)

        var previous = 0f
        for (step in 0..100) {
            val alpha = ViewerVisualMotion.returnChrome(step / 100f)
            assertTrue("more remaining must never show less chrome at $step",
                alpha >= previous - 0.0001f)
            previous = alpha
        }
    }

    @Test
    fun scrimTracksProgressExactly() {
        for (step in 0..20) {
            val p = step / 20f
            assertEquals(p, ViewerVisualMotion.scrim(p), 0f)
        }
        assertEquals(0f, ViewerVisualMotion.scrim(-3f), 0f)
        assertEquals(1f, ViewerVisualMotion.scrim(3f), 0f)
    }

    @Test
    fun toolbarCrossFadeDoesNotPopOffOnTheFirstGestureTick() {
        // Nothing exposed: the Viewer toolbar is untouched.
        assertEquals(1f, ViewerVisualMotion.viewerToolbarAlpha(1f), 0f)
        // The grid toolbar is behind the scrim, so a one-percent gesture cannot own
        // the top bar yet. The previous implementation set alpha to 0 here.
        assertTrue(ViewerVisualMotion.viewerToolbarAlpha(0.99f) > 0.9f)
        // A real cross-fade before the grid is meaningfully exposed.
        val mid = ViewerVisualMotion.viewerToolbarAlpha(0.9f)
        assertTrue("mid cross-fade was $mid", mid > 0.2f && mid < 0.8f)
        // By the time the grid is clearly visible the Viewer toolbar is gone.
        assertEquals(0f, ViewerVisualMotion.viewerToolbarAlpha(0.8f), 0f)
        assertEquals(0f, ViewerVisualMotion.viewerToolbarAlpha(0f), 0f)

        var previous = 0f
        for (step in 0..100) {
            val alpha = ViewerVisualMotion.viewerToolbarAlpha(step / 100f)
            assertTrue("monotonic at $step", alpha >= previous - 0.0001f)
            previous = alpha
        }
    }

    @Test
    fun backEdgeBiasStartsAtZeroAndFollowsTheGestureDirection() {
        val density = 3f
        val maxOffset = ViewerVisualMotion.BACK_EDGE_OFFSET_DP * density
        // Both edges start exactly on the un-biased frame.
        assertEquals(0f, ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0f, 0f), 0f)
        assertEquals(0f, ViewerVisualMotion.backEdgeOffsetPx(-1f, density, 0f, 0f), 0f)
        // Direction matches the platform's own pre-commit movement.
        assertTrue(ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0f, 1f) > 0f)
        assertTrue(ViewerVisualMotion.backEdgeOffsetPx(-1f, density, 0f, 1f) < 0f)
        // Bounded by the 14dp token, no exaggerated fly-out.
        assertEquals(maxOffset, ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0f, 1f), 0.5f)
        assertEquals(-maxOffset, ViewerVisualMotion.backEdgeOffsetPx(-1f, density, 0f, 1f), 0.5f)
        // Touch drags and unknown edges stay perfectly centred.
        assertEquals(0f, ViewerVisualMotion.backEdgeOffsetPx(0f, density, 0f, 0.6f), 0f)

        var previous = 0f
        for (step in 0..100) {
            val offset = ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0f, step / 100f)
            assertTrue("monotonic at $step", offset >= previous - 0.0001f)
            previous = offset
        }

        // A gesture that takes over an in-flight frame ramps from that frame: the
        // takeover progress itself must stay exactly where it was.
        assertEquals(0f, ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0.6f, 0.6f), 0f)
        assertTrue(ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0.6f, 0.8f) > 0f)
        assertEquals(maxOffset, ViewerVisualMotion.backEdgeOffsetPx(1f, density, 0.6f, 1f), 0.5f)
    }
}
