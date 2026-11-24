package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zoom-baseline consistency across renderer resource changes.
 *
 * A01/A02 encode the 2026-11-25 device failure: an entry photo whose proportional
 * preview (fit zoom 0.5 for the example geometry) is upgraded to the full decode
 * (fit zoom 0.25) must still be at fit for the shared-return proxy. Under the old
 * capture-once baseline both fail with `viewer_transition_photo_unavailable
 * count=4`; the Phase 2.2 baseline policy keeps them green without weakening any
 * gate: a genuinely zoomed-in user (A03, A15) is still rejected.
 */
class GestureZoomBaselineTest {
    private val tolerance = MAX_ZOOM_EQUALITY_TOLERANCE.toFloat()

    // 2000px preview and 4000px original in a 1000px-wide viewport: fits are 0.5
    // and 0.25, same aspect, so the library patches the zoom by exactly 0.5.
    private fun previewState(zoom: Float = 0.5f) =
        state(imageWidth = 2000f, imageHeight = 4000f, zoom = zoom)

    private fun fullState(zoom: Float = 0.25f) =
        state(imageWidth = 4000f, imageHeight = 8000f, zoom = zoom)

    private fun state(imageWidth: Float, imageHeight: Float, zoom: Float, viewport: Float = 1000f) =
        Frame(imageWidth, imageHeight, viewport, zoom)

    private data class Frame(val width: Float, val height: Float, val viewport: Float, val zoom: Float)

    private fun baseline() = GestureZoomBaseline(tolerance)

    private fun GestureZoomBaseline.send(frame: Frame): GestureZoomBaseline.Update =
        onStateChanged(
            imageWidth = frame.width,
            imageHeight = frame.height,
            hasSizes = frame.width > 0f && frame.height > 0f && frame.viewport > 0f,
            stateZoom = frame.zoom,
        )

    @Test
    fun a01SameVisualFitRectDifferentAbsoluteZoomStaysAtFit() {
        val baseline = baseline()
        baseline.send(previewState())
        assertTrue(baseline.isAtFit())
        // The full decode upgrades the drawable; the library patches the zoom to the
        // new absolute fit and the state change fires with the new image size.
        val update = baseline.send(fullState())
        assertTrue("an unzoomed user must stay at fit after the upgrade", baseline.isAtFit())
        assertTrue(update is GestureZoomBaseline.Update.Rebased)
        assertEquals(0.25f, baseline.initialZoom, 1e-6f)
    }

    @Test
    fun a02UnzoomedUserKeepsSharedReturnEligibilityAcrossTheUpgrade() {
        val baseline = baseline()
        baseline.send(previewState())
        baseline.send(fullState())
        // Shared proxy gate: the exact check that produced count=4 on device.
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a03UserZoomIsNotClearedByAResourceUpgrade() {
        val baseline = baseline()
        baseline.send(previewState())
        // User pinches to 1.5 absolute (3x relative).
        baseline.send(previewState(zoom = 1.5f))
        // Upgrade arrives; the library keeps the user's relative zoom: 1.5 * 0.5.
        val update = baseline.send(fullState(zoom = 0.75f))
        assertTrue(update is GestureZoomBaseline.Update.Scaled)
        assertFalse("a zoomed-in user must not be read as fit", baseline.isAtFit())
        assertEquals("the user's zoom survives the upgrade", 0.75f, baseline.currentZoom, 1e-6f)
        assertEquals("the baseline maps through the patch, not the user zoom",
            0.25f, baseline.initialZoom, 1e-6f)
    }

    @Test
    fun a04TallImageFitsByWidthAndStaysConsistent() {
        val baseline = baseline()
        baseline.send(state(1000f, 4000f, zoom = 1f))
        assertTrue(baseline.isAtFit())
        baseline.send(state(1000f, 4000f, zoom = 2f))
        assertFalse(baseline.isAtFit())
    }

    @Test
    fun a05LandscapeImageFitsByWidth() {
        val baseline = baseline()
        baseline.send(state(4000f, 2000f, zoom = 0.25f))
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a06PortraitImageUpgradeKeepsFit() {
        val baseline = baseline()
        baseline.send(state(2000f, 1000f, zoom = 0.5f))
        baseline.send(state(4000f, 2000f, zoom = 0.25f))
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a07ExifRotationStartsANewRequestAndRecapturesOnTheFirstCallback() {
        val baseline = baseline()
        baseline.send(state(4000f, 2000f, zoom = 0.25f))
        assertTrue(baseline.isAtFit())
        // rotateImageViewBy() -> loadBitmap() -> invalidate(); the rotated decode
        // arrives with swapped dimensions and a reset state.
        baseline.invalidate()
        val update = baseline.send(state(2000f, 4000f, zoom = 0.5f))
        assertTrue(update is GestureZoomBaseline.Update.Rebased)
        assertEquals(0.5f, baseline.initialZoom, 1e-6f)
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a08FullResourceBeforePreviewIsJustAFirstCapture() {
        val baseline = baseline()
        val update = baseline.send(fullState())
        assertTrue(update is GestureZoomBaseline.Update.Rebased)
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a09UpgradeDuringTheSharedAnimationDoesNotMarkAZoomedUserAsFit() {
        val baseline = baseline()
        baseline.send(previewState())
        baseline.send(previewState(zoom = 2f))
        baseline.send(fullState(zoom = 1f))
        assertFalse(baseline.isAtFit())
    }

    @Test
    fun a10UpgradeAfterTheSharedAnimationKeepsALaterPinchBackEligible() {
        val baseline = baseline()
        baseline.send(previewState())
        baseline.send(previewState(zoom = 1.5f))
        baseline.send(fullState(zoom = 0.75f))
        assertFalse(baseline.isAtFit())
        // The user pinches back out to the minimum (fit) zoom.
        baseline.send(fullState())
        assertTrue("returning to fit must restore shared-return eligibility", baseline.isAtFit())
    }

    @Test
    fun a11SubsamplingHandoffGateStillRequiresTheBaseline() {
        val baseline = baseline()
        assertFalse("no baseline yet - no subsampling takeover", baseline.isAtFit())
        baseline.send(previewState())
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a12FailedSubsamplingPreparationLeavesTheBaselineUsable() {
        val baseline = baseline()
        baseline.send(previewState())
        // A failed decode never swaps the drawable, so no state change with a new
        // image size ever arrives; the baseline keeps holding.
        baseline.send(previewState())
        assertTrue(baseline.isAtFit())
    }

    @Test
    fun a13AStaleCallbackWithForeignImageSizeCannotFakeAFitBaseline() {
        val baseline = baseline()
        baseline.send(previewState())
        baseline.send(previewState(zoom = 1.5f))
        // A different media's drawable arriving late: size change while zoomed.
        baseline.send(state(3000f, 1000f, zoom = 0.5f))
        assertFalse("foreign geometry must not read as the old fit", baseline.isAtFit())
    }

    @Test
    fun a14ViewportChangeWithANewRequestRecapturesTheBaseline() {
        val baseline = baseline()
        baseline.send(state(2000f, 4000f, zoom = 0.5f))
        // Configuration change -> loadImage() -> invalidate() -> new viewport.
        baseline.invalidate()
        baseline.send(state(2000f, 4000f, zoom = 0.4f, viewport = 800f))
        assertTrue(baseline.isAtFit())
        assertEquals(0.4f, baseline.initialZoom, 1e-6f)
    }

    @Test
    fun a15ARealZoomedInStateCannotBypassTheSharedReturnGate() {
        val baseline = baseline()
        baseline.send(previewState())
        baseline.send(previewState(zoom = 1.2f))
        assertFalse(baseline.isAtFit())
        // And even an upgrade cannot launder the zoom into a fit.
        baseline.send(fullState(zoom = 0.6f))
        assertFalse(baseline.isAtFit())
    }

    @Test
    fun a16PagingAndDoubleTapMathStayTiedToTheSameBaseline() {
        val baseline = baseline()
        baseline.send(previewState())
        assertTrue("an image at fit pages horizontally", baseline.zoomMatchesBaseline())
        baseline.send(previewState(zoom = 0.9f))
        assertFalse("a zoomed image keeps the pager out of the way", baseline.zoomMatchesBaseline())
        // Before any baseline exists, paging must stay possible (legacy behaviour).
        val fresh = baseline()
        assertTrue(fresh.zoomMatchesBaseline())
        assertFalse(fresh.isAtFit())
    }

    @Test
    fun invalidSizesNeverEstablishOrBreakABaseline() {
        val baseline = baseline()
        baseline.send(state(0f, 0f, zoom = 1f))
        assertFalse(baseline.hasBaseline)
        baseline.send(previewState())
        assertTrue(baseline.hasBaseline)
        // A transient cleared drawable (size reset) must not corrupt the baseline:
        // the next valid capture re-anchors it.
        baseline.invalidate()
        baseline.send(state(0f, 0f, zoom = 1f))
        assertFalse(baseline.hasBaseline)
        baseline.send(fullState())
        assertTrue(baseline.isAtFit())
    }
}
