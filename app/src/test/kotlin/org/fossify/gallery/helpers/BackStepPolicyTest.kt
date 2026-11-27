package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bottom bar root must never keep an in-app back step: back has to reach the
 * system so it can preview the exit. Only real upper states keep the step.
 */
class BackStepPolicyTest {

    @Test
    fun albumsRootWithNoUpperStateLeavesBackToTheSystem() {
        assertFalse(albums(grouped = true, opened = 1))
        assertFalse(albums(grouped = false, opened = 1))
    }

    @Test
    fun albumsInlineSearchKeepsItsOwnStep() {
        assertTrue(albums(search = true, grouped = false, opened = 1))
    }

    @Test
    fun albumsNestedSubfolderClimbsOneLevelFirst() {
        assertTrue(albums(grouped = true, opened = 2))
        // Returning to the tab root is not a step, even with grouping enabled.
        assertFalse(albums(grouped = true, opened = 1))
        // A stale prefix must not keep the callback alive when grouping is off.
        assertFalse(albums(grouped = false, opened = 3))
    }

    @Test
    fun mediaRootsOnlyOwnInlineSearch() {
        assertFalse(BackStepPolicy.mediaOwnsBackStep(searchOpen = false))
        assertTrue(BackStepPolicy.mediaOwnsBackStep(searchOpen = true))
    }

    @Test
    fun settingsHubLeavesBackToTheSystem() {
        assertFalse(BackStepPolicy.settingsOwnsBackStep(categoryOpen = false))
        assertTrue(BackStepPolicy.settingsOwnsBackStep(categoryOpen = true))
    }

    private fun albums(search: Boolean = false, grouped: Boolean, opened: Int): Boolean =
        BackStepPolicy.albumsOwnsBackStep(search, grouped, opened)
}
