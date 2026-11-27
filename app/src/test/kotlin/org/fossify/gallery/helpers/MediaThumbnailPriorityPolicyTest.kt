package org.fossify.gallery.helpers

import com.bumptech.glide.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaThumbnailPriorityPolicyTest {
    @Test fun visibleRowsLeadTheNextViewportAndFarPrefetch() {
        assertEquals(Priority.HIGH, MediaThumbnailPriorityPolicy.priority(12, 10, 15))
        assertEquals(Priority.NORMAL, MediaThumbnailPriorityPolicy.priority(9, 10, 15))
        assertEquals(Priority.NORMAL, MediaThumbnailPriorityPolicy.priority(20, 10, 15))
        assertEquals(Priority.LOW, MediaThumbnailPriorityPolicy.priority(22, 10, 15))
    }

    @Test fun initialBindingsPrioritizeTheEstimatedFirstViewport() {
        assertEquals(Priority.HIGH, MediaThumbnailPriorityPolicy.priority(0, -1, -1))
        assertEquals(Priority.HIGH, MediaThumbnailPriorityPolicy.priority(23, -1, -1))
        assertEquals(Priority.NORMAL, MediaThumbnailPriorityPolicy.priority(24, -1, -1))
        assertEquals(Priority.LOW, MediaThumbnailPriorityPolicy.priority(48, -1, -1))
    }
}
