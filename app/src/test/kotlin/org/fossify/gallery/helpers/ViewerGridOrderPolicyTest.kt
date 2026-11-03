package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerGridOrderPolicyTest {
    @Test fun sortedGridOrderWinsOverLateIndependentlySortedFolderScan() {
        assertEquals(listOf("d", "b", "a", "c"),
            ViewerGridOrderPolicy.reconcile(listOf("d", "b", "a", "c"),
                listOf("b", "a"), listOf("a", "b", "c", "d"),
                random = false) { it })
    }

    @Test fun randomGridOrderIsNotRebuiltFromTheFirstFivePages() {
        val grid = listOf("e", "b", "d", "a", "c", "f")
        assertEquals(grid, ViewerGridOrderPolicy.reconcile(
            grid, listOf("d", "a", "c"), listOf("c", "e", "f", "a", "b", "d"),
            random = true) { it })
    }

    @Test fun removedFilesStayRemovedWhileNewFilesAppend() {
        assertEquals(listOf("a", "c", "d"),
            ViewerGridOrderPolicy.reconcile(listOf("a", "b", "c"),
                listOf("a", "b", "c"), listOf("c", "d", "a"),
                random = false) { it })
    }

    @Test fun missedSnapshotStillKeepsProgressiveRandomWindow() {
        assertEquals(listOf("b", "a", "c"),
            ViewerGridOrderPolicy.reconcile(null, listOf("b", "a"),
                listOf("a", "b", "c"), random = true) { it })
    }
}
