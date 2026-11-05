package org.fossify.gallery.activities

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
import org.fossify.gallery.helpers.FolderReturnCheckpoint
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import java.util.concurrent.Future
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.CreateNewFolderDialog
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
import org.fossify.commons.helpers.SORT_BY_SIZE
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
    private var photoHasContent = false
    private var photoNeedsUnlock = false
    private var photoViewerTransition = false
    private var photoReturnProbeGeneration = 0L
    private var viewerHandoffGeneration = 0L
    private var photoLoadCompleted = false
    private var photoLoadedKey: String? = null
    private var photoLoadedRevision = -1L
    private var photoPublishedStage = -1
    private var photoRequestStarted = 0L
    private val photoScroll = FavoritesScrollRestorer()
    private val photoGridPublication = org.fossify.gallery.helpers.ViewerSwipePublicationGate<() -> Unit>()
    private var photoScrollProfileId: String? = null

    private fun photoDisplayKey(): String = org.fossify.gallery.helpers.PhotoProfileScope.capture(
        this, PhotoProfileRepository.of(this).state().current?.id, photoToken()
    ).toString()

    private fun rememberPhotoScrollAnchor() {
        if (!isPhotoProfiles) return
        val adapter = getMediaAdapter() ?: return
        photoScroll.remember(binding.mediaGrid, adapter.media.map { (it as? Medium)?.path })
        if (photoScroll.pending != null) photoScrollProfileId = photoProfileId
    }

    private fun clearPhotoDisplayState(keepAnchor: Boolean = false) {
        photoGridPublication.clear()
        photoLoadCompleted = false
        photoLoadedKey = null
        photoLoadedRevision = -1L
        photoViewerTransition = false
        if (!keepAnchor) {
            photoScroll.clear()
            photoScrollProfileId = null
        }
        org.fossify.gallery.helpers.PhotoViewerSnapshotStore.clear()
        binding.mediaGrid.adapter = null
        mMedia = ArrayList()
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
            photoHasContent = false
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
            photoHasContent = false
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
        reason: org.fossify.gallery.helpers.PhotoLoadReason = org.fossify.gallery.helpers.PhotoLoadReason.REFRESH
    ) {
        if (photoToken() != null) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val profile = PhotoProfileRepository.of(this).state().current
        photoProfileId = profile?.id
        photoNeedsUnlock = false
        val request = mediaLoadRequests.begin()
        mCurrAsyncTask?.stopFetching()
        if (photoScroll.pending != null && photoScrollProfileId != profile?.id) {
            photoScroll.clear()
            photoScrollProfileId = null
        }
        val requestedKey = photoDisplayKey()
        val retainGrid = reason != org.fossify.gallery.helpers.PhotoLoadReason.SCOPE_CHANGED &&
            !photoNeedsUnlock && photoLoadedKey == requestedKey &&
            photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
            getMediaAdapter() != null && mMedia.isNotEmpty()
        photoGridPublication.clear()
        photoPublishedStage = -1
        photoRequestStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        if (retainGrid) {
            rememberPhotoScrollAnchor()
        } else {
            getMediaAdapter()?.finishActMode()
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            photoLoadedKey = null
            photoHasContent = false
        }
        photoLoadCompleted = false
        // A viewer opened during refresh must not consume the previous final snapshot.
        displayedMediaIsFinal = false
        org.fossify.gallery.helpers.PhotoViewerSnapshotStore.clear()
        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate()
        mLoadedInitialPhotos = true
        updateMenuColors()
        if (profile == null) {
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
            GalleryBottomBar.markRootContentReady(this, GalleryBottomBar.RootContent.EMPTY)
            return
        }
        val requestedToken = photoToken()
        mIsGettingMedia = true
        setMediaLoading(true)
        binding.mediaEmptyTextPlaceholder.beGone()
        binding.mediaEmptyDescription.beGone()
        binding.mediaEmptyTextPlaceholder2.beGone()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, PhotoProfileMediaLoader.PATH,
            showAll = false, photoProfileId = profile.id, photoSessionId = requestedToken,
            manualRefresh = reason == org.fossify.gallery.helpers.PhotoLoadReason.REFRESH,
            diagnosticLoadReason = when (reason) {
                org.fossify.gallery.helpers.PhotoLoadReason.ENTER -> "INITIAL"
                org.fossify.gallery.helpers.PhotoLoadReason.REFRESH -> "REFRESH"
                org.fossify.gallery.helpers.PhotoLoadReason.SCOPE_CHANGED -> "CONFIG_SWITCH"
            },
            photoAllowPreview = !retainGrid,
            photoAnchorPath = photoScroll.pending?.path,
            onPhotoResult = photoResult@{ result ->
                // The shared worker/store still publishes every validated stage
                // to the viewer. A stopped grid needs only the final snapshot.
                if (photoViewerTransition && result.stage != org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE)
                    return@photoResult
                submitPhotoGridResult(result, request, requestedKey)
            },
            onFailure = { error ->
                if (mediaLoadRequests.isCurrent(request)) photoGridPublication.clear()
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
            .also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
    }

    internal fun submitPhotoGridResult(result: org.fossify.gallery.helpers.PhotoLoadResult, request: Long, requestedKey: String) {
        if (!mediaLoadRequests.isCurrent(request) || !result.context.isCurrent(applicationContext) ||
            photoDisplayKey() != requestedKey || result.stage.ordinal < photoPublishedStage) return
        photoPublishedStage = result.stage.ordinal
        val apply = { applyPhotoGridResult(result, request, requestedKey) }
        // Defer before full-list copies/comparisons, anchors and menus.
        // First content and access failures must never wait for scroll idle.
        if (!result.access.needsUnlock && getMediaAdapter() != null && mMedia.isNotEmpty() &&
            binding.mediaGrid.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            photoGridPublication.onScrollMoving(true)
            val replaced = photoGridPublication.hasPending
            photoGridPublication.deferIfMoving(apply)
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                if (replaced) "photo_grid_publication_replaced" else "photo_grid_publication_deferred")
        } else {
            photoGridPublication.clear()
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
            val complete = result.stage == org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE
            publishMedia(ArrayList<ThumbnailItem>(result.media), !complete, request)
            if (accessChanged) refreshMenuItems()
            if (!complete) {
                photoLoadedKey = requestedKey
                photoHasContent = result.media.isNotEmpty()
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
    private var mLastSearchedText = ""
    /** Query corresponding to the adapter's last committed visible-order snapshot. */
    private var mCommittedSearchText = ""
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    @Volatile private var mediaScanGeneration = 0L
    private val mediaLoadRequests = FavoriteMediaLoadRequests()
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
    private val mediaRefreshWatchdog = org.fossify.gallery.helpers.RefreshWatchdog()
    /** A deadline spans cache lookup, queue wait and scan, including ALL files. */
    private fun armMediaRefreshDeadline(request: Long) {
        if (isPhotoProfiles) return
        mediaRefreshWatchdog.stop()
        mediaRefreshWatchdog.arm {
            if (isDestroyed || isFinishing || !mediaLoadRequests.isCurrent(request)) return@arm
            org.fossify.gallery.helpers.FolderRefreshTrace.event(
                request, if (mShowAll) "all_files_timeout" else "folder_timeout"
            )
            // A deadline may only retire the request when it wins the terminal gate.
            if (!renderMediaScanState(request, org.fossify.gallery.helpers.GalleryScanResult(
                    org.fossify.gallery.helpers.GalleryScanState.TIMED_OUT))) return@arm
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
        // Root navigation uses the header strip. The central indicator is reserved
        // for the initial non-root folder/picker load; never show both together.
        val central = loading && mGallerySection == null && getMediaAdapter() == null
        if (loading && !central) OxygenProgressStyle.dispose(binding.loadingIndicator)
        else OxygenProgressStyle.setLoading(binding.loadingIndicator, central)
        mediaHeaderProgress?.apply {
            if (central) OxygenProgressStyle.dispose(this)
            OxygenProgressStyle.apply(this@MediaActivity, config, this)
            OxygenProgressStyle.setLoading(this, loading && !central)
        }
    }

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    private var folderUnlockSessionId: String? = null
    private var favoriteSessionId: String? = null
    private var favoriteOwnerId = java.util.UUID.randomUUID().toString()
    private var favoriteAuthGeneration = 0L
    private var folderAuthGeneration = 0L
    private var favoritePublicationGeneration = 0L
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
                binding.mediaGrid.adapter = null
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
    private var favoriteObservedTracker: androidx.room.InvalidationTracker? = null
    private val favoriteDatabaseObserver = object : androidx.room.InvalidationTracker.Observer("favorites") {
        override fun onInvalidated(tables: Set<String>) {
            if (favoriteObserverDisposed) return
            val token = favoriteSessionId ?: return
            val app = applicationContext
            ensureBackgroundThread {
                FavoriteAccessSessionManager.retainFavorites(token, app.getFavoritePaths().toSet())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
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
                    photoGridPublication.onScrollMoving(newState != RecyclerView.SCROLL_STATE_IDLE)?.invoke()
                }
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    favoritesScroll.clear()
                    photoScroll.clear()
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
                    binding.mediaGrid.adapter = null
                    mMedia = ArrayList()
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
                loadingFrame = { mediaHeaderProgress?.let(OxygenProgressStyle::revealPending) }
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
        } else if (mMedia.isEmpty() || !isRandomSorting ||
            (isRandomSorting && !returningFromViewer)) {
            val request = ++folderAuthGeneration
            val path = mPath
            val token = folderUnlockSessionId
            val app = applicationContext
            ensureBackgroundThread {
                val protectedFolder = FolderUnlockSessionManager.findProtectedAncestor(
                    path, app.config::isFolderProtected
                )
                val authorized = protectedFolder == null ||
                    FolderUnlockSessionManager.isFolderAuthorized(token, path)
                val canReuse = authorized && protectedFolder == null &&
                    returningFromViewer && returnCheckpoint != null &&
                    completePublicDisplay && !mShowAll && !isRandomSorting &&
                    FolderReturnCheckpoint.isCurrent(app, path, returnCheckpoint)
                runOnUiThread {
                    if (request != folderAuthGeneration || isFinishing || isDestroyed) return@runOnUiThread
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
        viewerHandoffGeneration++
        if (mPath == FAVORITES) rememberFavoritesScrollAnchor()
        favoritesScroll.cancelLayout()
        favoritesReturnCover?.pause()
        mediaSearchGeneration++
        mediaPollGeneration++
        mediaPollScheduled = false
        mediaPollInFlight = false
        mediaScanTerminals.finish(mediaRefreshRequest,
            org.fossify.gallery.helpers.GalleryScanResult(org.fossify.gallery.helpers.GalleryScanState.CANCELLED))
        super.onPause()
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
        if (!isPhotoProfiles || !photoViewerTransition) mCurrAsyncTask?.stopFetching()
    }

    override fun onStop() {
        mediaRefreshWatchdog.stop()
        super.onStop()
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

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
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
    }

    override fun onDestroy() {
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
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
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
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN
            val isPicker = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent || isSetWallpaperIntent()
            findItem(R.id.photo_profiles_manage).isVisible =
                org.fossify.gallery.helpers.ToolbarQuickActions.photoProfileManageVisible(
                    isPhotoProfiles, isPicker, mPath == RECYCLE_BIN
                )
            findItem(R.id.photo_profiles_unlock).isVisible = isPhotoProfiles && photoNeedsUnlock && photoProfileId != null
            findItem(R.id.photo_profiles_lock).isVisible = isPhotoProfiles && photoToken() != null

            findItem(R.id.folder_view).isVisible = hasFixedBrowseSwitch
            findItem(R.id.open_camera).isVisible = mShowAll
            findItem(R.id.about).isVisible = mShowAll
            findItem(R.id.create_new_folder).isVisible =
                !mShowAll && mPath != RECYCLE_BIN && mPath != FAVORITES
            findItem(R.id.open_recycle_bin).isVisible = config.useRecycleBin && mPath != RECYCLE_BIN

            findItem(R.id.temporarily_show_excluded).isVisible =
                mPath.isEmpty() && !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible =
                mPath.isEmpty() && config.temporarilyShowExcluded

            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.set_as_default_folder).isVisible = !isDefaultFolder
            findItem(R.id.unset_as_default_folder).isVisible = isDefaultFolder

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.column_count).isVisible = viewType == VIEW_TYPE_GRID
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
            if (isPhotoProfiles) {
                listOf(R.id.group, R.id.create_new_folder, R.id.set_as_default_folder,
                    R.id.unset_as_default_folder, R.id.temporarily_show_hidden, R.id.stop_showing_hidden,
                    R.id.temporarily_show_excluded, R.id.stop_showing_excluded).forEach { findItem(it)?.isVisible = false }
                findItem(R.id.slideshow).isVisible = mMedia.filterIsInstance<Medium>().any { org.fossify.gallery.helpers.PhotoPlaybackPolicy.canPlay(it.type, config.slideshowIncludeVideos, config.slideshowIncludeGIFs) }
            }
        }
        updateMenuColors()
    }

    private fun setupOptionsMenu() {
        OxygenToolbarStyle.preparePopup(this, binding.mediaMenu)
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mediaMenu.setupMenu()

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            binding.mediaRefreshLayout.isEnabled = text.isEmpty()
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
                R.id.create_new_folder -> createNewFolder()
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
            val isHome = mPath.isEmpty() || (isPhotoProfiles && org.fossify.gallery.helpers.PhotoPlaybackPolicy.toolbarScope(photoHasContent) == org.fossify.gallery.helpers.ToolbarQuickScope.HOME)
            val viewType = config.getFolderViewType(if (isHome && !isPhotoProfiles) SHOW_ALL else mPath)
            org.fossify.gallery.helpers.ToolbarQuickActions.apply(
                this, binding.mediaMenu,
                if (isHome) org.fossify.gallery.helpers.ToolbarQuickScope.HOME
                    else org.fossify.gallery.helpers.ToolbarQuickScope.FOLDER,
                if (isHome) config.toolbarHomeActions else config.toolbarFolderActions,
                hasBack = !isHome && !isPhotoProfiles,
                fixedActionCount = (if (hasFixedBrowseSwitch) 1 else 0) + (if (isPhotoProfiles) 1 else 0),
                supported = { action ->
                    if (isPhotoProfiles && action in listOf(
                        org.fossify.gallery.helpers.ToolbarQuickActions.HIDDEN,
                        org.fossify.gallery.helpers.ToolbarQuickActions.EXCLUDED,
                        org.fossify.gallery.helpers.ToolbarQuickActions.GROUP)) false else when (action) {
                        org.fossify.gallery.helpers.ToolbarQuickActions.COLUMNS,
                        org.fossify.gallery.helpers.ToolbarQuickActions.FILENAME -> viewType == VIEW_TYPE_GRID
                        org.fossify.gallery.helpers.ToolbarQuickActions.GROUP -> !config.scrollHorizontally
                        org.fossify.gallery.helpers.ToolbarQuickActions.SLIDESHOW ->
                            mMedia.filterIsInstance<Medium>().any { org.fossify.gallery.helpers.PhotoPlaybackPolicy.canPlay(it.type, config.slideshowIncludeVideos, config.slideshowIncludeGIFs) }
                        else -> true
                    }
                }
            )
        }
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

    private fun searchQueryChanged(text: String) {
        val searchGeneration = ++mediaSearchGeneration
        val request = displayedMediaRequest
        val finalResult = completedMediaRequest == request &&
            (isPhotoProfiles || (mediaScanOutcomeRequest == request && mediaScanOutcome.complete))
        val sourceMedia = ArrayList(mMedia)
        ensureBackgroundThread {
            try {
                val filtered = sourceMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = if (isPhotoProfiles) filtered.toCollection(ArrayList<ThumbnailItem>()) else MediaFetcher(applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                val safeGrouped = if (mPath == FAVORITES) FavoriteAccessPolicy.authorizedItems(
                    favoriteSessionId, grouped
                ) { (it as? Medium)?.path } else grouped
                runOnUiThread {
                    // An earlier background search must not overwrite a newer query.
                    if (isFinishing || isDestroyed || mLastSearchedText != text ||
                        searchGeneration != mediaSearchGeneration || !mediaLoadRequests.isCurrent(request)) return@runOnUiThread
                    if (mPath == FAVORITES && !FavoriteAccessSessionManager.isActive(favoriteSessionId)) return@runOnUiThread
                    if (safeGrouped.isEmpty() && (!isPhotoProfiles || finalResult)) {
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
                        binding.mediaFastscroller.beVisibleIf(safeGrouped.isNotEmpty())
                    }

                    getMediaAdapter()?.updateMedia(safeGrouped, preserveScroll = false, isCurrent = {
                        searchGeneration == mediaSearchGeneration && mLastSearchedText == text &&
                            mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed &&
                            (mPath != FAVORITES || FavoriteAccessSessionManager.isActive(favoriteSessionId))
                    }, onCommitted = {
                        if (searchGeneration == mediaSearchGeneration && mLastSearchedText == text) {
                            mCommittedSearchText = text
                        }
                        displayedMediaIsFinal = finalResult
                        restoreFavoritesScrollAnchor(request, finalResult)
                    }, diagnosticRequestId = request)
                }
            } catch (ignored: Exception) {
            }
        }
    }

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

            getMedia()
            setupLayoutManager()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter(onCommitted: () -> Unit = {}) {
        if (mPath == FAVORITES) {
            if (!FavoriteAccessSessionManager.isActive(favoriteSessionId)) return
        }
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        if (currAdapter == null) {
            initZoomListener()
            MediaAdapter(
                activity = this,
                media = mMedia.clone() as ArrayList<ThumbnailItem>,
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
                setupZoomListener(mZoomListener)
                binding.mediaGrid.adapter = this
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            val returningToFavorites = mPath == FAVORITES && favoritesReturnCover?.covering == true
            if (returningToFavorites) binding.mediaGrid.clearAnimation()
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled && !returningToFavorites) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
            if (mLastSearchedText.isNotEmpty()) searchQueryChanged(mLastSearchedText)
            else onCommitted()
        } else if (mLastSearchedText.isEmpty()) {
            val request = displayedMediaRequest
            val read = folderReadResult.takeIf { folderReadRequest == request }
            val searchGeneration = mediaSearchGeneration
            val folderToken = folderUnlockSessionId
            (currAdapter as MediaAdapter).updateMedia(mMedia, isCurrent = {
                mediaLoadRequests.isCurrent(request) && searchGeneration == mediaSearchGeneration &&
                    !isFinishing && !isDestroyed &&
                    (mPath != FAVORITES || FavoriteAccessSessionManager.isActive(favoriteSessionId)) &&
                    read?.accessCurrent?.invoke() != false &&
                    folderToken == folderUnlockSessionId &&
                    (folderToken == null || FolderUnlockSessionManager.isActive(folderToken))
            }, onCommitted = {
                handleGridSpacing(currAdapter.media)
                onCommitted()
            }, diagnosticRequestId = request)
        } else {
            searchQueryChanged(mLastSearchedText)
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
        OxygenFastScroller.apply(binding.mediaFastscroller, binding.mediaGrid,
            OxygenThemeEngine.snapshot(this, config),
            if (mGallerySection == null) 0 else resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height))
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
                    if (latest != null && (mLatestMediaId != latest.first ||
                            mLatestMediaDateId != latest.second)) {
                        mLatestMediaId = latest.first
                        mLatestMediaDateId = latest.second
                        if (mIsGettingMedia) mediaChangeGate.mediaChangedDuringScan()
                        else getMedia()
                    }
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this, photoProfile = isPhotoProfiles) {
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
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

    private fun getMedia(manualRefresh: Boolean = false) {
        if (isPhotoProfiles) { getPhotoProfileMedia(); return }
        if (mPath == FAVORITES && favoriteSessionId != null) {
            favoriteAccessPaths = FavoriteAccessSessionManager.getCapturedPaths(favoriteSessionId)
            if (favoriteAccessPaths == null) { finish(); return }
        }
        if (mPath == FAVORITES && favoriteAccessPaths == null) {
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
        mediaRefreshRequest = request
        mediaScanTerminals.begin(request)
        if (mPath == FAVORITES && favoritesReturnCover?.covering == true) {
            val token = favoriteSessionId
            favoritesReturnCover?.pause()
            favoritesReturnCover?.armTimeout({ isFavoritesReturnCurrent(request, token) }, ::retryFavoritesReturn)
        }
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = true
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
        val sortingPath = if (mShowAll) SHOW_ALL else mPath
        val sortingBySize = config.getFolderSorting(sortingPath) and SORT_BY_SIZE != 0

        if (mLoadedInitialPhotos) {
            startAsyncTask(request, manualRefresh)
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent,
                allowedFavoritePaths = favoriteAccessPaths
            ) { cached ->
                // Cache callbacks run on workers; AsyncTask and preview UI start on main.
                runOnUiThread {
                    if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return@runOnUiThread
                    if (cached.isEmpty()) {
                        binding.mediaRefreshLayout.isRefreshing = true
                    } else {
                        val cachedHasUnknownSize = cached.asSequence()
                            .filterIsInstance<Medium>()
                            .any { it.size <= 0L }
                        if (!sortingBySize || !cachedHasUnknownSize) {
                            gotMedia(cached, true, request)
                        }
                    }
                    startAsyncTask(request, manualRefresh, cached.filterIsInstance<Medium>())
                }
            }
        }

        mLoadedInitialPhotos = true
    }

    private var mediaScanOutcomeRequest = -1L
    private var mediaScanOutcome = org.fossify.gallery.helpers.GalleryScanResult(org.fossify.gallery.helpers.GalleryScanState.CANCELLED)
    private val mediaScanTerminals = org.fossify.gallery.helpers.GalleryScanTerminalGate()
    private var folderReadRequest = -1L
    private var folderReadResult: org.fossify.gallery.helpers.FolderMediaReadResult? = null
    private var folderStatsRequestVersion = 0L

    private val folderPublicationVersion = java.util.concurrent.atomic.AtomicLong()
    private fun folderPublicationAllowed(path: String, token: String? = folderUnlockSessionId): Boolean {
        val parent = File(path).parent ?: return false
        return FolderUnlockSessionManager.findProtectedAncestor(parent, config::isFolderProtected) == null ||
            FolderUnlockSessionManager.isMediaAuthorized(token, path, config::isFolderProtected)
    }

    private fun startAsyncTask(request: Long, manualRefresh: Boolean = false,
        retainedMedia: List<Medium> = mMedia.filterIsInstance<Medium>()) {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return
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
            onFolderPreview = { read, partial ->
                if (scanGeneration == mediaScanGeneration && mediaLoadRequests.isCurrent(request) &&
                    requestedFolder == mPath && !isFinishing && !isDestroyed) {
                    folderReadRequest = request
                    folderReadResult = read
                    gotMedia(partial, true, request)
                }
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
                        if (error is SecurityException && !mShowAll && mPath.startsWith('/')) {
                            mMedia.clear()
                            binding.mediaGrid.adapter = null
                        }
                        if (!renderMediaScanState(request, org.fossify.gallery.helpers.GalleryScanResult(
                            org.fossify.gallery.helpers.GalleryScanState.FAILED, cause = error))) return@runOnUiThread
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
        ) { scannedMedia ->
            // Capture the current grid snapshot on the callback (main) thread.
            // Reconciliation must not iterate mMedia while a newer scan replaces it.
            val oldMedia = mMedia.filterIsInstance<Medium>().map { it.copy() }
            ensureBackgroundThread {
                if (scanGeneration != mediaScanGeneration || !mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) {
                    return@ensureBackgroundThread
                }
                try {
                    val newPaths = scannedMedia.filterIsInstance<Medium>().mapTo(HashSet()) { it.path }
                    if (requestedFolder == FAVORITES) {
                        favoriteCatalogRelationshipPaths = scanTask?.favoriteRelationshipSnapshot
                    }
                    if (requestedFolder != mPath) return@ensureBackgroundThread
                    mediaScanOutcomeRequest = request
                    mediaScanOutcome = scanTask?.scanResult ?: org.fossify.gallery.helpers.GalleryScanResult(org.fossify.gallery.helpers.GalleryScanState.FAILED)
                    folderReadRequest = request
                    folderReadResult = scanTask?.folderReadResult
                    org.fossify.gallery.helpers.FolderRefreshTrace.event(request, "publication_candidate",
                        scannedMedia.filterIsInstance<Medium>().map { it.path }, "status=${folderReadResult?.status}")
                    gotMedia(scannedMedia, false, request)
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
                    val folderCheckpoint = scanTask?.folderCatalogCheckpoint
                    if (folderCheckpoint != null && !mShowAll && requestedFolder.isNotEmpty() &&
                        requestedFolder != FAVORITES && requestedFolder != RECYCLE_BIN) {
                        val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                        org.fossify.gallery.helpers.FolderViewerSnapshotStore.prepare(
                            applicationContext, requestedFolder, folderCheckpoint, folderCheckpoint,
                            scannedMedia.filterIsInstance<Medium>()
                        ) {
                            scanGeneration == mediaScanGeneration && mediaLoadRequests.isCurrent(request) &&
                                requestedFolder == mPath && !isFinishing && !isDestroyed
                        }
                        org.fossify.gallery.helpers.GalleryPerfTrace.end(
                            "viewer_catalog_handoff", catalogStarted,
                            scannedMedia.count { it is Medium }
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
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GalleryMedia", "Media scan reconciliation failed", e)
                }
            }
        }

        val task = scanTask ?: return
        mCurrAsyncTask = task
        try {
            org.fossify.gallery.helpers.FolderRefreshTrace.event(request, "folder_queued")
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

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
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
        if (mPath == FAVORITES && !isSetWallpaperIntent() &&
            !mIsGetImageIntent && !mIsGetVideoIntent && !mIsGetAnyIntent) {
            binding.mediaGrid.stopScroll()
            rememberFavoritesScrollAnchor()
            prepareFavoritesReturnCover()
        }
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
            mWasFullscreenViewOpen = true
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            // Never consult the legacy system-player preference for internal taps.
            if (config.gestureVideoPlayer && !isPhotoProfiles) {
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
        val request = ++viewerHandoffGeneration
        val source = mPath
        val folderToken = folderUnlockSessionId
        val favoriteToken = favoriteSessionId
        val showAll = mShowAll
        val app = applicationContext
        val neighborSourceType = if (source == FAVORITES) org.fossify.gallery.helpers.ViewerSourceType.FAVORITES
            else org.fossify.gallery.helpers.ViewerSourceType.FOLDER
        val neighborSourceKey = if (source == FAVORITES) FAVORITES else source
        val displayedOrder = getMediaAdapter()?.media ?: mMedia
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
            getMediaAdapter() != null
        val catalogLookupStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        ensureBackgroundThread {
            // Keep the existing constant-size return checkpoint before launching,
            // but never copy or validate the whole media list on the click path.
            // Handoff the committed visible grid order without conferring media access.
            val gridOrderId = if (!showAll && (source == FAVORITES || source.startsWith('/'))) {
                org.fossify.gallery.helpers.ViewerGridOrderStore.offer(source, path, displayedOrder)
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
            runOnUiThread {
                if (request != viewerHandoffGeneration || source != mPath || isFinishing || isDestroyed) {
                    org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborId)
                    return@runOnUiThread
                }
                pendingFolderReturn = checkpoint
                startActivity(Intent(this, ViewPagerActivity::class.java).apply {
                    folderToken?.let { putExtra(FOLDER_UNLOCK_SESSION, it) }
                    if (source == FAVORITES) putExtra(FAVORITE_ACCESS_SESSION, favoriteToken)
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, showAll)
                    putExtra(SHOW_FAVORITES, source == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, source == RECYCLE_BIN)
                    putExtra(IS_FROM_GALLERY, true)
                    if (mCommittedSearchText.isNotBlank()) putExtra(
                        org.fossify.gallery.helpers.ViewerSearchPolicy.EXTRA_QUERY, mCommittedSearchText
                    )
                    neighborId?.let { putExtra(org.fossify.gallery.helpers.ViewerNeighborHandoffStore.EXTRA_ID, it) }
                    gridOrderId?.let { putExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID, it) }
                    catalogId?.let { putExtra(org.fossify.gallery.helpers.FolderViewerSnapshotStore.EXTRA_ID, it) }
                    favoriteCatalogId?.let { putExtra(org.fossify.gallery.helpers.FavoriteViewerCatalogStore.EXTRA_ID, it) }
                })
            }
        }
    }

    private fun openInViewPager(path: String) {
        if (!isPhotoProfiles) {
            openOrdinaryViewer(path)
            return
        }
        if (mPath == FAVORITES && !FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, path)) {
            finish(); return
        }
        val folderToken = folderUnlockSessionId
        val app = applicationContext
        val requestedPhotoId = photoProfileId
        val requestedPhotoKey = if (isPhotoProfiles) photoDisplayKey() else null
        val requestedPhotoToken = if (isPhotoProfiles) photoToken() else null
        val gridOrderId = org.fossify.gallery.helpers.ViewerGridOrderStore.offer(
            PhotoProfileMediaLoader.PATH + requestedPhotoId.orEmpty() + requestedPhotoToken.orEmpty(),
            path, getMediaAdapter()?.media ?: mMedia)
        val neighborId = if (isPhotoProfiles) {
            val displayedOrder = getMediaAdapter()?.media ?: mMedia
            org.fossify.gallery.helpers.ViewerNeighborWindow.captureAround(displayedOrder, path)?.let { neighbors ->
                org.fossify.gallery.helpers.ViewerNeighborHandoffStore.offer(
                    org.fossify.gallery.helpers.ViewerNeighborWindow(
                        path, org.fossify.gallery.helpers.ViewerSourceType.PHOTO_PROFILE,
                        requestedPhotoId.orEmpty(), neighbors, displayedMediaIsFinal && !mIsGettingMedia
                    )
                )
            }
        } else null
        ensureBackgroundThread {
            val folderAuthorized = runCatching {
                FolderUnlockSessionManager.isMediaAuthorized(folderToken, path, app.config::isFolderProtected)
            }.getOrDefault(false)
            val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            // Both COMPLETE and already-validated INDEXED stage metadata are
            // eligible for handoff; a partial catalog is never marked COMPLETE.
            val fullCatalogId = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.handoffId(
                app, requestedPhotoId, requestedPhotoToken)
            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                if (fullCatalogId == null) "viewer_catalog_miss" else "viewer_catalog_hit",
                catalogStarted)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborId)
                    return@runOnUiThread
                }
                // A delayed click must never launch the old path under a newly selected scope.
                if (requestedPhotoKey != null && (photoProfileId != requestedPhotoId ||
                    photoDisplayKey() != requestedPhotoKey ||
                    mMedia.none { it is Medium && it.path == path })) {
                    org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborId)
                    return@runOnUiThread
                }
                Intent(this, ViewPagerActivity::class.java).apply {
                    if (folderAuthorized) putExtra(FOLDER_UNLOCK_SESSION, folderToken)
                    if (mPath == FAVORITES) putExtra(FAVORITE_ACCESS_SESSION, favoriteSessionId)
                    putExtra(PATH, path)
                    if (isPhotoProfiles) {
                        putExtra(PhotoProfileMediaLoader.EXTRA_ID, requestedPhotoId)
                        putExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION, requestedPhotoToken)
                        fullCatalogId?.let {
                            putExtra(org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.EXTRA_ID, it)
                        }
                    }
                    putExtra(SHOW_ALL, mShowAll)
                    putExtra(SHOW_FAVORITES, mPath == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
                    putExtra(IS_FROM_GALLERY, true)
                    if (mCommittedSearchText.isNotBlank()) putExtra(
                        org.fossify.gallery.helpers.ViewerSearchPolicy.EXTRA_QUERY, mCommittedSearchText
                    )
                    neighborId?.let { putExtra(org.fossify.gallery.helpers.ViewerNeighborHandoffStore.EXTRA_ID, it) }
                    gridOrderId?.let { putExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID, it) }
                    pendingFolderReturn = null
                    if (isPhotoProfiles) {
                        binding.mediaGrid.stopScroll()
                        photoScroll.clear()
                        rememberPhotoScrollAnchor()
                        photoViewerTransition = true
                    }
                    startActivity(this)
                }
            }
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
            ViewerAccessCoordinator.cancel(favoritePublicationTask)
            favoritePublicationTask = try { ViewerAccessCoordinator.submit {
                if (generation != favoritePublicationGeneration || Thread.currentThread().isInterrupted) return@submit
                val safe = FavoriteAccessPolicy.authorizedItems(token, candidateMedia) { (it as? Medium)?.path }
                runOnUiThread {
                    if (generation != favoritePublicationGeneration || !mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    if (!FavoriteAccessSessionManager.isActive(token)) {
                        finish()
                        return@runOnUiThread
                    }
                    publishMedia(safe, isFromCache, request)
                }
            } } catch (_: java.util.concurrent.RejectedExecutionException) {
                finish()
                null
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
                val safe = ArrayList(candidate.filter { item ->
                    (item as? Medium)?.let { folderPublicationAllowed(it.path, token) } != false
                })
                runOnUiThread {
                    if (version == folderPublicationVersion.get() && folder == mPath && token == folderUnlockSessionId &&
                        mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed &&
                        (token == null || FolderUnlockSessionManager.isActive(token))) {
                        publishMedia(safe, isFromCache, request)
                    }
                }
            }
            return
        }
        publishMedia(media, isFromCache, request)
    }

    private fun publishMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean, request: Long) {
        runOnUiThread {
            if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed ||
                (isFromCache && completedMediaRequest == request)) return@runOnUiThread

            val physicalFolder = !isPhotoProfiles && !mShowAll && mPath.startsWith('/') && mPath != RECYCLE_BIN
            if (physicalFolder) {
                val read = folderReadResult.takeIf { folderReadRequest == request && !isFromCache }
                if (read != null && !read.accessCurrent()) {
                    mMedia.clear()
                    binding.mediaGrid.adapter = null
                    mIsGettingMedia = false
                    setMediaLoading(false)
                    binding.mediaRefreshLayout.isRefreshing = false
                    toast(R.string.media_load_permission_error)
                    return@runOnUiThread
                }
            }
            val outcome = mediaScanOutcome.takeIf { mediaScanOutcomeRequest == request }
                ?: org.fossify.gallery.helpers.GalleryScanResult(org.fossify.gallery.helpers.GalleryScanState.COMPLETE)
            if (!isPhotoProfiles) {
                if (!renderMediaScanState(request, if (isFromCache) null else outcome)) return@runOnUiThread
            } else if (!isFromCache) {
                mIsGettingMedia = false
                setMediaLoading(false)
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
            if (isPhotoProfiles && displayedContentChanged && getMediaAdapter() != null) {
                // Capture once per actually applied change, including partials.
                // Photos deliberately opts out of the adapter's generic anchor.
                rememberPhotoScrollAnchor()
            }
            mMedia = ArrayList(media)
            if (isPhotoProfiles && !isFromCache) {
                photoHasContent = media.any { it is Medium }
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
            // A preview must not hide the central loading indicator or stop a manual refresh.
            binding.mediaEmptyDescription.beGone()
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = if (mPath == FAVORITES)
                    getString(org.fossify.commons.R.string.no_items_found)
                else getString(R.string.no_media_with_filters)
                if (mPath == FAVORITES) binding.mediaEmptyTextPlaceholder2.beGone()
                if (isPhotoProfiles) {
                    updatePhotoEmptyState()
                }
            }
            binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
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
                    displayedMediaIsFinal = !isFromCache && outcome.complete
                    org.fossify.gallery.helpers.FolderRefreshTrace.event(request, "adapter_committed",
                        getMediaAdapter()?.media.orEmpty().filterIsInstance<Medium>().map { it.path }, "final=$displayedMediaIsFinal")
                    if (isFromCache && media.isNotEmpty() && completedMediaRequest != request) setMediaLoading(true)
                    GalleryBottomBar.markRootContentReady(this)
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
            if (!isFromCache && media.isEmpty()) {
                GalleryBottomBar.markRootContentReady(this, if (photoNeedsUnlock)
                    GalleryBottomBar.RootContent.AUTHORIZATION else GalleryBottomBar.RootContent.EMPTY)
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
        if (!isPhotoProfiles && mediaLoadRequests.isCurrent(request)) {
            mLatestMediaId = getLatestMediaId()
            mLatestMediaDateId = getLatestMediaByDateId()
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




