package org.fossify.gallery.helpers.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import java.io.File

internal enum class BackupPathStatus { AVAILABLE, MISSING, OFFLINE_STORAGE, SAF_PERMISSION, MEDIA_PERMISSION, PROTECTED, UNCONFIRMED }
internal fun interface BackupPathAccess { fun inspect(path: String, folder: Boolean): BackupPathStatus }

/** Metadata checks only; never unlock, acquire a grant, scan media or decode a thumbnail. */
internal class DeviceBackupPathAccess(private val context: Context) : BackupPathAccess {
    override fun inspect(path: String, folder: Boolean): BackupPathStatus = try {
        if (path == org.fossify.commons.helpers.FAVORITES) {
            if (context.config.isFavoritePasswordProtectionOn || context.config.isFolderProtected(org.fossify.commons.helpers.FAVORITES) || context.config.isPhotoPasswordProtectionOn)
                BackupPathStatus.PROTECTED else BackupPathStatus.AVAILABLE
        } else if (!folder && (context.config.isFavoritePasswordProtectionOn || context.config.isFolderProtected(org.fossify.commons.helpers.FAVORITES))) BackupPathStatus.PROTECTED
        else if (FavoriteAccessSessionManager.captureSnapshot(listOf(path), context::favoriteProtection)
                .entries[path]?.locks?.isEmpty() != true || context.favoriteProtection(path) != null) BackupPathStatus.PROTECTED
        else if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            if (context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED)
                BackupPathStatus.SAF_PERMISSION else {
                val document = if (folder && android.provider.DocumentsContract.isDocumentUri(context, uri).not())
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    else androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                when {
                    document == null -> BackupPathStatus.UNCONFIRMED
                    !document.exists() -> BackupPathStatus.MISSING
                    !document.canRead() -> BackupPathStatus.SAF_PERMISSION
                    folder && document.isDirectory || !folder && document.isFile -> BackupPathStatus.AVAILABLE
                    else -> BackupPathStatus.UNCONFIRMED
                }
            }
        } else {
            val file = File(path)
            val volume = path.takeIf { it.startsWith("/storage/") }?.split('/')?.getOrNull(2)
            when {
                volume != null && volume !in setOf("emulated", "self") && !File("/storage/$volume").exists() -> BackupPathStatus.OFFLINE_STORAGE
                !hasMediaPermission() -> BackupPathStatus.MEDIA_PERMISSION
                !file.exists() -> if (file.parentFile?.canRead() == true) BackupPathStatus.MISSING else BackupPathStatus.UNCONFIRMED
                (folder && !file.isDirectory) || (!folder && !file.isFile) -> BackupPathStatus.MISSING
                !file.canRead() -> BackupPathStatus.UNCONFIRMED
                else -> BackupPathStatus.AVAILABLE
            }
        }
    } catch (_: Exception) { BackupPathStatus.UNCONFIRMED }

    private fun hasMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) return true
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
