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
        cancelled: () -> Boolean, onFetcher: (MediaFetcher) -> Unit = {}): Result {
        if (cancelled()) throw java.util.concurrent.CancellationException()
        val cfg = context.config
        val policy = FolderCategoryPolicy(flags[0], flags[1], flags[2], flags[3],
            cfg.includedFolders.toSet(), cfg.excludedFolders.toSet())
        val markers = NoMediaSnapshot()
        fun locked(path: String) = FolderUnlockSessionManager.findProtectedAncestor(path, cfg::isFolderProtected) != null
        fun allowed(path: String) = policy.allows(path, markers.contains(path), locked(path))
        val failures = linkedMapOf<String, Exception>()
        val fetcher = MediaFetcher(context, cancelled = cancelled, reportReadFailures = true, showDotFiles = policy.dots)
        onFetcher(fetcher)
        val seeds = linkedSetOf<String>().apply {
            addAll(cached.map { it.path }); addAll(cfg.everShownFolders)
            addAll(policy.includedRoots); addAll(policy.excludedRoots)
            val markerHints = context.getNoMediaFoldersSync(this, fetcher.cancellationSignal)
            markers.seedMarkerFolders(markerHints)
            addAll(markerHints)
            try { addAll(fetcher.getFoldersToScan()) }
            catch (error: Exception) { if (cancelled()) throw java.util.concurrent.CancellationException(); failures["index"] = error }
            listOf(Environment.DIRECTORY_DCIM, Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MOVIES).forEach {
                add(Environment.getExternalStoragePublicDirectory(it).toString())
            }
        }.filter { it.startsWith('/') }.toCollection(linkedSetOf())
        val discovered = FolderDiskDiscovery.find(seeds, allowed = { allowed(it) && !locked(it) }, cancelled = cancelled)
        val byPath = cached.associateBy { it.path }
        val candidates = (cached.map { it.path } + discovered + seeds)
            .filter { it.startsWith('/') }.distinctBy(FavoriteFolderRules::normalizedPath)
        val result = arrayListOf<Directory>()
        val favorites = context.getFavoritePaths()
        val covers = cfg.parseAlbumCovers()
        for (path in candidates) {
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (!allowed(path)) continue
            val old = byPath[path]
            val isProtected = locked(path)
            val hasMarker = markers.contains(path)
            val item = if (isProtected) {
                // Metadata rows never authorize a read of a protected folder or descendants.
                if (old == null && !File(path).isDirectory) continue
                (old?.copy() ?: Directory().apply { this.path = path; name = File(path).name }).apply {
                    tmb = ""; mediaCnt = 0; subfoldersMediaCount = 0; size = 0L
                }
            } else try {
                val media = fetcher.getFilesFrom(path, false, false, true, true,
                    cfg.directorySorting and SORT_BY_SIZE != 0, favorites, false,
                    hashMapOf(), fetcher.getSelectedFolderDateOverrides(listOf(path)), null)
                if (media.isEmpty()) continue // A confirmed empty album has no media card.
                context.createDirectoryFromMedia(path, media, covers, context.getString(R.string.hidden),
                    cfg.includedFolders, cfg.directorySorting and SORT_BY_SIZE != 0,
                    ArrayList(markers.markerFolders(path)))
            } catch (error: Exception) {
                if (cancelled()) throw java.util.concurrent.CancellationException()
                failures[path] = error
                if (old == null) continue
                old.copy()
            }
            fetcher.readFailures[path]?.let { failures[path] = it }
            item.folderStatuses = listOfNotNull(
                context.getString(R.string.folder_status_hidden).takeIf { FavoriteFolderRules.isDotHidden(path) },
                context.getString(R.string.folder_status_excluded).takeIf { FavoriteFolderRules.isExcluded(path, policy.excludedRoots) },
                context.getString(R.string.folder_status_nomedia).takeIf { hasMarker },
                context.getString(R.string.folder_status_protected).takeIf { isProtected },
                context.getString(R.string.folder_unavailable_suffix).takeIf { path in failures }
            ).joinToString(" · ")
            result.add(item)
        }
        failures.putAll(fetcher.readFailures)
        if (cancelled()) throw java.util.concurrent.CancellationException()
        return Result(result, failures)
    }
}
