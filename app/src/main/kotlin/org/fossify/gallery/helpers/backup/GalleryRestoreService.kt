package org.fossify.gallery.helpers.backup

import android.content.Context
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.FavoriteFolder

internal enum class GalleryRecoveryResult { NONE, FINISHED, REVERTED }
internal data class GalleryRestoreResult(val target: GalleryRestoreTarget, val retainedMedia: Int, val unresolvedPaths: Int)

/** A single Room transaction coordinates both relation tables. Preferences are compensated on
 * transaction failure; a durable checkpoint resolves a process-death gap by comparing DB state.
 * This is NOT a cross-storage atomic transaction. */
internal class GalleryRestoreService(
    private val context: Context,
    private val database: GalleryDatabase = GalleryDatabase.getInstance(context.applicationContext),
    private val access: BackupPathAccess = DeviceBackupPathAccess(context.applicationContext),
    private val writePreferences: (Map<String, Any?>) -> Unit = { BackupPreferences.commit(context.getSharedPrefs(), it) },
    private val fault: (String) -> Unit = {}
) {
    private val collector = GalleryBackupCollector(context, database)
    private val journal = GalleryRestoreJournal(context)

    fun snapshot(): GalleryBackupSnapshot = BackupOperationRunner.serialized {
        check(!journal.exists()) { "A pending restore must be recovered before export" }
        collector.capture().snapshot
    }
    fun preview(snapshot: GalleryBackupSnapshot): GalleryRestorePlan = BackupOperationRunner.serialized {
        if (journal.exists()) throw PendingGalleryRestore()
        GalleryRestorePlanner(context, access).plan(snapshot, collector.capture())
    }

    fun restore(plan: GalleryRestorePlan, mode: GalleryRestoreMode): GalleryRestoreResult = BackupOperationRunner.serialized {
        if (journal.exists()) throw PendingGalleryRestore()
        val local = collector.capture()
        if (local.token != plan.localToken || plan.statuses.any { (request, status) -> access.inspect(request.path, request.folder) != status })
            throw StaleGalleryRestorePlan()
        val target = requireNotNull(plan.targets[mode])
        val beforeDb = BackupDigest.relations(local.snapshot.mediaFavorites, local.snapshot.favoriteFolders)
        val afterDb = BackupDigest.relations(target.media, target.folders)
        val changes = target.preferenceChanges
        val before = changes.keys.associateWith { local.preferences[it] }
        val checkpoint = GalleryRestoreCheckpoint(beforeDb, afterDb, before, changes)
        val dbChanged = beforeDb != afterDb
        val cachedRepair = target.media.chunked(250).any { chunk -> database.MediumDao().getCachedMediaFromFiles(chunk).any { !it.isFavorite } }
        if (changes.isEmpty() && !dbChanged && !cachedRepair) return@serialized result(plan, target)
        fault("before_checkpoint")
        journal.write(checkpoint) // must succeed, including fsync, before either store is touched
        try {
            database.runInTransaction {
                // Recheck after waiting for SQLite's writer lock.
                if (BackupDigest.relations(database.FavoritesDao().getValidFavoritePaths(),
                        database.FavoriteFoldersDao().getAll().map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) }) != beforeDb ||
                    context.getSharedPrefs().all != local.preferences) throw StaleGalleryRestorePlan()
                applyRelations(local.snapshot, target)
                fault("after_room_changes")
                if (context.getSharedPrefs().all != local.preferences) throw StaleGalleryRestorePlan()
                if (changes.isNotEmpty()) writePreferences(changes)
                fault("after_preferences")
            }
        } catch (error: Exception) {
            val currentDb = relationToken()
            if (currentDb != beforeDb) {
                // Commit outcome is uncertain: preserve checkpoint for safe reconciliation, never report success.
                throw GalleryRestoreFailure(false, error)
            }
            val compensated = runCatching {
                if (!matches(before)) writePreferences(before)
                check(matches(before))
                journal.delete()
            }.isSuccess
            throw GalleryRestoreFailure(compensated, error)
        }
        // If cleanup fails the journal remains. Recovery can finish the already-confirmed target.
        try { fault("after_room_commit"); journal.delete() } catch (error: Exception) { throw GalleryRestoreFailure(false, error) }
        if (local.snapshot.mediaFavorites.toSet() != target.media.toSet() || cachedRepair) PhotoContentRevision.changed()
        result(plan, target)
    }

    fun recoverPending(): GalleryRecoveryResult = BackupOperationRunner.serialized {
        if (!journal.exists()) return@serialized GalleryRecoveryResult.NONE
        val checkpoint = journal.read()
        val current = context.getSharedPrefs().all
        require(checkpoint.beforePreferences.keys.all { key ->
            current[key] == checkpoint.beforePreferences[key] || current[key] == checkpoint.afterPreferences[key]
        }) { "Preferences changed since interrupted restore; manual review required" }
        val db = relationToken()
        val finish = when (db) {
            checkpoint.beforeDb -> false // ambiguous preference-only interruption safely reverts
            checkpoint.afterDb -> true
            else -> error("Relations changed since interrupted restore; manual review required")
        }
        val values = if (finish) checkpoint.afterPreferences else checkpoint.beforePreferences
        if (!matches(values)) writePreferences(values)
        check(matches(values)) { "Checkpoint recovery did not persist" }
        journal.delete()
        PhotoContentRevision.changed()
        if (finish) GalleryRecoveryResult.FINISHED else GalleryRecoveryResult.REVERTED
    }

    private fun relationToken(): String {
        var token = ""
        database.runInTransaction {
            token = BackupDigest.relations(database.FavoritesDao().getValidFavoritePaths(), database.FavoriteFoldersDao().getAll()
                .map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) })
        }
        return token
    }
    private fun matches(values: Map<String, Any?>): Boolean {
        val current = context.getSharedPrefs().all
        return values.all { (key, value) -> current[key] == value && (value != null || key !in current) }
    }
    private fun result(plan: GalleryRestorePlan, target: GalleryRestoreTarget) = GalleryRestoreResult(target,
        target.media.count { path -> path !in plan.incoming.mediaFavorites }, plan.unresolvedCount)

    private fun applyRelations(before: GalleryBackupSnapshot, target: GalleryRestoreTarget) {
        val favorites = database.FavoritesDao()
        val media = database.MediumDao()
        val oldPaths = before.mediaFavorites.toSet()
        val newPaths = target.media.toSet()
        val additions = newPaths - oldPaths
        val removals = oldPaths - newPaths
        (additions + removals).forEach { path ->
            if (access.inspect(path, false) != BackupPathStatus.AVAILABLE) throw StaleGalleryRestorePlan()
        }
        removals.forEach(favorites::deleteFavoritePath)
        additions.toList().chunked(250).forEach { chunk -> favorites.insertAllIgnore(chunk.map(context::getFavoriteFromPath)) }
        (oldPaths + newPaths).toList().chunked(250).forEach { chunk ->
            media.getCachedMediaFromFiles(chunk).forEach {
                val favorite = it.path in newPaths
                if (it.isFavorite != favorite) media.updateFavorite(it.path, favorite)
            }
        }
        val folders = database.FavoriteFoldersDao()
        val old = before.favoriteFolders.associateBy { it.path }
        val new = target.folders.associateBy { it.path }
        old.keys.filterNot { it in new }.forEach(folders::delete)
        new.values.filter { old[it.path] != it }.forEach { folders.insert(FavoriteFolder(it.path, it.addedAt)) }
    }
}
