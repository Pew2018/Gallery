package org.fossify.gallery.helpers

import org.fossify.gallery.models.ThumbnailSection

/** Same post-load boundary for cache, scans, pager preloading and slideshow. */
internal object FavoriteAccessPolicy {
    data class AuthorizedBatch<T>(
        val items: ArrayList<T>,
        val validation: FavoriteAccessSessionManager.ValidatedPaths
    )

    fun unprotectedSnapshot(snapshot: FavoriteAccessSessionManager.Snapshot): FavoriteAccessSessionManager.Snapshot =
        FavoriteAccessSessionManager.Snapshot(snapshot.entries.filterValues { it.locks.isEmpty() })

    fun unprotectedPaths(snapshot: FavoriteAccessSessionManager.Snapshot): Set<String> =
        unprotectedSnapshot(snapshot).entries.keys

    fun hasProtectedFavorites(snapshot: FavoriteAccessSessionManager.Snapshot): Boolean =
        snapshot.entries.values.any { it.locks.isNotEmpty() }

    /**
     * Performs one complete snapshot check for this batch. This method can
     * touch the filesystem and must be called off the UI thread.
     */
    fun <T> authorizedItems(token: String?, items: Collection<T>, pathOf: (T) -> String?): ArrayList<T> {
        return validateItems(token, items, pathOf)?.items ?: arrayListOf()
    }

    /** Viewer continuation: one full worker capture, followed only by a cheap publication check. */
    fun <T> validateViewerItems(
        token: String?, items: Collection<T>, pathOf: (T) -> String?, requiredPath: String
    ): AuthorizedBatch<T>? {
        val paths = LinkedHashSet<String>().apply {
            add(requiredPath)
            items.mapNotNullTo(this, pathOf)
        }
        val validation = FavoriteAccessSessionManager.validatePaths(token, paths) ?: return null
        if (requiredPath !in validation.paths) return null
        val safe = items.filter { item ->
            pathOf(item)?.let { FavoriteAccessSessionManager.isInValidatedPaths(validation, it) }
                ?: (item is ThumbnailSection)
        }
        return if (FavoriteAccessSessionManager.isPublicationCurrent(validation))
            AuthorizedBatch(ArrayList(safe), validation) else null
    }

    /** Progressive scan: check each member with the same selected-file boundary.
     * The final stage still reconciles the complete capability using validateViewerItems.
     */
    fun <T> validateViewerPreview(
        token: String?, items: Collection<T>, pathOf: (T) -> String?, requiredPath: String
    ): AuthorizedBatch<T>? {
        val selected = FavoriteAccessSessionManager.validateSelectedPath(token, requiredPath) ?: return null
        val safe = ArrayList<T>()
        for (item in items) {
            if (Thread.currentThread().isInterrupted) return null
            val path = pathOf(item) ?: continue
            val validation = if (path == requiredPath) selected else
                FavoriteAccessSessionManager.validateSelectedPath(token, path) ?: continue
            if (!FavoriteAccessSessionManager.isPublicationCurrent(validation)) return null
            safe.add(item)
        }
        return if (FavoriteAccessSessionManager.isPublicationCurrent(selected)) AuthorizedBatch(safe, selected) else null
    }

    fun <T> validateItems(
        token: String?,
        items: Collection<T>,
        pathOf: (T) -> String?,
        requiredPaths: Collection<String> = emptyList()
    ): AuthorizedBatch<T>? {
        val candidates = items.toList()
        val paths = LinkedHashSet<String>().apply {
            addAll(requiredPaths)
            candidates.mapNotNullTo(this, pathOf)
        }
        val validation = FavoriteAccessSessionManager.validatePaths(token, paths) ?: return null
        if (!validation.paths.containsAll(requiredPaths)) return null
        val safe = candidates.filter { item ->
            val path = pathOf(item)
            if (path == null) item is ThumbnailSection
            else FavoriteAccessSessionManager.isInValidatedPaths(validation, path)
        }
        return if (FavoriteAccessSessionManager.isValidationCurrent(validation)) {
            AuthorizedBatch(ArrayList(safe), validation)
        } else null
    }
}

