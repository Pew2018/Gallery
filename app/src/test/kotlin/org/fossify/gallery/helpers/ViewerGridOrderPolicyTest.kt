package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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


    @Test fun successfulRenameRetainsOrdinalButFreshAuthorizationStillOwnsMembership() {
        val order = ViewerGridOrderPolicy.renamed(listOf("a", "b", "c"), "b", "renamed")
        assertEquals(listOf("a", "renamed", "c"), order)
        assertEquals(order, ViewerGridOrderPolicy.reconcile(order, emptyList(),
            listOf("c", "a", "renamed"), false) { it })
        assertEquals(listOf("a", "c"), ViewerGridOrderPolicy.reconcile(order, emptyList(),
            listOf("a", "c"), false) { it })
    }


    @Test fun segmentedDirectionNeverInheritsAReversedGridOrAnInterleavedNewPath() {
        val all = (0 until 20_501).map { "/Gallery/$it.jpg" }
        val window = all.subList(9_880, 10_121)
        val reversedGrid = all.reversed()
        val (authority, inherited) = ViewerGridOrderPolicy.reconcilePhotoCatalog(
            reversedGrid, emptyList(), window, true) { it }
        assertFalse(inherited)
        assertEquals(window, authority)
        assertEquals(all[10_001], authority[authority.indexOf(all[10_000]) + 1])
        assertEquals(all[9_999], authority[authority.indexOf(all[10_000]) - 1])
        val matching = ViewerGridOrderPolicy.reconcilePhotoCatalog(all, emptyList(), window, true) { it }
        assertTrue(matching.second)
        assertEquals(window, matching.first)
        val added = listOf("/Gallery/new.jpg") + window
        val changed = ViewerGridOrderPolicy.reconcilePhotoCatalog(all, emptyList(), added, true) { it }
        assertFalse(changed.second)
        assertEquals(added, changed.first)
        // A fully resident catalog has no independent segment direction contract.
        val resident = ViewerGridOrderPolicy.reconcilePhotoCatalog(reversedGrid, emptyList(), window, false) { it }
        assertTrue(resident.second)
        assertEquals(window.reversed(), resident.first)
    }

}
