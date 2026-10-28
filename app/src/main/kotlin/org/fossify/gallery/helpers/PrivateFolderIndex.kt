package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Access-controlled directory INDEX, not encryption. Source media stays where it is.
 * Paths are used only to suppress entries in Gallery; the persisted SAF grants
 * are used to actually read media. No files are moved, renamed or deleted.
 */
class PrivateFolderIndex(private val context: Context) {
    data class Folder(val uri: String, val path: String, val name: String, val addedAt: Long)
    private val prefs = context.getSharedPreferences("oxygen_private_folder_index_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<Folder>>() {}.type

    fun folders(): List<Folder> = try {
        gson.fromJson<List<Folder>>(prefs.getString("folders", "[]"), type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun isAccessible(folder: Folder): Boolean = try {
        val uri = Uri.parse(folder.uri)
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } &&
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.canRead() == true
    } catch (_: Exception) {
        false
    }

    /** Restrict to mappable external-storage SAF directories so MediaStore filtering is reliable. */
    fun pathForUri(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
        val divider = id.indexOf(':')
        if (divider < 0) return null
        val volume = id.substring(0, divider)
        val relative = id.substring(divider + 1)
        if (relative.split('/').any { it == "." || it == ".." }) return null
        val base = if (volume.equals("primary", true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else if (volume.matches(Regex("[A-Za-z0-9_-]{4,32}"))) {
            "/storage/" + volume
        } else return null
        return File(base, relative).absolutePath.trimEnd('/')
    }

    fun add(uri: Uri): Boolean {
        val path = pathForUri(uri) ?: return false
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri) ?: return false
        if (!doc.isDirectory) return false
        val previous = folders()
        val entry = Folder(uri.toString(), path, doc.name ?: File(path).name, System.currentTimeMillis())
        if (previous.any { it.path.equals(path, true) }) return true
        prefs.edit().putString("folders", gson.toJson(previous + entry)).commit()
        return true
    }

    fun remove(folder: Folder) {
        prefs.edit().putString("folders", gson.toJson(folders().filterNot { it.uri == folder.uri })).commit()
        try { context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }

    companion object {
        fun protectedPaths(context: Context): List<String> =
            PrivateFolderIndex(context).folders().map { it.path.trimEnd('/').lowercase(Locale.ROOT) }

        fun isProtected(path: String, roots: List<String>): Boolean {
            if (path.isBlank()) return false
            val canonical = path.trimEnd('/').lowercase(Locale.ROOT)
            return roots.any { canonical == it || canonical.startsWith(it + "/") }
        }
        fun isProtected(context: Context, path: String): Boolean = isProtected(path, protectedPaths(context))
    }
}

/** Password verifier: salted PBKDF2-HMAC-SHA256, never stores the password. */
class PrivateFolderAccess(context: Context) {
    private val prefs = context.getSharedPreferences("oxygen_private_folder_auth_v1", Context.MODE_PRIVATE)
    private val rounds = 210_000
    val configured: Boolean get() = prefs.contains("salt") && prefs.contains("verifier")
    var biometricsEnabled: Boolean
        get() = prefs.getBoolean("biometrics", false)
        set(value) { prefs.edit().putBoolean("biometrics", value).apply() }

    private fun hash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    fun setup(password: CharArray): Boolean {
        if (configured || password.size < 6) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = hash(password, salt)
        return prefs.edit().putString("salt", encode(salt)).putString("verifier", encode(verifier)).commit()
    }
    fun verify(password: CharArray): Boolean {
        if (!configured) return false
        return try {
            val salt = decode(prefs.getString("salt", "")!!)
            val expected = decode(prefs.getString("verifier", "")!!)
            MessageDigest.isEqual(expected, hash(password, salt))
        } catch (_: Exception) { false }
    }
    fun changePassword(password: CharArray): Boolean {
        if (!configured || password.size < 6) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = hash(password, salt)
        return prefs.edit().putString("salt", encode(salt)).putString("verifier", encode(verifier)).commit()
    }
}
