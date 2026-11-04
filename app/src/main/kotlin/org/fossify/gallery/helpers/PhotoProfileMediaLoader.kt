package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Cached metadata is provisional; every newly published batch has fresh access evidence. */
class PhotoProfileMediaLoader(
    private val context: Context,
    private val cancellationFetcher: MediaFetcher = MediaFetcher(context),
    private val sessionId: String? = null,
    private val cancelled: () -> Boolean = { false }
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
        fun publishValidatedStage(stage: PhotoLoadResult) {
            if (stage.media.isEmpty() || (stableLockedPreview && lockedPreviewPublished)) return
            if (lastPublishedStage?.let { PhotoLoadPublicationPolicy.sameVisibleContent(it, stage) } == true) return
            PhotoSessionViewerCatalogStore.offerPartial(context, stage)
            onStage(stage)
            lastPublishedStage = stage
            if (stableLockedPreview) lockedPreviewPublished = true
        }

        val randomRanks = HashMap<String, Int>()
        fun sort(media: ArrayList<Medium>) {
            if (scope.sorting and SORT_BY_RANDOM != 0) {
                media.forEach { randomRanks.getOrPut(it.path) { java.util.concurrent.ThreadLocalRandom.current().nextInt() } }
                media.sortWith(compareBy<Medium> { randomRanks[it.path] }.thenBy { it.path })
            } else fetcher.sortMedia(media, scope.sorting)
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
        val reusable = if (forceScan) null else PhotoProfileMediaCache.readReusable(request, context)
        if (reusable == null) GalleryPerfTrace.end("viewer_catalog_miss", cacheLookup, 0)
        if (reusable != null) {
            val validationStarted = GalleryPerfTrace.begin()
            val validated = runCatching { result(PhotoLoadStage.COMPLETE, reusable, preserveInputOrder = true) }
                .getOrElse { throw it }
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

        // Keep already validated indexed/cache rows in later filesystem stages.
        val stagedCandidates = LinkedHashMap<String, Medium>()
        var preview = emptyList<Medium>()
        if (allowPreview && !stableLockedPreview) {
            val started = GalleryPerfTrace.begin()
            val cached = PhotoProfileMediaCache.read(request) ?: runCatching {
                readDatabaseCache(folders, favoriteFiles)
            }.getOrDefault(emptyList())
            GalleryPerfTrace.end("photo_cache", started, cached.size)
            val sorted = ArrayList(cached)
            sort(sorted)
            preview = PhotoProfileMediaCache.preview(sorted, anchorPath)
            if (preview.isNotEmpty()) {
                // Preview selection preserves the already-sorted cache order;
                // access validation only filters rows and does not reorder them.
                val validated = result(PhotoLoadStage.CACHE_PREVIEW, preview, preserveInputOrder = true)
                preview = validated.media
                preview.forEach { stagedCandidates[it.path] = it }
                if (preview.isNotEmpty()) publishValidatedStage(validated)
            }
        }
        checkCurrent(request)
        val query = GalleryPerfTrace.begin()
        val indexed = fetcher.queryProfileIndexedMedia(folders, favoriteFiles, ArrayList(request.favorites))
        GalleryPerfTrace.end("photo_index_query", query, indexed.mediaByPath.size)
        if (allowPreview && indexed.mediaByPath.isNotEmpty()) {
            // Keep freshly validated preview-only files until filesystem reconciliation completes.
            // Full-access mode also needs a cheap existence check before showing stale indexed rows.
            val sortedIndex = ArrayList(indexed.mediaByPath.values)
            // Use the same missing-capture-time fallback as the authorized
            // grid stage BEFORE indexing the provisional navigation order.
            sortedIndex.forEach { if (it.taken <= 0L) it.taken = it.modified }
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
                val stage = result(PhotoLoadStage.FILESYSTEM, batch, preserveInputOrder = true)
                stage.media.forEach { supplements[it.path] = it }
                val ordered = ArrayList(supplements.values)
                sort(ordered)
                publishValidatedStage(stage.copy(media = ordered))
            }
        }
        GalleryPerfTrace.end("photo_supplement_scan", scanning, media.size)
        val complete = result(PhotoLoadStage.COMPLETE, media)
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
                context.mediaDB.insertAll(complete.media.filter { it.deletedTS == 0L })
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

    private fun readDatabaseCache(folders: Set<String>, files: Set<String>): List<Medium> {
        val media = LinkedHashMap<String, Medium>()
        folders.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            context.mediaDB.getCachedMediaFromFolders(batch).forEach { media[it.path] = it }
        }
        files.toList().chunked(400).forEach { batch ->
            if (stopped()) throw PhotoLoadInvalidatedException()
            context.mediaDB.getCachedMediaFromFiles(batch).forEach { media[it.path] = it }
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
