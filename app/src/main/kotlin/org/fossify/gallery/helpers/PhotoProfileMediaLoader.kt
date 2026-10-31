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
    private val sessionId: String? = null,
    private val cancelled: () -> Boolean = { false }
) {
    companion object {
        const val PATH = "__photo_profiles__"
        const val EXTRA_ID = "org.fossify.gallery.PHOTO_PROFILE_ID"
        val MEDIA_TYPES = TYPE_VIDEOS or TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS
    }
    private val config get() = context.config
    private val noMediaFolders by lazy { context.getNoMediaFoldersSync() }
    private fun stopped() = cancelled() || cancellationFetcher.shouldStop || Thread.currentThread().isInterrupted

    /** Temporary global visibility flags and explicit inclusion never bypass these rules. */
    fun folderAllowed(path: String): Boolean = runCatching {
        path.startsWith('/') && !path.contains("/.") && !FavoriteFolderRules.hasNoMediaAncestor(path) &&
            !FavoriteFolderRules.isExcluded(path, config.excludedFolders) &&
            PhotoProfileAccessSessionManager.folderAuthorized(context, path, sessionId) &&
            !noMediaFolders.any { parent -> path == parent || path.startsWith("$parent/") }
    }.getOrDefault(false)

    fun favoritesAllowed(): Boolean = PhotoProfileAccessSessionManager.favoritesAuthorized(context, sessionId)

    /** Applied again immediately before publishing; preferences can change during a scan. */
    fun permitted(profileId: String?, items: List<Medium>): ArrayList<Medium> {
        val profile = PhotoProfileRepository.of(context).state().profiles.firstOrNull { it.id == profileId } ?: return arrayListOf()
        val folders = profile.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toSet()
        val favorites = if (profile.sources.any { it.type == PhotoSourceType.FAVORITES } && favoritesAllowed())
            context.getFavoritePaths().toSet() else emptySet()
        // Pure membership and media-type filtering precede filesystem validation.
        // Every surviving item still receives a full raw/canonical protection check.
        val filter = config.photoMediaFilter
        val candidates = PhotoProfilePolicy.unique(items.filter { item ->
            item.type and filter and MEDIA_TYPES != 0 &&
                item.deletedTS == 0L && !item.path.contains("/.") &&
                (item.parentPath in folders || item.path in favorites)
        }) { it.path }
        if (candidates.isEmpty()) return arrayListOf()
        val folderStatus = HashMap<String, Boolean>()
        // Check actual file identities as well as directory sentinels: a file symlink
        // can cross an additional protection boundary even when its parent is public.
        val snapshot = runCatching {
            PhotoPathAccess.capture(candidates.map { it.path }, context::favoriteProtection, ::stopped)
        }.getOrNull() ?: return arrayListOf()
        val bindings = config.photoSourceBindings
        val active = PhotoProfileAccessSessionManager.isActive(context, sessionId)
        val lockStatus = HashMap<Map<String, FavoriteAccessSessionManager.Protection>, Boolean>()
        return PhotoProfilePolicy.unique(candidates.filter { item ->
            val identity = snapshot[item.path] ?: return@filter false
            val resolvedParent = identity.canonicalPath.substringBeforeLast('/', "")
            lockStatus.getOrPut(identity.locks) {
                PhotoProfileAccessSessionManager.bindingsAuthorize(identity.locks, bindings, active)
            } &&
                folderStatus.getOrPut(resolvedParent) { folderAllowed(resolvedParent) } &&
            item.type and filter and MEDIA_TYPES != 0 && item.deletedTS == 0L && !item.path.contains("/.") &&
                PhotoProfilePolicy.includes(item.path, folders, favorites, true) {
                    folderStatus.getOrPut(item.parentPath) { folderAllowed(item.parentPath) }
                }
        }) { it.path }.toCollection(ArrayList())
    }

    fun load(profileId: String?): ArrayList<ThumbnailItem> {
        val profile = PhotoProfileRepository.of(context).state().profiles.firstOrNull { it.id == profileId } ?: return arrayListOf()
        val favorites = context.getFavoritePaths()
        val includeFavorites = profile.sources.any { it.type == PhotoSourceType.FAVORITES } && favoritesAllowed()
        val folderStatus = HashMap<String, Boolean>()
        fun allowed(folder: String) = folderStatus.getOrPut(folder) { folderAllowed(folder) }
        val folders = profile.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }
            .filter(::allowed).toSet()
        val favoriteFiles = if (includeFavorites) favorites.filter {
            it.substringBeforeLast('/') !in folders && allowed(it.substringBeforeLast('/'))
        }.toSet() else emptySet()
        val fetcher = MediaFetcher(context, config.photoMediaFilter and MEDIA_TYPES, ::stopped)
        if (stopped()) return arrayListOf()
        val media = fetcher.getPhotoProfileMedia(folders, favoriteFiles, favorites)
        if (stopped()) return arrayListOf()
        val safe = permitted(profileId, media)
        safe.forEach { if (it.taken <= 0L) it.taken = it.modified }
        fetcher.sortMedia(safe, config.getFolderSorting(PATH))
        // No folder grouping: keep the configured global ordering in grid and viewer.
        return ArrayList(safe)
    }
}
