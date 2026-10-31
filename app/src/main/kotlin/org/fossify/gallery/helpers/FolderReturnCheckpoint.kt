package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
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
    val capturedAt: Long
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

        fun capture(context: Context, folder: String): FolderReturnCheckpoint? {
            if (!isRPlus() || Environment.isExternalStorageManager() ||
                !folder.startsWith('/') || folder.contains("/.")) return null
            return runCatching {
                val cfg = context.config
                if (FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) != null ||
                    FavoriteFolderRules.isExcluded(folder, cfg.excludedFolders)) return null
                val directory = File(folder)
                if (!directory.isDirectory || !directory.canRead()) return null
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

        fun isCurrent(context: Context, folder: String, previous: FolderReturnCheckpoint): Boolean {
            val current = capture(context, folder) ?: return false
            return FolderReturnPolicy.canReuse(
                publicOrdinaryFolder = previous.folder == folder,
                completeDisplay = true,
                sameSettings = previous.configuration == current.configuration,
                sameMediaStore = previous.generations == current.generations,
                sameDirectoryTimestamp = previous.directoryModified == current.directoryModified,
                unchangedViewerContent = previous.revision == current.revision,
                ageMillis = current.capturedAt - previous.capturedAt
            )
        }
    }
}
