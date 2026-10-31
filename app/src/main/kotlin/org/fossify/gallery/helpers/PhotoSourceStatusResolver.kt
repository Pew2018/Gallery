package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import java.io.File

internal enum class PhotoSourceStatus(val stringRes: Int) {
    READY(org.fossify.gallery.R.string.photo_source_ready),
    LOCKED(org.fossify.gallery.R.string.photo_source_locked),
    SETUP_REQUIRED(org.fossify.gallery.R.string.photo_source_setup),
    HIDDEN(org.fossify.gallery.R.string.photo_source_hidden),
    EXCLUDED(org.fossify.gallery.R.string.photo_source_excluded),
    MISSING(org.fossify.gallery.R.string.photo_source_missing),
    UNKNOWN(org.fossify.gallery.R.string.photo_source_unknown)
}

/** Performs one shared visibility/index/protection snapshot for an entire source list. */
internal object PhotoSourceStatusResolver {
    fun inspect(context: Context, sources: List<PhotoSource>): Map<PhotoSource, PhotoSourceStatus> = runCatching {
        val app = context.applicationContext
        val config = app.config
        val token = PhotoProfileAccessSessionManager.currentToken(app)
        val noMedia = app.getNoMediaFoldersSync()
        val indexed = MediaFetcher(app, PhotoProfileMediaLoader.MEDIA_TYPES).getFoldersToScan().toSet()
        sources.associateWith { source -> runCatching {
            if (source.type == PhotoSourceType.FAVORITES) {
                when {
                    PhotoProfileAccessSessionManager.favoritesAuthorized(app, token) -> PhotoSourceStatus.READY
                    config.photoCredentialSnapshot().enabled -> PhotoSourceStatus.LOCKED
                    else -> PhotoSourceStatus.SETUP_REQUIRED
                }
            } else {
                val path = source.path.orEmpty()
                when {
                    path.contains("/.") || FavoriteFolderRules.hasNoMediaAncestor(path) ||
                        noMedia.any { path == it || path.startsWith("$it/") } -> PhotoSourceStatus.HIDDEN
                    FavoriteFolderRules.isExcluded(path, config.excludedFolders) -> PhotoSourceStatus.EXCLUDED
                    !PhotoProfileAccessSessionManager.folderAuthorized(app, path, token) ->
                        if (config.photoCredentialSnapshot().enabled) PhotoSourceStatus.LOCKED else PhotoSourceStatus.SETUP_REQUIRED
                    !File(path).isDirectory && path !in indexed -> PhotoSourceStatus.MISSING
                    else -> PhotoSourceStatus.READY
                }
            }
        }.getOrDefault(PhotoSourceStatus.UNKNOWN) }
    }.getOrElse { sources.associateWith { PhotoSourceStatus.UNKNOWN } }
}
