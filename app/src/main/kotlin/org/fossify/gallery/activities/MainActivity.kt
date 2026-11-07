Warning: truncated output (original token count: 37868)
Total output lines: 3143

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
                displayedDirectoryMetadataRevis…27868 tokens truncated…instances with a scanning worker or DiffUtil.
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
