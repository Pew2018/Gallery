package org.fossify.gallery.helpers

import android.os.SystemClock
import org.fossify.gallery.models.Medium
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Short-lived, process-only metadata handoff. The ID is never an authorization capability. */
internal object ViewerNeighborHandoffStore {
    const val EXTRA_ID = "org.fossify.gallery.VIEWER_NEIGHBOR_HANDOFF"
    private const val TTL_MS = 15_000L

    private data class Entry(val createdAt: Long, val window: ViewerNeighborWindow)
    private val entries = ConcurrentHashMap<String, Entry>()

    fun offer(window: ViewerNeighborWindow): String? {
        if (window.items.isEmpty() || window.items.size > ViewerNeighborWindow.MAX_ITEMS ||
            window.items.none { it.path == window.selectedPath }) return null
        purgeExpired()
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(SystemClock.elapsedRealtime(), window.copy(items = window.items.map { it.copy() }))
        return id
    }

    fun take(id: String?, selectedPath: String, sourceType: ViewerSourceType, sourceKey: String): ViewerNeighborWindow? {
        if (id == null) return null
        val entry = entries.remove(id) ?: return null
        if (SystemClock.elapsedRealtime() - entry.createdAt > TTL_MS) return null
        val window = entry.window
        if (window.selectedPath != selectedPath || window.sourceType != sourceType || window.sourceKey != sourceKey ||
            window.items.size > ViewerNeighborWindow.MAX_ITEMS || window.items.none { it.path == selectedPath }) return null
        return window.copy(items = window.items.map { it.copy() })
    }

    fun clear(id: String?) {
        if (id != null) entries.remove(id)
    }

    private fun purgeExpired() {
        val now = SystemClock.elapsedRealtime()
        entries.entries.toList().forEach { (id, entry) ->
            if (now - entry.createdAt > TTL_MS) entries.remove(id, entry)
        }
    }
}
