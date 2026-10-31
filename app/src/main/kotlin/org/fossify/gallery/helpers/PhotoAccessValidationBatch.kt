package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium

/** One publication only: never reuse a file's canonical-path evidence across stages. */
internal class PhotoAccessValidationBatch(
    private val context: Context,
    private val request: PhotoProfileLoadContext,
    private val cancelled: () -> Boolean,
    private val directVisibilityOnly: Boolean = false
) {
    private val scope = request.scope
    // With full filesystem access, visible() checks each raw/canonical ancestor
    // directly. A single-file first frame needs no global MediaStore .nomedia query.
    private val noMedia by lazy { if (directVisibilityOnly) emptyList<String>() else context.getNoMediaFoldersSync() }
    private val folderStatus = HashMap<String, Boolean>()
    private fun visible(folder: String) = folderStatus.getOrPut(folder) {
        folder.startsWith('/') && !folder.contains("/.") &&
            !FavoriteFolderRules.isExcluded(folder, scope.excluded) &&
            !FavoriteFolderRules.hasNoMediaAncestor(folder) &&
            noMedia.none { folder == it || folder.startsWith("$it/") }
    }

    fun validate(items: List<Medium>): Pair<ArrayList<Medium>, PhotoAccessSummary> {
        val folders = scope.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toSet()
        val includesFavorites = scope.sources.any { it.type == PhotoSourceType.FAVORITES }
        val favoritesAllowed = PhotoProfileAccessSessionManager.favoritesAuthorized(context, scope.sessionId)
        val favorites = if (includesFavorites && favoritesAllowed) request.favorites else emptySet()
        val candidates = items.asSequence().filter {
            it.type and scope.filter and PhotoProfileMediaLoader.MEDIA_TYPES != 0 && it.deletedTS == 0L &&
                !it.path.contains("/.") && (it.parentPath in folders || it.path in favorites)
        }.distinctBy { it.path }.toList()
        val sentinels = folders.map { "${it.trimEnd('/')}/__photo_scope__" }
        // Directory sentinels supply the source hint without canonicalizing every favorite
        // merely to show a bounded preview. Every displayed file still gets its own check.
        val favoriteRoots = if (includesFavorites) request.favorites.map {
            "${it.substringBeforeLast('/')}/__photo_scope__"
        }.toSet() else emptySet()
        val paths = (candidates.map { it.path } + sentinels + favoriteRoots).toSet()
        val started = GalleryPerfTrace.begin()
        val evidence = PhotoPathAccess.capture(paths, context::favoriteProtection, cancelled)
        val active = PhotoProfileAccessSessionManager.isActive(context, scope.sessionId)
        val decisions = HashMap<Map<String, FavoriteAccessSessionManager.Protection>, Boolean>()
        fun authorized(entry: PhotoPathAccess.Entry) = decisions.getOrPut(entry.locks) {
            PhotoProfileAccessSessionManager.bindingsAuthorize(entry.locks, scope.bindings, active)
        }
        val sourceDenied = evidence.values.any { !authorized(it) } ||
            (includesFavorites && !favoritesAllowed)
        val safe = candidates.filter { item ->
            if (cancelled()) throw PhotoLoadInvalidatedException()
            val entry = evidence.getValue(item.path)
            authorized(entry) && visible(item.parentPath) &&
                visible(entry.canonicalPath.substringBeforeLast('/', ""))
        }.map { it.copy(isFavorite = it.path in request.favorites) }.toCollection(ArrayList())
        GalleryPerfTrace.end("photo_access", started, paths.size)
        val publicOnly = scope.sessionId == null && evidence.values.all { it.locks.isEmpty() } &&
            (!includesFavorites || !context.config.isFavoritePasswordProtectionOn) &&
            (!includesFavorites || !context.config.isFolderProtected(FAVORITES))
        return safe to PhotoAccessSummary(sourceDenied, publicOnly)
    }
}

