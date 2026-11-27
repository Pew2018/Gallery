package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsamplingHandoffPolicyTest {
    @Test fun handoffWaitsForTheKnownFittedGlideGeometry() {
        assertFalse(SubsamplingHandoffPolicy.isAtFit(1f, 1f, initialZoomReady = false))
        assertTrue(SubsamplingHandoffPolicy.isAtFit(1f, 1f, initialZoomReady = true))
        assertTrue(SubsamplingHandoffPolicy.isAtFit(1.005f, 1f, initialZoomReady = true))
        assertFalse(SubsamplingHandoffPolicy.isAtFit(1.2f, 1f, initialZoomReady = true))
        assertFalse(SubsamplingHandoffPolicy.isAtFit(Float.NaN, 1f, initialZoomReady = true))
    }
}
