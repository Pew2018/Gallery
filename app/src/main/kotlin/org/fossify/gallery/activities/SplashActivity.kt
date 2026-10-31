package org.fossify.gallery.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.helpers.GalleryNavigation
import org.fossify.gallery.helpers.GallerySection
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.BuildConfig
import org.fossify.commons.extensions.appLaunched

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // check if previously selected favorite items have been properly migrated into the new Favorites table
        if (config.wereFavoritesMigrated) {
            launchActivity()
        } else {
            if (config.appRunCount == 0) {
                config.wereFavoritesMigrated = true
                launchActivity()
            } else {
                config.wereFavoritesMigrated = true
                ensureBackgroundThread {
                    val favorites = ArrayList<Favorite>()
                    val favoritePaths = mediaDB.getFavorites().map { it.path }.toMutableList() as ArrayList<String>
                    favoritePaths.forEach {
                        favorites.add(getFavoriteFromPath(it))
                    }
                    favoritesDB.insertAll(favorites)

                    runOnUiThread {
                        launchActivity()
                    }
                }
            }
        }
    }

    private fun launchActivity() {
        val lastAllFiles = config.lastMainViewAll
        val screen = GalleryNavigation.launchDestination(config.bottomNavigationOrder, lastAllFiles)
        // Materialize the legacy fallback before changing the active section's showAll flag.
        config.lastMainViewAll = lastAllFiles
        config.showAll = screen.isAllFiles
        val destination = if (screen.section == GallerySection.PHOTOS) {
            // MainActivity used to record every launcher visit. Keep that bookkeeping
            // when the launcher now goes directly to a media root.
            appLaunched(BuildConfig.APPLICATION_ID)
            Intent(this, MediaActivity::class.java).putExtra(DIRECTORY,
                PhotoProfileMediaLoader.PATH)
        } else {
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_FOLDERS, !screen.isAllFiles)
                .putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, screen.section == GallerySection.FAVORITES)
        }
        destination.putExtra(GalleryNavigation.EXTRA_SECTION, screen.section.name)
        startActivity(destination)
        finish()
    }
}

