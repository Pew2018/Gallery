Warning: truncated output (original token count: 51328)
Total output lines: 3795

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
import org.fossify.gallery.extensions.isThisOrParentExcluded
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.MediaUpdateReason
import org.fossify.gallery.helpers.DisplayedFolderSnapshot
import org.fossify.gallery.helpers.DisplayedSourceStage
import org.fossify.gallery.helpers.FolderCachePreviewAccess
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
    private var photoActiveRequestId = 0L
    private var photoFastScrollActive = false
    private val photoScroll = FavoritesScrollRestorer()
    private val photoGridPublication = org.fossify.gallery.helpers.ViewerSwipePublicationGate<() -> Unit>()
    private var photoScrollProfileId: String? = null

    private fun photoDisplayKey(): String = org.fossify.gallery.helpers.PhotoProfileScope.capture(
        this, PhotoProfileRepository.of(this).state().current?.id, photoToken()
    ).toString()

    private fun rememberPhotoScrollAnchor(source: String = "current_view") {
        if (!isPhotoProfiles) return
        val adapter = getMediaAdapter() ?: return
        val hadAnchor = photoScroll.pending != null
        photoScroll.remember(binding.mediaGrid, adapter.media.map { (it as? Medium)?.path })
        if (photoScroll.pending != null) photoScrollProfileId = photoProfileId
        if (!hadAnchor && photoScroll.pending != null) {
            val anchor = photoScroll.pending
            logPhotoScrollEvent("photo_scroll_anchor_capture", "source" to source,
                "ordinal" to anchor?.ordinal, "offset" to anchor?.offset)
        }
    }

    private fun onPhotoFastScrollStateChanged(active: Boolean) {
        if (!isPhotoProfiles) return
        if (active) {
            if (photoFastScrollActive) return
            photoFastScrollActive = true
            // Fast-scroller calls can leave RecyclerView.scrollState at IDLE.
            // A new thumb gesture supersedes any saved restore request.
            val hadAnchor = photoScroll.pending != null
            photoScroll.clear()
            photoScrollProfileId = null
            photoGridPublication.onScrollMoving(true)
            if (hadAnchor) logPhotoScrollEvent("photo_scroll_anchor_invalidated", "reason" to "fast_scroll")
            logPhotoScrollEvent("photo_fast_scroll_start", "anchorInvalidated" to hadAnchor)
            return
        }
        if (!photoFastScrollActive) return
        photoFastScrollActive = false
        logPhotoScrollEvent("photo_fast_scroll_end", "anchorPresent" to (photoScroll.pending != null))
        releasePhotoGridPublicationIfIdle()
    }

    private fun releasePhotoGridPublicationIfIdle() {
        if (photoFastScrollActive || binding.mediaGrid.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        if (photoGridPublication.hasPending) {
            // Capture after the user's final fast-scroll movement and before the
            // newest queued snapshot can replace adapter rows.
            rememberPhotoScrollAnchor("fast_scroll_release")
        }
        photoGridPublication.onScrollMoving(false)?.invoke()
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
                "fastScrollActive" to photoFastScrollActive,
                "anchorPresent" to (photoScroll.pending != null)
            ) + fields.toMap()
        )
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
        photoActiveRequestId = request
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
        binding.mediaEmptyTextPlaceholder.be…41328 tokens truncated…cleBinMembershipRevision.current()
                if (recycleBinLoadRevision != currentRevision) {
                    getMedia(reason = "recycle_bin_members_changed_during_load")
                    return@runOnUiThread
                }
                displayedRecycleBinRevision = recycleBinLoadRevision
            }
        if (mPath == FAVORITES && !isFromCache) {
            val currentRevision = favoriteRelationshipRevision.get()
            if (favoriteLoadRevision != currentRevision) {
                getMedia(reason = "favorite_members_changed_during_load")
                return@runOnUiThread
            }
                val relationships = favoriteCatalogRelationshipPaths
                val captured = FavoriteAccessSessionManager.getCapturedPaths(favoriteSessionId)
                if (relationships != null && captured?.containsAll(relationships) != true) {
                    sourceProtectionRefreshPending = true
                    FavoriteAccessSessionManager.revokeSession(favoriteSessionId)
                    return@runOnUiThread
                }
            displayedFavoriteRelationshipRevision = favoriteLoadRevision
        }

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
            displayedSourceVersion++
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
            if (!isFromCache && media.isNotEmpty()) setMediaLoading(true)
            if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "folder_snapshot_selected", request,
                    mapOf("snapshotStage" to (displayedFolderSnapshot?.stage?.name ?:
                        if (mPath == FAVORITES && isFromCache) "CACHE_AUTHORIZED" else null),
                        "generation" to displayedSourceVersion, "itemCount" to media.size,
                        "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(mPath)))
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
                    if (!isFromCache) setMediaLoading(false)
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




