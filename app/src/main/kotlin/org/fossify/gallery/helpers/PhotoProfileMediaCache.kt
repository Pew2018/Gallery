package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import org.fossify.gallery.models.Medium

/**
 * Why a cached catalog could not be used.
 *
 * The first six reasons fail *preview* eligibility and therefore also complete
 * reuse. [CHECKPOINT_UNAVAILABLE] and [CHECKPOINT_CHANGED] only fail complete
 * reuse: the stored metadata is still a legitimate, freshly validated preview.
 */
internal enum class PhotoCacheMissReason {
    NONE,
    NO_ENTRY,
    SCOPE_MISMATCH,
    CONTENT_REVISION_CHANGED,
    FAVORITES_CHANGED,
    EXPIRED,
    CHECKPOINT_UNAVAILABLE,
    CHECKPOINT_CHANGED;

    /**
     * Reasons that describe the *entry* going stale rather than this request being
     * outdated. Only these may drop the entry.
     *
     * A scope or favorite mismatch is ambiguous: it can mean the profile changed, or
     * that an old request is evaluating a newer entry. Evicting on that ambiguity
     * would let a superseded request delete the entry a concurrent load just
     * produced, so rejection alone is used there.
     */
    val invalidatesEntry: Boolean get() = this == CONTENT_REVISION_CHANGED || this == EXPIRED

    val token: String get() = name
}

/** Two independent decisions over one immutable entry. */
internal data class PhotoCacheVerdict(
    val previewEligible: Boolean,
    val completeReusable: Boolean,
    /** A preview-ineligible entry is rejected; only proven entry staleness removes it. */
    val eviction: Boolean,
    val missReason: PhotoCacheMissReason,
    val checkpointAvailable: Boolean,
    val cacheRows: Int,
    val ageMs: Long
) {
    companion object {
        fun miss(reason: PhotoCacheMissReason, rows: Int = 0, ageMs: Long = -1L, checkpoint: Boolean = false) =
            PhotoCacheVerdict(
                previewEligible = false, completeReusable = false,
                eviction = reason.invalidatesEntry, missReason = reason,
                checkpointAvailable = checkpoint, cacheRows = rows, ageMs = ageMs
            )
    }
}

/**
 * Public metadata only. Cache hits always undergo fresh path/protection validation.
 *
 * Eligibility is evaluated in two levels so that an entry which is safe to *show*
 * is never discarded merely because it cannot be *trusted as complete*:
 *
 *  - PREVIEW_ELIGIBLE: same profile scope, same content revision, same favorite
 *    relation, inside the age limit, and the request revision is still current.
 *  - COMPLETE_REUSABLE: additionally backed by a catalog checkpoint that still
 *    matches the live volume/folder state.
 *
 * A full-access (ALL_FILES) profile can never capture a checkpoint
 * ([PhotoProfileCatalogCheckpoint.capture] returns null there), so requiring one
 * before *reading* the cache would delete the only ordered copy on every entry
 * and force the Room full-filter path. Only completeness may depend on it.
 */
internal object PhotoProfileMediaCache {
    private const val MAX_PROFILES = 2
    private const val MAX_ITEMS = 12000
    private const val MAX_AGE_MS = 60_000L
    private data class Entry(
        val scope: PhotoProfileScope,
        val revision: Long,
        val favorites: Set<String>,
        val checkpoint: PhotoProfileCatalogCheckpoint?,
        val at: Long,
        val media: List<Medium>
    )
    private val entries = LinkedHashMap<String, Entry>(4, 0.75f, true)

    private var registered = false
    @Synchronized fun initialize(context: android.content.Context) {
        if (registered) return
        registered = true
        context.applicationContext.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(config: android.content.res.Configuration) = Unit
            override fun onLowMemory() = invalidate()
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) invalidate()
            }
        })
    }

    @Synchronized fun offer(
        context: android.content.Context,
        result: PhotoLoadResult,
        scanCheckpoint: PhotoProfileCatalogCheckpoint? = null
    ): Boolean {
        val scope = result.context.scope
        if (result.stage != PhotoLoadStage.COMPLETE || !result.access.publicOnly || scope.sessionId != null ||
            result.media.size > MAX_ITEMS || result.context.revision != PhotoContentRevision.current()) return false
        val freshnessStarted = GalleryPerfTrace.begin()
        val finishedCheckpoint = PhotoProfileCatalogCheckpoint.capture(context, scope)
        val checkpoint = finishedCheckpoint.takeIf {
            scanCheckpoint != null && scanCheckpoint == it &&
                PhotoProfileCatalogCheckpoint.hasReliableMediaStoreIdentity(result.media)
        }
        GalleryPerfTrace.end("viewer_catalog_freshness", freshnessStarted, if (checkpoint != null) 1 else 0)
        entries[scope.profileId] = Entry(
            scope, result.context.revision, result.context.favorites, checkpoint,
            SystemClock.elapsedRealtime(), result.media.map { it.copy() }
        )
        while (entries.size > MAX_PROFILES || entries.values.sumOf { it.media.size } > MAX_ITEMS) {
            entries.remove(entries.keys.first())
        }
        return checkpoint != null && entries[scope.profileId]?.checkpoint == checkpoint
    }

    /** Preview-only read. Returns defensive copies; the stored order is never handed out. */
    fun read(context: PhotoProfileLoadContext): List<Medium>? {
        val entry = currentEntry(context) ?: return null
        val verdict = previewVerdict(context, entry)
        if (!verdict.previewEligible) {
            if (verdict.eviction) evict(context, entry)
            return null
        }
        return entry.media.map { it.copy() }
    }

    /**
     * Ordered preview source for the load worker. The list is private and
     * immutable after insertion, so the caller may walk a bounded window of it
     * without copying or re-sorting the catalog.
     */
    fun readOrdered(context: PhotoProfileLoadContext): List<Medium>? {
        val entry = currentEntry(context) ?: return null
        val verdict = previewVerdict(context, entry)
        if (!verdict.previewEligible) {
            if (verdict.eviction) evict(context, entry)
            return null
        }
        return entry.media
    }

    /**
     * Worker-only complete cache hit. Metadata is reused; the caller must still
     * freshly validate every item. A completeness miss must not evict a preview.
     */
    fun reuse(context: PhotoProfileLoadContext, appContext: Context): Pair<PhotoCacheVerdict, List<Medium>?> =
        evaluate(context, appContext)

    /** Recheck catalog completeness after the potentially long per-item authorization batch. */
    @Synchronized fun isReusableCurrent(
        request: PhotoProfileLoadContext,
        appContext: android.content.Context,
        items: List<Medium>
    ): Boolean {
        val entry = entries[request.scope.profileId] ?: return false
        return entry.media === items && entry.scope == request.scope && entry.revision == request.revision &&
            entry.favorites == request.favorites && entry.checkpoint != null &&
            SystemClock.elapsedRealtime() - entry.at in 0..MAX_AGE_MS &&
            request.revision == PhotoContentRevision.current() &&
            PhotoProfileCatalogCheckpoint.isCurrent(appContext, request.scope, entry.checkpoint)
    }

    @Synchronized fun invalidate(profileId: String? = null) {
        if (profileId == null) entries.clear() else entries.remove(profileId)
    }

    fun preview(sorted: List<Medium>, anchorPath: String?, limit: Int = 160): List<Medium> {
        val anchor = sorted.indexOfFirst { it.path == anchorPath }.coerceAtLeast(0)
        val start = (anchor - limit / 4).coerceAtLeast(0)
        return sorted.subList(start, (start + limit).coerceAtMost(sorted.size))
    }

    private fun currentEntry(context: PhotoProfileLoadContext): Entry? =
        synchronized(this) { entries[context.scope.profileId] }

    /** Drop the entry only if it is still the one that failed, never a newer insert. */
    private fun evict(context: PhotoProfileLoadContext, entry: Entry) {
        synchronized(this) {
            if (entries[context.scope.profileId] === entry) entries.remove(context.scope.profileId)
        }
    }

    /** Cheap identity/age checks. No filesystem or MediaStore access. */
    private fun previewVerdict(context: PhotoProfileLoadContext, entry: Entry): PhotoCacheVerdict {
        val age = SystemClock.elapsedRealtime() - entry.at
        val reason = when {
            entry.scope != context.scope -> PhotoCacheMissReason.SCOPE_MISMATCH
            entry.revision != context.revision || context.revision != PhotoContentRevision.current() ->
                PhotoCacheMissReason.CONTENT_REVISION_CHANGED
            entry.favorites != context.favorites -> PhotoCacheMissReason.FAVORITES_CHANGED
            age < 0L || age > MAX_AGE_MS -> PhotoCacheMissReason.EXPIRED
            else -> PhotoCacheMissReason.NONE
        }
        if (reason != PhotoCacheMissReason.NONE) {
            return PhotoCacheVerdict.miss(reason, entry.media.size, age, entry.checkpoint != null)
        }
        return PhotoCacheVerdict(
            previewEligible = true, completeReusable = entry.checkpoint != null,
            eviction = false, missReason = PhotoCacheMissReason.NONE,
            checkpointAvailable = entry.checkpoint != null, cacheRows = entry.media.size, ageMs = age
        )
    }

    private fun evaluate(
        context: PhotoProfileLoadContext,
        appContext: Context
    ): Pair<PhotoCacheVerdict, List<Medium>?> {
        val entry = currentEntry(context)
            ?: return PhotoCacheVerdict.miss(PhotoCacheMissReason.NO_ENTRY) to null
        val preview = previewVerdict(context, entry)
        if (!preview.previewEligible) {
            if (preview.eviction) evict(context, entry)
            return preview to null
        }
        val checkpoint = entry.checkpoint
        if (checkpoint == null) {
            // Preview survives; completeness is simply unproven for this entry.
            return preview.copy(
                completeReusable = false, missReason = PhotoCacheMissReason.CHECKPOINT_UNAVAILABLE
            ) to null
        }
        // Filesystem + MediaStore work runs without the cache lock; a concurrently
        // replaced entry is rejected later by isReusableCurrent's identity check.
        val current = PhotoProfileCatalogCheckpoint.isCurrent(appContext, context.scope, checkpoint)
        if (!current) {
            return preview.copy(
                completeReusable = false, missReason = PhotoCacheMissReason.CHECKPOINT_CHANGED
            ) to null
        }
        return preview.copy(completeReusable = true, missReason = PhotoCacheMissReason.NONE) to entry.media
    }
}
