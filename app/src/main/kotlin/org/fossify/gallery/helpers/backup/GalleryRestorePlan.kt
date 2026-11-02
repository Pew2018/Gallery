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
    val targets: Map<GalleryRestoreMode, GalleryRestoreTarget>
) {
    val unresolvedCount get() = statuses.values.count { it != BackupPathStatus.AVAILABLE }
}
internal class StaleGalleryRestorePlan : IllegalStateException("Local data or access changed; preview again")
internal class PendingGalleryRestore : IllegalStateException("An earlier restore checkpoint needs recovery")
internal class GalleryRestoreFailure(val compensationSucceeded: Boolean, cause: Exception) :
    java.io.IOException("Restore failed; compensation succeeded=$compensationSucceeded", cause)
