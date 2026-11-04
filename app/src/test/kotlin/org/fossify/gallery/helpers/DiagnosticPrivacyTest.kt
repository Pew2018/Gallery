package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPrivacyTest {
    @Test fun loggingIsOffByDefaultAndExpiresAfterTwentyFourHours() {
        val default = DiagnosticLoggingState(false, 0)
        assertFalse(default.isActive(1))
        val on = DiagnosticLoggingState.toggle(true, 10_000)
        assertTrue(on.isActive(10_001))
        assertFalse(on.isActive(10_000 + 24 * 60 * 60 * 1000L))
        assertFalse(DiagnosticLoggingState.toggle(false, 10_000).isActive(10_001))
    }

    @Test fun removesPrivatePathsUrisAndMediaFilenames() {
        val text = "failed /storage/emulated/0/DCIM/Private/IMG_1234.jpg content://media/external/images/19"
        val cleaned = DiagnosticPrivacy.sanitize(text)
        assertFalse(cleaned.contains("/storage/emulated"))
        assertFalse(cleaned.contains("content://"))
        assertFalse(cleaned.contains("IMG_1234.jpg"))
        assertTrue(cleaned.contains("REDACTED"))
    }

    @Test fun sensitiveFieldNamesAreRejected() {
        listOf("path", "mediaUri", "password", "pinCode", "credential", "gps", "location").forEach {
            assertTrue("$it should be sensitive", DiagnosticPrivacy.isSensitiveKey(it))
        }
        assertFalse(DiagnosticPrivacy.isSensitiveKey("durationMs"))
        assertFalse(DiagnosticPrivacy.isSensitiveKey("count"))
    }

    @Test fun exceptionHandlerAlwaysDelegatesOriginalFailure() {
        var recorded = false
        var delegated = false
        val error = IllegalStateException("failure")
        val handler = GalleryDiagnosticExceptionHandler(Thread.UncaughtExceptionHandler { _, actual ->
            delegated = actual === error
        }) { _, actual -> recorded = actual === error }
        handler.uncaughtException(Thread.currentThread(), error)
        assertTrue(recorded)
        assertTrue(delegated)
    }
}
