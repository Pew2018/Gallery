package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoriteFolderRulesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun visibilityTogglesRemainIndependent() {
        for (showHidden in listOf(false, true)) {
            for (showExcluded in listOf(false, true)) {
                assertTrue(FavoriteFolderRules.shouldShow(false, false, showHidden, showExcluded))
                assertEquals(showHidden,
                    FavoriteFolderRules.shouldShow(true, false, showHidden, showExcluded))
                assertEquals(showExcluded,
                    FavoriteFolderRules.shouldShow(false, true, showHidden, showExcluded))
                assertEquals(showHidden && showExcluded,
                    FavoriteFolderRules.shouldShow(true, true, showHidden, showExcluded))
            }
        }
    }

    @Test fun excludedRootMatchesDescendantsButNotPrefixes() {
        val roots = listOf("/Pictures/Private/", "/Movies/Hidden/")
        assertTrue(FavoriteFolderRules.isExcluded("/Pictures/Private", roots))
        assertTrue(FavoriteFolderRules.isExcluded("/Pictures/Private/2026/", roots))
        assertTrue(FavoriteFolderRules.isExcluded("/Pictures/Temp/../Private/a", roots))
        assertTrue(FavoriteFolderRules.isExcluded("/Movies/Hidden/clip", roots))
        assertFalse(FavoriteFolderRules.isExcluded("/Pictures/PrivateBackup", roots))
        assertFalse(FavoriteFolderRules.isExcluded("/Pictures/private", roots))
        assertFalse(FavoriteFolderRules.isExcluded("", roots))
        assertFalse(FavoriteFolderRules.isExcluded("/Pictures/Other", listOf("")))
    }

    @Test fun dotHiddenAndNoMediaRemainSeparate() {
        assertTrue(FavoriteFolderRules.isDotHidden("/Pictures/.private/2026"))
        assertFalse(FavoriteFolderRules.isDotHidden("/Pictures/private"))
        val parent = temp.newFolder("parent")
        val child = File(parent, "child").apply { mkdir() }
        assertFalse(FavoriteFolderRules.hasNoMediaAncestor(child.absolutePath))
        File(parent, ".nomedia").writeText("")
        assertTrue(FavoriteFolderRules.hasNoMediaAncestor(child.absolutePath))
        assertFalse(FavoriteFolderRules.isDotHidden(child.absolutePath))
        assertFalse(FavoriteFolderRules.hasNoMediaAncestor(""))
    }

    @Test fun savedFavoriteWithoutMetadataIsStillEligible() {
        val persistedFavoritePath = "/storage/emulated/0/Pictures/OnlyInFavoriteDb"
        assertFalse(FavoriteFolderRules.isExcluded(persistedFavoritePath, emptyList()))
        assertTrue(FavoriteFolderRules.shouldShow(false, false, false, false))
    }
}
