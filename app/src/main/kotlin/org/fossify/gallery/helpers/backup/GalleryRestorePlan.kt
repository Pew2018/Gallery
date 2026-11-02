package org.fossify.gallery.helpers.backup

import org.fossify.gallery.helpers.FavoriteBackupCodec
import org.fossify.gallery.helpers.PhotoProfileState

internal enum class GalleryRestoreMode { MERGE, REPLACE }
internal data class BackupPathRequest(val path: String, val folder: Boolean)
internal data class RestoreCategoryCount(val incoming: Int, val existing: Int, val added: Int, val conflicts: Int = 0, val removed: Int = 0)
internal data class GalleryRestoreTarget(
    val mode: GalleryRestoreMode,
    val preferenceChanges: Map<String, Any?>,
    val media: List<String>,
    val folders: List<FavoriteBackupCodec.FolderRecord>,
    val profiles: PhotoProfileState,
    val mediaCount: RestoreCategoryCount,
    val folderCount: RestoreCategoryCount,
    val profileCount: RestoreCategoryCount,
    val retainedSecuritySettings: Set<String> = emptySet()
)
internal data class GalleryRestorePlan(
    val incoming: GalleryBackupSnapshot,
    val localToken: String,
    val statuses: Map<BackupPathRequest, BackupPathStatus>,
    val settingsCount: Int,
    val targets: Map<GalleryRestoreMode, GalleryRestoreTarget>,
    val accessDetails: Map<BackupPathRequest, RestorePathAccess> = emptyMap(),
    val existingMedia: Set<String> = emptySet(),
    val id: String = java.util.UUID.randomUUID().toString(),
    val securityVersion: String = "",
    val sessionGeneration: Long = 0,
    val authorization: GalleryRestoreAuthorization? = null,
    val authorizationBaseline: String = ""
) {
    val unresolvedCount get() = statuses.values.count { it != BackupPathStatus.AVAILABLE }
    fun statusesFor(mode: GalleryRestoreMode): Map<BackupPathRequest, BackupPathStatus> =
        if (authorization == null || authorization.mode == mode) statuses
        else accessDetails.mapValues { it.value.status() }
    fun requiresFavoriteAuthorization(mode: GalleryRestoreMode): Boolean {
        val candidates = incoming.mediaFavorites.toSet() + if (mode == GalleryRestoreMode.REPLACE) existingMedia else emptySet()
        return candidates.any { path -> accessDetails[BackupPathRequest(path, false)]?.let {
            it.requiresFavoriteCredential && it.storageStatus == BackupPathStatus.AVAILABLE &&
                it.protectedRoots.isEmpty() && !it.otherProtection
        } == true }
    }
}
internal class StaleGalleryRestorePlan(val reason: RestoreStaleReason = RestoreStaleReason.LOCAL_STATE) : IllegalStateException("Local data or access changed; preview again")
internal class PendingGalleryRestore : IllegalStateException("An earlier restore checkpoint needs recovery")
internal class GalleryRecoveryBlocked(cause: Exception) : IllegalStateException("Restore checkpoint needs review", cause)
internal enum class RestoreWriteStage { ROOM, PREFERENCES, CLEANUP }
internal class GalleryRestoreCheckpointFailure(cause: Exception) : java.io.IOException("Could not write restore checkpoint", cause)
internal class GalleryRestoreFailure(val compensationSucceeded: Boolean, cause: Exception, val stage: RestoreWriteStage = RestoreWriteStage.ROOM) :
    java.io.IOException("Restore failed; compensation succeeded=$compensationSucceeded", cause)
