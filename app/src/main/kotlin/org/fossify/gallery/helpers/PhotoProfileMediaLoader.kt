package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** No capability is inherited from Favorites or a previously unlocked folder. */
class PhotoProfileMediaLoader(
    private val context: Context,
    private val cancellationFetcher: MediaFetcher = MediaFetcher(context),
    private val cancelled: () -> Boolean = { false }
) {
    companion object {
        const val PATH = "__photo_profiles__"
        const val EXTRA_ID = "org.fossify.gallery.PHOTO_PROFILE_ID"
        val IMAGE_TYPES = TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS
    }
    private val config get() = context.config
    private val noMediaFolders by lazy { context.getNoMediaFoldersSync() }
    private fun stopped() = cancelled() || cancellationFetcher.shouldStop || Thread.currentThread().isInterrupted

    /** Temporary global visibility flags and explicit inclusion never bypass these rules. */
    fun folderAllowed(path: String): Boolean = runCatching {
        path.startsWith('/') && !path.contains("/.") && !FavoriteFolderRules.hasNoMediaAncestor(path) &&
            !FavoriteFolderRules.isExcluded(path, config.excludedFolders) &&
            FolderUnlockSessionManager.findProtectedAncestor(path, config::isFolderProtected) == null &&
            !config.isFolderProtected(path) &&
            !noMediaFolders.any { parent -> path == parent || path.startsWith("$parent/") }
    }.getOrDefault(false)

    fun favoritesAllowed(): Boolean = !config.isFavoritePasswordProtectionOn && !config.isFolderProtected(FAVORITES)

    /** Applied again immediately before publishing; preferences can change during a scan. */
    fun permitted(profileId: String?, items: List<Medium>): ArrayList<Medium> {
        val profile = PhotoProfileRepository.of(context).state().profiles.firstOrNull { it.id == profileId } ?: return arrayListOf()
        val folders = profile.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toSet()
        val favorites = if (profile.sources.any { it.type == PhotoSourceType.FAVORITES } && favoritesAllowed())
            context.getFavoritePaths().toSet() else emptySet()
        val folderStatus = HashMap<String, Boolean>()
        return PhotoProfilePolicy.unique(items.filter { item ->
            item.type and IMAGE_TYPES != 0 && item.deletedTS == 0L && !item.path.contains("/.") &&
                PhotoProfilePolicy.includes(item.path, folders, favorites, true) {
                    folderStatus.getOrPut(item.parentPath) { folderAllowed(item.parentPath) }
                }
        }) { it.path }.toCollection(ArrayList())
    }

    fun load(profileId: String?): ArrayList<ThumbnailItem> {
        val profile = PhotoProfileRepository.of(context).state().profiles.firstOrNull { it.id == profileId } ?: return arrayListOf()
        val favorites = context.getFavoritePaths()
        val includeFavorites = profile.sources.any { it.type == PhotoSourceType.FAVORITES } && favoritesAllowed()
        val folders = (profile.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path } +
            if (includeFavorites) favorites.map { it.substringBeforeLast('/') } else emptyList()).distinct().filter(::folderAllowed)
        val fetcher = MediaFetcher(context, IMAGE_TYPES, ::stopped)
        if (stopped()) return arrayListOf()
        val dates = fetcher.getDateTakens()
        val modified = fetcher.getLastModifieds()
        val grouped = fetcher.getAndroid11FolderMedia(true, false, favorites, false, true, HashMap(dates))
        val media = ArrayList<Medium>()
        for (folder in folders) {
            if (stopped()) { fetcher.shouldStop = true; return arrayListOf() }
            media.addAll(fetcher.getFilesFrom(folder, true, false, true, true, true, favorites, false, modified, dates, grouped))
        }
        if (stopped()) return arrayListOf()
        val safe = permitted(profileId, media)
        safe.forEach { if (it.taken <= 0L) it.taken = it.modified }
        fetcher.sortMedia(safe, config.getFolderSorting(PATH))
        // No folder grouping: keep the configured global ordering in grid and viewer.
        return ArrayList(safe)
    }
}
