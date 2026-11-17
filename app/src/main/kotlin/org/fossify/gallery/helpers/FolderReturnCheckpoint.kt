package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.extensions.config
import java.io.File

/**
 * Conservative return optimization for public, scoped-storage physical albums.
 * Only skip the normal reload when every supported media volume and the folder
 * itself have unchanged generations. All failures fall back to the existing scan.
 */
internal data class FolderReturnCheckpoint(
    val folder: String,
    val configuration: String,
    val generations: Map<String, String>,
    val directoryModified: Long,
    val revision: Long,
    val capturedAt: Long,
    val canonicalFolder: String = folder,
    val access: DirectoryAccessState? = null,
) {
    companion object {
        private fun configuration(context: Context, path: String): String {
            val cfg = context.config
            return listOf(
                cfg.getFolderSorting(path), cfg.getFolderGrouping(path),
                cfg.getFolderViewType(path), cfg.filterMedia, cfg.shouldShowHidden, cfg.excludedFolders,
                cfg.includedFolders, cfg.fileLoadingPriority,
                cfg.temporarilyShowHidden, cfg.temporarilyShowExcluded
            ).joinToString("|")
        }

        fun capture(context: Context, folder: String, allowManager: Boolean = false): FolderReturnCheckpoint? {
            if (!isRPlus() || (!allowManager && Environment.isExternalStorageManager()) ||
                !folder.startsWith('/') || folder.contains("/.")) return null
            return runCatching {
                val cfg = context.config
                if (context.isPathOnOTG(folder)) return null
                if (FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) != null ||
                    FavoriteFolderRules.isExcluded(folder, cfg.excludedFolders)) return null
                val directory = File(folder)
                if (!directory.isDirectory || !directory.canRead()) return null
                if (context.isRestrictedWithSAFSdk30(folder) || NoMediaSnapshot().contains(folder)) return null
                val modified = directory.lastModified()
                if (modified <= 0L) return null
                val volumes = MediaStore.getExternalVolumeNames(context)
                if (volumes.isEmpty()) return null
                val version = volumes.associateWith { volume ->
                    "${MediaStore.getVersion(context, volume)}:${MediaStore.getGeneration(context, volume)}"
                }
                FolderReturnCheckpoint(
                    folder, configuration(context, folder), version,
                    modified, FolderContentRevision.current(),
                    SystemClock.elapsedRealtime()
                )
            }.getOrNull()
        }

        // A partial catalog may outlive the host's pause only with full media
        // access. A selected-media grant or a SAF grant is intentionally ineligible.
        fun captureForViewerScan(context: Context, folder: String): FolderReturnCheckpoint? {
            if (!isRPlus() || (!Environment.isExternalStorageManager() &&
                !context.hasAllPermissions(getPermissionsToRequest()))) return null
            return runCatching {
                val checkpoint = capture(context, folder, allowManager = true) ?: return null
                checkpoint.copy(canonicalFolder = File(folder).canonicalPath,
                    access = DirectoryAccessState.capture(context))
            }.getOrNull()
        }

        fun isCurrent(context: Context, folder: String, previous: FolderReturnCheckpoint): Boolean {
            val current = capture(context, folder) ?: return false
            return sameSnapshot(previous, current) &&
                FolderReturnPolicy.canReuse(
                    publicOrdinaryFolder = previous.folder == folder,
                    completeDisplay = true,
                    sameSettings = previous.configuration == current.configuration,
                    sameMediaStore = previous.generations == current.generations,
                    sameDirectoryTimestamp = previous.directoryModified == current.directoryModified,
                    unchangedViewerContent = previous.revision == current.revision,
                    ageMillis = current.capturedAt - previous.capturedAt
                )
        }

        /** Compares a scan's before/after proof, intentionally ignoring capture time. */
        fun sameSnapshot(first: FolderReturnCheckpoint?, second: FolderReturnCheckpoint?): Boolean =
            first != null && second != null && first.folder == second.folder &&
                first.canonicalFolder == second.canonicalFolder && first.access == second.access &&
                first.configuration == second.configuration && first.generations == second.generations &&
                first.directoryModified == second.directoryModified && first.revision == second.revision
    }
}
