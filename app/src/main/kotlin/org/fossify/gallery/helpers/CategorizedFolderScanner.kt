package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.models.Directory
import java.io.File

/** Refresh category cards from current files; cached cards are only a presentation fallback. */
internal object CategorizedFolderScanner {
    data class Result(val directories: ArrayList<Directory>, val failures: Map<String, Exception>)
    fun scan(context: Context, cached: List<Directory>, flags: BooleanArray,
        cancelled: () -> Boolean, onFetcher: (MediaFetcher) -> Unit = {},
        onProgress: (Result) -> Unit = {},
        discover: (Collection<String>, (String) -> Boolean, () -> Boolean) -> Set<String> = FolderDiskDiscovery::find,
        onMetadata: (DirectoryScanSnapshot) -> Unit = {}): Result {
        if (cancelled()) throw java.util.concurrent.CancellationException()
        val cfg = context.config
        val policy = FolderCategoryPolicy(flags[0], flags[1], flags[2], flags[3],
            cfg.includedFolders.toSet(), cfg.excludedFolders.toSet())
        val markers = NoMediaSnapshot()
        fun locked(path: String) = FolderUnlockSessionManager.findProtectedAncestor(path, cfg::isFolderProtected) != null
        fun allowed(path: String) = policy.allows(path, markers.contains(path), locked(path))
        val failures = linkedMapOf<String, Exception>()
        val fetcher = MediaFetcher(context, cancelled = cancelled, reportReadFailures = true, showDotFiles = policy.dots).also { it.noMediaSnapshot = markers; it.traceRequest = System.nanoTime() }
        onFetcher(fetcher)
        val byPath = cached.associateBy { it.path }
        val result = linkedMapOf<String, Directory>()
        // Keep unvisited cached cards while publishing already-refreshed siblings.
        cached.filter { allowed(it.path) }.forEach { directory ->
            result[directory.path] = directory.snapshot().apply {
                if (locked(path)) { tmb = ""; mediaCnt = 0; subfoldersMediaCount = 0; size = 0L }
            }
        }
        val visited = hashSetOf<String>()
        fun snapshot() = Result(ArrayList(result.values.filter { allowed(it.path) }.map { it.snapshot() }), failures.toMap())
        var lastPublish = 0L
        fun publish(force: Boolean = false) {
            if (cancelled()) throw java.util.concurrent.CancellationException()
            val now = android.os.SystemClock.elapsedRealtime()
            if (force || now - lastPublish >= 150) { lastPublish = now; onProgress(snapshot()) }
        }
        val favorites = context.getFavoritePaths()
        val covers = cfg.parseAlbumCovers()
        fun scanPath(path: String) {
            if (!path.startsWith('/') || !visited.add(FavoriteFolderRules.normalizedPath(path))) return
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (!allowed(path)) return
            val old = byPath[path]
            val isProtected = locked(path)
            val hasMarker = markers.contains(path)
            val metadataScope = DirectoryScanScope(cfg.filterMedia, policy.dots)
            val metadataVersion = if (!isProtected && metadataScope.matches(context))
                DirectoryMetadataRepository.begin(path) else 0L
            val item = if (isProtected) {
                // Metadata rows never authorize a read of a protected folder or descendants.
                if (old == null && !File(path).isDirectory) return
                (old?.snapshot() ?: Directory().apply { this.path = path; name = File(path).name }).apply {
                    tmb = ""; mediaCnt = 0; subfoldersMediaCount = 0; size = 0L
                }
            } else try {
                val read = fetcher.readFolder(path, false, false, true, true,
                    cfg.directorySorting and SORT_BY_SIZE != 0, favorites, false,
                    hashMapOf(), fetcher.getSelectedFolderDateOverrides(listOf(path)), null)
                if (locked(path) || !read.accessCurrent()) throw SecurityException("Directory authorization changed")
                if (metadataVersion != 0L) {
                    if (read.status == FolderReadStatus.COMPLETE) {
                        onMetadata(DirectoryScanSnapshot(
                            path, metadataVersion, metadataScope, read, read.media.map { it.copy() },
                            isCurrent = { !cancelled() && !locked(path) }
                        ))
                    } else {
                        DirectoryMetadataRepository.defer(path, metadataVersion)
                    }
                }
                if (read.status != FolderReadStatus.COMPLETE) {
                    failures[path] = read.error ?: java.io.IOException("Directory refresh is incomplete")
                }
                if (read.status == FolderReadStatus.FAILED || read.status == FolderReadStatus.ACCESS_REQUIRED) {
                    if (old == null) return
                    old.snapshot()
                } else if (read.media.isEmpty()) {
                    if (read.status == FolderReadStatus.COMPLETE || old == null) { result.remove(path); return }
                    old.snapshot()
                } else {
                    val media = read.media
                    context.createDirectoryFromMedia(path, media, covers, context.getString(R.string.hidden),
                    cfg.includedFolders, cfg.directorySorting and SORT_BY_SIZE != 0,
                    ArrayList(markers.markerFolders(path))).apply {
                    if (read.status == FolderReadStatus.PARTIAL && old != null) {
                        // A card has no cached member list. Retain a conservative lower bound,
                        // not old+new (which would count overlapping members twice).
                        mediaCnt = maxOf(mediaCnt, old.mediaCnt)
                        size = maxOf(size, old.size)
                    }
                }
                }
            } catch (error: Exception) {
                if (metadataVersion != 0L) DirectoryMetadataRepository.defer(path, metadataVersion)
                if (cancelled()) throw java.util.concurrent.CancellationException()
                failures[path] = error
                if (old == null) return
                old.snapshot().apply { if (locked(path)) { tmb = ""; mediaCnt = 0; subfoldersMediaCount = 0; size = 0L } }
            }
            fetcher.readFailures[path]?.let { failures[path] = it }
            item.folderStatuses = listOfNotNull(
                context.getString(R.string.folder_status_hidden).takeIf { FavoriteFolderRules.isDotHidden(path) },
                context.getString(R.string.folder_status_excluded).takeIf { FavoriteFolderRules.isExcluded(path, policy.excludedRoots) },
                context.getString(R.string.folder_status_nomedia).takeIf { hasMarker },
                context.getString(R.string.folder_status_protected).takeIf { isProtected },
                context.getString(R.string.folder_unavailable_suffix).takeIf { path in failures }
            ).joinToString(" · ")
            result[path] = item
        }
        // Refresh known albums before provider lookups and recursive discovery.
        val seeds = linkedSetOf<String>().apply {
            addAll(cached.map { it.path }); addAll(cfg.everShownFolders)
            addAll(policy.includedRoots); addAll(policy.excludedRoots)
        }
        FolderRefreshTrace.measure(fetcher.traceRequest, "known_albums") {
            seeds.forEach { scanPath(it); publish() }
            publish(force = true)
        }
        FolderRefreshTrace.measure(fetcher.traceRequest, "album_discovery") {
            val hints = context.getNoMediaFoldersSync(seeds, fetcher.cancellationSignal)
            markers.seedMarkerFolders(hints)
            seeds.addAll(hints)
            try { seeds.addAll(fetcher.getFoldersToScan()) }
            catch (error: Exception) {
                if (cancelled()) throw java.util.concurrent.CancellationException()
                failures["index"] = error
            }
            listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MOVIES).forEach {
                seeds.add(Environment.getExternalStoragePublicDirectory(it).toString())
            }
            val discovered = discover(seeds, { allowed(it) && !locked(it) }, cancelled)
            (seeds + discovered).forEach { scanPath(it); publish() }
        }
        failures.putAll(fetcher.readFailures)
        if (cancelled()) throw java.util.concurrent.CancellationException()
        return snapshot()
    }
}
