package org.fossify.gallery.helpers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 16 / targetSdk 36 enables system predictive back by default, but an
 * explicit app-level opt-in and any per-Activity opt-out must stay visible in review.
 * This checks the manifest source; the merged manifest is checked again by the build.
 */
class PredictiveBackManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun applicationOptsIntoSystemBack() {
        val application = manifest.substringAfter("<application", missingDelimiterValue = "")
        assertTrue(
            "the application must keep predictive back enabled",
            application.substringBefore(">").contains("android:enableOnBackInvokedCallback=\"true\"")
        )
    }

    @Test fun noActivityOptsOutOfSystemBack() {
        assertFalse(
            "an opt-out would suppress the system cross-Activity preview",
            manifest.contains("android:enableOnBackInvokedCallback=\"false\"")
        )
    }
}
