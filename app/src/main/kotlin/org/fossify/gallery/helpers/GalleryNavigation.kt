package org.fossify.gallery.helpers

/** Navigation scope and presentation mode must never be inferred from Activity names. */
enum class GallerySection { ALL, FAVORITES }
enum class GalleryContentView { FILES, FOLDERS }

data class GalleryScreen(
    val section: GallerySection,
    val view: GalleryContentView
) {
    val bottomSelection: GallerySection get() = if (isFavoriteFiles) GallerySection.ALL else section
    val isAllFiles: Boolean get() = section == GallerySection.ALL && view == GalleryContentView.FILES
    val isFavoriteFiles: Boolean get() = section == GallerySection.FAVORITES && view == GalleryContentView.FILES
    fun switchView(): GalleryScreen = copy(
        view = if (view == GalleryContentView.FILES) GalleryContentView.FOLDERS else GalleryContentView.FILES
    )
}

object GalleryNavigation {
    const val EXTRA_SECTION = "org.fossify.gallery.GALLERY_SECTION"

    /** Explicit navigation overrides legacy folder-only Intents. */
    fun resolveSection(explicit: String?, legacyFavoriteFolders: Boolean = false): GallerySection =
        when (explicit) {
            GallerySection.FAVORITES.name -> GallerySection.FAVORITES
            GallerySection.ALL.name -> GallerySection.ALL
            else -> if (legacyFavoriteFolders) GallerySection.FAVORITES else GallerySection.ALL
        }

    fun remembered(
        section: GallerySection, lastAllFiles: Boolean, lastFavoriteFiles: Boolean
    ): GalleryScreen = GalleryScreen(
        section,
        if (if (section == GallerySection.ALL) lastAllFiles else lastFavoriteFiles)
            GalleryContentView.FILES else GalleryContentView.FOLDERS
    )

    /** Bottom Favorites always means favorite FOLDERS; the pinned album owns favorite FILES. */
    fun bottomDestination(section: GallerySection, lastAllFiles: Boolean): GalleryScreen =
        if (section == GallerySection.FAVORITES) {
            GalleryScreen(GallerySection.FAVORITES, GalleryContentView.FOLDERS)
        } else {
            GalleryScreen(GallerySection.ALL,
                if (lastAllFiles) GalleryContentView.FILES else GalleryContentView.FOLDERS)
        }
}
