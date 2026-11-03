package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isPathOnOTG
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
    private val onPhotoResult: ((PhotoLoadResult) -> Unit)? = null,
    private val manualRefresh: Boolean = false,
    private val folderSessionId: String? = null,
    private val requestId: Long = 0,
    private val onFolderPreview: ((FolderMediaReadResult, ArrayList<ThumbnailItem>) -> Unit)? = null,
    private val onFolderDetails: ((List<Medium>) -> Unit)? = null,
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, PhotoLoadResult, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context, reportReadFailures = true, discoverUnindexedFolders = manualRefresh).also { it.traceRequest = requestId }
    internal var folderReadResult: FolderMediaReadResult? = null
        private set
    /** Captured before cache fallback/group headers; never use an Adapter for directory counts. */
    internal var folderStatsMediaSnapshot: List<Medium> = emptyList()
        private set
    private var scanFailure: Exception? = null
    private var folderFailures: Map<String, Exception> = emptyMap()
    private var profileResult: PhotoLoadResult? = null
    /** Non-null only when a public single-folder scan was stable at start and completion. */
    internal var folderCatalogCheckpoint: FolderReturnCheckpoint? = null
        private set
    @Volatile var favoriteRelationshipSnapshot: Set<String>? = null
        private set

    internal var durationReader: (String) -> Int = { context.getDuration(it) ?: 0 }
    private val delivery = android.os.Handler(android.os.Looper.getMainLooper())
    private fun folderAllowed(path: String): Boolean {
        val parent = java.io.File(path).parent ?: return false
        return FolderUnlockSessionManager.findProtectedAncestor(parent, context.config::isFolderProtected) == null ||
            FolderUnlockSessionManager.isMediaAuthorized(folderSessionId, path, context.config::isFolderProtected)
    }
    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        FolderRefreshTrace.event(requestId, "worker_started")
        return try {
        val noMediaFolder = !showAll && mPath.startsWith('/') && photoProfileId == null &&
            mPath != PhotoProfileMediaLoader.PATH && mediaFetcher.noMediaSnapshot.contains(mPath)
        val folderCheckpointStarted = if (!noMediaFolder && !showAll && mPath.startsWith('/') && mPath != FAVORITES &&
            mPath != RECYCLE_BIN && photoProfileId == null) {
            runCatching { FolderReturnCheckpoint.capture(context, mPath) }.getOrNull()
        } else null
        if (mPath == PhotoProfileMediaLoader.PATH || photoProfileId != null) {
            val loader = PhotoProfileMediaLoader(context, mediaFetcher, photoSessionId) { isCancelled }
            val final = loader.loadStaged(photoProfileId, photoAllowPreview && onPhotoResult != null, photoAnchorPath,
                forceScan = manualRefresh) {
                if (!isCancelled) publishProgress(it)
            }
            profileResult = final
            return ArrayList(final.media)
        }
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        val getProperDateTaken = folderSorting and SORT_BY_DATE_TAKEN != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

        val getProperLastModified = folderSorting and SORT_BY_DATE_MODIFIED != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

        val getProperFileSize = folderSorting and SORT_BY_SIZE != 0
        val savedFavorites = FolderRefreshTrace.measure(requestId, "favorite_relationships") { context.getFavoritePaths() }
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
        val ordinaryFolder = onFolderPreview != null && !showAll && mPath.startsWith('/') && mPath != RECYCLE_BIN && mPath != FAVORITES
        // Publish the basic folder catalog before optional video metadata extraction.
        val getVideoDurations = context.config.showThumbnailVideoDuration && !ordinaryFolder
        val scopedAvailable = isRPlus() && !isExternalStorageManager()
        val scopedFolder = MediaQueryScope.shouldUseFolderQuery(
            showAll = showAll,
            path = mPath,
            scopedStorageAvailable = scopedAvailable,
            isOtg = scopedAvailable && mPath.startsWith('/') && context.isPathOnOTG(mPath)
        )
        // The scoped cursor already returns DATE_MODIFIED and DATE_TAKEN.
        // Only user-edited dates need a separate Room lookup.
        val indexedMetadata = scopedAvailable && (showAll || scopedFolder)
        val lastModifieds = if (getProperLastModified && !indexedMetadata && !noMediaFolder) {
            if (!showAll && mPath.startsWith('/')) mediaFetcher.getFolderLastModifieds(mPath) else mediaFetcher.getLastModifieds()
        } else HashMap()
        val dateTakens = when {
            !getProperDateTaken -> HashMap<String, Long>()
            noMediaFolder || scopedFolder -> mediaFetcher.getSelectedFolderDateOverrides(listOf(mPath))
            scopedAvailable && showAll -> mediaFetcher.getCustomDateTakenOverrides()
            !showAll && mPath.startsWith('/') -> mediaFetcher.getFolderDateTakens(mPath)
            else -> mediaFetcher.getDateTakens()
        }

        val scanStarted = GalleryPerfTrace.begin()
        val media = if (showAll) {
            val foldersToScan = mediaFetcher.getFoldersToScan().filter { it != RECYCLE_BIN && it != FAVORITES && !context.config.isFolderProtected(it) }
            // Scoped-storage MediaStore can be queried just once for all folders.
            // Reusing the grouped result avoids O(folderCount) full-store queries.
            val scopedFiles = mediaFetcher.getAndroid11FolderMedia(
                isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens
            )
            val result = FolderMediaScan.load(foldersToScan, cachedMediaFallback,
                cancelled = { isCancelled || mediaFetcher.shouldStop }) { folder ->
                mediaFetcher.getFilesFrom(
                    folder, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens, scopedFiles
                )
            }
            folderFailures = result.failures
            val media = result.media

            mediaFetcher.sortMedia(media, context.config.getFolderSorting(SHOW_ALL))
            media
        } else {
            val indexedFolder = if (noMediaFolder) hashMapOf() else if (scopedFolder) {
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
                val folderCache = cachedMediaFallback.filter { item ->
                    val parent = java.io.File(item.path).parent.orEmpty()
                    MediaStoreFolderKey.of(parent) == folderKey ||
                        (item.type == TYPE_PORTRAITS &&
                            MediaStoreFolderKey.of(java.io.File(parent).parent.orEmpty()) == folderKey)
                }
                mediaFetcher.onFolderProgress = { progress ->
                    if (!isCancelled && onFolderPreview != null && folderCache.isEmpty()) {
                        val merged = progress.presentation(folderCache.filter {
                            it.type and context.config.filterMedia != 0 &&
                                (context.config.shouldShowHidden || !it.name.startsWith('.')) &&
                                (!isPickImage || it.type != TYPE_VIDEOS) && (!isPickVideo || it.type != TYPE_IMAGES)
                        }, ::folderAllowed)
                        mediaFetcher.sortMedia(merged, folderSorting)
                        val grouped = mediaFetcher.groupMedia(merged, pathToUse)
                        delivery.post {
                            if (!isCancelled && progress.accessCurrent()) onFolderPreview.invoke(progress, grouped)
                        }
                    }
                }
                val read = mediaFetcher.readFolder(
                    mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                    getVideoDurations, lastModifieds, dateTakens, indexedFolder)
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
                val presented = read.presentation(fallback, ::allowed)
                mediaFetcher.sortMedia(presented, folderSorting)
                presented
            } else mediaFetcher.getFilesFrom(
                mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                getVideoDurations, lastModifieds, dateTakens, indexedFolder
            )
        }
        folderFailures = folderFailures + mediaFetcher.readFailures
        GalleryPerfTrace.end("media_async_scan", scanStarted, media.size)

        if (ordinaryFolder) {
            val cached = cachedMediaFallback.associateBy { it.path }
            media.forEach { item -> cached[item.path]?.let { old ->
                if (old.modified == item.modified && old.size == item.size) item.videoDuration = old.videoDuration
            } }
        }
        val result = FolderRefreshTrace.measure(requestId, "grouping") { mediaFetcher.groupMedia(media, pathToUse) }
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
            if (isCancelled || error is java.util.concurrent.CancellationException || error is android.os.OperationCanceledException)
                throw java.util.concurrent.CancellationException()
            scanFailure = error
            ArrayList()
        }
    }

    override fun onProgressUpdate(vararg values: PhotoLoadResult) {
        if (!isCancelled) values.forEach { result ->
            if (result.context.isCurrent(context)) onPhotoResult?.invoke(result)
        }
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        if (isCancelled) return
        val error = scanFailure
        if (error != null) { onFailure(error); return }
        val result = profileResult
        if (result != null) {
            if (!result.context.isCurrent(context)) { onFailure(PhotoLoadInvalidatedException()); return }
            if (onPhotoResult != null) onPhotoResult.invoke(result) else callback(media)
        } else {
            if (folderFailures.isNotEmpty()) onPartialFailure(folderFailures)
            callback(media)
            val read = folderReadResult
            if (read != null && onFolderPreview != null && context.config.showThumbnailVideoDuration && !isCancelled) {
                val old = cachedMediaFallback.associateBy { it.path }
                val base = media.filterIsInstance<Medium>().map { item ->
                    val cached = old[item.path]
                    if (cached != null && cached.modified == item.modified && cached.size == item.size)
                        item.copy(videoDuration = cached.videoDuration) else item
                }
                VideoDurationEnricher.enrich(base,
                    current = { !isCancelled && !mediaFetcher.shouldStop && read.accessCurrent() },
                    read = { path ->
                        if (!folderAllowed(path)) 0 else FolderRefreshTrace.measure(requestId, "video_duration") {
                            durationReader(path)
                        }
                    }, publish = { enriched ->
                        val safe = enriched.filter { folderAllowed(it.path) }.toCollection(ArrayList())
                        // Optional details must not re-enter the scan-complete callback,
                        // regroup the catalog, or repeat cache pruning/viewer preparation.
                        delivery.post { if (!isCancelled && read.accessCurrent()) onFolderDetails?.invoke(safe) }
                    })
            }
        }
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
        delivery.removeCallbacksAndMessages(null)
    }
}

