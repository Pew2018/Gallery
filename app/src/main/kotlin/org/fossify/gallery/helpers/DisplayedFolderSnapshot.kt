package org.fossify.gallery.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.extensions.config
import java.io.File

internal enum class DisplayedSourceStage { CACHE_PREVIEW, SCAN_PARTIAL, SCAN_COMPLETE }

/** Display capability, never a completeness/existence proof and never persisted. */
internal data class DisplayedFolderSnapshot(
    val request: Long, val version: Long, val stage: DisplayedSourceStage,
    val accessCurrent: () -> Boolean
)

internal object FolderCachePreviewAccess {
    /** Worker-only capture; no enumeration and no new permission acquisition. */
    fun capture(context: Context, folder: String): () -> Boolean {
        val target = File(folder).canonicalPath
        val shared = target.startsWith("/storage/") || target.startsWith("/sdcard/")
        val grants = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && grantCovers(target, it.uri) }.map { it.uri }.toSet()
        val legacyReadable = File(folder).canRead()
        // All-files permission does not grant access to SAF-restricted directories.
        val directReadable = legacyReadable && !context.isRestrictedWithSAFSdk30(folder)
        return {
            runCatching {
                when {
                    !shared -> legacyReadable
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        Environment.isExternalStorageManager() && directReadable -> true
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> legacyReadable &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                    else -> context.contentResolver.persistedUriPermissions.any {
                        it.isReadPermission && it.uri in grants
                    }
                }
            }.getOrDefault(false)
        }
    }

    private fun grantCovers(target: String, uri: Uri): Boolean = runCatching {
        if (uri.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri)) {
            return@runCatching false
        }
        val parts = DocumentsContract.getTreeDocumentId(uri).split(':', limit = 2)
        if (parts.size != 2 || parts[1].split('/').any { it == "." || it == ".." }) return@runCatching false
        val volume = when {
            parts[0] == "primary" -> Environment.getExternalStorageDirectory().path
            parts[0].matches(Regex("[A-Za-z0-9-]+")) -> "/storage/${parts[0]}"
            else -> return@runCatching false
        }
        val root = File(volume, parts[1]).canonicalPath.trimEnd('/')
        target == root || target.startsWith("$root/")
    }.getOrDefault(false)

    fun configuration(context: Context, folder: String): String = context.config.run {
        listOf(filterMedia, shouldShowHidden, getFolderGrouping(folder), excludedFolders.toSortedSet(),
            includedFolders.toSortedSet(), temporarilyShowExcluded).joinToString("|")
    }
}
