package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.provider.DocumentsContract
import android.net.Uri
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.isExternalStorageManager
import java.io.File
import java.io.IOException

internal data class FolderEnumeration(
    val files: List<File>, val method: String = "direct", val limited: Boolean = false,
    val current: () -> Boolean = { true }
)
internal data class FolderFileMetadata(val size: Long?, val modified: Long?, val readable: Boolean, val regular: Boolean)
internal interface FolderFileAccess {
    fun enumerate(folder: String): FolderEnumeration
    fun metadata(file: File): FolderFileMetadata
}

/** Worker-only. A grant is resolved by provider document ID and verified against persisted read grants.
 * No ordinary path is treated as a grant and no grants are acquired by this reader. */
internal class DeviceFolderFileAccess(private val context: Context) : FolderFileAccess {
    private val documents = hashMapOf<String, Uri>()
    override fun enumerate(folder: String): FolderEnumeration {
        documents.clear()
        val target = File(folder).canonicalFile.path
        val shared = target.startsWith("/storage/") || target.startsWith("/sdcard/")
        val limited = shared && isRPlus() && !isExternalStorageManager()
        val direct = File(folder).listFiles()?.toList()
        if (!limited && direct != null) return FolderEnumeration(direct, current = {
            !shared || !isRPlus() || isExternalStorageManager()
        })
        try {
        val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        for (grant in grants) {
            val uri = grant.uri
            if (uri.authority != "com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri)) continue
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val parts = rootId.split(':', limit = 2)
            if (parts.size != 2 || parts[1].split('/').any { it == "." || it == ".." }) continue
            val volume = if (parts[0] == "primary") Environment.getExternalStorageDirectory().path
                else if (parts[0].matches(Regex("[A-Za-z0-9-]+"))) "/storage/${parts[0]}" else continue
            val root = File(volume, parts[1]).canonicalFile.path.trimEnd('/')
            if (target != root && !target.startsWith("$root/")) continue
            fun current() = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            val suffix = target.removePrefix(root).trimStart('/')
            val targetId = rootId.trimEnd('/') + if (suffix.isEmpty()) "" else (if (rootId.endsWith(':')) "" else "/") + suffix
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, targetId)
            val files = arrayListOf<File>()
            val cursor = context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?: throw IOException("Directory provider returned no cursor")
            cursor.use {
                while (it.moveToNext()) {
                    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                    val id = it.getString(0)
                    val name = it.getString(1) ?: continue
                    if (name.contains('/') || name == "." || name == "..") continue
                    // ExternalStorageProvider child identity must describe exactly this direct child.
                    if (id != targetId.trimEnd('/') + (if (targetId.endsWith(':')) "" else "/") + name) continue
                    val file = File(folder, name)
                    documents[file.path] = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    files.add(file)
                }
            }
            if (!current()) throw SecurityException("Directory grant revoked")
            return FolderEnumeration(files, "persisted-tree", current = ::current)
        }
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException || Thread.currentThread().isInterrupted) throw error
            if (direct != null) return FolderEnumeration(direct, "direct-after-tree-failure", limited = true)
            throw error
        }
        val files = direct ?: if (limited) throw SecurityException("Full directory access is required")
            else throw IOException("Directory cannot be enumerated")
        return FolderEnumeration(files, limited = limited)
    }

    override fun metadata(file: File): FolderFileMetadata {
        val uri = documents[file.path]
        if (uri != null) {
            val columns = arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE)
            val cursor = context.contentResolver.query(uri, columns, null, null, null)
                ?: throw IOException("File metadata unavailable")
            return cursor.use {
                if (!it.moveToFirst()) throw IOException("File disappeared during scan")
                val size = if (it.isNull(0)) null else it.getLong(0)
                val regular = it.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR
                // An open descriptor proves readability; unknown size is retained as the project's 0 sentinel.
                val readable = regular && context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
                FolderFileMetadata(size, if (it.isNull(1)) null else it.getLong(1), readable, regular)
            }
        }
        // One attribute read gives type, size, and millisecond mtime from
        // the same file identity. An inaccessible path throws instead of
        // masquerading as an empty file.
        val attributes = java.nio.file.Files.readAttributes(
            file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java
        )
        val regular = attributes.isRegularFile
        return FolderFileMetadata(attributes.size(), attributes.lastModifiedTime().toMillis(),
            regular && file.canRead(), regular)
    }
}
