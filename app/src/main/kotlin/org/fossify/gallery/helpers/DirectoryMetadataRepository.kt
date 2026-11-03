package org.fossify.gallery.helpers

import android.content.Context
import android.util.Log
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.R
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** The filter and dot-file policy must match the public album catalog, not a picker or a category preview. */
internal data class DirectoryScanScope(
    val filterMedia: Int,
    val showDotFiles: Boolean,
    val pickImagesOnly: Boolean = false,
    val pickVideosOnly: Boolean = false
) {
    fun matches(context: Context): Boolean {
        val current = context.config
        return !pickImagesOnly && !pickVideosOnly && filterMedia != 0 &&
            current.filterMedia == filterMedia && current.shouldShowHidden == showDotFiles
    }
}

/** Raw media, never grouped ThumbnailItem rows or cache-merged presentation media. */
internal data class DirectoryScanSnapshot(
    val path: String,
    val requestVersion: Long,
    val scope: DirectoryScanScope,
    val readResult: FolderMediaReadResult,
    val mediaSnapshot: List<Medium>,
    val folderSessionId: String? = null,
    val isCurrent: () -> Boolean = { true },
    // MainActivity already updates its own adapter; do not recursively restart its scan.
    val notifyObservers: Boolean = true
)

/**
 * Per-path revision gate spans DB writes, so a completed earlier scan cannot overwrite a
 * later accepted scan. Changes are published only after successful Room persistence.
 * Pending paths survive Activity navigation (not process death) and are retried publicly.
 */
internal object DirectoryMetadataRepository {
    private val lock = Any()
    private var revision = 0L
    private val activeRequests = hashMapOf<String, Long>()
    private val committedRequests = hashMapOf<String, Long>()
    private val pending = linkedSetOf<String>()
    private val publishedRevisions = hashMapOf<String, Long>()
    private val observers = linkedSetOf<(String) -> Unit>()
    private val retryRunning = AtomicBoolean(false)

    fun begin(path: String): Long = synchronized(lock) {
        if (!path.startsWith('/') || path == "/") return@synchronized 0L
        val version = ++revision
        activeRequests[path] = version
        version
    }

    fun defer(path: String, requestVersion: Long) {
        if (requestVersion == 0L) return
        synchronized(lock) {
            if (activeRequests[path] == requestVersion && committedRequests[path] != requestVersion) pending.add(path)
        }
    }

    fun observe(observer: (String) -> Unit) { synchronized(lock) { observers.add(observer) } }
    fun removeObserver(observer: (String) -> Unit) { synchronized(lock) { observers.remove(observer) } }
    fun currentRevision(): Long = synchronized(lock) { revision }
    fun changedSince(version: Long): Set<String> = synchronized(lock) {
        publishedRevisions.filterValues { it > version }.keys.toSet()
    }
    fun pendingPaths(): Set<String> = synchronized(lock) { pending.toSet() }

    private fun authorized(context: Context, snapshot: DirectoryScanSnapshot): Boolean {
        if (!snapshot.isCurrent() || !snapshot.readResult.accessCurrent()) return false
        val cfg = context.config
        val protected = FolderUnlockSessionManager.findProtectedAncestor(snapshot.path, cfg::isFolderProtected) != null
        return !protected ||
            FolderUnlockSessionManager.isFolderAuthorized(snapshot.folderSessionId, snapshot.path) ||
            FolderUnlockSessionManager.isMediaAuthorized(
                snapshot.folderSessionId, snapshot.path, cfg::isFolderProtected
            )
    }

    fun commit(context: Context, snapshot: DirectoryScanSnapshot): Boolean {
        val path = snapshot.path
        val version = snapshot.requestVersion
        if (version == 0L || !path.startsWith('/') || !snapshot.scope.matches(context) ||
            snapshot.readResult.status != FolderReadStatus.COMPLETE) {
            defer(path, version)
            return false
        }
        if (!authorized(context, snapshot)) { defer(path, version); return false }

        val cfg = context.config
        val media = ArrayList(snapshot.mediaSnapshot.distinctBy { it.path }.map { it.copy() })
        val calculated = try {
            context.createDirectoryFromMedia(path, media, cfg.parseAlbumCovers(),
                context.getString(R.string.hidden), cfg.includedFolders,
                cfg.directorySorting and SORT_BY_SIZE != 0,
                ArrayList(NoMediaSnapshot().markerFolders(path)))
        } catch (error: Exception) {
            Log.w("GalleryDirs", "Cannot compute directory metadata", error)
            defer(path, version)
            return false
        }

        var changed = false
        var accepted = false
        var recipients: List<(String) -> Unit> = emptyList()
        synchronized(lock) {
            if (activeRequests[path] != version) return false
            if (!snapshot.scope.matches(context) || !authorized(context, snapshot)) {
                pending.add(path)
                return false
            }
            try {
                GalleryDatabase.getInstance(context.applicationContext).runInTransaction {
                    if (activeRequests[path] != version ||
                        !snapshot.scope.matches(context) || !authorized(context, snapshot)) {
                        throw SecurityException("Directory scan was invalidated before metadata write")
                    }
                    val dao = context.directoryDB
                    val old = dao.getByPath(path)
                    if (old == null) {
                        // A failed or inaccessible path must never create an empty phantom album.
                        if (!File(path).isDirectory) throw java.io.IOException("Directory is no longer visible")
                        dao.insert(calculated)
                        changed = true
                    } else if (old.tmb != calculated.tmb || old.mediaCnt != calculated.mediaCnt ||
                        old.modified != calculated.modified || old.taken != calculated.taken ||
                        old.size != calculated.size || old.types != calculated.types ||
                        old.sortValue != calculated.sortValue) {
                        val updated = dao.updateDirectory(
                            path, calculated.tmb, calculated.mediaCnt, calculated.modified,
                            calculated.taken, calculated.size, calculated.types, calculated.sortValue
                        )
                        if (updated != 1) throw java.io.IOException("Directory metadata update lost its row")
                        changed = true
                    }
                }
                accepted = true
                committedRequests[path] = version
                pending.remove(path)
                if (changed) {
                    publishedRevisions[path] = ++revision
                    recipients = observers.toList()
                }
            } catch (error: Exception) {
                pending.add(path)
                Log.w("GalleryDirs", "Directory metadata commit failed (id=${FolderRefreshTrace.pathId(path)})", error)
            }
        }
        if (changed && accepted) {
            FolderRefreshTrace.event(version, "directory_metadata_committed", listOf(path), "count=${calculated.mediaCnt}")
            if (snapshot.notifyObservers) recipients.forEach { observer ->
                try { observer(path) } catch (error: Exception) {
                    Log.w("GalleryDirs", "Directory metadata observer failed", error)
                }
            }
        }
        return accepted
    }

    /**
     * Complete an interrupted folder scan after returning to the album list.
     * Only unprotected, readable physical folders are eligible; failures remain pending.
     */
    fun refreshPendingPaths(context: Context) {
        if (!retryRunning.compareAndSet(false, true)) return
        try {
            GalleryDirectoryMetadataExecutor.execute {
                try {
                    val targets = pendingPaths().take(24)
                    for (path in targets) {
                        val cfg = context.config
                        if (!File(path).isDirectory || !File(path).canRead() ||
                            FolderUnlockSessionManager.findProtectedAncestor(path, cfg::isFolderProtected) != null)
                            continue
                        val scope = DirectoryScanScope(cfg.filterMedia, cfg.shouldShowHidden)
                        if (!scope.matches(context)) continue
                        val version = begin(path)
                        try {
                            val fetcher = MediaFetcher(context.applicationContext, reportReadFailures = true)
                            val read = fetcher.readFolder(path, false, false, true, true,
                                cfg.directorySorting and SORT_BY_SIZE != 0, context.getFavoritePaths(),
                                false, hashMapOf(), fetcher.getSelectedFolderDateOverrides(listOf(path)), null)
                            commit(context, DirectoryScanSnapshot(path, version, scope, read,
                                read.media.map { it.copy() }))
                        } catch (error: Exception) {
                            defer(path, version)
                            Log.w("GalleryDirs", "Deferred directory scan failed (id=${FolderRefreshTrace.pathId(path)})", error)
                        }
                    }
                } finally {
                    retryRunning.set(false)
                }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            retryRunning.set(false)
        }
    }
}
