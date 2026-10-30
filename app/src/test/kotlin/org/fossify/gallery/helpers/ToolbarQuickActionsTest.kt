package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarQuickActionsTest {
    @Test fun defaultsPreserveClassicSearchSortFilter() {
        assertEquals(listOf("search", "sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.HOME, emptyList()))
        assertEquals(listOf("search", "sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER, emptyList()))
    }

    @Test fun scopesRejectItemsFromOtherPageFamilies() {
        assertEquals(listOf("sort"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.HOME,
                listOf("group", "filename", "sort")))
        assertEquals(listOf("filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER,
                listOf("excluded", "filter")))
    }

    @Test fun normalizationLimitsFiveWithoutDuplicatesAndRetainsSelectionOrder() {
        assertEquals(listOf("search", "sort", "filter", "view", "columns"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.HOME,
                listOf("search", "sort", "sort", "filter", "view", "columns", "hidden")))
    }

    @Test fun searchCanBeRemovedWithoutDiscardingOtherChoices() {
        assertEquals(listOf("sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER, listOf("sort", "filter")))
    }
}
