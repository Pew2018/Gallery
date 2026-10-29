package org.fossify.gallery.helpers

import org.fossify.gallery.models.ThumbnailSection

/** Same post-load boundary for cache, scans, pager preloading and slideshow. */
internal object FavoriteAccessPolicy {
    fun unprotectedSnapshot(snapshot: FavoriteAccessSessionManager.Snapshot): FavoriteAccessSessionManager.Snapshot =
        FavoriteAccessSessionManager.Snapshot(snapshot.entries.filterValues { it.locks.isEmpty() })

    fun hasProtectedFavorites(snapshot: FavoriteAccessSessionManager.Snapshot): Boolean =
        snapshot.entries.values.any { it.locks.isNotEmpty() }

    fun <T> authorizedItems(token: String?, items: Collection<T>, pathOf: (T) -> String?): ArrayList<T> {
        if (FavoriteAccessSessionManager.getAllowedPaths(token) == null) return arrayListOf()
        val safe = items.toList().filter { item ->
            val path = pathOf(item)
            if (path == null) item is ThumbnailSection
            else FavoriteAccessSessionManager.isMediaAuthorized(token, path)
        }
        // A changed path can revoke the session during filtering. Never publish
        // a partial result collected before that revocation.
        return if (FavoriteAccessSessionManager.getAllowedPaths(token) != null) ArrayList(safe) else arrayListOf()
    }
}
