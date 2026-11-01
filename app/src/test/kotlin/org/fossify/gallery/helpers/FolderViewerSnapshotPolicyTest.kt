package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderViewerSnapshotPolicyTest {
    private val folder = "/storage/emulated/0/DCIM/Camera"
    private val one = "$folder/a.jpg"
    private val two = "$folder/b.jpg"

    @Test fun onlySinglePublicPhysicalFolderCanReuse() {
        assertTrue(FolderViewerSnapshotPolicy.isEligible(folder, one, listOf(one, two), false, false))
        assertFalse(FolderViewerSnapshotPolicy.isEligible(folder, one, listOf(one), true, false))
        assertFalse(FolderViewerSnapshotPolicy.isEligible(folder, one, listOf(one), false, true))
        assertFalse(FolderViewerSnapshotPolicy.isEligible(folder, one, listOf(one, "$folder/child/c.jpg"), false, false))
        assertFalse(FolderViewerSnapshotPolicy.isEligible(folder, "$folder/missing.jpg", listOf(one), false, false))
        assertFalse(FolderViewerSnapshotPolicy.isEligible("favorites", one, listOf(one), false, false))
    }

    @Test fun scanFreshnessMustMatchBeforeAndAfterForCatalogReuse() {
        val before = FolderReturnCheckpoint(folder, "settings", mapOf("external" to "1:2"), 42, 7, 100)
        val same = before.copy(capturedAt = 900)
        assertTrue(FolderReturnCheckpoint.sameSnapshot(before, same))
        assertFalse(FolderReturnCheckpoint.sameSnapshot(before, same.copy(generations = mapOf("external" to "1:3"))))
        assertFalse(FolderReturnCheckpoint.sameSnapshot(before, same.copy(directoryModified = 43)))
        assertFalse(FolderReturnCheckpoint.sameSnapshot(before, same.copy(configuration = "changed")))
        assertFalse(FolderReturnCheckpoint.sameSnapshot(before, same.copy(revision = 8)))
        assertFalse(FolderReturnCheckpoint.sameSnapshot(before, null))
    }
}
