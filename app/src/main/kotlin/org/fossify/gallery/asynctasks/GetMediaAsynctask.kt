package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

internal class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, val onFailure: (Exception) -> Unit = {},
    private val cachedMediaFallback: List<Medium> = emptyList(),
    private val onPartialFailure: (Map<String, Exception>) -> Unit = {},
    private val favoritePathsOverride: ArrayList<String>? = null,
    private val favoriteSessionId: String? = null,
    private val photoProfileId: String? = null,
    private val photoSessionId: String? = null,
    private val photoAllowPreview: Boolean = true,
    private val photoAnchorPath: String? = null,
    private val photoReuseProducer: Boolean = false,
    private val onPhotoResult: ((PhotoLoadResult) -> Unit)? = null,
    private val manualRefresh: Boolean = false,
    private val folderSessionId: String? = null,
    private val requestId: Long = 0,
    private val diagnosticLoadReason: String? = null,
    private val folderPriorityPaths: List<String>? = null,
    private val onScanCancelled: (() -> Unit)? = null,
    private val retainSortMetadata: Boolean = false,
    private val pagePreviewTarget: Int = 0,
    private val reuseAllMediaIndex: Boolean = false,
    private val onPagePreview: ((MediaPageBatch) -> Unit)? = null,
    private val onFolderPreview: ((FolderMediaReadResult, ArrayList<ThumbnailItem>) -> Unit)? = null,
    private val onSortingMetadataRefreshRequired: (() -> Unit)? = null,
    private val onFavoritePreview: ((List<Medium>, Set<String>) -> Unit)? = null,
    private val onFolderDetails: ((List<Medium>) -> Unit)? = null,
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, PhotoLoadResult, ArrayList<ThumbnailItem>>() {
    private val diagnosticSource = when {
        photoProfileId != null || mPath == PhotoProfileMediaLoader.PATH -> "PHOTO_PROFILE"
        mPath == FAVORITES -> "FAVORITES"
        mPath == RECYCLE_BIN -> "RECYCLE_BIN"
        showAll -> "FOLDER"
        else -> "FOLDER"
    }
    private val pipelineDiagnostics = if (GalleryDiagnosticLogger.isEnabled())
        MediaPipelineDiagnostics.create(
            if (photoProfileId != null) "photo-profile:$photoProfileId" else mPath,
            diagnosticSource, if (manualRefresh) "REFRESH" else diagnosticLoadReason ?: "INITIAL", requestId,
            permissionMode = when {
                android.os.Build.VERSION.SDK_INT < 30 -> "LEGACY"
                android.os.Environment.isExternalStorageManager() -> "ALL_FILES"
                else -> "SCOPED_STORAGE"
            }
        ) else null
    private val createdAtElapsedMs = android.os.SystemClock.elapsedRealtime()
    private var queuedAtElapsedMs = createdAtElapsedMs
    private var workerStartedElapsedMs = createdAtElapsedMs
    private var workerFinishedElapsedMs = createdAtElapsedMs
    private val mediaFetcher = MediaFetcher(context, reportReadFailures = true, discoverUnindexedFolders = manualRefresh).also {
        it.traceRequest = requestId
        it.preferredFolderOrder = folderPriorityPaths
        it.pipelineDiagnostics = pipelineDiagnostics
    }
    init {
        pipelineDiagnostics?.apply {
            add("cacheLookupCount")
            if (cachedMediaFallback.isEmpty()) add("cacheMissCount") else {
                add("cacheHitCount")
                add("cachedItemCount", cachedMediaFallback.size.toLong())
            }
        }
    }
    internal var folderReadResult: FolderMediaReadResult? = null
    internal var preparedSorting: Int? = null
        private set
    internal var pageAccessCurrent: (() -> Boolean)? = null
        private set
    private var sortingMetadataRefreshRequired = false
        private set
    /** Captured before cache fallback/group headers; never use an Adapter for directory counts. */
    internal var folderStatsMediaSnapshot: List<Medium> = emptyList()
        private set
    internal var scanResult = GalleryScanResult(GalleryScanState.CANCELLED)
        private set
    private var successfulSources = 0
    private var scanFailure: Exception? = null
    private var folderFailures: Map<String, Exception> = emptyMap()
    private var profileResult: PhotoLoadResult? = null
    private val diagnosticProfileStages = HashSet<String>()
    /** Non-null only when a public single-folder scan was stable at start and completion. */
    internal var folderCatalogCheckpoint: FolderReturnCheckpoint? = null
        private set
    @Volatile var favoriteRelationshipSnapshot: Set<String>? = null
        private set

    internal var durationReader: (String) -> Int = { context.getDuration(it) ?: 0 }
    private val delivery = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onPreExecute() {
        super.onPreExecute()
        queuedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
    }

    private fun folderAllowed(path: String): Boolean {
        val parent = java.io.File(path).parent ?: return false
        return FolderUnlockSessionManager.findProtectedAncestor(parent, context.config::isFolderProtected) == null ||
            FolderUnlockSessionManager.isMediaAuthorized(folderSessionId, path, context.config::isFolderProtected)
    }
    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        workerStartedElapsedMs = android.os.SystemClock.elapsedRealtime()
        FolderRefreshDiagnostics.event(requestId, "scan_started", detail = "source=${if (photoProfileId != null) "photo_profile" else if (showAll) "all_media" else if (mPath == FAVORITES) "favorites" else "folder"}")
        FolderRefreshDiagnostics.event(requestId, "worker_started")
        return try {
        val noMediaFolder = !showAll && mPath.startsWith('/') && photoProfileId == null &&
            mPath != PhotoProfileMediaLoader.PATH && mediaFetcher.noMediaSnapshot.contains(mPath)
        val folderCheckpointStarted = if (!noMediaFolder && !showAll && mPath.startsWith('/') && mPath != FAVORITES &&
            mPath != RECYCLE_BIN && photoProfileId == null) {
            runCatching { FolderReturnCheckpoint.capture(context, mPath) }.getOrNull()
        } else null
        if (mPath == PhotoProfileMediaLoader.PATH || photoProfileId != null) {
            val loader = PhotoProfileMediaLoader(context, mediaFetcher, photoSessionId, { isCancelled }, pipelineDiagnostics)
            val final = loader.loadStaged(photoProfileId, photoAllowPreview && onPhotoResult != null, photoAnchorPath,
                forceScan = manualRefresh, reuseActiveProducer = photoReuseProducer) {
                if (!isCancelled) publishProgress(it.snapshotForUi())
            }
            scanResult = GalleryScanResult(if (final.stage == PhotoLoadStage.COMPLETE)
                GalleryScanState.COMPLETE else GalleryScanState.PARTIAL)
            profileResult = final.snapshotForUi()
            return ArrayList(final.media)
        }
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        // Retain sortable fields for physical folders and Favorites in this one
        // scan. A choice made while scanning must not restart enumeration just
        // because its numeric fields were not requested at scan start.
        val progressivePage = onPagePreview != null
        val usePageIndex = progressivePage || reuseAllMediaIndex
        val physicalSortMetadata = retainSortMetadata && (showAll || mPath.startsWith('/') || mPath == FAVORITES) &&
            mPath != RECYCLE_BIN
        val getProperDateTaken = physicalSortMetadata || folderSorting and SORT_BY_DATE_TAKEN != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

        val getProperLastModified = physicalSortMetadata || folderSorting and SORT_BY_DATE_MODIFIED != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

        val getProperFileSize = physicalSortMetadata || folderSorting and SORT_BY_SIZE != 0
        val savedFavorites = FolderRefreshDiagnostics.measure(requestId, "favorite_relationships") { context.getFavoritePaths() }
        if (mPath == FAVORITES) favoriteRelationshipSnapshot = savedFavorites.toSet()
        val favoritePaths = if (mPath == FAVORITES) {
            val allowed = if (favoriteSessionId != null) {
                FavoriteAccessSessionManager.validatePaths(favoriteSessionId, savedFavorites)?.paths.orEmpty()
            } else {
                runCatching {
                    FavoriteAccessPolicy.unprotectedPaths(
                        FavoriteAccessSessionManager.captureSnapshot(savedFavorites, context::favoriteProtection)
                    )
                }.getOrDefault(emptySet())
            }
            (favoritePathsOverride ?: savedFavorites).filter { it in allowed }.toCollection(ArrayList())
        } else favoritePathsOverride ?: savedFavorites
        val ordinaryFolder = (onFolderPreview != null || progressivePage) && !showAll &&
            mPath.startsWith('/') && mPath != RECYCLE_BIN && mPath != FAVORITES
        // Publish the basic folder catalog before optional video metadata extraction.
        val getVideoDurations = context.config.showThumbnailVideoDuration && !ordinaryFolder && !progressivePage
        // MediaStore indexing and direct filesystem access are separate
        // capabilities. MANAGE_EXTERNAL_STORAGE enables the latter; it does
        // not make indexed rows unavailable as an early, validated preview.
        val scopedAvailable = isRPlus()
        val scopedFolder = MediaQueryScope.shouldUseFolderQuery(
            showAll = showAll,
            path = mPath,
            scopedStorageAvailable = scopedAvailable,
            isOtg = scopedAvailable && mPath.startsWith('/') && context.isPathOnOTG(mPath)
        )
        // The scoped cursor already returns DATE_MODIFIED and DATE_TAKEN.
        // Only user-edited dates need a separate Room lookup.
        val pageIndex = usePageIndex && (showAll ||
            (ordinaryFolder && !context.isPathOnOTG(mPath) && !noMediaFolder &&
                !context.isRestrictedWithSAFSdk30(mPath)))
        val indexedMetadata = pageIndex || (scopedAvailable && (showAll || scopedFolder))
        val lastModifieds = if (getProperLastModified && !indexedMetadata && !noMediaFolder && (showAll || !mPath.startsWith('/'))) {
            if (!showAll && mPath.startsWith('/')) mediaFetcher.getFolderLastModifieds(mPath) else mediaFetcher.getLastModifieds()
        } else HashMap()
        val dateTakens = when {
            !getProperDateTaken -> HashMap<String, Long>()
            (pageIndex || scopedAvailable) && showAll -> mediaFetcher.getCustomDateTakenOverrides()
            noMediaFolder || scopedFolder || pageIndex -> mediaFetcher.getSelectedFolderDateOverrides(listOf(mPath))
            !showAll && mPath.startsWith('/') -> mediaFetcher.getFolderDateTakens(mPath)
            else -> mediaFetcher.getDateTakens()
        }

        val access = if (progressivePage) DirectoryAccessState.capture(context) else null
        fun accessCurrent() = runCatching {
            (access == null || access == DirectoryAccessState.capture(context)) &&
                (folderSessionId == null || FolderUnlockSessionManager.isActive(folderSessionId))
        }.getOrDefault(false)
        if (progressivePage) pageAccessCurrent = ::accessCurrent
        val visibility = MediaPageVisibility(context, showAll, ::folderAllowed)
        if (ordinaryFolder && !folderAllowed(java.io.File(mPath, "access-probe").path)) {
            throw SecurityException("Folder authorization required")
        }
        fun deliver(stage: DisplayedSourceStage, rows: List<Medium>, sourceCurrent: () -> Boolean) {
            if (isCancelled || rows.isEmpty()) return
            val batch = MediaPageBatch(stage, rows) { accessCurrent() && sourceCurrent() }
            FolderRefreshDiagnostics.event(requestId,
                if (stage == DisplayedSourceStage.INDEX_PREVIEW) "media_index_preview_ready" else "media_scan_partial",
                detail = "count=${rows.size}")
            delivery.post { if (!isCancelled) onPagePreview?.invoke(batch) }
        }

        var indexAvailable = false
        val indexed = if (pageIndex) {
            val preview = LinkedHashMap<String, Medium>()
            val started = android.os.SystemClock.elapsedRealtime()
            var published = false
            fun publishIndex() {
                if (published || preview.isEmpty()) return
                published = true
                val rows = ArrayList(preview.values)
                mediaFetcher.sortMedia(rows, folderSorting)
                deliver(DisplayedSourceStage.INDEX_PREVIEW, rows) { true }
            }
            mediaFetcher.queryPageIndex(isPickImage, isPickVideo, favoritePaths, dateTakens,
                if (showAll) null else setOf(mPath)) { item ->
                if (progressivePage && !published && visibility.allowsIndexedFile(item.path)) {
                    preview.putIfAbsent(item.path, item)
                    if (preview.size >= pagePreviewTarget || android.os.SystemClock.elapsedRealtime() - started >= 150L) {
                        publishIndex()
                    }
                }
            }?.also {
                indexAvailable = true
                publishIndex()
            } ?: hashMapOf()
        } else null

        if (progressivePage) {
            val candidates = HashSet<String>()
            val pending = ArrayList<Medium>()
            val sourceChecks = LinkedHashMap<String, () -> Boolean>()
            val cadence = MediaPreviewCadence(pagePreviewTarget, workerStartedElapsedMs)
            mediaFetcher.onPageCandidate = { item, current ->
                if (visibility.allows(item.path) && candidates.add(item.path)) {
                    if (candidates.size == 1) FolderRefreshDiagnostics.event(requestId, "media_first_candidate_ready")
                    pending.add(item.copy())
                    sourceChecks.putIfAbsent(item.parentPath, current)
                    if (cadence.shouldPublish(candidates.size, android.os.SystemClock.elapsedRealtime())) {
                        val checks = sourceChecks.values.toList()
                        deliver(DisplayedSourceStage.SCAN_PARTIAL, ArrayList(pending)) { checks.all { it() } }
                        pending.clear()
                        sourceChecks.clear()
                    }
                }
            }
        }

        successfulSources = if (showAll) 0 else 1
        val scanStarted = GalleryPerfTrace.begin()
        val media = if (showAll) {
            val foldersToScan = mediaFetcher.getFoldersToScan(indexedFolders = indexed?.keys?.takeIf { indexAvailable },
                continueWithoutIndex = usePageIndex).filter { it != RECYCLE_BIN && it != FAVORITES && FolderUnlockSessionManager.findProtectedAncestor(it, context.config::isFolderProtected) == null }
            // Scoped-storage MediaStore can be queried just once for all folders.
            // Reusing the grouped result avoids O(folderCount) full-store queries.
            val scopedFiles = indexed ?: mediaFetcher.getAndroid11FolderMedia(
                isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens
            )
            val result = FolderMediaScan.load(foldersToScan, cachedMediaFallback,
                cancelled = { isCancelled || mediaFetcher.shouldStop }) { folder ->
                val read = mediaFetcher.readFolder(
                    folder, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens, scopedFiles, sortResult = !progressivePage)
                if (!read.accessCurrent()) throw SecurityException("Folder authorization expired")
                if (read.status != FolderReadStatus.COMPLETE) {
                    folderFailures = folderFailures + (folder to (read.error ?: java.io.IOException("Incomplete folder refresh")))
                    if (read.status != FolderReadStatus.PARTIAL) throw folderFailures.getValue(folder)
                }
                read.presentation(cachedMediaFallback.filter { java.io.File(it.path).parent == folder }) {
                    folderAllowed(it) && (!usePageIndex || visibility.allows(it))
                }
            }
            successfulSources = result.successfulSources
            folderFailures = folderFailures + result.failures
            val media = result.media
            if (usePageIndex) media.removeAll { !folderAllowed(it.path) || !visibility.allows(it.path) }

            if (!progressivePage) mediaFetcher.sortMedia(media, context.config.getFolderSorting(SHOW_ALL))
            media
        } else {
            val indexedFolder = indexed ?: if (noMediaFolder) hashMapOf() else if (scopedFolder) {
                mediaFetcher.getAndroid11FolderMedia(
                    isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken,
                    HashMap(dateTakens), allowedFolders = setOf(mPath)
                )
            } else null
            if (mPath.startsWith('/') && mPath != RECYCLE_BIN) {
                fun allowed(path: String): Boolean {
                    val parent = java.io.File(path).parent ?: return false
                    return FolderUnlockSessionManager.findProtectedAncestor(parent, context.config::isFolderProtected) == null ||
                        FolderUnlockSessionManager.isMediaAuthorized(folderSessionId, path, context.config::isFolderProtected)
                }
                if (!allowed(java.io.File(mPath, "access-probe").path)) throw SecurityException("Folder authorization required")
                val folderKey = MediaStoreFolderKey.of(mPath)
                val indexedFolderMedia = indexedFolder?.get(folderKey).orEmpty()
                val folderCache = cachedMediaFallback.filter { item ->
                    val parent = java.io.File(item.path).parent.orEmpty()
                    MediaStoreFolderKey.of(parent) == folderKey ||
                        (item.type == TYPE_PORTRAITS &&
                            MediaStoreFolderKey.of(java.io.File(parent).parent.orEmpty()) == folderKey)
                }
                val folderProbe = java.io.File(mPath, "access-probe").path
                fun publishFolderPreview(progress: FolderMediaReadResult) {
                    if (isCancelled || onFolderPreview == null) return
                    val retained = if (progress.method == "mediastore_index_preview") progress
                        else FolderMediaProgressPreview.retainIndexedSnapshot(progress, indexedFolderMedia)
                    // A cache is a preview, not proof that the scan is finished.
                    // Keep cached rows while the live read is partial, then let a
                    // successful complete read remove paths confirmed absent.
                    val merged = retained.presentation(folderCache, ::folderAllowed)
                    if (merged.isEmpty()) return
                    mediaFetcher.sortMedia(merged, folderSorting)
                    val grouped = mediaFetcher.groupMedia(merged, pathToUse)
                    delivery.post {
                        if (!isCancelled && retained.accessCurrent()) onFolderPreview?.invoke(retained, grouped)
                    }
                }
                if (!progressivePage) mediaFetcher.onFolderProgress = ::publishFolderPreview
                if (scopedFolder && !progressivePage) {
                    val indexPreview = FolderMediaProgressPreview.fromScopedIndex(
                        indexedFolder?.get(folderKey).orEmpty()
                    ) { !isCancelled && allowed(folderProbe) }
                    if (indexPreview != null) publishFolderPreview(indexPreview)
                }
                val read = mediaFetcher.readFolder(
                    mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                    getVideoDurations, lastModifieds, dateTakens, indexedFolder, sortResult = false)
                folderReadResult = read
                folderStatsMediaSnapshot = read.media.map { it.copy() }
                when (read.status) {
                    FolderReadStatus.FAILED, FolderReadStatus.ACCESS_REQUIRED -> throw (read.error ?: SecurityException("Directory access required"))
                    FolderReadStatus.PARTIAL -> folderFailures = mapOf(mPath to (read.error ?: java.io.IOException("Incomplete folder refresh")))
                    else -> Unit
                }
                val fallback = folderCache.filter {
                    it.type and context.config.filterMedia != 0 &&
                        (context.config.shouldShowHidden || !it.name.startsWith('.')) &&
                        (!isPickImage || it.type != TYPE_VIDEOS) && (!isPickVideo || it.type != TYPE_IMAGES)
                }
                val presented = read.presentation(fallback) { allowed(it) && (!progressivePage || visibility.allows(it)) }
                if (!progressivePage) mediaFetcher.sortMedia(presented, folderSorting)
                presented
            } else {
                val staged = ArrayList<Medium>()
                var lastPreview = 0L
                mediaFetcher.getFilesFrom(
                    mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                    getVideoDurations, lastModifieds, dateTakens, indexedFolder,
                    onCandidateBatch = if (mPath == FAVORITES && onFavoritePreview != null) { batch ->
                        staged.addAll(batch)
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPreview >= 500L && !isCancelled) {
                            lastPreview = now
                            val preview = ArrayList(staged)
                            mediaFetcher.sortMedia(preview, folderSorting)
                            val relationship = savedFavorites.toSet()
                            delivery.post {
                                if (!isCancelled) onFavoritePreview.invoke(preview, relationship)
                            }
                        }
                    } else null
                )
            }
        }
        folderFailures = folderFailures + mediaFetcher.readFailures
        val discovery = mediaFetcher.folderDiscovery
        folderFailures = folderFailures + discovery.unreadablePaths.associateWith { java.io.IOException("Directory discovery incomplete") }
        scanResult = GalleryScanResult.sources(successfulSources, folderFailures, discovery.complete)
        GalleryPerfTrace.end("media_async_scan", scanStarted, media.size)

        if (ordinaryFolder) {
            val cached = cachedMediaFallback.associateBy { it.path }
            media.forEach { item -> cached[item.path]?.let { old ->
                if (old.modified == item.modified && old.size == item.size) item.videoDuration = old.videoDuration
            } }
            // Sorting can change while a long directory read is in flight. Apply
            // the latest effective setting to the complete source before grouping
            // and publishing, so the earlier task snapshot cannot restore old order.
            val latestSorting = context.config.getFolderSorting(pathToUse)
            if (!progressivePage && latestSorting != folderSorting) {
                val needsUnavailableMetadata =
                    (latestSorting and SORT_BY_DATE_TAKEN != 0 && !getProperDateTaken) ||
                    (latestSorting and SORT_BY_DATE_MODIFIED != 0 && !getProperLastModified) ||
                    (latestSorting and SORT_BY_SIZE != 0 && !getProperFileSize)
                if (needsUnavailableMetadata) sortingMetadataRefreshRequired = true
                else mediaFetcher.sortMedia(media, latestSorting)
            }
        } else if (mPath == FAVORITES || mPath == RECYCLE_BIN) {
            // Virtual sources read from a fixed relationship/Room snapshot. Re-read
            // only the effective sort mode here; keep the one source read and do
            // not let a sort chosen during that read publish the scan-start order.
            val latestSorting = context.config.getFolderSorting(pathToUse)
            if (latestSorting != folderSorting) {
                val needsUnavailableMetadata =
                    (latestSorting and SORT_BY_DATE_TAKEN != 0 && !getProperDateTaken) ||
                    (latestSorting and SORT_BY_DATE_MODIFIED != 0 && !getProperLastModified) ||
                    (latestSorting and SORT_BY_SIZE != 0 && !getProperFileSize)
                if (needsUnavailableMetadata) sortingMetadataRefreshRequired = true
                else mediaFetcher.sortMedia(media, latestSorting)
            }
        }
        if (progressivePage) {
            val latestSorting = context.config.getFolderSorting(pathToUse)
            if (latestSorting and SORT_BY_RANDOM == 0) mediaFetcher.sortMedia(media, latestSorting)
            preparedSorting = latestSorting
        }
        val result = if (progressivePage && preparedSorting?.and(SORT_BY_RANDOM) != 0) {
            // The page retains the displayed random prefix before grouping.
            ArrayList<ThumbnailItem>(media)
        } else FolderRefreshDiagnostics.measure(requestId, "grouping") { mediaFetcher.groupMedia(media, pathToUse) }
        val safeResult = if (mPath == FAVORITES && favoriteSessionId != null) {
            // Revalidate the complete captured snapshot once after scanning,
            // then use the returned capability set for a pure in-memory filter.
            val currentFavorites = context.getFavoritePaths().toSet()
            val allowed = FavoriteAccessSessionManager.retainFavorites(favoriteSessionId, currentFavorites).orEmpty()
            if (allowed.isEmpty()) ArrayList() else ArrayList(result.filter { item ->
                val path = (item as? Medium)?.path
                path == null || path in allowed
            })
        } else result
        if (mPath == FAVORITES && context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.d("GalleryFavorites", "savedPaths=${favoritePaths.size}, scannedMedia=${safeResult.count { it is Medium }}")
        }
        if (folderCheckpointStarted != null && folderFailures.isEmpty() && !isCancelled && scanFailure == null) {
            val freshnessStarted = GalleryPerfTrace.begin()
            val finished = runCatching { FolderReturnCheckpoint.capture(context, mPath) }.getOrNull()
            folderCatalogCheckpoint = finished.takeIf {
                FolderReturnCheckpoint.sameSnapshot(folderCheckpointStarted, it)
            }
            GalleryPerfTrace.end("viewer_catalog_freshness", freshnessStarted, if (folderCatalogCheckpoint != null) 1 else 0)
        }
        safeResult
        } catch (error: Exception) {
            if (isCancelled || error is java.util.concurrent.CancellationException ||
                error is android.os.OperationCanceledException || error is InterruptedException) {
                // Cancellation is an expected scan outcome. Do not rethrow it from
                // doInBackground: AsyncTask can wrap a worker-thrown
                // CancellationException in RuntimeException and crash the process.
                scanResult = GalleryScanResult(GalleryScanState.CANCELLED)
                mediaFetcher.shouldStop = true
                if (error is InterruptedException) Thread.currentThread().interrupt()
                return ArrayList()
            }
            scanResult = GalleryScanResult(
                if (error is SecurityException) GalleryScanState.ACCESS_REQUIRED else GalleryScanState.FAILED,
                folderFailures.keys, error
            )
            scanFailure = error
            ArrayList()
        } finally {
            workerFinishedElapsedMs = android.os.SystemClock.elapsedRealtime()
        }
    }

    override fun onProgressUpdate(vararg values: PhotoLoadResult) {
        if (!isCancelled) values.forEach { result ->
            if (result.context.isCurrent(context)) {
                pipelineDiagnostics?.apply {
                    add("stageCount")
                }
                if (GalleryDiagnosticLogger.isEnabled() && diagnosticProfileStages.add(result.stage.name)) GalleryDiagnosticLogger.event(
                    DiagnosticLevel.INFO, "PhotoProfile", "scan_partial_published", requestId,
                    mapOf("stage" to result.stage.name, "count" to result.media.size, "requestVersion" to result.context.revision)
                )
                onPhotoResult?.invoke(result)
            }
        }
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        if (isCancelled || scanResult.state == GalleryScanState.CANCELLED) {
            FolderRefreshDiagnostics.event(requestId, "scan_cancelled")
            finishPipelineDiagnostics("CANCELLED", media)
            onScanCancelled?.invoke()
            return
        }
        val error = scanFailure ?: if (scanResult.state == GalleryScanState.FAILED)
            scanResult.cause ?: java.io.IOException("Media scan has no reliable source") else null
        if (error != null) {
            FolderRefreshDiagnostics.event(requestId, "scan_failed", detail = "type=${error.javaClass.simpleName}")
            finishPipelineDiagnostics(if (error is SecurityException) "ACCESS_REQUIRED" else "FAILED", media)
            onFailure(error)
            return
        }
        FolderRefreshDiagnostics.event(requestId, "scan_completed", detail = "count=${media.size} state=${scanResult.state.name}")
        val result = profileResult
        if (result != null) {
            if (!result.context.isCurrent(context)) {
                FolderRefreshDiagnostics.event(requestId, "scan_failed", detail = "type=PhotoLoadInvalidatedException")
                finishPipelineDiagnostics("FAILED", media)
                onFailure(PhotoLoadInvalidatedException())
                return
            }
            finishPipelineDiagnostics(scanResult.state.name, media)
            if (onPhotoResult != null) onPhotoResult.invoke(result) else callback(media)
        } else {
            if (folderFailures.isNotEmpty()) onPartialFailure(folderFailures)
            finishPipelineDiagnostics(scanResult.state.name, media)
            if (sortingMetadataRefreshRequired) {
                FolderRefreshDiagnostics.event(requestId, "sorting_refresh_required", detail = "metadata_not_loaded")
                onSortingMetadataRefreshRequired?.invoke()
                return
            }
            callback(media)
            val read = folderReadResult
            if ((read != null && (onFolderPreview != null || onPagePreview != null) || showAll && onPagePreview != null) &&
                context.config.showThumbnailVideoDuration && !isCancelled) {
                fun detailsCurrent() = !isCancelled && !mediaFetcher.shouldStop &&
                    read?.accessCurrent?.invoke() != false && pageAccessCurrent?.invoke() != false
                val old = cachedMediaFallback.associateBy { it.path }
                val base = media.filterIsInstance<Medium>().map { item ->
                    val cached = old[item.path]
                    if (cached != null && cached.modified == item.modified && cached.size == item.size)
                        item.copy(videoDuration = cached.videoDuration) else item
                }
                VideoDurationEnricher.enrich(base,
                    current = ::detailsCurrent,
                    read = { path ->
                        if (!folderAllowed(path)) 0 else FolderRefreshDiagnostics.measure(requestId, "video_duration") {
                            durationReader(path)
                        }
                    }, publish = { enriched ->
                        val safe = enriched.filter { folderAllowed(it.path) }.toCollection(ArrayList())
                        // Optional details must not re-enter the scan-complete callback,
                        // regroup the catalog, or repeat cache pruning/viewer preparation.
                        delivery.post { if (detailsCurrent()) onFolderDetails?.invoke(safe) }
                    })
            }
        }
    }

    override fun onCancelled(media: ArrayList<ThumbnailItem>?) {
        super.onCancelled(media)
        finishPipelineDiagnostics("CANCELLED", media.orEmpty())
        onScanCancelled?.invoke()
    }

    private fun finishPipelineDiagnostics(terminalState: String, media: List<ThumbnailItem>) {
        val trace = pipelineDiagnostics ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val metrics = trace.snapshot()
        trace.finishIfOpen(
            terminalState = terminalState,
            queueWaitMs = (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
            workerMs = (workerFinishedElapsedMs - workerStartedElapsedMs).coerceAtLeast(0L),
            publicationWaitMs = (now - workerFinishedElapsedMs).coerceAtLeast(0L),
            candidateCount = metrics["candidateCount"] ?: metrics["mediaStoreRows"] ?: -1L,
            acceptedCount = if (terminalState == "COMPLETE" || terminalState == "PARTIAL")
                media.count { it is Medium }.toLong() else -1L
        )
    }

    /** A timed-out business request must not be diagnosed as just CANCELLED. */
    fun reportTimedOut() {
        finishPipelineDiagnostics("TIMED_OUT", emptyList())
    }

    /** A rejected executor never started the scan, and has no post-execute result. */
    fun reportRejectedExecution() {
        finishPipelineDiagnostics("FAILED", emptyList())
    }

    fun stopFetching() {
        scanResult = GalleryScanResult(GalleryScanState.CANCELLED)
        FolderRefreshDiagnostics.event(requestId, "scan_cancelled", detail = "reason=stop_fetching")
        mediaFetcher.shouldStop = true
        cancel(true)
        delivery.removeCallbacksAndMessages(null)
    }
}

