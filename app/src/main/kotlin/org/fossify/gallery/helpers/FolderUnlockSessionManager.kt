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
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Folder session creation cancelled")
        val root = canonical(folderPath)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Folder session creation cancelled")
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
        return isMediaAuthorized(session, mediaPath, isFolderProtected, ::canonical)
    }

    /** Batch counterpart with caches scoped to this call only. */
    fun authorizedPaths(
        token: String?,
        mediaPaths: Collection<String>,
        isFolderProtected: (String) -> Boolean
    ): Set<String> {
        val session = token?.let(sessions::get) ?: return emptySet()
        val canonicalCache = hashMapOf<String, String>()
        val protectionCache = hashMapOf<String, Boolean>()
        fun cachedCanonical(path: String): String = canonicalCache.getOrPut(path) { canonical(path) }
        fun cachedProtected(path: String): Boolean = protectionCache.getOrPut(path) { isFolderProtected(path) }
        val authorized = LinkedHashSet<String>()
        mediaPaths.forEach { path ->
            if (Thread.currentThread().isInterrupted) return emptySet()
            if (isMediaAuthorized(session, path, ::cachedProtected, ::cachedCanonical)) authorized.add(path)
        }
        return authorized
    }

    private fun isMediaAuthorized(
        session: Session,
        mediaPath: String,
        isFolderProtected: (String) -> Boolean,
        canonicalPath: (String) -> String
    ): Boolean {
        val target = canonicalPath(mediaPath)
        if (target == session.root || !isInside(session.root, target)) return false

        // Respect additional folder locks inside the unlocked folder, including
        // media reached through a symbolic link or an alternate path spelling.
        val rawParent = File(mediaPath).parent ?: return false
        val resolvedParent = File(target).parent ?: return false
        val protectedAncestors = listOfNotNull(
            findProtectedAncestor(rawParent, isFolderProtected, canonicalPath),
            findProtectedAncestor(resolvedParent, isFolderProtected, canonicalPath)
        )
        for (protectedAncestor in protectedAncestors) {
            val protectedRoot = canonicalPath(protectedAncestor)
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
    ): String? = findProtectedAncestor(folderPath, isFolderProtected, ::canonical)

    private fun findProtectedAncestor(
        folderPath: String,
        isFolderProtected: (String) -> Boolean,
        canonicalPath: (String) -> String
    ): String? {
        var candidate: File? = File(folderPath).absoluteFile
        while (candidate != null) {
            if (Thread.currentThread().isInterrupted) return null
            val rawPath = candidate.path
            val resolved = canonicalPath(rawPath)
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
