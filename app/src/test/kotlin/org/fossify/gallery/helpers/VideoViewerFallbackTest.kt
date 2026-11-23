package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Video/unsupported media has no static photo geometry and goes through the
 * entry fallback. That path must still release the frozen page and all leases;
 * otherwise a later photo visit inherits a hidden scrim or an input lock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VideoViewerFallbackTest {
    @Test
    fun fallbackEntryClearsTheBackdropAndEveryLease() {
        val session = MediaTransitionSessionCoordinator()
        val token = session.entry
        val backdrop = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        session.publishEntryBackdrop(token, backdrop)
        assertNotNull(session.readEntryBackdrop(token))
        val leases = session.leases()
        var inputReleased = false
        assertTrue(leases.acquire(token, MediaTransitionSessionCoordinator.Resource.INPUT) {
            inputReleased = true
        })

        assertTrue(session.entryFallback(token, "app_entry_unavailable"))
        assertTrue(session.entryFinished(token))
        assertNull("the fallback page must not survive the entry", session.readEntryBackdrop(token))
        assertTrue(leases.release(token, MediaTransitionSessionCoordinator.Resource.INPUT))
        assertTrue(inputReleased)
        assertFalse(session.hasLeases())

        val returning = requireNotNull(session.beginReturn())
        assertTrue(session.targetReady(returning))
        assertTrue(session.terminate(returning, State.FALLBACK, "no_static_geometry"))
        assertTrue(session.clean())
        assertFalse(session.hasLeases())
    }
}
