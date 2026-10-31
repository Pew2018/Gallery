package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryScanEpochTest {
    @Test fun retirementAndNewRequestsInvalidateOldCallbacks() {
        val epoch = DirectoryScanEpoch()
        val first = epoch.begin()
        assertTrue(epoch.isCurrent(first))
        val second = epoch.begin()
        assertFalse(epoch.isCurrent(first))
        assertTrue(epoch.isCurrent(second))
        epoch.retire()
        assertFalse(epoch.isCurrent(second))
    }
}
