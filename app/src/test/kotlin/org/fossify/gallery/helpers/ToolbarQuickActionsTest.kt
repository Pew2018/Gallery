package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarQuickActionsTest {
    @Test fun defaultSelectionIsSearchSortFilter() {
        assertEquals(
            listOf("search", "sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.HOME, emptyList())
        )
        assertEquals(
            listOf("search", "sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER, emptyList())
        )
    }

    @Test fun pageScopesRejectIncompatibleActions() {
        assertEquals(
            listOf("sort"), ToolbarQuickActions.normalize(
                ToolbarQuickScope.HOME, listOf("group", "filename", "sort")
            )
        )
        assertEquals(
            listOf("filter"), ToolbarQuickActions.normalize(
                ToolbarQuickScope.FOLDER, listOf("excluded", "filter")
            )
        )
    }

    @Test fun selectionIsDistinctAndLimitedToFive() {
        assertEquals(
            listOf("search", "sort", "filter", "view", "columns"),
            ToolbarQuickActions.normalize(
                ToolbarQuickScope.HOME,
                listOf("search", "sort", "sort", "filter", "view", "columns", "hidden")
            )
        )
    }

    @Test fun searchCanBeOmittedButAlwaysSortsFirstWhenEnabled() {
        assertEquals(
            listOf("sort", "filter"),
            ToolbarQuickActions.normalize(ToolbarQuickScope.FOLDER, listOf("sort", "filter"))
        )
        assertEquals(
            listOf("search", "filter", "sort"),
            ToolbarQuickActions.normalize(
                ToolbarQuickScope.FOLDER, listOf("filter", "search", "sort")
            )
        )
    }

    @Test fun priorityAndSpaceProduceOrderedVisibleSlots() {
        assertEquals(
            listOf("filter", "sort"),
            ToolbarQuickActions.plan(
                ToolbarQuickScope.HOME, listOf("search", "filter", "sort", "view"), 2
            )
        )
        assertEquals(
            listOf("view"),
            ToolbarQuickActions.plan(
                ToolbarQuickScope.HOME, listOf("search", "filter", "sort", "view"), 4
            ) { it == "view" }
        )
    }

    @Test fun planRetainsHiddenPreferencesWhenNotSupportedOnCurrentPage() {
        assertEquals(
            emptyList<String>(),
            ToolbarQuickActions.plan(
                ToolbarQuickScope.HOME, listOf("slideshow"), 4
            ) { it != "slideshow" }
        )
        assertEquals(
            listOf("slideshow"),
            ToolbarQuickActions.plan(ToolbarQuickScope.HOME, listOf("slideshow"), 4)
        )
        assertEquals(
            emptyList<String>(),
            ToolbarQuickActions.plan(ToolbarQuickScope.HOME, listOf("sort"), 0)
        )
    }
}
