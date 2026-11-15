package org.fossify.gallery.helpers

import org.fossify.gallery.helpers.PhotoTransitionCompletionGate.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTransitionCompletionGateTest {
    @Test fun sharedMotionRequiresReleaseMappingAndCapture() {
        val entry = PhotoTransitionCompletionGate()
        assertFalse(entry.startMotion())
        assertTrue(entry.requestRelease())
        assertFalse(entry.requestRelease())
        assertEquals(Stage.RELEASE_REQUESTED, entry.stage)
        assertTrue(entry.release())
        assertFalse(entry.startMotion())
        assertTrue(entry.map())
        assertFalse(entry.startMotion())
        assertTrue(entry.capture())
        assertTrue(entry.startMotion())
        assertTrue(entry.complete())
        assertFalse(entry.startFallback())
        assertFalse(entry.complete())
    }

    @Test fun timeoutWithoutLayoutReleasesAndRejectedMappingCannotBecomeShared() {
        val entry = PhotoTransitionCompletionGate()
        assertTrue(entry.release())
        assertFalse(entry.requestRelease())
        assertTrue(entry.reject())
        assertFalse(entry.reject())
        assertTrue(entry.startFallback())
        assertFalse(entry.map())
        assertFalse(entry.capture())
        assertFalse(entry.startMotion())
        assertFalse(entry.startFallback())
        assertTrue(entry.complete())
        assertEquals(Stage.COMPLETE, entry.stage)
    }

    @Test fun noAnimatorAndCancellationRaceThroughOneTerminalGate() {
        val entry = PhotoTransitionCompletionGate()
        entry.release()
        entry.map()
        entry.capture()
        assertFalse(entry.complete())
        assertTrue(entry.startFallback())
        assertTrue(entry.cancel())
        assertFalse(entry.complete())
        assertFalse(entry.cancel())
        assertEquals(Stage.CANCELLED, entry.stage)
    }
}
