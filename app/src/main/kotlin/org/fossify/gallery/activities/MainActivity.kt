package org.fossify.gallery.activities

import org.fossify.gallery.helpers.FOLDER_UNLOCK_SESSION
import org.fossify.gallery.helpers.FolderUnlockSessionManager
import org.fossify.gallery.helpers.ViewerAccessCoordinator
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.ViewGroup
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import java.util.concurrent.Future
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.DirectoryScanEpoch
import org.fossify.gallery.helpers.DirectoryMetadataRepository
import org.fossify.gallery.helpers.DirectoryScanScope
import org.fossify.gallery.helpers.DirectoryScanSnapshot
import org.fossify.gallery.helpers.FolderMediaReadResult
import org.fossify.gallery.helpers.FolderReadStatus
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileCount
import org.fossify.commons.extensions.getFilePublicUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.helpers.GalleryScanResult
import org.fossify.gallery.helpers.GalleryScanState
import org.fossify.gallery.helpers.GalleryScanTerminalGate
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMainBinding
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.addTempFolderIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoriteFoldersDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getDirectorySortingValue
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getDistinctPath
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.getOTGFolderChildrenNames
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.movePinnedDirectoriesToFront
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.removeInvalidDBDirectories
import org.fossify.gallery.extensions.storeDirectoryItems
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateDBDirectory
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.FavoriteFolderRules
import org.fossify.gallery.helpers.FavoriteMenuActionTitles
import org.fossify.gallery.helpers.FavoriteAccessPolicy
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import org.fossify.gallery.helpers.OxygenFastScroller
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenProgressStyle
import org.fossify.gallery.helpers.MediaCachePrunePolicy
import org.fossify.gallery.helpers.FavoriteFolderLoadCoordinator
import org.fossify.gallery.helpers.FavoriteFolderLoadKey
import org.fossify.gallery.helpers.FavoriteFolderLoadStatus
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.GROUP_DESCENDING
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.MONTH_MILLISECONDS
import org.fossify.gallery.helpers.GalleryBottomBar
import org.fossify.gallery.helpers.GalleryNavigation
import org.fossify.gallery.helpers.GalleryScreen
import org.fossify.gallery.helpers.GallerySection
import org.fossify.gallery.helpers.GalleryContentView
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenSearchNavigation
import org.fossify.gallery.helpers.GalleryPerfTrace
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.getDefaultFileFilter
import org.fossify.gallery.helpers.getPermissionToRequest
import org.fossify.gallery.helpers.getPermissionsToRequest
import org.fossify.gallery.interfaces.DirectoryOperationsListener
import org.fossify.gallery.jobs.NewPhotoFetcher
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : SimpleActivity(), DirectoryOperationsListener {
    override var isSearchBarEnabled = true

    companion object {
        private const val PICK_MEDIA = 2
        private const val PICK_WALLPAPER = 3
        private const val LAST_MEDIA_CHECK_PERIOD = 3000L
        const val EXTRA_SHOW_FOLDERS = "org.fossify.gallery.SHOW_FOLDERS"
        const val EXTRA_FOLDER_FAVORITES = "org.fossify.gallery.FOLDER_FAVORITES"
    }

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mGallerySection = GallerySection.ALL
    private val mShowingFolderFavorites get() = mGallerySection == GallerySection.FAVORITES
    // Sensitive categories are opt-in for the current Activity session only.
    private var mFolderVisibility = booleanArrayOf(false, false, false, true)
    private fun isSpecialFolderFilterActive() = mFolderVisibility[0] ||
        mFolderVisibility[1] || mFolderVisibility[2] || !mFolderVisibility[3]
    private val favoriteLoad = FavoriteFolderLoadCoordinator()
    private var favoriteViewForeground = false
    private var categorizedLoadVersion = 0L
    private var categorizedLoadActive = false
    private var categorizedReloadPending = false
    private var mFavoriteFolderPaths: Set<String> = emptySet()
    // Saved visibility preferences never serve as authentication credentials.
    private var mFavoriteHiddenAuthorized = false
    private var mFavoriteExcludedAuthorized = false
    private var mFavoriteAuthRequested = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    @Volatile private var mShouldStopFetching = false
    private var mWasDefaultFolderChecked = false
    private var mWasMediaManagementPromptShown = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L

    // used at "Group direct subfolders" for navigation
    private var mCurrentPathPrefix = ""

    // used at "Group direct subfolders" for navigating Up with the back button
    private var mOpenedSubfolders = arrayListOf("")

    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    @Volatile private var mLastMediaFetcher: MediaFetcher? = null
    private val directoryScanEpoch = DirectoryScanEpoch()
    private val directoryScanTerminals = GalleryScanTerminalGate()
    // Diagnostic leases are separate from business scan epochs and cannot publish UI.
    private var activeHomeScanDiagnostics: org.fossify.gallery.helpers.MediaPipelineDiagnostics? = null
    private var activeCategoryScanDiagnostics: org.fossify.gallery.helpers.MediaPipelineDiagnostics? = null
    // Cache preview is optional. Kept injectable for Activity-level failure regression tests.
    internal var directoryCacheReader: () -> List<Directory> = { directoryDB.getAll() }
    private val directoryRandomOrder = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var directorySortStartedNanos = 0L
    private var directorySortRequestedMode = 0

    private fun isCurrentDirectoryScan(id: Long): Boolean =
        directoryScanEpoch.isCurrent(id) && !mShouldStopFetching &&
            !mShowingFolderFavorites && !isDestroyed && !isFinishing

    /** Called on the UI thread before navigation, invalidation or final stop. */
    private fun retireDirectoryScan() {
        activeHomeScanDiagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
        activeHomeScanDiagnostics = null
        activeCategoryScanDiagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
        activeCategoryScanDiagnostics = null
        directoryScanTerminals.finish(directoryScanEpoch.currentVersion(), GalleryScanResult(GalleryScanState.CANCELLED))
        directoryScanEpoch.retire()
        categorizedLoadVersion++
        categorizedLoadActive = false
        mShouldStopFetching = true
        mLastMediaFetcher?.shouldStop = true
        mIsGettingDirs = false
        setDirectoryLoading(false)
    }
    private var mDirs = ArrayList<Directory>()
    private var mDirsIgnoringSearch = ArrayList<Directory>()
    private var displayedDirectoryMetadataRevision = 0L
    private val directoryMetadataRebindGate = org.fossify.gallery.helpers.DirectoryMetadataRebindGate()
    private val directoryMetadataHandler = Handler(android.os.Looper.getMainLooper())
    private val directoryMetadataRebindWatchdog = org.fossify.gallery.helpers.RefreshWatchdog()
    private var directoryMetadataRebindScheduled = false
    private val directoryMetadataRebindRunnable = Runnable {
        directoryMetadataRebindScheduled = false
        publishPendingDirectoryMetadata()
    }

    private fun canPublishDirectoryMetadata(): Boolean =
        !isFinishing && !isDestroyed && !mShowingFolderFavorites && !config.showAll &&
            !isSpecialFolderFilterActive() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    private fun scheduleDirectoryMetadataRebind() {
        if (!directoryMetadataRebindGate.hasPending() || directoryMetadataRebindScheduled ||
            directoryMetadataRebindGate.isActive() || mIsGettingDirs || !canPublishDirectoryMetadata()) return
        directoryMetadataRebindScheduled = true
        directoryMetadataHandler.postDelayed(directoryMetadataRebindRunnable, 250L)
    }

    private fun failDirectoryMetadataRebind(requestId: Long, error: Exception) {
        android.util.Log.w("GalleryDirs", "Metadata catalog read failed", error)
        runOnUiThread {
            if (directoryMetadataRebindGate.finish(requestId, applied = false)) {
                directoryMetadataRebindWatchdog.stop()
                // Keep changed paths until the next event, resume or manual refresh.
            }
        }
    }

    private fun publishPendingDirectoryMetadata() {
        if (mIsGettingDirs || !canPublishDirectoryMetadata()) return
        val request = directoryMetadataRebindGate.begin() ?: return
        val scanEpoch = directoryScanEpoch.currentVersion()
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        directoryMetadataRebindWatchdog.stop()
        directoryMetadataRebindWatchdog.arm {
            if (directoryMetadataRebindGate.finish(request.id, applied = false)) {
                android.util.Log.w("GalleryDirs", "Metadata catalog refresh timed out; awaiting retry")
            }
        }
        getCachedDirectories(getVideos && !getImages, getImages && !getVideos,
            executor = org.fossify.gallery.helpers.GalleryAlbumScanExecutor,
            isCurrent = { directoryMetadataRebindGate.isCurrent(request.id) && !isDestroyed && !isFinishing },
            onFailure = { error -> failDirectoryMetadataRebind(request.id, error) }
        ) { cached ->
            val catalog = try {
                val favoriteCount = runCatching { favoritesDB.getFavoritesCount() }
                    .onFailure { android.util.Log.w("GalleryDirs", "Cannot read favorites count", it) }.getOrNull()
                val deletedCount = if (config.showRecycleBinAtFolders) {
                    runCatching { mediaDB.getDeletedMediaCount().toLong() }
                        .onFailure { android.util.Log.w("GalleryDirs", "Cannot read recycle count", it) }.getOrNull()
                } else 0L
                org.fossify.gallery.helpers.RootVirtualAlbumPolicy.reconcile(
                    cached, favoriteCount, deletedCount, config.showRecycleBinAtFolders,
                    config.isFavoritePasswordProtectionOn || config.isFolderProtected(FAVORITES),
                    config.shouldShowHidden,
                    getString(org.fossify.commons.R.string.favorites),
                    getString(org.fossify.commons.R.string.recycle_bin), LOCATION_INTERNAL
                )
            } catch (error: Exception) {
                failDirectoryMetadataRebind(request.id, error)
                return@getCachedDirectories
            }
            runOnUiThread {
                if (!directoryMetadataRebindGate.isCurrent(request.id)) return@runOnUiThread
                val isCurrentPresentation = {
                    directoryMetadataRebindGate.isLatest(request.id) &&
                        canPublishDirectoryMetadata() && !mIsGettingDirs &&
                        directoryScanEpoch.currentVersion() == scanEpoch
                }
                directoryMetadataRebindWatchdog.stop()
                if (!isCurrentPresentation()) {
                    directoryMetadataRebindGate.finish(request.id, applied = false)
                    return@runOnUiThread
                }
                setupAdapter(addTempFolderIfNeeded(catalog), presentationIsCurrent = isCurrentPresentation,
                    onCommitted = {
                        if (directoryMetadataRebindGate.finish(request.id, applied = isCurrentPresentation()))
                            scheduleDirectoryMetadataRebind()
                    },
                    onDiscarded = {
                        // No self-retry loop: next metadata event/resume/manual scan retries retained paths.
                        directoryMetadataRebindGate.finish(request.id, applied = false)
                    })
            }
        }
    }

    private val directoryMetadataListener: (String) -> Unit = {
        runOnUiThread {
            if (canPublishDirectoryMetadata()) {
                val repository = org.fossify.gallery.helpers.DirectoryMetadataRepository
                val changed = repository.changedSince(displayedDirectoryMetadataRevision)
                displayedDirectoryMetadataRevision = repository.currentRevision()
                if (changed.isNotEmpty()) {
                    directoryMetadataRebindGate.enqueue(changed)
                    scheduleDirectoryMetadataRebind()
                }
            }
        }
    }

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredStyleString = ""
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private var directoryHeaderProgress: com.google.android.material.progressindicator.LinearProgressIndicator? = null

    private fun setupDirectoryProgressIndicator() {
        if (directoryHeaderProgress != null) return
        directoryHeaderProgress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            OxygenProgressStyle.apply(this@MainActivity, config, this)
        }.also { indicator ->
            binding.mainMenu.addView(indicator, android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                OxygenProgressStyle.thicknessPx(this)
            ))
        }
    }

    private var directoryTraceRequest = 0L

    /** Preserve cached album cards; an empty, failed scan needs a retry action. */
    private fun presentDirectoryLoadFailure(messageRes: Int) {
        if (isDestroyed || isFinishing) return
        val hasVisibleAlbums = getRecyclerAdapter()?.dirs?.isNotEmpty() == true
        if (hasVisibleAlbums) {
            toast(messageRes)
            return
        }
        binding.directoriesEmptyPlaceholder.setText(messageRes)
        binding.directoriesEmptyPlaceholder.beVisible()
        binding.directoriesEmptyPlaceholder2.setText(R.string.media_load_retry)
        binding.directoriesEmptyPlaceholder2.beVisible()
        binding.directoriesEmptyPlaceholder2.setOnClickListener {
            if (!isFinishing && !isDestroyed && !mIsGettingDirs) {
                getDirectories(forceFavoriteReload = true, forceRefresh = true)
            }
        }
        binding.directoriesFastscroller.beGone()
    }

    private val directoryRefreshWatchdog = org.fossify.gallery.helpers.RefreshWatchdog()
    private fun setDirectoryLoading(loading: Boolean) {
        val update = {
            if (loading) org.fossify.gallery.helpers.FolderRefreshTrace.configure(applicationContext)
            if (loading) directoryRefreshWatchdog.arm {
                org.fossify.gallery.helpers.FolderRefreshTrace.event(directoryTraceRequest, "album_timeout")
                val request = directoryScanEpoch.currentVersion()
                if (isCurrentDirectoryScan(request) && !mShowingFolderFavorites && !categorizedLoadActive) {
                    finishDirectoryScan(request, GalleryScanResult(GalleryScanState.TIMED_OUT))
                } else {
                    presentDirectoryLoadFailure(R.string.folder_refresh_timeout)
                }
                retireDirectoryScan()
            } else directoryRefreshWatchdog.stop()
            if (!isDestroyed && !isFinishing) directoryHeaderProgress?.apply {
                OxygenProgressStyle.apply(this@MainActivity, config, this)
                OxygenProgressStyle.setLoading(this, loading)
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
        else runOnUiThread { update() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        org.fossify.gallery.helpers.OxygenClassicEdgeGlow.install(binding.root, this)
        GalleryBottomBar.prepareRootSurface(this, binding.root)
        setupDirectoryProgressIndicator()
        binding.directoriesRefreshLayout.setColorSchemeColors(getProperPrimaryColor())
        binding.directoriesRefreshLayout.isEnabled = true
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            // Retain periodic cleanup and job registration even when the
            // launcher immediately hands off to the All Photos Activity.
            checkRecycleBinItems()
            startNewPhotoFetcher()
            // Only potentially slow directory inspection is deferred.
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) removeTempFolder()
            }, 800L)
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent = mIsPickImageIntent
                || mIsPickVideoIntent
                || mIsGetImageContentIntent
                || mIsGetVideoContentIntent
                || mIsGetAnyContentIntent
                || mIsSetWallpaperIntent

        mGallerySection = if (mIsThirdPartyIntent) GallerySection.ALL else
            GalleryNavigation.resolveSection(
                intent.getStringExtra(GalleryNavigation.EXTRA_SECTION),
                intent.getBooleanExtra(EXTRA_FOLDER_FAVORITES, false)
            )
        // The launcher honors the last All Photos / folder-view choice. Only
        // explicit navigation requests override it; never reset it on cold start.
        when {
            mShowingFolderFavorites -> config.showAll = false
            intent.getBooleanExtra(EXTRA_SHOW_FOLDERS, false) -> {
                config.showAll = false
                if (!mIsThirdPartyIntent) config.lastMainViewAll = false
            }
            !mIsThirdPartyIntent -> config.showAll = config.lastMainViewAll
        }

        setupOptionsMenu()
        refreshMenuItems()
        binding.mainMenu.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateMenuColors()
        }

        if (!mIsThirdPartyIntent) {
            GalleryBottomBar.attach(this, binding.root,
                if (mShowingFolderFavorites) GalleryBottomBar.Tab.FAVORITES else GalleryBottomBar.Tab.ALL,
                loadingFrame = { directoryHeaderProgress?.let(OxygenProgressStyle::revealPending) }
            ) { tab ->
                getRecyclerAdapter()?.finishActMode()
                val section = GallerySection.valueOf(tab.name)
                openGalleryRoot(GalleryNavigation.bottomDestination(
                    section, config.lastMainViewAll
                ))
            }
            val bottom = resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height)
            binding.directoriesGrid.setPadding(
                binding.directoriesGrid.paddingLeft, binding.directoriesGrid.paddingTop,
                binding.directoriesGrid.paddingRight, bottom
            )
            binding.directoriesGrid.clipToPadding = false
        }

        setupEdgeToEdge(
            padTopSystem = listOf(binding.mainMenu),
            padBottomImeAndSystem = listOf(binding.directoriesGrid)
        )

        binding.directoriesRefreshLayout.setOnRefreshListener {
            binding.directoriesRefreshLayout.isRefreshing = false
            getDirectories(forceFavoriteReload = true, forceRefresh = true)
            promptProtectedFavoriteFilters(force = true)
        }
        storeStateVariables()
        checkWhatsNewDialog()
        setupLatestMediaId()

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia and TYPE_SVGS == 0) {
                config.filterMedia += TYPE_SVGS
            }
        }

        if (!config.wasSortingByNumericValueAdded) {
            config.wasSortingByNumericValueAdded = true
            config.sorting = config.sorting or SORT_USE_NUMERIC_VALUE
        }

        updateWidgets()
        registerFileUpdateListener()

        binding.directoriesSwitchSearching.setOnClickListener {
            launchSearchActivity()
        }

        // just request the permission, tryLoadGallery will then trigger in onResume
        handleMediaPermissions()
    }

    private fun handleMediaPermissions(callback: (() -> Unit)? = null) {
        requestMediaPermissions(enableRationale = true) {
            callback?.invoke()
            if (isRPlus() && !mWasMediaManagementPromptShown) {
                mWasMediaManagementPromptShown = true
                handleMediaManagementPrompt { }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        favoriteViewForeground = true
        // Settings can change the credential while this Activity is stopped.
        // Rebind before its previous thumbnail is drawn again.
        getRecyclerAdapter()?.refreshFavoriteProtection()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        directoryHeaderProgress?.let { OxygenProgressStyle.apply(this, config, it) }
        GalleryBottomBar.recolor(this)
        config.isThirdPartyIntent = false
        mDateFormat = config.dateFormat
        mTimeFormat = getTimeFormat()

        refreshMenuItems()

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.directoriesGrid.adapter = null
            getDirectories()
        }

        if (mStoredTextColor != getProperTextColor()) {
            getRecyclerAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor()
        }

        val styleString =
            "${config.folderStyle}${config.showFolderMediaCount}${config.limitFolderTitle}"
        if (mStoredStyleString != styleString) {
            setupAdapter(mDirs, forceRecreate = true)
        }

        binding.directoriesFastscroller.updateColors(primaryColor)
        styleFastScroller()
        binding.directoriesRefreshLayout.isEnabled = true
        getRecyclerAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.directoriesEmptyPlaceholder.setTextColor(getProperTextColor())
        binding.directoriesEmptyPlaceholder2.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.underlineText()
        binding.directoriesEmptyPlaceholder2.bringToFront()

        org.fossify.gallery.helpers.DirectoryMetadataRepository.observe(directoryMetadataListener)
        directoryMetadataListener("")
        scheduleDirectoryMetadataRebind()
        if (!isSpecialFolderFilterActive()) {
            org.fossify.gallery.helpers.DirectoryMetadataRepository.refreshPendingPaths(applicationContext)
        }
        // A canceled category request must resume even while inline search is open.
        resumeCategorizedLoadIfNeeded()
        if (!binding.mainMenu.isSearchOpen) {
            refreshMenuItems()
            tryLoadGallery()
            promptProtectedFavoriteFilters()
        }

        if (config.searchAllFilesByDefault && !mShowingFolderFavorites) {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))
        } else {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_folders))
        }
    }

    override fun onPause() {
        org.fossify.gallery.helpers.DirectoryMetadataRepository.removeObserver(directoryMetadataListener)
        directoryMetadataHandler.removeCallbacks(directoryMetadataRebindRunnable)
        directoryMetadataRebindScheduled = false
        directoryMetadataRebindWatchdog.stop()
        directoryMetadataRebindGate.cancel()
        mediaPollGeneration++
        mediaPollScheduled = false
        mediaPollInFlight = false
        super.onPause()
        // Authentication Activities can temporarily pause us. Favorite requests are
        // retired at onStop, not here, so their successful results are not lost.
        categorizedLoadVersion++
        activeCategoryScanDiagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
        activeCategoryScanDiagnostics = null
        if (!mShowingFolderFavorites && isSpecialFolderFilterActive()) categorizedReloadPending = true
        if (categorizedLoadActive) {
            mLastMediaFetcher?.shouldStop = true
            categorizedLoadActive = false
            mIsGettingDirs = false
            setDirectoryLoading(false)
        }
        if (!mShowingFolderFavorites) {
            binding.directoriesRefreshLayout.isRefreshing = false
            // Pausing for authentication must not allow a duplicate scan.
        }
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
        if (!mShowingFolderFavorites) retireDirectoryScan()
        favoriteViewForeground = false
        if (mShowingFolderFavorites) {
            favoriteLoad.invalidate()
            mIsGettingDirs = false
            setDirectoryLoading(false)
            mFavoriteHiddenAuthorized = false
            mFavoriteExcludedAuthorized = false
            // A canceled external authentication has no failure callback in
            // Commons. Keep the attempt latch until explicit user retry, so
            // returning to the Activity cannot launch an endless prompt loop.
            if (config.isExcludedPasswordProtectionOn || config.isHiddenPasswordProtectionOn) {
                clearFavoriteDirectoryViews()
            }
        }

        if (config.temporarilyShowHidden || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        directoryMetadataHandler.removeCallbacks(directoryMetadataRebindRunnable)
        directoryMetadataRebindWatchdog.stop()
        directoryMetadataRebindGate.cancel()
        directoryRefreshWatchdog.stop()
        GalleryBottomBar.dispose(this)
        directoryHeaderProgress?.let(OxygenProgressStyle::dispose)
        folderOpenGeneration++
        ViewerAccessCoordinator.cancel(folderOpenTask)
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                // The application database outlives pages and their background workers.
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            OxygenSearchNavigation.closeInlineSearch(this, binding.mainMenu)
            true
        } else if (config.groupDirectSubfolders) {
            if (mCurrentPathPrefix.isEmpty()) {
                appLockManager.lock()
                false
            } else {
                mOpenedSubfolders.removeAt(mOpenedSubfolders.lastIndex)
                mCurrentPathPrefix = mOpenedSubfolders.last()
                setupAdapter(mDirs)
                true
            }
        } else {
            appLockManager.lock()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true
                                && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> {
                            fillPickedPaths(resultData, resultIntent)
                        }

                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(RESULT_OK)
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        if (!mIsThirdPartyIntent) {
            binding.mainMenu.requireToolbar().menu.apply {
                findItem(R.id.show_all).isVisible = !mShowingFolderFavorites
                findItem(R.id.show_all).title = if (mShowingFolderFavorites)
                    getString(R.string.gallery_file_view) else getString(R.string.show_all)
                findItem(R.id.manage_unavailable_folder_favorites).isVisible = mShowingFolderFavorites
                findItem(R.id.favorite_always_show_excluded).apply {
                    isVisible = mShowingFolderFavorites
                    setTitle(FavoriteMenuActionTitles.excluded(config.favoriteShowExcluded))
                }
                findItem(R.id.favorite_always_show_hidden).apply {
                    isVisible = mShowingFolderFavorites
                    setTitle(FavoriteMenuActionTitles.hidden(config.favoriteShowHidden))
                }
                findItem(R.id.column_count).isVisible = config.viewTypeFolders == VIEW_TYPE_GRID
                findItem(R.id.set_as_default_folder).isVisible = !config.defaultFolder.isEmpty()
                findItem(R.id.open_recycle_bin).isVisible =
                    config.useRecycleBin && !config.showRecycleBinAtFolders
                findItem(R.id.more_apps_from_us).isVisible =
                    resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)
            }
        }

        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.temporarily_show_hidden).isVisible =
                !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.temporarily_show_excluded).isVisible =
                !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible =
                config.temporarilyShowExcluded
        }
        updateMenuColors()
    }

    private fun setupOptionsMenu() {
        val menuId = if (mIsThirdPartyIntent) {
            R.menu.menu_main_intent
        } else {
            R.menu.menu_main
        }

        OxygenToolbarStyle.preparePopup(this, binding.mainMenu)
        binding.mainMenu.requireToolbar().inflateMenu(menuId)
        binding.mainMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            binding.directoriesRefreshLayout.isEnabled =
                org.fossify.gallery.helpers.SearchRefreshPolicy.isRefreshEnabled(
                    binding.mainMenu.isSearchOpen, binding.mainMenu.getCurrentQuery()
                )
        }

        binding.mainMenu.onSearchOpenListener = {
            binding.directoriesRefreshLayout.isEnabled = false
            // Favorites always searches its own folder scope; never silently
            // switch to the global all-files search Activity.
            if (config.searchAllFilesByDefault && !mShowingFolderFavorites) {
                launchSearchActivity()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            setupAdapter(mDirsIgnoringSearch, text)
            binding.directoriesRefreshLayout.isEnabled =
                org.fossify.gallery.helpers.SearchRefreshPolicy.isRefreshEnabled(
                    binding.mainMenu.isSearchOpen, text
                )
            binding.directoriesSwitchSearching.beVisibleIf(text.isNotEmpty())
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            if (org.fossify.gallery.helpers.ToolbarQuickActions.isSlot(menuItem.itemId)) {
                when (org.fossify.gallery.helpers.ToolbarQuickActions.actionForSlot(binding.mainMenu, menuItem.itemId)) {
                    org.fossify.gallery.helpers.ToolbarQuickActions.SORT -> showSortingDialog()
                    org.fossify.gallery.helpers.ToolbarQuickActions.FILTER -> showFilterMediaDialog()
                    org.fossify.gallery.helpers.ToolbarQuickActions.VIEW -> changeViewType()
                    org.fossify.gallery.helpers.ToolbarQuickActions.COLUMNS -> changeColumnCount()
                    org.fossify.gallery.helpers.ToolbarQuickActions.HIDDEN -> tryToggleTemporarilyShowHidden()
                    org.fossify.gallery.helpers.ToolbarQuickActions.EXCLUDED -> tryToggleTemporarilyShowExcluded()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
            when (menuItem.itemId) {
                R.id.quick_toolbar_search_overflow -> {
                    binding.mainMenu.focusView()
                    showKeyboard(binding.mainMenu.binding.topToolbarSearch)
                }
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.folder_visibility -> showFolderVisibilityDialog()
                R.id.manage_unavailable_folder_favorites -> manageUnavailableFolderFavorites()
                R.id.favorite_always_show_excluded -> {
                    val next = !config.favoriteShowExcluded
                    // Save the preference even when authentication is canceled.
                    config.favoriteShowExcluded = next
                    if (!next) mFavoriteExcludedAuthorized = false
                    clearFavoriteDirectoryViews()
                    refreshMenuItems()
                    getDirectories(forceFavoriteReload = true)
                    if (next) promptProtectedFavoriteFilters(force = true)
                }
                R.id.favorite_always_show_hidden -> {
                    val next = !config.favoriteShowHidden
                    config.favoriteShowHidden = next
                    if (!next) mFavoriteHiddenAuthorized = false
                    clearFavoriteDirectoryViews()
                    refreshMenuItems()
                    getDirectories(forceFavoriteReload = true)
                    if (next) promptProtectedFavoriteFilters(force = true)
                }
                R.id.open_camera -> launchCamera()
                R.id.show_all -> showAllMedia()
                R.id.change_view_type -> changeViewType()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
        OxygenSystemBars.apply(this)
        OxygenToolbarStyle.apply(
            this, binding.mainMenu,
            when {
                mShowingFolderFavorites -> getString(org.fossify.commons.R.string.favorites)
                mIsThirdPartyIntent -> getString(R.string.app_launcher_name)
                else -> getString(R.string.gallery_nav_albums)
            }
        )
        if (!mIsThirdPartyIntent) {
            val fixedBrowseActionCount = if (
                binding.mainMenu.requireToolbar().menu.findItem(R.id.show_all).isVisible
            ) 1 else 0
            org.fossify.gallery.helpers.ToolbarQuickActions.apply(
                this, binding.mainMenu,
                org.fossify.gallery.helpers.ToolbarQuickScope.HOME,
                config.toolbarHomeActions, hasBack = false,
                fixedActionCount = fixedBrowseActionCount,
                supported = { action ->
                    action != org.fossify.gallery.helpers.ToolbarQuickActions.SLIDESHOW &&
                        (action != org.fossify.gallery.helpers.ToolbarQuickActions.COLUMNS ||
                            config.viewTypeFolders == VIEW_TYPE_GRID)
                }
            )
        }
    }

    private fun getRecyclerAdapter() = binding.directoriesGrid.adapter as? DirectoryAdapter

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredStyleString = "$folderStyle$showFolderMediaCount$limitFolderTitle"
        }
    }

    private fun startNewPhotoFetcher() {
        val photoFetcher = NewPhotoFetcher()
        if (!photoFetcher.isScheduled(applicationContext)) {
            photoFetcher.scheduleJob(applicationContext)
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (getDoesFilePathExist(newFolder.absolutePath) && newFolder.isDirectory) {
                if (
                    newFolder.getProperSize(true) == 0L
                    && newFolder.getFileCount(true) == 0
                    && newFolder.list()?.isEmpty() == true
                ) {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            config.tempFolderPath
                        ), Toast.LENGTH_LONG
                    )
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull {
                    it.trimEnd('/') != internalStoragePath
                            && it.trimEnd('/') != sdCardPath
                }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }
            }
        }
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            val OTGPath = config.OTGPath
            spamFolders.forEach {
                if (getDoesFilePathExist(it, OTGPath)) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    private fun tryLoadGallery() {
        // avoid calling anything right after granting the permission, it will be called from onResume()
        val wasMissingPermission =
            config.appRunCount == 1 && !hasAllPermissions(getPermissionsToRequest())
        handleMediaPermissions {
            if (wasMissingPermission) {
                return@handleMediaPermissions
            }

            if (!mWasDefaultFolderChecked) {
                // An explicit root (launcher or bottom tab) must not be redirected
                // into the legacy default directory. Picker/direct-folder routes stay intact.
                if (!intent.hasExtra(GalleryNavigation.EXTRA_SECTION)) openDefaultFolder()
                mWasDefaultFolderChecked = true
            }

            checkOTGPath()
            checkDefaultSpamFolders()

            if (config.showAll) {
                showAllMedia()
            } else {
                getDirectories()
            }

            setupLayoutManager()
        }
    }

    private fun getDirectories(forceFavoriteReload: Boolean = false, forceRefresh: Boolean = false) {
        // Favorites have their own versioned state. The normal gallery scan lock
        // must never block a favorite reload after permission or auth changes.
        if (mShowingFolderFavorites) {
            loadFavoriteFoldersWithOverrides(forceFavoriteReload)
            return
        }
        if (mIsGettingDirs) {
            if (!forceRefresh) return
            // A manual pull owns a new generation, even while an old result is
            // deferred by scrolling or action mode. Cancellation reaches queries.
            retireDirectoryScan()
        }
        if (isSpecialFolderFilterActive()) {
            loadCategorizedFolders()
            return
        }

        mLastMediaFetcher?.shouldStop = true
        directoryMediaChangeGate.scanStarted()
        directoryMetadataRebindGate.cancel()
        directoryMetadataRebindWatchdog.stop()
        val scanRequest = directoryScanEpoch.begin()
        directoryScanTerminals.begin(scanRequest)
        val diagnostics = if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.MediaPipelineDiagnostics.create(
                "HOME", "HOME", if (forceRefresh) "REFRESH" else "INITIAL", scanRequest
            ) else null
        activeHomeScanDiagnostics = diagnostics
        var queuedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        directoryTraceRequest = scanRequest
        org.fossify.gallery.helpers.FolderRefreshTrace.event(scanRequest, "album_queued")
        mShouldStopFetching = false
        mIsGettingDirs = true
        setDirectoryLoading(true)
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        val launched = java.util.concurrent.atomic.AtomicBoolean(false)
        fun launch(cached: ArrayList<Directory>) {
            if (!isCurrentDirectoryScan(scanRequest) || !launched.compareAndSet(false, true)) return
            queuedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            try {
                org.fossify.gallery.helpers.GalleryAlbumScanExecutor.execute {
                    if (!isCurrentDirectoryScan(scanRequest)) {
                        val stoppedAt = android.os.SystemClock.elapsedRealtime()
                        diagnostics?.finishIfOpen("CANCELLED", (stoppedAt - queuedAtElapsedMs).coerceAtLeast(0L), 0L, 0L, -1L, -1L)
                        return@execute
                    }
                    val workerStartedElapsedMs = android.os.SystemClock.elapsedRealtime()
                    val outcome = try {
                        gotDirectories(addTempFolderIfNeeded(cached), scanRequest, forceRefresh, diagnostics)
                    } catch (error: Exception) {
                        android.util.Log.e("GalleryDirs", "Directory scan failed", error)
                        GalleryScanResult(if (error is java.util.concurrent.CancellationException ||
                            error is android.os.OperationCanceledException) GalleryScanState.CANCELLED
                            else GalleryScanState.FAILED, cause = error)
                    }
                    val workerFinishedElapsedMs = android.os.SystemClock.elapsedRealtime()
                    if (isCurrentDirectoryScan(scanRequest)) finishDirectoryScan(scanRequest, outcome, diagnostics,
                        queuedAtElapsedMs, workerStartedElapsedMs, workerFinishedElapsedMs)
                    else diagnostics?.finishIfOpen("CANCELLED", (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
                        (workerFinishedElapsedMs - workerStartedElapsedMs).coerceAtLeast(0L), 0L, -1L, -1L)
                }
            } catch (error: java.util.concurrent.RejectedExecutionException) {
                finishDirectoryScan(scanRequest, GalleryScanResult(GalleryScanState.FAILED, cause = error), diagnostics,
                    queuedAtElapsedMs, queuedAtElapsedMs, android.os.SystemClock.elapsedRealtime())
            }
        }
        // Capture an immutable presentation fallback on the UI thread; do not clear it on cache failure.
        val retained = mDirsIgnoringSearch.mapTo(ArrayList()) { it.snapshot() }
        getCachedDirectories(getVideos && !getImages, getImages && !getVideos,
            executor = org.fossify.gallery.helpers.GalleryAlbumScanExecutor,
            readCache = directoryCacheReader,
            onFailure = { error ->
                android.util.Log.w("GalleryDirs", "Cache preview skipped; continuing formal scan", error)
                launch(retained)
            },
            isCurrent = { isCurrentDirectoryScan(scanRequest) }
        ) { launch(it) }
    }

    private fun finishDirectoryScan(
        request: Long, result: GalleryScanResult,
        diagnostics: org.fossify.gallery.helpers.MediaPipelineDiagnostics? = null,
        queuedAtElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
        workerStartedElapsedMs: Long = queuedAtElapsedMs,
        workerFinishedElapsedMs: Long = android.os.SystemClock.elapsedRealtime()
    ) {
        runOnUiThread {
            if (!isCurrentDirectoryScan(request) || !directoryScanTerminals.finish(request, result)) {
                diagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
                return@runOnUiThread
            }
            diagnostics?.finish(
                result.state.name,
                (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
                (workerFinishedElapsedMs - workerStartedElapsedMs).coerceAtLeast(0L),
                (android.os.SystemClock.elapsedRealtime() - workerFinishedElapsedMs).coerceAtLeast(0L),
                diagnostics.snapshot()["candidateCount"] ?: -1L,
                diagnostics.snapshot()["acceptedCount"] ?: -1L
            )
            if (activeHomeScanDiagnostics === diagnostics) activeHomeScanDiagnostics = null
            mIsGettingDirs = false
            setDirectoryLoading(false)
            binding.directoriesRefreshLayout.isRefreshing = false
            onNormalDirectoryScanFinished(result)
            when (result.state) {
                GalleryScanState.PARTIAL -> presentDirectoryLoadFailure(R.string.media_load_partial_error)
                GalleryScanState.FAILED -> presentDirectoryLoadFailure(R.string.folder_load_failed)
                GalleryScanState.TIMED_OUT -> presentDirectoryLoadFailure(R.string.folder_refresh_timeout)
                else -> Unit
            }
        }
    }

    /** Remove old adapter and thumbnail references before reducing visible permissions. */
    private fun clearFavoriteDirectoryViews() {
        if (!mShowingFolderFavorites) return
        favoriteLoad.invalidate()
        binding.directoriesGrid.adapter = null
        mDirs.clear()
        mDirsIgnoringSearch.clear()
        mFavoriteFolderPaths = emptySet()
        mLoadedInitialPhotos = false
        binding.directoriesRefreshLayout.isRefreshing = false
        binding.directoriesEmptyPlaceholder.beGone()
        binding.directoriesEmptyPlaceholder2.beGone()
    }

    /**
     * The Commons protection callbacks report success, but not cancellation.
     * A canceled prompt is retried by explicit pull-to-refresh or menu action.
     */
    private fun promptProtectedFavoriteFilters(force: Boolean = false) {
        if (!mShowingFolderFavorites || !favoriteViewForeground ||
            isFinishing || isDestroyed || (mFavoriteAuthRequested && !force)) return
        val needHidden = (config.favoriteShowHidden || config.temporarilyShowHidden) &&
            config.isHiddenPasswordProtectionOn &&
            !mFavoriteHiddenAuthorized
        val needExcluded = (config.favoriteShowExcluded || config.temporarilyShowExcluded) &&
            config.isExcludedPasswordProtectionOn &&
            !mFavoriteExcludedAuthorized
        if (!needHidden && !needExcluded) {
            mFavoriteAuthRequested = false
            return
        }

        mFavoriteAuthRequested = true
        val askExcluded = {
            if ((config.favoriteShowExcluded || config.temporarilyShowExcluded) &&
                config.isExcludedPasswordProtectionOn &&
                !mFavoriteExcludedAuthorized && favoriteViewForeground) {
                handleExcludedFolderPasswordProtection {
                    if (favoriteViewForeground && !isFinishing && !isDestroyed) {
                        mFavoriteExcludedAuthorized = true
                        mFavoriteAuthRequested = false
                        getDirectories(forceFavoriteReload = true)
                    }
                }
            } else {
                mFavoriteAuthRequested = false
            }
        }
        if (needHidden) {
            handleHiddenFolderPasswordProtection {
                if (favoriteViewForeground && !isFinishing && !isDestroyed) {
                    mFavoriteHiddenAuthorized = true
                    getDirectories(forceFavoriteReload = true)
                    askExcluded()
                }
            }
        } else {
            askExcluded()
        }
    }

    private fun loadFavoriteFoldersWithOverrides(force: Boolean = false) {
        val hiddenProtected = config.isHiddenPasswordProtectionOn
        val excludedProtected = config.isExcludedPasswordProtectionOn
        val showHidden = (config.favoriteShowHidden || config.temporarilyShowHidden) &&
            (!hiddenProtected || mFavoriteHiddenAuthorized)
        val showExcluded = (config.favoriteShowExcluded || config.temporarilyShowExcluded) &&
            (!excludedProtected || mFavoriteExcludedAuthorized)
        val authorizationPending = ((config.favoriteShowHidden || config.temporarilyShowHidden) &&
                hiddenProtected && !mFavoriteHiddenAuthorized) ||
            ((config.favoriteShowExcluded || config.temporarilyShowExcluded) &&
                excludedProtected && !mFavoriteExcludedAuthorized)
        val excludedRoots = config.excludedFolders.toSet()
        val request = favoriteLoad.begin(
            FavoriteFolderLoadKey(showHidden, showExcluded, authorizationPending, excludedRoots),
            force
        ) ?: return
        mIsGettingDirs = true
        setDirectoryLoading(true)

        ensureBackgroundThread {
            try {
                // The favorite relationship is authoritative. Do not call
                // getCachedDirectories(): it filters .nomedia and may swallow
                // Room errors or run unrelated cache cleanup.
                val paths = favoriteFoldersDB.getPaths().filter { it.startsWith('/') }
                    .distinctBy(FavoriteFolderRules::normalizedPath)
                val cachedByPath = try {
                    paths.chunked(250).flatMap { directoryDB.getByPaths(it) }
                        .associateBy { FavoriteFolderRules.normalizedPath(it.path) }
                } catch (cacheError: Exception) {
                    android.util.Log.w("GalleryFavorites", "Directory metadata unavailable", cacheError)
                    emptyMap()
                }
                val selected = ArrayList<Directory>(paths.size)
                val accessiblePaths = HashSet<String>()
                fun protected(path: String) = FolderUnlockSessionManager.findProtectedAncestor(
                    path, config::isFolderProtected) != null
                // Classification uses only bounded path/ancestor checks. No child
                // enumeration, media query or date lookup precedes the manifest.
                for (path in paths) {
                    if (!favoriteLoad.isCurrent(request)) return@ensureBackgroundThread
                    val file = File(path)
                    val accessible = file.isDirectory && file.canRead()
                    if (accessible) accessiblePaths.add(path)
                    val hidden = FavoriteFolderRules.isDotHidden(path) ||
                        FavoriteFolderRules.hasNoMediaAncestor(path) || (!accessible && hiddenProtected)
                    val excluded = FavoriteFolderRules.isExcluded(path, excludedRoots)
                    if (!FavoriteFolderRules.shouldShow(hidden, excluded, showHidden, showExcluded)) continue
                    val cached = cachedByPath[FavoriteFolderRules.normalizedPath(path)]
                    val item = if (!protected(path) && accessible && cached != null) cached.snapshot()
                    else Directory().apply {
                        this.path = path
                        name = file.name.ifBlank { path }
                        location = LOCATION_INTERNAL
                    }
                    selected.add(item)
                }
                fun publish(complete: Boolean, failed: Boolean = false) {
                    val snapshot = selected.mapTo(ArrayList()) { it.snapshot() }
                    runOnUiThread {
                        if (!favoriteViewForeground || isFinishing || isDestroyed ||
                            !mShowingFolderFavorites || !favoriteLoad.isCurrent(request)) return@runOnUiThread
                        if (complete && !(if (failed) favoriteLoad.fail(request)
                            else favoriteLoad.complete(request, snapshot.size, authorizationPending))) return@runOnUiThread
                        if (!complete) favoriteLoad.visible(request)
                        mFavoriteFolderPaths = paths.toSet()
                        mLoadedInitialPhotos = true
                        mIsGettingDirs = !complete
                        // This indicator means first usable content, not authoritative
                        // metadata completion (which remains owned by favoriteLoad).
                        setDirectoryLoading(false)
                        binding.directoriesRefreshLayout.isRefreshing = false
                        setupAdapter(snapshot, presentationIsCurrent = {
                            favoriteViewForeground && mShowingFolderFavorites && favoriteLoad.isCurrent(request)
                        })
                        if (snapshot.isEmpty() && authorizationPending) {
                            binding.directoriesEmptyPlaceholder.setText(R.string.favorite_folders_auth_required)
                            binding.directoriesEmptyPlaceholder2.beGone()
                        }
                    }
                }
                publish(complete = false)
                GalleryPerfTrace.event("favorite_manifest_ready", selected.size)

                val fetcher = MediaFetcher(applicationContext, cancelled = { !favoriteLoad.isCurrent(request) },
                    reportReadFailures = true)
                val favorites = getFavoritePaths()
                val noMedia = getNoMediaFoldersSync()
                val covers = config.parseAlbumCovers()
                val dates = fetcher.getSelectedFolderDateOverrides(selected.map { it.path }.filterNot(::protected))
                var lastPublished = android.os.SystemClock.elapsedRealtime()
                var changedCards = 0
                var metadataFailed = false
                for (index in selected.indices) {
                    if (!favoriteLoad.isCurrent(request)) return@ensureBackgroundThread
                    val path = selected[index].path
                    if (protected(path)) continue
                    try {
                        val read = fetcher.readFolder(path, false, false, true, true,
                            true, favorites, false, HashMap(), dates, null)
                        if (read.status != FolderReadStatus.COMPLETE || !read.accessCurrent())
                            throw read.error ?: java.io.IOException("Incomplete favorite metadata")
                        val media = read.media
                        if (!favoriteLoad.isCurrent(request)) return@ensureBackgroundThread
                        val fresh = if (media.isNotEmpty()) {
                            createDirectoryFromMedia(path, media, covers, getString(R.string.hidden),
                                config.includedFolders, true, noMedia)
                        } else if (path in accessiblePaths) Directory().apply {
                            this.path = path
                            name = File(path).name.ifBlank { path }
                            location = LOCATION_INTERNAL
                        } else selected[index].snapshot().apply {
                            name += getString(R.string.folder_unavailable_suffix)
                        }
                        // A lock applied while the worker was reading must never
                        // leak a late cover/count, even before the next reload.
                        if (!protected(path)) selected[index] = fresh
                        changedCards++
                    } catch (error: Exception) {
                        metadataFailed = true
                        // An unreadable source is not an empty or deleted favorite.
                        android.util.Log.w("GalleryFavorites", "Favorite metadata unavailable", error)
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (changedCards >= 8 && now - lastPublished >= 250L) {
                        publish(complete = false)
                        changedCards = 0
                        lastPublished = now
                    }
                }
                publish(complete = true, failed = metadataFailed)
                GalleryPerfTrace.event(if (metadataFailed) "favorite_metadata_partial" else "favorite_metadata_finished", selected.size)
            } catch (error: Exception) {
                android.util.Log.e("GalleryFavorites", "Could not load favorite directories", error)
                runOnUiThread {
                    if (!favoriteViewForeground || isFinishing || isDestroyed ||
                        !favoriteLoad.fail(request)) return@runOnUiThread
                    mIsGettingDirs = false
                    setDirectoryLoading(false)
                    binding.directoriesRefreshLayout.isRefreshing = false
                    android.widget.Toast.makeText(this,
                        getString(R.string.favorite_folders_load_failed), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchSearchActivity() {
        hideKeyboard()
        Intent(this, SearchActivity::class.java).apply {
            startActivity(this)
        }

        // Close only after Commons finishes its opening callback, without a timer.
        binding.mainMenu.post {
            if (!isFinishing && !isDestroyed && binding.mainMenu.isSearchOpen) {
                OxygenSearchNavigation.closeInlineSearch(this, binding.mainMenu)
            }
        }
    }

    /** The catalog contains physical children too; adapter rows may be searched/grouped. */
    private fun refreshDirectoryPresentation() {
        if (mShowingFolderFavorites) {
            getDirectories(forceFavoriteReload = true)
            return
        }
        // Keep an active scan alive so sorting a partial cache cannot lose unseen albums.
        setupAdapter(mDirs)
    }

    private fun resumeCategorizedLoadIfNeeded() {
        if (categorizedReloadPending && !mShowingFolderFavorites && isSpecialFolderFilterActive() && !mIsGettingDirs) {
            loadCategorizedFolders()
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            directorySortStartedNanos = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            directorySortRequestedMode = config.directorySorting
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "album_sort_requested", revision = directorySortRequestedMode.toLong()
            )
            directoryRandomOrder.clear()
            if (config.directorySorting and SORT_BY_DATE_MODIFIED != 0 || config.directorySorting and SORT_BY_DATE_TAKEN != 0 ||
                config.directorySorting and SORT_BY_SIZE != 0) {
                retireDirectoryScan()
                getDirectories()
            } else {
                refreshDirectoryPresentation()
            }

            getRecyclerAdapter()?.directorySorting = config.directorySorting
        }
    }

    private fun manageUnavailableFolderFavorites() {
        ensureBackgroundThread {
            val unavailable = favoriteFoldersDB.getPaths().filter { path ->
                !getDoesFilePathExist(path, config.OTGPath)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (unavailable.isEmpty()) {
                    toast(R.string.favorite_folders_none_unavailable)
                    return@runOnUiThread
                }
                OxygenClassicDialog.show(this,
                    OxygenClassicDialog.builder(this, getString(R.string.favorite_folders_cleanup_title))
                    .setMessage(
                        getString(R.string.favorite_folders_cleanup_message, unavailable.size,
                            unavailable.take(8).joinToString("\n") +
                                if (unavailable.size > 8) "\n…" else "")
                    )
                    .setNegativeButton(R.string.favorite_folders_keep, null)
                    .setPositiveButton(R.string.favorite_folders_cleanup_action) { _, _ ->
                        ensureBackgroundThread {
                            unavailable.forEach { favoriteFoldersDB.delete(it) }
                            runOnUiThread { if (!isFinishing && !isDestroyed) getDirectories() }
                        }
                    })
            }
        }
    }

    private fun showFolderVisibilityDialog() {
        val labels = arrayOf(
            getString(R.string.folder_filter_hidden),
            getString(R.string.folder_filter_excluded),
            getString(R.string.folder_filter_nomedia),
            getString(R.string.folder_filter_protected)
        )
        val checked = mFolderVisibility.copyOf()
        OxygenClassicDialog.show(this,
            OxygenClassicDialog.builder(this, getString(R.string.folder_filter_title))
            .setMultiChoiceItems(labels, checked) { _, index, value -> checked[index] = value }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(R.string.folder_filter_apply) { _, _ ->
                val apply = {
                    mFolderVisibility = checked
                    retireDirectoryScan()
                    binding.directoriesGrid.adapter = null
                    getDirectories()
                }
                val verifyExcluded = {
                    if (checked[1] && !mFolderVisibility[1]) {
                        handleExcludedFolderPasswordProtection { apply() }
                    } else {
                        apply()
                    }
                }
                if ((checked[0] || checked[2]) && !(mFolderVisibility[0] || mFolderVisibility[2])) {
                    handleHiddenFolderPasswordProtection { verifyExcluded() }
                } else {
                    verifyExcluded()
                }
            })
    }

    private fun loadCategorizedFolders() {
        if (mIsGettingDirs) return
        // Category results must supersede an older normal album scan, not just
        // stop its fetcher while leaving its cache/UI callbacks eligible to publish.
        retireDirectoryScan()
        mIsGettingDirs = true
        setDirectoryLoading(true)
        val requestVersion = ++categorizedLoadVersion
        directoryTraceRequest = requestVersion
        org.fossify.gallery.helpers.FolderRefreshTrace.event(requestVersion, "album_queued")
        categorizedLoadActive = true
        categorizedReloadPending = false
        val requestedVisibility = mFolderVisibility.copyOf()
        val diagnostics = if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.MediaPipelineDiagnostics.create(
                requestedVisibility.contentToString(), "CATEGORIZED", "INITIAL", requestVersion
            ) else null
        activeCategoryScanDiagnostics = diagnostics
        var queuedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        var workerStartedElapsedMs = queuedAtElapsedMs
        var workerFinishedElapsedMs = queuedAtElapsedMs
        mLastMediaFetcher?.shouldStop = true
        val retained = mDirsIgnoringSearch.mapTo(ArrayList()) { it.snapshot() }
        val failed: (Exception) -> Unit = { error ->
            android.util.Log.e("GalleryFolders", "Folder category scan failed", error)
            val finishedAt = android.os.SystemClock.elapsedRealtime()
            runOnUiThread {
                val stillCurrent = !isFinishing && !isDestroyed && requestVersion == categorizedLoadVersion &&
                    requestedVisibility.contentEquals(mFolderVisibility)
                diagnostics?.finishIfOpen(if (stillCurrent) "FAILED" else "CANCELLED",
                    (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
                    (workerFinishedElapsedMs - workerStartedElapsedMs).coerceAtLeast(0L),
                    (finishedAt - workerFinishedElapsedMs).coerceAtLeast(0L), -1L, -1L)
                if (activeCategoryScanDiagnostics === diagnostics) activeCategoryScanDiagnostics = null
                if (stillCurrent) {
                    categorizedLoadActive = false
                    mIsGettingDirs = false
                    setDirectoryLoading(false)
                    binding.directoriesRefreshLayout.isRefreshing = false
                    toast(R.string.folder_load_failed)
                }
            }
        }
        fun current() = !isFinishing && !isDestroyed && requestVersion == categorizedLoadVersion &&
            requestedVisibility.contentEquals(mFolderVisibility)
        getCachedDirectories(forceShowHidden = true, forceShowExcluded = true, onFailure = failed,
            executor = org.fossify.gallery.helpers.GalleryAlbumScanExecutor, isCurrent = { current() },
            readCache = {
                try {
                    directoryCacheReader()
                } catch (error: Exception) {
                    if (error is java.util.concurrent.CancellationException ||
                        error is android.os.OperationCanceledException) throw error
                    android.util.Log.w("GalleryFolders", "Category cache unavailable; continuing scan")
                    retained
                }
            }) { cached ->
            try {
                queuedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                val scanned = org.fossify.gallery.helpers.CategorizedFolderScanner.scan(
                    applicationContext, cached, requestedVisibility, cancelled = { !current() }, onFetcher = { fetcher ->
                        fetcher.traceRequest = requestVersion
                        fetcher.pipelineDiagnostics = diagnostics
                        workerStartedElapsedMs = android.os.SystemClock.elapsedRealtime()
                        org.fossify.gallery.helpers.FolderRefreshTrace.event(requestVersion, "album_worker_started")
                        runOnUiThread { if (current()) mLastMediaFetcher = fetcher else fetcher.shouldStop = true }
                    }, onMetadata = { snapshot ->
                        org.fossify.gallery.helpers.DirectoryMetadataRepository.commit(applicationContext, snapshot)
                    }, onProgress = { partial ->
                        diagnostics?.apply { add("stageCount"); add("stagePublishCount") }
                        runOnUiThread {
                            if (current()) {
                                mDirs = partial.directories
                                mDirsIgnoringSearch = ArrayList(partial.directories)
                                setupAdapter(partial.directories, presentationIsCurrent = { current() })
                                checkPlaceholderVisibility(partial.directories)
                            }
                        }
                    })
                workerFinishedElapsedMs = android.os.SystemClock.elapsedRealtime()
                val visible = scanned.directories
                runOnUiThread {
                    if (!current()) {
                        diagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
                        return@runOnUiThread
                    }
                    categorizedLoadActive = false
                    mDirs = visible
                    mDirsIgnoringSearch = ArrayList(visible)
                    mLoadedInitialPhotos = true
                    mIsGettingDirs = false
                    setDirectoryLoading(false)
                    setupAdapter(visible, presentationIsCurrent = { current() }, onCommitted = {
                        val finishedAt = android.os.SystemClock.elapsedRealtime()
                        diagnostics?.finishIfOpen(if (scanned.failures.isEmpty()) "COMPLETE" else "PARTIAL",
                            (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
                            (workerFinishedElapsedMs - workerStartedElapsedMs).coerceAtLeast(0L),
                            (finishedAt - workerFinishedElapsedMs).coerceAtLeast(0L),
                            visible.size.toLong(), visible.size.toLong())
                        if (activeCategoryScanDiagnostics === diagnostics) activeCategoryScanDiagnostics = null
                    }, onDiscarded = {
                        diagnostics?.finishIfOpen("CANCELLED", -1L, -1L, -1L, -1L, -1L)
                        if (activeCategoryScanDiagnostics === diagnostics) activeCategoryScanDiagnostics = null
                    })
                    checkPlaceholderVisibility(visible)
                    binding.directoriesRefreshLayout.isRefreshing = false
                    if (scanned.failures.isNotEmpty()) toast(R.string.media_load_partial_error)
                }
            } catch (error: Exception) {
                if (current()) failed(error)
                else {
                    val finishedAt = android.os.SystemClock.elapsedRealtime()
                    diagnostics?.finishIfOpen("CANCELLED", (workerStartedElapsedMs - queuedAtElapsedMs).coerceAtLeast(0L),
                        (finishedAt - workerStartedElapsedMs).coerceAtLeast(0L), 0L, -1L, -1L)
                }
            }
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            retireDirectoryScan()
            binding.directoriesRefreshLayout.isRefreshing = true
            binding.directoriesGrid.adapter = null
            getDirectories()
        }
    }

    /** Called only by root navigation; leave third-party picker contracts untouched. */
    private fun openGalleryRoot(screen: GalleryScreen) {
        if (screen.section == GallerySection.ALL) {
            config.lastMainViewAll = screen.view == GalleryContentView.FILES
        }
        // The pinned favorite-files album must not change the saved favorite-folder tab.
        // Retain the legacy flag for older callers, but the destination owns scope.
        config.showAll = screen.isAllFiles
        val destination = if (screen.section == GallerySection.PHOTOS) {
            Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
        } else if (screen.view == GalleryContentView.FILES) {
            Intent(this, MediaActivity::class.java).putExtra(
                DIRECTORY, if (screen.isFavoriteFiles) FAVORITES else ""
            )
        } else {
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_FOLDERS, true)
                .putExtra(EXTRA_FOLDER_FAVORITES, screen.section == GallerySection.FAVORITES)
        }
        destination.putExtra(GalleryNavigation.EXTRA_SECTION, screen.section.name)
        if (binding.mainMenu.isSearchOpen) {
            OxygenSearchNavigation.closeInlineSearch(this, binding.mainMenu)
        }
        hideKeyboard()
        GalleryBottomBar.open(this, destination, finishCurrent = true)
    }

    private fun showAllMedia() {
        if (!mIsThirdPartyIntent) {
            openGalleryRoot(GalleryScreen(mGallerySection, GalleryContentView.FILES))
            return
        }
        // Legacy chooser/wallpaper flow: no bottom navigation or remembered tab.
        config.showAll = true
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, "")
            handleMediaIntent(this)
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            refreshMenuItems()
            setupLayoutManager()
            binding.directoriesGrid.adapter = null
            setupAdapter(getRecyclerAdapter()?.dirs ?: mDirs)
        }
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
        if (mShowingFolderFavorites) {
            if (show) mFavoriteHiddenAuthorized = true
            else if (!config.favoriteShowHidden) mFavoriteHiddenAuthorized = false
            clearFavoriteDirectoryViews()
            getDirectories(forceFavoriteReload = true)
        } else {
            retireDirectoryScan()
            binding.directoriesGrid.adapter = null
            getDirectories()
        }
        refreshMenuItems()
    }

    private fun tryToggleTemporarilyShowExcluded() {
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection {
                toggleTemporarilyShowExcluded(true)
            }
        }
    }

    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowExcluded = show
        if (mShowingFolderFavorites) {
            if (show) mFavoriteExcludedAuthorized = true
            else if (!config.favoriteShowExcluded) mFavoriteExcludedAuthorized = false
            clearFavoriteDirectoryViews()
            getDirectories(forceFavoriteReload = true)
        } else {
            retireDirectoryScan()
            binding.directoriesGrid.adapter = null
            getDirectories()
        }
        refreshMenuItems()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val fileDirItems = folders
            .asSequence()
            .filter { it.isDirectory }
            .map { FileDirItem(it.absolutePath, it.name, true) }
            .toMutableList() as ArrayList<FileDirItem>

        when {
            fileDirItems.isEmpty() -> return
            fileDirItems.size == 1 -> {
                try {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            fileDirItems.first().name
                        )
                    )
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> {
                val baseString = if (config.useRecycleBin && !config.tempSkipRecycleBin) {
                    org.fossify.commons.R.plurals.moving_items_into_bin
                } else {
                    org.fossify.commons.R.plurals.delete_items
                }

                toast(
                    msg = resources.getQuantityString(
                        baseString, fileDirItems.size, fileDirItems.size
                    )
                )
            }
        }

        val itemsToDelete = ArrayList<FileDirItem>()
        val filter = config.filterMedia
        val showHidden = config.shouldShowHidden
        fileDirItems.filter { it.isDirectory }.forEach {
            val files = File(it.path).listFiles()
            files?.filter {
                it.absolutePath.isMediaFile()
                        && (showHidden || !it.name.startsWith('.'))
                        && ((it.isImageFast() && filter and TYPE_IMAGES != 0)
                        || (it.isVideoFast() && filter and TYPE_VIDEOS != 0)
                        || (it.isGif() && filter and TYPE_GIFS != 0)
                        || (it.isRawFast() && filter and TYPE_RAWS != 0)
                        || (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(itemsToDelete) { it.toFileDirItem(applicationContext) }
        }

        if (config.useRecycleBin && !config.tempSkipRecycleBin) {
            val pathsToDelete = ArrayList<String>()
            itemsToDelete.mapTo(pathsToDelete) { it.path }

            movePathsInRecycleBin(pathsToDelete) {
                if (it) {
                    deleteFilteredFileDirItems(itemsToDelete, folders)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            deleteFilteredFileDirItems(itemsToDelete, folders)
        }
    }

    private fun deleteFilteredFileDirItems(
        fileDirItems: ArrayList<FileDirItem>,
        folders: ArrayList<File>
    ) {
        val OTGPath = config.OTGPath
        deleteFiles(fileDirItems) {
            runOnUiThread {
                refreshItems()
            }

            ensureBackgroundThread {
                folders.filter { !getDoesFilePathExist(it.absolutePath, OTGPath) }.forEach {
                    directoryDB.deleteDirPath(it.absolutePath)
                }

                if (config.deleteEmptyFolders) {
                    folders.filter {
                        !it.absolutePath.isDownloadsFolder()
                                && it.isDirectory
                                && it.toFileDirItem(this).getProperFileCount(this, true) == 0
                    }
                        .forEach {
                            tryDeleteFileDirItem(it.toFileDirItem(this), true, true)
                        }
                }
            }
        }
    }

    private fun setupLayoutManager() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }

        (binding.directoriesRefreshLayout.layoutParams as RelativeLayout.LayoutParams)
            .addRule(RelativeLayout.BELOW, R.id.directories_switch_searching)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.directoriesRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun initZoomListener() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) {
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                retireDirectoryScan()
                getDirectories()
            }
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

        val currentColumnCount =
            (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.dirColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount =
            config.dirColumnCnt
        refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, dirs.size)
        }
    }

    private fun isPickImageIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))
    }

    private fun isPickVideoIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))
    }

    private fun isPickIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_PICK
    }

    private fun isGetContentIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_GET_CONTENT && intent.type != null
    }

    private fun anyExtraMimeTypeStartingWith(intent: Intent, mimeTypePrefix: String): Boolean {
        return intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
            ?.any { it.startsWith(mimeTypePrefix) } == true
    }

    private fun isGetImageContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("image/")
                || intent.type == Images.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "image/"))
    }

    private fun isGetVideoContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("video/")
                || intent.type == Video.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "video/"))
    }

    private fun isGetAnyContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent) && intent.type == "*/*"
    }

    private fun isSetWallpaperIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_SET_WALLPAPER
    }

    private fun hasImageContentData(intent: Intent): Boolean {
        return intent.data == Images.Media.EXTERNAL_CONTENT_URI
                || intent.data == Images.Media.INTERNAL_CONTENT_URI
    }

    private fun hasVideoContentData(intent: Intent): Boolean {
        return intent.data == Video.Media.EXTERNAL_CONTENT_URI
                || intent.data == Video.Media.INTERNAL_CONTENT_URI
    }

    private fun isImageType(intent: Intent): Boolean {
        return (intent.type?.startsWith("image/") == true
                || intent.type == Images.Media.CONTENT_TYPE)
    }

    private fun isVideoType(intent: Intent): Boolean {
        return (intent.type?.startsWith("video/") == true
                || intent.type == Video.Media.CONTENT_TYPE)
    }

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data!!.path!!)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            inputStream.copyTo(outputStream!!)
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras!!.getStringArrayList(PICKED_PATHS)
        val uris = paths!!
            .map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData(
            "Attachment",
            arrayOf("image/*", "video/*"),
            ClipData.Item(uris.removeAt(0))
        )

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data!!.path
        val uri = getFilePublicUri(File(path!!), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Volatile
    private var folderOpenGeneration = 0L
    private var folderOpenTask: Future<*>? = null

    private fun itemClicked(path: String) {
        val request = ++folderOpenGeneration
        val app = applicationContext
        ViewerAccessCoordinator.cancel(folderOpenTask)
        folderOpenTask = try { ViewerAccessCoordinator.submit {
            if (request != folderOpenGeneration || Thread.currentThread().isInterrupted) return@submit
            val protectedFolder = if (path == FAVORITES) null else FolderUnlockSessionManager.findProtectedAncestor(
                path, app.config::isFolderProtected
            )
            runOnUiThread {
                if (request != folderOpenGeneration || isFinishing || isDestroyed) return@runOnUiThread
                val launch = {
                    val openActivity = { sessionId: String? ->
                        if (request != folderOpenGeneration || isFinishing || isDestroyed) {
                            FolderUnlockSessionManager.revokeSession(sessionId)
                        } else {
                            Intent(this, MediaActivity::class.java).apply {
                                sessionId?.let { putExtra(FOLDER_UNLOCK_SESSION, it) }
                                putExtra(DIRECTORY, path)
                                retireDirectoryScan()
                                handleMediaIntent(this)
                            }
                        }
                    }
                    if (path == FAVORITES) {
                        // MediaActivity prepares/migrates the virtual album's
                        // dedicated protection before publishing any contents.
                        openActivity(null)
                    } else handleLockedFolderOpening(protectedFolder ?: path) { success ->
                        if (!success) return@handleLockedFolderOpening
                        if (protectedFolder == null) {
                            openActivity(null)
                        } else {
                            ViewerAccessCoordinator.cancel(folderOpenTask)
                            folderOpenTask = try { ViewerAccessCoordinator.submit {
                                val token = FolderUnlockSessionManager.createSession(path)
                                runOnUiThread {
                                    if (request != folderOpenGeneration || isFinishing || isDestroyed) {
                                        FolderUnlockSessionManager.revokeSession(token)
                                    } else openActivity(token)
                                }
                            } } catch (_: java.util.concurrent.RejectedExecutionException) {
                                finish()
                                return@handleLockedFolderOpening
                            }
                        }
                    }
                }
                // Displaying excluded metadata never bypasses the existing
                // excluded folders password gate when the user opens a folder.
                if (FavoriteFolderRules.isExcluded(path, config.excludedFolders)) {
                    handleExcludedFolderPasswordProtection { launch() }
                } else {
                    launch()
                }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            finish()
            return
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        hideKeyboard()
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    private fun gotDirectories(
        newDirs: ArrayList<Directory>, scanRequest: Long, discoverUnindexed: Boolean = false,
        diagnostics: org.fossify.gallery.helpers.MediaPipelineDiagnostics? = null
    ): GalleryScanResult {
        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        if (mShowingFolderFavorites) {
            // All favorite results must pass the same authentication and generation
            // checks, including callbacks from sorting/pinning operations.
            runOnUiThread {
                if (favoriteViewForeground && !isFinishing && !isDestroyed) {
                    getDirectories(forceFavoriteReload = true)
                }
            }
            return GalleryScanResult(GalleryScanState.CANCELLED)
        }
        // Keep worker state and UI mutations separate. Initial publication below
        // releases the cache-loading lock on the main thread only if still current.

        // The directory cache predates the dedicated favorites credential. Never
        // publish its old cover or count while the virtual album is protected.
        if (config.isFavoritePasswordProtectionOn || config.isFolderProtected(FAVORITES)) {
            newDirs.indexOfFirst { it.path == FAVORITES }.takeIf { it >= 0 }?.let { index ->
                newDirs[index] = newDirs[index].copy(tmb = "", mediaCnt = 0, subfoldersMediaCount = 0)
            }
        }

        // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
        if (!config.shouldShowHidden) {
            val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
            if (
                favoritesFolder != null
                && favoritesFolder.tmb.getFilenameFromPath().startsWith('.')
            ) {
                newDirs.remove(favoritesFolder)
            }
        }

        // Publish every special directory before the first sort so the list
        // has one stable order throughout the subsequent media scan.
        val dirs = ArrayList(newDirs.filterNot { it.path == FAVORITES })
        if (favoritesDB.getFavoritesCount() > 0L) {
            dirs.add(newDirs.firstOrNull { it.path == FAVORITES }?.copy() ?: Directory().apply {
                path = FAVORITES
                name = getString(org.fossify.commons.R.string.favorites)
                location = LOCATION_INTERNAL
            })
        }

        if (
            config.showRecycleBinAtFolders
            && !config.showRecycleBinLast
            && dirs.none { it.isRecycleBin() }
        ) {
            try {
                if (mediaDB.getDeletedMediaCount() > 0) {
                    dirs.add(Directory().apply {
                        path = RECYCLE_BIN
                        name = getString(org.fossify.commons.R.string.recycle_bin)
                        location = LOCATION_INTERNAL
                    })
                }
            } catch (ignored: Exception) {
            }
        }

        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        // Each publication sorts against the current UI setting below; a worker
        // must not freeze the order selected before a long scan began.

        var isPlaceholderVisible = dirs.isEmpty()

        // Snapshot on the scanning thread before it starts replacing entries.
        setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)

        // The worker always retains its own fetcher; a newer scan cannot hijack it.
        mLastMediaFetcher?.shouldStop = true
        val scanFetcher = MediaFetcher(applicationContext, cancelled = {
            !isCurrentDirectoryScan(scanRequest)
        }, reportReadFailures = true, discoverUnindexedFolders = discoverUnindexed)
        scanFetcher.traceRequest = scanRequest
        scanFetcher.pipelineDiagnostics = diagnostics
        mLastMediaFetcher = scanFetcher
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        val getImagesOnly = getImages && !getVideos
        val getVideosOnly = getVideos && !getImages
        val favoritePaths = getFavoritePaths()
        val hiddenString = getString(R.string.hidden)
        val albumCovers = config.parseAlbumCovers()
        val includedFolders = config.includedFolders
        val noMediaFolders = getNoMediaFoldersSync()
        val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
        // In scoped storage the grouped index already contains normal timestamps
        // and sizes. Do not query the entire MediaStore twice more for those columns.
        // The file-system/legacy path retains the original metadata queries.
        val indexedMetadata = isRPlus() && !isExternalStorageManager()
        val lastModifieds = if (indexedMetadata) HashMap<String, Long>()
            else scanFetcher.getLastModifieds()
        val dateTakens = if (indexedMetadata) scanFetcher.getCustomDateTakenOverrides()
            else scanFetcher.getDateTakens()

        // fetch files from MediaStore only, unless the app has the MANAGE_EXTERNAL_STORAGE permission on Android 11+
        val indexedStarted = GalleryPerfTrace.begin()
        val android11Files = scanFetcher.getAndroid11FolderMedia(
            isPickImage = getImagesOnly,
            isPickVideo = getVideosOnly,
            favoritePaths = favoritePaths,
            getFavoritePathsOnly = false,
            getProperDateTaken = true,
            dateTakens = dateTakens
        )
        GalleryPerfTrace.end("album_index_ready", indexedStarted, android11Files?.size)
        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        val folderFailures = LinkedHashMap<String, Exception>()
        var successfulSources = 0
        val metadataScope = DirectoryScanScope(config.filterMedia, config.shouldShowHidden,
            getImagesOnly, getVideosOnly)
        fun beginMetadata(path: String): Long {
            if (!metadataScope.matches(applicationContext) || !path.startsWith('/') ||
                FolderUnlockSessionManager.findProtectedAncestor(path, config::isFolderProtected) != null) return 0L
            return DirectoryMetadataRepository.begin(path)
        }
        fun commitMetadata(path: String, version: Long, read: FolderMediaReadResult?): Boolean {
            if (version == 0L || read == null) return false
            if (read.status != FolderReadStatus.COMPLETE || !read.accessCurrent()) {
                DirectoryMetadataRepository.defer(path, version)
                return false
            }
            return DirectoryMetadataRepository.commit(applicationContext,
                DirectoryScanSnapshot(path, version, metadataScope, read, read.media.map { it.copy() },
                    isCurrent = { isCurrentDirectoryScan(scanRequest) && scanFetcher === mLastMediaFetcher },
                    notifyObservers = false))
        }
        var cachedCardUpdates = 0
        for (index in dirs.indices) {
            val directory = dirs[index]
            if (!isCurrentDirectoryScan(scanRequest)) {
                return GalleryScanResult(GalleryScanState.CANCELLED)
            }
            // A locked album is represented by its cached metadata only.
            // Its media may be scanned only by MediaActivity after authentication.
            if (config.isFolderProtected(directory.path) ||
                (directory.path.startsWith('/') &&
                    FolderUnlockSessionManager.findProtectedAncestor(
                        directory.path, config::isFolderProtected) != null)) continue
            if (directory.path == FAVORITES && config.isFavoritePasswordProtectionOn) {
                directory.tmb = ""
                directory.mediaCnt = 0
                directory.subfoldersMediaCount = 0
                updateDBDirectory(directory)
                setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)
                continue
            }

            val pathsForCard = if (directory.path == FAVORITES) {
                runCatching {
                    FavoriteAccessPolicy.unprotectedPaths(
                        FavoriteAccessSessionManager.captureSnapshot(
                            favoritePaths, applicationContext::favoriteProtection
                        )
                    ).toCollection(ArrayList())
                }.getOrDefault(arrayListOf())
            } else favoritePaths

            val sorting = config.getFolderSorting(directory.path)
            val grouping = config.getFolderGrouping(directory.path)
            val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                    || sorting and SORT_BY_DATE_TAKEN != 0
                    || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                    || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

            val getProperLastModified =
                config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                        || sorting and SORT_BY_DATE_MODIFIED != 0
                        || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                        || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

            val isPhysicalDirectory = directory.path.startsWith('/') &&
                !directory.areFavorites() && !directory.isRecycleBin()
            val metadataVersion = if (isPhysicalDirectory) beginMetadata(directory.path) else 0L
            var physicalRead: FolderMediaReadResult? = null
            val curMedia = try {
                if (isPhysicalDirectory) {
                    scanFetcher.readFolder(directory.path, getImagesOnly, getVideosOnly,
                        getProperDateTaken, getProperLastModified, getProperFileSize,
                        pathsForCard, false, lastModifieds, dateTakens, android11Files)
                        .also { physicalRead = it }.media
                } else scanFetcher.getFilesFrom(
                    curPath = directory.path,
                    isPickImage = getImagesOnly,
                    isPickVideo = getVideosOnly,
                    getProperDateTaken = getProperDateTaken,
                    getProperLastModified = getProperLastModified,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = pathsForCard,
                    getVideoDurations = false,
                    lastModifieds = lastModifieds,
                    dateTakens = dateTakens,
                    android11Files = android11Files
                )
            } catch (error: Exception) {
                DirectoryMetadataRepository.defer(directory.path, metadataVersion)
                folderFailures[directory.path] = error
                android.util.Log.w("GalleryDirs", "Could not refresh album", error)
                continue
            }

            if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
            // Picker scopes can still display their own results, but never write public counts.
            // Only fully read physical directories can replace authoritative cache rows.
            if (isPhysicalDirectory && (physicalRead?.status != FolderReadStatus.COMPLETE || !physicalRead.accessCurrent())) {
                DirectoryMetadataRepository.defer(directory.path, metadataVersion)
                folderFailures[directory.path] = physicalRead?.error ?: java.io.IOException("Incomplete directory scan")
                if (curMedia.isNotEmpty() && physicalRead?.accessCurrent?.invoke() == true) successfulSources++
                continue
            }
            if (isPhysicalDirectory && metadataVersion != 0L && !commitMetadata(directory.path, metadataVersion, physicalRead))
                folderFailures[directory.path] = java.io.IOException("Directory metadata commit deferred")
            if (isPhysicalDirectory || curMedia.isNotEmpty()) successfulSources++
            if (directory.path == FAVORITES) {
                // Match the album's current saved and unprotected paths, even
                // if the favorites table or source locks changed during the scan.
                val latestPublicPaths = runCatching {
                    val saved = getFavoritePaths()
                    FavoriteAccessPolicy.unprotectedPaths(
                        FavoriteAccessSessionManager.captureSnapshot(
                            saved, applicationContext::favoriteProtection
                        )
                    )
                }.getOrDefault(emptySet())
                curMedia.retainAll { it.path in latestPublicPaths }
                if (config.isFavoritePasswordProtectionOn || config.isFolderProtected(FAVORITES)) {
                    directory.tmb = ""
                    directory.mediaCnt = 0
                    directory.subfoldersMediaCount = 0
                    updateDBDirectory(directory)
                    setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)
                    continue
                }
            }

            val newDir = if (curMedia.isEmpty() && isPhysicalDirectory && metadataVersion != 0L &&
                physicalRead?.status == FolderReadStatus.COMPLETE) {
                // This is a verified full scan, not an unavailable folder or an empty preview.
                createDirectoryFromMedia(directory.path, arrayListOf(), albumCovers,
                    hiddenString, includedFolders, getProperFileSize, noMediaFolders)
            } else if (curMedia.isEmpty()) {
                // An empty scan can mean permission loss, a changed filter,
                // or a temporarily unavailable volume. Never delete a valid
                // directory record based only on an empty result.
                if (directory.areFavorites()) directory.copy(tmb = "", mediaCnt = 0)
                else directory
            } else {
                createDirectoryFromMedia(
                    path = directory.path,
                    curMedia = curMedia,
                    albumCovers = albumCovers,
                    hiddenString = hiddenString,
                    includedFolders = includedFolders,
                    getProperFileSize = getProperFileSize,
                    noMediaFolders = noMediaFolders
                )
            }

            // An unavailable physical folder is not evidence of changed content.
            if (curMedia.isEmpty() && !directory.areFavorites() && !directory.isRecycleBin() &&
                !(metadataVersion != 0L && physicalRead?.status == FolderReadStatus.COMPLETE)) continue

            // Adapter holds the previous Directory instance. Replace it, so
            // DiffUtil can compare stable immutable before/after snapshots.
            val revisedDir = directory.copy(
                tmb = newDir.tmb,
                name = newDir.name,
                mediaCnt = newDir.mediaCnt,
                modified = newDir.modified,
                taken = newDir.taken,
                size = newDir.size,
                types = newDir.types,
                sortValue = getDirectorySortingValue(
                    curMedia, directory.path, newDir.name, newDir.size, newDir.mediaCnt
                )
            ).also { it.folderStatuses = directory.folderStatuses }
            if (directory == revisedDir) continue
            dirs[index] = revisedDir
            cachedCardUpdates++
            if (cachedCardUpdates >= 8) {
                setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)
                cachedCardUpdates = 0
            }

            // Already on the cached-media worker thread: do not create a
            // short-lived Thread per album during a large library refresh.
            if (!isPhysicalDirectory) updateDBDirectory(revisedDir)
            if (!directory.isRecycleBin() && !directory.areFavorites()) {
                try {
                    mediaDB.insertAll(curMedia)
                } catch (ignored: Exception) {
                }
            }

            if (!directory.isRecycleBin() && !directory.areFavorites()) {
                val scanOwner = scanFetcher
                val freshPaths = curMedia.mapTo(HashSet()) { it.path }
                getCachedMedia(directory.path, getVideosOnly, getImagesOnly) { cached ->
                    if (directoryScanTerminals.result(scanRequest)?.complete != true ||
                        mShouldStopFetching || isDestroyed || isFinishing ||
                        scanOwner !== mLastMediaFetcher || !isCurrentDirectoryScan(scanRequest)
                    ) return@getCachedMedia

                    // Comparing entire Medium objects is unsafe: modified
                    // metadata makes a still-existing file look "deleted".
                    // Absence from MediaStore alone is also not proof.
                    val stale = cached.filterIsInstance<Medium>().filter { old ->
                        val file = File(old.path)
                        val parentReadable = file.parentFile?.let {
                            it.isDirectory && it.canRead()
                        } == true
                        MediaCachePrunePolicy.canPrune(
                            oldPath = old.path,
                            latestPaths = freshPaths,
                            isFavoritesView = false,
                            parentReadable = parentReadable,
                            fileExists = file.exists()
                        )
                    }
                    if (stale.isNotEmpty()) {
                        mediaDB.deleteMedia(*stale.toTypedArray())
                    }
                }
            }
        }

        if (cachedCardUpdates > 0 && isCurrentDirectoryScan(scanRequest)) {
            setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)
        }


        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        val periodicDiscovery = if (!discoverUnindexed && (config.appRunCount == 1 || config.appRunCount % 30 == 0))
            getFoldersWithMedia(config.internalStoragePath, scanRequest)
        else org.fossify.gallery.helpers.FolderDiscoveryResult(emptySet(), emptySet())
        val foldersToScan = (scanFetcher.getFoldersToScan() + periodicDiscovery.folders).distinct().toCollection(ArrayList())
        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        foldersToScan.remove(FAVORITES)
        foldersToScan.add(0, FAVORITES)
        if (config.showRecycleBinAtFolders) {
            if (foldersToScan.contains(RECYCLE_BIN)) {
                foldersToScan.remove(RECYCLE_BIN)
                foldersToScan.add(0, RECYCLE_BIN)
            } else {
                foldersToScan.add(0, RECYCLE_BIN)
            }
        } else {
            foldersToScan.remove(RECYCLE_BIN)
        }

        dirs.filterNot { it.path == RECYCLE_BIN || it.path == FAVORITES }.forEach {
            foldersToScan.remove(it.path)
        }

        // Batch newly discovered directories and media in Room transactions.
        // Avoid a thread and full UI list-sort for every discovered folder.
        val pendingDirs = ArrayList<Directory>()
        val pendingMedia = ArrayList<Medium>()
        var foldersSinceUpdate = 0
        fun commitBatch() {
            if ((pendingDirs.isEmpty() && pendingMedia.isEmpty()) || !isCurrentDirectoryScan(scanRequest)) return
            try {
                GalleryDatabase.getInstance(applicationContext).runInTransaction {
                    if (!isCurrentDirectoryScan(scanRequest)) throw java.util.concurrent.CancellationException()
                    if (pendingDirs.isNotEmpty()) directoryDB.insertAll(pendingDirs)
                    if (pendingMedia.isNotEmpty()) mediaDB.insertAll(pendingMedia)
                }
            } catch (error: Exception) {
                pendingDirs.forEach { folderFailures[it.path] = error }
                pendingMedia.forEach { folderFailures[it.parentPath] = error }
            }
            pendingDirs.clear()
            pendingMedia.clear()
        }

        // check the remaining folders which were not cached at all yet
        for (folder in foldersToScan) {
            if (!isCurrentDirectoryScan(scanRequest)) {
                return GalleryScanResult(GalleryScanState.CANCELLED)
            }
            if (config.isFolderProtected(folder) ||
                (folder.startsWith('/') &&
                    FolderUnlockSessionManager.findProtectedAncestor(folder, config::isFolderProtected) != null)) continue

            val sorting = config.getFolderSorting(folder)
            val grouping = config.getFolderGrouping(folder)
            val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                    || sorting and SORT_BY_DATE_TAKEN != 0
                    || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                    || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

            val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                    || sorting and SORT_BY_DATE_MODIFIED != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

            val isPhysicalDirectory = folder.startsWith('/') && folder != FAVORITES && folder != RECYCLE_BIN
            val metadataVersion = if (isPhysicalDirectory) beginMetadata(folder) else 0L
            var physicalRead: FolderMediaReadResult? = null
            val newMedia = try {
                if (isPhysicalDirectory) {
                    scanFetcher.readFolder(folder, getImagesOnly, getVideosOnly, getProperDateTaken,
                        getProperLastModified, getProperFileSize, favoritePaths,
                        false, lastModifieds, dateTakens, android11Files)
                        .also { physicalRead = it }.media
                } else scanFetcher.getFilesFrom(
                    curPath = folder,
                    isPickImage = getImagesOnly,
                    isPickVideo = getVideosOnly,
                    getProperDateTaken = getProperDateTaken,
                    getProperLastModified = getProperLastModified,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = favoritePaths,
                    getVideoDurations = false,
                    lastModifieds = lastModifieds,
                    dateTakens = dateTakens,
                    android11Files = android11Files
                )
            } catch (error: Exception) {
                DirectoryMetadataRepository.defer(folder, metadataVersion)
                folderFailures[folder] = error
                android.util.Log.w("GalleryDirs", "Could not load album", error)
                continue
            }

            if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
            if (isPhysicalDirectory && (physicalRead?.status != FolderReadStatus.COMPLETE || !physicalRead.accessCurrent())) {
                DirectoryMetadataRepository.defer(folder, metadataVersion)
                folderFailures[folder] = physicalRead?.error ?: java.io.IOException("Incomplete directory scan")
                if (newMedia.isNotEmpty() && physicalRead?.accessCurrent?.invoke() == true) successfulSources++
                continue
            }
            if (isPhysicalDirectory && metadataVersion != 0L && newMedia.isNotEmpty() &&
                !commitMetadata(folder, metadataVersion, physicalRead))
                folderFailures[folder] = java.io.IOException("Directory metadata commit deferred")
            if (isPhysicalDirectory || newMedia.isNotEmpty()) successfulSources++
            if (newMedia.isEmpty()) {
                continue
            }

            if (isPlaceholderVisible) {
                isPlaceholderVisible = false
                runOnUiThread {
                    if (!isCurrentDirectoryScan(scanRequest)) return@runOnUiThread
                    binding.directoriesEmptyPlaceholder.beGone()
                    binding.directoriesEmptyPlaceholder2.beGone()
                    binding.directoriesFastscroller.beVisible()
                }
            }

            val newDir = createDirectoryFromMedia(
                path = folder,
                curMedia = newMedia,
                albumCovers = albumCovers,
                hiddenString = hiddenString,
                includedFolders = includedFolders,
                getProperFileSize = getProperFileSize,
                noMediaFolders = noMediaFolders
            )
            dirs.add(newDir)
            // Physical rows were already saved by DirectoryMetadataRepository under
            // their scan version. A batch REPLACE here would reintroduce the race.
            if (!isPhysicalDirectory) pendingDirs.add(newDir)
            if (folder != RECYCLE_BIN && folder != FAVORITES) {
                pendingMedia.addAll(newMedia)
            }
            foldersSinceUpdate++
            if (foldersSinceUpdate >= 8 || pendingMedia.size >= 1200) {
                commitBatch()
                setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest)
                foldersSinceUpdate = 0
            }
        }

        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        commitBatch()
        // Prune before the only final reorder, under the same scan identity.
        folderFailures.putAll(scanFetcher.readFailures)
        (scanFetcher.folderDiscovery.unreadablePaths + periodicDiscovery.unreadablePaths).forEach {
            folderFailures[it] = java.io.IOException("Incomplete directory discovery")
        }
        val outcome = GalleryScanResult.sources(successfulSources, folderFailures,
            scanFetcher.folderDiscovery.complete && periodicDiscovery.complete)
        if (outcome.complete) checkInvalidDirectories(dirs, scanRequest, folderFailures.keys)
        if (!isCurrentDirectoryScan(scanRequest)) return GalleryScanResult(GalleryScanState.CANCELLED)
        if (outcome.state != GalleryScanState.FAILED)
            setupAdapter(dirs, sortDirs = true, scanRequest = scanRequest, scanComplete = outcome.complete)
        mLoadedInitialPhotos = true
        if (config.appRunCount > 1) {
            checkLastMediaChanged()
        }

        if (mDirs.size > 50) {
            excludeSpamFolders()
        }

        val excludedFolders = config.excludedFolders
        val everShownFolders = config.everShownFolders.toMutableSet() as HashSet<String>

        // do not add excluded folders and their subfolders at everShownFolders
        dirs.filter { dir ->
            return@filter !excludedFolders.any { dir.path.startsWith(it) }
        }.mapTo(everShownFolders) { it.path }

        try {
            // Reuse this request's bounded discovery; no second recursive walk after publication.
            everShownFolders.addAll(periodicDiscovery.folders)

            // catch some extreme exceptions like too many everShownFolders for storing, shouldnt really happen
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }

        return outcome

    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }

    private fun openDefaultFolder() {
        if (config.defaultFolder.isEmpty()) {
            return
        }

        val defaultDir = File(config.defaultFolder)

        if ((!defaultDir.exists() || !defaultDir.isDirectory) && (config.defaultFolder != RECYCLE_BIN && config.defaultFolder != FAVORITES)) {
            config.defaultFolder = ""
            return
        }

        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, config.defaultFolder)
            handleMediaIntent(this)
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) {
        binding.directoriesEmptyPlaceholder.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        binding.directoriesEmptyPlaceholder2.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)

        if (binding.mainMenu.isSearchOpen) {
            binding.directoriesEmptyPlaceholder.text =
                getString(org.fossify.commons.R.string.no_items_found)
            binding.directoriesEmptyPlaceholder2.beGone()
        } else if (dirs.isEmpty() && config.filterMedia == getDefaultFileFilter()) {
            if (isRPlus() && !isExternalStorageManager()) {
                binding.directoriesEmptyPlaceholder.text =
                    getString(org.fossify.commons.R.string.no_items_found)
                binding.directoriesEmptyPlaceholder2.beGone()
            } else {
                binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_add_included)
                binding.directoriesEmptyPlaceholder2.text = getString(R.string.add_folder)
            }

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showAddIncludedFolderDialog {
                    refreshItems()
                }
            }
        } else {
            binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_with_filters)
            binding.directoriesEmptyPlaceholder2.text =
                getString(R.string.change_filters_underlined)

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showFilterMediaDialog()
            }
        }

        binding.directoriesEmptyPlaceholder2.underlineText()
        binding.directoriesFastscroller.beVisibleIf(binding.directoriesEmptyPlaceholder.isGone())
    }

    private fun setupAdapter(
        dirs: ArrayList<Directory>,
        textToSearch: String? = null,
        forceRecreate: Boolean = false,
        sortDirs: Boolean = true,
        scanRequest: Long? = null,
        scanComplete: Boolean = false,
        presentationIsCurrent: () -> Boolean = { true },
        onCommitted: () -> Unit = {},
        onDiscarded: () -> Unit = {}
    ) {
        // Do not share mutable Directory instances with a scanning worker or DiffUtil.
        val snapshot = dirs.map { it.copy().also { copy -> copy.folderStatuses = it.folderStatuses } }
            .toCollection(ArrayList())
        runOnUiThread {
            if (isFinishing || isDestroyed || !presentationIsCurrent() ||
                (scanRequest != null && !isCurrentDirectoryScan(scanRequest))) {
                onDiscarded()
                return@runOnUiThread
            }
            val query = textToSearch ?: binding.mainMenu.getCurrentQuery()
            val sortingAtPublication = config.directorySorting
            val preparationStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            val measurePreparationDetails = org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()
            val dedupeStarted = if (measurePreparationDetails)
                org.fossify.gallery.helpers.GalleryPerfTrace.begin() else 0L
            val distinctDirs = snapshot.filter { !mShowingFolderFavorites || mFavoriteFolderPaths.contains(it.path) }
                .distinctBy {
                    if (mShowingFolderFavorites) FavoriteFolderRules.normalizedPath(it.path)
                    else it.path.getDistinctPath()
                }.toCollection(ArrayList())
            if (measurePreparationDetails) org.fossify.gallery.helpers.GalleryPerfTrace.end(
                "album_presentation_dedupe", dedupeStarted, snapshot.size
            )
            val sortStarted = if (measurePreparationDetails)
                org.fossify.gallery.helpers.GalleryPerfTrace.begin() else 0L
            val sorted = if (sortDirs) getSortedDirectories(distinctDirs, directoryRandomOrder) else distinctDirs
            if (measurePreparationDetails) org.fossify.gallery.helpers.GalleryPerfTrace.end(
                "album_presentation_sort", sortStarted, distinctDirs.size
            )
            if (textToSearch == null) {
                // Search always derives from the latest complete catalog, not the first cache.
                mDirs = ArrayList(sorted)
                mDirsIgnoringSearch = ArrayList(sorted)
            }
            val filterStarted = if (measurePreparationDetails)
                org.fossify.gallery.helpers.GalleryPerfTrace.begin() else 0L
            var visible = getDirsToShow(sorted, mDirs, mCurrentPathPrefix, directoryRandomOrder, sortDirs = sortDirs)
                .toCollection(ArrayList())
            if (query.isNotEmpty()) visible = visible.filter { it.name.contains(query, true) }
                .sortedBy { !it.name.startsWith(query, true) }.toCollection(ArrayList())
            if (measurePreparationDetails) org.fossify.gallery.helpers.GalleryPerfTrace.end(
                "album_presentation_filter", filterStarted, visible.size
            )

            if (scanRequest != null && org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "album_adapter_submit", scanRequest,
                    mapOf("itemCount" to visible.size, "sortDirs" to sortDirs, "scanComplete" to scanComplete)
                )

            org.fossify.gallery.helpers.GalleryPerfTrace.end("album_presentation_prepare", preparationStarted, snapshot.size)
            val committed = {
                if (presentationIsCurrent() && (scanRequest == null || isCurrentDirectoryScan(scanRequest))) {
                    // Only an in-progress scan can publish an unconfirmed empty cache.
                    if (scanRequest == null || visible.isNotEmpty() || scanComplete) {
                        checkPlaceholderVisibility(visible)
                        GalleryBottomBar.markRootContentReady(this@MainActivity, if (visible.isEmpty())
                            GalleryBottomBar.RootContent.EMPTY else GalleryBottomBar.RootContent.CONTENT)
                    }
                    if (scanRequest != null && org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
                        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaUI", "album_adapter_commit", scanRequest,
                            mapOf("itemCount" to visible.size, "scanComplete" to scanComplete)
                        )
                    if (directorySortStartedNanos != 0L &&
                        directorySortRequestedMode == sortingAtPublication &&
                        sortingAtPublication == config.directorySorting &&
                        (scanComplete || (scanRequest == null && !mIsGettingDirs))) {
                        org.fossify.gallery.helpers.GalleryPerfTrace.end(
                            "album_sort_to_commit", directorySortStartedNanos, visible.size
                        )
                        directorySortStartedNanos = 0L
                    }
                    onCommitted()
                } else onDiscarded()
            }
            val current = getRecyclerAdapter()
            if (current == null || forceRecreate) {
                initZoomListener()
                DirectoryAdapter(this, visible, this, binding.directoriesGrid,
                    isPickIntent(intent) || isGetAnyContentIntent(intent), binding.directoriesRefreshLayout) {
                    val clicked = it as Directory
                    if (clicked.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                        if (clicked.path != config.tempFolderPath) itemClicked(clicked.path)
                    } else {
                        mCurrentPathPrefix = clicked.path
                        mOpenedSubfolders.add(clicked.path)
                        setupAdapter(mDirs, "")
                    }
                }.apply {
                    setupZoomListener(mZoomListener)
                    binding.directoriesGrid.adapter = this
                    setupScrollDirection()
                    if (config.viewTypeFolders == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                        binding.directoriesGrid.scheduleLayoutAnimation()
                    }
                }
                committed()
            } else {
                current.directorySorting = config.directorySorting
                current.updateDirs(visible, isCurrent = {
                    !isFinishing && !isDestroyed && presentationIsCurrent() && binding.directoriesGrid.adapter === current &&
                        config.directorySorting == sortingAtPublication &&
                        (scanRequest == null || isCurrentDirectoryScan(scanRequest))
                }, onCommitted = committed, preserveOrder = !sortDirs, onDiscarded = onDiscarded)
            }
        }
    }

    private fun setupScrollDirection() {
        val scrollHorizontally =
            config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        binding.directoriesFastscroller.setScrollVertically(!scrollHorizontally)
        styleFastScroller()
    }

    private fun styleFastScroller() {
        OxygenFastScroller.apply(binding.directoriesFastscroller, binding.directoriesGrid,
            OxygenThemeEngine.snapshot(this, config),
            if (mIsThirdPartyIntent) 0 else resources.getDimensionPixelSize(R.dimen.oxygen_bottom_bar_height))
    }

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>, scanRequest: Long, failedPaths: Set<String> = emptySet()) {
        if (!isCurrentDirectoryScan(scanRequest)) return
        val invalidDirs = ArrayList<Directory>()
        val verifiedMissingPaths = HashSet<String>()
        val OTGPath = config.OTGPath
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach { directory ->
            val path = directory.path
            if (path in failedPaths) return@forEach
            val folder = File(path)
            val parentReadable = folder.parentFile?.let {
                it.isDirectory && it.canRead()
            } == true
            if (MediaCachePrunePolicy.canPruneDirectory(
                    path, parentReadable, folder.exists()
                )
            ) {
                invalidDirs.add(directory)
                verifiedMissingPaths.add(path)
            } else if (getDoesFilePathExist(path, OTGPath) &&
                path != config.tempFolderPath && (!isRPlus() || isExternalStorageManager())
            ) {
                // list() == null means unknown/unreadable, NOT an empty folder.
                val children = if (isPathOnOTG(path)) {
                    getOTGFolderChildrenNames(path)
                } else {
                    folder.list()?.asList()
                }
                if (children != null) {
                    val hasMediaFile = children.any { child ->
                        child != null && (
                            child.isMediaFile() ||
                                (child.startsWith("img_", true) && File(child).isDirectory)
                            )
                    }
                    if (!hasMediaFile) {
                        // Hide empty folders from the current grid without
                        // deleting their valid saved database metadata.
                        invalidDirs.add(directory)
                    }
                }
            }
        }

        if (getFavoritePaths().isEmpty()) {
            dirs.firstOrNull { it.areFavorites() }?.let(invalidDirs::add)
        }

        if (config.useRecycleBin) {
            try {
                val recycleBin = dirs.firstOrNull { it.path == RECYCLE_BIN }
                if (recycleBin != null && mediaDB.getDeletedMedia().isEmpty()) {
                    invalidDirs.add(recycleBin)
                }
            } catch (error: Exception) {
                android.util.Log.w("GalleryDirs", "Could not inspect recycle bin", error)
            }
        }

        if (invalidDirs.isNotEmpty()) {
            if (!isCurrentDirectoryScan(scanRequest)) return
            dirs.removeAll(invalidDirs.toSet())
            // A virtual folder, an empty folder, or an unmounted SD card must
            // never lose its DB record because a scan returned no media.
            verifiedMissingPaths.forEach { path ->
                if (!isCurrentDirectoryScan(scanRequest)) return
                try {
                    directoryDB.deleteDirPath(path)
                } catch (error: Exception) {
                    android.util.Log.w("GalleryDirs", "Could not prune missing folder", error)
                }
            }
        }
    }

    private fun getCurrentlyDisplayedDirs() = getRecyclerAdapter()?.dirs ?: ArrayList()

    private fun setupLatestMediaId() {
        ensureBackgroundThread {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                mLatestMediaId = getLatestMediaId()
                mLatestMediaDateId = getLatestMediaByDateId()
            }
        }
    }

    private var mediaPollScheduled = false
    private var mediaPollInFlight = false
    private var mediaPollGeneration = 0L
    private val directoryMediaChangeGate = org.fossify.gallery.helpers.MediaChangeRetryGate()

    private fun onNormalDirectoryScanFinished(result: GalleryScanResult) {
        scheduleDirectoryMetadataRebind()
        if (!mIsGettingDirs && canPublishDirectoryMetadata() &&
            directoryMediaChangeGate.scanFinished(result.complete)) {
            getDirectories()
        }
    }

    private fun checkLastMediaChanged() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { checkLastMediaChanged() }
            return
        }
        if (!canPublishDirectoryMetadata() || mediaPollScheduled || mediaPollInFlight) return
        mediaPollScheduled = true
        val generation = mediaPollGeneration
        mLastMediaHandler.postDelayed({
            mediaPollScheduled = false
            if (generation != mediaPollGeneration || !canPublishDirectoryMetadata()) return@postDelayed
            mediaPollInFlight = true
            ensureBackgroundThread {
                val latest = runCatching { getLatestMediaId() to getLatestMediaByDateId() }
                    .onFailure { android.util.Log.w("GalleryDirs", "MediaStore poll failed", it) }.getOrNull()
                runOnUiThread {
                    if (generation != mediaPollGeneration) return@runOnUiThread
                    mediaPollInFlight = false
                    if (!canPublishDirectoryMetadata()) return@runOnUiThread
                    if (latest != null && (mLatestMediaId != latest.first ||
                            mLatestMediaDateId != latest.second)) {
                        mLatestMediaId = latest.first
                        mLatestMediaDateId = latest.second
                        if (mIsGettingDirs) directoryMediaChangeGate.mediaChangedDuringScan()
                        else getDirectories()
                    }
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                ensureBackgroundThread {
                    try {
                        val filesToDelete = mediaDB.getOldRecycleBinItems(
                            System.currentTimeMillis() - MONTH_MILLISECONDS
                        )
                        filesToDelete.forEach {
                            if (File(it.path.replaceFirst(RECYCLE_BIN, recycleBinPath)).delete()) {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                        if (filesToDelete.isNotEmpty()) {
                            org.fossify.gallery.helpers.RecycleBinMembershipRevision.changed()
                        }
                    } catch (e: Exception) {
                    }
                }
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        ensureBackgroundThread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = ArrayList<String>()
                val oftenRepeatedPaths = ArrayList<String>()
                val paths = mDirs
                    .map { it.path.removePrefix(internalPath) }
                    .toMutableList() as ArrayList<String>
                paths.forEach {
                    val parts = it.split("/")
                    var currentString = ""
                    for (i in 0 until parts.size) {
                        currentString += "${parts[i]}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.filter {
                    val path = it
                    it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
                }

                oftenRepeatedPaths.removeAll(substringToRemove)
                val OTGPath = config.OTGPath
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (getDoesFilePathExist(file.absolutePath, OTGPath)) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun getFoldersWithMedia(path: String, request: Long): org.fossify.gallery.helpers.FolderDiscoveryResult {
        val mediaFolders = linkedSetOf<String>()
        val statuses = hashMapOf<String, Boolean>()
        val cfg = config
        val excluded = if (cfg.temporarilyShowExcluded) hashSetOf<String>() else cfg.excludedFolders
        val androidRoot = "${cfg.internalStoragePath.trimEnd('/')}/Android"
        val discovery = org.fossify.gallery.helpers.FolderDiskDiscovery.find(listOf(path), allowed = { folder ->
            folder != androidRoot && !folder.startsWith("$androidRoot/") &&
                FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) == null &&
                folder.shouldFolderBeVisible(excluded, cfg.includedFolders, cfg.shouldShowHidden, statuses) { key, value ->
                    statuses[key] = value
                }
        }, cancelled = { !isCurrentDirectoryScan(request) }, listFiles = { folder ->
            folder.listFiles()?.also { children ->
                if (children.any { it.isFile && it.isMediaFile() }) mediaFolders.add(folder.path)
            }
        })
        return discovery.copy(folders = mediaFolders.intersect(discovery.folders))
    }

    override fun refreshItems() {
        runOnUiThread {
            getRecyclerAdapter()?.discardPendingUpdates()
            retireDirectoryScan()
            if (mShowingFolderFavorites || lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                getDirectories(forceFavoriteReload = true)
            }
        }
    }

    override fun recheckPinnedFolders() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                refreshDirectoryPresentation()
            }
        }
    }

    override fun onDirectoryMutation() {
        runOnUiThread {
            getRecyclerAdapter()?.discardPendingUpdates()
            retireDirectoryScan()
        }
    }

    override fun updateDirectories(directories: ArrayList<Directory>, removedPaths: Set<String>) {
        // These are adapter rows, not an authoritative replacement for the catalog.
        // Invalidate the old scan before selection teardown can replay its snapshot.
        runOnUiThread {
            getRecyclerAdapter()?.discardPendingUpdates()
            retireDirectoryScan()
            val snapshots = directories.map { it.snapshot() }
            val updates = snapshots.associateBy { it.path }
            mDirs = mDirs.filterNot { directory -> removedPaths.any { path ->
                directory.path == path || directory.path.startsWith("${path.trimEnd('/')}/")
            } }.mapTo(ArrayList()) { updates[it.path]?.snapshot() ?: it.snapshot() }
            mDirsIgnoringSearch = mDirs.mapTo(ArrayList()) { it.snapshot() }
            ensureBackgroundThread {
                try {
                    directoryDB.insertAll(snapshots.filter { it.containsMediaFilesDirectly })
                    removeInvalidDBDirectories()
                } catch (error: Exception) {
                    android.util.Log.w("GalleryDirs", "Could not save local directory change", error)
                } finally {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            getDirectories(forceFavoriteReload = true)
                        }
                    }
                }
            }
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
