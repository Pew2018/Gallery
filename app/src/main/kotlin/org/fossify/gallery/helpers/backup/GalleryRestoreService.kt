package org.fossify.gallery.helpers.backup

import android.content.Context
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.FavoriteFolder

internal enum class GalleryRecoveryResult { NONE, FINISHED, REVERTED }

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
    private val consumedPlans = hashSetOf<String>()

    fun snapshot(): GalleryBackupSnapshot = BackupOperationRunner.serialized {
        if (journal.exists()) throw PendingGalleryRestore()
        collector.capture().snapshot
    }
    fun preview(snapshot: GalleryBackupSnapshot): GalleryRestorePlan = BackupOperationRunner.serialized {
        if (journal.exists()) throw PendingGalleryRestore()
        GalleryRestorePlanner(context, access).plan(snapshot, collector.capture())
    }

    /** Authentication UI runs before this method, outside the serialized worker lock. */
    fun authorize(plan: GalleryRestorePlan, mode: GalleryRestoreMode, proof: Config.FavoriteCredentialProof?,
                  operation: GalleryRestoreOperation): GalleryRestorePlan = BackupOperationRunner.serialized {
        if (journal.exists()) throw PendingGalleryRestore()
        if (!operation.isActive()) throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        val local = collector.capture()
        validateBaseline(plan, local, afterAuthentication = true)
        val grant = GalleryRestoreAuthorization.issue(context, plan, mode, operation.id, proof, local.token)
        if (!operation.attach(grant)) throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        try {
            GalleryRestorePlanner(context, access).plan(plan.incoming, local, grant, plan.id).also {
                validateBaseline(it, collector.capture())
                validateAuthorization(it, mode, operation)
            }
        } catch (error: Exception) { grant.invalidate(); throw error }
    }

    fun restore(plan: GalleryRestorePlan, mode: GalleryRestoreMode,
                operation: GalleryRestoreOperation? = null): GalleryRestoreResult = BackupOperationRunner.serialized {
        try {
            if (journal.exists()) throw PendingGalleryRestore()
            val local = collector.capture()
            validateBaseline(plan, local)
            validateAuthorization(plan, mode, operation)
            if (!consumedPlans.add(plan.id)) throw StaleGalleryRestorePlan()
            val target = requireNotNull(plan.targets[mode])
            val statuses = plan.statusesFor(mode)
            val beforeDb = BackupDigest.relations(local.snapshot.mediaFavorites, local.snapshot.favoriteFolders)
            val afterDb = BackupDigest.relations(target.media, target.folders)
            val changes = target.preferenceChanges
            val before = changes.keys.associateWith { local.preferences[it] }
            val checkpoint = GalleryRestoreCheckpoint(beforeDb, afterDb, before, changes)
            val dbChanged = beforeDb != afterDb
            val eligible = (local.snapshot.mediaFavorites + target.media).distinct().filter {
                statuses[BackupPathRequest(it, false)] == BackupPathStatus.AVAILABLE
            }
            val desiredMedia = target.media.toSet()
            val cachedRepair = eligible.chunked(250).any { chunk ->
                database.MediumDao().getCachedMediaFromFiles(chunk).any { it.isFavorite != (it.path in desiredMedia) }
            }
            if (changes.isEmpty() && !dbChanged && !cachedRepair)
                return@serialized observeResult(plan, target, local, 0)
            try {
                fault("before_checkpoint")
                journal.write(checkpoint.copy(mediaChanged = local.snapshot.mediaFavorites.toSet() != desiredMedia || cachedRepair)) // fsync before either store is touched; contains no authorization
            } catch (error: Exception) { throw GalleryRestoreCheckpointFailure(error) }
            var result: GalleryRestoreResult? = null
            var stage = RestoreWriteStage.ROOM
            try {
                database.runInTransaction {
                    if (BackupDigest.relations(database.FavoritesDao().getValidFavoritePaths(),
                            database.FavoriteFoldersDao().getAll().map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) }) != beforeDb ||
                        context.getSharedPrefs().all != local.preferences) throw StaleGalleryRestorePlan()
                    validateAccess(plan)
                    validateAuthorization(plan, mode, operation)
                    val repaired = applyRelations(local.snapshot, target, eligible.toSet())
                    fault("after_room_changes")
                    if (context.getSharedPrefs().all != local.preferences) throw StaleGalleryRestorePlan()
                    validateAuthorization(plan, mode, operation)
                    stage = RestoreWriteStage.PREFERENCES
                    if (changes.isNotEmpty()) writePreferences(changes)
                    fault("after_preferences")
                    // Last access and authorization checks are inside the transaction, immediately before commit.
                    validateAccess(plan)
                    validateAuthorization(plan, mode, operation)
                    val expectedPreferences = local.preferences.toMutableMap().apply {
                        changes.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
                    }
                    if (context.getSharedPrefs().all != expectedPreferences) throw StaleGalleryRestorePlan()
                    result = observeResult(plan, target, local, repaired)
                }
            } catch (error: Exception) {
                val currentDb = runCatching { relationToken() }.getOrNull()
                if (currentDb != beforeDb) throw GalleryRestoreFailure(false, error, stage)
                val compensated = runCatching {
                    if (!matches(before)) writePreferences(before)
                    check(matches(before))
                    journal.delete()
                }.isSuccess
                throw GalleryRestoreFailure(compensated, error, stage)
            }
            try { fault("after_room_commit"); journal.delete() }
            catch (error: Exception) { throw GalleryRestoreFailure(false, error, RestoreWriteStage.CLEANUP) }
            val committed = requireNotNull(result)
            if (committed.media.appliedAdded > 0 || committed.media.appliedRemoved > 0 || committed.media.cacheRepaired > 0)
                PhotoContentRevision.changed()
            committed
        } finally { plan.authorization?.invalidate() }
    }

    private fun validateBaseline(plan: GalleryRestorePlan, local: GalleryLocalBackupState, afterAuthentication: Boolean = false) {
        if (plan.securityVersion != GalleryRestoreAuthorization.securityVersion(context))
            throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        if (local.token != plan.localToken && !(afterAuthentication && local.authorizationToken == plan.authorizationBaseline)) throw StaleGalleryRestorePlan()
        if (plan.sessionGeneration != FavoriteAccessSessionManager.currentGeneration())
            throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        validateAccess(plan)
    }

    private fun validateAccess(plan: GalleryRestorePlan) {
        if (access.inspectRestoreBatch(context, plan.accessDetails.keys) != plan.accessDetails)
            throw StaleGalleryRestorePlan(RestoreStaleReason.ACCESS)
    }

    private fun validateAuthorization(plan: GalleryRestorePlan, mode: GalleryRestoreMode, operation: GalleryRestoreOperation?) {
        if (operation?.isActive() == false) throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        if (plan.sessionGeneration != FavoriteAccessSessionManager.currentGeneration())
            throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        val grant = plan.authorization
        if (grant != null) {
            if (operation == null || grant.operationId != operation.id || !grant.current(context, plan, mode))
                throw StaleGalleryRestorePlan(RestoreStaleReason.AUTHORIZATION)
        } else if (plan.requiresFavoriteAuthorization(mode)) {
            throw RestoreAuthorizationRequired(RestoreAuthorizationReason.REQUIRED)
        }
    }

    private fun observeResult(plan: GalleryRestorePlan, target: GalleryRestoreTarget,
                              before: GalleryLocalBackupState, repaired: Int): GalleryRestoreResult {
        val media = database.FavoritesDao().getValidFavoritePaths()
        val folders = database.FavoriteFoldersDao().getAll().map { FavoriteBackupCodec.FolderRecord(it.path, it.addedAt) }
        val preferences = context.getSharedPrefs().all.toMap()
        val rawProfiles = preferences[PhotoProfileRepository.KEY] as? String
        val profiles = if (rawProfiles.isNullOrBlank()) PhotoProfileState() else PhotoProfileBackupCodec.decodeStrict(rawProfiles)
        check(media.toSet() == target.media.toSet() && folders.toSet() == target.folders.toSet() && profiles == target.profiles) {
            "Committed observations do not match the confirmed restore target"
        }
        return GalleryRestoreResults.build(plan, target, before, media, folders, profiles, preferences, repaired)
    }

    fun recoverPending(): GalleryRecoveryResult = BackupOperationRunner.serialized {
        try {
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
            if (checkpoint.mediaChanged && (finish || checkpoint.beforeDb == checkpoint.afterDb)) PhotoContentRevision.changed()
            if (finish) GalleryRecoveryResult.FINISHED else GalleryRecoveryResult.REVERTED
        } catch (error: Exception) { throw GalleryRecoveryBlocked(error) }
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
    private fun applyRelations(before: GalleryBackupSnapshot, target: GalleryRestoreTarget, eligible: Set<String>): Int {
        val favorites = database.FavoritesDao()
        val media = database.MediumDao()
        val oldPaths = before.mediaFavorites.toSet()
        val newPaths = target.media.toSet()
        val additions = newPaths - oldPaths
        val removals = oldPaths - newPaths
        if (!(additions + removals).all { it in eligible }) throw StaleGalleryRestorePlan(RestoreStaleReason.ACCESS)
        var repaired = 0
        removals.forEach(favorites::deleteFavoritePath)
        additions.toList().chunked(250).forEach { chunk -> favorites.insertAllIgnore(chunk.map(context::getFavoriteFromPath)) }
        eligible.toList().chunked(250).forEach { chunk ->
            media.getCachedMediaFromFiles(chunk).forEach {
                val favorite = it.path in newPaths
                if (it.isFavorite != favorite) { media.updateFavorite(it.path, favorite); repaired++ }
            }
        }
        val folders = database.FavoriteFoldersDao()
        val old = before.favoriteFolders.associateBy { it.path }
        val new = target.folders.associateBy { it.path }
        old.keys.filterNot { it in new }.forEach(folders::delete)
        new.values.filter { old[it.path] != it }.forEach { folders.insert(FavoriteFolder(it.path, it.addedAt)) }
        return repaired
    }
}
