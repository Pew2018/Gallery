package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteFolderRulesTest {
    @Test fun visibilityTogglesRemainIndependent() {
        assertFalse(FavoriteFolderRules.shouldShow(true, false, false, true))
        assertTrue(FavoriteFolderRules.shouldShow(true, false, true, false))
        assertFalse(FavoriteFolderRules.shouldShow(false, true, true, false))
        assertTrue(FavoriteFolderRules.shouldShow(false, true, false, true))
        assertFalse(FavoriteFolderRules.shouldShow(true, true, true, false))
        assertFalse(FavoriteFolderRules.shouldShow(true, true, false, true))
        assertTrue(FavoriteFolderRules.shouldShow(true, true, true, true))
        assertTrue(FavoriteFolderRules.shouldShow(false, false, false, false))
    }

    @Test fun excludedRootMatchesChildrenButNotSimilarlyNamedFolder() {
        val roots = listOf("/Pictures/Private", "/Movies/Hidden/")
        assertTrue(FavoriteFolderRules.isExcluded("/Pictures/Private", roots))
        assertTrue(FavoriteFolderRules.isExcluded("/Pictures/Private/2026", roots))
        assertTrue(FavoriteFolderRules.isExcluded("/Movies/Hidden/clip", roots))
        assertFalse(FavoriteFolderRules.isExcluded("/Pictures/PrivateBackup", roots))
    }
}
