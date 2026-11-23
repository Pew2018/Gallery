package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch-time anchor is a fallback for a covered parent, never an
 * unconditional replay of stale coordinates. Each test invalidates exactly one
 * invariant the production validator reads.
 */
class ViewerCachedReturnAnchorTest {
    private fun evidence(
        launchMatches: Boolean = true,
        scopeMatches: Boolean = true,
        pathStillSelected: Boolean = true,
        adapterVersionMatches: Boolean = true,
        mediaIdentityMatches: Boolean = true,
        fileVersionMatches: Boolean = true,
        windowGeometryMatches: Boolean = true,
        drawableAvailable: Boolean = true,
    ) = ViewerCachedReturnAnchorEvidence(
        launchMatches, scopeMatches, pathStillSelected, adapterVersionMatches,
        mediaIdentityMatches, fileVersionMatches, windowGeometryMatches, drawableAvailable,
    )

    @Test
    fun verifiedAnchorSurvivesParentCover() {
        val decision = ViewerCachedReturnAnchorPolicy.decide(evidence())
        assertTrue(decision.accepted)
        assertEquals(ViewerCachedReturnAnchorReason.READY, decision.reason)
    }

    @Test
    fun anotherLaunchCannotReuseThePreviousAnchor() {
        val decision = ViewerCachedReturnAnchorPolicy.decide(evidence(launchMatches = false))
        assertFalse(decision.accepted)
        assertEquals(ViewerCachedReturnAnchorReason.LAUNCH_CHANGED, decision.reason)
    }

    @Test
    fun switchedPageOrChangedScopeIsRejected() {
        assertEquals(
            ViewerCachedReturnAnchorReason.SCOPE_CHANGED,
            ViewerCachedReturnAnchorPolicy.decide(evidence(scopeMatches = false)).reason,
        )
        assertEquals(
            ViewerCachedReturnAnchorReason.SCOPE_CHANGED,
            ViewerCachedReturnAnchorPolicy.decide(evidence(pathStillSelected = false)).reason,
        )
    }

    @Test
    fun deletedResortedOrReboundMediaIsRejected() {
        listOf(
            evidence(adapterVersionMatches = false),
            evidence(mediaIdentityMatches = false),
            evidence(fileVersionMatches = false),
        ).forEach {
            val decision = ViewerCachedReturnAnchorPolicy.decide(it)
            assertFalse(decision.accepted)
            assertEquals(ViewerCachedReturnAnchorReason.IDENTITY_CHANGED, decision.reason)
        }
    }

    @Test
    fun changedWindowGeometryOrMissingPixelsIsRejected() {
        val geometry = ViewerCachedReturnAnchorPolicy.decide(evidence(windowGeometryMatches = false))
        assertFalse(geometry.accepted)
        assertEquals(ViewerCachedReturnAnchorReason.BOUNDS_UNAVAILABLE, geometry.reason)

        val pixels = ViewerCachedReturnAnchorPolicy.decide(evidence(drawableAvailable = false))
        assertFalse(pixels.accepted)
        assertEquals(ViewerCachedReturnAnchorReason.PIXELS_UNAVAILABLE, pixels.reason)
    }
}
