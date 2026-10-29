package org.fossify.gallery.helpers

import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/** Process-only capabilities for one virtual album, never for a filesystem directory. */
internal object FavoriteAccessSessionManager {
    data class Protection(val type: Int, val hash: String)
    data class Entry(
        val canonicalPath: String,
        val fileKey: String?,
        val fileSize: Long?,
        val fileModified: Long?,
        val locks: Map<String, Protection>
    )
    data class Snapshot(val entries: Map<String, Entry>)
    private data class Session(
        val owner: String,
        val snapshot: Snapshot,
        val protection: (String) -> Protection?,
        val credentialIsCurrent: () -> Boolean = { true }
    )

    /** A batch result is valid only while the exact session value remains installed. */
    data class ValidatedPaths internal constructor(
        val token: String,
        val paths: Set<String>,
        internal val session: Any
    )

    private val random = SecureRandom()
    private val generation = AtomicLong()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val listeners = ConcurrentHashMap<String, (String) -> Unit>()

    fun captureSnapshot(paths: Collection<String>, protection: (String) -> Protection?): Snapshot {
        val protectionCache = hashMapOf<String, Protection?>()
        fun cachedProtection(path: String): Protection? {
            if (!protectionCache.containsKey(path)) protectionCache[path] = protection(path)
            return protectionCache[path]
        }
        val entries = LinkedHashMap<String, Entry>(paths.size)
        paths.forEach { path ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Favorite snapshot capture cancelled")
            require(File(path).isAbsolute)
            val canonical = File(path).canonicalPath
            val locks = linkedMapOf<String, Protection>()
            // Check every raw AND resolved ancestor. A symlink or nested lock
            // must not hide another independent protection boundary.
            listOf(File(path).absoluteFile.parentFile, File(canonical).parentFile).forEach { parent ->
                var folder = parent
                while (folder != null) {
                    val currentFolder = folder
                    cachedProtection(currentFolder.path)?.let { locks[currentFolder.path] = it }
                    folder = currentFolder.parentFile
                }
            }
            val canonicalFile = File(canonical)
            val stat = runCatching { android.system.Os.stat(canonical) }.getOrNull()
            val exists = canonicalFile.exists()
            entries[path] = Entry(
                canonical,
                stat?.let { "${it.st_dev}:${it.st_ino}" },
                canonicalFile.takeIf { exists }?.length(),
                canonicalFile.takeIf { exists }?.lastModified(),
                locks
            )
        }
        return Snapshot(entries)
    }

    /** Commons 7.1.3: only nonempty pattern/PIN hashes are comparable.
     * Biometric authentication has no equivalent per-folder secret. */
    fun challenges(snapshot: Snapshot): List<Set<String>> {
        val groups = linkedMapOf<Any, MutableSet<String>>()
        snapshot.entries.values.flatMap { it.locks.entries }.forEach { (root, protection) ->
            val key: Any = if (protection.type in 0..1 && protection.hash.isNotEmpty()) protection else root
            groups.getOrPut(key) { linkedSetOf() }.add(root)
        }
        return groups.values.map { it.toSet() }
    }

    fun createSession(
        ownerId: String,
        snapshot: Snapshot,
        authorizedRoots: Set<String>,
        protection: (String) -> Protection?
    ): String? {
        if (!authorizedRoots.containsAll(snapshot.entries.values.flatMap { it.locks.keys })) return null
        if (runCatching { captureSnapshot(snapshot.entries.keys, protection) }.getOrNull() != snapshot) return null
        val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        sessions[token] = Session(ownerId, snapshot, protection)
        return token
    }

    /** A single favorites credential authorizes every captured source path. */
    fun createFavoritesSession(
        ownerId: String,
        snapshot: Snapshot,
        authenticationSucceeded: Boolean,
        credentialGeneration: Long,
        credentialIsCurrent: () -> Boolean,
        protection: (String) -> Protection?
    ): String? {
        if (!authenticationSucceeded || generation.get() != credentialGeneration || !credentialIsCurrent()) return null
        if (runCatching { captureSnapshot(snapshot.entries.keys, protection) }.getOrNull() != snapshot) return null
        if (generation.get() != credentialGeneration || !credentialIsCurrent()) return null
        val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        sessions[token] = Session(ownerId, snapshot, protection) {
            generation.get() == credentialGeneration && credentialIsCurrent()
        }
        return token
    }

    fun currentGeneration(): Long = generation.get()

    fun isOwnedBy(token: String?, ownerId: String): Boolean = token?.let(sessions::get)?.owner == ownerId

    /** Resolve a live token after configuration recreation without saving it in Bundle state. */
    fun tokenForOwner(ownerId: String): String? =
        sessions.entries.firstOrNull { it.value.owner == ownerId }?.key

    fun isMediaAuthorized(token: String?, path: String): Boolean {
        val session = token?.let(sessions::get) ?: return false
        if (!session.credentialIsCurrent()) {
            revokeSession(token)
            return false
        }
        val expected = session.snapshot.entries[path] ?: return false
        val actual = runCatching { captureSnapshot(listOf(path), session.protection).entries[path] }.getOrNull()
        if (expected != actual) {
            revokeSession(token)
            return false
        }
        return sessions[token]?.snapshot?.entries?.get(path) == expected
    }

    /**
     * Re-checks the complete captured snapshot once, then returns the requested
     * members. Call this from a worker thread. Keeping the full snapshot check
     * preserves the original rule that any identity or lock-boundary change
     * invalidates the whole favorites capability.
     */
    fun validatePaths(token: String?, requestedPaths: Collection<String>): ValidatedPaths? {
        val session = token?.let(sessions::get) ?: return null
        if (!session.credentialIsCurrent()) {
            revokeSession(token)
            return null
        }
        // Requested entries are captured first so callers can put the clicked
        // media ahead of the remaining batch. The full captured set is still
        // compared before any result is accepted.
        val orderedPaths = LinkedHashSet<String>().apply {
            addAll(requestedPaths.filter { it in session.snapshot.entries })
            addAll(session.snapshot.entries.keys)
        }
        val fresh = try {
            captureSnapshot(orderedPaths, session.protection)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } catch (_: Exception) {
            null
        }
        if (Thread.currentThread().isInterrupted) return null
        if (fresh != session.snapshot) {
            revokeSession(token)
            return null
        }
        if (sessions[token] !== session || !session.credentialIsCurrent()) return null
        return ValidatedPaths(
            token = token,
            paths = session.snapshot.entries.keys.intersect(requestedPaths.toSet()),
            session = session
        )
    }

    /** Cheap membership check for an item whose batch was already validated. */
    fun isInValidatedPaths(validation: ValidatedPaths?, path: String): Boolean {
        val token = validation?.token ?: return false
        val expected = validation.session as? Session ?: return false
        return sessions[token] === expected && expected.credentialIsCurrent() && path in validation.paths
    }

    fun isValidationCurrent(validation: ValidatedPaths?): Boolean {
        val token = validation?.token ?: return false
        val expected = validation.session as? Session ?: return false
        return sessions[token] === expected && expected.credentialIsCurrent()
    }

    /** Cheap capability membership for adapters after a validated batch was published. */
    fun isCapturedPath(token: String?, path: String): Boolean {
        val session = token?.let(sessions::get) ?: return false
        if (!session.credentialIsCurrent()) {
            revokeSession(token)
            return false
        }
        return sessions[token] === session && path in session.snapshot.entries
    }

    fun isActive(token: String?): Boolean = token != null && sessions.containsKey(token)

    /** Snapshot membership only; callers still need a worker-thread batch validation before publication. */
    fun getCapturedPaths(token: String?): Set<String>? {
        val session = token?.let(sessions::get) ?: return null
        if (!session.credentialIsCurrent()) {
            revokeSession(token)
            return null
        }
        return if (sessions[token] === session) session.snapshot.entries.keys.toSet() else null
    }

    fun getAllowedPaths(token: String?): Set<String>? {
        val session = token?.let(sessions::get) ?: return null
        if (!session.credentialIsCurrent()) {
            revokeSession(token)
            return null
        }
        if (runCatching { captureSnapshot(session.snapshot.entries.keys, session.protection) }.getOrNull() != session.snapshot) {
            revokeSession(token)
            return null
        }
        return if (sessions[token] === session) session.snapshot.entries.keys.toSet() else getAllowedPaths(token)
    }

    /** Called with a fresh database result on the background loading path. */
    fun retainFavorites(token: String?, current: Set<String>): Set<String>? {
        val validation = validatePaths(token, current) ?: return null
        (sessionPaths(validation) - current).forEach(::revokePath)
        val session = sessions[token] ?: return null
        return if (session.credentialIsCurrent()) session.snapshot.entries.keys.toSet() else null
    }

    private fun sessionPaths(validation: ValidatedPaths): Set<String> =
        (validation.session as? Session)?.snapshot?.entries?.keys.orEmpty()

    fun revokePath(path: String) {
        sessions.keys.toList().forEach { token ->
            var changed = false
            while (true) {
                val session = sessions[token] ?: break
                if (path !in session.snapshot.entries) break
                val reduced = session.copy(snapshot = Snapshot(session.snapshot.entries - path))
                if (sessions.replace(token, session, reduced)) { changed = true; break }
            }
            if (changed) listeners.values.toList().forEach { it(token) }
        }
    }

    fun addRevocationListener(id: String, listener: (String) -> Unit) { listeners[id] = listener }
    fun removeRevocationListener(id: String) { listeners.remove(id) }

    fun revokeSession(token: String?) {
        if (token != null && sessions.remove(token) != null) listeners.values.toList().forEach { it(token) }
    }

    fun revokeAll() {
        generation.incrementAndGet()
        sessions.keys.toList().forEach(::revokeSession)
    }
}
