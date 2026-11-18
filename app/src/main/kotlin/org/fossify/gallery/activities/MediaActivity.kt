package org.fossify.gallery.activities

import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.helpers.OxygenClassicActivityMotion

import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.PhotoAccessEvent
import org.fossify.gallery.helpers.mustClearProtectedMedia
import org.fossify.gallery.extensions.unlockPhotoProfile

import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.helpers.FAVORITE_ACCESS_SESSION
import org.fossify.gallery.helpers.FAVORITE_PASSWORD_ENABLED
import org.fossify.gallery.helpers.FAVORITE_PASSWORD_HASH
import org.fossify.gallery.helpers.FAVORITE_PROTECTION_TYPE
import org.fossify.gallery.helpers.FAVORITE_FALLBACK_PASSWORD_HASH
import org.fossify.gallery.helpers.FAVORITE_FALLBACK_PROTECTION_TYPE
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import org.fossify.gallery.helpers.FavoriteAccessPolicy
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.extensions.authenticateFavoritePassword
import org.fossify.gallery.extensions.prepareFavoritePasswordProtection
import org.fossify.gallery.helpers.FOLDER_UNLOCK_SESSION
import org.fossify.gallery.helpers.FolderUnlockSessionManager
import org.fossify.gallery.helpers.ViewerAccessCoordinator
import org.fossify.gallery.helpers.ViewerEntryDiagnostics
import org.fossify.gallery.helpers.DiagnosticLevel
import org.fossify.gallery.helpers.FrameEvidence
import org.fossify.gallery.helpers.MediaPublicationStage
import org.fossify.gallery.helpers.MediaRequestDiagnostics
import org.fossify.gallery.helpers.ViewerScopeType
import org.fossify.gallery.helpers.ViewerReturnDiagnostics
import org.fossify.gallery.helpers.PhotoViewerReturnTarget
import org.fossify.gallery.helpers.MediaTransitionHandoff
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Resource
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Token
import org.fossify.gallery.helpers.PhotoViewerTransition
import org.fossify.gallery.helpers.OxygenClassicMotion
import org.fossify.gallery.helpers.FolderReturnCheckpoint
import org.fossify.gallery.extensions.isThisOrParentExcluded
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.MediaUpdateReason
import org.fossify.gallery.helpers.DisplayedFolderSnapshot
import org.fossify.gallery.helpers.DisplayedSourceStage
import org.fossify.gallery.helpers.FolderCachePreviewAccess
import org.fossify.gallery.helpers.BoundedScanExecutor
import org.fossify.gallery.helpers.DirectoryAccessState
import org.fossify.gallery.helpers.FolderRefreshDiagnostics
import org.fossify.gallery.helpers.GalleryDiagnosticLogger
import org.fossify.gallery.helpers.GalleryScanResult
import org.fossify.gallery.helpers.GalleryScanState
import org.fossify.gallery.helpers.MediaPageBatch
import org.fossify.gallery.helpers.MediaPagePreview
import org.fossify.gallery.helpers.PhotoProfileScope
import org.fossify.gallery.helpers.StagedMediaOrder
import org.fossify.gallery.extensions.getMediaPageCache
import android.app.ActivityOptions
import android.app.SharedElementCallback
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.Future
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.dialogs.ChangeGroupingDialog
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.deleteDBPath
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getHumanizedFilename
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getUpdatedDeletedMedia
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.GalleryBottomBar
import org.fossify.gallery.helpers.OxygenFastScroller
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenProgressStyle
import org.fossify.gallery.helpers.GalleryNavigation
import org.fossify.gallery.helpers.GallerySection
import org.fossify.gallery.helpers.GalleryScreen
import org.fossify.gallery.helpers.GalleryContentView
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenSearchNavigation
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MediaCachePrunePolicy
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.FavoriteMediaLoadRequests
import org.fossify.gallery.helpers.FavoritesReturnCover
import org.fossify.gallery.helpers.FavoritesScrollRestorer
import org.fossify.gallery.helpers.FavoriteMediaLoadPolicy
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.io.IOException

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private val isPhotoProfiles get() = mPath == PhotoProfileMediaLoader.PATH
    private var photoProfileId: String? = null
    private var photoNeedsUnlock = false
    private var photoViewerTransition = false
    private var pendingViewerReturnPath: String? = null
    private var viewerReturnGeneration = 0L
    private var viewerSourceLease: GridViewerSource? = null
    private var viewerSourceTrace: GridViewerSourceTrace? = null
    @Volatile private var viewerLaunchInProgress = false
    private val viewerLaunchGate = org.fossify.gallery.helpers.ViewerLaunchGate()
    private var viewerHandoffTask: java.util.concurrent.Future<*>? = null
    private val viewerHandoffDeadline = org.fossify.gallery.helpers.RefreshWatchdog()
    private var pendingViewerNeighborId: String? = null
    private var viewerFallbackReturning = false
    private data class ViewerLaunchOrigin(val folder: String, val query: String, val photoScope: String?)
    private var viewerLaunchOrigin: ViewerLaunchOrigin? = null
    private fun currentViewerLaunchOrigin() =
        ViewerLaunchOrigin(mPath, mCommittedSearchText, if (isPhotoProfiles) photoDisplayKey() else null)

    private fun viewerLaunchOriginMatches(): Boolean =
        viewerLaunchOrigin?.let { it == currentViewerLaunchOrigin() } != false

    private var viewerReentryObservedForCurrentLaunch = false
    private var viewerReturnMapped = false
    private var viewerReturnLaunch = 0L
    private val viewerResultDelivered get() = transitionSession?.resultAccepted == true
    private var viewerReturn: GridReturn? = null
    private var transitionSession: MediaTransitionSessionCoordinator? = null
    private val viewerDeletions = org.fossify.gallery.helpers.ViewerDeletionLedger()

    private class GridViewerSource(
        val path: String,
        val holder: RecyclerView.ViewHolder,
        val image: ImageView,
        val entry: Token,
        val leases: MediaTransitionSessionCoordinator.Leases,
    ) {
        var nameTimeout: Runnable? = null
        var nameOwned = true
    }

    // Observe the old grid location after Android relinquishes it, without retaining the holder.
    private class GridViewerSourceTrace(
        val path: String,
        val entryId: Long,
        val holderId: Int,
        val image: WeakReference<ImageView>,
    )

    private data class GridReturnPosition(val adapter: MediaAdapter, val version: Long, val index: Int)
    private data class GridReturnTarget(
        val position: GridReturnPosition,
        val holder: RecyclerView.ViewHolder,
        val image: ImageView,
        val drawable: android.graphics.drawable.Drawable,
        val loadedKey: Any,
        val bounds: android.graphics.Rect,
        val visible: android.graphics.Rect,
        val scaleType: ImageView.ScaleType,
        val imageMatrix: Matrix,
    ) {
        fun matches(other: GridReturnTarget?): Boolean = other != null && image === other.image &&
            holder === other.holder && drawable === other.drawable && this == other
    }

    private class GridReturn(
        val path: String,
        val requestId: Long,
        val folder: String,
        val query: String,
        val profileId: String?,
        val session: MediaTransitionSessionCoordinator,
        val operation: Token,
    ) {
        val leases = session.leases()
        var targetPreDraw: androidx.core.view.OneShotPreDrawListener? = null
        var targetRetry: Runnable? = null
        var destroying = false
        var requestedPosition: GridReturnPosition? = null
        var reportedPosition: GridReturnPosition? = null
        var readiness: String? = null
        var origin: FavoritesScrollRestorer.Anchor? = null
        var target: GridReturnTarget? = null
        val released get() = session.state != State.RETURN_PREPARING
        var resultDelivered = false
        var transitionTerminal: String? = null
        var stopObservingFirstFrame: (() -> Unit)? = null
        var firstFrameReported = false
        var stopWaitingForDraw: (() -> Unit)? = null
        var timeout: Runnable? = null
        var deletionCommitPending = false
    }

    private fun isCurrentViewerLaunch(data: Intent?): Boolean =
        viewerReturnLaunch == 0L || data == null ||
            data.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L) == viewerReturnLaunch

    private fun logViewerGridSource(event: String, requestId: Long = 0L) {
        val source = viewerSourceTrace ?: return
        ViewerReturnDiagnostics.event(requestId.takeIf { it != 0L } ?: source.entryId, event) {
            val image = source.image.get()
            ViewerReturnDiagnostics.thumbnailState(image) + mapOf(
                "launchId" to viewerReturnLaunch,
                "sourceMediaId" to GalleryDiagnosticLogger.anonymousId(source.path),
                "holderId" to source.holderId,
                "rebound" to image?.let { it.getTag(R.id.media_bound_path) != source.path },
            )
        }
    }

    private fun retireViewerSourceName(source: GridViewerSource) {
        if (!source.nameOwned) return
        source.leases.release(source.entry, Resource.SOURCE_NAME)
    }

    private fun releaseViewerSourceLease() {
        val source = viewerSourceLease ?: return
        viewerSourceLease = null
        source.leases.releaseAll()
        transitionSession?.clean()
    }

    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        val path = data?.getStringExtra(PhotoViewerTransition.EXTRA_RETURN_PATH)
        if (resultCode != RESULT_OK || path.isNullOrEmpty() || isFinishing || isDestroyed ||
            !isCurrentViewerLaunch(data) || !viewerLaunchOriginMatches() ||
            viewerResultDelivered || viewerReturnMapped) return
        val requestId = data.getLongExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, 0L)
        val operation = MediaTransitionHandoff.read(data) ?: return
        val session = transitionSession ?: return
        if (!session.acceptReenter(operation)) return
        // ActivityTransitionState restores the original exit views before dispatching reentry.
        releaseViewerSourceLease()
        logViewerGridSource("grid_source_reenter", requestId)
        endViewerReturn("superseded")
        viewerReturnGeneration++
        viewerReentryObservedForCurrentLaunch = true
        viewerReturnMapped = false
        pendingViewerReturnPath = path
        val deletedPaths = data?.getStringArrayListExtra(PhotoViewerTransition.EXTRA_DELETED_PATHS)
            .orEmpty().toSet()
        val request = GridReturn(path, requestId, mPath, mCommittedSearchText, photoProfileId, session, operation)
        request.leases.acquire(operation, Resource.TIMEOUT) {
            request.timeout?.let(albumTransitionHandler::removeCallbacks)
            request.timeout = null
        }
        request.leases.acquire(operation, Resource.TARGET_PROBE) {
            request.targetPreDraw?.removeListener()
            request.targetPreDraw = null
            request.targetRetry?.let(albumTransitionHandler::removeCallbacks)
            request.targetRetry = null
        }
        request.deletionCommitPending = viewerDeletions.hasNew(viewerReturnLaunch, deletedPaths)
        viewerReturn = request
        installGridReturnCallback(request)
        if (isPhotoProfiles) photoScroll.clear()
        getMediaAdapter()?.clearRefreshScroll()
        ViewerReturnDiagnostics.event(requestId, "grid_reenter") {
            mapOf("mediaId" to GalleryDiagnosticLogger.anonymousId(path), "launchId" to viewerReturnLaunch,
                "runtimeSessionId" to operation.sessionId, "runtimeOperationId" to operation.operationId,
                "scrollState" to binding.mediaGrid.scrollState,
                "viewerSessionId" to data.getLongExtra(ViewerEntryDiagnostics.EXTRA_VIEWER_SESSION_ID, 0L).takeIf { it != 0L })
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_transition_reenter", 1)
        postponeEnterTransition()
        request.leases.acquire(operation, Resource.WINDOW_POSTPONE) {
            if (!isFinishing && !isDestroyed && !request.destroying) startPostponedEnterTransition()
        }
        request.timeout = Runnable { releaseViewerReturn(request, null, "timeout") }.also {
            albumTransitionHandler.postDelayed(it, PhotoViewerTransition.READY_TIMEOUT_MS)
        }
        if (request.deletionCommitPending) {
            applyViewerDeletedPaths(deletedPaths) {
                if (isViewerReturnCurrent(request)) {
                    request.deletionCommitPending = false
                    awaitViewerReturnTarget(request)
                }
            }
        } else awaitViewerReturnTarget(request)
    }

    /** Apply the confirmed deletion before mapping the return thumbnail. */
    private fun applyViewerDeletedPaths(paths: Set<String>, onCommitted: () -> Unit) {
        val fresh = viewerDeletions.confirm(viewerReturnLaunch, paths, mediaLoadRequests.current())
        if (fresh.isEmpty()) {
            onCommitted()
            return
        }
        mMedia = org.fossify.gallery.helpers.MediaUpdatePolicy.withoutDeletedPaths(mMedia, fresh)
        val adapter = getMediaAdapter()
        if (adapter == null) onCommitted()
        else adapter.removeMediaPaths(fresh, onCommitted)
    }

    private fun isViewerReturnCurrent(request: GridReturn): Boolean =
        viewerReturn === request && request.session.isReturning(request.operation) && !isFinishing && !isDestroyed

    private fun viewerReturnScopeMatches(request: GridReturn): Boolean =
        mPath == request.folder && mCommittedSearchText == request.query && photoProfileId == request.profileId

    private fun viewerReturnPosition(path: String): GridReturnPosition? {
        val adapter = getMediaAdapter() ?: return null
        val position = adapter.media.indexOfFirst { (it as? Medium)?.path == path }
        return GridReturnPosition(adapter, adapter.committedVersion, position)
    }

    private fun awaitViewerReturnTarget(request: GridReturn) {
        if (!isViewerReturnCurrent(request) || request.released) return
        if (!viewerReturnScopeMatches(request)) {
            releaseViewerReturn(request, null, "scope_changed")
            return
        }
        val grid = binding.mediaGrid
        val position = viewerReturnPosition(request.path)
        if (request.reportedPosition != position) {
            request.reportedPosition = position
            ViewerReturnDiagnostics.event(request.requestId, "grid_position") {
                mapOf("index" to position?.index, "version" to position?.version, "scrollState" to grid.scrollState)
            }
        }
        if (position != null && position.index < 0 && displayedMediaIsFinal) {
            releaseViewerReturn(request, null, "target_absent")
            return
        }
        val image = position?.let { findViewerReturnHolder(request.path, it)?.itemView?.findViewById<ImageView>(R.id.medium_thumbnail) }
        if (position != null && position.index >= 0 && !isMediaGridMoving &&
            !grid.isLayoutRequested && !grid.hasPendingAdapterUpdates() &&
            (image == null || !hasUsableViewerReturnBounds(image)) && request.requestedPosition != position) {
            if (request.requestedPosition == null) {
                request.origin = FavoritesScrollRestorer().capture(grid, position.adapter.media.map { (it as? Medium)?.path })
            }
            request.requestedPosition = position
            grid.scrollToPosition(position.index)
            ViewerReturnDiagnostics.event(request.requestId, "grid_position_requested") {
                mapOf("index" to position.index, "version" to position.version)
            }
        }
        request.targetPreDraw = grid.doOnPreDraw {
            request.targetPreDraw = null
            if (!isViewerReturnCurrent(request) || request.released) return@doOnPreDraw
            val target = readViewerReturnTarget(request, position)
            if (target != null) releaseViewerReturn(request, target, "target_ready")
            else request.targetRetry = Runnable {
                request.targetRetry = null
                awaitViewerReturnTarget(request)
            }.also { albumTransitionHandler.postDelayed(it, 16L) }
        }
        grid.postInvalidateOnAnimation()
    }

    private fun releaseViewerReturn(request: GridReturn, target: GridReturnTarget?, reason: String) {
        if (viewerReturn !== request) return
        if (!isViewerReturnCurrent(request)) {
            endViewerReturn("return_no_longer_active")
            return
        }
        if (request.released) return
        request.leases.release(request.operation, Resource.TARGET_PROBE)
        request.timeout?.let(albumTransitionHandler::removeCallbacks)
        request.timeout = null
        if (target == null) {
            pendingViewerReturnPath = null
            if (viewerReturnScopeMatches(request) && (viewerReturnPosition(request.path)?.index ?: -1) < 0) {
                restoreViewerReturnOrigin(request)
            }
        } else {
            if (!request.session.targetReady(request.operation)) return
            request.target = target
            if (request.leases.acquire(request.operation, Resource.TARGET) { target.holder.setIsRecyclable(true) }) {
                target.holder.setIsRecyclable(false)
            }
            if (request.leases.acquire(request.operation, Resource.PUBLICATION) {
                    target.position.adapter.setViewerReturnPublicationHeld(false)
                    // Apply queued updates outside the draw or result callback.
                    albumTransitionHandler.post { releaseMediaGridPublicationIfSafe() }
                }) {
                target.position.adapter.setViewerReturnPublicationHeld(true)
            }
            if (isPhotoProfiles) rememberPhotoScrollAnchor("viewer_return", request.path)
            if (mPath == FAVORITES) favoritesScroll.clear()
            request.timeout = Runnable {
                if (viewerReturn === request) endViewerReturn("lease_timeout")
            }.also { albumTransitionHandler.postDelayed(it, PhotoViewerTransition.READY_TIMEOUT_MS) }
        }
        ViewerReturnDiagnostics.event(request.requestId, "grid_released") {
            mapOf("reason" to reason, "index" to target?.position?.index, "version" to target?.position?.version,
                "bounds" to target?.bounds?.toShortString(), "visible" to target?.visible?.toShortString())
        }
        if (target == null) endViewerReturn(reason)
        else request.leases.release(request.operation, Resource.WINDOW_POSTPONE)
    }

    private fun restoreViewerReturnOrigin(request: GridReturn) {
        val origin = request.origin ?: return
        val grid = binding.mediaGrid
        val manager = grid.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        if (isMediaGridMoving || manager.orientation != origin.orientation) return
        val position = viewerReturnPosition(origin.path)?.index ?: -1
        if (position >= 0) manager.scrollToPositionWithOffset(position, origin.offset)
    }

    private fun onGridReturnTransitionTerminal(launch: Long, cancelled: Boolean) {
        if (launch != viewerReturnLaunch || !viewerReturnMapped) return
        val request = viewerReturn ?: return
        if (!isViewerReturnCurrent(request) || request.transitionTerminal != null) return
        request.transitionTerminal = if (cancelled) "transition_cancel" else "transition_end"
        ViewerReturnDiagnostics.event(request.requestId, if (cancelled) "grid_transition_cancel" else "grid_transition_end")
        logViewerGridSource("grid_source_return_terminal", request.requestId)
        awaitViewerReturnDraw(request)
    }

    private fun observeFirstViewerReturnFrame(request: GridReturn, target: GridReturnTarget) {
        if (!ViewerEntryDiagnostics.enabled(request.requestId) ||
            request.firstFrameReported || request.stopObservingFirstFrame != null) return
        val grid = binding.mediaGrid
        val observer = grid.viewTreeObserver
        if (!observer.isAlive) return
        val launch = viewerReturnLaunch
        val useFrameCommit = Build.VERSION.SDK_INT >= 29 && grid.isHardwareAccelerated
        var awaitingCommit = false
        var frameFields: Map<String, Any?>? = null
        var layerFields: Map<String, Any?>? = null

        val afterDraw = Runnable {
            if (!isViewerReturnCurrent(request) || launch != viewerReturnLaunch ||
                request.stopObservingFirstFrame == null) return@Runnable
            awaitingCommit = false
            val fields = frameFields ?: return@Runnable
            request.firstFrameReported = true
            request.leases.release(request.operation, Resource.FIRST_FRAME)
            ViewerReturnDiagnostics.event(request.requestId, "grid_first_return_frame") { fields }
            ViewerReturnDiagnostics.event(request.requestId, "grid_first_return_layers") { layerFields.orEmpty() }
        }

        val preDraw = ViewTreeObserver.OnPreDrawListener {
            if (!awaitingCommit && isViewerReturnCurrent(request)) {
                awaitingCommit = true
                frameFields = null
                layerFields = null
                if (Build.VERSION.SDK_INT >= 29) observer.registerFrameCommitCallback(afterDraw)
            }
            true
        }
        val draw = ViewTreeObserver.OnDrawListener {
            if (!isViewerReturnCurrent(request) || frameFields != null || !grid.isShown ||
                grid.windowVisibility != View.VISIBLE || grid.isLayoutRequested || grid.hasPendingAdapterUpdates() ||
                !viewerReturnScopeMatches(request) ||
                findViewerReturnHolder(request.path, target.position) !== target.holder) return@OnDrawListener
            // The grid can draw its new viewport while Android still hides the mapped thumbnail.
            val first = (grid.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                ?.findFirstVisibleItemPosition()
            val source = viewerSourceTrace?.image?.get()
            val image = target.image
            frameFields = mapOf(
                "launchId" to launch,
                "ack" to if (useFrameCommit) "frame_commit" else "after_draw",
                "frameEvidence" to FrameEvidence.gridAcknowledgement(useFrameCommit).name,
                "presented" to FrameEvidence.DRAW.presented,
                "drawElapsedMs" to android.os.SystemClock.elapsedRealtime(),
                "firstVisibleIndex" to first,
                "version" to target.position.version,
                "index" to target.position.index,
                "mediaId" to GalleryDiagnosticLogger.anonymousId(request.path),
                "targetBounds" to android.graphics.Rect().takeIf(image::getGlobalVisibleRect)?.toShortString(),
                "targetAlpha" to image.alpha,
                "targetTransitionAlpha" to image.takeIf { Build.VERSION.SDK_INT >= 29 }?.transitionAlpha,
                "targetVisibility" to image.visibility,
                "targetDrawableMatches" to (image.drawable === target.drawable),
                "sourceBoundMediaId" to (source?.getTag(R.id.media_bound_path) as? String)
                    ?.let(GalleryDiagnosticLogger::anonymousId),
                "sourceAlpha" to source?.alpha,
            )
            layerFields = mapOf(
                "launchId" to launch,
                "sourceTransitionAlpha" to source?.takeIf { Build.VERSION.SDK_INT >= 29 }?.transitionAlpha,
                "sourceAttached" to source?.isAttachedToWindow,
                "thumbnail" to ViewerEntryDiagnostics.viewState(image),
                "holder" to ViewerEntryDiagnostics.viewState(target.holder.itemView),
                "grid" to ViewerEntryDiagnostics.viewState(grid),
                "root" to ViewerEntryDiagnostics.viewState(binding.root),
                "decor" to ViewerEntryDiagnostics.viewState(window.decorView),
            )
            if (!useFrameCommit) albumTransitionHandler.post(afterDraw)
        }
        request.stopObservingFirstFrame = {
            request.stopObservingFirstFrame = null
            if (observer.isAlive) {
                observer.removeOnDrawListener(draw)
                if (useFrameCommit) observer.removeOnPreDrawListener(preDraw)
                if (Build.VERSION.SDK_INT >= 29 && useFrameCommit) observer.unregisterFrameCommitCallback(afterDraw)
            }
            albumTransitionHandler.removeCallbacks(afterDraw)
        }
        request.leases.acquire(request.operation, Resource.FIRST_FRAME) {
            request.stopObservingFirstFrame?.invoke()
        }
        // Pre-draw only registers the acknowledgement. OnDraw supplies the actual viewport state.
        if (useFrameCommit) observer.addOnPreDrawListener(preDraw)
        observer.addOnDrawListener(draw)
    }

    private fun awaitViewerReturnDraw(request: GridReturn) {
        val target = request.target ?: return
        if (!isViewerReturnCurrent(request) || !request.resultDelivered ||
            request.transitionTerminal == null || request.stopWaitingForDraw != null) return

        // The result comes from the actual Viewer finish. Wait for a normal draw of
        // the same thumbnail after Android has restored its draw alpha and layout.
        val grid = binding.mediaGrid
        val observer = grid.viewTreeObserver
        val useFrameCommit = Build.VERSION.SDK_INT >= 29 && grid.isHardwareAccelerated
        var awaitingCommit = false
        fun hasSamePixels(): Boolean =
            findViewerReturnHolder(request.path, target.position) === target.holder &&
                target.image.drawable === target.drawable &&
                target.image.getTag(R.id.thumbnail_loaded_key) == target.loadedKey &&
                PhotoViewerReturnTarget.isLoadedFor(request.path,
                    target.image.getTag(R.id.media_bound_path) as? String,
                    target.image.getTag(R.id.thumbnail_request_key), target.loadedKey)

        fun isReadyToDraw(): Boolean = target.matches(readViewerReturnTarget(request, target.position))

        val afterDraw = Runnable {
            if (!isViewerReturnCurrent(request)) return@Runnable
            when {
                !hasSamePixels() || !viewerReturnScopeMatches(request) -> endViewerReturn("target_changed_at_handoff")
                isReadyToDraw() -> {
                    ViewerReturnDiagnostics.event(request.requestId, "grid_target_return_frame") {
                        ViewerReturnDiagnostics.thumbnailState(target.image) + mapOf(
                            "launchId" to viewerReturnLaunch,
                            "ack" to if (useFrameCommit) "frame_commit" else "after_draw",
                            "frameEvidence" to FrameEvidence.gridAcknowledgement(useFrameCommit).name,
                            "presented" to FrameEvidence.DRAW.presented,
                            "index" to target.position.index,
                            "version" to target.position.version,
                            "scaleType" to target.image.scaleType,
                            "matrix" to target.image.imageMatrix.toShortString(),
                            "boundsOnScreen" to target.bounds.toShortString(),
                            "visibleOnScreen" to ViewerReturnDiagnostics.visibleOnScreen(target.image),
                            "drawableSize" to ViewerReturnDiagnostics.drawableSize(target.image),
                        )
                    }
                    endViewerReturn("target_drawn")
                }
                else -> {
                    awaitingCommit = false
                    target.image.postInvalidateOnAnimation()
                }
            }
        }

        fun inspectDraw() {
            if (awaitingCommit || !isViewerReturnCurrent(request)) return
            if (!hasSamePixels() || !viewerReturnScopeMatches(request)) {
                awaitingCommit = true
                albumTransitionHandler.post(afterDraw)
            } else if (isReadyToDraw()) {
                awaitingCommit = true
                if (Build.VERSION.SDK_INT >= 29 && useFrameCommit) observer.registerFrameCommitCallback(afterDraw)
                else albumTransitionHandler.post(afterDraw)
            }
        }

        val preDraw = ViewTreeObserver.OnPreDrawListener {
            inspectDraw()
            true
        }
        val draw = ViewTreeObserver.OnDrawListener { inspectDraw() }
        request.stopWaitingForDraw = {
            if (observer.isAlive) {
                if (useFrameCommit) observer.removeOnPreDrawListener(preDraw)
                else observer.removeOnDrawListener(draw)
                if (Build.VERSION.SDK_INT >= 29 && useFrameCommit) observer.unregisterFrameCommitCallback(afterDraw)
            }
            albumTransitionHandler.removeCallbacks(afterDraw)
        }
        request.leases.acquire(request.operation, Resource.TARGET_DRAW) {
            request.stopWaitingForDraw?.invoke()
            request.stopWaitingForDraw = null
        }
        // Register before the hardware traversal collects commit callbacks.
        // Older or software windows acknowledge after OnDraw returns.
        if (useFrameCommit) observer.addOnPreDrawListener(preDraw)
        else observer.addOnDrawListener(draw)
        target.image.postInvalidateOnAnimation()
    }

    private fun endViewerReturn(reason: String) {
        val request = viewerReturn ?: return
        viewerReturn = null
        request.destroying = reason == "destroyed"
        val terminal = when {
            reason == "destroyed" || reason == "next_launch" || reason == "superseded" ||
                request.transitionTerminal == "transition_cancel" || request.session.viewerMotionOutcome == State.CANCELLED -> State.CANCELLED
            reason == "target_drawn" -> State.COMPLETE
            else -> State.FALLBACK
        }
        if (request.session.outcome == null) request.session.terminate(request.operation, terminal, reason)
        request.leases.releaseAll()
        request.session.clean()
        pendingViewerReturnPath = null
        ViewerReturnDiagnostics.event(request.requestId, "grid_return_closed") {
            ViewerReturnDiagnostics.thumbnailState(request.target?.image) + mapOf(
                "reason" to reason, "transitionTerminal" to request.transitionTerminal, "viewerResult" to request.resultDelivered,
                "firstGridFrameObserved" to request.firstFrameReported)
        }
        logViewerGridSource("grid_source_return_closed", request.requestId)
    }

    private fun hasUsableViewerReturnBounds(image: ImageView, visible: android.graphics.Rect = android.graphics.Rect()): Boolean =
        image.getGlobalVisibleRect(visible) &&
            PhotoViewerReturnTarget.hasUsableVisibleArea(image.width, image.height, visible.width(), visible.height())

    private fun findViewerReturnHolder(path: String, position: GridReturnPosition): RecyclerView.ViewHolder? {
        val grid = binding.mediaGrid
        val adapter = position.adapter
        val index = position.index
        if (index < 0 || grid.adapter !== adapter || adapter.committedVersion != position.version ||
            (adapter.media.getOrNull(index) as? Medium)?.path != path) return null
        return grid.findViewHolderForAdapterPosition(index)?.takeIf {
            it.bindingAdapter === adapter && it.bindingAdapterPosition == index && it.itemView.parent === grid
        }
    }

    private fun readViewerReturnTarget(request: GridReturn, position: GridReturnPosition?): GridReturnTarget? {
        fun report(reason: String, image: ImageView? = null, visible: android.graphics.Rect? = null) {
            if (request.readiness == reason) return
            request.readiness = reason
            ViewerReturnDiagnostics.event(request.requestId, "grid_target") {
                mapOf("readiness" to reason, "index" to position?.index, "version" to position?.version,
                    "boundMatches" to image?.let { it.getTag(R.id.media_bound_path) == request.path },
                    "drawable" to image?.let { it.drawable != null }, "visible" to visible?.toShortString(),
                    "width" to image?.width, "height" to image?.height)
            }
        }
        val grid = binding.mediaGrid
        if (position == null || position.index < 0 || !viewerReturnScopeMatches(request)) {
            report("no_position")
            return null
        }
        if (isMediaGridMoving || grid.isLayoutRequested || grid.isComputingLayout ||
            grid.hasPendingAdapterUpdates() || grid.isAnimating) {
            report("grid_unsettled")
            return null
        }
        val holder = findViewerReturnHolder(request.path, position)
        val image = holder?.itemView?.findViewById<ImageView>(R.id.medium_thumbnail)
        if (image == null) {
            report("holder_unavailable")
            return null
        }
        val drawable = image.drawable
        val loadedKey = image.getTag(R.id.thumbnail_loaded_key)
        if (drawable == null || loadedKey == null ||
            !PhotoViewerReturnTarget.isLoadedFor(request.path, image.getTag(R.id.media_bound_path) as? String,
                image.getTag(R.id.thumbnail_request_key), loadedKey)) {
            report("pixels_unavailable", image)
            return null
        }
        val visible = android.graphics.Rect()
        if (!image.isAttachedToWindow || image.isLayoutRequested || !hasUsableViewerReturnBounds(image, visible)) {
            report("bounds_unavailable", image, visible)
            return null
        }
        if (!image.isShown || image.windowVisibility != View.VISIBLE || image.alpha <= 0f ||
            (Build.VERSION.SDK_INT >= 29 && image.transitionAlpha <= 0f)) {
            report("visibility_unavailable", image, visible)
            return null
        }
        report("ready", image, visible)
        val location = IntArray(2).also(image::getLocationOnScreen)
        val bounds = android.graphics.Rect(location[0], location[1], location[0] + image.width, location[1] + image.height)
        return GridReturnTarget(position, holder, image, drawable, loadedKey, bounds, visible,
            image.scaleType, Matrix(image.imageMatrix))
    }
    private val albumTransitionHandler = Handler()
    private var photoReturnProbeGeneration = 0L
    private var photoLoadCompleted = false
    private var photoLoadedKey: String? = null
    private var photoLoadedRevision = -1L
    private var photoPublishedStage = -1
    private var photoRequestStarted = 0L
    private var photoActiveRequestId = 0L
    private var mediaFastScrollActive = false
    private val photoScroll = FavoritesScrollRestorer()
    private data class GridPublication(val stage: Int, val apply: () -> Unit)
    private val mediaGridPublication = org.fossify.gallery.helpers.ViewerSwipePublicationGate<GridPublication>()

    private val isMediaGridMoving: Boolean
        get() = mediaFastScrollActive || binding.mediaGrid.scrollState != RecyclerView.SCROLL_STATE_IDLE

    private val isMediaGridTouchScrolling: Boolean
        get() = mediaFastScrollActive || binding.mediaGrid.scrollState == RecyclerView.SCROLL_STATE_DRAGGING

    private fun deferMediaGridPublication(stage: Int, apply: () -> Unit): Boolean {
        if ((!isMediaGridTouchScrolling && viewerReturn?.target == null) || getMediaAdapter()?.media?.isNotEmpty() != true) return false
        if (viewerReturn?.target != null) ViewerReturnDiagnostics.event(viewerReturn?.requestId ?: 0L, "grid_publication_deferred") {
            mapOf("stage" to stage)
        }
        mediaGridPublication.onScrollMoving(true)
        return mediaGridPublication.deferIfMoving(GridPublication(stage, apply)) { stage >= it.stage }
    }
    private var photoScrollProfileId: String? = null

    private fun photoDisplayKey(): String = org.fossify.gallery.helpers.PhotoProfileScope.capture(
        this, PhotoProfileRepository.of(this).state().current?.id, photoToken()
    ).toString()

    private fun rememberPhotoScrollAnchor(source: String = "current_view", target: String? = null) {
        if (!isPhotoProfiles || isMediaGridMoving ||
            (target == null && (viewerReturn != null || viewerFallbackReturning))) return
        val adapter = getMediaAdapter() ?: return
        val hadAnchor = photoScroll.pending != null
        photoScroll.remember(binding.mediaGrid, adapter.media.map { (it as? Medium)?.path }, target = target)
        if (photoScroll.pending != null) photoScrollProfileId = photoProfileId
        if (!hadAnchor && photoScroll.pending != null) {
            val anchor = photoScroll.pending
            logPhotoScrollEvent("photo_scroll_anchor_capture", "source" to source,
                "ordinal" to anchor?.ordinal, "offset" to anchor?.offset)
        }
    }

    private fun onPhotoFastScrollStateChanged(active: Boolean) {
        if (active) {
            if (mediaFastScrollActive) return
            mediaFastScrollActive = true
            viewerReturn?.origin = null
            // Fast-scroller calls can leave RecyclerView.scrollState at IDLE.
            // A new thumb gesture supersedes any saved restore request.
            val hadAnchor = isPhotoProfiles && photoScroll.pending != null
            photoScroll.clear()
            photoScrollProfileId = null
            favoritesScroll.clear()
            getMediaAdapter()?.setFastScrollActive(true)
            mediaGridPublication.onScrollMoving(true)
            if (hadAnchor) logPhotoScrollEvent("photo_scroll_anchor_invalidated", "reason" to "fast_scroll")
            if (isPhotoProfiles) logPhotoScrollEvent("photo_fast_scroll_start", "anchorInvalidated" to hadAnchor)
            return
        }
        if (!mediaFastScrollActive) return
        mediaFastScrollActive = false
        if (isPhotoProfiles) logPhotoScrollEvent("photo_fast_scroll_end", "anchorPresent" to (photoScroll.pending != null))
        releaseMediaGridPublicationIfSafe()
    }

    private fun releaseMediaGridPublicationIfSafe() {
        if (isMediaGridTouchScrolling || viewerReturn?.target != null || isFinishing || isDestroyed) return
        val publication = mediaGridPublication.onScrollMoving(false)
        getMediaAdapter()?.setFastScrollActive(false, submitPending = false)
        publication?.apply?.invoke()
        getMediaAdapter()?.submitPendingUpdateAfterFastScroll()
    }

    private fun logPhotoScrollEvent(name: String, vararg fields: Pair<String, Any?>) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        val adapter = getMediaAdapter()
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG,
            "PhotoGrid", name, photoActiveRequestId.takeIf { it != 0L },
            mapOf(
                "profile" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(photoProfileId.orEmpty()),
                "stage" to photoPublishedStage,
                "adapterVersion" to displayedSourceVersion,
                "mediaCount" to (adapter?.media?.size ?: 0),
                "scrollState" to binding.mediaGrid.scrollState,
                "fastScrollActive" to mediaFastScrollActive,
                "anchorPresent" to (photoScroll.pending != null)
            ) + fields.toMap()
        )
    }

    private fun clearPhotoDisplayState() {
        cancelViewerHandoff()
        photoLoadCompleted = false
        photoLoadedKey = null
        photoLoadedRevision = -1L
        photoViewerTransition = false
        photoScroll.clear()
        photoScrollProfileId = null
        org.fossify.gallery.helpers.PhotoViewerSnapshotStore.clear()
        discardInaccessibleGrid()
        mLoadedInitialPhotos = false
    }
    private fun photoToken() = PhotoProfileAccessSessionManager.currentToken(applicationContext)
    private fun unlockPhotoSources() = unlockPhotoProfile(photoProfileId) { reloadPhotoProfile() }
    private fun updatePhotoEmptyState(searchEmpty: Boolean = mLastSearchedText.isNotEmpty()) {
        if (!isPhotoProfiles || binding.mediaEmptyTextPlaceholder.visibility != View.VISIBLE) return
        when {
            photoNeedsUnlock -> {
                binding.mediaEmptyTextPlaceholder.setText(R.string.photo_profiles_locked_title)
                binding.mediaEmptyDescription.setText(R.string.photo_profiles_locked_description)
                binding.mediaEmptyDescription.beVisible()
                binding.mediaEmptyTextPlaceholder2.setText(R.string.photo_password_unlock)
                binding.mediaEmptyTextPlaceholder2.beVisible()
            }
            searchEmpty -> {
                binding.mediaEmptyTextPlaceholder.setText(R.string.photo_profiles_search_empty_title)
                binding.mediaEmptyDescription.setText(R.string.photo_profiles_search_empty_description)
                binding.mediaEmptyDescription.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            }
            else -> {
                binding.mediaEmptyTextPlaceholder.setText(R.string.photo_profiles_no_content_title)
                binding.mediaEmptyDescription.setText(R.string.photo_profiles_no_content_description)
                binding.mediaEmptyDescription.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            }
        }
    }
    private fun onPhotoAccessRevoked() {
        if (!isPhotoProfiles || isDestroyed) return
        runOnUiThread {
            if (isDestroyed || isFinishing || !isPhotoProfiles) return@runOnUiThread
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            org.fossify.gallery.helpers.PhotoProfileMediaCache.invalidate()
            org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate()
            clearPhotoDisplayState()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) getMedia()
        }
    }

    private fun reloadPhotoProfile() {
        getMediaAdapter()?.finishActMode()
        val nextProfile = PhotoProfileRepository.of(this).state().current?.id
        val sameAuthorizedScope = photoProfileId == nextProfile &&
            photoLoadedKey == photoDisplayKey() &&
            photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
            !photoNeedsUnlock && getMediaAdapter() != null && mMedia.isNotEmpty()
        if (!sameAuthorizedScope) {
            // A changed source, preference, or access capability must drop old thumbnails.
            clearPhotoDisplayState()
        } else rememberPhotoScrollAnchor()
        mediaSearchGeneration++
        mLastSearchedText = ""
        mCommittedSearchText = ""
        if (binding.mediaMenu.isSearchOpen) OxygenSearchNavigation.closeInlineSearch(this, binding.mediaMenu)
        photoProfileId = nextProfile
        updateMenuColors()
        getPhotoProfileMedia(if (sameAuthorizedScope) org.fossify.gallery.helpers.PhotoLoadReason.REFRESH
            else org.fossify.gallery.helpers.PhotoLoadReason.SCOPE_CHANGED)
    }

    private fun getPhotoProfileMedia(
        reason: org.fossify.gallery.helpers.PhotoLoadReason = org.fossify.gallery.helpers.PhotoLoadReason.REFRESH,
        previousSortScope: PhotoProfileScope? = null
    ) {
        if (photoToken() != null) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val profile = PhotoProfileRepository.of(this).state().current
        photoProfileId = profile?.id
        val request = mediaLoadRequests.begin()
        photoActiveRequestId = request
        beginMediaDiagnostic(request, reason.name)
        mCurrAsyncTask?.stopFetching("superseded")
        if (photoScroll.pending != null && photoScrollProfileId != profile?.id) {
            photoScroll.clear()
            photoScrollProfileId = null
        }
        val scope = PhotoProfileScope.capture(this, profile?.id, photoToken())
        val requestedKey = scope.toString()
        val sortOnly = previousSortScope != null && scope != null &&
            previousSortScope.copy(sorting = scope.sorting) == scope
        val retainedKey = if (sortOnly) previousSortScope.toString() else requestedKey
        val retainGrid = reason != org.fossify.gallery.helpers.PhotoLoadReason.SCOPE_CHANGED &&
            !photoNeedsUnlock && photoLoadedKey == retainedKey &&
            (isMediaGridMoving || photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current()) &&
            getMediaAdapter() != null && mMedia.isNotEmpty()
        photoNeedsUnlock = false
        mediaGridPublication.clear()
        photoPublishedStage = -1
        photoRequestStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        if (retainGrid) {
            rememberPhotoScrollAnchor()
        } else {
            getMediaAdapter()?.finishActMode()
            discardInaccessibleGrid()
            photoLoadedKey = null
        }
        photoLoadCompleted = false
        // A viewer opened during refresh must not consume the previous final snapshot.
        displayedMediaIsFinal = false
        org.fossify.gallery.helpers.PhotoViewerSnapshotStore.clear()
        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate()
        mLoadedInitialPhotos = true
        updateMenuColors()
        if (profile == null) {
            mediaDiagnostic(request)?.skip("profile_missing")
            photoLoadCompleted = true
            photoLoadedKey = photoDisplayKey()
            photoLoadedRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
            mIsGettingMedia = false
            setMediaLoading(false)
            OxygenProgressStyle.setLoading(binding.loadingIndicator, false)
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.setText(R.string.photo_profiles_empty_title)
            binding.mediaEmptyDescription.setText(R.string.photo_profiles_empty_description)
            binding.mediaEmptyDescription.beVisible()
            binding.mediaEmptyTextPlaceholder.beVisible()
            binding.mediaEmptyTextPlaceholder2.beGone()
            binding.mediaFastscroller.beGone()
            return
        }
        val requestedToken = photoToken()
        val order = if (retainGrid && sortOnly) StagedMediaOrder() else null
        if (order != null && scope != null) {
            photoLoadedKey = requestedKey
            val rows = mMedia.filterIsInstance<Medium>().mapTo(ArrayList()) { it.copy() }
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    order.sort(rows, scope.sorting, MediaFetcher(applicationContext))
                }
                if (mediaLoadRequests.isCurrent(request) && photoDisplayKey() == requestedKey &&
                    photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
                    photoPublishedStage < 0 && !isFinishing && !isDestroyed) {
                    publishMedia(ArrayList<ThumbnailItem>(rows), true, request)
                }
            }
        }
        mIsGettingMedia = true
        if (reason == org.fossify.gallery.helpers.PhotoLoadReason.REFRESH) {
            binding.mediaRefreshLayout.isRefreshing = true
        }
        setMediaLoading(true)
        binding.mediaEmptyTextPlaceholder.beGone()
        binding.mediaEmptyDescription.beGone()
        binding.mediaEmptyTextPlaceholder2.beGone()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, PhotoProfileMediaLoader.PATH,
            showAll = false, photoProfileId = profile.id, photoSessionId = requestedToken, requestId = request,
            manualRefresh = reason == org.fossify.gallery.helpers.PhotoLoadReason.REFRESH,
            diagnosticLoadReason = when (reason) {
                org.fossify.gallery.helpers.PhotoLoadReason.ENTER -> "INITIAL"
                org.fossify.gallery.helpers.PhotoLoadReason.REFRESH -> "REFRESH"
                org.fossify.gallery.helpers.PhotoLoadReason.SCOPE_CHANGED -> "CONFIG_SWITCH"
            },
            photoAllowPreview = !retainGrid,
            photoAnchorPath = photoScroll.pending?.path,
            photoOrder = order,
            onPhotoResult = photoResult@{ result ->
                // The shared worker/store still publishes every validated stage
                // to the viewer. A stopped grid needs only the final snapshot.
                if (photoViewerTransition && result.stage != org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE)
                    return@photoResult
                submitPhotoGridResult(result, request, requestedKey)
            },
            onFailure = { error ->
                if (mediaLoadRequests.isCurrent(request)) mediaGridPublication.clear()
                if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed) {
                    if (error is org.fossify.gallery.helpers.PhotoLoadInvalidatedException) {
                        getPhotoProfileMedia(org.fossify.gallery.helpers.PhotoLoadReason.ENTER)
                    } else if (mMedia.isNotEmpty() && photoDisplayKey() == requestedKey) {
                        mIsGettingMedia = false
                        setMediaLoading(false)
                        OxygenProgressStyle.setLoading(binding.loadingIndicator, false)
                        binding.mediaRefreshLayout.isRefreshing = false
                        toast(R.string.failed_to_load_media)
                    } else {
                        publishMedia(arrayListOf(), false, request)
                        binding.mediaEmptyTextPlaceholder.setText(R.string.failed_to_load_media)
                        binding.mediaEmptyDescription.beGone()
                    }
                }
            }) { /* Photos publish through the stage-aware callback above. */ }
            .also {
                it.requestDiagnostics = mediaDiagnostic(request)
                it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR)
            }
    }

    internal fun submitPhotoGridResult(result: org.fossify.gallery.helpers.PhotoLoadResult, request: Long, requestedKey: String) {
        if (!mediaLoadRequests.isCurrent(request) || !result.context.isCurrent(applicationContext) ||
            photoDisplayKey() != requestedKey || result.stage.ordinal < photoPublishedStage) return
        photoPublishedStage = result.stage.ordinal
        val apply = { applyPhotoGridResult(result, request, requestedKey) }
        val replaced = mediaGridPublication.hasPending
        if (!result.access.needsUnlock && deferMediaGridPublication(result.stage.ordinal + 1, apply)) {
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                if (replaced) "photo_grid_publication_replaced" else "photo_grid_publication_deferred")
            logPhotoScrollEvent("photo_grid_publication_deferred", "stage" to result.stage.name,
                "replaced" to replaced)
        } else {
            mediaGridPublication.clear()
            apply()
        }
    }

    private fun applyPhotoGridResult(result: org.fossify.gallery.helpers.PhotoLoadResult, request: Long, requestedKey: String) {
        val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed &&
            result.context.isCurrent(applicationContext) && photoDisplayKey() == requestedKey &&
            result.stage.ordinal >= photoPublishedStage) {
            photoPublishedStage = result.stage.ordinal
            val accessChanged = photoNeedsUnlock != result.access.needsUnlock
            photoNeedsUnlock = result.access.needsUnlock
            if (photoNeedsUnlock && accessChanged) discardInaccessibleGrid()
            val complete = result.stage == org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE
            logPhotoScrollEvent("photo_grid_publication_committed", "stage" to result.stage.name,
                "mediaCount" to result.media.size)
            publishMediaSnapshot(ArrayList<ThumbnailItem>(result.media), !complete, request, null, null,
                select = { result.context.isCurrent(applicationContext) && photoDisplayKey() == requestedKey },
                diagnosticStage = when (result.stage) {
                    org.fossify.gallery.helpers.PhotoLoadStage.CACHE_PREVIEW -> MediaPublicationStage.PREVIEW
                    org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE -> MediaPublicationStage.FINAL
                    else -> MediaPublicationStage.PARTIAL
                })
            if (accessChanged) refreshMenuItems()
            if (!complete) {
                photoLoadedKey = requestedKey
                photoLoadedRevision = result.context.revision
                mIsGettingMedia = true
                setMediaLoading(true)
            }
            if (binding.mediaEmptyTextPlaceholder.isVisible()) updatePhotoEmptyState()
            if (result.media.isNotEmpty() && photoRequestStarted != 0L) {
                val started = photoRequestStarted
                photoRequestStarted = 0L
                binding.mediaGrid.postOnAnimation {
                    if (mediaLoadRequests.isCurrent(request)) org.fossify.gallery.helpers.GalleryPerfTrace.end(
                        "photo_first_frame", started, result.media.size)
                }
            }
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.end("photo_grid_main_publish", started, result.media.size)
    }
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    // Null means a single folder or external picker, not a root navigation page.
    private var mGallerySection: GallerySection? = null
    private val hasFixedBrowseSwitch: Boolean
        get() = org.fossify.gallery.helpers.ToolbarQuickActions
            .hasFixedBrowseSwitch(mPath, mGallerySection)
    private var mLoadedInitialPhotos = false
    private var mShowLoadingIndicator = true
    private var mWasFullscreenViewOpen = false
    private var pendingFolderReturn: FolderReturnCheckpoint? = null
    private data class RetainedFolderScan(
        val request: Long, val task: GetMediaAsynctask, val checkpoint: FolderReturnCheckpoint
    )
    private var retainedFolderScan: RetainedFolderScan? = null
    private val detachedFolderPublication = org.fossify.gallery.helpers.ViewerSwipePublicationGate<GridPublication>()

    private fun detachFolderViewerScan(): Boolean {
        if (!mWasFullscreenViewOpen || mShowAll || isFinishing ||
            folderUnlockSessionId != null || config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) return false
        val task = mCurrAsyncTask ?: return false
        val checkpoint = task.viewerScanCheckpoint ?: return false
        if (checkpoint.folder != mPath || task.isCancelled || !task.viewerFolderUnchanged ||
            !mediaLoadRequests.isCurrent(mediaRefreshRequest) || !canRejoinFolderScan(mediaRefreshRequest)) return false
        retainedFolderScan = RetainedFolderScan(mediaRefreshRequest, task, checkpoint)
        detachedFolderPublication.onScrollMoving(true)
        return true
    }

    private fun canRejoinFolderScan(request: Long): Boolean {
        val terminal = mediaScanTerminals.result(request)
        return terminal == null || terminal.complete && completedMediaRequest == request &&
            displayedMediaRequest == request
    }

    private fun resumeFolderViewerScan(scan: RetainedFolderScan, unchanged: Boolean): Boolean {
        if (retainedFolderScan !== scan) return false
        retainedFolderScan = null
        val pending = detachedFolderPublication.onScrollMoving(false)
        val accessCurrent = scan.task.pageAccessCurrent?.invoke() == true
        if (!unchanged || scan.task !== mCurrAsyncTask || scan.task.isCancelled ||
            !mediaLoadRequests.isCurrent(scan.request) || !canRejoinFolderScan(scan.request) ||
            !scan.task.viewerFolderUnchanged || scan.checkpoint.revision != org.fossify.gallery.helpers.FolderContentRevision.current() || !accessCurrent) {
            scan.task.stopFetching("viewer_scan_invalidated")
            mediaLoadRequests.invalidate()
            mediaScanGeneration++
            if (!accessCurrent) discardInaccessibleGrid()
            return false
        }
        if (pending != null) {
            pending.apply()
        } else if (!displayedMediaIsFinal && mediaScanTerminals.result(scan.request)?.complete == true) {
            // The source was accepted before pause. Its Adapter submission may
            // have expired while the grid was stopped, so resubmit those rows.
            setupAdapter {
                if (mediaLoadRequests.isCurrent(scan.request)) displayedMediaIsFinal = true
            }
        }
        checkLastMediaChanged()
        return true
    }
    private var mLastSearchedText = ""
    /** Query corresponding to the adapter's last committed visible-order snapshot. */
    private var mCommittedSearchText = ""
    private var mediaPollInitialized = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    @Volatile private var mediaScanGeneration = 0L
    private val mediaLoadRequests = FavoriteMediaLoadRequests()
    private val mediaSortOperations = org.fossify.gallery.helpers.MediaSortOperationGate()
    private var completedMediaRequest = -1L
    // Only paths whose parent folder is unprotected or has passed the normal
    // folder-lock flow may enter the favorites cache or scan for this Activity.
    private var favoriteAccessPaths: Set<String>? = null
    @Volatile private var favoriteCatalogRelationshipPaths: Set<String>? = null
    private var resolvingFavoriteAccess = false
    private var favoritesNeedReload = false
    private val favoritesScroll = FavoritesScrollRestorer()
    private var favoritesReturnCover: FavoritesReturnCover? = null
    private var displayedMediaRequest = -1L
    private var displayedMediaIsFinal = false
    private var mediaPagePreview = MediaPagePreview()
    private var mediaPageOrder = StagedMediaOrder()
    private var mediaPagePreviewJob: Job? = null
    private var mediaPagePendingAccess: (() -> Boolean)? = null
    private var mediaPageAccess: (() -> Boolean)? = null
    private var mediaPageRequest = -1L
    private var firstAdapterCommit = -1L
    private var firstThumbnailResourceReady = -1L
    private var firstThumbnailNotVisible = -1L
    private var firstThumbnailDraw = -1L
    private var mediaPageOpenedAt = 0L
    private var mediaSearchGeneration = 0L
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredMarkFavoriteItems = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0

    private val binding by viewBinding(ActivityMediaBinding::inflate)
    private var mediaHeaderProgress: com.google.android.material.progressindicator.LinearProgressIndicator? = null

    private fun setupMediaProgressIndicator() {
        if (mediaHeaderProgress != null) return
        mediaHeaderProgress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            OxygenProgressStyle.apply(this@MediaActivity, config, this)
        }.also { indicator ->
            binding.mediaMenu.addView(indicator, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OxygenProgressStyle.thicknessPx(this)
            ))
        }
    }

    private var mediaRefreshRequest = -1L
    private var diagnosticMediaRequest: MediaRequestDiagnostics? = null

    private fun mediaDiagnostic(request: Long) = diagnosticMediaRequest?.takeIf { it.generation == request }

    private fun beginMediaDiagnostic(request: Long, trigger: String) {
        val previous = diagnosticMediaRequest
        previous?.skipIfUnadmitted("superseded")
        diagnosticMediaRequest = null
        if (!GalleryDiagnosticLogger.isEnabled()) return
        val source = when {
            isPhotoProfiles -> ViewerScopeType.PHOTO_PROFILE
            mShowAll -> ViewerScopeType.ALL_MEDIA
            mPath == FAVORITES -> ViewerScopeType.FAVORITES
            mPath == RECYCLE_BIN -> ViewerScopeType.RECYCLE_BIN
            else -> ViewerScopeType.FOLDER
        }
        val locator = if (isPhotoProfiles) photoProfileId.orEmpty() else if (mShowAll) SHOW_ALL else mPath
        val revision = when (source) {
            ViewerScopeType.PHOTO_PROFILE -> org.fossify.gallery.helpers.PhotoContentRevision.current()
            ViewerScopeType.FAVORITES -> favoriteRelationshipRevision.get()
            ViewerScopeType.RECYCLE_BIN -> org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()
            ViewerScopeType.FOLDER -> org.fossify.gallery.helpers.FolderContentRevision.current()
            else -> null
        }
        diagnosticMediaRequest = MediaRequestDiagnostics.start(
            request, source, locator, revision, trigger,
            previous?.id, android.os.SystemClock::elapsedRealtime
        ) { event, id, fields ->
            GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaPipeline", event, id, fields)
        }
    }
    private val mediaRefreshWatchdog = org.fossify.gallery.helpers.RefreshWatchdog()
    /** A deadline spans cache lookup, queue wait and scan, including ALL files. */
    private fun armMediaRefreshDeadline(request: Long) {
        if (isPhotoProfiles) return
        mediaRefreshWatchdog.stop()
        mediaRefreshWatchdog.arm {
            if (isDestroyed || isFinishing || !mediaLoadRequests.isCurrent(request)) return@arm
            org.fossify.gallery.helpers.FolderRefreshDiagnostics.event(
                request, if (mShowAll) "all_files_timeout" else "folder_timeout"
            )
            // A deadline may only retire the request when it wins the terminal gate.
            if (!renderMediaScanState(request, org.fossify.gallery.helpers.GalleryScanResult(
                    org.fossify.gallery.helpers.GalleryScanState.TIMED_OUT))) return@arm
            mediaDiagnostic(request)?.skipIfUnadmitted("timed_out")
            mCurrAsyncTask?.reportTimedOut()
            mediaLoadRequests.invalidate()
            mediaScanGeneration++
            mCurrAsyncTask?.stopFetching()
            mIsGettingMedia = false
            mediaChangeGate.scanFinished(success = false)
            setMediaLoading(false)
            binding.mediaRefreshLayout.isRefreshing = false
            if (mPath == FAVORITES) {
                favoritesReturnCover?.showError(::retryFavoritesReturn)
                toast(R.string.folder_refresh_timeout)
            } else {
                val hasContent = getMediaAdapter()?.media?.any { it is Medium } == true
                org.fossify.gallery.helpers.MediaLoadFailure.present(
                    this, binding.mediaEmptyTextPlaceholder, binding.mediaEmptyDescription,
                    binding.mediaEmptyTextPlaceholder2, hasContent,
                    java.util.concurrent.TimeoutException("Media scan deadline exceeded")
                ) { getMedia() }
                if (!hasContent) binding.mediaFastscroller.beGone()
            }
        }
    }

    private fun renderMediaScanState(request: Long, result: org.fossify.gallery.helpers.GalleryScanResult? = null): Boolean {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return false
        if (result != null && !mediaScanTerminals.finish(request, result)) return false
        mIsGettingMedia = result == null
        setMediaLoading(result == null)
        if (result != null) binding.mediaRefreshLayout.isRefreshing = false
        return true
    }

    private fun setMediaLoading(loading: Boolean) {
        // A cached preview is not a new request and cannot restart a deadline.
        if (!loading) mediaRefreshWatchdog.stop()
        if (isDestroyed || isFinishing) return
        // Adapter existence does not mean the initial result contains media. Keep
        // central feedback until a real, already-filtered Medium is published;
        // after that, ongoing scans use the quiet header strip.
        val hasMedia = getMediaAdapter()?.media?.any { it is Medium } == true
        updateMediaFastScrollerVisibility(hasMedia)
        val refreshing = binding.mediaRefreshLayout.isRefreshing
        val central = loading && mGallerySection == null && !hasMedia && !refreshing
        if (loading && !central) OxygenProgressStyle.dispose(binding.loadingIndicator)
        else OxygenProgressStyle.setLoading(binding.loadingIndicator, central)
        mediaHeaderProgress?.apply {
            OxygenProgressStyle.apply(this@MediaActivity, config, this)
            // Background discovery after content is visible stays quiet. The
            // swipe-refresh indicator carries explicit refresh feedback, while
            // GalleryBottomBar can still reveal this strip for its bounded
            // first-draw fallback through its own loadingFrame callback.
            OxygenProgressStyle.setLoading(this, false)
        }
    }

    private var mediaFastScrollerUpdateGeneration = 0L

    /** Use the range of the list currently laid out by RecyclerView, including partial scans. */
    private fun updateMediaFastScrollerVisibility(hasContent: Boolean = mMedia.any { it is Medium }) {
        val generation = ++mediaFastScrollerUpdateGeneration
        val empty = binding.mediaEmptyTextPlaceholder.isVisible()
        if (!hasContent || empty) OxygenFastScroller.setRangeAvailable(binding.mediaFastscroller, false)
        binding.mediaGrid.doOnPreDraw {
            if (generation != mediaFastScrollerUpdateGeneration || isFinishing || isDestroyed) {
                return@doOnPreDraw
            }
            val vertical = binding.mediaFastscroller.fastScrollDirection ==
                com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val hasScrollableRange = org.fossify.gallery.helpers.MediaFastScrollerPolicy.hasScrollableRange(
                vertical = vertical,
                verticalRange = binding.mediaGrid.computeVerticalScrollRange(),
                verticalExtent = binding.mediaGrid.computeVerticalScrollExtent(),
                horizontalRange = binding.mediaGrid.computeHorizontalScrollRange(),
                horizontalExtent = binding.mediaGrid.computeHorizontalScrollExtent(),
            )
            val ready = org.fossify.gallery.helpers.MediaFastScrollerPolicy.isReady(
                hasContent = hasContent,
                hasScrollableRange = hasScrollableRange,
                emptyStateVisible = empty,
            )
            OxygenFastScroller.setRangeAvailable(binding.mediaFastscroller, ready)
        }
    }

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
        private val mediaPageCacheExecutor = BoundedScanExecutor("gallery-page-cache", workers = 1, queued = 2)
    }

    private var folderUnlockSessionId: String? = null
    private var favoriteSessionId: String? = null
    private var favoriteOwnerId = java.util.UUID.randomUUID().toString()
    private var favoriteAuthGeneration = 0L
    private var folderAuthGeneration = 0L
    private var favoritePublicationGeneration = 0L
    private var recyclePublicationGeneration = 0L
    private var favoritePublicationTask: Future<*>? = null
    private var sourceProtectionRefreshPending = false
    private val favoriteListenerId = java.util.UUID.randomUUID().toString()
    private val favoriteProtectionListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val sourceProtectionChanged = org.fossify.gallery.helpers.PhotoProtectionChanges.credentialsChanged(key)
        val visibilityChanged = key == org.fossify.gallery.helpers.SHOW_HIDDEN_MEDIA ||
            key == org.fossify.gallery.helpers.EXCLUDED_FOLDERS ||
            (key == PhotoProfileRepository.KEY && photoLoadedKey != photoDisplayKey()) || key == "photo_source_bindings"
        if (isPhotoProfiles && sourceProtectionChanged) {
            org.fossify.gallery.helpers.PhotoProfileMediaCache.invalidate()
            org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate()
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            clearPhotoDisplayState()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) getMedia()
            return@OnSharedPreferenceChangeListener
        }
        if (isPhotoProfiles && visibilityChanged) {
            org.fossify.gallery.helpers.PhotoProfileMediaCache.invalidate()
            org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate()
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            clearPhotoDisplayState()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) getMedia()
            return@OnSharedPreferenceChangeListener
        }
        if (key == FAVORITE_PASSWORD_ENABLED || key == FAVORITE_PASSWORD_HASH || key == FAVORITE_PROTECTION_TYPE) {
            FavoriteAccessSessionManager.revokeAll()
        } else if (key == FAVORITE_FALLBACK_PASSWORD_HASH || key == FAVORITE_FALLBACK_PROTECTION_TYPE) {
            FavoriteAccessSessionManager.revokeAll()
        } else if (key == null || key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_HASH) ||
            key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_TYPE)) {
            folderAuthGeneration++
            FolderUnlockSessionManager.revokeAll()
            FavoriteAccessSessionManager.revokeAll()
            if (folderUnlockSessionId != null || mPath != FAVORITES) {
                discardInaccessibleGrid()
                finish()
            }
            favoriteSessionId?.let {
                sourceProtectionRefreshPending = true
                FavoriteAccessSessionManager.revokeSession(it)
            }
        }
    }
    private val favoriteObserverLock = Any()
    @Volatile private var favoriteObserverDisposed = false
    private val favoriteRelationshipRevision = java.util.concurrent.atomic.AtomicLong()
    private var displayedFavoriteRelationshipRevision = -1L
    private var favoriteLoadRevision = -1L
    @Volatile private var favoriteInteractiveSnapshot: org.fossify.gallery.helpers.FavoriteInteractiveSnapshot? = null
    @Volatile private var observedFavoriteRelationships: Set<String>? = null
    private var favoriteObservedTracker: androidx.room.InvalidationTracker? = null
    private val favoriteDatabaseObserver = object : androidx.room.InvalidationTracker.Observer("favorites") {
        override fun onInvalidated(tables: Set<String>) {
            if (favoriteObserverDisposed) return
            val token = favoriteSessionId ?: return
            val app = applicationContext
            ensureBackgroundThread {
                val current = app.getFavoritePaths().toSet()
                synchronized(favoriteObserverLock) {
                    val previous = observedFavoriteRelationships
                    if (previous == current) return@ensureBackgroundThread
                    observedFavoriteRelationships = current
                    // The first observation establishes a baseline; it does not
                    // prove a membership change since this Activity opened.
                    if (previous != null) favoriteRelationshipRevision.incrementAndGet()
                }
                val captured = FavoriteAccessSessionManager.getCapturedPaths(token).orEmpty()
                FavoriteAccessSessionManager.retainFavorites(token, current)
                if (current.any { it !in captured }) {
                    binding.root.post {
                        if (token == favoriteSessionId && !isFinishing && !isDestroyed) {
                            sourceProtectionRefreshPending = true
                            FavoriteAccessSessionManager.revokeSession(token)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Folder navigation uses Oxygen Classic's bounded page-level animation;
        // no scan or thumbnail readiness is part of the Activity transition.
        OxygenClassicActivityMotion.install(this)
        setContentView(binding.root)
        org.fossify.gallery.helpers.OxygenClassicEdgeGlow.install(binding.root, this)
        GalleryBottomBar.prepareRootSurface(this, binding.root)
        setupMediaProgressIndicator()
        PhotoProfileAccessSessionManager.addListener(favoriteListenerId) { event ->
            if (event.mustClearProtectedMedia()) onPhotoAccessRevoked()
        }
        binding.mediaRefreshLayout.setColorSchemeColors(getProperPrimaryColor())
        binding.mediaRefreshLayout.isEnabled = true

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        binding.mediaRefreshLayout.setOnRefreshListener {
            binding.mediaRefreshLayout.isRefreshing = false
            getMedia(manualRefresh = true)
        }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
            if (isPhotoProfiles) {
                photoProfileId = PhotoProfileRepository.of(this).state().current?.id
                if (!config.hasCustomSorting(mPath)) config.saveCustomSorting(mPath, SORT_BY_DATE_TAKEN or SORT_DESCENDING)
            }
            folderUnlockSessionId = intent.getStringExtra(FOLDER_UNLOCK_SESSION)
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        favoritesScroll.read(savedInstanceState)
        if (isPhotoProfiles && photoToken() == null) {
            photoScroll.read(savedInstanceState)
            if (photoScroll.pending != null) photoScrollProfileId = photoProfileId
        }
        if (mPath == FAVORITES && savedInstanceState?.getBoolean("favoritesReturnPending") == true) {
            showFavoritesReturnCover()
        }
        binding.mediaGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (isPhotoProfiles) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        photoScroll.clear()
                        photoScrollProfileId = null
                    }
                }
                if (isMediaGridTouchScrolling) mediaGridPublication.onScrollMoving(true)
                else releaseMediaGridPublicationIfSafe()
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    viewerReturn?.origin = null
                    favoritesScroll.clear()
                    if (!isPhotoProfiles) photoScroll.clear()
                }
            }
        })
        getSharedPrefs().registerOnSharedPreferenceChangeListener(favoriteProtectionListener)
        if (mPath == FAVORITES) {
            val app = applicationContext
            ensureBackgroundThread {
                synchronized(favoriteObserverLock) {
                    if (!favoriteObserverDisposed) {
                        favoriteObservedTracker = GalleryDatabase.getInstance(app).invalidationTracker.also {
                            it.addObserver(favoriteDatabaseObserver)
                        }
                    }
                }
            }
            favoriteOwnerId = savedInstanceState?.getString("favoriteOwnerId") ?: favoriteOwnerId
            favoriteSessionId = FavoriteAccessSessionManager.tokenForOwner(favoriteOwnerId)
            favoriteAccessPaths = FavoriteAccessSessionManager.getCapturedPaths(favoriteSessionId)
            // No old shared thumbnails may survive process death or a fresh entry.
            mMedia = ArrayList()
            FavoriteAccessSessionManager.addRevocationListener(favoriteListenerId) { revoked ->
                if (revoked == favoriteSessionId) binding.root.post {
                    if (isFinishing || isDestroyed || revoked != favoriteSessionId) return@post
                    prepareFavoritesReturnCover()
                    showFavoritesReturnCover()
                    discardInaccessibleGrid()
                    if (FavoriteAccessSessionManager.isActive(revoked)) {
                        favoriteAccessPaths = FavoriteAccessSessionManager.getCapturedPaths(revoked)
                        favoritesNeedReload = true
                        mLoadedInitialPhotos = false
                        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            favoritesNeedReload = false
                            getMedia()
                        }
                    } else if (sourceProtectionRefreshPending) {
                        sourceProtectionRefreshPending = false
                        favoriteSessionId = null
                        favoriteAccessPaths = null
                        favoritesNeedReload = true
                        mLoadedInitialPhotos = false
                        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            favoritesNeedReload = false
                            getMedia()
                        }
                    } else finish()
                }
            }
        }

        val isPicker = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent || isSetWallpaperIntent()
        mGallerySection = if (isPicker) null else when (mPath) {
            // Favorite media is a pinned virtual album in All, not the bottom
            // Favorites tab (which exclusively contains favorite folders).
            PhotoProfileMediaLoader.PATH -> GallerySection.PHOTOS
            FAVORITES, "" -> GallerySection.ALL
            else -> null
        }
        if (mGallerySection == GallerySection.FAVORITES ||
            (savedInstanceState == null && mGallerySection != null)) {
            // The legacy shared list can belong to the previous root tab.
            // Never retain favorite thumbnails across Activity recreation.
            mMedia = ArrayList()
        }
        setupOptionsMenu()
        refreshMenuItems()
        binding.mediaMenu.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateMenuColors()
        }
        storeStateVariables()
        if (mGallerySection != null) {
            GalleryBottomBar.attach(this, binding.root,
                GalleryBottomBar.Tab.valueOf(mGallerySection!!.name),
                loadingFrame = { mediaHeaderProgress?.let { indicator ->
                    OxygenProgressStyle.setLoading(indicator, true)
                    OxygenProgressStyle.revealPending(indicator)
                } },
                frameState = { when {
                    binding.mediaGrid.isShown && binding.mediaGrid.childCount > 0 -> "content"
                    binding.mediaEmptyTextPlaceholder.isShown -> "empty_or_error"
                    mediaHeaderProgress?.isShown == true -> "loading"
                    else -> null
                } }
            ) { tab ->
                getMediaAdapter()?.finishActMode()
                val section = GallerySection.valueOf(tab.name)
                openGalleryRoot(section)
            }
            val bottom = resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
            binding.mediaGrid.setPadding(
                binding.mediaGrid.paddingLeft, binding.mediaGrid.paddingTop,
                binding.mediaGrid.paddingRight, bottom
            )
            binding.mediaGrid.clipToPadding = false
        }
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mediaMenu),
            padBottomImeAndSystem = listOf(binding.mediaGrid)
        )

        if (mShowAll) {
            registerFileUpdateListener()
        }

        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            if (isPhotoProfiles) {
                if (photoNeedsUnlock) unlockPhotoSources()
                return@setOnClickListener
            }
            showFilterMediaDialog()
        }

        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (viewerResultDelivered) releaseViewerSourceLease()
        clearRevokedMediaPage()
        if (mPath == FAVORITES && favoritesReturnCover?.covering == true) showFavoritesReturnCover()
        updateMenuColors()
        mediaHeaderProgress?.let { OxygenProgressStyle.apply(this, config, it) }
        GalleryBottomBar.recolor(this)
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
            getMediaAdapter()?.updateShowFileTypes(config.showThumbnailFileTypes)
        }

        if (mStoredTextColor != getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getMediaAdapter()?.updatePrimaryColor()
        }

        if (mPath == FAVORITES && favoritesNeedReload) {
            favoritesNeedReload = false
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            // tryLoadGallery() below owns the single permission-gated reload.
            // A second request here can invalidate the cache callback before it
            // publishes data, leaving recovery dependent on the later scan.
        }

        if (
            mStoredThumbnailSpacing != config.thumbnailSpacing
            || mStoredRoundedCorners != config.fileRoundedCorners
            || mStoredMarkFavoriteItems != config.markFavoriteItems
        ) {
            binding.mediaGrid.adapter = null
            setupAdapter()
        }

        refreshMenuItems()

        binding.mediaFastscroller.updateColors(primaryColor)
        styleFastScroller()
        binding.mediaRefreshLayout.isEnabled = true
        getMediaAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(OxygenThemeEngine.snapshot(this, config).interactiveAccent)
        binding.mediaEmptyDescription.setTextColor(OxygenThemeEngine.snapshot(this, config).secondaryText)
        binding.mediaEmptyTextPlaceholder2.bringToFront()

        // Consume the viewer transition exactly once. A regular folder keeps its
        // already-drawn grid while the lightweight MediaStore checkpoint is checked.
        val returningFromViewer = mWasFullscreenViewOpen
        mWasFullscreenViewOpen = false
        val returnCheckpoint = pendingFolderReturn
        pendingFolderReturn = null
        val detachedScan = retainedFolderScan
        // Snapshot UI state before the worker starts; never access View
        // properties from a MediaStore/protection background thread.
        val completePublicDisplay = displayedMediaIsFinal &&
            binding.mediaGrid.adapter != null && mMedia.any { it is Medium } &&
            mMedia.filterIsInstance<Medium>().all { it.mediaStoreId > 0L }
        val isRandomSorting = config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0
        if (isPhotoProfiles) {
            // An internal viewer return is not a new Photos navigation. Preserve the
            // already committed grid and worker while its scope/capability is unchanged.
            val sameAuthorizedGrid = returningFromViewer && !photoNeedsUnlock &&
                photoProfileId == PhotoProfileRepository.of(this).state().current?.id &&
                photoLoadedKey == photoDisplayKey() &&
                photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
                binding.mediaGrid.adapter != null && displayedMediaIsFinal
            val catalogWorkerActive = returningFromViewer && photoViewerTransition &&
                mCurrAsyncTask?.status == android.os.AsyncTask.Status.RUNNING
            photoViewerTransition = false
            if (!sameAuthorizedGrid && !catalogWorkerActive) tryLoadGallery()
            else if (sameAuthorizedGrid) {
                setMediaLoading(false)
                binding.mediaRefreshLayout.isRefreshing = false
                // Reuse the committed grid immediately, but verify real directory
                // entries on a worker; MediaStore misses unindexed .nomedia children.
                val generation = ++photoReturnProbeGeneration
                val scope = org.fossify.gallery.helpers.PhotoProfileScope.capture(
                    applicationContext, photoProfileId, photoToken())
                val app = applicationContext
                if (scope != null) ensureBackgroundThread {
                    val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val changed = org.fossify.gallery.helpers.PhotoProfileReturnProbe.needsReconcile(app, scope)
                    org.fossify.gallery.helpers.GalleryPerfTrace.end(
                        "photo_return_source_check", started, if (changed) 1 else 0)
                    runOnUiThread {
                        if (generation != photoReturnProbeGeneration || isDestroyed || isFinishing ||
                            mPath != PhotoProfileMediaLoader.PATH || photoViewerTransition ||
                            scope != org.fossify.gallery.helpers.PhotoProfileScope.capture(
                                applicationContext, photoProfileId, photoToken())) return@runOnUiThread
                        if (changed) getPhotoProfileMedia(org.fossify.gallery.helpers.PhotoLoadReason.REFRESH)
                    }
                }
            }
        } else if (mPath == FAVORITES) {
            tryLoadGallery()
        } else if (detachedScan != null || mMedia.isEmpty() || !isRandomSorting ||
            (isRandomSorting && !returningFromViewer)) {
            val request = ++folderAuthGeneration
            val path = mPath
            val token = folderUnlockSessionId
            val app = applicationContext
            val checkpointTraceId = mediaDiagnostic(displayedMediaRequest)?.id
            ensureBackgroundThread {
                val protectedFolder = FolderUnlockSessionManager.findProtectedAncestor(
                    path, app.config::isFolderProtected
                )
                val authorized = protectedFolder == null ||
                    FolderUnlockSessionManager.isFolderAuthorized(token, path)
                val canRejoin = detachedScan != null && authorized && protectedFolder == null &&
                    detachedScan.task.viewerFolderUnchanged && FolderReturnCheckpoint.sameSnapshot(detachedScan.checkpoint,
                        FolderReturnCheckpoint.captureForViewerScan(app, path))
                val canReuse = authorized && protectedFolder == null &&
                    returningFromViewer && returnCheckpoint != null &&
                    completePublicDisplay && !mShowAll && !isRandomSorting &&
                    FolderReturnCheckpoint.isCurrent(app, path, returnCheckpoint)
                runOnUiThread {
                    if (request != folderAuthGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (returningFromViewer && GalleryDiagnosticLogger.isEnabled()) {
                        GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaPipeline", "folder_checkpoint_evaluated",
                            checkpointTraceId, mapOf(
                                "scopeId" to MediaRequestDiagnostics.scopeId(ViewerScopeType.FOLDER, path),
                                "result" to if (canReuse || canRejoin) "HIT" else "MISS",
                                "reason" to when {
                                    canRejoin -> "same_scan_request"
                                    !authorized -> "access_required"
                                    protectedFolder != null -> "protected"
                                    returnCheckpoint == null -> "checkpoint_missing"
                                    !completePublicDisplay -> "display_incomplete"
                                    mShowAll || isRandomSorting -> "scope_or_order_ineligible"
                                    canReuse -> "current"
                                    else -> "checkpoint_invalid_or_stale"
                                }
                            ))
                    }
                    if (detachedScan != null) {
                        if (retainedFolderScan !== detachedScan) return@runOnUiThread
                        if (resumeFolderViewerScan(detachedScan, canRejoin)) return@runOnUiThread
                    }
                    if (authorized) {
                        if (canReuse) {
                            // The normal permission gate remains mandatory even
                            // when metadata can be reused without rescanning.
                            requestMediaPermissions {
                                if (request == folderAuthGeneration && !isFinishing && !isDestroyed) {
                                    checkLastMediaChanged()
                                }
                            }
                        } else {
                            tryLoadGallery()
                        }
                    } else {
                        handleLockedFolderOpening(protectedFolder!!) { success ->
                            if (success) {
                                ensureBackgroundThread {
                                    val session = FolderUnlockSessionManager.createSession(path)
                                    runOnUiThread {
                                        if (request != folderAuthGeneration || isFinishing || isDestroyed) {
                                            FolderUnlockSessionManager.revokeSession(session)
                                            return@runOnUiThread
                                        }
                                        folderUnlockSessionId = session
                                        tryLoadGallery()
                                    }
                                }
                            } else finish()
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        mediaSortOperations.invalidate()
        folderAuthGeneration++
        cancelViewerHandoff()
        if (mPath == FAVORITES) rememberFavoritesScrollAnchor()
        favoritesScroll.cancelLayout()
        favoritesReturnCover?.pause()
        mediaSearchGeneration++
        mediaPollGeneration++
        mediaPollScheduled = false
        mediaPollInFlight = false
        val keepFolderScan = detachFolderViewerScan()
        super.onPause()
        if (keepFolderScan) {
            storeStateVariables()
            mLastMediaHandler.removeCallbacksAndMessages(null)
            return
        }
        retainedFolderScan = null
        detachedFolderPublication.clear()
        mediaScanTerminals.finish(mediaRefreshRequest, GalleryScanResult(GalleryScanState.CANCELLED))
        mIsGettingMedia = false
        setMediaLoading(false)
        if (!isPhotoProfiles || !photoViewerTransition) mediaLoadRequests.invalidate()
        mediaScanGeneration++
        if (folderStatsRequestVersion != 0L && mPath.startsWith('/')) {
            org.fossify.gallery.helpers.DirectoryMetadataRepository.defer(mPath, folderStatsRequestVersion)
        }
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
        if (!isPhotoProfiles || !photoViewerTransition) {
            diagnosticMediaRequest?.skipIfUnadmitted("activity_pause")
            mCurrAsyncTask?.stopFetching("activity_pause")
        }
    }

    override fun onStop() {
        if (retainedFolderScan == null) mediaRefreshWatchdog.stop()
        super.onStop()
        // Activity.onStop has reset the exit coordinator, including its captured view's alpha.
        releaseViewerSourceLease()
        if (isPhotoProfiles && !photoViewerTransition) {
            photoReturnProbeGeneration++
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            // Genuine departure from Photos still clears UI; internal viewer
            // transitions retain the grid and separate authorized catalog.
            clearPhotoDisplayState()
        }

        if (mPath == FAVORITES && !isFinishing && !isChangingConfigurations && !resolvingFavoriteAccess) {
            // Discard rendering/cache state, retaining the album capability while a viewer is open.
            rememberFavoritesScrollAnchor()
            prepareFavoritesReturnCover()
            showFavoritesReturnCover()
            mediaLoadRequests.invalidate()
            mediaScanGeneration++
            mCurrAsyncTask?.stopFetching()
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            favoritesNeedReload = true
            mLoadedInitialPhotos = false
        }

        if (config.temporarilyShowHidden) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun rememberFavoritesScrollAnchor() {
        val adapter = getMediaAdapter() ?: return
        favoritesScroll.remember(binding.mediaGrid, adapter.media.map { (it as? Medium)?.path })
    }

    private fun prepareFavoritesReturnCover() {
        if (mPath != FAVORITES) return
        val adapter = getMediaAdapter() ?: return
        val cover = getFavoritesReturnCover()
        cover.prepare(binding.mediaCoordinator, binding.mediaGrid) {
            adapter.media.getOrNull(it) is Medium
        }
    }

    private fun getFavoritesReturnCover(): FavoritesReturnCover {
        return favoritesReturnCover ?: FavoritesReturnCover(this).also {
            favoritesReturnCover = it
            binding.mediaCoordinator.addView(it, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            it.elevation = 32 * resources.displayMetrics.density
        }
    }

    private fun showFavoritesReturnCover() {
        if (mPath != FAVORITES) return
        val corners = if (config.fileRoundedCorners) resources.getDimension(
            org.fossify.commons.R.dimen.rounded_corner_radius_big) else 0f
        getFavoritesReturnCover().show(OxygenThemeEngine.pageSurface(this, config), getProperTextColor(), corners)
    }

    private fun isFavoritesReturnCurrent(request: Long, token: String?): Boolean =
        mediaLoadRequests.isCurrent(request) && favoriteSessionId == token &&
            FavoriteAccessSessionManager.isActive(token) && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    private fun retryFavoritesReturn() {
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED)) return
        showFavoritesReturnCover()
        getMedia()
    }

    private fun restoreFavoritesScrollAnchor(request: Long, finalResult: Boolean) {
        if (mPath != FAVORITES) return
        val adapter = getMediaAdapter() ?: return
        val token = favoriteSessionId
        favoritesScroll.restore(binding.mediaGrid, adapter.media.map { (it as? Medium)?.path }, finalResult,
            onResult = { result ->
                if (isFavoritesReturnCurrent(request, token) && binding.mediaGrid.adapter === adapter) {
                    when (result) {
                        FavoritesScrollRestorer.RestoreResult.POSITIONED,
                        FavoritesScrollRestorer.RestoreResult.NO_ANCHOR ->
                            favoritesReturnCover?.reveal { isFavoritesReturnCurrent(request, token) &&
                                binding.mediaGrid.adapter === adapter }
                        FavoritesScrollRestorer.RestoreResult.EMPTY ->
                            favoritesReturnCover?.revealAfterLayout { isFavoritesReturnCurrent(request, token) }
                        FavoritesScrollRestorer.RestoreResult.UNCONFIRMED ->
                            favoritesReturnCover?.showError(::retryFavoritesReturn)
                    }
                }
            }, isCurrent = { isFavoritesReturnCurrent(request, token) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mPath == FAVORITES) {
            rememberFavoritesScrollAnchor()
            favoritesScroll.save(outState)
            outState.putBoolean("favoritesReturnPending", favoritesReturnCover?.covering == true)
        }
        if (isPhotoProfiles && photoToken() == null) {
            rememberPhotoScrollAnchor()
            photoScroll.save(outState)
        }
        outState.putString("favoriteOwnerId", favoriteOwnerId)
        super.onSaveInstanceState(outState)
    }

    // Keep the activity's close animation contract consistent across system and toolbar back.
    override fun finishAfterTransition() { finish() }

    override fun finish() {
        favoritesReturnCover?.dismiss()
        favoritesScroll.clear()
        photoScroll.clear()
        favoriteAuthGeneration++
        folderAuthGeneration++
        favoritePublicationGeneration++
        ViewerAccessCoordinator.cancel(favoritePublicationTask)
        val revoked = favoriteSessionId
        favoriteSessionId = null
        FavoriteAccessSessionManager.revokeSession(revoked)
        super.finish()
        OxygenClassicActivityMotion.applyClose(this)
    }

    override fun onDestroy() {
        cancelViewerHandoff()
        viewerLaunchInProgress = false
        viewerFallbackReturning = false
        if (!isPhotoProfiles) mCurrAsyncTask?.stopFetching("activity_destroy")
        retainedFolderScan = null
        detachedFolderPublication.clear()
        viewerReturnGeneration++
        endViewerReturn("destroyed")
        releaseViewerSourceLease()
        retireTransitionSession("grid_destroyed")
        albumTransitionHandler.removeCallbacksAndMessages(null)
        mediaFastScrollActive = false
        mediaGridPublication.clear()
        OxygenFastScroller.disposeGalleryTouchHandling(binding.mediaFastscroller)
        mediaRefreshWatchdog.stop()
        GalleryBottomBar.dispose(this)
        mediaHeaderProgress?.let(OxygenProgressStyle::dispose)
        OxygenProgressStyle.dispose(binding.loadingIndicator)
        PhotoProfileAccessSessionManager.removeListener(favoriteListenerId)
        favoritesReturnCover?.let {
            it.dismiss()
            binding.mediaCoordinator.removeView(it)
        }
        favoritesReturnCover = null
        favoritesScroll.cancelLayout()
        photoScroll.cancelLayout()
        mediaSearchGeneration++
        favoriteAuthGeneration++
        folderAuthGeneration++
        favoritePublicationGeneration++
        ViewerAccessCoordinator.cancel(favoritePublicationTask)
        FavoriteAccessSessionManager.removeRevocationListener(favoriteListenerId)
        getSharedPrefs().unregisterOnSharedPreferenceChangeListener(favoriteProtectionListener)
        if (mPath == FAVORITES) {
            favoriteObserverDisposed = true
            ensureBackgroundThread {
                synchronized(favoriteObserverLock) {
                    favoriteObservedTracker?.removeObserver(favoriteDatabaseObserver)
                    favoriteObservedTracker = null
                }
            }
        }
        if (isFinishing && !isChangingConfigurations) {
            FavoriteAccessSessionManager.revokeSession(favoriteSessionId)
        }
        if (isFinishing && !isChangingConfigurations) {
            FolderUnlockSessionManager.revokeSession(folderUnlockSessionId)
        }
        super.onDestroy()
        if (mShowAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            unregisterFileUpdateListener()
            // The application database outlives pages and their background workers.
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mediaMenu.isSearchOpen) {
            OxygenSearchNavigation.closeInlineSearch(this, binding.mediaMenu)
            true
        } else {
            if (mGallerySection != null && mPath != FAVORITES) {
                appLockManager.lock()
            }
            if (mPath == FAVORITES) {
                favoriteAuthGeneration++
                FavoriteAccessSessionManager.revokeSession(favoriteSessionId)
            }

            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == PhotoViewerTransition.REQUEST_CODE) {
            if (!isCurrentViewerLaunch(resultData) || viewerResultDelivered) return
            val session = transitionSession
            val stamp = MediaTransitionHandoff.read(resultData)
            if (session == null || (resultData != null && stamp == null)) return
            val requestId = resultData?.getLongExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, 0L) ?: 0L
            val returnPath = resultData?.getStringExtra(PhotoViewerTransition.EXTRA_RETURN_PATH)
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_return_result_received",
                if (resultCode == RESULT_OK && !returnPath.isNullOrEmpty()) 1 else 0
            )
            val request = viewerReturn
            if (resultCode == RESULT_OK && request != null &&
                (request.path != returnPath || stamp != request.operation)) return
            if (stamp != null) {
                if (!session.acceptResult(stamp)) return
            } else if (resultCode != RESULT_CANCELED || !session.acceptFrameworkCancellation()) return
            viewerLaunchInProgress = false
            // A fade can deliver its result while we're paused, before Android restores
            // the original exit view in onResume. Retire the name without recycling it.
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                releaseViewerSourceLease()
            } else {
                viewerSourceLease?.let(::retireViewerSourceName)
            }
            logViewerGridSource("grid_source_activity_result", requestId)
            setExitSharedElementCallback(null as SharedElementCallback?)
            ViewerReturnDiagnostics.event(requestId, "grid_activity_result") {
                mapOf("sharedMapped" to viewerReturnMapped, "reentryObserved" to viewerReentryObservedForCurrentLaunch)
            }
            val scopeMatches = viewerLaunchOriginMatches() && request?.let(::viewerReturnScopeMatches) != false
            viewerLaunchOrigin = null
            if (viewerReturnMapped && request != null && resultCode == RESULT_OK && scopeMatches &&
                isViewerReturnCurrent(request)) {
                request.resultDelivered = true
                awaitViewerReturnDraw(request)
            } else {
                endViewerReturn("activity_result")
            }
            if (request == null && session.outcome == null) {
                val operation = session.returnOperation
                if (operation != null) session.terminate(operation, State.FALLBACK, "result_without_reenter")
                else if (!session.viewerAttached) session.cancel("result_without_viewer")
            }
            session.clean()
            val canRestoreReturnedPath = resultCode == RESULT_OK && !viewerReturnMapped && scopeMatches &&
                !returnPath.isNullOrEmpty() && !isFinishing && !isDestroyed
            val deletedPaths = resultData?.getStringArrayListExtra(PhotoViewerTransition.EXTRA_DELETED_PATHS)
                .orEmpty().toSet()
            if (resultCode == RESULT_OK && request == null && scopeMatches && !isFinishing && !isDestroyed &&
                viewerDeletions.hasNew(viewerReturnLaunch, deletedPaths)) {
                // Even an empty Viewer has a meaningful deletion result. Apply it
                // when there is no return target to restore.
                applyViewerDeletedPaths(deletedPaths) {
                    if (canRestoreReturnedPath) {
                        pendingViewerReturnPath = returnPath
                        restoreViewerReturnPositionWithoutTransition(requireNotNull(returnPath))
                    }
                }
            } else if (canRestoreReturnedPath) {
                // The Viewer can legitimately fall back to a fade when its current
                // renderer is unavailable. Activity reenter callbacks are then not
                // guaranteed, so restore the returned media identity from the result.
                pendingViewerReturnPath = returnPath
                restoreViewerReturnPositionWithoutTransition(requireNotNull(returnPath))
            }
            viewerReentryObservedForCurrentLaunch = false
        }
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PhotoProfileEditActivity.REQUEST_CODE && resultCode == RESULT_OK && !isFinishing) {
            reloadPhotoProfile()
        }
        if (requestCode == PhotoProfileManageActivity.REQUEST_CODE && resultCode == RESULT_OK && !isFinishing) {
            when {
                resultData?.getBooleanExtra(PhotoProfileManageActivity.EXTRA_RELOAD_MEDIA, false) == true ->
                    reloadPhotoProfile()
                resultData?.getBooleanExtra(PhotoProfileManageActivity.EXTRA_REFRESH_CURRENT_DISPLAY, false) == true ->
                    refreshPhotoProfileDisplay()
            }
        }
    }

    private fun restoreViewerReturnPositionWithoutTransition(path: String) {
        if (isPhotoProfiles) photoScroll.clear()
        getMediaAdapter()?.clearRefreshScroll()
        viewerFallbackReturning = true
        val generation = ++viewerReturnGeneration
        val sourcePath = mPath
        val sourceQuery = mCommittedSearchText
        val sourceProfile = photoProfileId
        val deadline = android.os.SystemClock.uptimeMillis() + PhotoViewerTransition.READY_TIMEOUT_MS
        var requestedPosition: GridReturnPosition? = null
        fun finishPositioning() {
            viewerFallbackReturning = false
            if (pendingViewerReturnPath == path) pendingViewerReturnPath = null
        }
        fun awaitTarget() {
            if (generation != viewerReturnGeneration || isFinishing || isDestroyed) return
            if (mPath != sourcePath || mCommittedSearchText != sourceQuery || photoProfileId != sourceProfile) {
                finishPositioning()
                return
            }
            val position = viewerReturnPosition(path)
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_return_fallback_position", position?.index ?: -1)
            val expired = android.os.SystemClock.uptimeMillis() >= deadline
            val grid = binding.mediaGrid
            if (expired || (position != null && position.index < 0 && displayedMediaIsFinal) || isMediaGridMoving) {
                finishPositioning()
                return
            }
            if (position != null && position.index >= 0 && !grid.isLayoutRequested && !grid.hasPendingAdapterUpdates()) {
                val image = findViewerReturnHolder(path, position)?.itemView?.findViewById<ImageView>(R.id.medium_thumbnail)
                if (image != null && hasUsableViewerReturnBounds(image)) {
                    if (isPhotoProfiles) rememberPhotoScrollAnchor("viewer_fade_return", path)
                    finishPositioning()
                    return
                }
                if (position != requestedPosition) {
                    requestedPosition = position
                    grid.scrollToPosition(position.index)
                }
            }
            albumTransitionHandler.postDelayed({ awaitTarget() }, 32L)
        }
        awaitTarget()
    }

    private fun refreshPhotoProfileDisplay() {
        if (!isPhotoProfiles) return
        updateMenuColors()
        binding.mediaMenu.updateHintText(
            getString(org.fossify.commons.R.string.search_in_placeholder, getString(R.string.gallery_nav_photos))
        )
    }

    private fun refreshMenuItems() {
        val isDefaultFolder = !config.defaultFolder.isEmpty()
                && File(config.defaultFolder).compareTo(File(mPath)) == 0

        binding.mediaMenu.requireToolbar().menu.apply {
            fun setVisibleIfChanged(id: Int, visible: Boolean) {
                findItem(id)?.let { if (it.isVisible != visible) it.isVisible = visible }
            }
            setVisibleIfChanged(R.id.group, !config.scrollHorizontally)

            setVisibleIfChanged(R.id.empty_recycle_bin, mPath == RECYCLE_BIN)
            setVisibleIfChanged(R.id.empty_disable_recycle_bin, mPath == RECYCLE_BIN)
            setVisibleIfChanged(R.id.restore_all_files, mPath == RECYCLE_BIN)
            val isPicker = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent || isSetWallpaperIntent()
            setVisibleIfChanged(R.id.photo_profiles_manage,
                org.fossify.gallery.helpers.ToolbarQuickActions.photoProfileManageVisible(
                    isPhotoProfiles, isPicker, mPath == RECYCLE_BIN
                ))
            setVisibleIfChanged(R.id.photo_profiles_unlock, isPhotoProfiles && photoNeedsUnlock && photoProfileId != null)
            setVisibleIfChanged(R.id.photo_profiles_lock, isPhotoProfiles && photoToken() != null)

            setVisibleIfChanged(R.id.folder_view, hasFixedBrowseSwitch)
            setVisibleIfChanged(R.id.open_camera, mShowAll)
            setVisibleIfChanged(R.id.about, mShowAll)
            setVisibleIfChanged(R.id.open_recycle_bin, config.useRecycleBin && mPath != RECYCLE_BIN)

            setVisibleIfChanged(R.id.temporarily_show_excluded, mPath.isEmpty() && !config.temporarilyShowExcluded)
            setVisibleIfChanged(R.id.stop_showing_excluded, mPath.isEmpty() && config.temporarilyShowExcluded)

            setVisibleIfChanged(R.id.temporarily_show_hidden, !config.shouldShowHidden)
            setVisibleIfChanged(R.id.stop_showing_hidden,
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden)

            setVisibleIfChanged(R.id.set_as_default_folder, !isDefaultFolder)
            setVisibleIfChanged(R.id.unset_as_default_folder, isDefaultFolder)

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            setVisibleIfChanged(R.id.column_count, viewType == VIEW_TYPE_GRID)
            setVisibleIfChanged(R.id.toggle_filename, viewType == VIEW_TYPE_GRID)
            if (isPhotoProfiles) {
                listOf(R.id.group, R.id.set_as_default_folder, R.id.unset_as_default_folder,
                    R.id.temporarily_show_hidden, R.id.stop_showing_hidden,
                    R.id.temporarily_show_excluded, R.id.stop_showing_excluded).forEach { setVisibleIfChanged(it, false) }
                setVisibleIfChanged(R.id.slideshow, hasPlayableMedia())
                findItem(R.id.slideshow)?.let {
                    val enabled = org.fossify.gallery.helpers.PhotoPlaybackPolicy
                        .slideshowEnabled(hasPlayableMedia(), photoNeedsUnlock)
                    if (it.isEnabled != enabled) it.isEnabled = enabled
                }
            }
        }
        updateMenuColors()
    }

    private fun setupOptionsMenu() {
        OxygenToolbarStyle.preparePopup(this, binding.mediaMenu)
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mediaMenu.setupMenu()

        binding.mediaMenu.onSearchOpenListener = {
            binding.mediaRefreshLayout.isEnabled = false
        }
        binding.mediaMenu.onSearchClosedListener = {
            binding.mediaRefreshLayout.isEnabled =
                org.fossify.gallery.helpers.SearchRefreshPolicy.isRefreshEnabled(
                    binding.mediaMenu.isSearchOpen, binding.mediaMenu.getCurrentQuery()
                )
        }

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            binding.mediaRefreshLayout.isEnabled =
                org.fossify.gallery.helpers.SearchRefreshPolicy.isRefreshEnabled(
                    binding.mediaMenu.isSearchOpen, text
                )
        }

        binding.mediaMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            if (org.fossify.gallery.helpers.ToolbarQuickActions.isSlot(menuItem.itemId)) {
                when (org.fossify.gallery.helpers.ToolbarQuickActions.actionForSlot(binding.mediaMenu, menuItem.itemId)) {
                    org.fossify.gallery.helpers.ToolbarQuickActions.SORT -> showSortingDialog()
                    org.fossify.gallery.helpers.ToolbarQuickActions.FILTER -> showFilterMediaDialog()
                    org.fossify.gallery.helpers.ToolbarQuickActions.VIEW -> changeViewType()
                    org.fossify.gallery.helpers.ToolbarQuickActions.COLUMNS -> changeColumnCount()
                    org.fossify.gallery.helpers.ToolbarQuickActions.HIDDEN -> tryToggleTemporarilyShowHidden()
                    org.fossify.gallery.helpers.ToolbarQuickActions.EXCLUDED -> tryToggleTemporarilyShowExcluded()
                    org.fossify.gallery.helpers.ToolbarQuickActions.SLIDESHOW -> startSlideshow()
                    org.fossify.gallery.helpers.ToolbarQuickActions.GROUP -> showGroupByDialog()
                    org.fossify.gallery.helpers.ToolbarQuickActions.FILENAME -> toggleFilenameVisibility()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
            when (menuItem.itemId) {
                R.id.quick_toolbar_search_overflow -> {
                    binding.mediaMenu.focusView()
                    showKeyboard(binding.mediaMenu.binding.topToolbarSearch)
                }
                R.id.photo_profiles_manage -> PhotoProfileManageActivity.launch(this)
                R.id.photo_profiles_unlock -> unlockPhotoSources()
                R.id.photo_profiles_lock -> PhotoProfileAccessSessionManager.revokeAll("user_locked_photo_sources")
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
                R.id.restore_all_files -> restoreAllFiles()
                R.id.toggle_filename -> toggleFilenameVisibility()
                R.id.open_camera -> launchCamera()
                R.id.folder_view -> switchToFolderView()
                R.id.change_view_type -> changeViewType()
                R.id.group -> showGroupByDialog()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.unset_as_default_folder -> unsetAsDefaultFolder()
                R.id.slideshow -> startSlideshow()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            hideKeyboard()
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                if (mPath == FAVORITES) {
                    if (!FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, item.path)) { finish(); return }
                    putExtra(FAVORITE_ACCESS_SESSION, favoriteSessionId)
                    putExtra(SHOW_FAVORITES, true)
                }
                folderUnlockSessionId?.let { putExtra(FOLDER_UNLOCK_SESSION, it) }
                putExtra(IS_FROM_GALLERY, true)
                putExtra(PATH, item.path)
                if (isPhotoProfiles) {
                    putExtra(PhotoProfileMediaLoader.EXTRA_ID, photoProfileId)
                    putExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION, photoToken())
                }
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mediaMenu.updateColors()
        OxygenSystemBars.apply(this)
        val picker = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent || isSetWallpaperIntent()
        OxygenToolbarStyle.apply(
            this, binding.mediaMenu,
            when {
                isPhotoProfiles -> getString(R.string.gallery_nav_photos)
                picker -> getString(R.string.app_launcher_name)
                mShowAll || mPath.isEmpty() -> getString(R.string.gallery_nav_albums)
                mPath == FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                mPath == RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                else -> getHumanizedFilename(mPath)
            },
            backButton = mPath == FAVORITES || (mGallerySection == null && !mShowAll),
        )
        if (!picker && mPath != RECYCLE_BIN) {
            // Toolbar layout follows page identity. Photo profile loading and
            // refresh publications must never switch its saved action family.
            val isHome = mPath.isEmpty()
            val viewType = config.getFolderViewType(if (isHome && !isPhotoProfiles) SHOW_ALL else mPath)
            val playable = hasPlayableMedia()
            org.fossify.gallery.helpers.ToolbarQuickActions.apply(
                this, binding.mediaMenu,
                if (isHome) org.fossify.gallery.helpers.ToolbarQuickScope.HOME
                    else org.fossify.gallery.helpers.ToolbarQuickScope.FOLDER,
                if (isHome) config.toolbarHomeActions else config.toolbarFolderActions,
                hasBack = !isHome && !isPhotoProfiles,
                fixedActionCount = (if (hasFixedBrowseSwitch) 1 else 0) + (if (isPhotoProfiles) 1 else 0),
                supported = { action ->
                    org.fossify.gallery.helpers.ToolbarQuickActions.supportsFolderAction(
                        action, grid = viewType == VIEW_TYPE_GRID,
                        horizontal = config.scrollHorizontally,
                        hasPlayableMedia = playable, photoProfile = isPhotoProfiles
                    )
                },
                enabled = { action ->
                    org.fossify.gallery.helpers.ToolbarQuickActions.folderActionEnabled(
                        action, photoProfile = isPhotoProfiles,
                        hasPlayableMedia = playable, needsUnlock = photoNeedsUnlock
                    )
                }
            )
        }
        // Commons can overwrite existing MenuItem drawables with the page's
        // black-on-light contrast tint. Reassert only their presentation color
        // after all action bindings, without changing the stable toolbar plan.
        OxygenToolbarStyle.reassertActionTints(
            binding.mediaMenu.requireToolbar().menu,
            OxygenThemeEngine.toolbarForeground(this, config)
        )
    }

    private fun hasPlayableMedia(): Boolean = mMedia.filterIsInstance<Medium>().any {
        org.fossify.gallery.helpers.PhotoPlaybackPolicy.canPlay(
            it.type, config.slideshowIncludeVideos, config.slideshowIncludeGIFs
        )
    }

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            mShowAll = mPath.isEmpty() && (showAll || mGallerySection == GallerySection.ALL)
        }
    }

    private enum class SearchTrigger { USER_INPUT, MEDIA_SNAPSHOT }

    private fun searchQueryChanged(
        text: String,
        onCommitted: () -> Unit = {},
        trigger: SearchTrigger = SearchTrigger.USER_INPUT
    ) {
        val searchStartedAt = android.os.SystemClock.elapsedRealtime()
        val searchGeneration = ++mediaSearchGeneration
        val request = displayedMediaRequest
        val finalResult = completedMediaRequest == request &&
            (isPhotoProfiles || (mediaScanOutcomeRequest == request && mediaScanOutcome.complete))
        val dataStage = when {
            request < 0 -> "UNKNOWN"
            finalResult -> "COMPLETE"
            else -> "PARTIAL"
        }
        val sourceVersion = displayedSourceVersion
        val searchSorting = config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath)
        val sourceMedia = ArrayList(mMedia)
        val favoriteSnapshot = favoriteInteractiveSnapshot
        val searchFavoriteToken = favoriteSessionId
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaSearch",
                if (trigger == SearchTrigger.USER_INPUT) "search_text_changed" else "search_snapshot_reapplied",
                request, mapOf("generation" to searchGeneration, "queryLength" to text.length,
                    "trigger" to trigger.name, "scanRequest" to mediaRefreshRequest,
                    "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
            )
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSearch", "search_snapshot_captured",
                request, mapOf("generation" to searchGeneration, "queryLength" to text.length,
                    "inputCount" to sourceMedia.count { it is Medium }, "complete" to finalResult,
                    "dataStage" to dataStage, "sourceVersion" to sourceVersion,
                    "cacheAuthorized" to (favoriteSnapshot?.isCurrent(request, searchFavoriteToken,
                        favoriteRelationshipRevision.get()) == true),
                    "scanRequest" to mediaRefreshRequest, "trigger" to trigger.name,
                    "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
            )
        ensureBackgroundThread {
            try {
                val filtered = sourceMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = if (isPhotoProfiles) filtered.toCollection(ArrayList<ThumbnailItem>()) else MediaFetcher(applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                val safeGrouped = if (mPath == FAVORITES) {
                    // Check Room on the worker as well as the observer epoch. A delayed
                    // observer must not let a removed favorite survive a search.
                    if (favoriteSnapshot != null &&
                        applicationContext.getFavoritePaths().toSet() != favoriteSnapshot.relationships) {
                        logFavoriteSearchRejected(request, searchGeneration, "relationship_changed")
                        return@ensureBackgroundThread
                    }
                    val batch = FavoriteAccessPolicy.validateItems(searchFavoriteToken, grouped, pathOf = { (it as? Medium)?.path })
                        ?: run {
                            logFavoriteSearchRejected(request, searchGeneration, "access_revoked")
                            return@ensureBackgroundThread
                        }
                    if (!FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)) return@ensureBackgroundThread
                    batch.items
                } else grouped
                runOnUiThread {
                    // An earlier background search must not overwrite a newer query.
                    if (isFinishing || isDestroyed || mLastSearchedText != text ||
                        searchGeneration != mediaSearchGeneration || sourceVersion != displayedSourceVersion ||
                        searchSorting != config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath) ||
                        !isSearchSnapshotCurrent(request)) {
                        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSearch", "search_stale_drop",
                                request, mapOf("generation" to searchGeneration, "queryLength" to text.length,
                                    "displayedRequest" to displayedMediaRequest, "reason" to when {
                                        isFinishing || isDestroyed -> "activity"
                                        searchGeneration != mediaSearchGeneration || mLastSearchedText != text -> "query"
                                        else -> "snapshot_or_access"
                                    }, "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
                            )
                        return@runOnUiThread
                    }
                    val searchAdapter = getMediaAdapter()
                    if (searchAdapter == null) {
                        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                                org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaSearch", "search_ui_unavailable",
                                request, mapOf("generation" to searchGeneration, "queryLength" to text.length,
                                    "reason" to "adapter_missing", "dataStage" to dataStage,
                                    "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
                            )
                        return@runOnUiThread
                    }
                    searchAdapter.updateMedia(safeGrouped,
                        preserveScroll = trigger == SearchTrigger.MEDIA_SNAPSHOT && favoritesScroll.pending == null,
                        isCurrent = {
                        searchGeneration == mediaSearchGeneration && mLastSearchedText == text &&
                            sourceVersion == displayedSourceVersion &&
                            searchSorting == config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath) &&
                            isSearchSnapshotCurrent(request)
                    }, onCommitted = {
                        // A PARTIAL snapshot without matches is provisional; later scan
                        // publications rerun this query against their newer media snapshot.
                        if (org.fossify.gallery.helpers.MediaSearchPublicationPolicy.showNoResults(
                                safeGrouped.isEmpty(), finalResult)) {
                            binding.mediaEmptyTextPlaceholder.text =
                                getString(org.fossify.commons.R.string.no_items_found)
                            binding.mediaEmptyTextPlaceholder.beVisible()
                            if (isPhotoProfiles) updatePhotoEmptyState(searchEmpty = true)
                            else binding.mediaEmptyDescription.beGone()
                            binding.mediaFastscroller.beGone()
                        } else {
                            binding.mediaEmptyTextPlaceholder.beGone()
                            binding.mediaEmptyDescription.beGone()
                            binding.mediaEmptyTextPlaceholder2.beGone()
                        }

                        logSearchCommit(request, searchGeneration, text.length, sourceMedia.count { it is Medium },
                            safeGrouped.count { it is Medium }, finalResult, dataStage, searchStartedAt)
                        if (searchGeneration == mediaSearchGeneration && mLastSearchedText == text) {
                            mCommittedSearchText = text
                        }
                        if (finalResult && mediaLoadRequests.isCurrent(request)) setMediaLoading(false)
                        displayedMediaIsFinal = finalResult
                        if (!isPhotoProfiles) setMediaLoading(mIsGettingMedia)
                        updateMediaFastScrollerVisibility(safeGrouped.isNotEmpty())
                        restoreFavoritesScrollAnchor(request, finalResult)
                        onCommitted()
                    }, diagnosticRequestId = request, updateReason = MediaUpdateReason.USER_SEARCH,
                        diagnosticTraceId = mediaDiagnostic(request)?.id,
                        effectiveSorting = searchSorting, diagnosticSnapshotVersion = sourceVersion,
                        diagnosticSnapshotStage = displayedFolderSnapshot?.stage?.name ?: dataStage)
                }
            } catch (error: Exception) {
                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                    org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaSearch", "search_failed",
                        request, mapOf("generation" to searchGeneration, "queryLength" to text.length,
                            "errorType" to error.javaClass.simpleName,
                            "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
                    )
            }
        }
    }

    private fun logFavoriteSearchRejected(request: Long, generation: Long, reason: String) {
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSearch", "cache_search_rejected",
                request, mapOf("generation" to generation, "reason" to reason))
    }

    private fun logSearchCommit(request: Long, searchGeneration: Long, queryLength: Int,
                                inputCount: Int, matchCount: Int, finalResult: Boolean,
                                dataStage: String, searchStartedAt: Long) {
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaSearch", "search_results_committed",
                request, mapOf("generation" to searchGeneration, "queryLength" to queryLength,
                    "inputCount" to inputCount,
                    "matchCount" to matchCount, "complete" to finalResult,
                    "dataStage" to dataStage,
                    "scanRequest" to mediaRefreshRequest,
                    "adapterVersion" to (getMediaAdapter()?.committedVersion ?: -1L),
                    "adapterId" to (getMediaAdapter()?.let { System.identityHashCode(it) } ?: -1),
                    "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath),
                    "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - searchStartedAt))
            )
    }

    /** A refresh can be scanning while the still-current displayed snapshot remains searchable. */
    private fun isSearchSnapshotCurrent(request: Long): Boolean {
        val accessCurrent = when {
            isPhotoProfiles -> mediaLoadRequests.isCurrent(request)
            mPath == FAVORITES -> favoriteInteractiveSnapshot?.isCurrent(
                request, favoriteSessionId, favoriteRelationshipRevision.get()
            ) ?: (FavoriteAccessSessionManager.getCapturedPaths(favoriteSessionId) != null &&
                displayedMediaIsFinal && displayedFavoriteRelationshipRevision == favoriteRelationshipRevision.get())
            mPath == RECYCLE_BIN -> displayedRecycleBinRevision ==
                org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()
            mPath.startsWith('/') && mPath != RECYCLE_BIN && !mShowAll ->
                displayedFolderSnapshot?.takeIf { it.request == request }?.accessCurrent?.invoke() == true
            mShowAll -> mediaPageAccess?.invoke() != false
            else -> true
        }
        return org.fossify.gallery.helpers.MediaSearchRequestPolicy.canPublish(
            snapshotRequest = request,
            displayedRequest = displayedMediaRequest,
            activityCurrent = !isFinishing && !isDestroyed && searchGenerationIsActive(),
            accessCurrent = accessCurrent
        )
    }

    private fun searchGenerationIsActive(): Boolean = lifecycle.currentState.isAtLeast(
        androidx.lifecycle.Lifecycle.State.STARTED
    )

    private fun tryLoadGallery() {
        requestMediaPermissions {
            val dirName = when (mPath) {
                PhotoProfileMediaLoader.PATH -> getString(R.string.gallery_nav_photos)
                FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                config.OTGPath -> getString(org.fossify.commons.R.string.usb)
                else -> getHumanizedFilename(mPath)
            }

            val searchHint = if (mShowAll) {
                getString(org.fossify.commons.R.string.search_files)
            } else {
                getString(org.fossify.commons.R.string.search_in_placeholder, dirName)
            }

            binding.mediaMenu.updateHintText(searchHint)
            if ((mGallerySection == null || mPath == FAVORITES) && !mShowAll) {
                // OxygenToolbarStyle owns the single closed-search back control.
                // Commons owns the search arrow only while search is expanded.
                binding.mediaMenu.onNavigateBackClickListener = {
                    OxygenSearchNavigation.hideImeAndClearFocus(this, binding.mediaMenu)
                    performDefaultBack()
                }
            }

            if (mShowLoadingIndicator) {
                setMediaLoading(true)
                mShowLoadingIndicator = false
            }

            setupLayoutManager()
            getMedia()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter(updateReason: MediaUpdateReason = MediaUpdateReason.SCAN,
                             onCommitted: () -> Unit = {}) {
        if (mPath == FAVORITES) {
            if (!FavoriteAccessSessionManager.isActive(favoriteSessionId)) return
        }
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        val completeCommit = {
            onCommitted()
            updateMediaFastScrollerVisibility()
        }
        if (currAdapter == null) {
            initZoomListener()
            MediaAdapter(
                activity = this,
                media = if (mLastSearchedText.isEmpty()) ArrayList(mMedia) else arrayListOf(),
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid
            ) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                onThumbnailLoaded = { _, image ->
                    val request = displayedMediaRequest
                    if (mediaPageRequest == request && request >= 0 && GalleryDiagnosticLogger.isEnabled()) {
                        if (firstThumbnailResourceReady != request) {
                            firstThumbnailResourceReady = request
                            logMediaPage(request, "media_first_thumbnail_resource_ready")
                        }
                    }
                    if (mediaPageRequest == request && request >= 0 && firstThumbnailDraw != request &&
                        GalleryDiagnosticLogger.isEnabled()) {
                        val thumbnailKey = image.getTag(R.id.thumbnail_loaded_key)
                        watchForFirstMediaThumbnailDraw(request, image, thumbnailKey)
                    }
                }
                sorting = config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath)
                setupZoomListener(mZoomListener)
                binding.mediaGrid.adapter = this
                logInitialSnapshot(displayedMediaRequest, mediaDiagnostic(displayedMediaRequest)?.id)
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            val returningToFavorites = mPath == FAVORITES && favoritesReturnCover?.covering == true
            if (returningToFavorites) binding.mediaGrid.clearAnimation()
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled && !returningToFavorites) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            if (mGallerySection != null && mPath in listOf("", FAVORITES, PhotoProfileMediaLoader.PATH)) {
                if (mPath == FAVORITES) {
                    GalleryNavigation.restoreListState(GalleryNavigation.LIST_FAVORITES_FILES, binding.mediaGrid)
                } else GalleryNavigation.restoreListState(
                    GalleryScreen(mGallerySection!!, GalleryContentView.FILES), binding.mediaGrid
                )
            }
            handleGridSpacing()
            if (mLastSearchedText.isNotEmpty()) searchQueryChanged(
                mLastSearchedText, completeCommit, SearchTrigger.MEDIA_SNAPSHOT
            )
            else completeCommit()
        } else if (mLastSearchedText.isEmpty()) {
            val request = displayedMediaRequest
            val read = folderReadResult.takeIf { folderReadRequest == request }
            val searchGeneration = mediaSearchGeneration
            val sourceVersion = displayedSourceVersion
            val effectiveSorting = config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath)
            val folderToken = folderUnlockSessionId
            (currAdapter as MediaAdapter).updateMedia(mMedia,
                preserveScroll = favoritesScroll.pending == null && photoScroll.pending == null &&
                    viewerReturn == null && !viewerFallbackReturning,
                isCurrent = {
                isSearchSnapshotCurrent(request) && searchGeneration == mediaSearchGeneration &&
                    sourceVersion == displayedSourceVersion &&
                    effectiveSorting == config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath) &&
                    !isFinishing && !isDestroyed &&
                    (mPath != FAVORITES || FavoriteAccessSessionManager.isActive(favoriteSessionId)) &&
                    read?.accessCurrent?.invoke() != false &&
                    (mediaPageRequest != request || mediaPageAccess?.invoke() != false) &&
                    folderToken == folderUnlockSessionId &&
                    (folderToken == null || FolderUnlockSessionManager.isActive(folderToken))
            }, onCommitted = {
                handleGridSpacing(currAdapter.media)
                if (completedMediaRequest == request && !mIsGettingMedia) setMediaLoading(false)
                completeCommit()
            }, diagnosticRequestId = request, updateReason = updateReason, effectiveSorting = effectiveSorting,
                diagnosticTraceId = mediaDiagnostic(request)?.id,
                diagnosticSnapshotVersion = sourceVersion,
                diagnosticSnapshotStage = displayedFolderSnapshot?.stage?.name)
        } else {
            searchQueryChanged(mLastSearchedText, onCommitted, SearchTrigger.MEDIA_SNAPSHOT)
        }

        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.mediaFastscroller.setScrollVertically(!scrollHorizontally)
        styleFastScroller()
    }

    private fun styleFastScroller() {
        val bottomBar = if (mGallerySection == null) 0 else
            resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
        OxygenFastScroller.apply(binding.mediaFastscroller, binding.mediaGrid,
            OxygenThemeEngine.snapshot(this, config), bottomBar)
        OxygenFastScroller.applyGalleryBottomInsets(binding.mediaFastscroller, bottomBar)
        OxygenFastScroller.installGalleryTouchHandling(
            binding.mediaFastscroller,
            binding.mediaGrid,
            ::onPhotoFastScrollStateChanged,
        )
    }

    private var mediaPollScheduled = false
    private var mediaPollInFlight = false
    private var mediaPollGeneration = 0L
    private val mediaChangeGate = org.fossify.gallery.helpers.MediaChangeRetryGate()

    private fun pollAllowed(): Boolean = !isPhotoProfiles && !isDestroyed && !isFinishing &&
        config.getFolderSorting(mPath) and SORT_BY_RANDOM == 0 &&
        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    private fun checkLastMediaChanged() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { checkLastMediaChanged() }
            return
        }
        if (!pollAllowed() || mediaPollScheduled || mediaPollInFlight) return
        mediaPollScheduled = true
        val generation = mediaPollGeneration
        mLastMediaHandler.postDelayed({
            mediaPollScheduled = false
            if (generation != mediaPollGeneration || !pollAllowed()) return@postDelayed
            mediaPollInFlight = true
            ensureBackgroundThread {
                val latest = runCatching { getLatestMediaId() to getLatestMediaByDateId() }
                    .onFailure { android.util.Log.w("GalleryMedia", "MediaStore poll failed", it) }.getOrNull()
                runOnUiThread {
                    if (generation != mediaPollGeneration) return@runOnUiThread
                    mediaPollInFlight = false
                    if (!pollAllowed()) return@runOnUiThread
                    if (latest != null) {
                        val changed = mediaPollInitialized &&
                            (mLatestMediaId != latest.first || mLatestMediaDateId != latest.second)
                        mediaPollInitialized = true
                        mLatestMediaId = latest.first
                        mLatestMediaDateId = latest.second
                        if (changed) {
                            if (mIsGettingMedia) mediaChangeGate.mediaChangedDuringScan()
                            else getMedia()
                        }
                    }
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        val previousPhotoScope = if (isPhotoProfiles) PhotoProfileScope.capture(this, photoProfileId, photoToken()) else null
        ChangeSortingDialog(this, false, true, mPath) {
            val sortOperation = mediaSortOperations.begin()
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val visibleRead = folderReadResult.takeIf { folderReadRequest == displayedMediaRequest }
            if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaSort", "sort_action_requested",
                    displayedMediaRequest, mapOf("generation" to sortOperation,
                        "sorting" to config.getFolderSorting(mPath), "itemCount" to mMedia.count { it is Medium },
                        "partial" to when (visibleRead?.status) {
                            org.fossify.gallery.helpers.FolderReadStatus.PARTIAL -> true
                            org.fossify.gallery.helpers.FolderReadStatus.COMPLETE -> false
                            else -> -1
                        },
                        "dataStage" to (displayedFolderSnapshot?.stage?.name ?: visibleRead?.status?.name ?: "UNKNOWN"),
                        "scanRequest" to mediaRefreshRequest,
                        "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
                )
            if (isPhotoProfiles) {
                getPhotoProfileMedia(org.fossify.gallery.helpers.PhotoLoadReason.ENTER, previousPhotoScope)
                return@ChangeSortingDialog
            }
            if (tryResortVirtualMediaSnapshot(sortOperation, startedAt)) return@ChangeSortingDialog
            if (resortCompletedPhysicalFolder(sortOperation, startedAt)) return@ChangeSortingDialog
            if (previewSortDuringFolderScan(sortOperation, startedAt)) return@ChangeSortingDialog
            // An active request will apply the latest sorting when it publishes.
            // Restarting it here canceled useful work and repeatedly invalidated
            // Adapter diffs during ordinary refreshes.
            if (!mIsGettingMedia) getMedia(reason = "sort_snapshot_unavailable")
            else if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
                val activeRead = folderReadResult.takeIf { folderReadRequest == displayedMediaRequest }
                val deferredReason = when {
                    isPhotoProfiles || mShowAll || mPath == FAVORITES || mPath == RECYCLE_BIN || !mPath.startsWith('/') ->
                        "special_source"
                    getMediaAdapter() == null -> "adapter_unavailable"
                    mMedia.any { it !is Medium } -> "grouped_rows"
                    displayedFolderSnapshot?.accessCurrent?.invoke() != true -> "display_capability_unavailable"
                    activeRead?.status != org.fossify.gallery.helpers.FolderReadStatus.PARTIAL &&
                        activeRead?.status != org.fossify.gallery.helpers.FolderReadStatus.COMPLETE -> "snapshot_incomplete"
                    else -> "preview_ineligible"
                }
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaSort", "sort_action_deferred",
                    displayedMediaRequest, mapOf("generation" to sortOperation,
                        "sorting" to config.getFolderSorting(mPath), "scanRequest" to mediaRefreshRequest,
                        "dataStage" to activeRead?.status?.name.orEmpty(),
                        "itemCount" to mMedia.count { it is Medium }, "reason" to deferredReason,
                        "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
                )
            }
        }
    }

    /** Sorts a verified display snapshot independently of the source scan terminal state. */
    private fun tryResortVirtualMediaSnapshot(sortOperation: Long, startedAt: Long): Boolean {
        val sourceKind = when (mPath) {
            FAVORITES -> "FAVORITES"
            RECYCLE_BIN -> "RECYCLE_BIN"
            else -> return false
        }
        val sourcePath = mPath
        val request = displayedMediaRequest
        val sourceVersion = displayedSourceVersion
        val scanInProgress = mIsGettingMedia
        val scanRequest = mediaRefreshRequest
        val sorting = config.getFolderSorting(sourcePath)
        val grouping = config.getFolderGrouping(sourcePath)
        val adapter = getMediaAdapter()
        val snapshot = mMedia.filterIsInstance<Medium>().map { it.copy() }
        val mediaPaths = snapshot.mapTo(LinkedHashSet()) { it.path }
        val favoriteRelationships = favoriteCatalogRelationshipPaths
        val favoriteToken = favoriteSessionId
        val favoriteRevision = favoriteRelationshipRevision.get()
        val interactiveFavorite = sourceKind == "FAVORITES" && favoriteInteractiveSnapshot?.isCurrent(
            request, favoriteToken, favoriteRevision) == true
        val recycleRevision = org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()
        val activityCurrent = !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        val complete = displayedMediaIsFinal && request >= 0 &&
            completedMediaRequest == request && mediaScanOutcomeRequest == request && mediaScanOutcome.complete
        val requestCurrent = if (scanInProgress) mediaLoadRequests.isCurrent(scanRequest) else mediaLoadRequests.isCurrent(request)
        val supportedRows = mMedia.all { it is Medium || it is ThumbnailSection } &&
            adapter?.media?.all { it is Medium || it is ThumbnailSection } == true
        val adapterMatches = adapter != null && adapter.path == sourcePath &&
            binding.mediaGrid.adapter === adapter && supportedRows
        val capturedFavoritePaths = if (sourceKind == "FAVORITES")
            FavoriteAccessSessionManager.getCapturedPaths(favoriteToken) else null
        val accessCurrent = when (sourceKind) {
            "FAVORITES" -> capturedFavoritePaths?.containsAll(mediaPaths) == true
            else -> true
        }
        val displayedMembersCurrent = when (sourceKind) {
            "FAVORITES" -> favoriteRelationships != null &&
                displayedFavoriteRelationshipRevision == favoriteRevision &&
                (interactiveFavorite || capturedFavoritePaths?.containsAll(favoriteRelationships) == true)
            else -> displayedRecycleBinRevision == recycleRevision
        }
        // Cache numeric fields have no load provenance. Repair requested fields on
        // the worker; a nonzero cached date may itself be a modified-date fallback.
        val identifiersPresent = snapshot.all { it.path.isNotBlank() && it.name.isNotBlank() }
        val needsMetadataRead = interactiveFavorite && !complete &&
            org.fossify.gallery.helpers.FavoriteSortMetadata.requiresRead(sorting, grouping)
        val metadataPresent = identifiersPresent && !needsMetadataRead
        val decision = org.fossify.gallery.helpers.VirtualMediaSortPolicy.decide(
            source = sourcePath,
            activityCurrent = activityCurrent,
            adapterMatchesSource = adapterMatches,
            displayedSnapshotComplete = complete,
            requestCurrent = requestCurrent,
            accessCurrent = accessCurrent,
            // The relationship/Room snapshot is checked off the UI thread immediately
            // before sorting and again before publishing the result.
            membersCurrent = if (displayedMembersCurrent) null else false,
            requiredSortMetadataPresent = metadataPresent,
            scanInProgress = scanInProgress,
            interactiveCacheVerified = interactiveFavorite
        )
        if (decision == org.fossify.gallery.helpers.VirtualMediaSortDecision.ACCESS_INVALID) {
            if (sourceKind == "FAVORITES") finish()
            return true
        }
        if (decision == org.fossify.gallery.helpers.VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH) {
            logVirtualSortDecision(sortOperation, sourceKind, sorting, snapshot.size, "wait_for_scan")
            return true
        }
        if (decision != org.fossify.gallery.helpers.VirtualMediaSortDecision.VERIFY_MEMBERS &&
            decision != org.fossify.gallery.helpers.VirtualMediaSortDecision.PREVIEW_COMPLETE_DURING_REFRESH &&
            decision != org.fossify.gallery.helpers.VirtualMediaSortDecision.PREVIEW_VERIFIED_CACHE) {
            logVirtualSortDecision(sortOperation, sourceKind, sorting, snapshot.size, "controlled_reload")
            if (!scanInProgress) getMedia(reason = "virtual_sort_snapshot_unavailable")
            return true
        }

        logVirtualSortDecision(sortOperation, sourceKind, sorting, snapshot.size,
            if (interactiveFavorite && !complete) "cache_preview_sort" else if (scanInProgress) "reuse_complete_during_refresh" else "reuse_complete")
        ensureBackgroundThread {
            fun membershipSnapshot(): Set<String>? {
                return when (sourceKind) {
                    "FAVORITES" -> {
                        if (favoriteRevision != favoriteRelationshipRevision.get()) return null
                        val captured = FavoriteAccessSessionManager.getCapturedPaths(favoriteToken)
                        if (captured == null || (!interactiveFavorite && !captured.containsAll(favoriteRelationships.orEmpty())) ||
                            !captured.containsAll(mediaPaths)) null
                        else applicationContext.getFavoritePaths().toSet().takeIf {
                            it == favoriteRelationships && favoriteRevision == favoriteRelationshipRevision.get()
                        }
                    }
                    else -> {
                        if (recycleRevision != org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()) return null
                        val filter = config.filterMedia
                        applicationContext.getUpdatedDeletedMedia().asSequence()
                            .filter { config.shouldShowHidden || !File(it.path).name.startsWith('.') }
                            .filter { it.type and filter != 0 }
                            .map { it.path }
                            .toCollection(LinkedHashSet())
                            .takeIf { recycleRevision == org.fossify.gallery.helpers.RecycleBinMembershipRevision.current() }
                    }
                }
            }

            try {
                val membersBefore = membershipSnapshot()
                val favoritesChanged = sourceKind == "FAVORITES" && membersBefore != favoriteRelationships
                if (membersBefore == null || favoritesChanged || !membersBefore.containsAll(mediaPaths)) {
                    runOnUiThread {
                        if (mediaSortOperations.isCurrent(sortOperation) && displayedMediaRequest == request &&
                            scanInProgress && mIsGettingMedia && mediaLoadRequests.isCurrent(scanRequest)) {
                            logVirtualSortDecision(sortOperation, sourceKind, sorting, snapshot.size, "members_changed_wait")
                        } else if (scanInProgress && !mIsGettingMedia && !isFinishing && !isDestroyed &&
                            mPath == sourcePath && mediaSortOperations.isCurrent(sortOperation)) {
                            tryResortVirtualMediaSnapshot(sortOperation, startedAt)
                        } else if (!scanInProgress && !isFinishing && !isDestroyed && mPath == sourcePath) {
                            getMedia(reason = "virtual_sort_members_changed")
                        }
                    }
                    return@ensureBackgroundThread
                }
                val sorted = if (interactiveFavorite && !complete) {
                    logVirtualSortDecision(sortOperation, sourceKind, sorting, snapshot.size, "cache_metadata_validation")
                    org.fossify.gallery.helpers.FavoriteSortMetadata.prepare(applicationContext, snapshot, sorting, grouping) {
                        mediaSortOperations.isCurrent(sortOperation) &&
                            favoriteInteractiveSnapshot?.isCurrent(request, favoriteToken, favoriteRevision) == true
                    } ?: return@ensureBackgroundThread
                } else ArrayList(snapshot)
                val verifiedDecision = org.fossify.gallery.helpers.VirtualMediaSortPolicy.decide(
                    source = sourcePath,
                    activityCurrent = activityCurrent,
                    adapterMatchesSource = adapterMatches,
                    displayedSnapshotComplete = complete,
                    requestCurrent = requestCurrent,
                    accessCurrent = (sourceKind != "FAVORITES" ||
                        FavoriteAccessSessionManager.getCapturedPaths(favoriteToken)?.containsAll(mediaPaths) == true) &&
                        (sourceKind != "RECYCLE_BIN" ||
                            recycleRevision == org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()),
                    membersCurrent = true,
                    requiredSortMetadataPresent = identifiersPresent,
                    scanInProgress = scanInProgress,
                    interactiveCacheVerified = interactiveFavorite
                )
                if (verifiedDecision != org.fossify.gallery.helpers.VirtualMediaSortDecision.REUSE_COMPLETE &&
                    verifiedDecision != org.fossify.gallery.helpers.VirtualMediaSortDecision.PREVIEW_COMPLETE_DURING_REFRESH &&
                    verifiedDecision != org.fossify.gallery.helpers.VirtualMediaSortDecision.PREVIEW_VERIFIED_CACHE) {
                    runOnUiThread {
                        if (scanInProgress && !mIsGettingMedia && !isFinishing && !isDestroyed &&
                            mPath == sourcePath && mediaSortOperations.isCurrent(sortOperation)) {
                            tryResortVirtualMediaSnapshot(sortOperation, startedAt)
                        } else if (!scanInProgress && !isFinishing && !isDestroyed &&
                            mPath == sourcePath && mediaSortOperations.isCurrent(sortOperation)) {
                            getMedia(reason = "virtual_sort_membership_invalid")
                        }
                    }
                    return@ensureBackgroundThread
                }
                MediaFetcher(applicationContext).sortMedia(sorted, sorting)
                val grouped = MediaFetcher(applicationContext).groupMedia(sorted, sourcePath)
                val membersAfter = membershipSnapshot()
                val sortValidation = if (interactiveFavorite && !complete)
                    FavoriteAccessSessionManager.validatePaths(favoriteToken, mediaPaths) ?: return@ensureBackgroundThread
                    else null
                runOnUiThread {
                    val stillCurrent = !isFinishing && !isDestroyed && mPath == sourcePath &&
                        (sortValidation == null || FavoriteAccessSessionManager.isPublicationCurrent(sortValidation)) &&
                        mediaSortOperations.isCurrent(sortOperation) && displayedMediaRequest == request &&
                        displayedSourceVersion == sourceVersion && binding.mediaGrid.adapter === adapter &&
                        config.getFolderSorting(sourcePath) == sorting && config.getFolderGrouping(sourcePath) == grouping &&
                        (sourceKind != "FAVORITES" ||
                            FavoriteAccessSessionManager.getCapturedPaths(favoriteToken)?.let {
                                it.containsAll(mediaPaths) && (interactiveFavorite || it.containsAll(favoriteRelationships.orEmpty()))
                            } == true) &&
                        (sourceKind != "FAVORITES" ||
                            (displayedFavoriteRelationshipRevision == favoriteRevision &&
                                favoriteRevision == favoriteRelationshipRevision.get())) &&
                        (!scanInProgress || (mediaRefreshRequest == scanRequest &&
                            mediaLoadRequests.isCurrent(scanRequest))) &&
                        (scanInProgress || mediaLoadRequests.isCurrent(request)) &&
                        membersAfter == membersBefore &&
                        (sourceKind != "FAVORITES" || favoriteRevision == favoriteRelationshipRevision.get()) &&
                        (sourceKind != "RECYCLE_BIN" ||
                            recycleRevision == org.fossify.gallery.helpers.RecycleBinMembershipRevision.current())
                    if (!stillCurrent) {
                        logSortStaleDrop(sortOperation, request, sorting, "virtual_source_or_members_changed")
                        if (scanInProgress && !mIsGettingMedia && !isFinishing && !isDestroyed &&
                            mPath == sourcePath && mediaSortOperations.isCurrent(sortOperation)) {
                            tryResortVirtualMediaSnapshot(sortOperation, startedAt)
                        }
                        return@runOnUiThread
                    }
                    mediaSearchGeneration++
                    displayedSourceVersion++
                    mMedia = ArrayList(grouped)
                    setupAdapter(MediaUpdateReason.USER_SORT) {
                        // A user sort can replace a pending final SCAN diff. Keep
                        // the real terminal state even if that diff's callback is dropped.
                        if (completedMediaRequest == request && mediaScanOutcomeRequest == request &&
                            mediaScanOutcome.complete && !mIsGettingMedia) displayedMediaIsFinal = true
                        logSortCommit("virtual_sort_snapshot_committed", sortOperation, request, sorting,
                            scanInProgress, startedAt, sorted.size, getMediaAdapter()?.committedVersion ?: -1L)
                    }
                }
            } catch (error: Exception) {
                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                    org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaSort", "virtual_sort_failed",
                        request, mapOf("generation" to sortOperation, "source" to sourceKind,
                            "errorType" to error.javaClass.simpleName)
                    )
                runOnUiThread {
                    if (!scanInProgress && !isFinishing && !isDestroyed && mPath == sourcePath &&
                        mediaSortOperations.isCurrent(sortOperation)) getMedia(reason = "virtual_sort_failed")
                }
            }
        }
        return true
    }

    private fun logVirtualSortDecision(generation: Long, source: String, sorting: Int,
                                       itemCount: Int, decision: String) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSort", "virtual_sort_decision",
            displayedMediaRequest, mapOf("generation" to generation, "source" to source,
                "sorting" to sorting, "itemCount" to itemCount, "decision" to decision,
                "scanRequest" to mediaRefreshRequest,
                "sourceVersion" to displayedSourceVersion,
                "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
        )
    }

    /** Display-only sort of already published rows while an ordinary folder scan continues. */
    private fun previewSortDuringFolderScan(sortOperation: Long, startedAt: Long): Boolean {
        val request = displayedMediaRequest
        val scanRequest = mediaRefreshRequest
        val read = folderReadResult.takeIf { folderReadRequest == request }
        val snapshot = displayedFolderSnapshot?.takeIf { it.request == request }
        val sourceVersion = displayedSourceVersion
        val adapter = getMediaAdapter() ?: return false
        val source = mMedia.filterIsInstance<Medium>()
        val sourcePaths = source.mapTo(HashSet()) { it.path }
        val adapterMatches = adapter.media.filterIsInstance<Medium>().all { it.path in sourcePaths }
        val token = folderUnlockSessionId
        val eligible = org.fossify.gallery.helpers.MediaSortPreviewPolicy.canPreview(
            physicalFolder = !isPhotoProfiles && (mShowAll || mPath.startsWith('/')) && mPath != RECYCLE_BIN,
            ordinarySource = mPath != FAVORITES,
            scanInProgress = mIsGettingMedia,
            snapshotValid = snapshot != null,
            requestCurrent = request >= 0 && displayedMediaRequest == request && scanRequest >= 0 &&
                mediaLoadRequests.isCurrent(scanRequest),
            accessCurrent = snapshot?.accessCurrent?.invoke() == true &&
                (token == null || FolderUnlockSessionManager.isActive(token)),
            adapterMatchesSource = adapterMatches,
            rows = source
        )
        if (!eligible) return false

        val folder = if (mShowAll) SHOW_ALL else mPath
        val sorting = config.getFolderSorting(folder)
        val order = mediaPageOrder
        logSortSnapshotSelected(sortOperation, request, sorting,
            snapshot?.stage?.name ?: "UNKNOWN", source.size)
        ensureBackgroundThread {
            try {
                val sorted = source.mapTo(ArrayList(source.size)) { it.copy() }
                val fetcher = MediaFetcher(applicationContext)
                order.sort(sorted, sorting, fetcher)
                val grouped = fetcher.groupMedia(sorted, folder)
                runOnUiThread {
                    if (isFinishing || isDestroyed || !mediaSortOperations.isCurrent(sortOperation) ||
                        displayedMediaRequest != request || !mediaLoadRequests.isCurrent(scanRequest) ||
                        (if (mShowAll) SHOW_ALL else mPath) != folder || folderUnlockSessionId != token ||
                        config.getFolderSorting(folder) != sorting || sourceVersion != displayedSourceVersion ||
                        snapshot?.accessCurrent?.invoke() != true ||
                        (token != null && !FolderUnlockSessionManager.isActive(token))) {
                        logSortStaleDrop(sortOperation, request, sorting, "preview_snapshot_or_access_changed")
                        return@runOnUiThread
                    }
                    mMedia = grouped
                    setupAdapter(MediaUpdateReason.USER_SORT) {
                        val sourceIsPartial = read?.status != org.fossify.gallery.helpers.FolderReadStatus.COMPLETE
                        logSortCommit("sort_preview_committed", sortOperation, request, sorting, sourceIsPartial,
                            startedAt, sorted.size, getMediaAdapter()?.committedVersion ?: -1L)
                    }
                }
            } catch (error: Exception) {
                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                    org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaSort", "sort_action_failed",
                        request, mapOf("generation" to sortOperation, "sorting" to sorting,
                            "reason" to (error::class.java.simpleName.takeIf { it.isNotBlank() } ?: "unknown"),
                            "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(folder)))
                android.util.Log.w("GalleryMedia", "Partial folder sort preview failed", error)
            }
        }
        return true
    }

    /** Reuse a complete physical-folder snapshot; incomplete or protected sources still reload. */
    private fun resortCompletedPhysicalFolder(sortOperation: Long, startedAt: Long): Boolean {
        val request = displayedMediaRequest
        val read = folderReadResult.takeIf { folderReadRequest == request }
        val canReuse = org.fossify.gallery.helpers.MediaSortReusePolicy.canReuse(
            photoProfile = isPhotoProfiles,
            showAll = mShowAll,
            physicalFolder = mPath.startsWith('/'),
            recycleBin = mPath == RECYCLE_BIN,
            scanInProgress = mIsGettingMedia,
            requestCurrent = request >= 0 && completedMediaRequest == request && mediaLoadRequests.isCurrent(request),
            scanComplete = mediaScanOutcomeRequest == request && mediaScanOutcome.complete,
            snapshotComplete = read?.status == org.fossify.gallery.helpers.FolderReadStatus.COMPLETE,
            accessCurrent = read?.accessCurrent?.invoke() == true,
            simpleMediaRows = mMedia.isNotEmpty() && mMedia.all { it is Medium }
        )
        if (!canReuse || read == null) return false

        val folder = mPath
        val folderToken = folderUnlockSessionId
        val sorting = config.getFolderSorting(folder)
        val sourceVersion = displayedSourceVersion
        val snapshot = mMedia.filterIsInstance<Medium>().map { it.copy() }
        logSortSnapshotSelected(sortOperation, request, sorting, "COMPLETE", snapshot.size)
        fun recoverFromSortFailure(error: Exception) {
            android.util.Log.w("GalleryMedia", "Background folder sort failed; retrying through scan path", error)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && mediaSortOperations.isCurrent(sortOperation) &&
                    mediaLoadRequests.isCurrent(request) && mPath == folder &&
                    config.getFolderSorting(folder) == sorting) getMedia()
            }
        }
        try {
            ensureBackgroundThread {
                try {
                    val sorted = ArrayList(snapshot)
                    MediaFetcher(applicationContext).sortMedia(sorted, sorting)
                    runOnUiThread {
                        if (isFinishing || isDestroyed || !mediaSortOperations.isCurrent(sortOperation) ||
                            !mediaLoadRequests.isCurrent(request) || completedMediaRequest != request ||
                            mIsGettingMedia || sourceVersion != displayedSourceVersion || mPath != folder ||
                            folderUnlockSessionId != folderToken ||
                            config.getFolderSorting(folder) != sorting ||
                            (folderToken != null && !FolderUnlockSessionManager.isActive(folderToken))) {
                            logSortStaleDrop(sortOperation, request, sorting, "complete_snapshot_or_access_changed")
                            return@runOnUiThread
                        }
                        val latestRead = folderReadResult.takeIf { folderReadRequest == request }
                        if (latestRead?.let {
                                it.status == org.fossify.gallery.helpers.FolderReadStatus.COMPLETE && it.accessCurrent()
                            } != true) {
                            logSortStaleDrop(sortOperation, request, sorting, "complete_authorization_expired")
                            return@runOnUiThread
                        }
                        mMedia = ArrayList(sorted)
                        setupAdapter(MediaUpdateReason.USER_SORT) {
                            logSortCommit("sort_final_committed", sortOperation, request, sorting, false,
                                startedAt, sorted.size, getMediaAdapter()?.committedVersion ?: -1L)
                        }
                    }
                } catch (error: Exception) {
                    recoverFromSortFailure(error)
                }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            recoverFromSortFailure(error)
        }
        return true
    }

    private fun logSortSnapshotSelected(generation: Long, request: Long, sorting: Int,
                                        dataStage: String, itemCount: Int) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSort", "sort_snapshot_selected", request,
            mapOf("generation" to generation, "sorting" to sorting, "dataStage" to dataStage,
                "scanRequest" to mediaRefreshRequest, "itemCount" to itemCount,
                "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
        )
    }

    private fun logSortStaleDrop(generation: Long, request: Long, sorting: Int, reason: String) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaSort", "sort_stale_drop", request,
            mapOf("generation" to generation, "sorting" to sorting, "reason" to reason,
                "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
        )
    }

    private fun logSortCommit(event: String, generation: Long, request: Long, sorting: Int,
                              partial: Boolean, startedAt: Long, itemCount: Int, adapterVersion: Long) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaSort", event, request,
            mapOf("generation" to generation, "sorting" to sorting, "itemCount" to itemCount,
                "partial" to partial, "elapsedMs" to (android.os.SystemClock.elapsedRealtime() - startedAt),
                "adapterVersion" to adapterVersion,
                "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath))
        )
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this, photoProfile = isPhotoProfiles) { filter ->
            val confirmedAt = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            org.fossify.gallery.helpers.GalleryPerfTrace.event("media_filter_confirmed")
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            if (!projectCurrentFolderToFilter(filter, confirmedAt)) binding.mediaGrid.adapter = null
            getMedia(reason = "filter_change")
        }
    }

    private fun projectCurrentFolderToFilter(filter: Int, confirmedAt: Long): Boolean {
        val grid = binding.mediaGrid
        val source = displayedFolderSnapshot ?: return false
        val read = folderReadResult ?: return false
        val checkScope = mCurrAsyncTask?.pageAccessCurrentAfterFilterChange ?: return false
        if (mShowAll || !mPath.startsWith('/') || isPhotoProfiles || mPath == RECYCLE_BIN ||
            folderUnlockSessionId != null || mIsGetAnyIntent || mIsGetImageIntent || mIsGetVideoIntent ||
            !displayedMediaIsFinal || mIsGettingMedia || source.stage != DisplayedSourceStage.SCAN_COMPLETE ||
            source.request != displayedMediaRequest || folderReadRequest != source.request ||
            read.status != org.fossify.gallery.helpers.FolderReadStatus.COMPLETE || read.method != "direct" ||
            mLastSearchedText.isNotEmpty() || mCommittedSearchText.isNotEmpty() || getMediaAdapter() == null ||
            viewerReturn != null || viewerFallbackReturning || mediaFastScrollActive || grid.isComputingLayout) return false

        val access = { config.filterMedia == filter && checkScope() && read.accessCurrent() }
        if (!access()) return false
        grid.stopScroll()
        mediaSearchGeneration++
        displayedSourceVersion++
        displayedMediaIsFinal = false
        displayedFolderSnapshot = DisplayedFolderSnapshot(
            source.request, displayedSourceVersion, DisplayedSourceStage.SCAN_PARTIAL, access
        )
        // The committed catalog proves only these previously visible survivors.
        // The new scan can find newly included types and publish the authoritative list.
        mMedia = org.fossify.gallery.helpers.MediaUpdatePolicy.retainMedia(mMedia) { it.type and filter != 0 }
        var committed = false
        setupAdapter(MediaUpdateReason.USER_FILTER) {
            committed = true
            org.fossify.gallery.helpers.GalleryPerfTrace.end("media_filter_projection_committed", confirmedAt, mMedia.size)
        }
        return committed
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        showRestoreConfirmationDialog(paths.size) {
            restoreRecycleBinPaths(paths) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(RECYCLE_BIN)
                }
                finish()
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun openGalleryRoot(section: GallerySection) {
        favoriteAuthGeneration++
        FavoriteAccessSessionManager.revokeSession(favoriteSessionId)
        if (mGallerySection != null && mPath in listOf("", FAVORITES, PhotoProfileMediaLoader.PATH)) {
            if (mPath == FAVORITES) {
                GalleryNavigation.rememberListState(GalleryNavigation.LIST_FAVORITES_FILES, binding.mediaGrid)
            } else GalleryNavigation.rememberListState(
                GalleryScreen(mGallerySection!!, GalleryContentView.FILES), binding.mediaGrid
            )
        }
        val screen = GalleryNavigation.bottomDestination(section, config.lastMainViewAll)
        config.showAll = screen.isAllFiles
        val destination = if (screen.section == GallerySection.PHOTOS) {
            Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
        } else if (screen.isAllFiles) {
            Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, "")
        } else {
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
                .putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, screen.section == GallerySection.FAVORITES)
        }
        destination.putExtra(GalleryNavigation.EXTRA_SECTION, screen.section.name)
        if (binding.mediaMenu.isSearchOpen) {
            OxygenSearchNavigation.closeInlineSearch(this, binding.mediaMenu)
        }
        hideKeyboard()
        GalleryBottomBar.open(this, destination, finishCurrent = true)
    }

    private fun switchToFolderView() {
        val section = mGallerySection
        if (section == null) {
            // Keep the legacy chooser's folder-switch action functional; it is
            // not part of the remembered bottom navigation state.
            hideKeyboard()
            config.lastMainViewAll = false
            config.showAll = false
            GalleryBottomBar.open(this, Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true), finishCurrent = true)
            return
        }
        if (section == GallerySection.ALL) config.lastMainViewAll = false
        else config.lastFavoritesViewFiles = false
        openGalleryRoot(section)
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    private fun getMedia(manualRefresh: Boolean = false, reason: String = "source_refresh") {
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaPipeline",
                "scan_restart_requested",
                fields = mapOf("reason" to reason, "scanInProgress" to mIsGettingMedia,
                    "previousGeneration" to mediaRefreshRequest, "previousRequestId" to diagnosticMediaRequest?.id,
                    "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath)))
        mediaSortOperations.invalidate()
        if (isPhotoProfiles) { getPhotoProfileMedia(); return }
        if (mPath == FAVORITES && favoriteSessionId != null) {
            favoriteAccessPaths = FavoriteAccessSessionManager.getCapturedPaths(favoriteSessionId)
            if (favoriteAccessPaths == null) {
                GalleryDiagnosticLogger.observe {
                    GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaPipeline", "media_request_not_created",
                        fields = mapOf("reason" to "favorites_access_expired"))
                }
                finish()
                return
            }
        }
        if (mPath == FAVORITES && favoriteAccessPaths == null) {
            GalleryDiagnosticLogger.observe {
                GalleryDiagnosticLogger.event(DiagnosticLevel.DEBUG, "MediaPipeline", "media_request_not_created",
                    fields = mapOf("reason" to "awaiting_favorites_access"))
            }
            resolveFavoriteAccess { allowedPaths ->
                favoriteAccessPaths = allowedPaths
                if (!isFinishing && !isDestroyed) getMedia()
            }
            return
        }
        // Every refresh has a separate request identity, including the cache step.
        // A late cache response must never launch an older scan or replace new UI.
        mediaRefreshWatchdog.stop()
        mediaChangeGate.scanStarted()
        val request = mediaLoadRequests.begin()
        retainedFolderScan = null
        detachedFolderPublication.clear()
        mediaGridPublication.clear()
        mediaRefreshRequest = request
        beginMediaDiagnostic(request, if (manualRefresh) "manual_refresh" else reason)
        if (mPath == FAVORITES) favoriteLoadRevision = favoriteRelationshipRevision.get()
        if (mPath == RECYCLE_BIN) recycleBinLoadRevision =
            org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()
        mediaScanTerminals.begin(request)
        if (mPath == FAVORITES && favoritesReturnCover?.covering == true) {
            val token = favoriteSessionId
            favoritesReturnCover?.pause()
            favoritesReturnCover?.armTimeout({ isFavoritesReturnCurrent(request, token) }, ::retryFavoritesReturn)
        }
        mCurrAsyncTask?.stopFetching("superseded")
        clearRevokedMediaPage()
        mIsGettingMedia = true
        if (manualRefresh) binding.mediaRefreshLayout.isRefreshing = true
        setMediaLoading(true)
        armMediaRefreshDeadline(request)
        binding.mediaEmptyTextPlaceholder.beGone()
        binding.mediaEmptyDescription.beGone()
        binding.mediaEmptyTextPlaceholder2.beGone()
        binding.mediaEmptyTextPlaceholder2.setText(R.string.change_filters_underlined)
        binding.mediaEmptyTextPlaceholder2.setOnClickListener { showFilterMediaDialog() }
        if (mPath == FAVORITES) {
            binding.mediaEmptyTextPlaceholder.beGone()
            binding.mediaEmptyDescription.beGone()
            binding.mediaEmptyTextPlaceholder2.beGone()
        }
        if (mShowAll || (mPath.startsWith('/') && mPath != RECYCLE_BIN)) {
            val folder = mPath
            val token = folderUnlockSessionId
            val target = mediaViewportItems()
            mediaPageRequest = request
            mediaPagePreviewJob?.cancel()
            mediaPagePreview = MediaPagePreview()
            mediaPageOrder = StagedMediaOrder()
            mediaPagePendingAccess = null
            mediaPageOpenedAt = android.os.SystemClock.elapsedRealtime()
            logMediaPage(request, "media_page_open")
            if (getMediaAdapter()?.media?.any { it is Medium } != true) {
                loadMediaPageCache(request, folder, token, target)
            }
            startAsyncTask(request, manualRefresh, pagePreviewTarget = target)
        } else if (mLoadedInitialPhotos) {
            startAsyncTask(request, manualRefresh)
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent,
                allowedFavoritePaths = favoriteAccessPaths
            ) { cached ->
                runOnUiThread {
                    if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return@runOnUiThread
                    if (cached.isNotEmpty()) gotMedia(cached, true, request)
                    startAsyncTask(request, manualRefresh, cached.filterIsInstance<Medium>())
                }
            }
        }
        mLoadedInitialPhotos = true
    }

    private fun clearRevokedMediaPage(): Boolean {
        if (mediaPageAccess?.invoke() != false) return false
        discardInaccessibleGrid()
        return true
    }

    private fun discardInaccessibleGrid() {
        mediaGridPublication.clear()
        mediaFastScrollerUpdateGeneration++
        mediaPageAccess = null
        displayedFolderSnapshot = null
        displayedMediaIsFinal = false
        mMedia = arrayListOf()
        binding.mediaGrid.adapter = null
        OxygenFastScroller.setRangeAvailable(binding.mediaFastscroller, false)
    }

    private fun mediaViewportItems(): Int {
        val metrics = resources.displayMetrics
        val grid = binding.mediaGrid
        val width = grid.width.takeIf { it > 0 } ?: metrics.widthPixels
        val height = grid.height.takeIf { it > 0 } ?: metrics.heightPixels
        val list = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath) == VIEW_TYPE_LIST
        val span = if (list) 1 else config.mediaColumnCnt.coerceAtLeast(1)
        val horizontal = !list && config.scrollHorizontally
        val cell = when {
            list -> resources.getDimensionPixelSize(R.dimen.list_view_folder_thumbnail_size)
            horizontal -> height / span
            else -> width / span
        }.coerceAtLeast(1)
        val extent = if (horizontal) width else height
        return (span * ((extent + cell - 1) / cell + 1)).coerceAtLeast(1)
    }

    private fun logMediaPage(request: Long, event: String, detail: String = "") {
        if (mediaPageRequest != request || !GalleryDiagnosticLogger.isEnabled()) return
        val elapsed = android.os.SystemClock.elapsedRealtime() - mediaPageOpenedAt
        FolderRefreshDiagnostics.event(request, event, detail = "elapsedMs=$elapsed $detail",
            diagnosticRequest = mediaDiagnostic(request)?.id,
            evidence = when (event) {
                "media_first_thumbnail_resource_ready" -> FrameEvidence.READY
                "media_first_thumbnail_rendered" -> FrameEvidence.PRE_DRAW
                else -> null
            })
    }

    private fun watchForFirstMediaThumbnailDraw(
        request: Long,
        image: ImageView,
        thumbnailKey: Any?
    ) {
        var removed = false
        var observing: android.view.ViewTreeObserver? = null
        lateinit var preDrawListener: android.view.ViewTreeObserver.OnPreDrawListener
        lateinit var attachListener: View.OnAttachStateChangeListener

        fun removePreDraw() {
            observing?.let { if (it.isAlive) it.removeOnPreDrawListener(preDrawListener) }
            observing = null
        }

        fun removeWatch() {
            if (removed) return
            removed = true
            removePreDraw()
            image.removeOnAttachStateChangeListener(attachListener)
        }

        fun watchAttachedView() {
            if (removed || !image.isAttachedToWindow) return
            val observer = image.viewTreeObserver
            if (!observer.isAlive || observing === observer) return
            removePreDraw()
            observing = observer
            observer.addOnPreDrawListener(preDrawListener)
            image.invalidate()
        }

        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = watchAttachedView()
            override fun onViewDetachedFromWindow(view: View) = removePreDraw()
        }
        preDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
            val current = request == mediaPageRequest && request == displayedMediaRequest &&
                mediaLoadRequests.isCurrent(request) && firstThumbnailDraw != request &&
                image.getTag(R.id.thumbnail_request_key) == thumbnailKey && image.drawable != null
            if (!current) {
                removeWatch()
            } else {
                val visibleRect = android.graphics.Rect()
                if (image.isShown && image.getGlobalVisibleRect(visibleRect) &&
                    !visibleRect.isEmpty) {
                    firstThumbnailDraw = request
                    logMediaPage(request, "media_first_thumbnail_rendered")
                    removeWatch()
                } else if (firstThumbnailNotVisible != request) {
                    firstThumbnailNotVisible = request
                    var ancestor: View? = image
                    var hiddenBy = "none"
                    while (ancestor != null) {
                        if (ancestor.visibility != View.VISIBLE) {
                            hiddenBy = ancestor.javaClass.simpleName
                            break
                        }
                        ancestor = ancestor.parent as? View
                    }
                    logMediaPage(request, "media_thumbnail_waiting_for_visible_draw",
                        "attached=${image.isAttachedToWindow}_shown=${image.isShown}_hiddenBy=${hiddenBy}_window=${image.windowVisibility}")
                }
            }
            true
        }

        image.addOnAttachStateChangeListener(attachListener)
        watchAttachedView()
    }

    private fun loadMediaPageCache(request: Long, folder: String, token: String?, target: Int) {
        val all = mShowAll
        val app = applicationContext
        val types = config.filterMedia and when {
            mIsGetImageIntent && !mIsGetVideoIntent -> TYPE_IMAGES
            mIsGetVideoIntent && !mIsGetImageIntent -> TYPE_VIDEOS
            else -> config.filterMedia
        }
        // One cache worker with a bounded queue. Every queued request checks
        // cancellation before starting I/O. A busy cache lane leaves scan delivery intact.
        try {
            mediaPageCacheExecutor.execute {
                if (!mediaLoadRequests.isCurrent(request)) return@execute
                val access = runCatching { DirectoryAccessState.capture(app) }.getOrNull() ?: return@execute
                val current = {
                    runCatching {
                        access == DirectoryAccessState.capture(app) &&
                            (token == null || FolderUnlockSessionManager.isActive(token))
                    }.getOrDefault(false)
                }
                logMediaPage(request, "media_cache_query_start")
                val rows = runCatching {
                    app.getMediaPageCache(folder.takeUnless { all }, types, target,
                        current = { mediaLoadRequests.isCurrent(request) },
                        allowed = { folderPublicationAllowed(it, token) })
                }.onFailure {
                    android.util.Log.w("GalleryMedia", "Media page cache unavailable", it)
                }.getOrNull().orEmpty()
                logMediaPage(request, "media_cache_query_end", "count=${rows.size}")
                runOnUiThread {
                    publishPagePreview(request, MediaPageBatch(DisplayedSourceStage.CACHE_PREVIEW, rows, current))
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            logMediaPage(request, "media_cache_query_skipped")
        }
    }

    private fun publishPagePreview(request: Long, batch: MediaPageBatch) {
        if (!mediaLoadRequests.isCurrent(request) || mediaScanTerminals.result(request) != null ||
            request != mediaPageRequest || isFinishing || isDestroyed || batch.rows.isEmpty() ||
            !batch.accessCurrent()) return
        // Refreshes keep the already committed grid until the final result. The
        // first page request can grow from cache/index even while the first diff runs.
        if (displayedMediaRequest != request && getMediaAdapter()?.media?.any { it is Medium } == true) return
        val rows = mediaPagePreview.add(batch.stage, batch.rows) ?: return
        val previousAccess = mediaPagePendingAccess
        val access = { batch.accessCurrent() && previousAccess?.invoke() != false }
        mediaPagePendingAccess = access
        sortPagePreview(request, batch.stage, rows, access)
    }

    private fun sortPagePreview(
        request: Long, stage: DisplayedSourceStage, rows: List<Medium>, access: () -> Boolean
    ) {
        mediaPagePreviewJob?.cancel()
        val path = if (mShowAll) SHOW_ALL else mPath
        val sorting = config.getFolderSorting(path)
        val grouping = config.getFolderGrouping(path)
        val order = mediaPageOrder
        mediaPagePreviewJob = lifecycleScope.launch {
            val grouped = withContext(Dispatchers.Default) {
                val sorted = rows.mapTo(ArrayList(rows.size)) {
                    it.copy(taken = if (it.taken > 0L) it.taken else it.modified)
                }
                val fetcher = MediaFetcher(applicationContext)
                order.sort(sorted, sorting, fetcher)
                fetcher.groupMedia(sorted, path)
            }
            if (!mediaLoadRequests.isCurrent(request) || mediaScanTerminals.result(request) != null ||
                request != mediaPageRequest || isFinishing || isDestroyed || !access()) return@launch
            if (sorting != config.getFolderSorting(path) || grouping != config.getFolderGrouping(path)) {
                sortPagePreview(request, stage, rows, access)
                return@launch
            }
            if (displayedMediaRequest != request) logMediaPage(request, "media_first_preview_submit", "stage=$stage")
            publishMedia(grouped, true, request, stage, access)
        }
    }

    private fun publishPageScan(
        request: Long, sorting: Int?, media: ArrayList<ThumbnailItem>, access: () -> Boolean
    ) {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed ||
            mediaScanTerminals.result(request) != null) return
        if (!access()) {
            discardInaccessibleGrid()
            renderMediaScanState(request, GalleryScanResult(GalleryScanState.ACCESS_REQUIRED,
                cause = SecurityException("Media access expired")))
            mediaChangeGate.scanFinished(success = false)
            toast(R.string.media_load_permission_error)
            return
        }

        val path = if (mShowAll) SHOW_ALL else mPath
        val latestSorting = config.getFolderSorting(path)
        val prepared = sorting == latestSorting && latestSorting and SORT_BY_RANDOM == 0
        if (!prepared) {
            val order = mediaPageOrder
            ensureBackgroundThread {
                val rows = media.filterIsInstance<Medium>().mapTo(ArrayList()) { it.copy() }
                val fetcher = MediaFetcher(applicationContext).also { it.traceRequest = request }
                order.sort(rows, latestSorting, fetcher)
                val grouped = fetcher.groupMedia(rows, path)
                runOnUiThread {
                    if (latestSorting == config.getFolderSorting(path)) commitPageScan(request, grouped, access)
                    else publishPageScan(request, null, grouped, access)
                }
            }
        } else {
            commitPageScan(request, media, access)
        }
    }

    private fun commitPageScan(request: Long, media: ArrayList<ThumbnailItem>, access: () -> Boolean) {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed ||
            mediaScanTerminals.result(request) != null) return
        if (!access()) {
            // Reuse the same terminal path if capabilities changed during final sorting.
            publishPageScan(request, null, media, access)
            return
        }
        if (!mediaScanOutcome.complete && media.none { it is Medium }) {
            if (!renderMediaScanState(request, mediaScanOutcome)) return
            mediaChangeGate.scanFinished(success = false)
            val hasContent = getMediaAdapter()?.media?.any { it is Medium } == true
            org.fossify.gallery.helpers.MediaLoadFailure.present(
                this, binding.mediaEmptyTextPlaceholder, binding.mediaEmptyDescription,
                binding.mediaEmptyTextPlaceholder2, hasContent,
                org.fossify.gallery.helpers.PartialMediaScanException(mediaScanOutcome.cause)
            ) { getMedia(manualRefresh = true) }
            return
        }
        mediaPagePreviewJob?.cancel()
        val stage = if (mediaScanOutcome.complete) DisplayedSourceStage.SCAN_COMPLETE else DisplayedSourceStage.SCAN_PARTIAL
        logMediaPage(request, "media_scan_complete", "state=${mediaScanOutcome.state} count=${media.size}")
        publishMedia(media, false, request, stage, access)
    }

    private var mediaScanOutcomeRequest = -1L
    private var mediaScanOutcome = org.fossify.gallery.helpers.GalleryScanResult(org.fossify.gallery.helpers.GalleryScanState.CANCELLED)
    private val mediaScanTerminals = org.fossify.gallery.helpers.GalleryScanTerminalGate()
    private var folderReadRequest = -1L
    private var folderReadResult: org.fossify.gallery.helpers.FolderMediaReadResult? = null
    private var folderStatsRequestVersion = 0L
    private var displayedSourceVersion = 0L
    private var displayedFolderSnapshot: DisplayedFolderSnapshot? = null
    private var displayedRecycleBinRevision = -1L
    private var recycleBinLoadRevision = -1L

    private val folderPublicationVersion = java.util.concurrent.atomic.AtomicLong()
    private fun folderPublicationAllowed(path: String, token: String? = folderUnlockSessionId): Boolean {
        val parent = File(path).parent ?: return false
        return FolderUnlockSessionManager.findProtectedAncestor(parent, config::isFolderProtected) == null ||
            FolderUnlockSessionManager.isMediaAuthorized(token, path, config::isFolderProtected)
    }

    private fun startAsyncTask(request: Long, manualRefresh: Boolean = false,
        retainedMedia: List<Medium> = getMediaAdapter()?.let { mMedia.filterIsInstance<Medium>() }.orEmpty(),
        pagePreviewTarget: Int = 0) {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) {
            mediaDiagnostic(request)?.skip("stale_or_inactive")
            return
        }
        // A canceled or superseded scan must not change the visible list or cached data.
        val scanGeneration = ++mediaScanGeneration
        mCurrAsyncTask?.stopFetching()
        val requestedFolder = mPath
        val metadataScope = org.fossify.gallery.helpers.DirectoryScanScope(
            config.filterMedia, config.shouldShowHidden,
            mIsGetImageIntent && !mIsGetVideoIntent,
            mIsGetVideoIntent && !mIsGetImageIntent
        )
        val metadataVersion = if (!mShowAll && !isPhotoProfiles && requestedFolder.startsWith('/') &&
            requestedFolder != RECYCLE_BIN && metadataScope.matches(applicationContext))
            org.fossify.gallery.helpers.DirectoryMetadataRepository.begin(requestedFolder) else 0L
        folderStatsRequestVersion = metadataVersion
        val metadataSession = folderUnlockSessionId
        val progressivePage = pagePreviewTarget > 0
        var scanTask: GetMediaAsynctask? = null
        scanTask = GetMediaAsynctask(
            context = applicationContext,
            manualRefresh = manualRefresh,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll,
            cachedMediaFallback = retainedMedia.map { it.copy() },
            folderSessionId = folderUnlockSessionId,
            requestId = request,
            retainSortMetadata = true,
            pagePreviewTarget = pagePreviewTarget,
            onPagePreview = if (progressivePage) { batch ->
                if (scanGeneration == mediaScanGeneration && requestedFolder == mPath) {
                    publishPagePreview(request, batch)
                }
            } else null,
            onScanCancelled = {
                if (scanGeneration == mediaScanGeneration &&
                    renderMediaScanState(request, GalleryScanResult(GalleryScanState.CANCELLED))) {
                    mediaChangeGate.scanFinished(success = false)
                }
            },
            onSortingMetadataRefreshRequired = {
                if (mediaLoadRequests.isCurrent(request) && scanGeneration == mediaScanGeneration &&
                    requestedFolder == mPath && !isFinishing && !isDestroyed) getMedia(reason = "sort_metadata_missing")
            },
            onFolderDetails = { details ->
                if (scanGeneration == mediaScanGeneration && mediaLoadRequests.isCurrent(request) &&
                    requestedFolder == mPath && !isFinishing && !isDestroyed) {
                    // Notify before mutating mMedia: initial adapter rows may share its objects.
                    getMediaAdapter()?.updateVideoDurations(details)
                    val byPath = details.associateBy { it.path }
                    mMedia.filterIsInstance<Medium>().forEach { item ->
                        byPath[item.path]?.takeIf { it.modified == item.modified && it.size == item.size }
                            ?.let { item.videoDuration = it.videoDuration }
                    }
                }
            },
            // Completeness and feedback are rendered once by publishMedia from scanResult.
            onPartialFailure = {},
            favoritePathsOverride = if (mPath == FAVORITES)
                favoriteAccessPaths?.toCollection(ArrayList()) else null,
            favoriteSessionId = if (mPath == FAVORITES) favoriteSessionId else null,
            onFailure = { error ->
                android.util.Log.e("GalleryMedia", "Media scan failed", error)
                runOnUiThread {
                    if (mediaLoadRequests.isCurrent(request) && scanGeneration == mediaScanGeneration &&
                        requestedFolder == mPath && !isFinishing && !isDestroyed) {
                        if (error is SecurityException) discardInaccessibleGrid()
                        else if (progressivePage) clearRevokedMediaPage()
                        if (!renderMediaScanState(request, GalleryScanResult(
                            if (error is SecurityException) GalleryScanState.ACCESS_REQUIRED else GalleryScanState.FAILED,
                            cause = error))) return@runOnUiThread
                        mediaChangeGate.scanFinished(success = false)
                        binding.mediaRefreshLayout.isRefreshing = false
                        if (mPath == FAVORITES) {
                            // The album is cleared while its viewer is open. Make a
                            // failed return scan visible and let pull-to-refresh retry.
                            binding.mediaEmptyTextPlaceholder.text = getString(R.string.failed_to_load_media)
                            binding.mediaEmptyTextPlaceholder.beVisible()
                            binding.mediaEmptyTextPlaceholder2.beGone()
                            binding.mediaFastscroller.beGone()
                            favoritesReturnCover?.showError(::retryFavoritesReturn)
                        } else {
                            val hasContent = getMediaAdapter()?.media?.any { it is Medium } == true
                            org.fossify.gallery.helpers.MediaLoadFailure.present(
                                this, binding.mediaEmptyTextPlaceholder, binding.mediaEmptyDescription,
                                binding.mediaEmptyTextPlaceholder2, hasContent, error
                            ) { getMedia() }
                            if (!hasContent) binding.mediaFastscroller.beGone()
                        }
                    }
                }
            }
        ) scanComplete@{ scannedMedia ->
            if (scanGeneration != mediaScanGeneration || !mediaLoadRequests.isCurrent(request) ||
                requestedFolder != mPath || isFinishing || isDestroyed) return@scanComplete
            // Capture the current grid snapshot on the callback (main) thread.
            // Reconciliation must not iterate mMedia while a newer scan replaces it.
            val oldMedia = mMedia.filterIsInstance<Medium>().map { it.copy() }
            mediaScanOutcomeRequest = request
            mediaScanOutcome = scanTask?.scanResult ?: GalleryScanResult(GalleryScanState.FAILED)
            folderReadRequest = request
            folderReadResult = scanTask?.folderReadResult
            if (progressivePage) {
                val access = scanTask?.pageAccessCurrent ?: { false }
                val read = folderReadResult
                publishPageScan(request, scanTask?.preparedSorting, scannedMedia) {
                    access() && read?.accessCurrent?.invoke() != false
                }
            }
            ensureBackgroundThread {
                if (scanGeneration != mediaScanGeneration || !mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) {
                    return@ensureBackgroundThread
                }
                try {
                    val newPaths = scannedMedia.filterIsInstance<Medium>().mapTo(HashSet()) { it.path }
                    if (requestedFolder != mPath) return@ensureBackgroundThread
                    if (!progressivePage) gotMedia(scannedMedia, false, request)
                    if (metadataVersion != 0L) {
                        val read = scanTask?.folderReadResult
                        if (read != null) {
                            org.fossify.gallery.helpers.DirectoryMetadataRepository.commit(
                                applicationContext,
                                org.fossify.gallery.helpers.DirectoryScanSnapshot(
                                    requestedFolder, metadataVersion, metadataScope, read,
                                    scanTask?.folderStatsMediaSnapshot.orEmpty(),
                                    metadataSession,
                                    isCurrent = {
                                        scanGeneration == mediaScanGeneration &&
                                            mediaLoadRequests.isCurrent(request) &&
                                            requestedFolder == mPath && !isFinishing && !isDestroyed
                                    }
                                )
                            )
                        } else {
                            org.fossify.gallery.helpers.DirectoryMetadataRepository.defer(requestedFolder, metadataVersion)
                        }
                    }
                    val catalogTask = scanTask
                    val folderCheckpoint = catalogTask?.folderCatalogCheckpoint
                    val catalogObservation = catalogTask?.folderCatalogObservation
                    if (folderCheckpoint != null && catalogObservation != null && !mShowAll && requestedFolder.isNotEmpty() &&
                        requestedFolder != FAVORITES && requestedFolder != RECYCLE_BIN) {
                        val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                        val catalogId = org.fossify.gallery.helpers.FolderViewerSnapshotStore.prepare(
                            applicationContext, requestedFolder, catalogTask.viewerScanCheckpoint, folderCheckpoint,
                            scannedMedia.filterIsInstance<Medium>(), catalogObservation
                        ) {
                            scanGeneration == mediaScanGeneration && mediaLoadRequests.isCurrent(request) &&
                                requestedFolder == mPath && !isFinishing && !isDestroyed
                        }
                        org.fossify.gallery.helpers.GalleryPerfTrace.end(
                            if (catalogId == null) "viewer_catalog_prepare_rejected" else "viewer_catalog_prepared",
                            catalogStarted, if (catalogId == null) 0 else scannedMedia.count { it is Medium }
                        )
                    }
                    // A filtered/partial/permission-limited scan is NOT evidence that an
                    // old file was deleted. Never clear a favorite relationship here.
                    // Only prune cached metadata for files confirmed absent from an
                    // accessible parent directory.
                    if (scanTask?.scanResult?.complete != true) return@ensureBackgroundThread
                    oldMedia.forEach { old ->
                        if (scanGeneration != mediaScanGeneration || !mediaLoadRequests.isCurrent(request)) return@ensureBackgroundThread
                        val file = File(old.path)
                        val parentReadable = file.parentFile?.let {
                            it.isDirectory && it.canRead()
                        } == true
                        if (MediaCachePrunePolicy.canPrune(
                                oldPath = old.path,
                                latestPaths = newPaths,
                                isFavoritesView = mPath == FAVORITES,
                                parentReadable = parentReadable,
                                fileExists = file.exists()
                            )
                        ) {
                            mediaDB.deleteMediumPath(old.path)
                            if (old.path.startsWith("$RECYCLE_BIN/")) {
                                org.fossify.gallery.helpers.RecycleBinMembershipRevision.changed()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GalleryMedia", "Media scan reconciliation failed", e)
                }
            }
        }

        val task = scanTask ?: return
        task.requestDiagnostics = mediaDiagnostic(request)
        mCurrAsyncTask = task
        try {
            org.fossify.gallery.helpers.FolderRefreshDiagnostics.event(request, "folder_queued")
            task.executeOnExecutor(org.fossify.gallery.helpers.GalleryMediaScanExecutor)
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            // No worker callback is guaranteed after an executor rejection.
            // Explicitly record the failed terminal before retiring this request.
            task.reportRejectedExecution()
            mediaScanTerminals.finish(request, org.fossify.gallery.helpers.GalleryScanResult(
                org.fossify.gallery.helpers.GalleryScanState.FAILED, cause = error))
            task.stopFetching()
            mediaLoadRequests.invalidate()
            mediaScanGeneration++
            android.util.Log.w("GalleryMedia", "Media scan executor rejected request", error)
            mIsGettingMedia = false
            mediaChangeGate.scanFinished(success = false)
            setMediaLoading(false)
            binding.mediaRefreshLayout.isRefreshing = false
            if (mPath == FAVORITES) {
                favoritesReturnCover?.showError(::retryFavoritesReturn)
                toast(R.string.folder_refresh_busy)
            } else {
                val hasContent = getMediaAdapter()?.media?.any { it is Medium } == true
                org.fossify.gallery.helpers.MediaLoadFailure.present(
                    this, binding.mediaEmptyTextPlaceholder, binding.mediaEmptyDescription,
                    binding.mediaEmptyTextPlaceholder2, hasContent, error
                ) { getMedia() }
                if (!hasContent) binding.mediaFastscroller.beGone()
            }
        }
    }

    /** Resolve the whole virtual album before publishing cache, adapter or scan data. */
    private fun resolveFavoriteAccess(onResolved: (Set<String>) -> Unit) {
        if (resolvingFavoriteAccess || isFinishing || isDestroyed) return
        resolvingFavoriteAccess = true
        // Include shortcuts/direct intents: migrate before reading the enabled
        // flag so a legacy-only lock cannot be mistaken for unprotected mode.
        prepareFavoritePasswordProtection { prepared ->
            if (isFinishing || isDestroyed) return@prepareFavoritePasswordProtection
            if (!prepared) {
                resolvingFavoriteAccess = false
                finish()
            } else {
                resolvePreparedFavoriteAccess(onResolved)
            }
        }
    }

    private fun resolvePreparedFavoriteAccess(onResolved: (Set<String>) -> Unit) {
        val generation = ++favoriteAuthGeneration
        val app = applicationContext
        fun current() = generation == favoriteAuthGeneration && !isFinishing && !isDestroyed
        ensureBackgroundThread {
            val paths = app.getFavoritePaths().toSet()
            val fullSnapshot = runCatching {
                FavoriteAccessSessionManager.captureSnapshot(paths, app::favoriteProtection)
            }.getOrNull()
            val protectionEnabled = app.config.isFavoritePasswordProtectionOn
            val visibleSnapshot = fullSnapshot?.let {
                if (protectionEnabled) it else FavoriteAccessPolicy.unprotectedSnapshot(it)
            }
            runOnUiThread {
                if (!current()) return@runOnUiThread
                if (fullSnapshot == null || visibleSnapshot == null) {
                    resolvingFavoriteAccess = false
                    finish()
                    return@runOnUiThread
                }

                fun publish(credential: org.fossify.gallery.helpers.Config.FavoriteCredentialProof?) {
                    ensureBackgroundThread {
                        val latestPaths = app.getFavoritePaths().toSet()
                        val latestSnapshot = runCatching {
                            FavoriteAccessSessionManager.captureSnapshot(paths, app::favoriteProtection)
                        }.getOrNull()
                        val stable = latestPaths == paths && latestSnapshot == fullSnapshot &&
                            app.config.isFavoritePasswordProtectionOn == protectionEnabled
                        runOnUiThread {
                            if (!current()) return@runOnUiThread
                            resolvingFavoriteAccess = false
                            val token = when {
                                !stable -> null
                                protectionEnabled && credential != null ->
                                    FavoriteAccessSessionManager.createFavoritesSession(
                                        favoriteOwnerId,
                                        fullSnapshot,
                                        authenticationSucceeded = true,
                                        credentialGeneration = credential.sessionGeneration,
                                        credentialIsCurrent = {
                                            app.config.isFavoriteCredentialCurrent(credential.credential)
                                        },
                                        protection = app::favoriteProtection
                                    )
                                !protectionEnabled ->
                                    FavoriteAccessSessionManager.createSession(
                                        favoriteOwnerId, visibleSnapshot, emptySet(), app::favoriteProtection
                                    )
                                else -> null
                            }
                            if (token == null) {
                                finish()
                                return@runOnUiThread
                            }
                            favoriteSessionId = token
                            onResolved(visibleSnapshot.entries.keys)
                        }
                    }
                }

                if (protectionEnabled) {
                    authenticateFavoritePassword { credential ->
                        if (!current()) return@authenticateFavoritePassword
                        if (credential == null) {
                            resolvingFavoriteAccess = false
                            finish()
                            return@authenticateFavoritePassword
                        }
                        publish(credential)
                    }
                } else {
                    publish(null)
                }
            }
        }
    }

    fun canAddFavoriteFromSource(path: String): Boolean {
        val snapshot = runCatching {
            FavoriteAccessSessionManager.captureSnapshot(listOf(path), applicationContext::favoriteProtection)
        }.getOrNull() ?: return false
        val entry = snapshot.entries[path] ?: return false
        return entry.locks.isEmpty() || FolderUnlockSessionManager.isMediaAuthorized(
            folderUnlockSessionId, path, config::isFolderProtected
        )
    }

    private fun isDirEmpty(): Boolean {
        if (isPhotoProfiles) return mMedia.isEmpty()
        return if (mMedia.isEmpty() && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) return false
            binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
            binding.mediaEmptyTextPlaceholder.beVisible()
            binding.mediaEmptyTextPlaceholder2.beGone()

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryToggleTemporarilyShowExcluded() {
        val update: (Boolean) -> Unit = { show ->
            config.temporarilyShowExcluded = show
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
            refreshMenuItems()
        }
        if (config.temporarilyShowExcluded) update(false)
        else handleExcludedFolderPasswordProtection { update(true) }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        getMedia()
        refreshMenuItems()
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mMedia) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            var currentGridDecoration: GridSpacingItemDecoration? = null
            if (binding.mediaGrid.itemDecorationCount > 0) {
                currentGridDecoration =
                    binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
                currentGridDecoration.items = media
            }

            val newGridDecoration = GridSpacingItemDecoration(
                spanCount = spanCount,
                spacing = spacing,
                isScrollingHorizontally = config.scrollHorizontally,
                addSideSpacing = config.fileRoundedCorners,
                items = media,
                useGridPosition = useGridPosition
            )
            if (currentGridDecoration.toString() != newGridDecoration.toString()) {
                if (currentGridDecoration != null) {
                    binding.mediaGrid.removeItemDecoration(currentGridDecoration)
                }
                binding.mediaGrid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(
                RadioItem(
                    id = i,
                    title = resources.getQuantityString(
                        org.fossify.commons.R.plurals.column_counts, i, i
                    )
                )
            )
        }

        val currentColumnCount = (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.mediaColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount = config.mediaColumnCnt
        handleGridSpacing()
        refreshMenuItems()
        getMediaAdapter()?.apply {
            notifyItemRangeChanged(0, media.size)
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun itemClicked(path: String) {
        if (mPath == FAVORITES && !FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, path)) {
            finish(); return
        }
        if (clearRevokedMediaPage()) {
            tryLoadGallery()
            return
        }
        if (mPath == FAVORITES && !isSetWallpaperIntent() &&
            !mIsGetImageIntent && !mIsGetVideoIntent && !mIsGetAnyIntent) {
            binding.mediaGrid.stopScroll()
            rememberFavoritesScrollAnchor()
            prepareFavoritesReturnCover()
        }
        binding.mediaGrid.stopScroll()
        hideKeyboard()
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()

            Glide.with(this)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                            setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            // Never consult the legacy system-player preference for internal taps.
            if (config.gestureVideoPlayer && !isPhotoProfiles && !OxygenClassicMotion.enabled(this)) {
                mWasFullscreenViewOpen = true
                launchGesturePlayer(path, folderSessionId = folderUnlockSessionId,
                    favoriteSessionId = if (mPath == FAVORITES) favoriteSessionId else null)
            } else {
                openInViewPager(path)
            }
        }
    }

    /** Internal folder/favorite taps need no whole-list snapshot before launching the viewer. */
    private fun openOrdinaryViewer(path: String) {
        if (mPath == FAVORITES && !FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, path)) {
            finish(); return
        }
        val request = viewerLaunchGate.begin(viewerLaunchBlocked()) ?: return
        val source = mPath
        val folderToken = folderUnlockSessionId
        val favoriteToken = favoriteSessionId
        val showAll = mShowAll
        val query = mCommittedSearchText
        val app = applicationContext
        val neighborSourceType = if (source == FAVORITES) org.fossify.gallery.helpers.ViewerSourceType.FAVORITES
            else org.fossify.gallery.helpers.ViewerSourceType.FOLDER
        val neighborSourceKey = if (showAll) SHOW_ALL else source
        val displayedOrder = getMediaAdapter()?.media ?: mMedia
        val committedVersion = getMediaAdapter()?.committedVersion ?: 0L
        val contentRevision = org.fossify.gallery.helpers.FolderContentRevision.current()
        val handoffContract = org.fossify.gallery.helpers.ViewerGridOrderStore.contract(
            config.getFolderSorting(if (showAll) SHOW_ALL else source), config.filterMedia, config.shouldShowHidden,
            mCommittedSearchText, if (source == FAVORITES) favoriteToken else folderToken, contentRevision)
        val neighborId = org.fossify.gallery.helpers.ViewerNeighborWindow.captureAround(displayedOrder, path)?.let { neighbors ->
            org.fossify.gallery.helpers.ViewerNeighborHandoffStore.offer(
                org.fossify.gallery.helpers.ViewerNeighborWindow(
                    path, neighborSourceType, neighborSourceKey, neighbors,
                    displayedMediaIsFinal && !mIsGettingMedia
                )
            )
        }
        val completePublicFolder = !showAll && folderToken == null && source.isNotEmpty() &&
            source != FAVORITES && source != RECYCLE_BIN && displayedMediaIsFinal && !mIsGettingMedia &&
            displayedMediaRequest == completedMediaRequest && mediaLoadRequests.isCurrent(displayedMediaRequest) &&
            getMediaAdapter() != null
        val catalogLookupStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        prepareViewerHandoff(request, neighborId, worker = {
            // Keep the existing constant-size return checkpoint before launching,
            // but never copy or validate the whole media list on the click path.
            // Handoff the committed visible grid order without conferring media access.
            val gridOrderId = if (!showAll && (source == FAVORITES || source.startsWith('/'))) {
                org.fossify.gallery.helpers.ViewerGridOrderStore.offer(source, path, displayedOrder,
                    committedVersion, handoffContract, includeCandidates = source.startsWith('/') && !showAll,
                    revision = contentRevision, isCurrent = { viewerLaunchGate.isCurrent(request) })
            } else null
            val checkpoint = if (completePublicFolder) FolderReturnCheckpoint.capture(app, source) else null
            val catalogId = if (completePublicFolder) {
                org.fossify.gallery.helpers.FolderViewerSnapshotStore.handoffId(app, source)
            } else null
            val catalogCount = org.fossify.gallery.helpers.FolderViewerSnapshotStore.handoffCount(catalogId)
            val favoriteCatalogId = if (source == FAVORITES && displayedMediaIsFinal && !mIsGettingMedia) {
                org.fossify.gallery.helpers.FavoriteViewerCatalogStore.handoffId(app)
            } else null
            val favoriteCatalogCount = org.fossify.gallery.helpers.FavoriteViewerCatalogStore.handoffCount(favoriteCatalogId)
            val hasCatalog = catalogId != null || favoriteCatalogId != null
            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                if (hasCatalog) "viewer_catalog_handoff" else "viewer_catalog_miss",
                catalogLookupStarted, catalogCount ?: favoriteCatalogCount ?: 0
            )
            PreparedViewerHandoff(gridOrderId, catalogId, favoriteCatalogId, checkpoint)
        }) { handoff ->
            if (source != mPath || showAll != mShowAll || query != mCommittedSearchText ||
                folderToken != folderUnlockSessionId || favoriteToken != favoriteSessionId ||
                (folderToken != null && !FolderUnlockSessionManager.isActive(folderToken)) ||
                (source == FAVORITES && !FavoriteAccessSessionManager.isCapturedPath(favoriteToken, path))) {
                return@prepareViewerHandoff false
            }
            pendingFolderReturn = handoff.checkpoint
            val viewerIntent = Intent(this, ViewPagerActivity::class.java).apply {
                folderToken?.let { putExtra(FOLDER_UNLOCK_SESSION, it) }
                if (source == FAVORITES) putExtra(FAVORITE_ACCESS_SESSION, favoriteToken)
                putExtra(PATH, path)
                putExtra(SHOW_ALL, showAll)
                putExtra(SHOW_FAVORITES, source == FAVORITES)
                putExtra(SHOW_RECYCLE_BIN, source == RECYCLE_BIN)
                putExtra(IS_FROM_GALLERY, true)
                if (query.isNotBlank()) putExtra(org.fossify.gallery.helpers.ViewerSearchPolicy.EXTRA_QUERY, query)
                neighborId?.let { putExtra(org.fossify.gallery.helpers.ViewerNeighborHandoffStore.EXTRA_ID, it) }
                handoff.orderId?.let { putExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID, it) }
                handoff.catalogId?.let { putExtra(org.fossify.gallery.helpers.FolderViewerSnapshotStore.EXTRA_ID, it) }
                handoff.favoriteCatalogId?.let { putExtra(org.fossify.gallery.helpers.FavoriteViewerCatalogStore.EXTRA_ID, it) }
            }
            launchAdmittedViewer(request, viewerIntent, path)
        }
    }

    private fun viewerLaunchBlocked(): Boolean = viewerLaunchInProgress || viewerReturn != null ||
        viewerSourceLease != null || viewerFallbackReturning || isFinishing || isDestroyed

    private data class PreparedViewerHandoff(
        val orderId: String?,
        val catalogId: String?,
        val favoriteCatalogId: String? = null,
        val checkpoint: FolderReturnCheckpoint? = null,
        val photoAccess: org.fossify.gallery.helpers.PhotoProfileLoadContext? = null,
    )

    private fun cancelViewerHandoff() {
        viewerLaunchGate.cancel()
        viewerHandoffDeadline.stop()
        ViewerAccessCoordinator.cancel(viewerHandoffTask)
        viewerHandoffTask = null
        org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(pendingViewerNeighborId)
        pendingViewerNeighborId = null
    }

    private fun prepareViewerHandoff(
        ticket: Long, neighborId: String?, worker: () -> PreparedViewerHandoff?,
        launch: (PreparedViewerHandoff) -> Boolean,
    ) {
        pendingViewerNeighborId = neighborId
        try {
            viewerHandoffTask = ViewerAccessCoordinator.submit {
                val prepared = if (viewerLaunchGate.isCurrent(ticket)) runCatching(worker).getOrNull() else null
                runOnUiThread {
                    val current = viewerLaunchGate.isCurrent(ticket)
                    if (current) {
                        viewerHandoffDeadline.stop()
                        viewerHandoffTask = null
                        pendingViewerNeighborId = null
                    }
                    val launched = current && !viewerLaunchBlocked() && prepared != null &&
                        runCatching { launch(prepared) }.getOrDefault(false)
                    if (!launched) {
                        viewerLaunchGate.consume(ticket)
                        org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborId)
                        org.fossify.gallery.helpers.ViewerGridOrderStore.clear(prepared?.orderId)
                    }
                }
            }
            viewerHandoffDeadline.arm {
                if (viewerLaunchGate.isCurrent(ticket)) cancelViewerHandoff()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            cancelViewerHandoff()
        }
    }

    private fun launchAdmittedViewer(ticket: Long, intent: Intent, path: String): Boolean {
        if (!viewerLaunchGate.consume(ticket)) return false
        viewerLaunchInProgress = true
        viewerLaunchOrigin = currentViewerLaunchOrigin()
        return runCatching {
            launchPhotoViewerWithSharedElement(intent, path)
            true
        }.getOrElse {
            viewerLaunchInProgress = false
            viewerLaunchOrigin = null
            mWasFullscreenViewOpen = false
            photoViewerTransition = false
            releaseViewerSourceLease()
            retireTransitionSession("launch_failed")
            setExitSharedElementCallback(null as SharedElementCallback?)
            pendingFolderReturn = null
            pendingViewerReturnPath = null
            false
        }
    }

    private fun launchPhotoViewerWithSharedElement(intent: Intent, path: String) {
        mWasFullscreenViewOpen = true
        endViewerReturn("next_launch")
        releaseViewerSourceLease()
        viewerSourceTrace = null
        retireTransitionSession("next_launch")
        val session = MediaTransitionHandoff.create()
        transitionSession = session
        MediaTransitionHandoff.offer(intent, session)
        viewerReturnGeneration++
        viewerReturnMapped = false
        val returnLaunch = ++viewerReturnLaunch
        intent.putExtra(PhotoViewerTransition.EXTRA_LAUNCH, returnLaunch)
        val entryId = ViewerEntryDiagnostics.newRequest()
        if (entryId != 0L) {
            val type = getMediaAdapter()?.media?.firstOrNull { it is Medium && it.path == path } as? Medium
            intent.putExtra(PhotoViewerTransition.EXTRA_ENTRY_ID, entryId)
            intent.putExtra(ViewerEntryDiagnostics.EXTRA_DIAGNOSTIC_PROCESS, GalleryDiagnosticLogger.session())
            intent.putExtra(ViewerEntryDiagnostics.EXTRA_MEDIA_TYPE, type?.type ?: 0)
            ViewerEntryDiagnostics.event(entryId, "entry_requested") {
                val origin = mediaDiagnostic(displayedMediaRequest)
                mapOf("viewerSessionId" to entryId, "runtimeSessionId" to session.entry.sessionId,
                    "runtimeOperationId" to session.entry.operationId, "media" to type?.type, "source" to when {
                    isPhotoProfiles -> "PHOTO_PROFILE"
                    mPath == FAVORITES -> "FAVORITES"
                    mPath == RECYCLE_BIN -> "RECYCLE_BIN"
                    mShowAll -> "ALL_MEDIA"
                    else -> "FOLDER"
                }, "sourceRequestId" to origin?.id, "scopeId" to origin?.scopeId,
                    "adapterVersion" to getMediaAdapter()?.committedVersion,
                    "mediaId" to GalleryDiagnosticLogger.anonymousId(path))
            }
        }

        fun fallback(reason: String) {
            ViewerEntryDiagnostics.event(entryId, "source_rejected") { mapOf("reason" to reason) }
            launchViewerWithoutSharedElement(intent)
        }

        if (!OxygenClassicMotion.enabled(this)) {
            fallback("animations_disabled")
            return
        }
        val adapter = getMediaAdapter() ?: run { fallback("adapter_unavailable"); return }
        viewerReentryObservedForCurrentLaunch = false
        val position = adapter.media.indexOfFirst { (it as? Medium)?.path == path }
        if (position < 0) { fallback("position_unavailable"); return }
        val holder = binding.mediaGrid.findViewHolderForAdapterPosition(position)
            ?: run { fallback("holder_unavailable"); return }
        val medium = adapter.media.getOrNull(position) as? Medium
        // These dedicated renderers do not publish a fitted bitmap for shared capture.
        if (holder.bindingAdapterPosition != position || medium?.path != path || medium.isGIF() || medium.isSVG()) {
            fallback("unsupported_or_stale"); return
        }
        val thumbnail = holder.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
            ?: run { fallback("thumbnail_view_unavailable"); return }
        if (!thumbnail.isAttachedToWindow || thumbnail.width <= 0 || thumbnail.height <= 0 ||
            thumbnail.drawable == null || !org.fossify.gallery.helpers.PhotoViewerReturnTarget.isLoadedFor(
                path,
                thumbnail.getTag(R.id.media_bound_path) as? String,
                thumbnail.getTag(R.id.thumbnail_request_key),
                thumbnail.getTag(R.id.thumbnail_loaded_key)
            )) {
            fallback("thumbnail_not_ready"); return
        }

        ViewerEntryDiagnostics.event(entryId, "source_validated") {
            mapOf(
                "matchesBound" to true,
                "drawable" to true,
                "thumbnail" to ViewerEntryDiagnostics.viewState(thumbnail),
                "visibleBounds" to android.graphics.Rect().takeIf(thumbnail::getGlobalVisibleRect)?.toShortString(),
                "sourceDecor" to ViewerEntryDiagnostics.viewState(window.decorView),
            )
        }
        return runCatching {
            pendingViewerReturnPath = path
            val source = GridViewerSource(path, holder, thumbnail, session.entry, session.leases())
            viewerSourceLease = source
            check(source.leases.acquire(source.entry, Resource.SOURCE) {
                holder.setIsRecyclable(true)
                logViewerGridSource("grid_source_released")
            })
            holder.setIsRecyclable(false)
            check(source.leases.acquire(source.entry, Resource.SOURCE_NAME) {
                source.nameOwned = false
                source.nameTimeout?.let(albumTransitionHandler::removeCallbacks)
                source.nameTimeout = null
                if (thumbnail.transitionName == PhotoViewerTransition.ELEMENT_NAME &&
                    thumbnail.getTag(R.id.media_bound_path) == path) thumbnail.transitionName = null
            })
            if (entryId != 0L) {
                viewerSourceTrace = GridViewerSourceTrace(path, entryId, System.identityHashCode(holder), WeakReference(thumbnail))
                ViewerEntryDiagnostics.event(entryId, "grid_source_lease_acquired") {
                    mapOf("adapterVersion" to adapter.committedVersion)
                }
            }
            OxygenClassicActivityMotion.configureShared(this)
            thumbnail.transitionName = PhotoViewerTransition.ELEMENT_NAME
            logViewerGridSource("grid_source_named")
            intent.putExtra(PhotoViewerTransition.EXTRA_PATH, path)
            window.sharedElementExitTransition = OxygenClassicActivityMotion.mediaTransition(
                PhotoViewerTransition.DURATION_MS, onTerminal = { cancelled ->
                    viewerSourceLease?.takeIf { viewerReturnLaunch == returnLaunch }?.let {
                        // Android keeps the captured view by reference. It can still hide it after
                        // the local animation ends, so keep the holder leased until stop or reentry.
                        logViewerGridSource(if (cancelled) "grid_source_exit_cancel" else "grid_source_exit_end")
                        retireViewerSourceName(it)
                    }
                })
            window.sharedElementReenterTransition = OxygenClassicActivityMotion.mediaTransition(
                PhotoViewerTransition.DURATION_MS,
                onStart = {
                    viewerReturn?.takeIf { returnLaunch == viewerReturnLaunch }?.let {
                        ViewerReturnDiagnostics.event(it.requestId, "grid_transition_start")
                    }
                },
                onTerminal = { cancelled -> onGridReturnTransitionTerminal(returnLaunch, cancelled) },
            )
            setExitSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                    val name = PhotoViewerTransition.ELEMENT_NAME
                    if (name !in names) return
                    val currentSource = viewerSourceLease?.takeIf {
                        session === transitionSession && session.entryActive && it.entry == session.entry && it.nameOwned
                    }
                    val image = currentSource?.image
                    val position = currentSource?.let { viewerReturnPosition(it.path) }
                    if (currentSource != null && image != null && position != null &&
                        findViewerReturnHolder(currentSource.path, position) === currentSource.holder &&
                        image.isAttachedToWindow && image.drawable != null && hasUsableViewerReturnBounds(image) &&
                        PhotoViewerReturnTarget.isLoadedFor(currentSource.path, image.getTag(R.id.media_bound_path) as? String,
                            image.getTag(R.id.thumbnail_request_key), image.getTag(R.id.thumbnail_loaded_key))) {
                        sharedElements[name] = image
                        logViewerGridSource("grid_source_map")
                    } else sharedElements.remove(name)
                }

                override fun onSharedElementsArrived(names: MutableList<String>, elements: MutableList<View>,
                    listener: OnSharedElementsReadyListener) {
                    if (session === transitionSession && session.entryActive &&
                        viewerSourceLease?.image?.let { image -> elements.any { it === image } } == true) {
                        logViewerGridSource("grid_source_exit_captured")
                    }
                    listener.onSharedElementsReady()
                }
            })
            val options = ActivityOptions.makeSceneTransitionAnimation(this, thumbnail, PhotoViewerTransition.ELEMENT_NAME)
            startActivityForResult(intent, PhotoViewerTransition.REQUEST_CODE, options.toBundle())
            if (viewerSourceLease === source && source.nameOwned) {
                source.nameTimeout = Runnable {
                    if (viewerSourceLease === source && viewerReturnLaunch == returnLaunch) {
                        logViewerGridSource("grid_source_exit_timeout")
                        retireViewerSourceName(source)
                    }
                }.also { albumTransitionHandler.postDelayed(it, PhotoViewerTransition.READY_TIMEOUT_MS) }
            }
            Unit
        }.getOrElse {
            releaseViewerSourceLease()
            setExitSharedElementCallback(null as SharedElementCallback?)
            pendingViewerReturnPath = null
            fallback("launch_failed")
        }
    }

    private fun installGridReturnCallback(request: GridReturn) {
        val launch = viewerReturnLaunch
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                val name = PhotoViewerTransition.ELEMENT_NAME
                if (name !in names) return
                if (launch == viewerReturnLaunch && isViewerReturnCurrent(request)) mapViewerReturnTarget(sharedElements)
                else sharedElements.remove(name)
            }

            override fun onSharedElementStart(names: MutableList<String>, elements: MutableList<View>, snapshots: MutableList<View>) {
                logReturnCapture("grid_capture_start", elements, snapshots)
            }

            override fun onSharedElementEnd(names: MutableList<String>, elements: MutableList<View>, snapshots: MutableList<View>) {
                logReturnCapture("grid_capture_end", elements, snapshots)
            }

            private fun logReturnCapture(event: String, elements: List<View>, snapshots: List<View>) {
                if (launch != viewerReturnLaunch || !isViewerReturnCurrent(request)) return
                ViewerReturnDiagnostics.event(request.requestId, event) {
                    val image = request.target?.image?.takeIf { target -> elements.any { it === target } }
                    val snapshot = snapshots.firstOrNull()
                    ViewerReturnDiagnostics.thumbnailState(image) + mapOf(
                        "launchId" to launch, "snapshotSlots" to snapshots.size,
                        "snapshot" to ViewerEntryDiagnostics.viewState(snapshot),
                        "snapshotDrawable" to (snapshot as? ImageView)?.drawable?.javaClass?.simpleName,
                        "bounds" to image?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
                        "scaleType" to image?.scaleType, "matrix" to image?.imageMatrix?.toShortString())
                }
            }
        })
    }

    private fun mapViewerReturnTarget(elements: MutableMap<String, View>) {
        val name = PhotoViewerTransition.ELEMENT_NAME
        val request = viewerReturn
        val expected = request?.target
        val current = if (request != null && expected != null && isViewerReturnCurrent(request)) {
            readViewerReturnTarget(request, expected.position)
        } else null
        viewerReturnMapped = expected?.matches(current) == true
        if (viewerReturnMapped && current != null && request != null) {
            request.leases.acquire(request.operation, Resource.TARGET_NAME) {
                if (current.image.transitionName == name &&
                    current.image.getTag(R.id.media_bound_path) == request.path) current.image.transitionName = null
            }
            current.image.transitionName = name
            elements[name] = current.image
            observeFirstViewerReturnFrame(request, current)
        } else elements.remove(name)
        if (request != null) {
            logViewerGridSource("grid_source_return_map", request.requestId)
            ViewerReturnDiagnostics.event(request.requestId, "grid_map") {
                ViewerReturnDiagnostics.thumbnailState(current?.image) + mapOf(
                    "mediaId" to GalleryDiagnosticLogger.anonymousId(request.path),
                    "holderId" to current?.holder?.let(System::identityHashCode),
                    "accepted" to viewerReturnMapped, "expectedVersion" to expected?.position?.version,
                    "adapterVersion" to getMediaAdapter()?.committedVersion, "scrollState" to binding.mediaGrid.scrollState,
                    "pendingLayout" to binding.mediaGrid.isLayoutRequested)
            }
            if (expected != null && !viewerReturnMapped) endViewerReturn("target_changed_before_mapping")
        }
    }

    private fun retireTransitionSession(reason: String) {
        val session = transitionSession ?: return
        transitionSession = null
        MediaTransitionHandoff.withdraw(session)
        if (reason != "grid_destroyed" || !session.viewerAttached || session.returnOperation != null) {
            if (session.outcome == null) session.cancel(reason)
            session.clean()
        }
    }

    private fun launchViewerWithoutSharedElement(intent: Intent) {
        viewerReentryObservedForCurrentLaunch = false
        releaseViewerSourceLease()
        pendingViewerReturnPath = null
        intent.removeExtra(PhotoViewerTransition.EXTRA_PATH)
        intent.putExtra(PhotoViewerTransition.EXTRA_FADE_ONLY, true)
        startActivityForResult(intent, PhotoViewerTransition.REQUEST_CODE)
        if (OxygenClassicMotion.enabled(this)) {
            overridePendingTransition(R.anim.photo_viewer_fade_in, R.anim.photo_viewer_fade_out)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    private fun openInViewPager(path: String) {
        if (!isPhotoProfiles) {
            openOrdinaryViewer(path)
            return
        }
        val requestedPhotoId = photoProfileId ?: return
        val app = applicationContext
        val token = photoToken()
        val scope = PhotoProfileScope.capture(app, requestedPhotoId, token) ?: return
        if (photoNeedsUnlock || scope.selectedId != requestedPhotoId) return
        val query = mCommittedSearchText
        val adapter = getMediaAdapter() ?: return
        val displayedOrder = adapter.media
        val selected = (displayedOrder.firstOrNull { it is Medium && it.path == path } as? Medium)?.copy() ?: return
        val ticket = viewerLaunchGate.begin(viewerLaunchBlocked()) ?: return
        val version = adapter.committedVersion
        val revision = org.fossify.gallery.helpers.PhotoContentRevision.current()
        val neighborId = org.fossify.gallery.helpers.ViewerNeighborWindow.captureAround(displayedOrder, path)?.let { neighbors ->
            org.fossify.gallery.helpers.ViewerNeighborHandoffStore.offer(
                org.fossify.gallery.helpers.ViewerNeighborWindow(
                    path, org.fossify.gallery.helpers.ViewerSourceType.PHOTO_PROFILE,
                    requestedPhotoId, neighbors, displayedMediaIsFinal && !mIsGettingMedia
                )
            )
        }
        prepareViewerHandoff(ticket, neighborId, worker = worker@{
            val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            val access = org.fossify.gallery.helpers.PhotoProfileLoadContext.captureNeighbors(
                app, requestedPhotoId, token, listOf(path)
            )?.takeIf { it.scope == scope && it.revision == revision } ?: return@worker null
            val (safe, _) = org.fossify.gallery.helpers.PhotoAccessValidationBatch(app, access,
                cancelled = { Thread.currentThread().isInterrupted || !viewerLaunchGate.isCurrent(ticket) },
                directVisibilityOnly = scope.allFiles || android.os.Build.VERSION.SDK_INT < 30
            ).validate(listOf(selected))
            if (safe.none { it.path == path } || !access.isCurrent(app) ||
                !viewerLaunchGate.isCurrent(ticket)) return@worker null
            val catalogId = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.handoffId(app, requestedPhotoId, token)
            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                if (catalogId == null) "viewer_catalog_miss" else "viewer_catalog_hit", started)
            val orderId = org.fossify.gallery.helpers.ViewerGridOrderStore.offer(
                PhotoProfileMediaLoader.PATH + requestedPhotoId + token.orEmpty(), path, displayedOrder,
                version = version,
                contract = org.fossify.gallery.helpers.ViewerGridOrderStore.contract(
                    scope.sorting, scope.filter, scope.hiddenPreference, query, token, revision),
                revision = revision, isCurrent = { viewerLaunchGate.isCurrent(ticket) }
            )
            PreparedViewerHandoff(orderId, catalogId, photoAccess = access)
        }) { handoff ->
            val access = handoff.photoAccess ?: return@prepareViewerHandoff false
            if (!isPhotoProfiles || photoNeedsUnlock || photoProfileId != requestedPhotoId ||
                photoToken() != token || mCommittedSearchText != query || !access.isCurrent(app) ||
                getMediaAdapter()?.media?.any { it is Medium && it.path == path } != true) {
                return@prepareViewerHandoff false
            }
            val viewerIntent = Intent(this, ViewPagerActivity::class.java).apply {
                putExtra(PATH, path)
                putExtra(PhotoProfileMediaLoader.EXTRA_ID, requestedPhotoId)
                putExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION, token)
                putExtra(IS_FROM_GALLERY, true)
                if (query.isNotBlank()) putExtra(org.fossify.gallery.helpers.ViewerSearchPolicy.EXTRA_QUERY, query)
                handoff.catalogId?.let { putExtra(org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.EXTRA_ID, it) }
                neighborId?.let { putExtra(org.fossify.gallery.helpers.ViewerNeighborHandoffStore.EXTRA_ID, it) }
                handoff.orderId?.let { putExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID, it) }
            }
            pendingFolderReturn = null
            binding.mediaGrid.stopScroll()
            photoScroll.clear()
            rememberPhotoScrollAnchor()
            photoViewerTransition = true
            launchAdmittedViewer(ticket, viewerIntent, path)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        openPath(
            path = path,
            forceChooser = false,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean, request: Long) {
        if (mPath == FAVORITES) {
            val generation = ++favoritePublicationGeneration
            val candidateMedia = ArrayList(media)
            val token = favoriteSessionId
            val revision = favoriteRelationshipRevision.get()
            ViewerAccessCoordinator.cancel(favoritePublicationTask)
            favoritePublicationTask = try { ViewerAccessCoordinator.submit {
                if (generation != favoritePublicationGeneration || Thread.currentThread().isInterrupted) return@submit
                val relationships = applicationContext.getFavoritePaths().toSet()
                val candidates = candidateMedia.filterIsInstance<Medium>()
                    .distinctBy { it.path }.filter { it.path in relationships }
                val batch = FavoriteAccessPolicy.validateItems(token, candidates, pathOf = { it.path }) ?: return@submit
                if (applicationContext.getFavoritePaths().toSet() != relationships) return@submit
                val sorting = config.getFolderSorting(FAVORITES)
                val grouping = config.getFolderGrouping(FAVORITES)
                val sorted = ArrayList(batch.items.map { it.copy() })
                MediaFetcher(applicationContext).sortMedia(sorted, sorting)
                val presented = MediaFetcher(applicationContext).groupMedia(sorted, FAVORITES)
                runOnUiThread {
                    if (generation != favoritePublicationGeneration || !mediaLoadRequests.isCurrent(request) ||
                        (isFromCache && completedMediaRequest == request) || token != favoriteSessionId || mPath != FAVORITES || isFinishing || isDestroyed ||
                        revision != favoriteRelationshipRevision.get() ||
                        !FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)) return@runOnUiThread
                    if (sorting != config.getFolderSorting(FAVORITES) || grouping != config.getFolderGrouping(FAVORITES)) {
                        gotMedia(candidateMedia, isFromCache, request)
                    } else {
                        val captured = FavoriteAccessSessionManager.getCapturedPaths(token)
                        if (!isFromCache && captured?.containsAll(relationships) != true) {
                            sourceProtectionRefreshPending = true
                            FavoriteAccessSessionManager.revokeSession(token)
                            return@runOnUiThread
                        }
                        publishMedia(presented, isFromCache, request, select = {
                            val valid = revision == favoriteRelationshipRevision.get() &&
                                token == favoriteSessionId &&
                                FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)
                            if (valid) {
                                synchronized(favoriteObserverLock) { observedFavoriteRelationships = relationships }
                                favoriteCatalogRelationshipPaths = relationships
                                favoriteInteractiveSnapshot = org.fossify.gallery.helpers.FavoriteInteractiveSnapshot(
                                    request, revision, relationships, batch.validation)
                                displayedFavoriteRelationshipRevision = revision
                                logVirtualSortDecision(-1L, "FAVORITES", sorting, sorted.size,
                                    if (isFromCache) "cache_authorized" else "final_scan_validated")
                            }
                            valid
                        })
                    }
                }
            } } catch (_: java.util.concurrent.RejectedExecutionException) {
                finish()
                null
            }
            return
        }
        if (mPath == RECYCLE_BIN) {
            val generation = ++recyclePublicationGeneration
            val source = ArrayList(media)
            val sorting = config.getFolderSorting(RECYCLE_BIN)
            val grouping = config.getFolderGrouping(RECYCLE_BIN)
            val requestPath = mPath
            ensureBackgroundThread {
                val presented = if (isFromCache) source else {
                    val sorted = ArrayList(source.filterIsInstance<Medium>().map { it.copy() })
                    MediaFetcher(applicationContext).sortMedia(sorted, sorting)
                    MediaFetcher(applicationContext).groupMedia(sorted, requestPath)
                }
                runOnUiThread {
                    if (generation != recyclePublicationGeneration || !mediaLoadRequests.isCurrent(request) ||
                        requestPath != mPath || isFinishing || isDestroyed) return@runOnUiThread
                    if (!isFromCache && (sorting != config.getFolderSorting(RECYCLE_BIN) ||
                            grouping != config.getFolderGrouping(RECYCLE_BIN))) {
                        gotMedia(source, false, request)
                    } else publishMedia(presented, isFromCache, request)
                }
            }
            return
        }
        if (!isPhotoProfiles && !mShowAll && mPath.startsWith('/') && mPath != RECYCLE_BIN) {
            val version = folderPublicationVersion.incrementAndGet()
            val token = folderUnlockSessionId
            val folder = mPath
            val candidate = ArrayList(media)
            ensureBackgroundThread {
                if (!mediaLoadRequests.isCurrent(request)) return@ensureBackgroundThread
                val configuration = FolderCachePreviewAccess.configuration(applicationContext, folder)
                val sorting = config.getFolderSorting(folder)
                val read = folderReadResult.takeIf { folderReadRequest == request }
                val storageAccess = if (isFromCache && read == null)
                    FolderCachePreviewAccess.capture(applicationContext, folder) else read?.accessCurrent ?: { false }
                val access = {
                    storageAccess() &&
                        configuration == FolderCachePreviewAccess.configuration(applicationContext, folder) &&
                        folder == mPath && token == folderUnlockSessionId &&
                        (token == null || FolderUnlockSessionManager.isActive(token))
                }
                val safe = ArrayList(candidate.filter { item ->
                    (item as? Medium)?.let {
                        folderPublicationAllowed(it.path, token) &&
                            (when {
                                mIsGetImageIntent && !mIsGetVideoIntent -> it.type == TYPE_IMAGES
                                mIsGetVideoIntent && !mIsGetImageIntent -> it.type == TYPE_VIDEOS
                                else -> it.type and config.filterMedia != 0
                            }) &&
                            (!it.path.contains("/.") || config.shouldShowHidden) &&
                            (config.temporarilyShowExcluded ||
                                it.parentPath.isThisOrParentIncluded(config.includedFolders) ||
                                !it.parentPath.isThisOrParentExcluded(config.excludedFolders))
                    } != false
                })
                // Reapply the latest sort to the full source, including the final
                // scan that may have been sorted before a UI choice changed.
                val sorted = ArrayList(safe.filterIsInstance<Medium>().map { it.copy() })
                MediaFetcher(applicationContext).sortMedia(sorted, sorting)
                val presented = MediaFetcher(applicationContext).groupMedia(sorted, folder)
                runOnUiThread {
                    if (version == folderPublicationVersion.get() && folder == mPath && token == folderUnlockSessionId &&
                        mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed &&
                        (token == null || FolderUnlockSessionManager.isActive(token))) {
                        if (isFromCache && completedMediaRequest == request) return@runOnUiThread
                        if (configuration != FolderCachePreviewAccess.configuration(applicationContext, folder)) {
                            getMedia(reason = "publication_configuration_changed")
                        } else if (sorting != config.getFolderSorting(folder)) {
                            gotMedia(candidate, isFromCache, request)
                        } else if (!storageAccess()) {
                            discardInaccessibleGrid()
                            renderMediaScanState(request, org.fossify.gallery.helpers.GalleryScanResult(
                                org.fossify.gallery.helpers.GalleryScanState.FAILED,
                                cause = SecurityException("Folder access expired")))
                            binding.mediaRefreshLayout.isRefreshing = false
                            toast(R.string.media_load_permission_error)
                        } else if (access()) {
                            val stage = when {
                                !isFromCache && read?.status == org.fossify.gallery.helpers.FolderReadStatus.COMPLETE ->
                                    DisplayedSourceStage.SCAN_COMPLETE
                                read != null -> DisplayedSourceStage.SCAN_PARTIAL
                                else -> DisplayedSourceStage.CACHE_PREVIEW
                            }
                            publishMedia(presented, isFromCache, request, stage, access)
                        }
                    }
                }
            }
            return
        }
        publishMedia(media, isFromCache, request)
    }

    private fun publishMedia(
        mediaSnapshot: ArrayList<ThumbnailItem>, isFromCache: Boolean, request: Long,
        stage: DisplayedSourceStage? = null, access: (() -> Boolean)? = null,
        select: (() -> Boolean)? = null,
    ) = publishMediaSnapshot(mediaSnapshot, isFromCache, request, stage, access, select, null)

    private fun publishMediaSnapshot(
        mediaSnapshot: ArrayList<ThumbnailItem>, isFromCache: Boolean, request: Long,
        stage: DisplayedSourceStage?, access: (() -> Boolean)?, select: (() -> Boolean)?,
        diagnosticStage: MediaPublicationStage?,
        verifiedRestorations: Set<String>? = null,
    ) {
        runOnUiThread {
            if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed ||
                (isFromCache && (completedMediaRequest == request ||
                    (!isPhotoProfiles && mediaScanTerminals.result(request) != null)))) return@runOnUiThread

            if (access?.invoke() == false) {
                if (displayedFolderSnapshot?.accessCurrent?.invoke() == false) discardInaccessibleGrid()
                return@runOnUiThread
            }

            if (mPath == RECYCLE_BIN && !isFromCache) {
                val currentRevision = org.fossify.gallery.helpers.RecycleBinMembershipRevision.current()
                if (recycleBinLoadRevision != currentRevision) {
                    getMedia(reason = "recycle_bin_members_changed_during_load")
                    return@runOnUiThread
                }
            }
            if (mPath == FAVORITES && !isFromCache && favoriteLoadRevision != favoriteRelationshipRevision.get()) {
                getMedia(reason = "favorite_members_changed_during_load")
                return@runOnUiThread
            }

            val physicalFolder = !isPhotoProfiles && !mShowAll && mPath.startsWith('/') && mPath != RECYCLE_BIN
            val read = folderReadResult.takeIf { physicalFolder && folderReadRequest == request && !isFromCache }
            val outcomeForRequest = mediaScanOutcome.takeIf { mediaScanOutcomeRequest == request }
            if (read?.accessCurrent?.invoke() == false ||
                (!isPhotoProfiles && !isFromCache && outcomeForRequest?.state == GalleryScanState.ACCESS_REQUIRED)) {
                discardInaccessibleGrid()
                renderMediaScanState(request, GalleryScanResult(GalleryScanState.ACCESS_REQUIRED))
                toast(R.string.media_load_permission_error)
                return@runOnUiThread
            }

            // Profile stages enter through submitPhotoGridResult. A local sort preview
            // uses the lowest priority, so it cannot replace a queued profile stage.
            val priority = if (isFromCache) stage?.ordinal ?: 0 else DisplayedSourceStage.entries.size
            val scope = if (mShowAll) SHOW_ALL else mPath
            if (retainedFolderScan?.request == request && detachedFolderPublication.deferIfMoving(
                    GridPublication(priority) {
                        publishMediaSnapshot(mediaSnapshot, isFromCache, request, stage, access, select,
                            diagnosticStage, verifiedRestorations)
                    }
                ) { priority >= it.stage }) return@runOnUiThread
            val sorting = config.getFolderSorting(scope)
            val grouping = config.getFolderGrouping(scope)
            if ((!isPhotoProfiles || !photoNeedsUnlock) &&
                deferMediaGridPublication(priority) {
                    if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed) {
                        if (sorting == config.getFolderSorting(scope) && grouping == config.getFolderGrouping(scope)) {
                            publishMediaSnapshot(mediaSnapshot, isFromCache, request, stage, access, select,
                                diagnosticStage, verifiedRestorations)
                        } else getMedia(reason = "publication_configuration_changed")
                    }
                }) return@runOnUiThread

            val authoritative = !isFromCache && (outcomeForRequest?.complete == true ||
                isPhotoProfiles && diagnosticStage == MediaPublicationStage.FINAL)
            if (authoritative && verifiedRestorations == null) {
                val candidates = viewerDeletions.restorationCandidates(mediaSnapshot, request)
                if (candidates.isNotEmpty()) {
                    val revision = org.fossify.gallery.helpers.FolderContentRevision.current()
                    lifecycleScope.launch {
                        val verified = withContext(Dispatchers.IO) {
                            candidates.filterTo(HashSet()) { path ->
                                runCatching { getDoesFilePathExist(path) }.getOrDefault(false)
                            }
                        }
                        if (revision == org.fossify.gallery.helpers.FolderContentRevision.current()) {
                            publishMediaSnapshot(mediaSnapshot, isFromCache, request, stage, access, select,
                                diagnosticStage, verified)
                        } else if (mediaLoadRequests.isCurrent(request)) {
                            getMedia(reason = "media_changed_during_restore_check")
                        }
                    }
                    return@runOnUiThread
                }
            }

            if (select?.invoke() == false) return@runOnUiThread
            if (mPath == FAVORITES && !isFromCache) {
                displayedFavoriteRelationshipRevision = favoriteLoadRevision
            }
            if (mPath == RECYCLE_BIN && !isFromCache) displayedRecycleBinRevision = recycleBinLoadRevision

            val outcome = outcomeForRequest ?: GalleryScanResult(GalleryScanState.COMPLETE)
            if (!isPhotoProfiles) {
                if (!renderMediaScanState(request, if (isFromCache) null else outcome)) return@runOnUiThread
            } else if (!isFromCache) {
                mIsGettingMedia = false
                setMediaLoading(false)
            }
            val media = viewerDeletions.filter(mediaSnapshot, request,
                verifiedRestorations.orEmpty().takeIf { authoritative }.orEmpty())
            if (stage != null && access != null) {
                displayedFolderSnapshot = DisplayedFolderSnapshot(request, displayedSourceVersion + 1, stage, access)
                if (mShowAll) mediaPageAccess = access
            }
            if (!isFromCache) completedMediaRequest = request
            checkLastMediaChanged()
            if (!isFromCache && pollAllowed() && mediaChangeGate.scanFinished(success = outcome.complete)) {
                binding.mediaGrid.post {
                    if (mediaLoadRequests.isCurrent(request) && !mIsGettingMedia && pollAllowed()) getMedia()
                }
            }
            // Compare against the last published source snapshot, not only the
            // visible filtered rows. COMPLETE still flows through below even when
            // the preview already showed the same ordered media.
            val displayedContentChanged = !org.fossify.gallery.helpers.MediaUpdatePolicy
                .sameDisplayedContent(mMedia, media)
            // A search over an earlier preview must not overwrite a newer stage of this request.
            if (isPhotoProfiles && displayedContentChanged) mediaSearchGeneration++
            displayedSourceVersion++
            mMedia = ArrayList(media)
            mediaDiagnostic(request)?.published(
                diagnosticStage ?: when {
                    stage == DisplayedSourceStage.SCAN_PARTIAL || (!isFromCache && !outcome.complete) -> MediaPublicationStage.PARTIAL
                    isFromCache -> MediaPublicationStage.PREVIEW
                    else -> MediaPublicationStage.FINAL
                },
                media.size, displayedSourceVersion, when {
                    stage == DisplayedSourceStage.CACHE_PREVIEW ||
                        diagnosticStage == MediaPublicationStage.PREVIEW -> "METADATA_PREVIEW"
                    stage == DisplayedSourceStage.INDEX_PREVIEW -> "INDEX_PREVIEW"
                    else -> "UNKNOWN"
                }
            )
            if (isPhotoProfiles && !isFromCache) {
                photoLoadCompleted = true
                photoLoadedKey = photoDisplayKey()
                photoLoadedRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
            }
            // The slideshow shortcut is only useful once at least one authorized
            // media item has been published; refresh after cache and final scan.
            if (displayedContentChanged && (isPhotoProfiles || org.fossify.gallery.helpers.ToolbarQuickActions.SLIDESHOW in
                (if (mPath.isEmpty()) config.toolbarHomeActions else config.toolbarFolderActions))) {
                refreshMenuItems()
            }
            displayedMediaRequest = request
            displayedMediaIsFinal = false
            if (!isFromCache && media.isNotEmpty()) setMediaLoading(true)
            if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "folder_snapshot_selected", request,
                    mapOf("snapshotStage" to (displayedFolderSnapshot?.stage?.name ?:
                        if (mPath == FAVORITES && isFromCache) "CACHE_AUTHORIZED" else null),
                        "generation" to displayedSourceVersion, "itemCount" to media.size,
                        "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath)))
            // A partial source cannot decide the empty state.
            binding.mediaEmptyDescription.beGone()
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache && outcome.complete)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache && outcome.complete)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = if (mPath == FAVORITES)
                    getString(org.fossify.commons.R.string.no_items_found)
                else getString(R.string.no_media_with_filters)
                if (mPath == FAVORITES) binding.mediaEmptyTextPlaceholder2.beGone()
                if (isPhotoProfiles) {
                    updatePhotoEmptyState()
                }
            }
            updateMediaFastScrollerVisibility(media.any { it is Medium })
            if (isPhotoProfiles && media.isEmpty() && !isFromCache) {
                getMediaAdapter()?.finishActMode()
                binding.mediaGrid.adapter = null
                photoScroll.clear()
            } else if (mPath == FAVORITES && media.isEmpty() && !isFromCache) {
                binding.mediaGrid.adapter = null
                favoritesScroll.clear()
                val token = favoriteSessionId
                favoritesReturnCover?.revealAfterLayout { isFavoritesReturnCurrent(request, token) }
            } else {
                setupAdapter {
                    if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return@setupAdapter
                    if (media.isNotEmpty() && firstAdapterCommit != request) {
                        firstAdapterCommit = request
                        logMediaPage(request, "media_first_adapter_commit", "count=${media.size}")
                    }
                    if (!isFromCache) logMediaPage(request, "media_final_commit", "count=${media.size}")
                    if (!isFromCache) setMediaLoading(false)
                    displayedMediaIsFinal = !isFromCache && outcome.complete
                    if (GalleryDiagnosticLogger.isEnabled()) FolderRefreshDiagnostics.event(request, "adapter_committed",
                        detail = "count=${getMediaAdapter()?.itemCount ?: 0} final=$displayedMediaIsFinal")
                    if (isFromCache && media.isNotEmpty() && completedMediaRequest != request) setMediaLoading(true)
                    if (mPath == FAVORITES && !isFromCache && media.isNotEmpty()) {
                        val committedItems = media
                        val token = favoriteSessionId
                        val sourceFavorites = favoriteCatalogRelationshipPaths.orEmpty()
                        ensureBackgroundThread {
                            val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                            val items = committedItems.filterIsInstance<Medium>()
                            val catalogId = org.fossify.gallery.helpers.FavoriteViewerCatalogStore.prepare(
                                applicationContext, items, sourceFavorites
                            ) {
                                mediaLoadRequests.isCurrent(request) && displayedMediaRequest == request &&
                                    mPath == FAVORITES && favoriteSessionId == token &&
                                    favoriteCatalogRelationshipPaths == sourceFavorites &&
                                    FavoriteAccessSessionManager.isActive(token) && !isFinishing && !isDestroyed
                            }
                            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                                if (catalogId == null) "viewer_catalog_miss" else "viewer_catalog_handoff",
                                started, items.size
                            )
                        }
                    }
                    // Restore the authorized path only after the new adapter rows are committed.
                    if (mLastSearchedText.isEmpty()) {
                        restoreFavoritesScrollAnchor(request, !isFromCache)
                        if (isPhotoProfiles && photoScroll.pending != null) {
                            val adapter = getMediaAdapter()
                            if (adapter != null) photoScroll.restore(
                                binding.mediaGrid, adapter.media.map { (it as? Medium)?.path }, !isFromCache,
                                onResult = { result ->
                                    logPhotoScrollEvent(
                                        if (result == FavoritesScrollRestorer.RestoreResult.POSITIONED)
                                            "photo_scroll_restore_applied" else "photo_scroll_restore_skipped",
                                        "result" to result.name
                                    )
                                },
                                isCurrent = {
                                    mediaLoadRequests.isCurrent(request) && binding.mediaGrid.adapter === adapter &&
                                        photoProfileId == PhotoProfileRepository.of(this).state().current?.id &&
                                        !isFinishing && !isDestroyed
                                }
                            )
                        }
                    }
                }
            }

            if (!isFromCache && !outcome.complete && !isPhotoProfiles) {
                org.fossify.gallery.helpers.MediaLoadFailure.present(
                    this, binding.mediaEmptyTextPlaceholder, binding.mediaEmptyDescription,
                    binding.mediaEmptyTextPlaceholder2, media.any { it is Medium },
                    org.fossify.gallery.helpers.PartialMediaScanException(outcome.cause)
                ) { getMedia(manualRefresh = true) }
            }
            if (mPath == FAVORITES && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                android.util.Log.d("GalleryFavorites", "source=${if (isFromCache) "cache" else "scan"}, adapterMedia=${mMedia.count { it is Medium }}")
            }
            if (!isFromCache && !isPhotoProfiles && outcome.complete) {
                // Never let a previous request persist stale favorites after a new refresh.
                val publishedMedia = mMedia
                val read = folderReadResult.takeIf { folderReadRequest == request }
                val confirmed = read?.confirmedPaths
                ensureBackgroundThread {
                    if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed && read?.accessCurrent?.invoke() != false) {
                        try {
                            mediaDB.insertAll(publishedMedia.filterIsInstance<Medium>().filter {
                                it.deletedTS == 0L && (confirmed == null || it.path in confirmed) &&
                                    (!physicalFolder || folderPublicationAllowed(it.path))
                            })
                        } catch (error: Exception) {
                            android.util.Log.e("GalleryMedia", "Cannot cache scanned media", error)
                        }
                    }
                }
            }
        }
    }

    override fun tryDeleteFiles(
        fileDirItems: ArrayList<FileDirItem>,
        skipRecycleBin: Boolean,
        onDeleted: (Set<String>) -> Unit
    ) {
        val filtered = fileDirItems
            .filter { !getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            onDeleted(emptySet())
            return
        }
        val diagnosticOperation = "grid-delete-${android.os.SystemClock.elapsedRealtime()}"
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaOperation", "delete_requested", diagnosticOperation,
                mapOf("count" to filtered.size, "catalogCount" to mMedia.count { it is Medium },
                    "photoProfile" to isPhotoProfiles)
            )
        }

        val movingToRecycleBin = config.useRecycleBin && !skipRecycleBin &&
            !filtered.first().path.startsWith(recycleBinPath)
        if (movingToRecycleBin) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered, permanentDelete = false, diagnosticOperation = diagnosticOperation, onDeleted = onDeleted)
                } else {
                    if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaOperation", "recycle_bin_move_failed", diagnosticOperation,
                        mapOf("requested" to filtered.size)
                    )
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    onDeleted(emptySet())
                    runOnUiThread { if (!isFinishing && !isDestroyed) getMedia() }
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)
            deleteFilteredFiles(filtered, permanentDelete = true, diagnosticOperation = diagnosticOperation, onDeleted = onDeleted)
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    private fun deleteFilteredFiles(
        filtered: ArrayList<FileDirItem>,
        permanentDelete: Boolean,
        diagnosticOperation: String,
        onDeleted: (Set<String>) -> Unit
    ) {
        val requestPath = mPath
        val requestIsPhotoProfile = isPhotoProfiles
        val requestPhotoProfileId = photoProfileId
        deleteFiles(filtered) {
            ensureBackgroundThread {
                // Commons 7.1.3 may report true when any item in a casual batch
                // succeeded. Verify the final state of each requested path.
                val deletedPaths = filtered.mapNotNullTo(LinkedHashSet()) { item ->
                    val sourceGone = !getDoesFilePathExist(item.path)
                    val recycledCopyExists = !permanentDelete &&
                        getDoesFilePathExist("$recycleBinPath/${item.path}")
                    item.path.takeIf { sourceGone && (permanentDelete || recycledCopyExists) }
                }
                if (deletedPaths.isEmpty()) {
                    if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaOperation", "delete_failed", diagnosticOperation,
                        mapOf("requested" to filtered.size)
                    )
                    runOnUiThread {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                        onDeleted(emptySet())
                    }
                    return@ensureBackgroundThread
                }

                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaOperation", "delete_completed", diagnosticOperation,
                    mapOf("requested" to filtered.size, "completed" to deletedPaths.size,
                        "permanent" to permanentDelete, "catalogCount" to mMedia.count { it is Medium })
                )

                if (permanentDelete) {
                    deletedPaths.forEach { mediaDB.deleteMediumPath(it) }
                    if (requestPath == RECYCLE_BIN || deletedPaths.any { it.startsWith("$RECYCLE_BIN/") }) {
                        org.fossify.gallery.helpers.RecycleBinMembershipRevision.changed()
                    }
                    org.fossify.gallery.helpers.PhotoContentRevision.changed()
                }
                runOnUiThread {
                    onDeleted(deletedPaths)
                    val sameScope = requestPath == mPath && requestIsPhotoProfile == isPhotoProfiles &&
                        (!requestIsPhotoProfile || requestPhotoProfileId == photoProfileId)
                    if (sameScope && !isFinishing && !isDestroyed) {
                        mMedia.removeAll { it is Medium && it.path in deletedPaths }
                        if (!requestIsPhotoProfile) deleteDirectoryIfEmpty()
                        // A new request makes any remaining progressive publication
                        // from before deletion stale without disabling progressive load.
                        getMedia()
                    }
                    if (deletedPaths.size < filtered.size) {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        if (binding.mediaGrid.itemDecorationCount > 0) {
            val currentGridDecoration =
                binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
            currentGridDecoration.items = media
        }
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = mPath
        refreshMenuItems()
    }

    private fun unsetAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }
}




