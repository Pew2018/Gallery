package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationOrderTest {
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
