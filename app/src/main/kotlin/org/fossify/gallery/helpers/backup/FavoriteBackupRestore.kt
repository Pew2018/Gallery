package org.fossify.gallery.helpers.backup

import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.FavoriteBackupCodec
import org.fossify.gallery.helpers.FavoriteFolderRules
import org.fossify.gallery.helpers.PhotoContentRevision
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.FavoriteFolder

/** Reusable, UI-free persistence. Availability and protection are evaluated by the caller's policy.
 * A false/failed protection check never authorizes a path. No sessions are created here. */
internal class FavoriteBackupRestore(private val database: GalleryDatabase) {
    fun importMedia(
        plan: MediaFavoritesPlan,
        accessible: (String) -> Boolean,
        unlocked: (String) -> Boolean,
        favorite: (String) -> Favorite
    ): BackupImportResult {
        var unavailable = 0
        var protectedCount = 0
        val candidates = plan.paths.filter { path ->
            when {
                !runCatching { accessible(path) }.getOrDefault(false) -> { unavailable++; false }
                !runCatching { unlocked(path) }.getOrDefault(false) -> { protectedCount++; false }
                else -> true
            }
        }
        var changed = 0
        var added = 0
        var existing = 0
        var cacheRepaired = false
        database.runInTransaction {
            val favorites = database.FavoritesDao()
            val media = database.MediumDao()
            val current = favorites.getValidFavoritePaths().toHashSet()
            val unmarked = candidates.chunked(250).flatMap(media::getCachedMediaFromFiles)
                .filterNot { it.isFavorite }.mapTo(hashSetOf()) { it.path }
            candidates.forEach { path ->
                if (!runCatching { unlocked(path) }.getOrDefault(false)) changed++
                else {
                    if (path in current) existing++
                    else if (favorites.insertAllIgnore(listOf(favorite(path))).single() >= 0L) {
                        added++; current.add(path)
                    } else existing++
                    media.updateFavorite(path, true)
                    if (path in unmarked) cacheRepaired = true
                }
            }
        }
        if (added > 0 || cacheRepaired) PhotoContentRevision.changed()
        return BackupImportResult(plan.total, added, existing, unavailable, plan.invalid,
            protectedCount, changed, plan.duplicates)
    }

    fun importFolders(records: List<FavoriteBackupCodec.FolderRecord>, isFile: (String) -> Boolean): BackupImportResult {
        // A missing/offline directory and a SAF URI remain valid relationships.
        val valid = records.filterNot { runCatching { isFile(it.path) }.getOrDefault(false) }
        var added = 0
        database.runInTransaction {
            val dao = database.FavoriteFoldersDao()
            val existing = dao.getPaths().mapTo(hashSetOf(), FavoriteFolderRules::normalizedPath)
            val incoming = valid.filter { existing.add(FavoriteFolderRules.normalizedPath(it.path)) }
            added = dao.insertAllIgnore(incoming.map { FavoriteFolder(it.path, it.addedAt) }).count { it >= 0L }
        }
        return BackupImportResult(records.size, added, valid.size - added, invalid = records.size - valid.size)
    }
}

data class MediaFavoritesPlan(val paths: List<String>, val total: Int, val invalid: Int, val duplicates: Int)
