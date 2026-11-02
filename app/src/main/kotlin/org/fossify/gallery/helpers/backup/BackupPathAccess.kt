package org.fossify.gallery.helpers.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.DocumentsContract
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import java.io.File

internal enum class BackupPathStatus { AVAILABLE, MISSING, OFFLINE_STORAGE, SAF_PERMISSION, MEDIA_PERMISSION, PROTECTED, UNCONFIRMED }

/** Physical access is independent of the virtual album credential. No secrets are retained here. */
internal data class RestorePathAccess(
    val storageStatus: BackupPathStatus,
    val requiresFavoriteCredential: Boolean = false,
    val protectedRoots: Set<String> = emptySet(),
    val otherProtection: Boolean = false,
    val identity: String? = null
) {
    fun status(favoriteAuthorized: Boolean = false): BackupPathStatus = when {
        storageStatus != BackupPathStatus.AVAILABLE -> storageStatus
        protectedRoots.isNotEmpty() || otherProtection || requiresFavoriteCredential && !favoriteAuthorized -> BackupPathStatus.PROTECTED
        else -> BackupPathStatus.AVAILABLE
    }
}

internal fun interface BackupPathAccess {
    fun inspect(path: String, folder: Boolean): BackupPathStatus
    fun inspectBatch(requests: Set<BackupPathRequest>): Map<BackupPathRequest, RestorePathAccess> = requests.associateWith {
        val status = inspect(it.path, it.folder)
        // A legacy/custom PROTECTED result is never promoted by virtual-album authorization.
        RestorePathAccess(if (status == BackupPathStatus.PROTECTED) BackupPathStatus.AVAILABLE else status,
            otherProtection = status == BackupPathStatus.PROTECTED)
    }
}

/** Enforce the album gate at the restoration boundary even for an injected storage inspector. */
internal fun BackupPathAccess.inspectRestoreBatch(context: Context, requests: Set<BackupPathRequest>): Map<BackupPathRequest, RestorePathAccess> {
    val favoriteRequired = context.config.isFavoritePasswordProtectionOn || context.config.isFolderProtected(FAVORITES)
    return inspectBatch(requests).mapValues { (request, detail) ->
        if (!request.folder && favoriteRequired) detail.copy(requiresFavoriteCredential = true) else detail
    }
}

/** Worker-only metadata checks. Never opens media, unlocks sources or acquires URI grants. */
internal class DeviceBackupPathAccess(private val context: Context) : BackupPathAccess {
    override fun inspect(path: String, folder: Boolean) = inspectBatch(setOf(BackupPathRequest(path, folder))).values.single().status()

    override fun inspectBatch(requests: Set<BackupPathRequest>): Map<BackupPathRequest, RestorePathAccess> {
        val config = context.config
        val favoriteRequired = config.isFavoritePasswordProtectionOn || config.isFolderProtected(FAVORITES)
        val resolved = requests.associateWith { request ->
            when {
                request.path.startsWith("content://") -> resolveDocumentPath(Uri.parse(request.path))
                request.path.startsWith('/') -> request.path
                else -> null
            }
        }
        val paths = resolved.values.filterNotNull().toSet()
        val snapshot = runCatching { FavoriteAccessSessionManager.captureSnapshot(paths, context::favoriteProtection) }.getOrNull()
        val permission = hasMediaPermission()
        return requests.associateWith { request ->
            val path = request.path
            if (path == FAVORITES) {
                RestorePathAccess(BackupPathStatus.AVAILABLE, otherProtection = favoriteRequired || config.isPhotoPasswordProtectionOn)
            } else try {
                val uri = path.takeIf { it.startsWith("content://") }?.let(Uri::parse)
                val source = resolved[request]
                val entry = source?.let { snapshot?.entries?.get(it) }
                val document = uri?.let { inspectDocument(it, request.folder) }
                val storage = document?.first ?: inspectFile(path, request.folder, permission)
                val roots = entry?.locks?.keys.orEmpty().toMutableSet()
                if (uri != null) roots.addAll(uriProtectedRoots(uri))
                if (source != null) {
                    if (context.favoriteProtection(source) != null) roots.add(source)
                    if (entry != null && context.favoriteProtection(entry.canonicalPath) != null) roots.add(entry.canonicalPath)
                }
                val verifiedStorage = if (storage == BackupPathStatus.AVAILABLE && entry == null) BackupPathStatus.UNCONFIRMED else storage
                RestorePathAccess(verifiedStorage, !request.folder && favoriteRequired, roots,
                    identity = entry?.let { BackupDigest.sha256((it.toString() + document?.second.orEmpty()).toByteArray(Charsets.UTF_8)) })
            } catch (_: Exception) { RestorePathAccess(BackupPathStatus.UNCONFIRMED, !request.folder && favoriteRequired) }
        }
    }

    private fun inspectFile(path: String, folder: Boolean, mediaPermission: Boolean): BackupPathStatus {
        if (!path.startsWith('/') || path.split('/').any { it == "." || it == ".." }) return BackupPathStatus.UNCONFIRMED
        val file = File(path)
        val volume = path.takeIf { it.startsWith("/storage/") }?.split('/')?.getOrNull(2)
        return when {
            volume != null && volume !in setOf("emulated", "self") && !File("/storage/$volume").exists() -> BackupPathStatus.OFFLINE_STORAGE
            !mediaPermission -> BackupPathStatus.MEDIA_PERMISSION
            !file.exists() -> if (file.parentFile?.canRead() == true) BackupPathStatus.MISSING else BackupPathStatus.UNCONFIRMED
            (folder && !file.isDirectory) || (!folder && !file.isFile) -> BackupPathStatus.MISSING
            !file.canRead() -> BackupPathStatus.UNCONFIRMED
            else -> BackupPathStatus.AVAILABLE
        }
    }

    private fun inspectDocument(uri: Uri, folder: Boolean): Pair<BackupPathStatus, String?> {
        val persisted = context.contentResolver.persistedUriPermissions.any { grant ->
            grant.isReadPermission && (grant.uri == uri || runCatching {
                grant.uri.authority == uri.authority && DocumentsContract.isTreeUri(grant.uri) &&
                    DocumentsContract.getDocumentId(uri).let { id ->
                        val root = DocumentsContract.getTreeDocumentId(grant.uri)
                        id == root || id.startsWith("$root/")
                    }
            }.getOrDefault(false))
        }
        if (!persisted || context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED)
            return BackupPathStatus.SAF_PERMISSION to null
        val document = if (folder && runCatching { DocumentsContract.getDocumentId(uri) }.isFailure)
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        else androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
        val status = when {
            document == null -> BackupPathStatus.UNCONFIRMED
            !document.exists() -> BackupPathStatus.MISSING
            !document.canRead() -> BackupPathStatus.SAF_PERMISSION
            folder && document.isDirectory || !folder && document.isFile -> BackupPathStatus.AVAILABLE
            else -> BackupPathStatus.UNCONFIRMED
        }
        val identity = if (status == BackupPathStatus.AVAILABLE) "$uri|${document!!.lastModified()}|${document.length()}" else null
        return status to identity
    }

    /** Only the system external-storage provider has an unambiguous filesystem protection scope.
     * Opaque provider URIs remain UNCONFIRMED even with a grant; never feed a URI to File snapshots. */
    private fun resolveDocumentPath(uri: Uri): String? = runCatching {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrElse { DocumentsContract.getTreeDocumentId(uri) }
        val parts = id.split(':', limit = 2)
        if (parts.size != 2 || parts[1].startsWith('/') || parts[1].split('/').any { it in setOf(".", "..") }) return null
        val root = if (parts[0] == "primary") Environment.getExternalStorageDirectory().absolutePath
            else if (parts[0].matches(Regex("[A-Za-z0-9-]+"))) "/storage/${parts[0]}" else return null
        root.trimEnd('/') + "/" + parts[1]
    }.getOrNull()

    private fun uriProtectedRoots(uri: Uri): Set<String> = buildSet {
        fun check(candidate: String) { if (context.favoriteProtection(candidate) != null) add(candidate) }
        check(uri.toString())
        runCatching {
            val authority = uri.authority ?: return@runCatching
            var id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrElse { DocumentsContract.getTreeDocumentId(uri) }
            while (true) {
                check(DocumentsContract.buildDocumentUri(authority, id).toString())
                check(DocumentsContract.buildTreeDocumentUri(authority, id).toString())
                if (DocumentsContract.isTreeUri(uri)) check(DocumentsContract.buildDocumentUriUsingTree(uri, id).toString())
                val slash = id.lastIndexOf('/')
                if (slash < 0) {
                    val colon = id.indexOf(':')
                    if (colon >= 0 && id.length > colon + 1) { id = id.substring(0, colon + 1); continue }
                    break
                }
                id = id.substring(0, slash)
            }
        }
    }

    private fun hasMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) return true
        val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
