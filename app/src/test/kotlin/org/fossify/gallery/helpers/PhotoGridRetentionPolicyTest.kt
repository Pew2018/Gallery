package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The configuration page is a child of the authorized Photos grid, not a
 * departure from Photos. The platform's cross-Activity preview needs the real
 * grid snapshot, so the adapter must survive the child-page stop.
 */
class PhotoGridRetentionPolicyTest {
    @Test fun coveringConfigurationPageKeepsTheDisplayedGrid() {
        assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(
            isPhotoProfile = true, viewerTransition = false, childPageCovered = true))
    }

    @Test fun genuinePhotoDepartureStillClearsAndRevalidates() {
        assertTrue(PhotoGridRetentionPolicy.clearsDisplayedGrid(
            isPhotoProfile = true, viewerTransition = false, childPageCovered = false))
    }

    @Test fun viewerTransitionNeverClearsTheGrid() {
        assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(
            isPhotoProfile = true, viewerTransition = true, childPageCovered = false))
    }

    @Test fun nonPhotoPagesAreUnaffected() {
        assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(
            isPhotoProfile = false, viewerTransition = false, childPageCovered = false))
    }
}
