package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.extensions.getFavoritePaths

/** In-memory photo capability. Persisted source bindings are never sufficient without fresh authentication. */
internal object PhotoProfileAccessSessionManager {
    const val FAVORITE_CREDENTIAL_BINDING = "virtual:favorites:credential"
    const val EXTRA_SESSION = "org.fossify.gallery.PHOTO_ACCESS_SESSION"
    private val capability = PhotoAccessCapability()
    @Volatile private var token: String? = null
    private val listeners = PhotoAccessEventListeners()
    private val eventVersion = java.util.concurrent.atomic.AtomicLong()
    fun currentGeneration() = capability.generation()
    fun currentEventVersion() = eventVersion.get()
    fun currentToken(context: Context): String? = token?.takeIf { isActive(context, it) }
    fun isActive(context: Context, candidate: String?): Boolean = capability.active(candidate)
    fun create(context: Context, proof: Config.PhotoCredentialProof): String? {
        if (!PhotoAuthentication.requests.activeId(proof.requestId)) return null
        return capability.create(proof.sessionGeneration) {
            context.config.isPhotoCredentialCurrent(proof.credential)
        }?.also { created ->
            val changed = token != created
            token = created
            if (changed) publish(PhotoAccessEvent.GRANTED)
        }
    }
    fun addListener(id: String, listener: (PhotoAccessEvent) -> Unit) { listeners.add(id, listener) }
    fun removeListener(id: String) { listeners.remove(id) }
    fun revokeAll(reason: String = "unspecified") {
        android.util.Log.d("PhotoProfileAuth", "revoke=$reason")
        capability.revoke()
        token = null
        publish(PhotoAccessEvent.REVOKED)
    }
    private fun publish(event: PhotoAccessEvent) {
        eventVersion.incrementAndGet()
        listeners.publish(event)
    }
    fun bindingsAuthorize(locks: Map<String, FavoriteAccessSessionManager.Protection>, bindings: Map<String, String>, active: Boolean): Boolean =
        locks.isEmpty() || (active && locks.all { (root, lock) -> bindings[root] == fingerprint(lock.type, lock.hash) })
    fun fingerprint(type: Int, hash: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("$type:$hash".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun favoriteIdentity(context: Context): String = fingerprint(-1,
        com.google.gson.Gson().toJson(context.config.favoriteCredentialSnapshot()))
    fun rootBound(context: Context, root: String): Boolean {
        val protection = context.favoriteProtection(root) ?: return true
        return context.config.photoSourceBindings[root] == fingerprint(protection.type, protection.hash)
    }
    /** Worker-thread check of every raw and canonical ancestor, including nested locks. */
    fun folderAuthorized(context: Context, folder: String, candidate: String?): Boolean {
        val roots = locksForPaths(context, listOf("${folder.trimEnd('/')}/__photo_scope__"))
        return bindingsAuthorize(roots, context.config.photoSourceBindings, isActive(context, candidate))
    }
    fun favoritesAuthorized(context: Context, candidate: String?): Boolean {
        val config = context.config
        val dedicated = !config.isFavoritePasswordProtectionOn ||
            (isActive(context, candidate) && config.photoSourceBindings[FAVORITE_CREDENTIAL_BINDING] == favoriteIdentity(context))
        return dedicated && (!config.isFolderProtected(FAVORITES) ||
            (isActive(context, candidate) && rootBound(context, FAVORITES)))
    }
    fun locksForPaths(context: Context, paths: Collection<String>): Map<String, FavoriteAccessSessionManager.Protection> =
        PhotoPathAccess.capture(paths, context::favoriteProtection).values
            .flatMap { it.locks.entries }.associate { it.key to it.value }
    fun profileLocks(context: Context, profile: PhotoProfile): Map<String, FavoriteAccessSessionManager.Protection> {
        val paths = profile.sources.filter { it.type == PhotoSourceType.FOLDER }
            .mapNotNull { it.path?.let { folder -> "${folder.trimEnd('/')}/__photo_scope__" } }.toMutableList()
        if (profile.sources.any { it.type == PhotoSourceType.FAVORITES }) paths.addAll(context.getFavoritePaths())
        return locksForPaths(context, paths).toMutableMap().apply {
            if (profile.sources.any { it.type == PhotoSourceType.FAVORITES }) context.favoriteProtection(FAVORITES)?.let { put(FAVORITES, it) }
        }
    }
}

internal enum class PhotoAccessEvent { GRANTED, REVOKED }

internal fun PhotoAccessEvent.mustClearProtectedMedia(): Boolean = this == PhotoAccessEvent.REVOKED

/** Snapshot dispatch avoids holding a registry lock while invoking Activity listeners. */
internal class PhotoAccessEventListeners {
    private val listeners = java.util.concurrent.ConcurrentHashMap<String, (PhotoAccessEvent) -> Unit>()
    fun add(id: String, listener: (PhotoAccessEvent) -> Unit) { listeners[id] = listener }
    fun remove(id: String) { listeners.remove(id) }
    fun publish(event: PhotoAccessEvent) { listeners.values.toList().forEach { it(event) } }
}
