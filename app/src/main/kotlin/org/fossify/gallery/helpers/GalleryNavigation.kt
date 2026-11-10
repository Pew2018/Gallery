package org.fossify.gallery.helpers

import android.os.Parcelable
import androidx.recyclerview.widget.RecyclerView

/** Navigation scope and presentation mode must never be inferred from Activity names. */
enum class GallerySection { ALL, FAVORITES, PHOTOS }
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
    const val LIST_FAVORITES_FILES = "favorites_files"

    // Root tab changes replace MainActivity/MediaActivity instances. Keep each
    // root list's native layout-manager anchor while the process remains alive.
    private val rootListStates = mutableMapOf<String, Parcelable>()

    fun rememberListState(screen: GalleryScreen, list: RecyclerView) {
        rememberListState(screen.key(), list)
    }

    fun rememberListState(key: String, list: RecyclerView) {
        list.layoutManager?.onSaveInstanceState()?.let { rootListStates[key] = it }
    }

    fun restoreListState(screen: GalleryScreen, list: RecyclerView) {
        restoreListState(screen.key(), list)
    }

    fun restoreListState(key: String, list: RecyclerView) {
        val state = rootListStates.remove(key) ?: return
        list.post {
            if (list.isAttachedToWindow && list.adapter != null) {
                list.layoutManager?.onRestoreInstanceState(state)
            } else {
                // Adapter publication can finish after the first scan callback.
                rootListStates.putIfAbsent(key, state)
            }
        }
    }

    private fun GalleryScreen.key(): String = "${section.name}:${view.name}"

    /** Explicit navigation overrides legacy folder-only Intents. */
    fun resolveSection(explicit: String?, legacyFavoriteFolders: Boolean = false): GallerySection =
        when (explicit) {
            GallerySection.PHOTOS.name -> GallerySection.PHOTOS
            GallerySection.FAVORITES.name -> GallerySection.FAVORITES
            GallerySection.ALL.name -> GallerySection.ALL
            else -> if (legacyFavoriteFolders) GallerySection.FAVORITES else GallerySection.ALL
        }

    fun remembered(
        section: GallerySection, lastAllFiles: Boolean, lastFavoriteFiles: Boolean
    ): GalleryScreen = GalleryScreen(
        section,
        if (section == GallerySection.PHOTOS || if (section == GallerySection.ALL) lastAllFiles else lastFavoriteFiles)
            GalleryContentView.FILES else GalleryContentView.FOLDERS
    )

    /** Normalized persistent order owns the launcher destination, independently of last tab. */
    fun launchDestination(order: List<String>, lastAllFiles: Boolean): GalleryScreen =
        bottomDestination(GallerySection.valueOf(NavigationOrder.normalize(order).first()), lastAllFiles)

    /** Bottom Favorites always means favorite FOLDERS; the pinned album owns favorite FILES. */
    fun bottomDestination(section: GallerySection, lastAllFiles: Boolean): GalleryScreen =
        if (section == GallerySection.PHOTOS) {
            GalleryScreen(GallerySection.PHOTOS, GalleryContentView.FILES)
        } else if (section == GallerySection.FAVORITES) {
            GalleryScreen(GallerySection.FAVORITES, GalleryContentView.FOLDERS)
        } else {
            GalleryScreen(GallerySection.ALL,
                if (lastAllFiles) GalleryContentView.FILES else GalleryContentView.FOLDERS)
        }
}

