package org.fossify.gallery.helpers

import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral, folder-scoped authorization for an already authenticated gallery
 * navigation session. Never persist these tokens or accept a caller's Intent
 * boolean as proof of authentication.
 */
internal object FolderUnlockSessionManager {
    private data class Session(val root: String)

    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, Session>()

    fun createSession(folderPath: String): String {
        val root = canonical(folderPath)
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        sessions[token] = Session(root)
        return token
    }

    fun isFolderAuthorized(token: String?, folderPath: String): Boolean {
        val session = token?.let(sessions::get) ?: return false
        return session.root == canonical(folderPath)
    }

    fun isMediaAuthorized(
        token: String?,
        mediaPath: String,
        isFolderProtected: (String) -> Boolean
    ): Boolean {
        val session = token?.let(sessions::get) ?: return false
        val target = canonical(mediaPath)
        if (target == session.root || !isInside(session.root, target)) return false

        // Respect additional folder locks inside the unlocked folder, including
        // media reached through a symbolic link or an alternate path spelling.
        val rawParent = File(mediaPath).parent ?: return false
        val resolvedParent = File(target).parent ?: return false
        val protectedAncestors = listOfNotNull(
            findProtectedAncestor(rawParent, isFolderProtected),
            findProtectedAncestor(resolvedParent, isFolderProtected)
        )
        for (protectedAncestor in protectedAncestors) {
            val protectedRoot = canonical(protectedAncestor)
            if (protectedRoot != session.root && isInside(session.root, protectedRoot)) {
                return false
            }
            if (protectedRoot != session.root && !isInside(protectedRoot, session.root)) {
                return false
            }
        }
        return true
    }

    fun findProtectedAncestor(
        folderPath: String,
        isFolderProtected: (String) -> Boolean
    ): String? {
        var candidate: File? = File(folderPath).absoluteFile
        while (candidate != null) {
            val rawPath = candidate.path
            val resolved = canonical(rawPath)
            if (isFolderProtected(rawPath) || isFolderProtected(resolved)) return rawPath
            candidate = candidate.parentFile
        }
        return null
    }

    fun revokeSession(token: String?) {
        if (token != null) sessions.remove(token)
    }

    fun revokeAll() {
        sessions.clear()
    }

    private fun canonical(path: String): String =
        File(path).canonicalPath

    private fun isInside(root: String, target: String): Boolean =
        target.startsWith(if (root == File.separator) root else root + File.separator)
}
