package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.extensions.config
import java.io.File

/** Freshness evidence for reusing a complete public Photos-profile catalog. */
internal data class PhotoProfileCatalogCheckpoint(
    val volumeGenerations: Map<String, String>,
    val folderStates: Map<String, FolderState>
) {
    internal data class FolderState(val canonicalPath: String, val modified: Long)

    companion object {
        fun capture(context: Context, scope: PhotoProfileScope): PhotoProfileCatalogCheckpoint? {
            if (!isRPlus() || Environment.isExternalStorageManager() || scope.sessionId != null || scope.allFiles) return null
            return runCatching {
                val folders = linkedMapOf<String, FolderState>()
                scope.sources.filter { it.type == PhotoSourceType.FOLDER }.forEach { source ->
                    val path = source.path ?: return null
                    if (!path.startsWith('/') || path.contains("/.") ||
                        FavoriteFolderRules.isExcluded(path, scope.excluded) ||
                        FolderUnlockSessionManager.findProtectedAncestor(path, context.config::isFolderProtected) != null) {
                        return null
                    }
                    val folder = File(path)
                    if (!folder.isDirectory || !folder.canRead()) return null
                    val canonical = folder.canonicalPath
                    val modified = folder.lastModified()
                    if (modified <= 0L) return null
                    folders[path] = FolderState(canonical, modified)
                }
                val volumes = MediaStore.getExternalVolumeNames(context)
                if (volumes.isEmpty()) return null
                val generations = volumes.associateWith { volume ->
                    "${MediaStore.getVersion(context, volume)}:${MediaStore.getGeneration(context, volume)}"
                }
                PhotoProfileCatalogCheckpoint(generations, folders)
            }.getOrNull()
        }

        fun isCurrent(context: Context, scope: PhotoProfileScope, previous: PhotoProfileCatalogCheckpoint): Boolean =
            capture(context, scope) == previous
    }
}
