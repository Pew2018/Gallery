package org.fossify.gallery.helpers.backup

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.*

internal enum class RestoreSkipReason { FAVORITE_AUTHORIZATION, SOURCE_PROTECTION, OTHER_PROTECTION, MISSING, OFFLINE, SAF_PERMISSION, MEDIA_PERMISSION, UNCONFIRMED }
internal enum class GalleryRestoreCompletion { COMPLETE, PARTIAL, NO_CHANGES }
internal data class AppliedMediaRestore(
    val backupTotal: Int, val existing: Int, val appliedAdded: Int, val appliedRemoved: Int,
    val duplicates: Int, val skipped: Map<RestoreSkipReason, Int>, val retained: Int,
    val retainedUnsafe: Int, val cacheRepaired: Int, val finalCount: Int
) { val skippedCount get() = skipped.values.sum() }
internal data class AppliedFolderRestore(val backupTotal: Int, val appliedAdded: Int, val existing: Int,
    val appliedRemoved: Int, val dateConflicts: Int, val offlineRetained: Int)
internal data class AppliedProfileRestore(val appliedAdded: Int, val existing: Int, val conflicts: Int,
    val appliedRemoved: Int, val inaccessibleSourcesRetained: Int)
internal data class AppliedSettingsRestore(val changed: Int, val securityRetained: Int, val unchanged: Int)
internal data class GalleryRestoreResult(
    val target: GalleryRestoreTarget,
    val retainedMedia: Int,
    val unresolvedPaths: Int,
    val media: AppliedMediaRestore,
    val folders: AppliedFolderRestore,
    val profiles: AppliedProfileRestore,
    val settings: AppliedSettingsRestore,
    val completion: GalleryRestoreCompletion
)

/** Called with observations inside the committing Room transaction, never with preview counts. */
internal object GalleryRestoreResults {
    fun build(plan: GalleryRestorePlan, target: GalleryRestoreTarget, before: GalleryLocalBackupState,
              mediaAfter: List<String>, foldersAfter: List<FavoriteBackupCodec.FolderRecord>,
              profilesAfter: PhotoProfileState, preferencesAfter: Map<String, Any?>, cacheRepaired: Int): GalleryRestoreResult {
        val old = before.snapshot.mediaFavorites.toSet()
        val actual = mediaAfter.toSet()
        val incoming = plan.incoming.mediaFavorites.toSet()
        val statuses = plan.statusesFor(target.mode)
        val skipped = incoming.mapNotNull { path ->
            val request = BackupPathRequest(path, false)
            val detail = plan.accessDetails[request]
            when (statuses[request]) {
                BackupPathStatus.AVAILABLE -> null
                BackupPathStatus.MISSING -> RestoreSkipReason.MISSING
                BackupPathStatus.OFFLINE_STORAGE -> RestoreSkipReason.OFFLINE
                BackupPathStatus.SAF_PERMISSION -> RestoreSkipReason.SAF_PERMISSION
                BackupPathStatus.MEDIA_PERMISSION -> RestoreSkipReason.MEDIA_PERMISSION
                BackupPathStatus.PROTECTED -> when {
                    detail?.protectedRoots?.isNotEmpty() == true -> RestoreSkipReason.SOURCE_PROTECTION
                    detail?.otherProtection == true -> RestoreSkipReason.OTHER_PROTECTION
                    detail?.requiresFavoriteCredential == true -> RestoreSkipReason.FAVORITE_AUTHORIZATION
                    else -> RestoreSkipReason.OTHER_PROTECTION
                }
                else -> RestoreSkipReason.UNCONFIRMED
            }
        }.groupingBy { it }.eachCount()
        val retained = old.intersect(actual)
        val unsafe = retained.count { statuses[BackupPathRequest(it, false)] != BackupPathStatus.AVAILABLE }
        val media = AppliedMediaRestore(plan.incoming.mediaTotal,
            incoming.count { it in old && it in actual && statuses[BackupPathRequest(it, false)] == BackupPathStatus.AVAILABLE },
            (actual - old).size, (old - actual).size, plan.incoming.mediaDuplicates, skipped,
            retained.size, unsafe, cacheRepaired, actual.size)
        fun keyed(records: List<FavoriteBackupCodec.FolderRecord>) = records.associateBy { FavoriteFolderRules.normalizedPath(it.path) }
        val oldFolders = keyed(before.snapshot.favoriteFolders)
        val newFolders = keyed(foldersAfter)
        val incomingFolders = keyed(plan.incoming.favoriteFolders)
        val folders = AppliedFolderRestore(incomingFolders.size, (newFolders.keys - oldFolders.keys).size,
            incomingFolders.keys.count { it in oldFolders && it in newFolders }, (oldFolders.keys - newFolders.keys).size,
            incomingFolders.count { (key, value) -> key in oldFolders && oldFolders[key] != value },
            foldersAfter.count { statuses[BackupPathRequest(it.path, true)] == BackupPathStatus.OFFLINE_STORAGE })
        val oldProfiles = before.snapshot.photoProfiles.profiles
        val actualProfiles = profilesAfter.profiles
        fun equivalent(a: PhotoProfile, b: PhotoProfile) = a.name == b.name && a.sources.toSet() == b.sources.toSet()
        val inaccessibleSources = actualProfiles.flatMap { it.sources }.distinct().count { source ->
            val request = BackupPathRequest(if (source.type == PhotoSourceType.FAVORITES) FAVORITES else source.path!!, true)
            statuses[request] != BackupPathStatus.AVAILABLE
        }
        val profiles = AppliedProfileRestore(actualProfiles.count { p -> oldProfiles.none { it.id == p.id } },
            plan.incoming.photoProfiles.profiles.count { p -> oldProfiles.any { equivalent(it, p) } && actualProfiles.any { equivalent(it, p) } },
            plan.incoming.photoProfiles.profiles.count { p -> oldProfiles.any { it.id == p.id && !equivalent(it, p) } && actualProfiles.any { equivalent(it, p) } },
            oldProfiles.count { p -> actualProfiles.none { it.id == p.id } }, inaccessibleSources)
        val changedKeys = (before.preferences.keys + preferencesAfter.keys).filter {
            before.preferences[it] != preferencesAfter[it] || (it in before.preferences) != (it in preferencesAfter)
        }.toSet() - PhotoProfileRepository.KEY
        val suppliedKeys = LegacySettingsCodec.decode(plan.incoming.legacySettings).values.keys + plan.incoming.oxygenSettings.values.keys
        val settings = AppliedSettingsRestore(changedKeys.size, target.retainedSecuritySettings.size, (suppliedKeys - changedKeys).size)
        val partial = skipped.isNotEmpty() || unsafe > 0 || folders.offlineRetained > 0 || inaccessibleSources > 0 || settings.securityRetained > 0
        val changed = old != actual || before.snapshot.favoriteFolders.toSet() != foldersAfter.toSet() ||
            before.snapshot.photoProfiles != profilesAfter || changedKeys.isNotEmpty() || cacheRepaired > 0
        val completion = if (partial) GalleryRestoreCompletion.PARTIAL else if (!changed) GalleryRestoreCompletion.NO_CHANGES else GalleryRestoreCompletion.COMPLETE
        return GalleryRestoreResult(target, retained.size,
            statuses.filter { it.key.path != FAVORITES && it.value != BackupPathStatus.AVAILABLE }.keys.map { it.path }.toSet().size,
            media, folders, profiles, settings, completion)
    }
}
