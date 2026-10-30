package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
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
            context.getSharedPrefs().edit().putString("oxygen_bottom_nav_order", "FAVORITE_FOLDERS,ALL").commit()
            assertEquals(listOf("FAVORITES", "ALL", "PHOTOS"), Config.newInstance(context).bottomNavigationOrder)
            settings.bottomNavigationOrder = listOf("PHOTOS", "ALL", "FAVORITES")
            assertEquals(listOf("PHOTOS", "ALL", "FAVORITES"), Config.newInstance(context).bottomNavigationOrder)
        } finally {
            settings.bottomNavigationOrder = previous
        }
    }
}
