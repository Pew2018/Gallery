package org.fossify.gallery.activities

import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
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
import org.fossify.gallery.helpers.PhotoProfileDialogs
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
    private val photoDialogs by lazy { PhotoProfileDialogs(this) { reloadPhotoProfile() } }
    private fun profileDialogs() = photoDialogs
    private var photoHasContent = false
    private var photoNeedsUnlock = false
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
                binding.mediaEmptyTextPlaceholder2.setText(R.string.photo_profiles_edit)
                binding.mediaEmptyTextPlaceholder2.beVisible()
            }
        }
    }
    private fun onPhotoAccessRevoked() {
        if (!isPhotoProfiles || isDestroyed) return
        runOnUiThread {
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            photoHasContent = false
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) getMedia()
        }
    }

    private fun reloadPhotoProfile() {
        photoHasContent = false
        mediaSearchGeneration++
        mLastSearchedText = ""
        if (binding.mediaMenu.isSearchOpen) OxygenSearchNavigation.closeInlineSearch(this, binding.mediaMenu)
        getMediaAdapter()?.finishActMode()
        binding.mediaGrid.adapter = null
        mMedia = ArrayList()
        photoProfileId = PhotoProfileRepository.of(this).state().current?.id
        updateMenuColors()
        getMedia()
    }

    private fun getPhotoProfileMedia() {
        if (photoToken() != null) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val profile = PhotoProfileRepository.of(this).state().current
        photoProfileId = profile?.id
        photoNeedsUnlock = false
        val request = mediaLoadRequests.begin()
        mCurrAsyncTask?.stopFetching()
        binding.mediaGrid.adapter = null
        mMedia = ArrayList()
        mLoadedInitialPhotos = true
        updateMenuColors()
        if (profile == null) {
            mIsGettingMedia = false
            setMediaLoading(false)
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.setText(R.string.photo_profiles_empty_title)
            binding.mediaEmptyDescription.setText(R.string.photo_profiles_empty_description)
            binding.mediaEmptyDescription.beVisible()
            binding.mediaEmptyTextPlaceholder.beVisible()
            binding.mediaEmptyTextPlaceholder2.setText(R.string.photo_profiles_create)
            binding.mediaEmptyTextPlaceholder2.beVisible()
            binding.mediaFastscroller.beGone()
            return
        }
        val requestedToken = photoToken()
        ensureBackgroundThread {
            val needsUnlock = runCatching {
                val roots = PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile)
                !PhotoProfileAccessSessionManager.bindingsAuthorize(roots, config.photoSourceBindings,
                    PhotoProfileAccessSessionManager.isActive(applicationContext, requestedToken)) ||
                    (profile.sources.any { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } &&
                        !PhotoProfileAccessSessionManager.favoritesAuthorized(applicationContext, requestedToken))
            }.getOrDefault(true)
            runOnUiThread {
                if (mediaLoadRequests.isCurrent(request) && !isDestroyed && !isFinishing) {
                    photoNeedsUnlock = needsUnlock
                    refreshMenuItems()
                    if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                        updatePhotoEmptyState()
                    }
                }
            }
        }
        mIsGettingMedia = true
        setMediaLoading(true)
        binding.mediaEmptyTextPlaceholder.beGone()
        binding.mediaEmptyDescription.beGone()
        binding.mediaEmptyTextPlaceholder2.beGone()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, PhotoProfileMediaLoader.PATH,
            showAll = false, photoProfileId = profile.id, photoSessionId = photoToken(),
            onFailure = {
                if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed) {
                    publishMedia(arrayListOf(), false, request)
                    binding.mediaEmptyTextPlaceholder.setText(R.string.failed_to_load_media)
                    binding.mediaEmptyDescription.beGone()
                }
            }) { items ->
            if (mediaLoadRequests.isCurrent(request) && photoProfileId == profile.id && !isFinishing && !isDestroyed) {
                publishMedia(items, false, request)
                if (mLastSearchedText.isNotEmpty()) searchQueryChanged(mLastSearchedText)
            }
        }.also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
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
    private var mLastSearchedText = ""
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

    private fun setMediaLoading(loading: Boolean) {
        mediaHeaderProgress?.apply {
            OxygenProgressStyle.apply(this@MediaActivity, config, this)
            visibility = if (loading) View.VISIBLE else View.GONE
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
            key == org.fossify.gallery.helpers.EXCLUDED_FOLDERS
        if (isPhotoProfiles && sourceProtectionChanged) {
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) getMedia()
            return@OnSharedPreferenceChangeListener
        }
        if (isPhotoProfiles && visibilityChanged) {
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
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
        setupMediaProgressIndicator()
        PhotoProfileAccessSessionManager.addListener(favoriteListenerId, ::onPhotoAccessRevoked)
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
            getMedia()
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
        if (mPath == FAVORITES && savedInstanceState?.getBoolean("favoritesReturnPending") == true) {
            showFavoritesReturnCover()
        }
        binding.mediaGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) favoritesScroll.clear()
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
                GalleryBottomBar.Tab.valueOf(mGallerySection!!.name)
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
                if (PhotoProfileRepository.of(this).state().current == null) profileDialogs().edit()
                else if (photoNeedsUnlock) unlockPhotoSources() else profileDialogs().edit(PhotoProfileRepository.of(this).state().current)
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

        // do not refresh Random sorted files after opening a fullscreen image and going Back
        val isRandomSorting = config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0
        if (isPhotoProfiles) {
            tryLoadGallery()
        } else if (mPath == FAVORITES) {
            tryLoadGallery()
        } else if (mMedia.isEmpty() || !isRandomSorting ||
            (isRandomSorting && !mWasFullscreenViewOpen)) {
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
                runOnUiThread {
                    if (request != folderAuthGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (authorized) {
                        tryLoadGallery()
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
        if (mPath == FAVORITES) rememberFavoritesScrollAnchor()
        favoritesScroll.cancelLayout()
        favoritesReturnCover?.pause()
        mediaSearchGeneration++
        super.onPause()
        mIsGettingMedia = false
        setMediaLoading(false)
        mediaLoadRequests.invalidate()
        mediaScanGeneration++
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
        mCurrAsyncTask?.stopFetching()
    }

    override fun onStop() {
        super.onStop()
        if (isPhotoProfiles) {
            mediaLoadRequests.invalidate()
            mCurrAsyncTask?.stopFetching()
            binding.mediaGrid.adapter = null
            mMedia = ArrayList()
            mLoadedInitialPhotos = false
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
        outState.putString("favoriteOwnerId", favoriteOwnerId)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        favoritesReturnCover?.dismiss()
        favoritesScroll.clear()
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
        PhotoProfileAccessSessionManager.removeListener(favoriteListenerId)
        favoritesReturnCover?.let {
            it.dismiss()
            binding.mediaCoordinator.removeView(it)
        }
        favoritesReturnCover = null
        favoritesScroll.cancelLayout()
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
            GalleryDatabase.destroyInstance()
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
    }

    private fun refreshMenuItems() {
        val isDefaultFolder = !config.defaultFolder.isEmpty()
                && File(config.defaultFolder).compareTo(File(mPath)) == 0

        binding.mediaMenu.requireToolbar().menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.photo_profiles_manage).isVisible = isPhotoProfiles
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
                R.id.photo_profiles_manage -> profileDialogs().manage()
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
        OxygenToolbarStyle.apply(
            this, binding.mediaMenu,
            when {
                isPhotoProfiles -> PhotoProfileRepository.of(this).state().current?.name ?: getString(R.string.gallery_nav_photos)
                mShowAll || mPath.isEmpty() -> getString(R.string.app_launcher_name)
                mPath == FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                mPath == RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                else -> getHumanizedFilename(mPath)
            },
            backButton = mPath == FAVORITES || (mGallerySection == null && !mShowAll),
            titleAction = if (isPhotoProfiles) ({ if (!binding.mediaMenu.isSearchOpen) profileDialogs().switch() }) else null
        )
        val picker = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent || isSetWallpaperIntent()
        if (!picker && mPath != RECYCLE_BIN) {
            val isHome = mPath.isEmpty() || (isPhotoProfiles && org.fossify.gallery.helpers.PhotoPlaybackPolicy.toolbarScope(photoHasContent) == org.fossify.gallery.helpers.ToolbarQuickScope.HOME)
            val viewType = config.getFolderViewType(if (isHome && !isPhotoProfiles) SHOW_ALL else mPath)
            org.fossify.gallery.helpers.ToolbarQuickActions.apply(
                this, binding.mediaMenu,
                if (isHome) org.fossify.gallery.helpers.ToolbarQuickScope.HOME
                    else org.fossify.gallery.helpers.ToolbarQuickScope.FOLDER,
                if (isHome) config.toolbarHomeActions else config.toolbarFolderActions,
                hasBack = !isHome && !isPhotoProfiles,
                fixedActionCount = if (hasFixedBrowseSwitch) 1 else 0,
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
        val finalResult = displayedMediaIsFinal
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
                    if (safeGrouped.isEmpty()) {
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
                        binding.mediaFastscroller.beVisible()
                    }

                    handleGridSpacing(safeGrouped)
                    getMediaAdapter()?.updateMedia(safeGrouped)
                    restoreFavoritesScrollAnchor(request, finalResult)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        requestMediaPermissions {
            val dirName = when (mPath) {
                PhotoProfileMediaLoader.PATH -> PhotoProfileRepository.of(this).state().current?.name ?: getString(R.string.gallery_nav_photos)
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
                binding.loadingIndicator.show()
                mShowLoadingIndicator = false
            }

            getMedia()
            setupLayoutManager()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter() {
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
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mMedia)
            handleGridSpacing()
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

    private fun checkLastMediaChanged() {
        if (isDestroyed || config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
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

    private fun getMedia() {
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
        val request = mediaLoadRequests.begin()
        if (mPath == FAVORITES && favoritesReturnCover?.covering == true) {
            val token = favoriteSessionId
            favoritesReturnCover?.pause()
            favoritesReturnCover?.armTimeout({ isFavoritesReturnCurrent(request, token) }, ::retryFavoritesReturn)
        }
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = true
        setMediaLoading(true)
        if (mPath == FAVORITES) {
            binding.mediaEmptyTextPlaceholder.beGone()
            binding.mediaEmptyDescription.beGone()
            binding.mediaEmptyTextPlaceholder2.beGone()
        }
        val sortingPath = if (mShowAll) SHOW_ALL else mPath
        val sortingBySize = config.getFolderSorting(sortingPath) and SORT_BY_SIZE != 0

        if (mLoadedInitialPhotos) {
            startAsyncTask(request)
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent,
                allowedFavoritePaths = favoriteAccessPaths
            ) { cached ->
                if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return@getCachedMedia

                if (cached.isEmpty()) {
                    runOnUiThread {
                        if (mediaLoadRequests.isCurrent(request)) binding.mediaRefreshLayout.isRefreshing = true
                    }
                } else {
                    val cachedHasUnknownSize = cached.asSequence()
                        .filterIsInstance<Medium>()
                        .any { it.size <= 0L }
                    // Cached metadata is allowed for fast display only when it can
                    // preserve both size ordering and the fast-scroller size label.
                    if (!sortingBySize || !cachedHasUnknownSize) {
                        gotMedia(cached, true, request)
                    }
                }
                startAsyncTask(request)
            }
        }

        mLoadedInitialPhotos = true
    }

    private fun startAsyncTask(request: Long) {
        if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) return
        // A canceled or superseded scan must not change the visible list or cached data.
        val scanGeneration = ++mediaScanGeneration
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(
            context = applicationContext,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll,
            favoritePathsOverride = if (mPath == FAVORITES)
                favoriteAccessPaths?.toCollection(ArrayList()) else null,
            favoriteSessionId = if (mPath == FAVORITES) favoriteSessionId else null,
            onFailure = { error ->
                android.util.Log.e("GalleryMedia", "Media scan failed", error)
                runOnUiThread {
                    if (mediaLoadRequests.isCurrent(request) && !isFinishing && !isDestroyed) {
                        mIsGettingMedia = false
                        setMediaLoading(false)
                        binding.loadingIndicator.hide()
                        binding.mediaRefreshLayout.isRefreshing = false
                        if (mPath == FAVORITES) {
                            // The album is cleared while its viewer is open. Make a
                            // failed return scan visible and let pull-to-refresh retry.
                            binding.mediaEmptyTextPlaceholder.text = getString(R.string.failed_to_load_media)
                            binding.mediaEmptyTextPlaceholder.beVisible()
                            binding.mediaEmptyTextPlaceholder2.beGone()
                            binding.mediaFastscroller.beGone()
                            favoritesReturnCover?.showError(::retryFavoritesReturn)
                        }
                    }
                }
            }
        ) { scannedMedia ->
            ensureBackgroundThread {
                if (scanGeneration != mediaScanGeneration || !mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed) {
                    return@ensureBackgroundThread
                }
                try {
                    val oldMedia = mMedia.filterIsInstance<Medium>()
                    val newPaths = scannedMedia.filterIsInstance<Medium>()
                        .mapTo(HashSet()) { it.path }
                    gotMedia(scannedMedia, false, request)

                    // A filtered/partial/permission-limited scan is NOT evidence that an
                    // old file was deleted. Never clear a favorite relationship here.
                    // Only prune cached metadata for files confirmed absent from an
                    // accessible parent directory.
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

        mCurrAsyncTask!!.execute()
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
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            // A favorite album can scan empty when its storage is temporarily
            // unavailable. Keep the virtual folder and saved relationships.
            if (mPath == RECYCLE_BIN || mPath == FAVORITES) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                finish()
            }

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

    private fun openInViewPager(path: String) {
        if (mPath == FAVORITES && !FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, path)) {
            finish(); return
        }
        val folderToken = folderUnlockSessionId
        val app = applicationContext
        ensureBackgroundThread {
            val folderAuthorized = runCatching {
                FolderUnlockSessionManager.isMediaAuthorized(folderToken, path, app.config::isFolderProtected)
            }.getOrDefault(false)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Intent(this, ViewPagerActivity::class.java).apply {
                    if (folderAuthorized) putExtra(FOLDER_UNLOCK_SESSION, folderToken)
                    if (mPath == FAVORITES) putExtra(FAVORITE_ACCESS_SESSION, favoriteSessionId)
                    putExtra(PATH, path)
                    if (isPhotoProfiles) {
                    putExtra(PhotoProfileMediaLoader.EXTRA_ID, photoProfileId)
                    putExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION, photoToken())
                }
                    putExtra(SHOW_ALL, mShowAll)
                    putExtra(SHOW_FAVORITES, mPath == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
                    putExtra(IS_FROM_GALLERY, true)
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
        publishMedia(media, isFromCache, request)
    }

    private fun publishMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean, request: Long) {
        runOnUiThread {
            if (!mediaLoadRequests.isCurrent(request) || isFinishing || isDestroyed ||
                (isFromCache && completedMediaRequest == request)) return@runOnUiThread

            if (!isFromCache) {
                mIsGettingMedia = false
                setMediaLoading(false)
                completedMediaRequest = request
            }
            checkLastMediaChanged()
            mMedia = ArrayList(media)
            if (isPhotoProfiles && !isFromCache) photoHasContent = media.any { it is Medium }
            // The slideshow shortcut is only useful once at least one authorized
            // media item has been published; refresh after cache and final scan.
            if (isPhotoProfiles || org.fossify.gallery.helpers.ToolbarQuickActions.SLIDESHOW in
                (if (mPath.isEmpty()) config.toolbarHomeActions else config.toolbarFolderActions)) {
                refreshMenuItems()
            }
            displayedMediaRequest = request
            displayedMediaIsFinal = !isFromCache
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
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
            if (mPath == FAVORITES && media.isEmpty() && !isFromCache) {
                binding.mediaGrid.adapter = null
                favoritesScroll.clear()
                val token = favoriteSessionId
                favoritesReturnCover?.revealAfterLayout { isFavoritesReturnCurrent(request, token) }
            } else {
                setupAdapter()
            }

            // Cache can avoid a top-of-list flash, but only the final authorized
            // dataset may consume the anchor after layout confirms the scroll.
            if (mLastSearchedText.isEmpty()) {
                restoreFavoritesScrollAnchor(request, !isFromCache)
            }

            if (mPath == FAVORITES && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                android.util.Log.d("GalleryFavorites", "source=${if (isFromCache) "cache" else "scan"}, adapterMedia=${mMedia.count { it is Medium }}")
            }
            if (!isFromCache && !isPhotoProfiles) {
                // Never let a previous request persist stale favorites after a new refresh.
                val mediaToInsert = mMedia.filterIsInstance<Medium>().filter { it.deletedTS == 0L }
                ensureBackgroundThread {
                    if (mediaLoadRequests.isCurrent(request)) {
                        try {
                            mediaDB.insertAll(mediaToInsert)
                        } catch (error: Exception) {
                            android.util.Log.e("GalleryMedia", "Cannot cache scanned media", error)
                        }
                    }
                }
            }
        }
        if (mediaLoadRequests.isCurrent(request)) {
            mLatestMediaId = getLatestMediaId()
            mLatestMediaDateId = getLatestMediaByDateId()
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems
            .filter { !getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (
            config.useRecycleBin
            && !skipRecycleBin
            && !filtered.first().path.startsWith(recycleBinPath)
        ) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                finish()
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



