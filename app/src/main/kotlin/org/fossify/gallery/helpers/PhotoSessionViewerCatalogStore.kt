package org.fossify.gallery.helpers

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.models.Medium
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal process-only Photos metadata. A handoff ID is NEVER permission.
 * Complete catalogs and indexed provisional catalogs have separate completion
 * flags; every provisional expansion is checked before it becomes visible.
 */
internal object PhotoSessionViewerCatalogStore {
    const val EXTRA_ID = "org.fossify.gallery.PHOTO_SESSION_VIEWER_CATALOG"
    private const val MAX_ITEMS = 20_000
    private const val MAX_AGE_MS = 30 * 60_000L
    private const val EXPAND_BATCH = 120

    data class Catalog(
        val media: List<Medium>,
        val pathIndex: Map<String, Int>,
        val revision: Long,
        val isComplete: Boolean = true,
        // Bounded to the visible window; independent of the segment LRU.
        val ordinals: Map<String, Int> = emptyMap(),
        val before: PhotoViewerCatalogCoordinator.Boundary = if (isComplete) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.UNKNOWN,
        val after: PhotoViewerCatalogCoordinator.Boundary = if (isComplete) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.UNKNOWN
    )

    private data class Entry(
        val id: String,
        val request: PhotoProfileLoadContext,
        val createdAt: Long,
        val catalog: Catalog,
        val stage: PhotoLoadStage = PhotoLoadStage.COMPLETE
    )
    private data class IndexedCandidates(
        val request: PhotoProfileLoadContext,
        val order: List<Medium>,
        val segments: SegmentedPhotoCatalog? = null,
        val complete: Boolean = false
    )

    private data class Focus(val profile: String?, val session: String?, val path: String)
    @Volatile private var focus: Focus? = null
    fun focus(profile: String?, session: String?, path: String) { focus = Focus(profile, session, path) }
    fun focusPath(request: PhotoProfileLoadContext): String? = focus?.takeIf {
        it.profile == request.scope.profileId && it.session == request.scope.sessionId
    }?.path
    @Volatile private var prepared: Entry? = null
    @Volatile private var partial: Entry? = null
    @Volatile private var loading: PhotoProfileLoadContext? = null
    @Volatile private var indexed: IndexedCandidates? = null
    private val listeners = ConcurrentHashMap<String, () -> Unit>()
    private var memoryCallbackRegistered = false

    fun addListener(id: String, callback: () -> Unit) { listeners[id] = callback }
    fun removeListener(id: String) { listeners.remove(id) }
    private fun notifyChanged() { listeners.values.toList().forEach { runCatching { it() } } }

    @Synchronized
    private fun registerMemoryCallback(context: Context) {
        if (memoryCallbackRegistered) return
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = invalidate()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) invalidate()
            }
        })
        memoryCallbackRegistered = true
    }

    private fun catalog(items: List<Medium>, revision: Long, isComplete: Boolean): Catalog? {
        if (items.isEmpty() || items.size > MAX_ITEMS) return null
        val media = Collections.unmodifiableList(items.map { it.copy() })
        val index = HashMap<String, Int>(media.size * 4 / 3 + 1)
        media.forEachIndexed { pos, item -> if (index.put(item.path, pos) != null) return null }
        return Catalog(media, Collections.unmodifiableMap(index), revision, isComplete)
    }

    /** Worker only; a producer is shared with the viewer while the grid is stopped. */
    @Synchronized fun beginLoading(request: PhotoProfileLoadContext, context: Context? = null, reuseActive: Boolean = false): Boolean {
        val active = loading
        if (reuseActive && context != null && active != null && active.scope == request.scope &&
            active.revision == request.revision && active.isCurrent(context)) return false
        loading = request
        prepared = null
        partial = null
        indexed?.segments?.close()
        indexed = null
        notifyChanged()
        return true
    }

    @Synchronized fun finishLoading(request: PhotoProfileLoadContext) {
        if (loading !== request) return
        loading = null
        if (indexed?.complete != true) { indexed?.segments?.close(); indexed = null }
        notifyChanged()
    }

    fun isLoading(context: Context, profileId: String?, sessionId: String?): Boolean {
        val request = loading ?: return false
        return request.scope.profileId == profileId && request.scope.sessionId == sessionId &&
            request.isCurrent(context)
    }

    /** Indexed candidates remain private until a bounded access batch approves them. */
    fun offerIndexedCandidates(context: Context, request: PhotoProfileLoadContext, items: List<Medium>) {
        if (loading !== request || !request.isCurrent(context)) return
        val segments = if (items.size > MAX_ITEMS) SegmentedPhotoCatalog.write(context.cacheDir, items) else null
        synchronized(this) {
            if (loading !== request || !request.isCurrent(context)) { segments?.close(); return }
            indexed?.segments?.close()
            indexed = IndexedCandidates(request, if (segments == null) items else emptyList(), segments)
        }
    }

    /** Worker-only stage publication. Rows have already passed PhotoAccessValidationBatch.
     * A blocked source requires unlocking but does not invalidate other authorized media.
     */
    fun offerPartial(context: Context, result: PhotoLoadResult) {
        if (result.stage == PhotoLoadStage.COMPLETE ||
            (loading != null && loading !== result.context) || !result.context.isCurrent(context)) return
        synchronized(this) {
            if ((loading != null && loading !== result.context) || !result.context.isCurrent(context)) return
            if (prepared?.request === result.context ||
                (indexed?.complete == true && indexed?.request === result.context)) return
            val old = partial?.takeIf { it.request.scope == result.context.scope &&
                it.request.revision == result.context.revision }
            // Never let an older cache/index callback displace a newer stage.
            if (old != null && result.stage.ordinal < old.stage.ordinal) return
            val incomingPaths = result.media.mapTo(HashSet(result.media.size)) { it.path }
            val containsPrevious = old == null || old.catalog.media.all { it.path in incomingPaths }
            val combined = LinkedHashMap<String, Medium>()
            result.media.forEach { combined[it.path] = it }
            old?.catalog?.media?.forEach { combined.putIfAbsent(it.path, it) }
            val anchor = focusPath(result.context)
            // Stage-wise sorted lists cannot be concatenated without re-sorting.
            val all = ArrayList(combined.values)
            if (!containsPrevious && result.context.scope.sorting and org.fossify.commons.helpers.SORT_BY_RANDOM == 0) {
                MediaFetcher(context).sortMedia(all, result.context.scope.sorting)
            }
            val pos = all.indexOfFirst { it.path == anchor }.coerceAtLeast(0)
            val start = (pos - MAX_ITEMS / 2).coerceIn(0, (all.size - MAX_ITEMS).coerceAtLeast(0))
            val window = all.subList(start, (start + MAX_ITEMS).coerceAtMost(all.size))
            if (old != null && ViewerMediaUpdatePolicy.sameCatalog(old.catalog.media, window)) {
                partial = old.copy(stage = result.stage)
                return
            }
            val next = catalog(window, result.context.revision, false) ?: return
            partial = Entry(old?.id ?: UUID.randomUUID().toString(), result.context,
                SystemClock.elapsedRealtime(), next, result.stage)
        }
        registerMemoryCallback(context)
        notifyChanged()
    }

    /** Worker only. COMPLETE means all currently authorized sources were scanned.
     * The loader excluded locked sources before scanning; this is not a public cache grant.
     */
    fun prepare(context: Context, result: PhotoLoadResult): String? {
        if (result.stage != PhotoLoadStage.COMPLETE ||
            (loading != null && loading !== result.context) || !result.context.isCurrent(context)) return null
        val started = GalleryPerfTrace.begin()
        val segments = if (result.media.size > MAX_ITEMS) SegmentedPhotoCatalog.write(context.cacheDir, result.media) else null
        val anchor = focusPath(result.context)
        val pos = result.media.indexOfFirst { it.path == anchor }.coerceAtLeast(0)
        val start = if (segments == null) 0 else (pos - EXPAND_BATCH).coerceAtLeast(0)
        val end = if (segments == null) result.media.size else (pos + EXPAND_BATCH + 1).coerceAtMost(result.media.size)
        val initial = segments?.range(start, end - 1) ?: result.media.subList(start, end)
        val next = catalog(initial, result.context.revision, segments == null)?.copy(
            ordinals = if (segments == null) emptyMap() else initial.mapIndexed { offset, item -> item.path to start + offset }.toMap(),
            before = if (start == 0) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.HAS_MORE,
            after = if (end == result.media.size) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.HAS_MORE
        ) ?: return null
        if (!result.context.isCurrent(context)) { segments?.close(); return null }
        val candidate: Entry
        synchronized(this) {
            if ((loading != null && loading !== result.context) || !result.context.isCurrent(context)) {
                segments?.close()
                return null
            }
            val old = partial?.takeIf { it.request.scope == result.context.scope &&
                it.request.revision == result.context.revision }
            candidate = Entry(old?.id ?: UUID.randomUUID().toString(), result.context,
                SystemClock.elapsedRealtime(), next)
            indexed?.segments?.close()
            if (segments == null) {
                prepared = candidate
                partial = null
                indexed = null
            } else {
                prepared = null
                partial = candidate
                indexed = IndexedCandidates(result.context, emptyList(), segments, complete = true)
            }
        }
        registerMemoryCallback(context)
        GalleryPerfTrace.end("photo_catalog_ready", started, next.media.size)
        notifyChanged()
        return candidate.id
    }

    private fun valid(context: Context, entry: Entry, profileId: String?, sessionId: String?): Boolean {
        if (entry.request.scope.profileId != profileId || entry.request.scope.selectedId != profileId ||
            entry.request.scope.sessionId != sessionId ||
            SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS ||
            !entry.request.isCurrent(context)) return false
        if (entry.request.scope.sources.any { it.type == PhotoSourceType.FAVORITES } &&
            context.getFavoritePaths().toSet() != entry.request.favorites) return false
        return true
    }

    /** Worker only. Includes validated partial stage for taps before COMPLETE. */
    fun handoffId(context: Context, profileId: String?, sessionId: String?): String? {
        val entry = prepared ?: partial ?: return null
        if (!valid(context, entry, profileId, sessionId)) return null
        return entry.id
    }

    /** Worker only; metadata alone grants no access to the selected image. */
    fun take(context: Context, id: String?, profileId: String?,
        sessionId: String?, selectedPath: String): Catalog? {
        if (id == null) return null
        val entry = (prepared ?: partial) ?: return null
        if (id != entry.id || !valid(context, entry, profileId, sessionId)) return null
        return catalogAt(context, entry, selectedPath)
    }

    /** Worker-only query used when the grid publishes a later validated stage. */
    fun latest(context: Context, profileId: String?, sessionId: String?, selectedPath: String): Catalog? {
        val entry = prepared ?: partial ?: return null
        if (!valid(context, entry, profileId, sessionId)) return null
        return catalogAt(context, entry, selectedPath)
    }

    private fun catalogAt(context: Context, entry: Entry, path: String): Catalog? {
        if (entry.catalog.pathIndex.containsKey(path)) return entry.catalog
        val source = indexed ?: return null
        val segments = source.segments ?: return null
        if (!source.complete || source.request !== entry.request) return null
        val pos = segments.indexOf(path)
        if (pos < 0) return null
        val start = (pos - EXPAND_BATCH).coerceAtLeast(0)
        val end = (pos + EXPAND_BATCH).coerceAtMost(segments.size - 1)
        val items = segments.range(start, end)
        val next = catalog(items, entry.request.revision, false)?.copy(
            ordinals = items.mapIndexed { offset, item -> item.path to start + offset }.toMap(),
            before = if (start == 0) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.HAS_MORE,
            after = if (end == segments.size - 1) PhotoViewerCatalogCoordinator.Boundary.END else PhotoViewerCatalogCoordinator.Boundary.HAS_MORE
        ) ?: return null
        synchronized(this) {
            if (partial !== entry || indexed !== source || !entry.request.isCurrent(context)) return null
            partial = entry.copy(catalog = next)
        }
        return next
    }

    /**
     * Expand only from the indexed order already queried by the grid worker.
     * Never requery the full profile, expose unvalidated rows, or increase the
     * full-image predecode limit. Reconciles back into that order on a worker.
     */
    fun requestExpand(context: Context, profileId: String?, sessionId: String?,
        anchor: String, forward: Boolean, query: String? = null, cancelled: () -> Boolean = { false }): Boolean {
        val old = partial ?: return false
        val source = indexed ?: return false
        if (old.request !== source.request || !valid(context, old, profileId, sessionId) ||
            !source.request.isCurrent(context) || cancelled()) return false
        val size = source.segments?.size ?: source.order.size
        fun item(index: Int) = source.segments?.get(index) ?: source.order[index]
        val pos = if (source.complete) old.catalog.ordinals[anchor] ?: return false
            else source.segments?.indexOf(anchor) ?: source.order.indexOfFirst { it.path == anchor }
        // The live anchor can be an unindexed filesystem supplement. Those rows
        // arrive via offerPartial; absence from MediaStore is not an END signal.
        if (pos < 0) return false
        val existing = old.catalog.pathIndex
        val batch = ArrayList<Medium>()
        val ordinals = HashMap(old.catalog.ordinals)
        var cursor = pos + if (forward) 1 else -1
        while (cursor in 0 until size && batch.size < EXPAND_BATCH) {
            if (cancelled()) return false
            val candidate = item(cursor)
            if (candidate.path !in existing && (query.isNullOrBlank() || candidate.name.contains(query, ignoreCase = true))) {
                batch += candidate
                ordinals[candidate.path] = cursor
            }
            cursor += if (forward) 1 else -1
        }
        val exhausted = cursor !in 0 until size
        if (cancelled()) return false
        val started = GalleryPerfTrace.begin()
        GalleryPerfTrace.event("viewer_expand_started", batch.size)
        val (safe, _) = PhotoAccessValidationBatch(context, source.request, cancelled,
            directVisibilityOnly = true).validate(batch)
        // Denied source roots do not block expansion with independently validated public files.
        if (cancelled() || !source.request.isCurrent(context)) return false
        val collected = LinkedHashMap<String, Medium>()
        old.catalog.media.forEach { collected[it.path] = it }
        // Indexed rows without taken dates use the same modified-date fallback
        // as the visible Photos grid before any partial sort.
        val normalized = safe.map { medium ->
            if (medium.taken <= 0L) medium.copy().apply { taken = medium.modified } else medium
        }
        normalized.forEach { collected[it.path] = it }
        val sorting = source.request.scope.sorting
        val combined = when {
            // The completed segmented source knows the true full-grid ordinal,
            // including files that MediaStore did not index.
            source.complete -> {
                // Every retained row carries the ordinal recorded when read.
                // Never rank an LRU miss as -1, nor scan the full disk per row.
                if (collected.keys.any { it !in ordinals }) return false
                collected.values.sortedBy { ordinals.getValue(it.path) }
            }
            // Before completion, index positions cannot rank .nomedia supplements.
            sorting and org.fossify.commons.helpers.SORT_BY_RANDOM == 0 ->
                ArrayList(collected.values).also { MediaFetcher(context).sortMedia(it, sorting) }
            // Random partial stages keep the already displayed neighborhood stable;
            // a backward batch is traversed in reverse and must be prepended reversed.
            forward -> old.catalog.media + normalized
            else -> normalized.asReversed() + old.catalog.media
        }
        val center = combined.indexOfFirst { it.path == anchor }.coerceAtLeast(0)
        val first = (center - MAX_ITEMS / 2).coerceIn(0, (combined.size - MAX_ITEMS).coerceAtLeast(0))
        val window = combined.drop(first).take(MAX_ITEMS)
        val next = catalog(window, source.request.revision, false)?.copy(
            ordinals = if (source.complete) window.associate { it.path to ordinals.getValue(it.path) } else emptyMap(),
            before = if (source.complete && ((!forward && exhausted && first == 0) || (first == 0 && old.catalog.before == PhotoViewerCatalogCoordinator.Boundary.END))) PhotoViewerCatalogCoordinator.Boundary.END else if (source.complete) PhotoViewerCatalogCoordinator.Boundary.HAS_MORE else PhotoViewerCatalogCoordinator.Boundary.UNKNOWN,
            after = if (source.complete && ((forward && exhausted && first + window.size == combined.size) || (first + window.size == combined.size && old.catalog.after == PhotoViewerCatalogCoordinator.Boundary.END))) PhotoViewerCatalogCoordinator.Boundary.END else if (source.complete) PhotoViewerCatalogCoordinator.Boundary.HAS_MORE else PhotoViewerCatalogCoordinator.Boundary.UNKNOWN
        ) ?: return false
        if (ViewerMediaUpdatePolicy.sameCatalog(old.catalog.media, next.media) &&
            old.catalog.before == next.before && old.catalog.after == next.after) return false
        synchronized(this) {
            if (partial !== old || indexed !== source || !source.request.isCurrent(context)) return false
            partial = old.copy(catalog = next, createdAt = SystemClock.elapsedRealtime())
        }
        GalleryPerfTrace.end("viewer_expand_finished", started, safe.size)
        GalleryPerfTrace.end("viewer_partial_window_expand", started, safe.size)
        notifyChanged()
        return true
    }

    @Synchronized fun invalidate(profileId: String? = null) {
        if (profileId == null || loading?.scope?.profileId == profileId || prepared?.request?.scope?.profileId == profileId ||
            partial?.request?.scope?.profileId == profileId) {
            prepared = null
            partial = null
            indexed?.segments?.close()
            indexed = null
            loading = null
            focus = null
            notifyChanged()
        }
    }
}
