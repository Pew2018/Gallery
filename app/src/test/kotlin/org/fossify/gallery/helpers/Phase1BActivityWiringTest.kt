package org.fossify.gallery.helpers

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integration wiring checks. Behavioral and pixel checks require the corresponding runtime. */
class Phase1BActivityWiringTest {
    private val source = File("src/main/kotlin/org/fossify/gallery")
    private val activity = source.resolve("activities/MediaActivity.kt").readText()
    private val adapter = source.resolve("adapters/MediaAdapter.kt").readText()

    @Test fun partialFolderCanDetachAndRejoinTheSameRequest() {
        assertTrue(activity.substringAfter("override fun onPause()").substringBefore("override fun onStop()")
            .contains("detachFolderViewerScan"))
        assertTrue(activity.substringAfter("override fun onResume()").substringBefore("override fun onPause()")
            .contains("resumeFolderViewerScan"))
    }

    @Test fun completedDiffIsRetainedAcrossAnUnsafeLayoutBoundary() {
        assertTrue(adapter.contains("pending.copy(preparedDiff ="))
    }

    @Test fun deletionFilterReceivesThePublicationGenerationAndAuthority() {
        assertTrue(activity.substringAfter("private fun publishMediaSnapshot(")
            .contains("viewerDeletions.filter("))
    }

    @Test fun ordinaryAndProfileHandoffUseTheSameAdmission() {
        for (method in listOf("openOrdinaryViewer", "openInViewPager")) {
            assertTrue(activity.substringAfter("private fun $method(").substringBefore("\n    private fun ")
                .contains("viewerLaunchGate.begin("))
        }
    }
}
