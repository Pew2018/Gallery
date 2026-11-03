package org.fossify.gallery.helpers

import android.content.Context
import android.provider.MediaStore.Files
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.extensions.getDoesFilePathExist
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
    private val folderStatus = HashMap<Pair<String, Boolean>, Boolean>()
    private fun visible(folder: String, explicit: Boolean, noMedia: List<String>) =
        folderStatus.getOrPut(folder to explicit) {
            PhotoProfileVisibilityPolicy.visible(folder, explicit, scope.excluded, noMedia)
        }

    fun validate(items: List<Medium>): Pair<ArrayList<Medium>, PhotoAccessSummary> {
        val folders = scope.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }.toSet()
        val includesFavorites = scope.sources.any { it.type == PhotoSourceType.FAVORITES }
        val favoritesAllowed = PhotoProfileAccessSessionManager.favoritesAuthorized(context, scope.sessionId)
        val boundedFavorites = request.favoriteMembershipPaths != null
        val favorites = if (includesFavorites && favoritesAllowed) request.favorites else emptySet()
        val candidates = items.asSequence().filter {
            it.type and scope.filter and PhotoProfileMediaLoader.MEDIA_TYPES != 0 && it.deletedTS == 0L &&
                !it.path.contains("/.") && java.io.File(it.path).parent == it.parentPath &&
                (it.parentPath in folders || it.path in favorites)
        }.distinctBy { it.path }.toList()
        val sentinels = folders.map { "${it.trimEnd('/')}/__photo_scope__" }
        // Directory sentinels supply the source hint without canonicalizing every favorite
        // merely to show a bounded preview. Every displayed file still gets its own check.
        // Full loads retain their source sentinels. A neighbor request already has
        // fresh point membership for each candidate; enumerating all favorite roots
        // here would turn a five-item window back into O(all favorites) filesystem work.
        val favoriteRoots = if (includesFavorites && !boundedFavorites) request.favorites.map {
            "${it.substringBeforeLast('/')}/__photo_scope__"
        }.toSet() else emptySet()
        val paths = (candidates.map { it.path } + sentinels + favoriteRoots).toSet()
        val started = GalleryPerfTrace.begin()
        val evidence = PhotoPathAccess.capture(paths, context::favoriteProtection, cancelled)
        val noMedia = if (directVisibilityOnly) {
            // Match the full validator's MediaStore .nomedia rule with point-like
            // checks for ancestors of these candidates, rather than scanning every
            // indexed .nomedia marker in the device library.
            val ancestors = evidence.flatMap { (path, entry) ->
                sequenceOf(entry.canonicalPath, path)
                    .filter { it.isNotEmpty() }
                    .flatMap { path ->
                        var folder = java.io.File(path).absoluteFile.parentFile
                        generateSequence(folder) { it.parentFile }.map { it.absolutePath }
                    }.map { "$it/.nomedia" }
                    .toList()
            }.distinct()
            queryNoMediaMarkers(ancestors)
        } else if (!includesFavorites) {
            // Every candidate belongs to an explicitly chosen folder, where
            // .nomedia is intentionally bypassed. No library-wide marker query
            // is needed for each validated scan batch.
            emptyList()
        } else context.getNoMediaFoldersSync()
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
            val explicit = item.parentPath in folders
            authorized(entry) && visible(item.parentPath, explicit, noMedia) &&
                visible(entry.canonicalPath.substringBeforeLast('/', ""), explicit, noMedia)
        }.map { it.copy(isFavorite = it.path in request.favorites) }.toCollection(ArrayList())
        GalleryPerfTrace.end("photo_access", started, paths.size)
        val publicOnly = scope.sessionId == null && evidence.values.all { it.locks.isEmpty() } &&
            (!includesFavorites || !context.config.isFavoritePasswordProtectionOn) &&
            (!includesFavorites || !context.config.isFolderProtected(FAVORITES))
        return safe to PhotoAccessSummary(sourceDenied, publicOnly)
    }

    private fun queryNoMediaMarkers(markers: List<String>): List<String> {
        if (markers.isEmpty()) return emptyList()
        val uri = Files.getContentUri("external")
        val projection = arrayOf(Files.FileColumns.DATA)
        val selection = "${Files.FileColumns.MEDIA_TYPE} = ? AND ${Files.FileColumns.DATA} IN (${markers.joinToString(",") { "?" }})"
        val args = arrayOf(Files.FileColumns.MEDIA_TYPE_NONE.toString(), *markers.toTypedArray())
        val folders = LinkedHashSet<String>()
        runCatching {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val column = cursor.getColumnIndex(Files.FileColumns.DATA)
                if (column >= 0) while (cursor.moveToNext()) {
                    val path = cursor.getString(column) ?: continue
                    if (path.endsWith("/.nomedia") && context.getDoesFilePathExist(path, context.config.OTGPath)) {
                        folders.add(path.removeSuffix("/.nomedia"))
                    }
                }
            }
        }
        return folders.toList()
    }
}

