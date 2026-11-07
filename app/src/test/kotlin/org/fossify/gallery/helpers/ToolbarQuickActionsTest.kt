package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun fixedPhotoProfileActionIsReservedBeforeOptionalActionsAtTargetWidths() {
        val widths = listOf(320, 360, 393, 480)
        val expectedSlots = listOf(1, 2, 2, 4)
        widths.zip(expectedSlots).forEach { (width, expected) ->
            assertEquals(expected, ToolbarQuickActions.availableSlots(
                widthDp = width, hasBack = false, showSearch = true, fixedActionCount = 1
            ))
            assertEquals(expected + 1, ToolbarQuickActions.availableSlots(
                widthDp = width, hasBack = false, showSearch = true, fixedActionCount = 0
            ))
        }
        assertTrue(ToolbarQuickActions.photoProfileManageVisible(true, false, false))
        assertFalse(ToolbarQuickActions.photoProfileManageVisible(false, false, false))
        assertFalse(ToolbarQuickActions.photoProfileManageVisible(true, true, false))
        assertFalse(ToolbarQuickActions.photoProfileManageVisible(true, false, true))
    }

    @Test fun profileUsesSavedFolderActionsIncludingHiddenAndGroupInOriginalOrder() {
        val home = listOf(ToolbarQuickActions.SEARCH, ToolbarQuickActions.VIEW)
        val folder = listOf(ToolbarQuickActions.SEARCH, ToolbarQuickActions.HIDDEN,
            ToolbarQuickActions.GROUP, ToolbarQuickActions.SORT, ToolbarQuickActions.FILTER)
        val expected = listOf(ToolbarQuickActions.HIDDEN, ToolbarQuickActions.GROUP,
            ToolbarQuickActions.SORT, ToolbarQuickActions.FILTER)

        for (width in listOf(320, 360, 393, 480)) {
            val profileSlots = ToolbarQuickActions.availableSlots(
                widthDp = width, hasBack = false, showSearch = true, fixedActionCount = 1
            )
            val physicalSlots = ToolbarQuickActions.availableSlots(
                widthDp = width, hasBack = true, showSearch = true
            )
            val profile = ToolbarQuickActions.plan(
                ToolbarQuickScope.FOLDER, folder, profileSlots
            ) { action ->
                ToolbarQuickActions.supportsFolderAction(
                    action, grid = true, horizontal = false,
                    hasPlayableMedia = false, photoProfile = true
                )
            }
            val physical = ToolbarQuickActions.plan(
                ToolbarQuickScope.FOLDER, folder, physicalSlots
            ) { action ->
                ToolbarQuickActions.supportsFolderAction(
                    action, grid = true, horizontal = false,
                    hasPlayableMedia = false, photoProfile = false
                )
            }
            assertEquals(expected.take(profileSlots), profile)
            assertEquals(expected.take(physicalSlots), physical)
            assertEquals(physical.take(profileSlots), profile)
            assertFalse(profile == ToolbarQuickActions.plan(
                ToolbarQuickScope.HOME, home, profileSlots
            ))
        }
        assertTrue(ToolbarQuickActions.folderActionEnabled(
            ToolbarQuickActions.HIDDEN, true, false, false
        ))
        assertFalse(ToolbarQuickActions.folderActionEnabled(
            ToolbarQuickActions.GROUP, true, true, false
        ))
        assertTrue(ToolbarQuickActions.folderActionEnabled(
            ToolbarQuickActions.GROUP, false, true, false
        ))
    }

    @Test fun sharedFolderEligibilityStillRespectsGridAndHorizontalMode() {
        assertFalse(ToolbarQuickActions.supportsFolderAction(
            ToolbarQuickActions.GROUP, grid = true, horizontal = true,
            hasPlayableMedia = true, photoProfile = true
        ))
        for (action in listOf(ToolbarQuickActions.COLUMNS, ToolbarQuickActions.FILENAME)) {
            assertFalse(ToolbarQuickActions.supportsFolderAction(
                action, grid = false, horizontal = false,
                hasPlayableMedia = true, photoProfile = true
            ))
        }
        // A profile's slideshow remains visible while loading; only its
        // enabled state changes. Physical folders retain their current policy.
        assertTrue(ToolbarQuickActions.supportsFolderAction(
            ToolbarQuickActions.SLIDESHOW, grid = true, horizontal = false,
            hasPlayableMedia = false, photoProfile = true
        ))
        assertFalse(ToolbarQuickActions.supportsFolderAction(
            ToolbarQuickActions.SLIDESHOW, grid = true, horizontal = false,
            hasPlayableMedia = false, photoProfile = false
        ))
        assertFalse(ToolbarQuickActions.folderActionEnabled(
            ToolbarQuickActions.SLIDESHOW, true, true, true
        ))
    }

    @Test fun photoProfileLayoutPlanDoesNotDependOnMediaAvailability() {
        val selected = listOf(ToolbarQuickActions.SEARCH, ToolbarQuickActions.FILTER,
            ToolbarQuickActions.SLIDESHOW, ToolbarQuickActions.SORT)
        listOf(320, 360, 393, 480).forEach { width ->
            val slots = ToolbarQuickActions.availableSlots(width, hasBack = false,
                showSearch = true, fixedActionCount = 1)
            val beforeLoad = ToolbarQuickActions.plan(ToolbarQuickScope.FOLDER, selected, slots) { true }
            val afterLoad = ToolbarQuickActions.plan(ToolbarQuickScope.FOLDER, selected, slots) { true }
            assertEquals(beforeLoad, afterLoad)
        }
    }

    @Test fun unchangedSlotBindingIsSkippedButReinflatedItemsAreInitialized() {
        assertFalse(ToolbarQuickActions.shouldBindSlot("sort", sameMenuItem = true, action = "sort"))
        assertTrue(ToolbarQuickActions.shouldBindSlot("sort", sameMenuItem = false, action = "sort"))
        assertTrue(ToolbarQuickActions.shouldBindSlot("sort", sameMenuItem = true, action = "filter"))
        assertFalse(ToolbarQuickActions.shouldBindSlot("sort", sameMenuItem = true, action = null))
    }

    @Test fun slideshowAvailabilityChangesEnabledStateWithoutChangingThePlan() {
        val selected = listOf(ToolbarQuickActions.SLIDESHOW, ToolbarQuickActions.SORT)
        val plan = ToolbarQuickActions.plan(ToolbarQuickScope.FOLDER, selected, 3)
        assertEquals(listOf(ToolbarQuickActions.SLIDESHOW, ToolbarQuickActions.SORT), plan)
        assertFalse(PhotoPlaybackPolicy.slideshowEnabled(hasPlayableMedia = false, needsUnlock = false))
        assertTrue(PhotoPlaybackPolicy.slideshowEnabled(hasPlayableMedia = true, needsUnlock = false))
        assertFalse(PhotoPlaybackPolicy.slideshowEnabled(hasPlayableMedia = true, needsUnlock = true))
        assertEquals(plan, ToolbarQuickActions.plan(ToolbarQuickScope.FOLDER, selected, 3))
    }
}
