package org.fossify.gallery.helpers

import android.os.SystemClock

/**
 * UI-thread-owned, bounded authorization evidence for a Photos viewer.
 * Entries are not credentials and cannot outlive their revision or short TTL.
 */
internal class PhotoViewerPageAccessState(
    private val capacity: Int = 16,
    private val maxAgeMs: Long = 15_000L,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    init {
        require(capacity >= ViewerNeighborWindow.MAX_ITEMS)
        require(maxAgeMs > 0L)
    }

    private val verified = LinkedHashMap<String, Long>(capacity, 0.75f, true)
    private var revision = -1L

    fun reset(newRevision: Long) {
        verified.clear()
        revision = newRevision
    }

    fun approve(paths: Collection<String>, forRevision: Long): Boolean {
        if (forRevision != revision) return false
        val timestamp = now()
        paths.forEach { path ->
            verified[path] = timestamp
            if (verified.size > capacity) verified.remove(verified.keys.first())
        }
        return true
    }

    fun contains(path: String, forRevision: Long): Boolean {
        if (forRevision != revision) return false
        val validatedAt = verified[path] ?: return false
        if (now() - validatedAt !in 0..maxAgeMs) {
            verified.remove(path)
            return false
        }
        return true
    }

    fun missing(paths: Collection<String>, forRevision: Long): List<String> =
        paths.filterNot { contains(it, forRevision) }

    fun remove(path: String) { verified.remove(path) }

    fun size(): Int = verified.size
}
