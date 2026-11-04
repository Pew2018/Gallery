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

    @Test fun clickedGridOrderWinsAcrossIndexedFilesystemAndCompleteWithoutRevivingRemovedPaths() {
        val grid = (0 until 30).map { "/source/${29 - it}.jpg" }
        var published = grid.subList(13, 18)
        listOf(grid.shuffled(java.util.Random(1)), grid.reversed() + "/other/15.jpg",
            grid.reversed().filterNot { it == grid[2] } + "/other/15.jpg").forEach { stage ->
            published = ViewerGridOrderPolicy.reconcile(grid, published, stage, false) { it }
            assertEquals(grid.filter { it in stage } + stage.filter { it !in grid }, published)
            val selected = published.indexOf(grid[15])
            (-8..8).forEach { offset ->
                assertEquals(grid.filter { it in stage }[selected + offset], published[selected + offset])
            }
            assertEquals(published.size, published.distinct().size)
        }
    }

}
