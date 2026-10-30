Warning: truncated output (original token count: 27127)
Total output lines: 2515

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
import org.fossify.gallery.BuildConfig
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
    private var mFavoriteFolderPaths: Set<String> = emptySet()
    // Saved visibility preferences never serve as authentication credentials.
    private var mFavoriteHiddenAuthorized = false
    private var mFavoriteExcludedAuthorized = false
    private var mFavoriteAuthRequested = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    private var mShouldStopFetching = false
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
    private var mLastMediaFetcher: MediaFetcher? = null
    private var mDirs = ArrayList<Directory>()
    private var mDirsIgnoringSearch = ArrayList<Directory>()

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

    private fun setDirectoryLoading(loading: Boolean) {
        val update = {
            directoryHeaderProgress?.apply {
                OxygenProgressStyle.apply(this@MainActivity, config, this)
                visibility = if (loading) View.VISIBLE else View.GONE
            }
            getRecyclerAdapter()?.setRefreshLoading(loading)
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) update()
        else runOnUiThread { update() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupDirectoryProgressIndicator()
        binding.directoriesRefreshLayout.setColorSchemeColors(getProperPrimaryColor())
        binding.directoriesRefreshLayout.isEnabled = true
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
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
                if (mShowingFolderFavorites) GalleryBottomBar.Tab.FAVORITES else GalleryBottomBar.Tab.ALL
            ) { tab ->
                getRecyclerAdapter()?.finishActMode()
                val section = if (tab == GalleryBottomBar.Tab.FAVORITES)
                    GallerySection.FAVORITES else GallerySection.ALL
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
            getDirectories(forceFavoriteReload = true)
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
        super.onPause()
        // Authentication Activities can temporarily pause us. Favorite requests are
        // retired at onStop, not here, so their successful results are not lost.
        categorizedLoadVersion++
        if (!mShowingFolderFavorites) {
            binding.directoriesRefreshLayout.isRefreshing = false
            mIsGettingDirs = false
            setDirectoryLoading(false)
        }
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
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

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        folderOpenGeneration++
        ViewerAccessCoordinator.cancel(folderOpenTask)
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                GalleryDatabase.destroyInstance()
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
                findItem(R.id.show_all).title = if (mShowingFolderFavorites)
                    "文件视图" else getString(R.string.show_all)
                findItem(R.id.manage_unavailable_folder_favorites).isVisible = mShowingFolderFavorites
                findItem(R.id.favorite_always_show_excluded).apply {
                    isVisible = mShowingFolderFavorites
                    isChecked = config.favoriteShowExcluded
                }
                findItem(R.id.favorite_always_show_hidden).apply {
                    isVisible = mShowingFolderFavorites
                    isChecked = config.favoriteShowHidden
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

        binding.mainMenu.onSearchOpenListener = {
            // Favorites always searches its own folder scope; never silently
            // switch to the global all-files search Activity.
            if (config.searchAllFilesByDefault && !mShowingFolderFavorites) {
                launchSearchActivity()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            setupAdapter(mDirsIgnoringSearch, text)
            binding.directoriesRefreshLayout.isEnabled = text.isEmpty()
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
            if (mShowingFolderFavorites) getString(org.fossify.commons.R.string.favorites)
            else getString(R.string.app_launcher_name)
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
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.is…9127 tokens truncated…he virtual album's
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

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        if (mShowingFolderFavorites) {
            // All favorite results must pass the same authentication and generation
            // checks, including callbacks from sorting/pinning operations.
            runOnUiThread {
                if (favoriteViewForeground && !isFinishing && !isDestroyed) {
                    getDirectories(forceFavoriteReload = true)
                }
            }
            return
        }
        mIsGettingDirs = false
        setDirectoryLoading(false)
        mShouldStopFetching = false

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
            dirs.add(Directory().apply {
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

        val orderedDirs = getSortedDirectories(dirs)
        dirs.clear()
        dirs.addAll(orderedDirs)
        if (config.groupDirectSubfolders) {
            mDirs = dirs.clone() as ArrayList<Directory>
        }

        var isPlaceholderVisible = dirs.isEmpty()

        runOnUiThread {
            checkPlaceholderVisibility(dirs)
            setupAdapter(dirs.clone() as ArrayList<Directory>, sortDirs = false)
        }

        // cached folders have been loaded, recheck folders one by one starting with the first displayed
        mLastMediaFetcher?.shouldStop = true
        mLastMediaFetcher = MediaFetcher(applicationContext)
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        val getImagesOnly = getImages && !getVideos
        val getVideosOnly = getVideos && !getImages
        val favoritePaths = getFavoritePaths()
        val hiddenString = getString(R.string.hidden)
        val albumCovers = config.parseAlbumCovers()
        val includedFolders = config.includedFolders
        val noMediaFolders = getNoMediaFoldersSync()
        val tempFolderPath = config.tempFolderPath
        val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
        val dirPathsToRemove = ArrayList<String>()
        val lastModifieds = mLastMediaFetcher!!.getLastModifieds()
        val dateTakens = mLastMediaFetcher!!.getDateTakens()

        // fetch files from MediaStore only, unless the app has the MANAGE_EXTERNAL_STORAGE permission on Android 11+
        val android11Files = mLastMediaFetcher?.getAndroid11FolderMedia(
            isPickImage = getImagesOnly,
            isPickVideo = getVideosOnly,
            favoritePaths = favoritePaths,
            getFavoritePathsOnly = false,
            getProperDateTaken = true,
            dateTakens = dateTakens
        )
        try {
            for (directory in dirs) {
                if (mShouldStopFetching || isDestroyed || isFinishing) {
                    return
                }
                // A locked album is represented by its cached metadata only.
                // Its media may be scanned only by MediaActivity after authentication.
                if (config.isFolderProtected(directory.path)) continue
                if (directory.path == FAVORITES && config.isFavoritePasswordProtectionOn) {
                    directory.tmb = ""
                    directory.mediaCnt = 0
                    directory.subfoldersMediaCount = 0
                    updateDBDirectory(directory)
                    setupAdapter(dirs, sortDirs = false)
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

                val curMedia = mLastMediaFetcher!!.getFilesFrom(
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
                        setupAdapter(dirs, sortDirs = false)
                        continue
                    }
                }

                val newDir = if (curMedia.isEmpty()) {
                    // An empty scan can mean permission loss, a changed filter,
                    // or a temporarily unavailable volume. Never delete a valid
                    // directory record based only on an empty result.
                    val folder = File(directory.path)
                    val parentReadable = folder.parentFile?.let {
                        it.isDirectory && it.canRead()
                    } == true
                    if (directory.path != tempFolderPath &&
                        !directory.areFavorites() && !directory.isRecycleBin() &&
                        MediaCachePrunePolicy.canPruneDirectory(
                            directory.path, parentReadable, folder.exists()
                        )
                    ) {
                        dirPathsToRemove.add(directory.path)
                    }
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

                // we are looping through the already displayed folders looking for changes, do not do anything if nothing changed
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                    continue
                }

                directory.apply {
                    tmb = newDir.tmb
                    name = newDir.name
                    mediaCnt = newDir.mediaCnt
                    modified = newDir.modified
                    taken = newDir.taken
                    this@apply.size = newDir.size
                    types = newDir.types
                    sortValue = getDirectorySortingValue(curMedia, path, name, size, mediaCnt)
                }

                setupAdapter(dirs, sortDirs = false)

                // Already on the cached-media worker thread: do not create a
                // short-lived Thread per album during a large library refresh.
                updateDBDirectory(directory)
                if (!directory.isRecycleBin() && !directory.areFavorites()) {
                    try {
                        mediaDB.insertAll(curMedia)
                    } catch (ignored: Exception) {
                    }
                }

                if (!directory.isRecycleBin() && !directory.areFavorites()) {
                    val scanOwner = mLastMediaFetcher
                    val freshPaths = curMedia.mapTo(HashSet()) { it.path }
                    getCachedMedia(directory.path, getVideosOnly, getImagesOnly) { cached ->
                        if (mShouldStopFetching || isDestroyed || isFinishing ||
                            scanOwner !== mLastMediaFetcher
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

            if (dirPathsToRemove.isNotEmpty()) {
                val dirsToRemove = dirs.filter { dirPathsToRemove.contains(it.path) }
                dirsToRemove.forEach {
                    directoryDB.deleteDirPath(it.path)
                }
                dirs.removeAll(dirsToRemove)
                setupAdapter(dirs, sortDirs = false)
            }
        } catch (ignored: Exception) {
        }

        val foldersToScan = mLastMediaFetcher!!.getFoldersToScan()
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
            if (pendingDirs.isEmpty()) return
            try {
                GalleryDatabase.getInstance(applicationContext).runInTransaction {
                    directoryDB.insertAll(pendingDirs)
                    if (pendingMedia.isNotEmpty()) mediaDB.insertAll(pendingMedia)
                }
            } catch (ignored: Exception) {
            }
            pendingDirs.clear()
            pendingMedia.clear()
        }

        // check the remaining folders which were not cached at all yet
        for (folder in foldersToScan) {
            if (mShouldStopFetching || isDestroyed || isFinishing) {
                return
            }
            if (config.isFolderProtected(folder)) continue

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

            val newMedia = mLastMediaFetcher!!.getFilesFrom(
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

            if (newMedia.isEmpty()) {
                continue
            }

            if (isPlaceholderVisible) {
                isPlaceholderVisible = false
                runOnUiThread {
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
            pendingDirs.add(newDir)
            if (folder != RECYCLE_BIN && folder != FAVORITES) {
                pendingMedia.addAll(newMedia)
            }
            foldersSinceUpdate++
            if (foldersSinceUpdate >= 8 || pendingMedia.size >= 1200) {
                commitBatch()
                setupAdapter(dirs, sortDirs = false)
                foldersSinceUpdate = 0
            }
        }

        commitBatch()
        // One final differential adapter submission, including the last partial batch.
        setupAdapter(dirs, sortDirs = true)
        mLoadedInitialPhotos = true
        if (config.appRunCount > 1) {
            checkLastMediaChanged()
        }

        runOnUiThread {
            binding.directoriesRefreshLayout.isRefreshing = false
            checkPlaceholderVisibility(dirs)
        }

        checkInvalidDirectories(dirs)
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
            // scan the internal storage from time to time for new folders
            if (config.appRunCount == 1 || config.appRunCount % 30 == 0) {
                everShownFolders.addAll(getFoldersWithMedia(config.internalStoragePath))
            }

            // catch some extreme exceptions like too many everShownFolders for storing, shouldnt really happen
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }

        mDirs = dirs.clone() as ArrayList<Directory>
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
        textToSearch: String = binding.mainMenu.getCurrentQuery(),
        forceRecreate: Boolean = false,
        sortDirs: Boolean = true
    ) {
        val currAdapter = binding.directoriesGrid.adapter
        val distinctDirs = dirs
            .filter { !mShowingFolderFavorites || mFavoriteFolderPaths.contains(it.path) }
            .distinctBy {
                if (mShowingFolderFavorites) FavoriteFolderRules.normalizedPath(it.path)
                else it.path.getDistinctPath()
            }
            .toMutableList() as ArrayList<Directory>

        val sortedDirs = if (sortDirs) getSortedDirectories(distinctDirs) else distinctDirs
        var dirsToShow = getDirsToShow(
            dirs = sortedDirs,
            allDirs = mDirs,
            currentPathPrefix = mCurrentPathPrefix
        ).clone() as ArrayList<Directory>

        if (currAdapter == null || forceRecreate) {
            mDirsIgnoringSearch = dirs
            initZoomListener()
            DirectoryAdapter(
                this,
                dirsToShow,
                this,
                binding.directoriesGrid,
                isPickIntent(intent) || isGetAnyContentIntent(intent),
                binding.directoriesRefreshLayout
            ) {
                val clickedDir = it as Directory
                val path = clickedDir.path
                if (clickedDir.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                    if (path != config.tempFolderPath) {
                        itemClicked(path)
                    }
                } else {
                    mCurrentPathPrefix = path
                    mOpenedSubfolders.add(path)
                    setupAdapter(mDirs, "")
                }
            }.apply {
                setupZoomListener(mZoomListener)
                setRefreshLoading(mIsGettingDirs)
                runOnUiThread {
                    binding.directoriesGrid.adapter = this
                    setupScrollDirection()

                    if (config.viewTypeFolders == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                        binding.directoriesGrid.scheduleLayoutAnimation()
                    }
                }
            }
        } else {
            runOnUiThread {
                if (textToSearch.isNotEmpty()) {
                    dirsToShow = dirsToShow
                        .filter { it.name.contains(textToSearch, true) }
                        .sortedBy { !it.name.startsWith(textToSearch, true) }
                        .toMutableList() as ArrayList
                }
                checkPlaceholderVisibility(dirsToShow)

                (binding.directoriesGrid.adapter as? DirectoryAdapter)?.updateDirs(dirsToShow)
            }
        }

        // recyclerview sometimes becomes empty at init/update, triggering an invisible refresh like this seems to work fine
        binding.directoriesGrid.postDelayed({
            binding.directoriesGrid.scrollBy(0, 0)
        }, 500)
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

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>) {
        val invalidDirs = ArrayList<Directory>()
        val verifiedMissingPaths = HashSet<String>()
        val OTGPath = config.OTGPath
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach { directory ->
            val path = directory.path
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
            dirs.removeAll(invalidDirs.toSet())
            setupAdapter(dirs)
            // A virtual folder, an empty folder, or an unmounted SD card must
            // never lose its DB record because a scan returned no media.
            verifiedMissingPaths.forEach { path ->
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

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories()
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
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

    private fun getFoldersWithMedia(path: String): HashSet<String> {
        val folders = HashSet<String>()
        try {
            val files = File(path).listFiles()
            if (files != null) {
                files.sortBy { !it.isDirectory }
                for (file in files) {
                    if (file.isDirectory && !file.startsWith("${config.internalStoragePath}/Android")) {
                        folders.addAll(getFoldersWithMedia(file.absolutePath))
                    } else if (file.isFile && file.isMediaFile()) {
                        folders.add(file.parent ?: "")
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return folders
    }

    override fun refreshItems() {
        getDirectories()
    }

    override fun recheckPinnedFolders() {
        ensureBackgroundThread {
            gotDirectories(movePinnedDirectoriesToFront(getCurrentlyDisplayedDirs()))
        }
    }

    override fun updateDirectories(directories: ArrayList<Directory>) {
        ensureBackgroundThread {
            storeDirectoryItems(directories)
            removeInvalidDBDirectories()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}

