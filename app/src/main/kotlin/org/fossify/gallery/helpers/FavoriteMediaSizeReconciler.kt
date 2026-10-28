package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/**
 * Reconcile favorite media sizes with a single MediaStore index.
 * Paths are case-sensitive; missing items retain their existing size.
 */
internal object FavoriteMediaSizeReconciler {
    fun apply(favorites: List<Medium>, mediaByFolder: Map<String, ArrayList<Medium>>) {
        if (favorites.isEmpty() || mediaByFolder.isEmpty()) return

        val sizes = HashMap<String, Long>()
        for (folderMedia in mediaByFolder.values) {
            for (medium in folderMedia) {
                sizes[medium.path] = medium.size
            }
        }

        for (favorite in favorites) {
            sizes[favorite.path]?.let { favorite.size = it }
        }
    }
}
