package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class NavigationOrderTest {
    @Test fun reorderedItemMovesUseCurrentBoundaries() {
        val items = mutableListOf("ALL", "FAVORITES", "PHOTOS")
        assertTrue(moveNavigationItem(items, 2, 1))
        assertEquals(listOf("ALL", "PHOTOS", "FAVORITES"), items)
        assertTrue(moveNavigationItem(items, 1, 0))
        assertEquals(listOf("PHOTOS", "ALL", "FAVORITES"), items)
        assertFalse(moveNavigationItem(items, -1, 0))
        assertFalse(moveNavigationItem(items, 2, 3))
        assertEquals(listOf("PHOTOS", "ALL", "FAVORITES"), items)
    }

    @Test fun migrationPreservesRelativeOrder() {
        assertEquals(listOf("FAVORITES", "ALL", "PHOTOS"),
            NavigationOrder.normalize(listOf("PRIVATE", "FAVORITE_FOLDERS", "ALL")))
    }
    @Test fun malformedSettingsRecoverMissingTabsWithoutDuplicates() {
        assertEquals(listOf("FAVORITES", "ALL", "PHOTOS"),
            NavigationOrder.normalize(listOf("FAVORITE_FOLDERS", "UNKNOWN", "FAVORITE_FOLDERS")))
        assertEquals(NavigationOrder.defaults, NavigationOrder.normalize(emptyList()))
    }
    @Test fun savedOrderSurvivesReload() {
        val saved = listOf("PHOTOS", "FAVORITES", "ALL")
        assertEquals(saved, NavigationOrder.normalize(saved.joinToString(",").split(",")))
    }
}
