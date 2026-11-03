package org.fossify.gallery.helpers

import android.os.SystemClock
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.UUID

/** One-use ordering evidence, never proof of permission to load a photo. */
internal object ViewerGridOrderStore {
    const val EXTRA_ID = "org.fossify.gallery.VIEWER_GRID_ORDER"
    private const val MAX_AGE_MS = 30_000L
    private const val MAX_ITEMS = 50_000

    private data class Entry(
        val id: String, val source: String, val selected: String,
        val createdAt: Long, val paths: List<String>
    )
    private var pending: Entry? = null

    @Synchronized fun offer(source: String, selected: String, items: List<ThumbnailItem>): String? {
        if (source.isBlank() || selected.isBlank()) return null
        val paths = items.asSequence().filterIsInstance<Medium>()
            .map { it.path }.distinct().take(MAX_ITEMS + 1).toList()
        if (paths.isEmpty() || paths.size > MAX_ITEMS || selected !in paths) return null
        val id = UUID.randomUUID().toString()
        pending = Entry(id, source, selected, SystemClock.elapsedRealtime(), paths)
        return id
    }

    @Synchronized fun take(id: String?, source: String, selected: String): List<String>? {
        val entry = pending ?: return null
        if (id == null || entry.id != id) return null
        pending = null
        if (entry.source != source || entry.selected != selected ||
            SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS) return null
        return entry.paths
    }
}

/** Reorder freshly validated media only; removed or unauthorized files never reappear. */
internal object ViewerGridOrderPolicy {
    fun <T> reconcile(
        gridOrder: List<String>?, previous: List<T>, fresh: List<T>,
        random: Boolean, path: (T) -> String
    ): List<T> {
        if (gridOrder == null) return FolderViewerMediaPolicy.reconcile(previous, fresh, random, path)
        val remaining = LinkedHashMap<String, T>()
        fresh.forEach { remaining[path(it)] = it }
        val ordered = ArrayList<T>(remaining.size)
        gridOrder.forEach { key -> remaining.remove(key)?.let(ordered::add) }
        // Newly discovered files have no grid ordinal; leave them in fresh scan order.
        ordered.addAll(remaining.values)
        return ordered
    }
}
