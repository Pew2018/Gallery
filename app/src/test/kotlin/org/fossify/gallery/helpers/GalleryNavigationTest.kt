package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class GalleryNavigationTest {
    @Test fun fourScreensHaveDistinctScopesAndSelectedTabs() {
        val screens = GallerySection.values().flatMap { section ->
            GalleryContentView.values().map { GalleryScreen(section, it) }
        }
        assertEquals(4, screens.size)
        assertEquals(1, screens.count { it.isAllFiles })
        assertEquals(1, screens.count { it.isFavoriteFiles })
        screens.forEach { assertEquals(it.section, it.bottomSelection) }
    }

    @Test fun switchingViewsKeepsSection() {
        GallerySection.values().forEach { section ->
            GalleryContentView.values().forEach { view ->
                val original = GalleryScreen(section, view)
                assertEquals(section, original.switchView().section)
                assertEquals(original, original.switchView().switchView())
            }
        }
        assertEquals(
            GalleryScreen(GallerySection.FAVORITES, GalleryContentView.FILES),
            GalleryScreen(GallerySection.FAVORITES, GalleryContentView.FOLDERS).switchView()
        )
    }

    @Test fun independentViewPreferencesSurviveTenTabChanges() {
        var allFiles = true
        var favoritesFiles = false
        repeat(10) { iteration ->
            val all = GalleryNavigation.remembered(GallerySection.ALL, allFiles, favoritesFiles)
            val fav = GalleryNavigation.remembered(GallerySection.FAVORITES, allFiles, favoritesFiles)
            assertEquals(if (allFiles) GalleryContentView.FILES else GalleryContentView.FOLDERS, all.view)
            assertEquals(if (favoritesFiles) GalleryContentView.FILES else GalleryContentView.FOLDERS, fav.view)
            if (iteration % 2 == 0) allFiles = !allFiles else favoritesFiles = !favoritesFiles
        }
    }

    @Test fun explicitAndLegacyNavigationRequestsHaveCorrectPrecedence() {
        assertEquals(GallerySection.FAVORITES, GalleryNavigation.resolveSection(null, true))
        assertEquals(GallerySection.ALL, GalleryNavigation.resolveSection("ALL", true))
        assertEquals(GallerySection.FAVORITES, GalleryNavigation.resolveSection("FAVORITES"))
        assertEquals(GallerySection.ALL, GalleryNavigation.resolveSection(null))
    }

    @Test fun simulatedMediaAndFavoriteFolderCollectionsRemainDistinct() {
        val media = (1..400).map { "/DCIM/photo$it.jpg" }
        val starred = media.take(60).toSet()
        val savedFolders = (1..8).map { "/Pictures/folder$it" }
        assertEquals(400, media.size)
        assertEquals(60, media.count { it in starred })
        assertEquals(8, savedFolders.size)
        assertFalse(savedFolders.any { folder -> starred.any { it.startsWith("$folder/") } })
    }
}
