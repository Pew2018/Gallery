package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test
import org.fossify.gallery.R

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

    @Test fun fixedBrowseSwitchBelongsOnlyToAllFilesRoot() {
        assertEquals(true, ToolbarQuickActions.hasFixedBrowseSwitch("", GallerySection.ALL))
        assertEquals(false, ToolbarQuickActions.hasFixedBrowseSwitch("/storage/DCIM/Camera", GallerySection.ALL))
        assertEquals(false, ToolbarQuickActions.hasFixedBrowseSwitch("", GallerySection.FAVORITES))
        assertEquals(false, ToolbarQuickActions.hasFixedBrowseSwitch("", null))
    }

    @Test fun fixedBrowseSwitchIsNotASelectableQuickAction() {
        assertEquals(false, ToolbarQuickActions.options(ToolbarQuickScope.HOME).contains("browse"))
        assertEquals(false, ToolbarQuickActions.options(ToolbarQuickScope.FOLDER).contains("browse"))
        assertEquals(
            listOf("search", "sort", "filter", "view", "columns"),
            ToolbarQuickActions.normalize(
                ToolbarQuickScope.HOME, listOf("search", "sort", "filter", "view", "columns")
            )
        )
    }

    @Test fun sortAndFilterUseDistinctToolbarIcons() {
        assertEquals(
            org.fossify.commons.R.drawable.ic_sort_vector,
            ToolbarQuickActions.iconRes(ToolbarQuickActions.SORT)
        )
        assertEquals(
            R.drawable.ic_quick_media_filter_vector,
            ToolbarQuickActions.iconRes(ToolbarQuickActions.FILTER)
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
