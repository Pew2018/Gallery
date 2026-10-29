package org.fossify.gallery.helpers

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as TestConfig

@RunWith(RobolectricTestRunner::class)
@TestConfig(sdk = [28], application = Application::class)
class GalleryNavigationPersistenceTest {
    @Test fun preferencesPersistIndependentlyAcrossFreshConfigInstances() {
        val context = RuntimeEnvironment.getApplication()
        val original = Config.newInstance(context)
        val savedAll = original.lastMainViewAll
        val savedFavorites = original.lastFavoritesViewFiles
        val savedOrder = original.bottomNavigationOrder
        try {
            original.lastMainViewAll = true
            original.lastFavoritesViewFiles = false
            val reopened = Config.newInstance(context)
            assertTrue(reopened.lastMainViewAll)
            assertFalse(reopened.lastFavoritesViewFiles)

            reopened.lastFavoritesViewFiles = true
            val again = Config.newInstance(context)
            assertTrue(again.lastMainViewAll)
            assertTrue(again.lastFavoritesViewFiles)

            again.lastMainViewAll = false
            assertFalse(Config.newInstance(context).lastMainViewAll)
            assertTrue(Config.newInstance(context).lastFavoritesViewFiles)
        } finally {
            original.lastMainViewAll = savedAll
            original.lastFavoritesViewFiles = savedFavorites
            original.bottomNavigationOrder = savedOrder
        }
    }

    @Test fun oldNavigationOrderIdentifiersMigrateWithoutLosingReordering() {
        val context = RuntimeEnvironment.getApplication()
        val settings = Config.newInstance(context)
        val previous = settings.bottomNavigationOrder
        try {
            settings.bottomNavigationOrder = listOf("FAVORITE_FOLDERS", "ALL")
            assertEquals(listOf("FAVORITES", "ALL"), Config.newInstance(context).bottomNavigationOrder)
            settings.bottomNavigationOrder = listOf("ALL", "FAVORITES")
            assertEquals(listOf("ALL", "FAVORITES"), Config.newInstance(context).bottomNavigationOrder)
        } finally {
            settings.bottomNavigationOrder = previous
        }
    }
}
