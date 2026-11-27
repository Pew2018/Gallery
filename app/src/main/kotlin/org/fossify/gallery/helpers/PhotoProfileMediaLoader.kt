package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Cached metadata is provisional; every newly published batch has fresh access evidence. */
class PhotoProfileMediaLoader internal constructor(
    private val context: Context,
    private val cancellationFetcher: MediaFetcher = MediaFetcher(context),
    private val sessionId: String? = null,
    private val cancelled: () -> Boolean = { false },
    private val diagnostics: MediaPipelineDiagnostics? = null,
    private val retainedGridOrder: StagedMediaOrder? = null,
    /** Root-transition token that caused this load; links a bottom-bar tap to the worker. */
    private val navigationToken: String? = null,
    /** Process-unique diagnostic id shared with the grid-side events of the same tap. */
    private val diagnosticRequestId: String = GalleryDiagnosticLogger.nextPhotoRequestId(),
    /** Displayed rows kept for visual continuity; never part of the authoritative result. */
    private val refreshBaseline: PhotoRefreshBaseline? = null,
    /** Media already committed to the grid when this request started. */
    private val displayedGridSize: Int = 0,
    /** The displayed rows were the COMPLETE result of this exact scope. */
    private val baselineComplete: Boolean = false,
    /** Items the user can still scroll through before reaching the displayed end. */
    private val browseHeadroom: () -> Int = { Int.MAX_VALUE },
) {
    companion object {
        const val PATH = "__photo_profiles__"
        const val EXTRA_ID = "org.fossify.gallery.PHOTO_PROFILE_ID"
        val MEDIA_TYPES = TYPE_VIDEOS or TYPE_IMAGES or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS
    }
    private fun stopped() = cancelled() || cancellationFetcher.shouldStop || Thread.currentThread().isInterrupted
    private fun checkCurrent(request: PhotoProfileLoadContext) {
        if (stopped() || !request.isCurrent(context) ||
            (request.scope.sources.any { it.type == PhotoSourceType.FAVORITES } &&
                request.favorites != context.getFavoritePaths().toSet())) throw PhotoLoadInvalidatedException()
    }

    /** Legacy final-only entry used by the viewer. */
    fun load(profileId: String?): ArrayList<ThumbnailItem> =
        ArrayList(loadStaged(profileId, allowPreview = false).media)

    fun loadStaged(
        profileId: String?,
        allowPreview: Boolean,
        anchorPath: String? = null,
        forceScan: Boolean = false,
        reuseActiveProducer: Boolean = false,
        onStage: (PhotoLoadResult) -> Unit = {}
    ): PhotoLoadResult {
        val request = PhotoProfileLoadContext.capture(context, profileId, sessionId) ?: throw PhotoLoadInvalidatedException()
        val scope = request.scope
        if (!PhotoSessionViewerCatalogStore.beginLoading(request, context, reuseActiveProducer)) throw PhotoLoadSharedException()
        try {
        PhotoProfileMediaCache.initialize(context.applicationContext)
        val fetcher = MediaFetcher(context, scope.filter and MEDIA_TYPES, ::stopped, reportReadFailures = true)
        fetcher.pipelineDiagnostics = diagnostics
        fetcher.traceRequest = cancellationFetcher.traceRequest
        val requestedFolders = scope.sources.filter { it.type == PhotoSourceType.FOLDER }.mapNotNull { it.path }
            .filter { !it.contains("/.") && !FavoriteFolderRules.isExcluded(it, scope.excluded) }.toSet()
        // Never enumerate or query protected source roots before their capability
        // has been granted. This also covers canonical ancestors of each root.
        val scanPlan = PhotoProfileSourceScanPolicy.select(
            requestedFolders,
            scope.sources.any { it.type == PhotoSourceType.FAVORITES },
            folderPermitted = { PhotoProfileAccessSessionManager.folderAuthorized(context, it, scope.sessionId) },
            favoritesPermitted = { PhotoProfileAccessSessionManager.favoritesAuthorized(context, scope.sessionId) },
            cancelled = ::stopped
        )
        checkCurrent(request)
        val folders = scanPlan.permittedFolders
        val favoriteFiles = if (scanPlan.favoritesPermitted) request.favorites.filter {
            it.substringBeforeLast('/') !in folders && it.substringBeforeLast('/') !in scanPlan.blockedFolders
        }.toSet() else emptySet()
        // A locked mixed profile may display one validated public preview, but
        // must not repeatedly replace its grid for every filesystem batch.
        val stableLockedPreview = scanPlan.hasBlockedSources
        var lockedPreviewPublished = false
        var lastPublishedStage: PhotoLoadResult? = null
        val timeline = PhotoPublicationTimeline(
            diagnostics, cancellationFetcher.traceRequest, diagnosticRequestId,
            navigationToken, scope.profileId)
        fun publishValidatedStage(stage: PhotoLoadResult) {
            if (stage.media.isEmpty() || (stableLockedPreview && lockedPreviewPublished)) return
            if (lastPublishedStage?.let { PhotoLoadPublicationPolicy.sameVisibleContent(it, stage) } == true) return
            PhotoSessionViewerCatalogStore.offerPartial(context, stage)
            diagnostics?.add("stagePublishCount")
            if (stage.stage == PhotoLoadStage.CACHE_PREVIEW) {
                timeline.mark("photo_preview_ui_enqueued", stage.stage.name, stage.media.size)
            }
            onStage(stage)
            lastPublishedStage = stage
            if (stableLockedPreview) lockedPreviewPublished = true
        }

        val order = retainedGridOrder ?: StagedMediaOrder()
        fun sort(media: ArrayList<Medium>) {
            media.forEach { if (it.taken <= 0L) it.taken = it.modified }
            order.sort(media, scope.sorting, fetcher)
        }
        fun result(stage: PhotoLoadStage, media: List<Medium>, preserveInputOrder: Boolean = false): PhotoLoadResult {
            checkCurrent(request)
            val (safe, access) = PhotoAccessValidationBatch(context, request, ::stopped).validate(media)
            safe.forEach { if (it.taken <= 0L) it.taken = it.modified }
            if (!preserveInputOrder) {
                val sorting = GalleryPerfTrace.begin()
                sort(safe)
                GalleryPerfTrace.end("photo_sort", sorting, safe.size)
            }
            checkCurrent(request)
            return PhotoLoadResult(request, stage, safe, access)
        }

        val cacheLookup = GalleryPerfTrace.begin()
        val reuse = if (forceScan) null else PhotoProfileMediaCache.reuse(request, context)
        val reusable = reuse?.second
        val verdict = reuse?.first
        diagnostics?.apply {
            add("cacheLookupCount")
            if (reusable == null) add("cacheMissCount") else {
                add("cacheHitCount")
                add("cachedItemCount", reusable.size.toLong())
            }
            // Two-level eligibility counters: a preview-eligible entry that failed
            // only completeness must never be reported as a plain cache miss.
            if (verdict != null) {
                if (verdict.previewEligible) add("cachePreviewEligibleCount") else add("cachePreviewRejectedCount")
                if (verdict.completeReusable) add("cacheCompleteReusableCount")
                if (verdict.eviction) add("cacheEvictionCount")
                when (verdict.missReason) {
                    PhotoCacheMissReason.NO_ENTRY -> add("cacheMissNoEntryCount")
                    PhotoCacheMissReason.SCOPE_MISMATCH -> { add("cacheMissScopeCount"); add("cacheMissStaleCount") }
                    PhotoCacheMissReason.CONTENT_REVISION_CHANGED -> { add("cacheMissRevisionCount"); add("cacheMissStaleCount") }
                    PhotoCacheMissReason.FAVORITES_CHANGED -> { add("cacheMissFavoritesCount"); add("cacheMissStaleCount") }
                    PhotoCacheMissReason.EXPIRED -> { add("cacheMissExpiredCount"); add("cacheMissStaleCount") }
                    PhotoCacheMissReason.CHECKPOINT_UNAVAILABLE -> add("cacheMissCheckpointUnavailableCount")
                    PhotoCacheMissReason.CHECKPOINT_CHANGED -> add("cacheMissCheckpointChangedCount")
                    PhotoCacheMissReason.NONE -> Unit
                }
            }
            if (forceScan) add("cacheForcedScanCount")
        }
        if (verdict != null) {
            timeline.mark("photo_cache_reuse_decision", if (reusable != null) "REUSABLE" else "PREVIEW_ONLY",
                reusable?.size ?: verdict.cacheRows, mapOf(
                    "previewEligible" to verdict.previewEligible,
                    "completeReusable" to verdict.completeReusable,
                    "checkpointAvailable" to verdict.checkpointAvailable,
                    "evictionPerformed" to verdict.eviction,
                    "missReason" to verdict.missReason.token,
                    "cacheRows" to verdict.cacheRows,
                    "cacheAgeMs" to verdict.ageMs,
                    "requestGeneration" to cancellationFetcher.traceRequest,
                ))
        }
        if (!allowPreview || stableLockedPreview) {
            timeline.mark("photo_cache_lookup_end", "REUSABLE", reusable?.size ?: 0,
                mapOf("cacheHit" to (reusable != null), "reusableCatalog" to (reusable != null),
                    "forcedScan" to forceScan))
        }
        if (reusable == null) GalleryPerfTrace.end("viewer_catalog_miss", cacheLookup, 0)

        // Keep already validated indexed/cache rows in later filesystem stages.
        val stagedCandidates = LinkedHashMap<String, Medium>()
        // Hot-cache first slice: select and validate only a bounded window instead
        // of filtering and sorting every cached row before the first frame. The
        // stored order belongs to this exact scope (sorting included), so it is
        // already the order this request would produce unless sorting is random.
        // Completeness still comes from the indexed/filesystem/COMPLETE stages.
        var preview = emptyList<Medium>()
        if (allowPreview && !stableLockedPreview) {
            preview = selectCachedPreview(
                reusable, request, folders, favoriteFiles, scope, anchorPath, forceScan, ::sort, timeline)
            timeline.mark("photo_preview_slice_ready", PhotoLoadStage.CACHE_PREVIEW.name, preview.size,
                mapOf("limit" to CachedMediaPreviewOrder.LIMIT))
            if (preview.isNotEmpty()) {
                // The cache sample has already had missing capture times normalized.
                // Access validation only removes rows and updates favorite membership.
                val validated = result(PhotoLoadStage.CACHE_PREVIEW, preview, preserveInputOrder = true)
                preview = validated.media
                timeline.mark("photo_preview_access_validated", PhotoLoadStage.CACHE_PREVIEW.name, preview.size)
                preview.forEach { stagedCandidates[it.path] = it }
                if (preview.isNotEmpty()) publishValidatedStage(validated)
            }
        }
        checkCurrent(request)
        if (reusable != null) {
            val validationStarted = GalleryPerfTrace.begin()
            val validated = result(PhotoLoadStage.COMPLETE, reusable,
                preserveInputOrder = retainedGridOrder == null || scope.sorting and SORT_BY_RANDOM == 0)
            GalleryPerfTrace.end("viewer_catalog_validation", validationStarted, validated.media.size)
            checkCurrent(request)
            // A generation-stable catalog should validate as the same complete set.
            // If any row disappeared or became hidden, use the existing full query
            // and supplement path to rebuild completeness conservatively.
            if (validated.media.size == reusable.size) {
                if (PhotoProfileMediaCache.isReusableCurrent(request, context, reusable)) {
                    GalleryPerfTrace.end("viewer_catalog_hit", cacheLookup, validated.media.size)
                    PhotoSessionViewerCatalogStore.prepare(context, validated)
                    PhotoProfileReturnProbe.record(context, validated)
                    return validated
                }
            }
            GalleryPerfTrace.end("viewer_catalog_miss", cacheLookup, validated.media.size)
        }

        // A catalog is reusable only when the source was stable across the scan
        // that created it. Capture this before index queries and file supplements.
        val scanCheckpointStarted = PhotoProfileCatalogCheckpoint.capture(context, scope)

        checkCurrent(request)
        val query = GalleryPerfTrace.begin()
        val indexed = fetcher.queryProfileIndexedMedia(folders, favoriteFiles, ArrayList(request.favorites))
        GalleryPerfTrace.end("photo_index_query", query, indexed.mediaByPath.size)
        if (allowPreview && indexed.mediaByPath.isNotEmpty()) {
            // Keep freshly validated preview-only files until filesystem reconciliation completes.
            // Full-access mode also needs a cheap existence check before showing stale indexed rows.
            val sortedIndex = ArrayList(indexed.mediaByPath.values)
            sort(sortedIndex)
            PhotoSessionViewerCatalogStore.offerIndexedCandidates(context, request, sortedIndex)
            val indexedCandidates = PhotoProfileMediaCache.preview(sortedIndex, anchorPath).filter {
                // A locked configuration must not publish unverified stale index rows.
                // Scoped storage cannot cheaply prove File.isFile(), so its locked
                // preview waits for the filesystem stage instead.
                if (stableLockedPreview) {
                    (android.os.Build.VERSION.SDK_INT < 30 || scope.allFiles) && java.io.File(it.path).isFile
                } else {
                    (!scope.allFiles && android.os.Build.VERSION.SDK_INT >= 30) || java.io.File(it.path).isFile
                }
            }
            val merged = LinkedHashMap<String, Medium>()
            preview.forEach { merged[it.path] = it }
            indexedCandidates.forEach { merged[it.path] = it }
            val stage = result(PhotoLoadStage.INDEXED, ArrayList(merged.values))
            if (stage.media.isNotEmpty()) {
                stage.media.forEach { stagedCandidates[it.path] = it }
                publishValidatedStage(stage)
            }
        }
        checkCurrent(request)
        val scanning = GalleryPerfTrace.begin()
        val supplements = LinkedHashMap<String, Medium>(stagedCandidates)
        // A baseline only proves what the grid shows, never that a file still exists.
        // It is merged into partial stages and never into the COMPLETE result below.
        val baseline = refreshBaseline?.takeIf { it.supports(request) }
        val baselinePaths = baseline?.items?.mapTo(HashSet(baseline.size * 2)) { it.path }
        if (baseline != null) {
            timeline.mark("photo_refresh_baseline_captured", PhotoLoadStage.FILESYSTEM.name, baseline.size,
                mapOf("complete" to baseline.complete, "displayedGrid" to displayedGridSize,
                    "revision" to baseline.revision, "accessVersion" to baseline.accessVersion))
        } else if (refreshBaseline != null) {
            timeline.mark("photo_refresh_baseline_rejected", PhotoLoadStage.FILESYSTEM.name, 0,
                mapOf("requestedRevision" to refreshBaseline.revision,
                    "currentRevision" to request.revision))
        }
        var publishedCount = maxOf(displayedGridSize, baseline?.size ?: 0)
        // Only a baseline that actually covers the grid may suppress intermediates;
        // otherwise a long scan would publish nothing over an empty page.
        val coveredByCompleteGrid = baselineComplete && baseline != null
        val publicationBudget = ProgressivePublicationBudget()
        var supplementVersion = 0L
        val pendingSupplements = LinkedHashMap<String, Medium>()
        val media = fetcher.scanProfileFileSupplements(folders, favoriteFiles, ArrayList(request.favorites), indexed,
            preferredPath = { PhotoSessionViewerCatalogStore.focusPath(request) ?: anchorPath }) { batch ->
            checkCurrent(request)
            if (stableLockedPreview) {
                // Once one authorized public preview is visible, subsequent batches
                // remain worker-local until COMPLETE. No repeated full sort/adapter
                // replacement while a protected source is still locked.
                if (!lockedPreviewPublished) {
                    val stage = result(PhotoLoadStage.FILESYSTEM, batch)
                    publishValidatedStage(stage)
                }
            } else {
                // Keep normal public-profile progressive loading unchanged.
                // This batch is merged into the previously validated candidates
                // and the cumulative list is sorted below before publication.
                // Sorting the batch here as well only repeats work and does not
                // affect the order of the published snapshot.
                batch.forEach {
                    if ((pendingSupplements[it.path] ?: supplements[it.path]) != it) supplementVersion++
                    pendingSupplements[it.path] = it
                }
                // Display need is decided before validation, copy and sort: a suppressed
                // batch keeps accumulating here and costs nothing until it is published.
                if (publicationBudget.admit(android.os.SystemClock.elapsedRealtime(), supplementVersion)) {
                    // Union size, without allocating the merged list for a batch that
                    // is only going to be merged into the next one.
                    val discoveredPaths = supplements.keys + pendingSupplements.keys
                    val mergedCount = if (baseline == null || baselinePaths == null) {
                        discoveredPaths.size
                    } else {
                        baseline.size + discoveredPaths.count { it !in baselinePaths }
                    }
                    val decision = PhotoSnapshotAdmission.decide(
                        PhotoLoadStage.FILESYSTEM, publishedCount, mergedCount, coveredByCompleteGrid,
                        browseHeadroom())
                    if (decision == SnapshotDecision.PUBLISH) {
                        val validated = result(PhotoLoadStage.FILESYSTEM, ArrayList(pendingSupplements.values),
                            preserveInputOrder = true)
                        pendingSupplements.clear()
                        validated.media.forEach { supplements[it.path] = it }
                        val discovered = ArrayList(supplements.values)
                        val ordered = if (baseline == null) {
                            discovered.also { sort(it) }
                        } else {
                            val union = baseline.merged(discovered)
                            timeline.mark("photo_partial_reconciled", PhotoLoadStage.FILESYSTEM.name,
                                union.media.size, mapOf("baseline" to baseline.size,
                                    "discovered" to discovered.size, "retained" to union.retained,
                                    "added" to union.added, "updated" to union.updated,
                                    "baselineComplete" to coveredByCompleteGrid))
                            ArrayList(union.media).also { sort(it) }
                        }
                        publishedCount = maxOf(publishedCount, ordered.size)
                        publishValidatedStage(validated.copy(media = ordered))
                    } else {
                        timeline.mark("photo_partial_publication_suppressed", PhotoLoadStage.FILESYSTEM.name,
                            mergedCount, mapOf("reason" to decision.token, "displayed" to publishedCount,
                                "baselineComplete" to coveredByCompleteGrid,
                                "headroom" to browseHeadroom()))
                    }
                }
            }
        }
        diagnostics?.add("diskSupplementCount", (media.size - indexed.mediaByPath.size).coerceAtLeast(0).toLong())
        GalleryPerfTrace.end("photo_supplement_scan", scanning, media.size)
        val complete = result(PhotoLoadStage.COMPLETE, media)
        diagnostics?.apply {
            set("candidateCount", media.size.toLong())
            set("acceptedCount", complete.media.size.toLong())
        }
        checkCurrent(request)
        if (complete.access.publicOnly && scope.sessionId == null) {
            val handoffStarted = GalleryPerfTrace.begin()
            val reusableCatalog = PhotoProfileMediaCache.offer(context, complete, scanCheckpointStarted)
            GalleryPerfTrace.end(
                if (reusableCatalog) "viewer_catalog_handoff" else "viewer_catalog_miss",
                handoffStarted, complete.media.size
            )
            // Public metadata only, never persist an authorization or a partial result.
            runCatching {
                checkCurrent(request)
                val writeStarted = if (diagnostics != null) android.os.SystemClock.elapsedRealtime() else 0L
                context.mediaDB.insertAll(complete.media.filter { it.deletedTS == 0L })
                diagnostics?.apply {
                    add("dbWriteCount")
                    add("dbWriteInputRows", complete.media.count { it.deletedTS == 0L }.toLong())
                    add("dbWriteMs", (android.os.SystemClock.elapsedRealtime() - writeStarted).coerceAtLeast(0L))
                }
            }.onFailure { if (it is PhotoLoadInvalidatedException) throw it }
        }
        checkCurrent(request)
        // Build the full authorized catalog while the existing load worker owns
        // the validated result, not when the user taps a grid thumbnail.
        PhotoSessionViewerCatalogStore.prepare(context, complete)
        PhotoProfileReturnProbe.record(context, complete)
        return complete
        } finally {
            PhotoSessionViewerCatalogStore.finishLoading(request)
        }
    }

    /**
     * Bounded first slice. Only the in-memory catalog stores a list already ordered
     * by this exact scope; Room rows are a candidate set, not an order.
     *
     * Both sources end in the same bounded existence walk. Sorting a Room snapshot
     * costs about a millisecond for 5k rows, while checking every row against the
     * filesystem costs about a millisecond per five rows, so the cold path orders
     * first and then validates only the window the first frame can show.
     */
    private fun selectCachedPreview(
        reusable: List<Medium>?,
        request: PhotoProfileLoadContext,
        folders: Set<String>,
        favoriteFiles: Set<String>,
        scope: PhotoProfileScope,
        anchorPath: String?,
        forceScan: Boolean,
        sort: (ArrayList<Medium>) -> Unit,
        timeline: PhotoPublicationTimeline
    ): List<Medium> {
        var orderedCache = reusable != null
        val cache: List<Medium> = if (reusable != null) {
            reusable
        } else {
            val started = GalleryPerfTrace.begin()
            val memory = PhotoProfileMediaCache.readOrdered(request)
            orderedCache = memory != null
            val cached = memory ?: runCatching {
                readDatabaseCache(folders, favoriteFiles)
            }.getOrDefault(emptyList())
            diagnostics?.apply {
                add("cacheLookupCount")
                if (cached.isEmpty()) add("cacheMissCount") else {
                    add("cacheHitCount")
                    add("cachedItemCount", cached.size.toLong())
                }
            }
            GalleryPerfTrace.end("photo_cache", started, cached.size)
            cached
        }
        timeline.mark("photo_cache_lookup_end",
            if (reusable != null) "REUSABLE" else "CACHE_PREVIEW", cache.size,
            mapOf("cacheHit" to cache.isNotEmpty(), "reusableCatalog" to (reusable != null),
                "orderedCache" to orderedCache, "forcedScan" to forceScan))
        val quickStarted = GalleryPerfTrace.begin()
        val randomSort = scope.sorting and SORT_BY_RANDOM != 0
        // One parent-readability memo per bounded batch: a first slice spans very few
        // folders, so the per-row parent probe is redundant work on the hot path.
        val visibleBatch = CachedMediaPreviewFilter.VisibleBatch()
        val window: CachedPreviewSlice? = when {
            // A stored order is only trusted for a non-random sort of this exact scope.
            orderedCache && !randomSort ->
                CachedMediaPreviewOrder.selectBounded(cache, scope.sorting, anchorPath, visible = visibleBatch::isVisible)
            // Room rows are unordered: order them (cheap) before the existence walk (expensive).
            !randomSort -> {
                val ordering = GalleryPerfTrace.begin()
                val ordered = ArrayList(cache).also { sort(it) }
                GalleryPerfTrace.end("photo_cache_order", ordering, ordered.size)
                CachedMediaPreviewOrder.selectBounded(ordered, scope.sorting, anchorPath, visible = visibleBatch::isVisible)
            }
            else -> null
        }
        if (window != null) {
            val slice = window.items
            diagnostics?.apply {
                add("cachePreviewBoundedCount")
                add("cachePreviewExistenceChecks", window.checks.toLong())
                add("cachePreviewSelectedCount", slice.size.toLong())
                if (!window.complete) add("cachePreviewDegradedCount")
            }
            timeline.mark("photo_cache_missing_filter_end", PhotoLoadStage.CACHE_PREVIEW.name,
                slice.size, mapOf("boundedSelection" to true, "cacheRows" to cache.size,
                    "checks" to window.checks, "limit" to CachedMediaPreviewOrder.LIMIT,
                    "budgetExhausted" to !window.complete))
            GalleryPerfTrace.end("photo_cache_missing_filter", quickStarted, slice.size)
            timeline.mark("photo_cache_order_ready", PhotoLoadStage.CACHE_PREVIEW.name, slice.size,
                mapOf("reusedOrder" to orderedCache))
            // The readable catalog is private/immutable; stage its own copies
            // before any later sort normalizes capture times in place.
            return slice.map { it.copy() }
        }
        // Random sorting keeps the full, explicitly seeded order. Its candidate
        // window is not a prefix of any stable order, so the bounded walk cannot
        // be proven equivalent and the previous path is retained unchanged.
        diagnostics?.add("cachePreviewFullFilterCount")
        // Copy first: the readable catalog is shared and must never be mutated in place.
        val sorted = CachedMediaPreviewFilter.removeConfirmedMissing(cache.map { it.copy() })
        timeline.mark("photo_cache_missing_filter_end", PhotoLoadStage.CACHE_PREVIEW.name,
            sorted.size, mapOf("boundedSelection" to false, "cacheRows" to cache.size,
                "checks" to cache.size, "budgetExhausted" to false))
        GalleryPerfTrace.end("photo_cache_missing_filter", quickStarted, sorted.size)
        val ordering = GalleryPerfTrace.begin()
        sort(sorted)
        GalleryPerfTrace.end("photo_cache_order", ordering, sorted.size)
        timeline.mark("photo_cache_order_ready", PhotoLoadStage.CACHE_PREVIEW.name, sorted.size,
            mapOf("reusedOrder" to false))
        return PhotoProfileMediaCache.preview(sorted, anchorPath).map { it.copy() }
    }

    private fun readDatabaseCache(folders: Set<String>, files: Set<String>): List<Medium> {
        val media = LinkedHashMap<String, Medium>()
        folders.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            val started = if (diagnostics != null) android.os.SystemClock.elapsedRealtime() else 0L
            val rows = context.mediaDB.getCachedMediaFromFolders(batch)
            diagnostics?.apply {
                add("dbReadCount")
                add("dbReadRows", rows.size.toLong())
                add("dbReadMs", (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
            }
            rows.forEach { media[it.path] = it }
        }
        files.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            val started = if (diagnostics != null) android.os.SystemClock.elapsedRealtime() else 0L
            val rows = context.mediaDB.getCachedMediaFromFiles(batch)
            diagnostics?.apply {
                add("dbReadCount")
                add("dbReadRows", rows.size.toLong())
                add("dbReadMs", (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
            }
            rows.forEach { media[it.path] = it }
        }
        return ArrayList(media.values)
    }
}

/** Match the grid's rendered-content comparison before publishing another staged snapshot. */
internal object PhotoLoadPublicationPolicy {
    fun sameVisibleContent(first: PhotoLoadResult, second: PhotoLoadResult): Boolean {
        if (first.context != second.context || first.access != second.access || first.media.size != second.media.size) {
            return false
        }
        return first.media.indices.all { index ->
            val before = first.media[index]
            val after = second.media[index]
            before.path == after.path && MediaUpdatePolicy.sameContent(before, after)
        }
    }
}
