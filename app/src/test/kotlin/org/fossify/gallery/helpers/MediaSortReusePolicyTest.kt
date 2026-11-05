package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSortReusePolicyTest {
    private fun eligible() = MediaSortReusePolicy.canReuse(
        photoProfile = false, showAll = false, physicalFolder = true, recycleBin = false,
        scanInProgress = false, requestCurrent = true, scanComplete = true,
        snapshotComplete = true, accessCurrent = true, simpleMediaRows = true
    )

    @Test fun completeCurrentFolderSnapshotCanBeResortedWithoutRescanning() {
        assertTrue(eligible())
    }

    @Test fun incompleteOrInvalidatedSnapshotsMustUseTheScanPath() {
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, false, true, false, true, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, false, true, true, false, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, true, true, true, true, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, false, false, true, true, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, false, true, true, true, false, true))
    }

    @Test fun protectedAggregateAndGroupedSourcesKeepTheirValidationPath() {
        assertFalse(MediaSortReusePolicy.canReuse(true, false, true, false, false, true, true, true, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, true, false, false, false, true, true, true, true, true))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, false, false, true, true, true, true, false))
        assertFalse(MediaSortReusePolicy.canReuse(false, false, true, true, false, true, true, true, true, true))
    }
}
