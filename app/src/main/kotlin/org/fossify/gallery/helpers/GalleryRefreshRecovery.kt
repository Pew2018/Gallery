package org.fossify.gallery.helpers

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.models.Directory

/** Keeps changed album paths alive across cancellation, timeout and navigation. */
internal class DirectoryMetadataRebindGate {
    data class Request(val id: Long, val paths: Set<String>)
    private val pending = linkedSetOf<String>()
    private var active: Request? = null
    private var generation = 0L

    @Synchronized fun enqueue(paths: Collection<String>) {
        pending.addAll(paths.filter { it.isNotBlank() })
    }
    @Synchronized fun hasPending() = pending.isNotEmpty()
    @Synchronized fun isActive() = active != null

    @Synchronized fun begin(): Request? {
        if (active != null || pending.isEmpty()) return null
        val next = Request(++generation, pending.toSet())
        pending.clear()
        active = next
        return next
    }
    @Synchronized fun isCurrent(id: Long) = active?.id == id
    @Synchronized fun isLatest(id: Long) = generation == id

    @Synchronized fun finish(id: Long, applied: Boolean): Boolean {
        val request = active?.takeIf { it.id == id } ?: return false
        active = null
        if (!applied) pending.addAll(request.paths)
        return true
    }
    @Synchronized fun cancel() {
        active?.let { pending.addAll(it.paths) }
        active = null
        generation++
    }
}

/** A failed refresh keeps external changes pending, but never retries itself. */
internal class MediaChangeRetryGate {
    // Earlier failures remain unresolved until a later scan *succeeds*.
    private var deferredBeforeScan = false
    private var changedDuringScan = false

    fun mediaChangedDuringScan() { changedDuringScan = true }

    fun scanStarted() {
        deferredBeforeScan = deferredBeforeScan || changedDuringScan
        changedDuringScan = false
    }

    fun scanFinished(success: Boolean): Boolean {
        if (!success) {
            deferredBeforeScan = deferredBeforeScan || changedDuringScan
            changedDuringScan = false
            return false
        }
        // Only new changes observed during this successful scan require catch-up.
        val needsCatchUp = changedDuringScan
        deferredBeforeScan = false
        changedDuringScan = false
        return needsCatchUp
    }

    fun hasPending() = deferredBeforeScan || changedDuringScan
}

/** Do not reintroduce hidden/removed virtual album rows from an old adapter. */
internal object RootVirtualAlbumPolicy {
    fun reconcile(
        cached: List<Directory>,
        favoriteCount: Long?,
        deletedCount: Long?,
        showRecycleBin: Boolean,
        favoritesLocked: Boolean,
        showHidden: Boolean,
        favoriteName: String,
        recycleBinName: String,
        internalLocation: Int
    ): ArrayList<Directory> {
        val oldFavorites = cached.firstOrNull { it.path == FAVORITES }
        val oldRecycle = cached.firstOrNull { it.path == RECYCLE_BIN }
        val result = cached.filterNot { it.path == FAVORITES || it.path == RECYCLE_BIN }
            .mapTo(ArrayList()) { it.snapshot() }

        if (favoriteCount?.let { it > 0L } ?: (oldFavorites != null)) {
            val safe = oldFavorites?.takeUnless {
                !showHidden && it.tmb.substringAfterLast('/').startsWith('.')
            }
            val favorite = safe?.snapshot() ?: Directory().apply {
                path = FAVORITES
                name = favoriteName
                location = internalLocation
            }
            if (favoritesLocked) {
                favorite.tmb = ""
                favorite.mediaCnt = 0
                favorite.subfoldersMediaCount = 0
            }
            result.add(favorite)
        }
        if (showRecycleBin && (deletedCount?.let { it > 0L } ?: (oldRecycle != null))) {
            result.add(oldRecycle?.snapshot() ?: Directory().apply {
                path = RECYCLE_BIN
                name = recycleBinName
                location = internalLocation
            })
        }
        return result
    }
}
