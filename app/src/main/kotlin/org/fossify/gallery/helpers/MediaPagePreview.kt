package org.fossify.gallery.helpers

import android.content.Context
import java.io.File
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isThisOrParentExcluded
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.models.Medium
import org.fossify.commons.helpers.SORT_BY_RANDOM
import kotlin.random.Random

/** First publication is sized for the grid. Later snapshots grow geometrically and are throttled. */
internal class MediaPreviewCadence(private val viewportItems: Int, startedAt: Long) {
    private var lastSize = 0
    private var lastAt = startedAt

    fun shouldPublish(size: Int, now: Long): Boolean {
        if (size <= lastSize) return false
        val ready = when {
            lastSize == 0 -> true
            lastSize < viewportItems -> size >= viewportItems || now - lastAt >= 150L
            else -> now - lastAt >= 300L && size - lastSize >= lastSize
        }
        if (ready) {
            lastSize = size
            lastAt = now
        }
        return ready
    }
}

/** Main-thread, request-local accumulation. Newer sources can correct cached sort keys. */
internal class MediaPagePreview {
    private val byPath = LinkedHashMap<String, Medium>()
    private var stage: DisplayedSourceStage? = null

    fun add(next: DisplayedSourceStage, rows: List<Medium>): ArrayList<Medium>? {
        if (next.ordinal < (stage?.ordinal ?: 0)) return null
        stage = next
        var changed = false
        rows.forEach { row ->
            val previous = byPath.put(row.path, row)
            if (previous == null || !MediaUpdatePolicy.sameContent(previous, row)) changed = true
        }
        if (!changed) return null
        return ArrayList(byPath.values)
    }
}

/** Result of one bounded first-slice selection, including the work it actually performed. */
internal data class CachedPreviewSlice(
    val items: List<Medium>,
    /** Existence/visibility checks performed, i.e. the real filesystem cost of this slice. */
    val checks: Int,
    /** False when the check budget ended the walk before the target count was reached. */
    val complete: Boolean
)

/** Cached-preview ordering: full sort for untrusted order, bounded window for a scope-owned catalog. */
internal object CachedMediaPreviewOrder {
    private const val RANDOM_PREVIEW_SEED = 0x4D45444941434143L
    private const val ANCHOR_CONTEXT_DIVISOR = 4
    const val LIMIT = 160

    /**
     * How many candidates a first slice may inspect per requested row. A stale
     * cache therefore degrades to a bounded scan instead of an unbounded one over
     * the whole catalog, while still tolerating the usual handful of deleted files.
     */
    const val CHECK_BUDGET_MULTIPLIER = 4

    /**
     * Selects the first visible window without walking or re-sorting the whole
     * catalog. A readable [PhotoProfileMediaCache] entry belongs to an exact
     * [PhotoProfileScope], sorting included, so its stored order is already the
     * order this request would produce unless sorting is random. Null means the
     * caller must run the full [order] + missing-filter path.
     */
    fun select(
        cached: List<Medium>,
        sorting: Int,
        anchorPath: String?,
        limit: Int = LIMIT,
        visible: (Medium) -> Boolean = { CachedMediaPreviewFilter.isVisible(it) }
    ): List<Medium>? = selectBounded(
        cached, sorting, anchorPath, limit, Int.MAX_VALUE, visible
    )?.items

    /**
     * Same contract as [select], but the walk stops after [maxChecks] candidates.
     * A partial slice is still a valid preview: the first frame only needs rows the
     * grid can show, and the indexed/filesystem stages will converge on the rest.
     */
    fun selectBounded(
        cached: List<Medium>,
        sorting: Int,
        anchorPath: String?,
        limit: Int = LIMIT,
        maxChecks: Int = limit * CHECK_BUDGET_MULTIPLIER,
        visible: (Medium) -> Boolean = { CachedMediaPreviewFilter.isVisible(it) }
    ): CachedPreviewSlice? {
        if (cached.isEmpty() || limit <= 0 || maxChecks <= 0 || sorting and SORT_BY_RANDOM != 0) return null
        var checks = 0
        // The stored order already matches scope.sorting; only the viewport moves.
        val anchor = anchorPath?.let { path -> cached.indexOfFirst { it.path == path } }?.takeIf { it >= 0 } ?: 0
        val start = (anchor - limit / ANCHOR_CONTEXT_DIVISOR).coerceAtLeast(0)
        val selected = ArrayList<Medium>(limit)
        var index = start
        while (index < cached.size && selected.size < limit && checks < maxChecks) {
            val item = cached[index++]
            checks++
            if (visible(item)) selected.add(item)
        }
        if (selected.size < limit && checks < maxChecks) {
            // The viewport opened near the end of the cached order; fill backwards
            // while keeping the final list in its original (ascending) order.
            val earlier = ArrayList<Medium>(limit - selected.size)
            index = start - 1
            while (index >= 0 && earlier.size < limit - selected.size && checks < maxChecks) {
                val item = cached[index--]
                checks++
                if (visible(item)) earlier.add(item)
            }
            val ordered = ArrayList<Medium>(earlier.size + selected.size)
            ordered.addAll(earlier.asReversed())
            ordered.addAll(selected)
            return CachedPreviewSlice(ordered, checks, ordered.size >= limit)
        }
        return CachedPreviewSlice(selected, checks, selected.size >= limit)
    }

    fun order(rows: List<Medium>, sorting: Int, fetcher: MediaFetcher): ArrayList<Medium> {
        if (rows.isEmpty()) return arrayListOf()
        val ordered = rows.mapTo(ArrayList(rows.size)) { row ->
            row.copy(taken = if (row.taken > 0L) row.taken else row.modified)
        }
        if (sorting and SORT_BY_RANDOM != 0) {
            // Cache selection must not depend on Room's row order. The request's
            // StagedMediaOrder applies its own rank after these candidates arrive.
            ordered.sortWith(compareBy<Medium> {
                Random(RANDOM_PREVIEW_SEED xor it.path.hashCode().toLong()).nextLong()
            }.thenBy { it.path })
        } else {
            fetcher.sortMedia(ordered, sorting)
        }
        return ordered
    }
}

/** A seeded path rank keeps the relative order of known rows as discovery continues. */
internal class StagedMediaOrder(private val seed: Long = Random.nextLong()) {
    fun sort(media: ArrayList<Medium>, sorting: Int, fetcher: MediaFetcher) {
        if (sorting and SORT_BY_RANDOM != 0) {
            val ranks = media.associate { it.path to Random(seed xor it.path.hashCode().toLong()).nextLong() }
            media.sortWith(compareBy<Medium> { ranks.getValue(it.path) }.thenBy { it.path })
        } else {
            fetcher.sortMedia(media, sorting)
        }
    }
}

/** One sorted, physically verified viewport after the complete index becomes available. */
internal object IndexedMediaViewport {
    private const val REFINEMENT_BUDGET_MS = 150L

    fun select(
        rows: List<Medium>, sorting: Int, fetcher: MediaFetcher, visibility: MediaPageVisibility,
        target: Int, current: () -> Boolean,
    ): List<Medium> {
        if (sorting and SORT_BY_RANDOM != 0) return emptyList()
        val ordered = CachedMediaPreviewOrder.order(rows, sorting, fetcher).iterator()
        val result = ArrayList<Medium>()
        val deadline = android.os.SystemClock.elapsedRealtime() + REFINEMENT_BUDGET_MS
        fun available() = current() && android.os.SystemClock.elapsedRealtime() < deadline
        while (ordered.hasNext() && result.size < target.coerceAtLeast(1) && available()) {
            visibility.checkedIndexedFile(ordered.next())?.let(result::add)
        }
        return result
    }
}

/** Indexed and cached rows must obey the same folder visibility rules as the grid. */
internal class MediaPageVisibility(
    private val context: Context,
    private val showAll: Boolean,
    private val authorized: (String) -> Boolean
) {
    private val hidden = context.config.shouldShowHidden
    private val included = context.config.includedFolders
    private val excluded = context.config.let { if (it.temporarilyShowExcluded) hashSetOf() else it.excludedFolders }
    private val unrestrictedFolders = HashMap<String, Boolean>()
    private val visibleFolders = HashMap<String, Boolean>()
    private val markers = HashMap<String, Boolean>()

    fun allows(path: String): Boolean {
        val folder = File(path).parent ?: return false
        if (!hidden && path.contains("/.")) return false
        return visibleFolders.getOrPut(folder) {
            authorized(path) && if (showAll) {
                !folder.equals("/storage/emulated/legacy", ignoreCase = true) &&
                    folder.shouldFolderBeVisible(excluded, included, hidden, markers) { key, present ->
                        markers[key] = present
                    }
            } else {
                folder.isThisOrParentIncluded(included) || !folder.isThisOrParentExcluded(excluded)
            }
        }
    }

    fun allowsIndexedFile(path: String): Boolean = indexedAttributes(path) != null

    /** Reuse the physical check already required for a preview, including precise mtime. */
    fun checkedIndexedFile(item: Medium): Medium? = indexedAttributes(item.path)?.let { attributes ->
        val modified = attributes.lastModifiedTime().toMillis()
        item.copy(modified = modified, size = attributes.size(),
            taken = if (item.takenIsFallback) modified else item.taken)
    }

    private fun indexedAttributes(path: String): java.nio.file.attribute.BasicFileAttributes? {
        val file = File(path)
        val folder = file.parent ?: return null
        if (!allows(path) || !unrestrictedFolders.getOrPut(folder) {
            !context.isRestrictedWithSAFSdk30(folder)
        }) return null
        return runCatching {
            val attributes = java.nio.file.Files.readAttributes(
                file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java,
                java.nio.file.LinkOption.NOFOLLOW_LINKS
            )
            attributes.takeIf { it.isRegularFile && it.size() > 0 && file.canRead() }
        }.getOrNull()
    }
}

internal data class MediaPageBatch(
    val stage: DisplayedSourceStage,
    val rows: List<Medium>,
    val accessCurrent: () -> Boolean
)
