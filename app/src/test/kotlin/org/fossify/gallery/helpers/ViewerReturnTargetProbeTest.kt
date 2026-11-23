package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exact rejection reasons for the Grid return target. These are state decisions,
 * not source-string checks: each case changes one real evidence field and asserts
 * what the production probe would do with it.
 */
class ViewerReturnTargetProbeTest {
    private fun evidence(
        scopeMatches: Boolean = true,
        hasPosition: Boolean = true,
        gridSettled: Boolean = true,
        holderBound: Boolean = true,
        pixelsBound: Boolean = true,
        identityMatches: Boolean = true,
        attached: Boolean = true,
        boundsUsable: Boolean = true,
        parentWindowVisible: Boolean = true,
        targetVisible: Boolean = true,
        geometryUsable: Boolean = true,
    ) = ViewerReturnTargetEvidence(
        scopeMatches, hasPosition, gridSettled, holderBound, pixelsBound, identityMatches,
        attached, boundsUsable, parentWindowVisible, targetVisible, geometryUsable,
    )

    private fun reason(evidence: ViewerReturnTargetEvidence) =
        ViewerReturnTargetProbe.decide(evidence).reason

    @Test
    fun everyFailureHasItsOwnReason() {
        assertEquals(ViewerReturnTargetReason.SCOPE_CHANGED, reason(evidence(scopeMatches = false)))
        assertEquals(ViewerReturnTargetReason.NO_POSITION, reason(evidence(hasPosition = false)))
        assertEquals(ViewerReturnTargetReason.GRID_UNSETTLED, reason(evidence(gridSettled = false)))
        assertEquals(ViewerReturnTargetReason.HOLDER_UNAVAILABLE, reason(evidence(holderBound = false)))
        assertEquals(ViewerReturnTargetReason.IDENTITY_CHANGED, reason(evidence(identityMatches = false)))
        assertEquals(ViewerReturnTargetReason.PIXELS_UNAVAILABLE, reason(evidence(pixelsBound = false)))
        assertEquals(ViewerReturnTargetReason.BOUNDS_UNAVAILABLE, reason(evidence(attached = false)))
        assertEquals(ViewerReturnTargetReason.BOUNDS_UNAVAILABLE, reason(evidence(boundsUsable = false)))
        assertEquals(ViewerReturnTargetReason.BOUNDS_UNAVAILABLE, reason(evidence(geometryUsable = false)))
        assertEquals(ViewerReturnTargetReason.PARENT_WINDOW_HIDDEN, reason(evidence(parentWindowVisible = false)))
        assertEquals(ViewerReturnTargetReason.TARGET_NOT_VISIBLE, reason(evidence(targetVisible = false)))
    }

    @Test
    fun coveredParentIsAcceptedOnlyWithLiveGeometryAndIdentity() {
        val covered = ViewerReturnTargetProbe.decide(
            evidence(parentWindowVisible = false, targetVisible = false)
        )
        assertTrue("a stopped Grid still owns a legal rectangle", covered.accepted)
        assertEquals(ViewerReturnTargetReason.PARENT_WINDOW_HIDDEN, covered.reason)
        assertTrue(covered.geometryFromCoveredParent)

        // Covered is not a license to ignore identity. A rebound or re-sorted
        // thumbnail must be rejected even though the parent is hidden.
        val rebound = ViewerReturnTargetProbe.decide(
            evidence(parentWindowVisible = false, identityMatches = false)
        )
        assertFalse(rebound.accepted)
        assertEquals(ViewerReturnTargetReason.IDENTITY_CHANGED, rebound.reason)

        val missingPixels = ViewerReturnTargetProbe.decide(
            evidence(parentWindowVisible = false, pixelsBound = false)
        )
        assertFalse(missingPixels.accepted)
        assertEquals(ViewerReturnTargetReason.PIXELS_UNAVAILABLE, missingPixels.reason)
    }

    @Test
    fun visibleTargetWithoutPixelsOrBoundsIsRejected() {
        assertFalse(ViewerReturnTargetProbe.decide(evidence(pixelsBound = false)).accepted)
        assertFalse(ViewerReturnTargetProbe.decide(evidence(boundsUsable = false)).accepted)
    }

    @Test
    fun readyTargetKeepsTheVisiblePath() {
        val ready = ViewerReturnTargetProbe.decide(evidence())
        assertTrue(ready.accepted)
        assertEquals(ViewerReturnTargetReason.READY, ready.reason)
        assertFalse(ready.geometryFromCoveredParent)
    }
}
