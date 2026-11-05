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
        val createdAt: Long, val evidence: Evidence
    )
    data class Evidence(
        val paths: List<String>, val candidates: List<Medium>,
        val version: Long, val contract: String?, val revision: Long
    )
    private var pending: Entry? = null

    /** This identifies display scope, not access. Sessions must still be validated at publication. */
    fun contract(sorting: Int, filter: Int, hidden: Boolean, query: String, session: String?, revision: Long) =
        listOf(sorting, filter, hidden, query, session, revision).joinToString("\u0000")

    @Synchronized fun offer(source: String, selected: String, items: List<ThumbnailItem>,
        version: Long = 0L, contract: String? = null, includeCandidates: Boolean = false,
        revision: Long = FolderContentRevision.current()): String? {
        if (source.isBlank() || selected.isBlank()) return null
        // A concurrent adapter commit may replace its backing list while the
        // handoff worker reads it; missing a snapshot must never prevent opening.
        val paths = runCatching {
            items.asSequence().filterIsInstance<Medium>()
                .map { it.path }.distinct().take(MAX_ITEMS + 1).toList()
        }.getOrNull() ?: return null
        if (paths.isEmpty() || paths.size > MAX_ITEMS || selected !in paths) return null
        val id = UUID.randomUUID().toString()
        val candidates = if (includeCandidates) items.filterIsInstance<Medium>().map { it.copy() } else emptyList()
        pending = Entry(id, source, selected, SystemClock.elapsedRealtime(), Evidence(paths, candidates, version, contract, revision))
        if (GalleryDiagnosticLogger.isEnabled()) GalleryDiagnosticLogger.event(
            DiagnosticLevel.INFO, "Viewer", "viewer_grid_handoff_capture", id,
            mapOf("scopeKey" to GalleryDiagnosticLogger.anonymousId(source), "gridVersion" to version,
                "revision" to revision, "itemCount" to paths.size,
                "mediaId" to GalleryDiagnosticLogger.anonymousId(selected), "containsSelected" to true))
        return id
    }

    fun take(id: String?, source: String, selected: String): List<String>? = takeEvidence(id, source, selected)?.paths

    @Synchronized fun takeEvidence(id: String?, source: String, selected: String,
        contract: String? = null): Evidence? {
        val entry = pending ?: return null
        if (id == null || entry.id != id) return null
        pending = null
        if (entry.source != source || entry.selected != selected ||
            (contract != null && entry.evidence.contract != contract) ||
            SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS) return null
        return entry.evidence
    }
}

/** Reorder freshly validated media only; removed or unauthorized files never reappear. */
internal object ViewerGridOrderPolicy {
    /**
     * A partial scan is not proof that omitted paths were deleted. Publish only
     * the contiguous known run around the live anchor; the omitted path remains
     * a navigation boundary until a complete source snapshot can classify it.
     */
    fun <T> contiguousPartial(
        gridOrder: List<String>?, fresh: List<T>, anchor: String, path: (T) -> String
    ): List<T> {
        // Without the committed grid order, or without evidence for the live
        // page, a partial source cannot prove adjacency. An empty result tells
        // callers to keep the already validated pager window until COMPLETE.
        if (gridOrder == null || fresh.isEmpty()) return emptyList()
        val byPath = LinkedHashMap<String, T>(fresh.size)
        fresh.forEach { byPath.putIfAbsent(path(it), it) }
        val anchorIndex = gridOrder.indexOf(anchor)
        if (anchorIndex < 0 || anchor !in byPath) return emptyList()
        var first = anchorIndex
        var last = anchorIndex
        while (first > 0 && gridOrder[first - 1] in byPath) first--
        while (last < gridOrder.lastIndex && gridOrder[last + 1] in byPath) last++
        return gridOrder.subList(first, last + 1).mapNotNull(byPath::get)
    }

    /** Only called after a successful, authorized file rename. Still grants no access. */
    fun renamed(order: List<String>?, oldPath: String, newPath: String): List<String>? =
        order?.map { if (it == oldPath) newPath else it }?.distinct()

    /** Segment expansion directions belong to its authoritative ordinal order.
     * A conflicting old grid cannot reorder just the resident window safely. */
    fun <T> reconcilePhotoCatalog(
        gridOrder: List<String>?, previous: List<T>, fresh: List<T>,
        segmented: Boolean, complete: Boolean = false, random: Boolean = false, path: (T) -> String
    ): Pair<List<T>, Boolean> {
        if (complete && !random) return fresh to false
        val ordered = reconcile(gridOrder, previous, fresh, random, path)
        if (segmented && gridOrder != null && fresh.indices.any { path(fresh[it]) != path(ordered[it]) }) {
            return fresh to false
        }
        return ordered to (gridOrder != null)
    }

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
