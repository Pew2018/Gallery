package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ownership and progress contract of the Viewer's system bar surfaces.
 *
 * Device evidence behind this file (Pixel 8 Pro, Android 16, build `5807a5510e15`,
 * `Gallery-Diagnostics-20261124-212905.txt`): 14 Viewer exits logged a bar appearance
 * frozen at the gesture's release progress - `heldProgress=0.49627578`,
 * `0.9895514`, `0.41481012`, `0.6585804`, `0.75474906`, `0.372931`, `0.7647971` - and
 * the same log shows one monotonic entry flipping the status icons
 * `LIGHT -> DARK -> LIGHT` (`progress 0.0 / 0.010795873 / 0.1496258`).
 *
 * The class under test is pure Kotlin, so these are the exact frame-by-frame
 * semantics the Activity now follows. What remains device-only: how the platform
 * composites the two windows and what SystemUI does with the polarity at the handover.
 */
class ViewerSystemBarChoreographyTest {
    private fun choreography() = ViewerSystemBarChoreography()

    @Test
    fun entryBlendsOnlyWhileTheViewerIsTheDestination() {
        val bars = choreography()
        bars.startEntry()
        assertEquals(ViewerBarOwner.VIEWER_ENTERING, bars.owner)
        assertEquals(0f, bars.canvasBarProgress(0f)!!, 0f)
        assertEquals(0.42f, bars.canvasBarProgress(0.42f)!!, 0f)

        bars.settleViewer()
        assertEquals(ViewerBarOwner.VIEWER, bars.owner)
        assertEquals(1f, bars.canvasBarProgress(1f)!!, 0f)
    }

    @Test
    fun aCommittedReturnHoldsTheReleaseFrameAndThenContinuesFromIt() {
        val bars = choreography()
        bars.startEntry()
        bars.settleViewer()
        bars.canvasBarProgress(0.49627578f)

        val token = bars.beginReturn(0.49627578f)
        assertEquals(ViewerBarOwner.RETURN_WAIT_TARGET, bars.owner)
        // Waiting for the Grid's target: the release frame stays, and the canvas frames
        // that the teardown path posts are no longer allowed to write.
        assertNull(bars.canvasBarProgress(1f))
        assertEquals(0.49627578f, bars.returnBarProgress(token, 1f)!!, 1e-6f)

        // The return clock runs: the surface continues to the page colour instead of
        // staying at the release blend and jumping on removal.
        assertEquals(ViewerBarOwner.RETURN_SETTLING, bars.owner)
        assertEquals(0.49627578f * 0.5f, bars.returnBarProgress(token, 0.5f)!!, 1e-6f)
        assertEquals(0f, bars.returnBarProgress(token, 0f)!!, 0f)
    }

    @Test
    fun theSettledReturnIsMonotonicForEveryReleasePoint() {
        val remainders = listOf(1f, 0.95f, 0.8f, 0.6f, 0.4f, 0.2f, 0.05f, 0f)
        listOf(0.1f, 0.372931f, 0.49627578f, 0.7647971f, 0.9895514f, 1f).forEach { release ->
            val bars = choreography()
            bars.settleViewer()
            val token = bars.beginReturn(release)
            var previous = release
            remainders.forEach { remaining ->
                val value = bars.returnBarProgress(token, remaining)!!
                assertTrue("release $release regressed at $remaining", value <= previous + 1e-6f)
                previous = value
            }
            assertEquals("release $release must end on the page surface", 0f, previous, 1e-6f)
        }
    }

    @Test
    fun aCancelledReturnRestoresTheViewerAppearance() {
        val bars = choreography()
        bars.settleViewer()
        val token = bars.beginReturn(0.4f)
        bars.returnBarProgress(token, 0.5f)

        assertEquals(1f, bars.terminal(token, cancelled = true)!!, 0f)
        assertEquals(ViewerBarOwner.CANCEL_RESTORE, bars.owner)
        bars.settleViewer()
        assertEquals(ViewerBarOwner.VIEWER, bars.owner)
        assertEquals(1f, bars.canvasBarProgress(1f)!!, 0f)
    }

    @Test
    fun aCommittedReturnWritesThePageSurfaceAtMostOnce() {
        val bars = choreography()
        bars.settleViewer()
        val token = bars.beginReturn(0.6f)
        bars.returnBarProgress(token, 0.2f)

        assertEquals(0f, bars.terminal(token, cancelled = false)!!, 0f)
        assertEquals(ViewerBarOwner.PAGE_HANDOFF, bars.owner)
        // The teardown restore, a late lifecycle frame and a second terminal are all
        // silent once the page owns the surface.
        assertNull(bars.terminal(token, cancelled = false))
        assertNull(bars.canvasBarProgress(1f))
        assertNull(bars.returnBarProgress(token, 0f))
    }

    @Test
    fun aReturnThatNeverSettledStillHandsOverThePageSurfaceOnce() {
        val bars = choreography()
        bars.settleViewer()
        val token = bars.beginReturn(0.85f)

        assertEquals(0f, bars.terminal(token, cancelled = false)!!, 0f)
        assertNull(bars.terminal(token, cancelled = false))
    }

    @Test
    fun anOldReturnCannotOverwriteTheCurrentOwner() {
        val bars = choreography()
        bars.settleViewer()
        val first = bars.beginReturn(0.4f)
        bars.returnBarProgress(first, 0.8f)

        val second = bars.beginReturn(0.9f)
        assertTrue(second != first)
        // Frames of the superseded return are rejected, both progress and terminal.
        assertNull(bars.returnBarProgress(first, 0.1f))
        assertNull(bars.terminal(first, cancelled = false))
        assertEquals(0.9f, bars.returnBarProgress(second, 1f)!!, 1e-6f)
        assertEquals(ViewerBarOwner.RETURN_SETTLING, bars.owner)
    }

    @Test
    fun aGestureCancelledBackToTheViewerLeavesSteadyOwnership() {
        val bars = choreography()
        bars.settleViewer()
        bars.startInteractive()
        assertEquals(ViewerBarOwner.INTERACTIVE_RETURN, bars.owner)
        assertEquals(0.62f, bars.canvasBarProgress(0.62f)!!, 0f)

        bars.cancelInteractive()
        assertEquals(ViewerBarOwner.VIEWER, bars.owner)
        assertEquals(1f, bars.canvasBarProgress(1f)!!, 0f)
    }

    @Test
    fun aFadeFallbackReleasesTheWindowWithoutAFinalWrite() {
        val bars = choreography()
        bars.settleViewer()
        val token = bars.beginReturn(0.5f)
        bars.releaseToPage()

        assertEquals(ViewerBarOwner.PAGE_HANDOFF, bars.owner)
        assertNull(bars.returnBarProgress(token, 0.2f))
        assertNull(bars.canvasBarProgress(0.5f))
        assertFalse(bars.isReturning())
    }
}
