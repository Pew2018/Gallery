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
        val isComplete: Boolean = true
    )

    private data class Entry(
        val id: String,
        val request: PhotoProfileLoadContext,
        val createdAt: Long,
        val catalog: Catalog
    )
    private data class IndexedCandidates(
        val request: PhotoProfileLoadContext,
        val order: List<Medium>
    )

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
    @Synchronized fun beginLoading(request: PhotoProfileLoadContext) {
        loading = request
        indexed = null
    }

    @Synchronized fun finishLoading(request: PhotoProfileLoadContext) {
        if (loading !== request) return
        loading = null
        indexed = null
        notifyChanged()
    }

    fun isLoading(context: Context, profileId: String?, sessionId: String?): Boolean {
        val request = loading ?: return false
        return request.scope.profileId == profileId && request.scope.sessionId == sessionId &&
            request.isCurrent(context)
    }

    /** Indexed candidates remain private until a bounded access batch approves them. */
    fun offerIndexedCandidates(context: Context, request: PhotoProfileLoadContext, items: List<Medium>) {
        if (items.size > MAX_ITEMS || loading !== request ||
            !request.isCurrent(context)) return
        indexed = IndexedCandidates(request, items)
    }

    /** Worker-only stage publication. Never call this with unvalidated indexed rows. */
    fun offerPartial(context: Context, result: PhotoLoadResult) {
        if (result.stage == PhotoLoadStage.COMPLETE || result.access.needsUnlock ||
            (loading != null && loading !== result.context) || !result.context.isCurrent(context)) return
        val next = catalog(result.media, result.context.revision, false) ?: return
        synchronized(this) {
            val old = partial?.takeIf { it.request.scope == result.context.scope &&
                it.request.revision == result.context.revision }
            // Never shrink an already expanded authenticated window when an
            // earlier INDEXED-stage callback finishes after a range extension.
            partial = if (old != null && old.catalog.media.size > next.media.size) old else
                Entry(old?.id ?: UUID.randomUUID().toString(), result.context,
                    SystemClock.elapsedRealtime(), next)
        }
        registerMemoryCallback(context)
        notifyChanged()
    }

    /** Worker only. COMPLETE data is authoritative; never infer it from a partial stage. */
    fun prepare(context: Context, result: PhotoLoadResult): String? {
        if (result.stage != PhotoLoadStage.COMPLETE || result.access.needsUnlock ||
            (loading != null && loading !== result.context) || !result.context.isCurrent(context)) return null
        val started = GalleryPerfTrace.begin()
        val next = catalog(result.media, result.context.revision, true) ?: return null
        if (!result.context.isCurrent(context)) return null
        val candidate: Entry
        synchronized(this) {
            val old = partial?.takeIf { it.request.scope == result.context.scope &&
                it.request.revision == result.context.revision }
            candidate = Entry(old?.id ?: UUID.randomUUID().toString(), result.context,
                SystemClock.elapsedRealtime(), next)
            prepared = candidate
            partial = null
            indexed = null
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
        if (id != entry.id || !entry.catalog.pathIndex.containsKey(selectedPath) ||
            !valid(context, entry, profileId, sessionId)) return null
        return entry.catalog
    }

    /** Worker-only query used when the grid publishes a later validated stage. */
    fun latest(context: Context, profileId: String?, sessionId: String?, selectedPath: String): Catalog? {
        val entry = prepared ?: partial ?: return null
        if (!entry.catalog.pathIndex.containsKey(selectedPath) ||
            !valid(context, entry, profileId, sessionId)) return null
        return entry.catalog
    }

    /**
     * Expand only from the indexed order already queried by the grid worker.
     * Never requery the full profile, expose unvalidated rows, or increase the
     * full-image predecode limit. Reconciles back into that order on a worker.
     */
    fun requestExpand(context: Context, profileId: String?, sessionId: String?,
        anchor: String, forward: Boolean, cancelled: () -> Boolean = { false }): Boolean {
        val old = partial ?: return false
        val source = indexed ?: return false
        if (old.request !== source.request || !valid(context, old, profileId, sessionId) ||
            !source.request.isCurrent(context) || cancelled()) return false
        val pos = source.order.indexOfFirst { it.path == anchor }
        if (pos < 0) return false
        val range = if (forward) {
            pos..(pos + EXPAND_BATCH).coerceAtMost(source.order.lastIndex)
        } else {
            (pos - EXPAND_BATCH).coerceAtLeast(0)..pos
        }
        val existing = old.catalog.pathIndex
        val batch = source.order.slice(range).filterNot { it.path in existing }
        if (batch.isEmpty() || cancelled()) return false
        val started = GalleryPerfTrace.begin()
        val (safe, _) = PhotoAccessValidationBatch(context, source.request, cancelled,
            directVisibilityOnly = true).validate(batch)
        if (cancelled() || !source.request.isCurrent(context)) return false
        val additions = safe.associateBy { it.path }
        val collected = HashMap<String, Medium>(existing.size + additions.size)
        old.catalog.media.forEach { collected[it.path] = it }
        collected.putAll(additions)
        // Source order is stable, including random sorting, across all stages.
        val combined = source.order.mapNotNull { collected[it.path] }
        val next = catalog(combined, source.request.revision, false) ?: return false
        synchronized(this) {
            if (partial !== old || indexed !== source || !source.request.isCurrent(context)) return false
            partial = old.copy(catalog = next, createdAt = SystemClock.elapsedRealtime())
        }
        GalleryPerfTrace.end("viewer_partial_window_expand", started, safe.size)
        notifyChanged()
        return true
    }

    @Synchronized fun invalidate(profileId: String? = null) {
        if (profileId == null || prepared?.request?.scope?.profileId == profileId ||
            partial?.request?.scope?.profileId == profileId) {
            prepared = null
            partial = null
            indexed = null
            loading = null
        }
    }
}
