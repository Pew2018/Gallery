package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchRequestPolicyTest {
    @Test fun displayedSnapshotRemainsSearchableAcrossRefreshRequest() {
        assertTrue(MediaSearchRequestPolicy.canPublish(4, 4, true, true))
        assertFalse(MediaSearchRequestPolicy.canPublish(4, 5, true, true))
        assertFalse(MediaSearchRequestPolicy.canPublish(4, 4, true, false))
        assertFalse(MediaSearchRequestPolicy.canPublish(4, 4, false, true))
    }
}
