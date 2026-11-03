package org.fossify.gallery.activities

import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.PhotoAccessEvent
import org.fossify.gallery.helpers.PhotoPlaybackPolicy

import org.fossify.gallery.helpers.FOLDER_UNLOCK_SESSION
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.helpers.FAVORITE_ACCESS_SESSION
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import org.fossify.gallery.helpers.FolderUnlockSessionManager
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.exifinterface.media.ExifInterface
import androidx.print.PrintHelper
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getDataColumn
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFinalUriFromPath
import org.fossify.commons.extensions.getImageResolution
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getResolution
import org.fossify.commons.extensions.getUriMimeType
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.helpers.FavoriteFolderRules
import org.fossify.gallery.helpers.OxygenToolbarStyle
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPortrait
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.scanPathRecursively
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.tryGenericMimeType
import org.fossify.commons.extensions.updateBrightness
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.REQUEST_SET_AS
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.adapters.ViewerPagerHost
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivityMediumBinding
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.dialogs.SaveAsDialog
import org.fossify.gallery.dialogs.SlideshowDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.hideSystemUI
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchResizeImageDialog
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.restoreRecycleBinPath
import org.fossify.gallery.extensions.saveRotatedImageToFile
import org.fossify.gallery.extensions.setAs
import org.fossify.gallery.extensions.shareMediumPath
import org.fossify.gallery.extensions.showFileOnMap
import org.fossify.gallery.extensions.showSystemUI
import org.fossify.gallery.extensions.toggleFileVisibility
import org.fossify.gallery.extensions.tryCopyMoveFilesTo
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.extensions.ensureFavoriteAddAllowed
import org.fossify.gallery.extensions.updateFavoritePaths
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.BOTTOM_ACTION_CHANGE_ORIENTATION
import org.fossify.gallery.helpers.BOTTOM_ACTION_COPY
import org.fossify.gallery.helpers.FolderContentRevision
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.BOTTOM_ACTION_DELETE
import org.fossify.gallery.helpers.BOTTOM_ACTION_EDIT
import org.fossify.gallery.helpers.BOTTOM_ACTION_MOVE
import org.fossify.gallery.helpers.BOTTOM_ACTION_PROPERTIES
import org.fossify.gallery.helpers.BOTTOM_ACTION_RENAME
import org.fossify.gallery.helpers.BOTTOM_ACTION_RESIZE
import org.fossify.gallery.helpers.BOTTOM_ACTION_ROTATE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SET_AS
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHARE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHOW_ON_MAP
import org.fossify.gallery.helpers.BOTTOM_ACTION_SLIDESHOW
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_FAVORITE
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_VISIBILITY
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.DefaultPageTransformer
import org.fossify.gallery.helpers.FadePageTransformer
import org.fossify.gallery.helpers.GO_TO_NEXT_ITEM
import org.fossify.gallery.helpers.GO_TO_PREV_ITEM
import org.fossify.gallery.helpers.HIDE_SYSTEM_UI_DELAY
import org.fossify.gallery.helpers.IS_VIEW_INTENT
import org.fossify.gallery.helpers.MAX_PRINT_SIDE_SIZE
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PORTRAIT_PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_NEXT_ITEM
import org.fossify.gallery.helpers.SHOW_PREV_ITEM
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.ViewerAuthenticationPolicy
import org.fossify.gallery.helpers.ViewerAccessCoordinator
import org.fossify.gallery.helpers.FavoriteMediaLoadPolicy
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_FADE
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_NONE
import org.fossify.gallery.helpers.SLIDESHOW_ANIMATION_SLIDE
import org.fossify.gallery.helpers.SLIDESHOW_DEFAULT_INTERVAL
import org.fossify.gallery.helpers.SLIDESHOW_FADE_DURATION
import org.fossify.gallery.helpers.SLIDESHOW_SLIDE_DURATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.getPermissionToRequest
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.io.File
import java.util.concurrent.Future
import kotlin.math.min

@Suppress("UNCHECKED_CAST")
class ViewPagerActivity : BaseViewerActivity(), ViewPager.OnPageChangeListener, ViewerPagerHost {
    companion object {
        private const val REQUEST_VIEW_VIDEO = 1
        private const val SAVED_PATH = "current_path"
    }

    private var mPath = ""
    private var viewerInitialSelectedPath = ""
    private var viewerSelectedImageStarted = 0L
    private var selectedImageRenderedLogged = false
    private val renderedAdjacentPaths = HashSet<String>()
    private var mDirectory = ""
    private var mIsFullScreen = false
    private var mPos = -1
    private var mShowAll = false
    /** The tapped grid's ordering evidence; never an authorization capability. */
    private var viewerGridOrder: List<String>? = null
    private var initialPublicFolderSnapshot = false
    private var initialPublicFolderCheckpoint: org.fossify.gallery.helpers.FolderReturnCheckpoint? = null
    private var stagedInternalViewer = false
    private var mIsSlideshowActive = false
    private var activePagerAdapter: MyPagerAdapter? = null
    private data class PendingPagerPublication(
        val media: List<Medium>,
        val positions: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>?,
        val generation: Long,
        val folderRevision: Long,
        val photoRevision: Long,
        val scope: String
    )
    private var pendingPagerPublication: PendingPagerPublication? = null

    /** A catalog upgrade may finish on any animation frame; never rebuild pages mid-swipe. */
    private data class SwipeMediaPublication(
        val media: ArrayList<Medium>,
        val positionIndex: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>,
        val pathIndex: Map<String, Int>,
        val authorizedPaths: Set<String>,
        val refetchPosition: Boolean,
        val fullCatalog: Boolean,
        val generation: Long,
        val folderRevision: Long,
        val photoRevision: Long
    )
    private val swipePublicationGate =
        org.fossify.gallery.helpers.ViewerSwipePublicationGate<SwipeMediaPublication>()
    private var swipePageOrigin = -1
    private var swipePrefetchTarget = -1
    private var pendingPageAccessRefresh = false
    private var pendingSwipeMenuRefresh = false
    private var publishedFolderRevision: Long? = null
    private var publishedPhotoRevision: Long? = null
    private var mediaPublicationGeneration = 0L
    private var mediaPublicationTask: Future<*>? = null
    private var slideshowUpdateGeneration = 0L
    private val mediaPublicationExecutor = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.LinkedBlockingQueue(1),
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
    )
    private var orientationRequestGeneration = 0L
    private var orientationTask: Future<*>? = null
    private val orientationExecutor = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.LinkedBlockingQueue(1),
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
    )

    private var mSlideshowHandler = Handler()
    private var mSlideshowInterval = SLIDESHOW_DEFAULT_INTERVAL
    private var mSlideshowMoveBackwards = false
    private var mSlideshowMedia = mutableListOf<Medium>()
    private var mAreSlideShowMediaVisible = false
    private var mRandomSlideshowStopped = false

    private var mIsOrientationLocked = false

    private val favoriteListenerId = java.util.UUID.randomUUID().toString()
    private val favoriteProtectionPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (isPhotoProfile && org.fossify.gallery.helpers.PhotoProtectionChanges.viewerScopeChanged(key)) {
            closeUnauthorizedFavorites()
            return@OnSharedPreferenceChangeListener
        }
        if (key == null || key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_HASH) ||
            key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_TYPE) ||
            key == org.fossify.gallery.helpers.FAVORITE_PASSWORD_ENABLED ||
            key == org.fossify.gallery.helpers.FAVORITE_PASSWORD_HASH ||
            key == org.fossify.gallery.helpers.FAVORITE_PROTECTION_TYPE ||
            key == org.fossify.gallery.helpers.FAVORITE_FALLBACK_PASSWORD_HASH ||
            key == org.fossify.gallery.helpers.FAVORITE_FALLBACK_PROTECTION_TYPE) {
            if (isFavoriteRequest) {
                // A credential or lock change invalidates the current viewing
                // capability immediately; do not recapture thousands of files
                // from this SharedPreferences callback on the main thread.
                closeUnauthorizedFavorites()
            }
            if (key == null || key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_HASH) ||
                key.startsWith(org.fossify.commons.helpers.PROTECTED_FOLDER_TYPE)) {
                viewerAccessGeneration++
                ViewerAccessCoordinator.cancel(viewerAccessTask)
                FolderUnlockSessionManager.revokeAll()
                FavoriteAccessSessionManager.revokeAll()
                binding.viewPager.adapter = null
                activePagerAdapter = null
                pendingPagerPublication = null
                authorizedViewerPaths = emptySet()
                mMediaFiles = arrayListOf()
                finish()
            }
        }
    }
    private val photoSessionId get() = intent.getStringExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION)
    private var slideshowEntryConsumed = false
    private val photoProfileId get() = intent.getStringExtra(PhotoProfileMediaLoader.EXTRA_ID)
    private val viewerSearchQuery get() = intent.getStringExtra(org.fossify.gallery.helpers.ViewerSearchPolicy.EXTRA_QUERY)
    private val isPhotoProfile get() = intent.hasExtra(PhotoProfileMediaLoader.EXTRA_ID)
    private val favoriteSessionId get() = intent.getStringExtra(FAVORITE_ACCESS_SESSION)
    private val isFavoriteRequest get() = intent.getBooleanExtra(SHOW_FAVORITES, false) || intent.hasExtra(FAVORITE_ACCESS_SESSION)
    private val neighborHandoffId get() = intent.getStringExtra(org.fossify.gallery.helpers.ViewerNeighborHandoffStore.EXTRA_ID)
    private var viewerLoadGeneration = 0L
    private var viewerLoadTask: GetMediaAsynctask? = null
    @Volatile
    private var viewerAccessGeneration = 0L
    private var viewerAccessTask: Future<*>? = null
    private var viewerStageGeneration = 0L
    private var viewerAccessSubmission = 0L
    private var retryViewerScan: (() -> Unit)? = null
    private var viewerScanNeedsRetry = false
    private var viewerBoundaryStarted = 0L
    private var viewerMediaPathIndex: Map<String, Int> = emptyMap()
    private val viewerLoading by lazy { org.fossify.gallery.helpers.ViewerLoadingMotion(binding.photoBoundaryLoading) }
    private val viewerSourceType get() = when {
        isPhotoProfile -> org.fossify.gallery.helpers.ViewerSourceType.PHOTO_PROFILE
        isFavoriteRequest -> org.fossify.gallery.helpers.ViewerSourceType.FAVORITES
        else -> org.fossify.gallery.helpers.ViewerSourceType.FOLDER
    }
    private val progressiveInternalViewer get() = !isExternalIntent() &&
        (isPhotoProfile || isFavoriteRequest || stagedInternalViewer)
    private val photoNavigation = org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator()
    private var photoCatalogAdvanceQueued = false
    private var photoExpandGeneration = 0L
    private var photoLastExpandKey: String? = null
    private var photoLastDirection = true
    private var photoFailureVisible = false
    private var photoCatalogMode = false
    private var photoCatalogRevision = -1L
    private val photoPageAccess = org.fossify.gallery.helpers.PhotoViewerPageAccessState()
    private var photoPageAccessTask: Future<*>? = null
    private var photoPageAccessGeneration = 0L
    private var photoPageInFlight: Set<String> = emptySet()
    private var photoPageInFlightCenter = -1
    private var photoPageQueuedCenter: Int? = null
    private var photoCatalogUpdating = false
    private var photoCatalogComplete = false
    private var photoFallbackStarted = false
    private var photoCatalogUpdateGeneration = 0L
    private var photoCatalogUpdateTask: Future<*>? = null
    private var photoPartialExpandTask: Future<*>? = null
    private var activeFolderUnlockSessionId: String? = null
    private var ownedFolderUnlockSessionId: String? = null
    @Volatile private var authorizedViewerPaths: Set<String> = emptySet()

    private fun closeUnauthorizedFavorites() {
        photoPageAccessGeneration++
        ViewerAccessCoordinator.cancel(photoPageAccessTask)
        photoPageAccessTask = null
        photoPageInFlight = emptySet()
        photoPageQueuedCenter = null
        photoNavigation.invalidate()
        swipePublicationGate.clear()
        pendingPageAccessRefresh = false
        pendingSwipeMenuRefresh = false
        photoExpandGeneration++
        viewerLoading.setLoading(false)
        photoCatalogMode = false
        photoCatalogUpdating = false
        photoCatalogComplete = false
        photoCatalogUpdateGeneration++
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        ViewerAccessCoordinator.cancel(photoPartialExpandTask)
        photoPageAccess.reset(-1L)
        stopSlideshow()
        viewerLoadGeneration++
        viewerLoadTask?.stopFetching()
        binding.viewPager.adapter = null
        activePagerAdapter = null
        pendingPagerPublication = null
        authorizedViewerPaths = emptySet()
        mMediaFiles = arrayListOf()
        mSlideshowMedia.clear()
        finish()
    }

    override fun mayLoadFavoritePath(path: String): Boolean {
        if (!checkFavoriteAccess(path)) return false
        if (isPhotoProfile && photoCatalogMode) {
            if (photoCatalogUpdating ||
                photoCatalogRevision != org.fossify.gallery.helpers.PhotoContentRevision.current()) return false
            if (!photoPageAccess.contains(path, photoCatalogRevision)) return false
        }
        val token = activeFolderUnlockSessionId ?: return true
        if (!FolderUnlockSessionManager.isActive(token)) {
            authorizedViewerPaths = emptySet()
            binding.root.post { if (!isFinishing && !isDestroyed) closeUnauthorizedFavorites() }
            return false
        }
        // Atomically replaced with the validated catalog and cleared on revocation.
        return path in authorizedViewerPaths
    }

    private fun checkFavoriteAccess(path: String? = null): Boolean {
        if (isPhotoProfile && photoSessionId != null && !PhotoProfileAccessSessionManager.isActive(applicationContext, photoSessionId)) {
            binding.root.post { if (!isFinishing && !isDestroyed) closeUnauthorizedFavorites() }
            return false
        }
        if (!isFavoriteRequest) return true
        val valid = if (path == null) FavoriteAccessSessionManager.isActive(favoriteSessionId)
            else FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, path)
        if (!valid) binding.root.post { if (!isFinishing && !isDestroyed) closeUnauthorizedFavorites() }
        return valid
    }

    private var mMediaFiles = ArrayList<Medium>()
    private var mFavoritePaths = ArrayList<String>()
    private var mIgnoredPaths = ArrayList<String>()
    private var mOriginalBrightness: Float? = null

    private val binding by viewBinding(ActivityMediumBinding::inflate)

    override val contentHolder: View
        get() = binding.fragmentHolder

    override val appBarLayout: AppBarLayout
        get() = binding.mediumViewerAppbar

    private val slideshowFragments = object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(manager: androidx.fragment.app.FragmentManager, fragment: androidx.fragment.app.Fragment) {
            if (fragment !is VideoFragment) return
            // The adapter records instantiated fragments after its transaction. Wait
            // for that record, then check identity again instead of polling forever.
            binding.viewPager.post {
                if (mIsSlideshowActive && !isFinishing && !isDestroyed &&
                    fragment === getCurrentFragment() && fragment.isResumed) scheduleSwipe()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore media fragments before the current password/session
        // flow validates the viewer request.
        super.onCreate(null)
        supportFragmentManager.registerFragmentLifecycleCallbacks(slideshowFragments, false)
        setContentView(binding.root)
        org.fossify.gallery.helpers.OxygenProgressStyle.apply(this, config, binding.photoBoundaryLoading)
        binding.viewPager.onUnknownBoundary = { forward -> requestViewerBoundary(forward) }
        PhotoProfileAccessSessionManager.addListener(favoriteListenerId) { event ->
            if (event == PhotoAccessEvent.REVOKED && isPhotoProfile && photoSessionId != null) runOnUiThread {
                if (!isDestroyed && !isFinishing && isPhotoProfile && photoSessionId != null) closeUnauthorizedFavorites()
            }
        }
        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.addListener(favoriteListenerId) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed && isPhotoProfile && !isExternalIntent()) {
                    consumePhotoCatalogAdvance()
                }
            }
        }
        if (!checkFavoriteAccess()) return
        getSharedPrefs().registerOnSharedPreferenceChangeListener(favoriteProtectionPrefsListener)
        FavoriteAccessSessionManager.addRevocationListener(favoriteListenerId) { revoked ->
            if (revoked == favoriteSessionId) binding.root.post {
                    if (isFinishing || isDestroyed || revoked != favoriteSessionId) return@post
                if (!FavoriteAccessSessionManager.isActive(revoked)) closeUnauthorizedFavorites()
                else {
                    stopSlideshow()
                    binding.viewPager.adapter = null
                    activePagerAdapter = null
                    pendingPagerPublication = null
                    authorizedViewerPaths = emptySet()
                    mSlideshowMedia.clear()
                    mAreSlideShowMediaVisible = false
                    mRandomSlideshowStopped = false
                    mMediaFiles = ArrayList(mMediaFiles.filter { FavoriteAccessSessionManager.isCapturedPath(revoked, it.path) })
                    if (mMediaFiles.isEmpty()) finish() else refreshUI(mMediaFiles, true)
                }
            }
        }
        setupEdgeToEdge(
            padBottomSystem = listOf(binding.bottomActions.bottomActionsWrapper),
        )

        setupOptionsMenu()
        refreshMenuItems()

        window.decorView.setBackgroundColor(getProperBackgroundColor())
        // Internal gallery taps carry a bounded neighbor window; do not duplicate
        // the entire grid (which can contain tens of thousands of entries) here.
        if (!isPhotoProfile && !intent.getBooleanExtra(IS_FROM_GALLERY, false)) {
            (MediaActivity.mMedia.clone() as ArrayList<ThumbnailItem>).filterIsInstanceTo(mMediaFiles, Medium::class.java)
        }

        requestMediaPermissions {
            initViewPager(
                savedPath = savedInstanceState?.getString(SAVED_PATH).orEmpty()
            )
        }

        initFavorites()
    }

    override fun onResume() {
        super.onResume()
        if (!checkFavoriteAccess()) return
        OxygenToolbarStyle.applyViewerPopup(this, binding.mediumViewerToolbar)
        if (!hasPermission(getPermissionToRequest())) {
            finish()
            return
        }

        initBottomActions()
        mOriginalBrightness = window.updateBrightness(config.maxBrightness, mOriginalBrightness)
        setupOrientation()
        refreshMenuItems()

        val filename = getCurrentMedium()?.name ?: mPath.getFilenameFromPath()
        binding.mediumViewerToolbar.title = filename
    }

    override fun onPostResume() {
        super.onPostResume()
        activePagerAdapter?.onHostResumed()
        pendingPagerPublication?.let { pending ->
            pendingPagerPublication = null
            val scope = photoProfileId ?: favoriteSessionId ?: mDirectory
            val stillCurrent = pending.generation == mediaPublicationGeneration &&
                pending.folderRevision == FolderContentRevision.current() &&
                pending.photoRevision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
                pending.scope == scope && checkFavoriteAccess()
            if (stillCurrent) updatePagerItems(pending.media, pending.positions)
            else if (!isFinishing && !isDestroyed) refreshViewPager(true)
        }
    }

    override fun onPause() {
        super.onPause()
        stopSlideshow()
    }

    override fun onDestroy() {
        retryViewerScan = null
        finishViewerBoundaryTrace()
        viewerLoading.dispose()
        swipePublicationGate.clear()
        pendingPageAccessRefresh = false
        org.fossify.gallery.helpers.ViewerChromeMotion.cancel(binding.root)
        authorizedViewerPaths = emptySet()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(slideshowFragments)
        mSlideshowHandler.removeCallbacksAndMessages(null)
        PhotoProfileAccessSessionManager.removeListener(favoriteListenerId)
        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.removeListener(favoriteListenerId)
        photoExpandGeneration++
        photoNavigation.invalidate()
        binding.viewPager.onUnknownBoundary = null
        photoCatalogUpdateGeneration++
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        ViewerAccessCoordinator.cancel(photoPartialExpandTask)
        getSharedPrefs().unregisterOnSharedPreferenceChangeListener(favoriteProtectionPrefsListener)
        FavoriteAccessSessionManager.removeRevocationListener(favoriteListenerId)
        org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborHandoffId)
        viewerAccessGeneration++
        ViewerAccessCoordinator.cancel(viewerAccessTask)
        photoPageAccessGeneration++
        ViewerAccessCoordinator.cancel(photoPageAccessTask)
        photoPageAccess.reset(-1L)
        photoPageInFlight = emptySet()
        photoPageQueuedCenter = null
        FolderUnlockSessionManager.revokeSession(ownedFolderUnlockSessionId)
        viewerLoadGeneration++
        viewerLoadTask?.stopFetching()
        mediaPublicationGeneration++
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.shutdownNow()
        orientationRequestGeneration++
        orientationTask?.cancel(true)
        orientationExecutor.shutdownNow()
        super.onDestroy()
        ColorModeHelper.resetColorMode(this)

        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            config.temporarilyShowHidden = false
        }

        if (config.isThirdPartyIntent) {
            config.isThirdPartyIntent = false

            if (intent.extras == null || isExternalIntent()) {
                mMediaFiles = arrayListOf()
            }
        }
    }

    fun refreshMenuItems() {
        val currentMedium = getCurrentMedium() ?: return
        currentMedium.isFavorite = mFavoritePaths.contains(currentMedium.path)
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0

        runOnUiThread {
            val rotationDegrees = getCurrentPhotoFragment()?.mCurrentRotationDegrees ?: 0
            binding.mediumViewerToolbar.menu.apply {
                findItem(R.id.menu_show_on_map).isVisible = visibleBottomActions and BOTTOM_ACTION_SHOW_ON_MAP == 0
                findItem(R.id.menu_slideshow).isVisible = visibleBottomActions and BOTTOM_ACTION_SLIDESHOW == 0
                findItem(R.id.menu_properties).isVisible = visibleBottomActions and BOTTOM_ACTION_PROPERTIES == 0
                findItem(R.id.menu_delete).isVisible = visibleBottomActions and BOTTOM_ACTION_DELETE == 0
                findItem(R.id.menu_share).isVisible = visibleBottomActions and BOTTOM_ACTION_SHARE == 0
                findItem(R.id.menu_edit).isVisible = visibleBottomActions and BOTTOM_ACTION_EDIT == 0 && !currentMedium.isSVG()
                findItem(R.id.menu_rename).isVisible = visibleBottomActions and BOTTOM_ACTION_RENAME == 0 && !currentMedium.getIsInRecycleBin()
                findItem(R.id.menu_rotate).isVisible = currentMedium.isImage() && visibleBottomActions and BOTTOM_ACTION_ROTATE == 0
                findItem(R.id.menu_set_as).isVisible = visibleBottomActions and BOTTOM_ACTION_SET_AS == 0
                findItem(R.id.menu_copy_to_clipboard).isVisible = currentMedium.isImage()
                findItem(R.id.menu_copy_to).isVisible = visibleBottomActions and BOTTOM_ACTION_COPY == 0
                findItem(R.id.menu_move_to).isVisible = visibleBottomActions and BOTTOM_ACTION_MOVE == 0
                findItem(R.id.menu_save_as).isVisible = rotationDegrees != 0
                findItem(R.id.menu_print).isVisible = currentMedium.isImage() || currentMedium.isRaw()
                findItem(R.id.menu_resize).isVisible = visibleBottomActions and BOTTOM_ACTION_RESIZE == 0 && currentMedium.isImage()
                findItem(R.id.menu_hide).isVisible =
                    (!isRPlus() || isExternalStorageManager()) && !currentMedium.isHidden() && visibleBottomActions and BOTTOM_ACTION_TOGGLE_VISIBILITY == 0 && !currentMedium.getIsInRecycleBin()

                findItem(R.id.menu_unhide).isVisible =
                    (!isRPlus() || isExternalStorageManager()) && currentMedium.isHidden() && visibleBottomActions and BOTTOM_ACTION_TOGGLE_VISIBILITY == 0 && !currentMedium.getIsInRecycleBin()

                findItem(R.id.menu_add_to_favorites).isVisible =
                    !currentMedium.isFavorite && visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE == 0 && !currentMedium.getIsInRecycleBin()

                findItem(R.id.menu_remove_from_favorites).isVisible =
                    currentMedium.isFavorite && visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE == 0 && !currentMedium.getIsInRecycleBin()

                findItem(R.id.menu_restore_file).isVisible = currentMedium.path.startsWith(recycleBinPath)
                findItem(R.id.menu_create_shortcut).isVisible = true
                findItem(R.id.menu_change_orientation).isVisible = rotationDegrees == 0 && visibleBottomActions and BOTTOM_ACTION_CHANGE_ORIENTATION == 0
                findItem(R.id.menu_rotate).setShowAsAction(
                    if (rotationDegrees != 0) {
                        MenuItem.SHOW_AS_ACTION_ALWAYS
                    } else {
                        MenuItem.SHOW_AS_ACTION_IF_ROOM
                    }
                )
            }

            if (visibleBottomActions != 0) {
                updateBottomActionIcons(currentMedium)
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.mediumViewerToolbar.apply {
            OxygenToolbarStyle.applyViewerPopup(this@ViewPagerActivity, this)
            setTitleTextColor(Color.WHITE)
            overflowIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_three_dots_vector, Color.WHITE)
            navigationIcon = resources.getColoredDrawableWithColor(org.fossify.commons.R.drawable.ic_arrow_left_vector, Color.WHITE)
        }

        binding.mediumViewerToolbar.inflateMenu(R.menu.menu_viewpager)
        updateMenuItemColors(binding.mediumViewerToolbar.menu, forceWhiteIcons = true)
        binding.mediumViewerToolbar.setOnMenuItemClickListener { menuItem ->
            if (getCurrentMedium() == null) {
                return@setOnMenuItemClickListener true
            }

            when (menuItem.itemId) {
                R.id.menu_set_as -> setAs(getCurrentPath())
                R.id.menu_slideshow -> initSlideshow()
                R.id.menu_copy_to -> checkMediaManagementAndCopy(true)
                R.id.menu_move_to -> moveFileTo()
                R.id.menu_open_with -> openPath(getCurrentPath(), true)
                R.id.menu_hide -> toggleFileVisibility(true)
                R.id.menu_unhide -> toggleFileVisibility(false)
                R.id.menu_share -> shareMediumPath(getCurrentPath())
                R.id.menu_delete -> checkDeleteConfirmation()
                R.id.menu_rename -> checkMediaManagementAndRename()
                R.id.menu_print -> printFile()
                R.id.menu_edit -> openEditor(getCurrentPath())
                R.id.menu_properties -> showProperties()
                R.id.menu_show_on_map -> showFileOnMap(getCurrentPath())
                R.id.menu_rotate_right -> rotateImage(90)
                R.id.menu_rotate_left -> rotateImage(-90)
                R.id.menu_rotate_one_eighty -> rotateImage(180)
                R.id.menu_add_to_favorites -> toggleFavorite()
                R.id.menu_remove_from_favorites -> toggleFavorite()
                R.id.menu_restore_file -> restoreFile()
                R.id.menu_force_portrait -> toggleOrientation(SCREEN_ORIENTATION_PORTRAIT)
                R.id.menu_force_landscape -> toggleOrientation(SCREEN_ORIENTATION_LANDSCAPE)
                R.id.menu_force_landscape_reverse -> toggleOrientation(SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
                R.id.menu_default_orientation -> toggleOrientation(SCREEN_ORIENTATION_UNSPECIFIED)
                R.id.menu_save_as -> saveImageAs()
                R.id.menu_create_shortcut -> createShortcut()
                R.id.menu_resize -> resizeImage()
                R.id.menu_settings -> launchSettings()
                R.id.menu_copy_to_clipboard -> copyImageToClipboard()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.mediumViewerToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE && resultCode == Activity.RESULT_OK && resultData != null) {
            mPos = -1
            refreshViewPager()
        } else if (requestCode == REQUEST_SET_AS && resultCode == Activity.RESULT_OK) {
            toast(R.string.wallpaper_set_successfully)
        } else if (requestCode == REQUEST_VIEW_VIDEO && resultCode == Activity.RESULT_OK && resultData != null) {
            if (resultData.getBooleanExtra(GO_TO_NEXT_ITEM, false)) {
                goToNextItem()
            } else if (resultData.getBooleanExtra(GO_TO_PREV_ITEM, false)) {
                goToPrevItem()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        initBottomActionsLayout()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_PATH, getCurrentPath())
    }

    private fun initViewPager(savedPath: String) {
        val uri = intent.data
        if (uri != null && savedPath.isEmpty()) {
            val request = ++viewerAccessGeneration
            submitViewerAccess(request) {
                val resolvedPath = runCatching { getDataColumn(uri).orEmpty() }.getOrDefault("")
                runOnUiThread {
                    if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (resolvedPath.isEmpty()) finish() else initViewPager(resolvedPath)
                }
            }
            return
        }
        if (uri != null) {
            mPath = savedPath.ifEmpty { getDataColumn(uri).orEmpty() }
        } else {
            try {
                mPath = savedPath.ifEmpty { intent.getStringExtra(PATH).orEmpty() }

                // make sure "Open Recycle Bin" works well with "Show all folders content"
                mShowAll = config.showAll && (mPath.isNotEmpty() && !mPath.startsWith(recycleBinPath))
            } catch (e: Exception) {
                showErrorToast(e)
                finish()
                return
            }
        }

        if (savedPath.isEmpty() && intent.extras?.containsKey(REAL_FILE_PATH) == true) {
            mPath = intent.extras!!.getString(REAL_FILE_PATH)!!
        }
        viewerInitialSelectedPath = mPath

        if (mPath.isEmpty()) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        if (mPath.isPortrait() && getPortraitPath() == "") {
            val newIntent = Intent(this, ViewPagerActivity::class.java)
            newIntent.putExtras(intent!!.extras!!)
            newIntent.putExtra(PORTRAIT_PATH, mPath)
            newIntent.putExtra(PATH, "${mPath.getParentPath().getParentPath()}/${mPath.getFilenameFromPath()}")

            startActivity(newIntent)
            finish()
            return
        }

        val existenceRequest = ++viewerAccessGeneration
        val requestedPath = mPath
        submitViewerAccess(existenceRequest) {
            val exists = runCatching { File(requestedPath).exists() || getPortraitPath().isNotEmpty() }.getOrDefault(false)
            runOnUiThread {
                if (existenceRequest != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                if (exists || isPhotoProfile) authenticateViewerRequest() else finish()
            }
        }
    }

    /** Populate swiping only after the selected file can render; never initialize the pager twice. */
    private fun completePhotoViewerLoad(
        request: Long, cached: ArrayList<Medium>?, firstContext: org.fossify.gallery.helpers.PhotoProfileLoadContext?,
        initialized: Boolean,
        neighborWindow: org.fossify.gallery.helpers.ViewerNeighborWindow?,
        preparedCatalog: org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.Catalog?,
        gridProducerActive: Boolean
    ) {
        val profile = photoProfileId
        val session = photoSessionId
        val clicked = mPath
        fun publish(items: List<Medium>, preserveCatalogOrder: Boolean = false, complete: Boolean = true) {
            if (request != viewerLoadGeneration || isFinishing || isDestroyed) return
            if (firstContext != null && !firstContext.isCurrent(applicationContext)) { finish(); return }
            if (!checkFavoriteAccess() || items.isEmpty()) { finish(); return }
            val publication = ++mediaPublicationGeneration
            mediaPublicationTask?.cancel(true)
            mediaPublicationExecutor.queue.clear()
            val previousOrder = mMediaFiles
            mediaPublicationTask = mediaPublicationExecutor.submit {
                // The catalog is already sorted in the grid's committed order.
                // Never reshuffle a complete random Photos snapshot on viewer entry.
                val scopedItems = if (preserveCatalogOrder) searchedViewerMedia(items)
                    else stabilizeRandomViewerOrder(searchedViewerMedia(items), previousOrder)
                runOnUiThread {
                    if (publication != mediaPublicationGeneration || request != viewerLoadGeneration ||
                        isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                    if (scopedItems.isEmpty()) { finish(); return@runOnUiThread }
                    if (initialized) {
                        gotMedia(ArrayList<ThumbnailItem>(scopedItems), refetchViewPagerPosition = true, fullCatalog = complete)
                    } else {
                        mMediaFiles = ArrayList(scopedItems)
                        initContinue()
                    }
                }
            }
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_initial_catalog", preparedCatalog?.media?.size ?: 0)
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_handoff_result", if (preparedCatalog == null) 0 else 1)
        org.fossify.gallery.helpers.GalleryPerfTrace.event(
            if (preparedCatalog == null) "viewer_handoff_result_missing" else "viewer_handoff_result_ready")
        if (preparedCatalog != null && initialized && firstContext != null &&
            preparedCatalog.revision == org.fossify.gallery.helpers.PhotoContentRevision.current()) {
            photoCatalogMode = true
            photoCatalogUpdating = false
            photoCatalogComplete = preparedCatalog.isComplete
            photoNavigation.publish(preparedCatalog.isComplete, preparedCatalog.before, preparedCatalog.after)
            photoCatalogRevision = preparedCatalog.revision
            // The selected file was freshly authorized by firstFrame on the worker.
            photoPageAccess.reset(preparedCatalog.revision)
            photoPageAccess.approve(listOf(clicked), preparedCatalog.revision)
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_catalog_hit", preparedCatalog.media.size)
            publish(preparedCatalog.media, preserveCatalogOrder = true,
                complete = preparedCatalog.isComplete)
            if (!preparedCatalog.isComplete) consumePhotoCatalogAdvance()
            return
        }
        if (gridProducerActive) {
            // The grid's worker is still preparing the complete catalog. Do not
            // launch a duplicate GetMediaAsynctask merely because the 5-item
            // first frame arrived before INDEXED / COMPLETE.
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_catalog_waiting_for_grid")
        } else org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_catalog_fallback")
        fun loadFullCollection() {
            if (gridProducerActive && initialized && firstContext != null) {
                consumePhotoCatalogAdvance()
                return
            }
            startPhotoProgressiveFallback()
        }
        val window = neighborWindow
        if (!initialized || firstContext == null || window == null) { loadFullCollection(); return }
        val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        adjacentReadyStarted = started
        viewerAccessTask = try { ViewerAccessCoordinator.submit {
            val safe = runCatching {
                org.fossify.gallery.helpers.PhotoViewerSnapshotStore.validateNeighborWindow(
                    applicationContext, profile, session, window.items, clicked
                ) { Thread.currentThread().isInterrupted }
            }.getOrNull()
            runOnUiThread {
                if (request != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_neighbors_validation", started, window.items.size)
                if (safe != null && safe.context.isCurrent(applicationContext) && safe.media.any { it.path == clicked }) {
                    publishAdjacent(safe.media)
                }
                // A local denial must not discard the selected image. The full request
                // performs the existing complete authorization and error handling.
                loadFullCollection()
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { loadFullCollection(); null }
    }

    /**
     * Consumes newer grid stages without starting another full profile scan.
     * The selected file is rechecked on a worker before switching catalogs.
     * The ViewPager itself is not recreated; gotMedia preserves the live Path.
     */
    private fun consumePhotoCatalogAdvance() {
        if (!isPhotoProfile || isExternalIntent() || photoCatalogUpdating ||
            mDirectory != PhotoProfileMediaLoader.PATH || mMediaFiles.isEmpty() ||
            isFinishing || isDestroyed) return
        if (photoCatalogUpdateTask != null) { photoCatalogAdvanceQueued = true; return }
        val selectedPath = getCurrentPath().ifEmpty { mPath }
        val previouslyPublished = mMediaFiles.toList()
        val request = ++photoCatalogUpdateGeneration
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        val profile = photoProfileId
        val session = photoSessionId
        val app = applicationContext
        photoCatalogUpdateTask = try {
            ViewerAccessCoordinator.submit {
                val store = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore
                val candidate = store.latest(app, profile, session, selectedPath)
                val producerActive = store.isLoading(app, profile, session)
                val medium = candidate?.pathIndex?.get(selectedPath)?.let { candidate.media[it] }
                val selectedCheck = medium?.let {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.firstFrame(
                        app, profile, session, it
                    ) { Thread.currentThread().isInterrupted }
                }
                val visible = if (candidate != null && selectedCheck != null) {
                    org.fossify.gallery.helpers.ViewerSearchPolicy.filter(
                        candidate.media, viewerSearchQuery.orEmpty())
                } else emptyList()
                val changed = !org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.sameCatalog(
                    previouslyPublished, visible)
                runOnUiThread {
                    if (request != photoCatalogUpdateGeneration || isDestroyed || isFinishing ||
                        !checkFavoriteAccess() || photoCatalogUpdating) return@runOnUiThread
                    photoCatalogUpdateTask = null
                    if (candidate != null && selectedCheck != null &&
                        selectedCheck.context.isCurrent(app) &&
                        candidate.revision == org.fossify.gallery.helpers.PhotoContentRevision.current() &&
                        visible.any { it.path == selectedPath }) {
                        if (!photoCatalogMode || photoCatalogRevision != candidate.revision) {
                            photoPageAccess.reset(candidate.revision)
                        }
                        photoCatalogMode = true
                        photoCatalogRevision = candidate.revision
                        photoCatalogComplete = candidate.isComplete
                        photoNavigation.publish(candidate.isComplete, candidate.before, candidate.after)
                        photoFailureVisible = false
                        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_catalog_upgrade", visible.size)
                        photoPageAccess.approve(listOf(selectedPath), candidate.revision)
                        if (candidate.isComplete || changed) {
                            // The producer already sorted this catalog in the grid's
                            // canonical order; don't prepend the initial five-page window.
                            gotMedia(ArrayList<ThumbnailItem>(visible), refetchViewPagerPosition = true,
                                fullCatalog = candidate.isComplete)
                        }
                        if (!candidate.isComplete) requestPhotoPartialExpand(mPos.coerceAtLeast(0))
                    } else if (!producerActive && !photoFallbackStarted &&
                        !photoCatalogComplete && !isFinishing) {
                        startPhotoProgressiveFallback()
                    }
                    if (photoCatalogAdvanceQueued) {
                        photoCatalogAdvanceQueued = false
                        consumePhotoCatalogAdvance()
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            binding.viewPager.postDelayed({ if (!isDestroyed && !isFinishing) consumePhotoCatalogAdvance() }, 150)
            // Transient bounded-pool pressure is not a failed authorization.
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_page_access_wait")
            null
        }
    }

    /** One owner for a fallback scan. Its staged results use the same store/listener as the grid. */
    private fun startPhotoProgressiveFallback() {
        if (photoFallbackStarted || !isPhotoProfile || isExternalIntent() || isFinishing || isDestroyed) return
        photoFallbackStarted = true
        val generation = viewerLoadGeneration
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_fallback_reason_no_producer")
        viewerLoadTask = GetMediaAsynctask(applicationContext, PhotoProfileMediaLoader.PATH,
            showAll = false, photoProfileId = photoProfileId, photoSessionId = photoSessionId,
            photoAnchorPath = getCurrentPath().ifEmpty { mPath }, photoReuseProducer = true, onPhotoResult = {
                if (generation == viewerLoadGeneration && !isFinishing && !isDestroyed) consumePhotoCatalogAdvance()
            }, onFailure = {
                if (generation == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                    if (it is org.fossify.gallery.helpers.PhotoLoadSharedException) {
                        photoFallbackStarted = false
                        consumePhotoCatalogAdvance()
                        return@GetMediaAsynctask
                    }
                    photoFailureVisible = true
                    photoNavigation.cancel()
                    viewerLoading.setLoading(false)
                    showErrorToast(it)
                }
            }) {
                if (generation == viewerLoadGeneration && !isFinishing && !isDestroyed) consumePhotoCatalogAdvance()
            }.also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
    }

    /** Real media indices remain the only indices used by edit/delete/share/slideshow. */
    private fun requestViewerBoundary(forward: Boolean): Boolean {
        if (!progressiveInternalViewer || !checkFavoriteAccess() || (isPhotoProfile && photoCatalogComplete) || photoNavigation.state == org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.State.COMPLETE) return false
        val path = getCurrentPath()
        if (photoFailureVisible) {
            photoFailureVisible = false
            photoFallbackStarted = false
        }
        if (path.isEmpty() || !photoNavigation.request(path, forward)) return false
        if (viewerBoundaryStarted == 0L) {
            viewerBoundaryStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_expand_started", activePagerAdapter?.count)
        }
        photoLastDirection = forward
        photoLastExpandKey = null
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_boundary_requested_${viewerSourceType.name.lowercase()}", activePagerAdapter?.count)
        if (isPhotoProfile) org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.focus(photoProfileId, photoSessionId, path)
        viewerLoading.setLoading(true)
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_boundary_wait", activePagerAdapter?.count)
        if (isPhotoProfile) {
            requestPhotoPartialExpand(fullCatalogPosition(mPos), forward)
            consumePhotoCatalogAdvance()
        }
        // Join the active producer; only a failed/incomplete producer can be retried.
        if (!isPhotoProfile && viewerScanNeedsRetry) {
            viewerScanNeedsRetry = false
            retryViewerScan?.invoke()
        }
        return true
    }

    private fun updatePhotoBoundaryWait() {
        // No pending edge means no list allocation/linear scan on this frame.
        if (photoNavigation.pending == null) {
            viewerLoading.setLoading(false)
            finishViewerBoundaryTrace()
            return
        }
        val media = getCurrentMedia()
        val displayed = activePagerAdapter?.media ?: return
        if (displayed !== media && (displayed.size != media.size ||
            displayed.indices.any { displayed[it].path != media[it].path })) return
        val paths = media.map { it.path }
        val destination = photoNavigation.destination(paths, getCurrentPath())
        if (!isPhotoProfile && viewerScanNeedsRetry && destination == null) photoNavigation.cancel()
        viewerLoading.setLoading(photoNavigation.pending != null && !photoFailureVisible)
        if (photoNavigation.pending == null) finishViewerBoundaryTrace()
        if (destination != null) {
            // Do not consume a queued direction until the real adapter publication has caught up.
            if (activePagerAdapter?.media === media || activePagerAdapter?.media === displayed ||
                (displayed.size == media.size && displayed.indices.all { displayed[it].path == media[it].path })) {
                binding.viewPager.setCurrentItem(destination,
                    kotlin.math.abs(destination - binding.viewPager.currentItem) == 1 &&
                        org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
                validatePhotoPageWindow(fullCatalogPosition(destination))
            }
        }
    }

    private fun finishViewerBoundaryTrace() {
        if (viewerBoundaryStarted == 0L) return
        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_boundary_wait", viewerBoundaryStarted, activePagerAdapter?.count)
        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_expand_finished", viewerBoundaryStarted, activePagerAdapter?.count)
        viewerBoundaryStarted = 0L
    }

    /** Works before the first catalog handoff, with UNKNOWN edges owned by the producer. */
    private fun requestPhotoPartialExpand(position: Int, direction: Boolean? = null) {
        if (!isPhotoProfile || isExternalIntent() || photoCatalogComplete ||
            photoCatalogUpdating || photoPartialExpandTask != null || position !in mMediaFiles.indices) return
        val forward = direction ?: when {
            position <= 3 && position >= mMediaFiles.lastIndex - 3 -> photoLastDirection
            position >= mMediaFiles.lastIndex - 4 -> true
            position <= 4 -> false
            else -> return
        }
        val boundary = if (forward) photoNavigation.after else photoNavigation.before
        if (boundary == org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.Boundary.END) return
        val anchor = if (forward) mMediaFiles.last().path else mMediaFiles.first().path
        val key = "$anchor:$forward:${mMediaFiles.size}:${photoCatalogRevision}:$boundary"
        if (photoLastExpandKey == key) return
        photoLastExpandKey = key
        val profile = photoProfileId
        val session = photoSessionId
        val generation = ++photoExpandGeneration
        val revision = org.fossify.gallery.helpers.PhotoContentRevision.current()
        val app = applicationContext
        photoPartialExpandTask = try {
            ViewerAccessCoordinator.submit {
                val store = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore
                val expanded = try {
                    store.requestExpand(app, profile, session, anchor, forward, viewerSearchQuery) { Thread.currentThread().isInterrupted }
                } catch (cancel: java.util.concurrent.CancellationException) { throw cancel
                } catch (_: Exception) {
                    org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_fallback_reason_segment_read")
                    false
                }
                val producerActive = store.isLoading(app, profile, session)
                runOnUiThread {
                    if (generation != photoExpandGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    photoPartialExpandTask = null
                    if (revision != org.fossify.gallery.helpers.PhotoContentRevision.current()) { photoLastExpandKey = null; return@runOnUiThread }
                    if (!expanded && !producerActive && !photoCatalogComplete) {
                        if (!photoFallbackStarted) startPhotoProgressiveFallback()
                        else if (photoNavigation.pending != null && !photoFailureVisible) {
                            // A completed/failed producer without a usable successor is
                            // a recoverable load failure, never a successful END.
                            photoFailureVisible = true
                            photoNavigation.cancel()
                            viewerLoading.setLoading(false)
                            toast(R.string.photo_viewer_load_retry)
                        }
                    }
                    if (expanded) consumePhotoCatalogAdvance()
                    else if (photoNavigation.pending != null && photoFailureVisible) {
                        photoNavigation.cancel()
                        viewerLoading.setLoading(false)
                    }
                    // A direction change during the worker is coalesced here.
                    val pending = photoNavigation.pending
                    if (pending != null && pending.forward != forward) requestPhotoPartialExpand(mPos, pending.forward)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            photoLastExpandKey = null
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_access_window_wait")
            binding.viewPager.postDelayed({ if (!isFinishing && !isDestroyed) requestPhotoPartialExpand(mPos, direction) }, 150)
            null
        }
    }

    private fun selectedViewerMedium(path: String): Medium =
        mMediaFiles.firstOrNull { it.path == path }?.copy() ?: Medium(
            null, path.getFilenameFromPath(), path, path.getParentPath(),
            0, 0, 0, getTypeFromPath(path), 0, false, 0L, 0L
        )

    /** Full results remain inside the search result set used to build the tapped window. */
    private fun searchedViewerMedia(media: List<Medium>): List<Medium> {
        val query = viewerSearchQuery
        if (query.isNullOrBlank()) return media
        val matches = org.fossify.gallery.helpers.ViewerSearchPolicy.filter(media, query)
        if (isPhotoProfile) return matches
        val groupingPath = if (isFavoriteRequest) FAVORITES else mDirectory
        val grouped = MediaFetcher(applicationContext).groupMedia(ArrayList(matches), groupingPath)
        return grouped.filterIsInstance<Medium>()
    }

    /** Keeps an already exposed random neighborhood stable as any source completes. */
    private fun stabilizeRandomViewerOrder(media: List<Medium>, previous: List<Medium> = mMediaFiles): List<Medium> {
        val sortPath = when {
            isPhotoProfile -> PhotoProfileMediaLoader.PATH
            isFavoriteRequest -> FAVORITES
            else -> mDirectory
        }
        if (config.getFolderSorting(sortPath) and SORT_BY_RANDOM == 0) return media
        return org.fossify.gallery.helpers.FolderViewerMediaPolicy.reconcile(
            previous, media, random = true
        ) { it.path }
    }

    private fun takeNeighborWindow(
        sourceType: org.fossify.gallery.helpers.ViewerSourceType,
        sourceKey: String
    ): org.fossify.gallery.helpers.ViewerNeighborWindow? {
        if (isExternalIntent() || !intent.getBooleanExtra(IS_FROM_GALLERY, false)) {
            org.fossify.gallery.helpers.ViewerNeighborHandoffStore.clear(neighborHandoffId)
            return null
        }
        return org.fossify.gallery.helpers.ViewerNeighborHandoffStore.take(
            neighborHandoffId, mPath, sourceType, sourceKey
        )
    }

    private fun publishAdjacent(media: List<Medium>) {
        if (media.size < 2 || media.none { it.path == mPath } || isFinishing || isDestroyed) return
        if (isPhotoProfile && (photoCatalogMode || mMediaFiles.size > media.size)) return
        gotMedia(ArrayList<ThumbnailItem>(media), refetchViewPagerPosition = true, fullCatalog = false)
        logPagerReady("viewer_adjacent_pager_ready", adjacentReadyStarted, media.size, mPath, requireMultiplePages = true)
    }

    private var adjacentReadyStarted = 0L

    private fun logPagerReady(
        stage: String, started: Long, count: Int, expectedPath: String? = null,
        requireMultiplePages: Boolean = false, remainingFrames: Int = 8
    ) {
        binding.viewPager.postOnAnimation {
            val adapterCount = binding.viewPager.adapter?.count ?: 0
            val pathReady = expectedPath == null || getCurrentMedium()?.path == expectedPath
            val countReady = adapterCount > 0 && (!requireMultiplePages || adapterCount > 1)
            if (!isFinishing && !isDestroyed && pathReady && countReady) {
                org.fossify.gallery.helpers.GalleryPerfTrace.end(stage, started, count)
            } else if (!isFinishing && !isDestroyed && remainingFrames > 0) {
                logPagerReady(stage, started, count, expectedPath, requireMultiplePages, remainingFrames - 1)
            }
        }
    }

    /** Freshly validate one captured favorite, then reconcile the full capability on a worker. */
    private fun startFavoriteViewer() {
        photoNavigation.publish(false)
        val request = ++viewerAccessGeneration
        val load = ++viewerLoadGeneration
        val selectedStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        viewerLoadTask?.stopFetching()
        val path = mPath
        viewerGridOrder = org.fossify.gallery.helpers.ViewerGridOrderStore.take(
            intent.getStringExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID), FAVORITES, path)
        val selected = selectedViewerMedium(path)
        val token = favoriteSessionId
        val neighborWindow = takeNeighborWindow(
            org.fossify.gallery.helpers.ViewerSourceType.FAVORITES, FAVORITES
        )
        submitViewerAccess(request) {
            val first = FavoriteAccessSessionManager.validateSelectedPath(token, path)
            runOnUiThread {
                if (request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                if (!FavoriteAccessSessionManager.isPublicationCurrent(first)) { closeUnauthorizedFavorites(); return@runOnUiThread }
                stagedInternalViewer = true
                mMediaFiles = arrayListOf(selected)
                initContinue()
                viewerSelectedImageStarted = selectedStarted
                logPagerReady("viewer_selected_pager_ready", selectedStarted, 1, path)
                var loadCompleteFavoritesFromScan: (() -> Unit)? = null
                fun publishValidatedFavorites(
                    items: List<Medium>,
                    fullStarted: Long,
                    expectedFavoritePaths: Set<String>? = null,
                    catalogStarted: Long? = null,
                    completeSource: Boolean = true
                ) {
                    val stage = ++viewerStageGeneration
                    val validationStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val app = applicationContext
                    val previous = mMediaFiles
                    submitViewerAccess(request) {
                        val candidates = if (completeSource) items else (items + previous).distinctBy { it.path }
                        val relationCurrent = expectedFavoritePaths == null || runCatching {
                            app.getFavoritePaths().toSet() == expectedFavoritePaths
                        }.getOrDefault(false)
                        val batch = if (relationCurrent) {
                            if (completeSource) org.fossify.gallery.helpers.FavoriteAccessPolicy.validateViewerItems(
                                token, candidates, { it.path }, path
                            ) else org.fossify.gallery.helpers.FavoriteAccessPolicy.validateViewerPreview(
                                token, candidates, { it.path }, path
                            )
                        } else null
                        org.fossify.gallery.helpers.GalleryPerfTrace.end(
                            "viewer_catalog_validation", validationStarted, batch?.items?.size ?: 0
                        )
                        runOnUiThread {
                            if (stage != viewerStageGeneration || request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                            if (!relationCurrent && expectedFavoritePaths != null) {
                                // The database relationship changed since this list was prepared.
                                // Re-enter the original scan path so it can reconcile/revoke the
                                // full Session before any result is published.
                                if (completeSource) loadCompleteFavoritesFromScan?.invoke()
                                return@runOnUiThread
                            }
                            if (batch == null && !completeSource && FavoriteAccessSessionManager.isActive(token)) return@runOnUiThread
                            if (batch == null || batch.items.none { it.path == path } || !FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)) {
                                closeUnauthorizedFavorites(); return@runOnUiThread
                            }
                            val publication = ++mediaPublicationGeneration
                            mediaPublicationTask?.cancel(true)
                            mediaPublicationExecutor.queue.clear()
                            val previousOrder = mMediaFiles
                            mediaPublicationTask = mediaPublicationExecutor.submit {
                                val sorted = searchedViewerMedia(batch.items).toCollection(ArrayList())
                                if (!completeSource) MediaFetcher(app).sortMedia(sorted, config.getFolderSorting(FAVORITES))
                                val searched = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcile(
                                    viewerGridOrder, previousOrder, sorted,
                                    config.getFolderSorting(FAVORITES) and SORT_BY_RANDOM != 0
                                ) { it.path }
                                runOnUiThread {
                                    if (stage != viewerStageGeneration || publication != mediaPublicationGeneration || request != viewerAccessGeneration ||
                                        load != viewerLoadGeneration || isFinishing || isDestroyed ||
                                        !FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)) return@runOnUiThread
                                    gotMedia(ArrayList<ThumbnailItem>(searched), refetchViewPagerPosition = true, fullCatalog = completeSource)
                                    if (catalogStarted != null) {
                                        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_hit", catalogStarted, searched.size)
                                    }
                                    logPagerReady("viewer_full_list_prepared", fullStarted, searched.size)
                                }
                            }
                        }
                    }
                }
                loadCompleteFavoritesFromScan = {
                    viewerScanNeedsRetry = false
                    val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    viewerLoadTask = GetMediaAsynctask(applicationContext, FAVORITES, showAll = false,
                        favoriteSessionId = token,
                        onFavoritePreview = { candidates, relationship ->
                            if (request == viewerAccessGeneration && load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                                publishValidatedFavorites(candidates, fullStarted, relationship, completeSource = false)
                            }
                        }, onFailure = {
                            if (load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                                if (!FavoriteAccessSessionManager.isActive(token)) closeUnauthorizedFavorites()
                                else {
                                    viewerScanNeedsRetry = true
                                    photoNavigation.cancel()
                                    photoNavigation.publish(false)
                                    finishViewerBoundaryTrace()
                                    viewerLoading.setLoading(false)
                                    showErrorToast(it)
                                }
                            }
                        }
                    ) { items ->
                        if (request == viewerAccessGeneration && load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                            viewerScanNeedsRetry = viewerLoadTask?.scanResult?.complete != true
                            publishValidatedFavorites(items.filterIsInstance<Medium>(), fullStarted, completeSource = !viewerScanNeedsRetry)
                        }
                    }.also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
                }
                retryViewerScan = { loadCompleteFavoritesFromScan?.invoke() }
                fun loadCompleteFavorites() {
                    val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val catalog = org.fossify.gallery.helpers.FavoriteViewerCatalogStore.take(
                        applicationContext,
                        intent.getStringExtra(org.fossify.gallery.helpers.FavoriteViewerCatalogStore.EXTRA_ID)
                    )
                    if (catalog == null) {
                        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_miss", catalogStarted, 0)
                        loadCompleteFavoritesFromScan?.invoke()
                    } else {
                        val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                        publishValidatedFavorites(
                            catalog.items, fullStarted, catalog.favoritePaths, catalogStarted
                        )
                    }
                }
                fun validateNeighborsThenContinue(selectedValidation: FavoriteAccessSessionManager.ValidatedPaths?) {
                    val window = neighborWindow
                    if (window == null) { loadCompleteFavorites(); return }
                    val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    adjacentReadyStarted = started
                    submitViewerAccess(request) {
                        if (Thread.currentThread().isInterrupted) return@submitViewerAccess
                        val validations = window.items.mapNotNull { item ->
                            val validation = if (item.path == path) selectedValidation
                                else FavoriteAccessSessionManager.validateSelectedPath(token, item.path)
                            validation?.let { item to it }
                        }
                        val selectedStillValid = validations.any { it.first.path == path } &&
                            FavoriteAccessSessionManager.isActive(token)
                        val safe = window.items.filter { item -> validations.any { it.first.path == item.path } }
                        val validPublication = validations.all { FavoriteAccessSessionManager.isPublicationCurrent(it.second) }
                        runOnUiThread {
                            if (request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                            org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_neighbors_validation", started, window.items.size)
                            if (!selectedStillValid || !validPublication || !FavoriteAccessSessionManager.isActive(token)) {
                                closeUnauthorizedFavorites(); return@runOnUiThread
                            }
                            if (safe.isNotEmpty()) publishAdjacent(safe)
                            loadCompleteFavorites()
                        }
                    }
                }
                validateNeighborsThenContinue(first)
            }
        }
    }

    /** Authentication has succeeded; only the clicked path gates the first image. */
    private fun startFolderViewer(existingToken: String?, authenticatedRoot: String?, onInvalid: () -> Unit) {
        photoNavigation.publish(false)
        val request = ++viewerAccessGeneration
        val load = ++viewerLoadGeneration
        val selectedStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        viewerLoadTask?.stopFetching()
        val path = mPath
        if (viewerGridOrder == null) {
            viewerGridOrder = org.fossify.gallery.helpers.ViewerGridOrderStore.take(
                intent.getStringExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID),
                path.getParentPath(), path)
        }
        // The folder catalog was freshness-checked and consumed on the auth worker.
        // Keep its list reference while initializing the selected page; don't copy
        // or scan it on the UI thread.
        val reusedCatalog = mMediaFiles.takeIf { initialPublicFolderSnapshot }
        val reusedCatalogCheckpoint = initialPublicFolderCheckpoint
        val selected = selectedViewerMedium(path)
        val app = applicationContext
        submitViewerAccess(request) {
            val token = existingToken ?: runCatching {
                FolderUnlockSessionManager.createSession(authenticatedRoot ?: path.getParentPath())
            }.getOrNull()
            val allowed = runCatching {
                FolderUnlockSessionManager.isMediaAuthorized(token, path, app.config::isFolderProtected) && File(path).isFile
            }.getOrDefault(false)
            runOnUiThread {
                if (request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) {
                    if (existingToken == null) FolderUnlockSessionManager.revokeSession(token)
                    return@runOnUiThread
                }
                if (!allowed || !FolderUnlockSessionManager.isActive(token)) {
                    if (existingToken == null) FolderUnlockSessionManager.revokeSession(token)
                    onInvalid(); return@runOnUiThread
                }
                if (existingToken == null) ownedFolderUnlockSessionId = token
                activeFolderUnlockSessionId = token
                stagedInternalViewer = true
                mMediaFiles = arrayListOf(selected)
                authorizedViewerPaths = setOf(selected.path)
                initContinue()
                viewerSelectedImageStarted = selectedStarted
                logPagerReady("viewer_selected_pager_ready", selectedStarted, 1, path)
                val neighborWindow = takeNeighborWindow(
                    org.fossify.gallery.helpers.ViewerSourceType.FOLDER, mDirectory
                )
                // Publish the already displayed folder candidates before a fresh scan.
                // Never treat a nonempty (possibly partial) handoff as the final list.
                fun scanRemaining() {
                    viewerScanNeedsRetry = false
                    retryViewerScan = { scanRemaining() }
                    val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val fallbackStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    viewerLoadTask = GetMediaAsynctask(app, mDirectory, showAll = false,
                        folderSessionId = token,
                        onFolderPreview = { result, candidates ->
                            if (request == viewerAccessGeneration && load == viewerLoadGeneration && result.accessCurrent()) {
                                publishFolderCandidates(request, load, token, path,
                                    candidates.filterIsInstance<Medium>(), completeSource = false)
                            }
                        },
                        onFailure = {
                            if (load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                                viewerScanNeedsRetry = true
                                photoNavigation.cancel()
                                photoNavigation.publish(false)
                                finishViewerBoundaryTrace()
                                viewerLoading.setLoading(false)
                                showErrorToast(it)
                                // Keep authorized pages usable; allow another entry to retry.
                            }
                        }
                    ) { items ->
                        if (request == viewerAccessGeneration && load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                            val fullMedia = items.filterIsInstance<Medium>()
                            viewerScanNeedsRetry = viewerLoadTask?.scanResult?.complete != true
                            publishFolderCandidates(request, load, token, path, fullMedia, completeSource = !viewerScanNeedsRetry) {
                                logPagerReady("viewer_full_list_prepared", fullStarted, fullMedia.size)
                                org.fossify.gallery.helpers.GalleryPerfTrace.end(
                                    "viewer_full_scan_fallback", fallbackStarted, fullMedia.size
                                )
                            }
                        }
                    }.also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
                }
                fun completeFromCatalogOrScan() {
                    val catalog = reusedCatalog
                    if (catalog.isNullOrEmpty()) {
                        scanRemaining()
                    } else {
                        val scopedCatalog = catalog
                        val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                        publishFolderCandidates(
                            request, load, token, path, scopedCatalog,
                            freshnessCheckpoint = reusedCatalogCheckpoint,
                            onStale = {
                                org.fossify.gallery.helpers.GalleryPerfTrace.end(
                                    "viewer_catalog_miss", fullStarted, scopedCatalog.size
                                )
                                scanRemaining()
                            }
                        ) {
                            org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_validation", fullStarted, scopedCatalog.size)
                            org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_hit", fullStarted, scopedCatalog.size)
                            logPagerReady("viewer_full_list_prepared", fullStarted, scopedCatalog.size)
                        }
                    }
                }
                val window = neighborWindow
                if (window == null) completeFromCatalogOrScan() else {
                    val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    adjacentReadyStarted = started
                    submitViewerAccess(request) {
                        val candidates = window.items.filter {
                            it.path.getParentPath() == path.getParentPath() ||
                                (it.type == TYPE_PORTRAITS && it.path.getParentPath().getParentPath() == path.getParentPath())
                        }
                        val paths = LinkedHashSet<String>().apply { add(path); candidates.forEach { add(it.path) } }
                        val authorized = runCatching {
                            FolderUnlockSessionManager.authorizedPaths(token, paths, app.config::isFolderProtected)
                        }.getOrDefault(emptySet())
                        val safe = candidates.filter { it.path in authorized && runCatching { File(it.path).isFile }.getOrDefault(false) }
                        runOnUiThread {
                            if (request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                            org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_neighbors_validation", started, window.items.size)
                            if (!FolderUnlockSessionManager.isActive(token) || path !in authorized) {
                                finish(); return@runOnUiThread
                            }
                            if (safe.any { it.path == path }) publishAdjacent(safe)
                            completeFromCatalogOrScan()
                        }
                    }
                }
            }
        }
    }

    /** Validate every candidate off the UI thread before exposing adjacent pages. */
    private fun publishFolderCandidates(
        request: Long, load: Long, token: String?, selectedPath: String,
        items: List<Medium>,
        freshnessCheckpoint: org.fossify.gallery.helpers.FolderReturnCheckpoint? = null,
        onStale: (() -> Unit)? = null,
        completeSource: Boolean = true,
        onPublished: (() -> Unit)? = null
    ) {
        val stage = ++viewerStageGeneration
        val app = applicationContext
        val folder = selectedPath.getParentPath()
        val publishedOrder = mMediaFiles
        submitViewerAccess(request) {
            val candidates = if (completeSource) items else
                (items + publishedOrder).distinctBy { it.path }
            val searchedItems = searchedViewerMedia(candidates)
            val directItems = searchedItems.filter {
                it.path.getParentPath() == folder ||
                    (it.type == TYPE_PORTRAITS && it.path.getParentPath().getParentPath() == folder)
            }
            val paths = LinkedHashSet<String>().apply { add(selectedPath); directItems.forEach { add(it.path) } }
            val authorized = runCatching {
                FolderUnlockSessionManager.authorizedPaths(token, paths, app.config::isFolderProtected)
            }.getOrDefault(emptySet())
            val safe = directItems.filter { it.path in authorized && runCatching { File(it.path).isFile }.getOrDefault(false) }.toCollection(ArrayList())
            if (!completeSource) MediaFetcher(app).sortMedia(safe, config.getFolderSorting(folder))
            val random = config.getFolderSorting(folder) and SORT_BY_RANDOM != 0
            val ordered = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcile(
                viewerGridOrder, publishedOrder, safe, random
            ) { it.path }
            val freshnessCurrent = freshnessCheckpoint == null || runCatching {
                org.fossify.gallery.helpers.FolderReturnCheckpoint.isCurrent(app, folder, freshnessCheckpoint)
            }.getOrDefault(false)
            val directPaths = directItems.mapTo(HashSet(directItems.size)) { it.path }
            runOnUiThread {
                if (stage != viewerStageGeneration || request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                if (!freshnessCurrent) {
                    onStale?.invoke()
                    return@runOnUiThread
                }
                val currentPath = getCurrentPath().ifEmpty { selectedPath }
                val currentDenied = currentPath in directPaths && currentPath !in authorized
                if (!FolderUnlockSessionManager.isActive(token) || selectedPath !in authorized || currentDenied || safe.isEmpty()) {
                    finish(); return@runOnUiThread
                }
                // A late scan must retain the photo the user has already swiped to.
                mPath = currentPath
                gotMedia(ArrayList<ThumbnailItem>(ordered), refetchViewPagerPosition = true, fullCatalog = completeSource)
                onPublished?.invoke()
            }
        }
    }

    private fun authenticateViewerRequest() {
        showSystemUI()
        if (isPhotoProfile) {
            if (isExternalIntent()) { finish(); return }
            if (photoSessionId != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            mShowAll = false
            // Internal, short-lived metadata handoff: no second MediaStore scan
            // for public Photos profiles when the already-validated list is unchanged.
            val cached = org.fossify.gallery.helpers.PhotoViewerSnapshotStore.take(
                applicationContext,
                intent.getStringExtra(org.fossify.gallery.helpers.PhotoViewerSnapshotStore.EXTRA_ID),
                photoProfileId, photoSessionId, mPath
            )
            val request = ++viewerLoadGeneration
            viewerLoadTask?.stopFetching()
            ViewerAccessCoordinator.cancel(viewerAccessTask)
            val profile = photoProfileId
            val session = photoSessionId
            val clicked = mPath
            val neighborWindow = takeNeighborWindow(
                org.fossify.gallery.helpers.ViewerSourceType.PHOTO_PROFILE, profile.orEmpty()
            )
            val catalogId = intent.getStringExtra(
                org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.EXTRA_ID)
            viewerAccessTask = try { ViewerAccessCoordinator.submit {
                val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                val store = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore
                val preparedCatalog = if (!isExternalIntent() &&
                    intent.getBooleanExtra(IS_FROM_GALLERY, false)) {
                    store.take(applicationContext, catalogId, profile, session, clicked)
                        ?: store.latest(applicationContext, profile, session, clicked)
                } else null
                store.focus(profile, session, clicked)
                val gridProducerActive = store.isLoading(applicationContext, profile, session)
                org.fossify.gallery.helpers.GalleryPerfTrace.end(
                    if (preparedCatalog == null) "viewer_catalog_miss" else "viewer_catalog_lookup",
                    catalogStarted, preparedCatalog?.media?.size ?: 0)
                val selected = preparedCatalog?.pathIndex?.get(clicked)?.let { preparedCatalog.media[it] }
                    ?: cached?.firstOrNull { it.path == clicked } ?: Medium(
                        null, clicked.getFilenameFromPath(), clicked, clicked.getParentPath(),
                        0, 0, 0, getTypeFromPath(clicked), 0, false, 0L, 0L)
                val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                val first = runCatching {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.firstFrame(
                        applicationContext, profile, session, selected
                    ) { Thread.currentThread().isInterrupted }
                }.getOrNull()
                runOnUiThread {
                    if (request != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (first != null && !first.context.isCurrent(applicationContext)) { finish(); return@runOnUiThread }
                    if (first != null) {
                        mMediaFiles = ArrayList(first.media)
                        initContinue()
                        viewerSelectedImageStarted = started
                        logPagerReady("viewer_selected_pager_ready", started, 1, clicked)
                    }
                    completePhotoViewerLoad(request, cached, first?.context, first != null, neighborWindow,
                        preparedCatalog.takeIf { first != null }, gridProducerActive)
                }
            } } catch (_: java.util.concurrent.RejectedExecutionException) { finish(); null }
            return
        }

        // This viewer is exported for external VIEW/REVIEW Intents. The skip
        // extra is caller-controlled and must never bypass the folder lock.
        // A missing manifest lookup fails closed; the normal auth callback
        // still owns all password decisions.
        @Suppress("DEPRECATION")
        val exportedOrUnknown = runCatching {
            packageManager.getActivityInfo(componentName, 0).exported
        }.getOrDefault(true)
        if (isFavoriteRequest) {
            mShowAll = false
            startFavoriteViewer()
            return
        }
        val folderSessionId = intent.getStringExtra(FOLDER_UNLOCK_SESSION)
        activeFolderUnlockSessionId = folderSessionId
        // Shared metadata is never proof of authorization for an exported
        // request. Locked cache entries need this request's own capability.
        val authenticateFolder = {
            val request = ++viewerAccessGeneration
            val path = mPath
            val app = applicationContext
            submitViewerAccess(request) {
                val protectedFolder = FavoriteMediaLoadPolicy.protectedFolderForPath(
                    path, app.config::isFolderProtected
                ) ?: path.getParentPath()
                runOnUiThread {
                    if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    handleLockedFolderOpening(protectedFolder) { success ->
                        if (success) {
                            unlockFolderAndFilter(protectedFolder)
                        } else finish()
                    }
                }
            }
        }
        val continueAfterFailedFolderCapability = {
            if (ViewerAuthenticationPolicy.mayHonorSkipExtra(
                    intent.getBooleanExtra(SKIP_AUTHENTICATION, false), exportedOrUnknown
                )) {
                initContinue()
            } else if (FavoriteFolderRules.isExcluded(mPath.getParentPath(), config.excludedFolders)) {
                handleExcludedFolderPasswordProtection { authenticateFolder() }
            } else {
                authenticateFolder()
            }
        }
        if (folderSessionId != null && !isExternalIntent() && !mShowAll && !intent.getBooleanExtra(SHOW_RECYCLE_BIN, false)) {
            startFolderViewer(folderSessionId, null) { continueAfterFailedFolderCapability() }
            return
        }
        if (folderSessionId != null) {
            val request = ++viewerAccessGeneration
            val app = applicationContext
            val media = mMediaFiles.toList()
            submitViewerAccess(request) {
                val orderedPaths = LinkedHashSet<String>().apply {
                    add(mPath)
                    media.forEach { add(it.path) }
                }
                val authorizedPaths = runCatching {
                    FolderUnlockSessionManager.authorizedPaths(
                        folderSessionId, orderedPaths, app.config::isFolderProtected
                    )
                }.getOrDefault(emptySet())
                val valid = mPath in authorizedPaths
                val safe = if (valid) media.filter { it.path in authorizedPaths } else emptyList()
                runOnUiThread {
                    if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (valid) {
                        // A valid unlock is scoped to this folder. The current
                        // path was checked first in the same bounded batch.
                        mShowAll = false
                        mMediaFiles = ArrayList(safe)
                        initContinue()
                    } else continueAfterFailedFolderCapability()
                }
            }
        } else {
            // A snapshot never proves authorization; consume its metadata off
            // the UI thread, then run the exact original folder unlock flow.
            val snapshotId = if (!mShowAll && !isExternalIntent()) {
                intent.getStringExtra(org.fossify.gallery.helpers.FolderViewerSnapshotStore.EXTRA_ID)
            } else null
            if (snapshotId == null) {
                continueAfterFailedFolderCapability()
            } else {
                val request = ++viewerAccessGeneration
                val app = applicationContext
                val folder = mPath.getParentPath()
                val path = mPath
                submitViewerAccess(request) {
                    val catalogStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val cached = runCatching {
                        org.fossify.gallery.helpers.FolderViewerSnapshotStore.take(
                            app, snapshotId, folder, path
                        )
                    }.getOrNull()
                    org.fossify.gallery.helpers.GalleryPerfTrace.end(
                        if (cached == null) "viewer_catalog_miss" else "viewer_catalog_handoff",
                        catalogStarted, cached?.items?.size ?: 0
                    )
                    runOnUiThread {
                        if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                        if (cached != null) {
                            mMediaFiles = cached.items
                            initialPublicFolderCheckpoint = cached.checkpoint
                            initialPublicFolderSnapshot = true
                        }
                        continueAfterFailedFolderCapability()
                    }
                }
            }
        }
    }

    private fun unlockFolderAndFilter(protectedFolder: String) {
        if (!isExternalIntent() && !mShowAll && !intent.getBooleanExtra(SHOW_RECYCLE_BIN, false)) {
            startFolderViewer(null, protectedFolder) { finish() }
            return
        }
        val request = ++viewerAccessGeneration
        val media = mMediaFiles.toList()
        val orderedPaths = LinkedHashSet<String>().apply {
            add(mPath)
            media.forEach { add(it.path) }
        }
        val app = applicationContext
        submitViewerAccess(request) {
            val token = runCatching {
                FolderUnlockSessionManager.createSession(protectedFolder)
            }.getOrNull()
            val authorizedPaths = token?.let {
                runCatching {
                    FolderUnlockSessionManager.authorizedPaths(it, orderedPaths, app.config::isFolderProtected)
                }.getOrDefault(emptySet())
            }.orEmpty()
            val currentAllowed = mPath in authorizedPaths
            val safe = if (currentAllowed) media.filter { it.path in authorizedPaths } else emptyList()
            runOnUiThread {
                if (request != viewerAccessGeneration || isFinishing || isDestroyed) {
                    FolderUnlockSessionManager.revokeSession(token)
                    return@runOnUiThread
                }
                if (!currentAllowed) {
                    FolderUnlockSessionManager.revokeSession(token)
                    finish()
                    return@runOnUiThread
                }
                ownedFolderUnlockSessionId = token
                activeFolderUnlockSessionId = token
                mMediaFiles = ArrayList(safe)
                authorizedViewerPaths = safe.mapTo(HashSet(safe.size)) { it.path }
                initContinue()
            }
        }
    }

    private fun submitViewerAccess(request: Long, work: () -> Unit) {
        ViewerAccessCoordinator.cancel(viewerAccessTask)
        val submission = ++viewerAccessSubmission
        fun attempt() {
            if (submission != viewerAccessSubmission || request != viewerAccessGeneration || isFinishing || isDestroyed) return
            viewerAccessTask = try {
                ViewerAccessCoordinator.submit {
                    if (request == viewerAccessGeneration && !Thread.currentThread().isInterrupted) work()
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_access_executor_busy")
                // Coalesce retries to the latest submission; congestion is not a
                // denied credential and must not permanently strand five pages.
                binding.viewPager.postDelayed({ attempt() }, 120L)
                null
            }
        }
        attempt()
    }

    private fun initContinue() {
        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            if (isShowHiddenFlagNeeded()) {
                if (!config.isHiddenPasswordProtectionOn) {
                    config.temporarilyShowHidden = true
                }
            }

            config.isThirdPartyIntent = true
        }

        val isShowingFavorites = isFavoriteRequest
        val isShowingRecycleBin = intent.getBooleanExtra(SHOW_RECYCLE_BIN, false)
        mDirectory = when {
            isPhotoProfile -> PhotoProfileMediaLoader.PATH
            isShowingFavorites -> FAVORITES
            isShowingRecycleBin -> RECYCLE_BIN
            else -> mPath.getParentPath()
        }
        binding.mediumViewerToolbar.title = mPath.getFilenameFromPath()

        binding.viewPager.onGlobalLayout {
            if (!isDestroyed) {
                if (mMediaFiles.isNotEmpty()) {
                    gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
                    checkSlideshowOnEnter()
                }
            }
        }

        // show the selected image asap, while loading the rest in the background to allow swiping between them. Might be needed at third party intents
        if (mMediaFiles.isEmpty() && mPath.isNotEmpty() && mDirectory != FAVORITES) {
            val filename = mPath.getFilenameFromPath()
            val folder = mPath.getParentPath()
            val type = getTypeFromPath(mPath)
            val medium = Medium(null, filename, mPath, folder, 0, 0, 0, type, 0, false, 0L, 0L)
            mMediaFiles.add(medium)
            gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
        }

        if (!isPhotoProfile && !initialPublicFolderSnapshot && !stagedInternalViewer) refreshViewPager(true)
        binding.viewPager.offscreenPageLimit = 2

        if (config.blackBackground) {
            binding.fragmentHolder.background = Color.BLACK.toDrawable()
            binding.viewPager.background = Color.BLACK.toDrawable()
        }

        if (config.hideSystemUI) {
            binding.viewPager.onGlobalLayout {
                Handler().postDelayed({
                    fragmentClicked()
                }, HIDE_SYSTEM_UI_DELAY)
            }
        }

        if (
            intent.action == "com.android.camera.action.REVIEW"
            || intent.action == MediaStore.ACTION_REVIEW
        ) {
            ensureBackgroundThread {
                if (mediaDB.getMediaFromPath(mPath).isEmpty()) {
                    val filename = mPath.getFilenameFromPath()
                    val parent = mPath.getParentPath()
                    val type = getTypeFromPath(mPath)
                    val isFavorite = favoritesDB.isFavorite(mPath)
                    val duration = if (type == TYPE_VIDEOS) getDuration(mPath) ?: 0 else 0
                    val ts = System.currentTimeMillis()
                    val medium = Medium(null, filename, mPath, parent, ts, ts, File(mPath).length(), type, duration, isFavorite, 0, 0L)
                    mediaDB.insert(medium)
                }
            }
        }
    }

    private fun getTypeFromPath(path: String): Int {
        return when {
            path.isVideoFast() -> TYPE_VIDEOS
            path.isGif() -> TYPE_GIFS
            path.isSvg() -> TYPE_SVGS
            path.isRawFast() -> TYPE_RAWS
            path.isPortrait() -> TYPE_PORTRAITS
            else -> TYPE_IMAGES
        }
    }

    private fun initBottomActions() {
        initBottomActionButtons()
        initBottomActionsLayout()
    }

    private fun initFavorites() {
        ensureBackgroundThread {
            mFavoritePaths = getFavoritePaths()
        }
    }

    private fun setupOrientation() {
        if (!mIsOrientationLocked) {
            if (config.screenRotation == ROTATE_BY_DEVICE_ROTATION) {
                requestedOrientation = SCREEN_ORIENTATION_SENSOR
            } else if (config.screenRotation == ROTATE_BY_SYSTEM_SETTING) {
                requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun updatePagerItems(
        media: List<Medium>,
        positionIndex: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>? = null
    ) {
        if (!checkFavoriteAccess()) return
        if (supportFragmentManager.isStateSaved) {
            pendingPagerPublication = PendingPagerPublication(
                media, positionIndex, mediaPublicationGeneration, FolderContentRevision.current(),
                org.fossify.gallery.helpers.PhotoContentRevision.current(), photoProfileId ?: favoriteSessionId ?: mDirectory
            )
            return
        }
        if (!isDestroyed) {
            val pagerAdapter = activePagerAdapter ?: MyPagerAdapter(this, supportFragmentManager, media, positionIndex).also {
                activePagerAdapter = it
            }
            binding.viewPager.apply {
                // must remove the listener before changing adapter, otherwise it might cause `mPos` to be set to 0
                removeOnPageChangeListener(this@ViewPagerActivity)
                if (adapter !== pagerAdapter) adapter = pagerAdapter
                else pagerAdapter.updateMedia(media, positionIndex)
                addOnPageChangeListener(this@ViewPagerActivity)
                val target = mPos.coerceIn(0, (pagerAdapter.count - 1).coerceAtLeast(0))
                if (currentItem != target) setCurrentItem(target, false)
            }
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_pager_count", pagerAdapter.count)
            if (progressiveInternalViewer) updatePhotoBoundaryWait()
            if (isPhotoProfile) {
                if (photoCatalogMode) validatePhotoPageWindow(fullCatalogPosition(mPos))
                requestPhotoPartialExpand(fullCatalogPosition(mPos))
            }
        }
    }

    private fun checkSlideshowOnEnter() {
        if (!slideshowEntryConsumed && intent.getBooleanExtra(SLIDESHOW_START_ON_ENTER, false)) {
            slideshowEntryConsumed = true
            initSlideshow()
        }
    }

    private fun initSlideshow() {
        SlideshowDialog(this) {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        prepareSlideshowStart {
            binding.viewPager.onGlobalLayout {
                if (!isFinishing && !isDestroyed &&
                    lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    if (config.slideshowAnimation == SLIDESHOW_ANIMATION_FADE) {
                        binding.viewPager.setPageTransformer(false, FadePageTransformer())
                    }

                    hideSystemUI()
                    if (!mIsFullScreen) {
                        mIsFullScreen = true
                        fullscreenToggled()
                    }
                    mRandomSlideshowStopped = false
                    mSlideshowInterval = config.slideshowInterval
                    mSlideshowMoveBackwards = config.slideshowMoveBackwards
                    mIsSlideshowActive = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleSwipe()
                }
            }
        }
    }

    private fun prepareSlideshowStart(onReady: () -> Unit) {
        if (!checkFavoriteAccess()) return
        val generation = ++slideshowUpdateGeneration
        val source = mMediaFiles
        val includeVideos = config.slideshowIncludeVideos
        val includeGifs = config.slideshowIncludeGIFs
        val random = config.slideshowRandomOrder
        mediaPublicationExecutor.queue.clear()
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val prepared = source.filter { PhotoPlaybackPolicy.canPlay(it.type, includeVideos, includeGifs) }.toMutableList()
            if (random) prepared.shuffle()
            runOnUiThread {
                if (generation != slideshowUpdateGeneration || isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                if (prepared.isEmpty()) { toast(R.string.no_media_for_slideshow); return@runOnUiThread }
                mSlideshowMedia = prepared
                mPos = if (random) 0 else prepared.indexOfFirst { it.path == getCurrentPath() }.takeIf { it >= 0 }
                    ?: mPos.coerceIn(0, prepared.lastIndex)
                updatePagerItems(prepared)
                mAreSlideShowMediaVisible = true
                onReady()
            }
        }
    }

    private fun goToNextMedium(forward: Boolean) {
        val oldPosition = binding.viewPager.currentItem
        val newPosition = if (forward) oldPosition + 1 else oldPosition - 1
        val count = binding.viewPager.adapter?.count ?: return
        if (newPosition !in 0 until count) {
            if (requestViewerBoundary(forward)) return
            slideshowEnded(forward)
        } else {
            binding.viewPager.setCurrentItem(newPosition, false)
        }
    }

    private fun animatePagerTransition(forward: Boolean) {
        val oldPosition = binding.viewPager.currentItem
        val animator = ValueAnimator.ofInt(0, binding.viewPager.width)
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                if (binding.viewPager.isFakeDragging) {
                    try {
                        binding.viewPager.endFakeDrag()
                    } catch (ignored: Exception) {
                        stopSlideshow()
                    }

                    if (binding.viewPager.currentItem == oldPosition) {
                        slideshowEnded(forward)
                    }
                }
            }

            override fun onAnimationCancel(animation: Animator) {
                binding.viewPager.endFakeDrag()
            }

            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        })

        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_SLIDE) {
            animator.interpolator = DecelerateInterpolator()
            animator.duration = SLIDESHOW_SLIDE_DURATION
        } else {
            animator.duration = SLIDESHOW_FADE_DURATION
        }

        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            var oldDragPosition = 0
            override fun onAnimationUpdate(animation: ValueAnimator) {
                if (binding.viewPager.isFakeDragging == true) {
                    val dragPosition = animation.animatedValue as Int
                    val dragOffset = dragPosition - oldDragPosition
                    oldDragPosition = dragPosition
                    try {
                        binding.viewPager.fakeDragBy(dragOffset * (if (forward) -1f else 1f))
                    } catch (e: Exception) {
                        stopSlideshow()
                    }
                }
            }
        })

        binding.viewPager.beginFakeDrag()
        animator.start()
    }

    private fun slideshowEnded(forward: Boolean) {
        if (config.loopSlideshow) {
            if (forward) {
                binding.viewPager.setCurrentItem(0, false)
            } else {
                binding.viewPager.adapter?.count?.takeIf { it > 0 }?.let { binding.viewPager.setCurrentItem(it - 1, false) }
            }
        } else {
            stopSlideshow()
            toast(R.string.slideshow_ended)
        }
    }

    private fun stopSlideshow() {
        slideshowUpdateGeneration++
        if (mIsSlideshowActive) {
            val stoppedPath = getCurrentPath()
            binding.viewPager.setPageTransformer(false, DefaultPageTransformer())
            mIsSlideshowActive = false
            showSystemUI()
            mSlideshowHandler.removeCallbacksAndMessages(null)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            mAreSlideShowMediaVisible = false

            mRandomSlideshowStopped = false
            mPos = mMediaFiles.indexOfFirst { it.path == stoppedPath }.takeIf { it >= 0 }
                ?: mPos.coerceIn(0, (mMediaFiles.size - 1).coerceAtLeast(0))
            if (mMediaFiles.isNotEmpty() && !isDestroyed) updatePagerItems(mMediaFiles.toMutableList())
        }
    }

    private fun scheduleSwipe() {
        mSlideshowHandler.removeCallbacksAndMessages(null)
        if (mIsSlideshowActive && !isFinishing && !isDestroyed) {
            if (!checkFavoriteAccess()) return
            val medium = getCurrentMedium() ?: run { stopSlideshow(); return }
            if (medium.isImage() || medium.isGIF() || medium.isPortrait()) {
                mSlideshowHandler.postDelayed({
                    if (mIsSlideshowActive && !isDestroyed) {
                        swipeToNextMedium()
                    }
                }, mSlideshowInterval * 1000L)
            } else {
                (getCurrentFragment() as? VideoFragment)?.playSlideshowVideo(medium.path)
            }
        }
    }

    private fun swipeToNextMedium() {
        if (!checkFavoriteAccess()) return
        if (config.slideshowAnimation == SLIDESHOW_ANIMATION_NONE) {
            goToNextMedium(!mSlideshowMoveBackwards)
        } else {
            animatePagerTransition(!mSlideshowMoveBackwards)
        }
    }

    private fun moveFileTo() {
        handleDeletePasswordProtection {
            checkMediaManagementAndCopy(false)
        }
    }

    private fun checkMediaManagementAndCopy(isCopyOperation: Boolean) {
        handleMediaManagementPrompt {
            copyMoveTo(isCopyOperation)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val currPath = getCurrentPath()
        if (!isCopyOperation && currPath.startsWith(recycleBinPath)) {
            toast(org.fossify.commons.R.string.moving_recycle_bin_items_disabled, Toast.LENGTH_LONG)
            return
        }

        val fileDirItems = arrayListOf(FileDirItem(currPath, currPath.getFilenameFromPath()))
        tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val newPath = "$it/${currPath.getFilenameFromPath()}"
            rescanPaths(arrayListOf(newPath)) {
                fixDateTaken(arrayListOf(newPath), false)
            }

            config.tempFolderPath = ""
            FolderContentRevision.changed()
            if (!isCopyOperation) {
                val token = activeFolderUnlockSessionId
                if (token != null && !FolderUnlockSessionManager.isMediaAuthorized(token, newPath, config::isFolderProtected)) {
                    authorizedViewerPaths = emptySet()
                    closeUnauthorizedFavorites()
                    return@tryCopyMoveFilesTo
                }
                authorizedViewerPaths = authorizedViewerPaths - currPath
                refreshViewPager()
                updateFavoritePaths(fileDirItems, it)
            }
        }
    }

    private fun toggleFileVisibility(hide: Boolean, callback: (() -> Unit)? = null) {
        val oldPath = getCurrentPath()
        toggleFileVisibility(oldPath, hide) { newPath ->
            val newFileName = newPath.getFilenameFromPath()
            binding.mediumViewerToolbar.title = newFileName

            FolderContentRevision.changed()
            org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(oldPath)
            org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(newPath)
            val current = getCurrentMedium() ?: return@toggleFileVisibility
            val replacement = current.copy(path = newPath, name = newFileName, parentPath = newPath.getParentPath())
            val updated = ArrayList(mMediaFiles)
            val mediaIndex = updated.indexOfFirst { medium -> medium.path == oldPath }
            if (mediaIndex >= 0) updated[mediaIndex] = replacement
            val slideIndex = mSlideshowMedia.indexOfFirst { medium -> medium.path == oldPath }
            if (slideIndex >= 0) mSlideshowMedia[slideIndex] = replacement

            val token = activeFolderUnlockSessionId
            if (token != null && !FolderUnlockSessionManager.isMediaAuthorized(token, newPath, config::isFolderProtected)) {
                authorizedViewerPaths = authorizedViewerPaths - oldPath
                closeUnauthorizedFavorites()
                return@toggleFileVisibility
            }
            if (token != null) authorizedViewerPaths = (authorizedViewerPaths - oldPath) + newPath
            mPath = newPath
            if (isPhotoProfile) {
                commitPhotoCatalogMutation(updated)
                refreshMenuItems()
                callback?.invoke()
                return@toggleFileVisibility
            }

            val generation = ++mediaPublicationGeneration
            mediaPublicationTask?.cancel(true)
            mediaPublicationTask = mediaPublicationExecutor.submit {
                val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(updated)
                val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(updated)
                val authorized = if (token != null) authorizedViewerPaths else emptySet()
                runOnUiThread {
                    if (generation != mediaPublicationGeneration || isFinishing || isDestroyed || !checkFavoriteAccess(newPath)) return@runOnUiThread
                    refreshUI(updated, false, positions, paths, authorized)
                }
            }

            refreshMenuItems()
            callback?.invoke()
        }
    }

    private fun rotateImage(degrees: Int) {
        val currentPath = getCurrentPath()
        if (needsStupidWritePermissions(currentPath)) {
            handleSAFDialog(currentPath) {
                if (it) {
                    rotateBy(degrees)
                }
            }
        } else {
            rotateBy(degrees)
        }
    }

    private fun rotateBy(degrees: Int) {
        getCurrentPhotoFragment()?.rotateImageViewBy(degrees)
        refreshMenuItems()
    }

    private fun toggleOrientation(orientation: Int) {
        requestedOrientation = orientation
        mIsOrientationLocked = orientation != SCREEN_ORIENTATION_UNSPECIFIED
        refreshMenuItems()
    }

    private fun getChangeOrientationIcon(): Int {
        return if (mIsOrientationLocked) {
            if (requestedOrientation == SCREEN_ORIENTATION_PORTRAIT) {
                org.fossify.commons.R.drawable.ic_orientation_portrait_vector
            } else {
                org.fossify.commons.R.drawable.ic_orientation_landscape_vector
            }
        } else {
            org.fossify.commons.R.drawable.ic_orientation_auto_vector
        }
    }

    private fun saveImageAs() {
        val currPath = getCurrentPath()
        SaveAsDialog(this, currPath, false) {
            val newPath = it
            handleSAFDialog(it) {
                if (!it) {
                    return@handleSAFDialog
                }

                toast(org.fossify.commons.R.string.saving)
                ensureBackgroundThread {
                    val photoFragment = getCurrentPhotoFragment() ?: return@ensureBackgroundThread
                    saveRotatedImageToFile(currPath, newPath, photoFragment.mCurrentRotationDegrees, true) {
                        toast(org.fossify.commons.R.string.file_saved)
                        FolderContentRevision.changed()
                        getCurrentPhotoFragment()?.mCurrentRotationDegrees = 0
                        refreshMenuItems()
                    }
                }
            }
        }
    }

    private fun createShortcut() {
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val medium = getCurrentMedium() ?: return
            val path = medium.path
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            getShortcutImage(path, drawable) {
                val intent = Intent(this, ViewPagerActivity::class.java).apply {
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, config.showAll)
                    putExtra(SHOW_FAVORITES, path == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, path == RECYCLE_BIN)
                    action = Intent.ACTION_VIEW
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val shortcut = ShortcutInfo.Builder(this, path)
                    .setShortLabel(medium.name)
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun getCurrentPhotoFragment() = getCurrentFragment() as? PhotoFragment

    private fun getPortraitPath() = intent.getStringExtra(PORTRAIT_PATH) ?: ""

    private fun isShowHiddenFlagNeeded(): Boolean {
        val file = File(mPath)
        if (file.isHidden) {
            return true
        }

        var parent = file.parentFile ?: return false
        while (true) {
            if (parent.isHidden || parent.list()?.any { it.startsWith(NOMEDIA) } == true) {
                return true
            }

            if (parent.absolutePath == "/") {
                break
            }
            parent = parent.parentFile ?: return false
        }

        return false
    }

    private fun getCurrentFragment() = (binding.viewPager.adapter as? MyPagerAdapter)?.getCurrentFragment(binding.viewPager.currentItem)

    private fun showProperties() {
        if (getCurrentMedium() != null) {
            PropertiesDialog(this, getCurrentPath(), false)
        }
    }

    private fun initBottomActionsLayout() {
        if (config.bottomActions) {
            binding.bottomActions.root.beVisible()
        } else {
            binding.bottomActions.root.beGone()
        }
    }

    private fun initBottomActionButtons() {
        val currentMedium = getCurrentMedium()
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0
        binding.bottomActions.bottomFavorite.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE != 0 && currentMedium?.getIsInRecycleBin() == false)
        binding.bottomActions.bottomFavorite.setOnLongClickListener { toast(R.string.toggle_favorite); true }
        binding.bottomActions.bottomFavorite.setOnClickListener {
            toggleFavorite()
        }

        binding.bottomActions.bottomEdit.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_EDIT != 0 && currentMedium?.isSVG() == false)
        binding.bottomActions.bottomEdit.setOnLongClickListener { toast(R.string.edit); true }
        binding.bottomActions.bottomEdit.setOnClickListener {
            openEditor(getCurrentPath())
        }

        binding.bottomActions.bottomShare.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SHARE != 0)
        binding.bottomActions.bottomShare.setOnLongClickListener { toast(org.fossify.commons.R.string.share); true }
        binding.bottomActions.bottomShare.setOnClickListener {
            shareMediumPath(getCurrentPath())
        }

        binding.bottomActions.bottomDelete.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_DELETE != 0)
        binding.bottomActions.bottomDelete.setOnLongClickListener { toast(org.fossify.commons.R.string.delete); true }
        binding.bottomActions.bottomDelete.setOnClickListener {
            checkDeleteConfirmation()
        }

        binding.bottomActions.bottomRotate.beVisibleIf(config.visibleBottomActions and BOTTOM_ACTION_ROTATE != 0 && getCurrentMedium()?.isImage() == true)
        binding.bottomActions.bottomRotate.setOnLongClickListener { toast(R.string.rotate); true }
        binding.bottomActions.bottomRotate.setOnClickListener {
            rotateImage(90)
        }

        binding.bottomActions.bottomProperties.applyColorFilter(Color.WHITE)
        binding.bottomActions.bottomProperties.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_PROPERTIES != 0)
        binding.bottomActions.bottomProperties.setOnLongClickListener { toast(org.fossify.commons.R.string.properties); true }
        binding.bottomActions.bottomProperties.setOnClickListener {
            showProperties()
        }

        binding.bottomActions.bottomChangeOrientation.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_CHANGE_ORIENTATION != 0)
        binding.bottomActions.bottomChangeOrientation.setOnLongClickListener { toast(R.string.change_orientation); true }
        binding.bottomActions.bottomChangeOrientation.setOnClickListener {
            requestedOrientation = when (requestedOrientation) {
                SCREEN_ORIENTATION_PORTRAIT -> SCREEN_ORIENTATION_LANDSCAPE
                SCREEN_ORIENTATION_LANDSCAPE -> SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> SCREEN_ORIENTATION_UNSPECIFIED
                else -> SCREEN_ORIENTATION_PORTRAIT
            }
            mIsOrientationLocked = requestedOrientation != SCREEN_ORIENTATION_UNSPECIFIED
            updateBottomActionIcons(currentMedium)
        }

        binding.bottomActions.bottomSlideshow.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SLIDESHOW != 0)
        binding.bottomActions.bottomSlideshow.setOnLongClickListener { toast(R.string.slideshow); true }
        binding.bottomActions.bottomSlideshow.setOnClickListener {
            initSlideshow()
        }

        binding.bottomActions.bottomShowOnMap.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SHOW_ON_MAP != 0)
        binding.bottomActions.bottomShowOnMap.setOnLongClickListener { toast(R.string.show_on_map); true }
        binding.bottomActions.bottomShowOnMap.setOnClickListener {
            showFileOnMap(getCurrentPath())
        }

        binding.bottomActions.bottomToggleFileVisibility.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_TOGGLE_VISIBILITY != 0)
        binding.bottomActions.bottomToggleFileVisibility.setOnLongClickListener {
            toast(if (currentMedium?.isHidden() == true) org.fossify.commons.R.string.unhide else org.fossify.commons.R.string.hide); true
        }

        binding.bottomActions.bottomToggleFileVisibility.setOnClickListener {
            currentMedium?.apply {
                toggleFileVisibility(!isHidden()) {
                    updateBottomActionIcons(currentMedium)
                }
            }
        }

        binding.bottomActions.bottomRename.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_RENAME != 0 && currentMedium?.getIsInRecycleBin() == false)
        binding.bottomActions.bottomRename.setOnLongClickListener { toast(org.fossify.commons.R.string.rename); true }
        binding.bottomActions.bottomRename.setOnClickListener {
            checkMediaManagementAndRename()
        }

        binding.bottomActions.bottomSetAs.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_SET_AS != 0)
        binding.bottomActions.bottomSetAs.setOnLongClickListener { toast(org.fossify.commons.R.string.set_as); true }
        binding.bottomActions.bottomSetAs.setOnClickListener {
            setAs(getCurrentPath())
        }

        binding.bottomActions.bottomCopy.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_COPY != 0)
        binding.bottomActions.bottomCopy.setOnLongClickListener { toast(org.fossify.commons.R.string.copy); true }
        binding.bottomActions.bottomCopy.setOnClickListener {
            checkMediaManagementAndCopy(true)
        }

        binding.bottomActions.bottomMove.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_MOVE != 0)
        binding.bottomActions.bottomMove.setOnLongClickListener { toast(org.fossify.commons.R.string.move); true }
        binding.bottomActions.bottomMove.setOnClickListener {
            moveFileTo()
        }

        binding.bottomActions.bottomResize.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_RESIZE != 0 && currentMedium?.isImage() == true)
        binding.bottomActions.bottomResize.setOnLongClickListener { toast(org.fossify.commons.R.string.resize); true }
        binding.bottomActions.bottomResize.setOnClickListener {
            resizeImage()
        }
    }

    private fun updateBottomActionIcons(medium: Medium?) {
        if (medium == null) {
            return
        }

        val favoriteIcon =
            if (medium.isFavorite) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector
        binding.bottomActions.bottomFavorite.setImageResource(favoriteIcon)

        val hideIcon =
            if (medium.isHidden()) org.fossify.commons.R.drawable.ic_unhide_vector else org.fossify.commons.R.drawable.ic_hide_vector
        binding.bottomActions.bottomToggleFileVisibility.setImageResource(hideIcon)

        binding.bottomActions.bottomRotate.beVisibleIf(config.visibleBottomActions and BOTTOM_ACTION_ROTATE != 0 && getCurrentMedium()?.isImage() == true)
        binding.bottomActions.bottomChangeOrientation.setImageResource(getChangeOrientationIcon())
    }

    private fun canAddFavoriteFromSource(path: String): Boolean {
        val snapshot = runCatching {
            FavoriteAccessSessionManager.captureSnapshot(listOf(path), applicationContext::favoriteProtection)
        }.getOrNull() ?: return false
        val entry = snapshot.entries[path] ?: return false
        return entry.locks.isEmpty() || (isPhotoProfile &&
            PhotoProfileAccessSessionManager.isActive(applicationContext, photoSessionId) &&
            entry.locks.all { (root, lock) -> config.photoSourceBindings[root] == PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash) }) || FolderUnlockSessionManager.isMediaAuthorized(
            activeFolderUnlockSessionId, path, config::isFolderProtected
        )
    }

    private fun toggleFavorite() {
        val medium = getCurrentMedium() ?: return
        val adding = !medium.isFavorite
        val sourceCheck: (String) -> Boolean = ::canAddFavoriteFromSource
        val applyChange = {
            ensureBackgroundThread {
                val changed = updateFavorite(medium.path, adding, sourceCheck)
                runOnUiThread {
                    if (changed) {
                        FolderContentRevision.changed()
                        medium.isFavorite = adding
                        if (isPhotoProfile) {
                            val includesVirtualFavorites = org.fossify.gallery.helpers.PhotoProfileRepository.of(this)
                                .state().current?.sources?.any {
                                    it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES
                                } == true
                            if (includesVirtualFavorites || !photoCatalogMode) refreshViewPager(true)
                            else commitPhotoCatalogMutation(mMediaFiles)
                        }
                        if (adding) mFavoritePaths.add(medium.path) else mFavoritePaths.remove(medium.path)
                    } else if (adding) {
                        toast(org.fossify.gallery.R.string.favorite_add_blocked_changed)
                    }
                    refreshMenuItems()
                }
            }
        }
        if (adding) {
            ensureFavoriteAddAllowed(listOf(medium.path), sourceCheck) { allowed ->
                if (allowed) applyChange()
            }
        } else {
            applyChange()
        }
    }

    private fun printFile() {
        sendPrintIntent(getCurrentPath())
    }

    private fun sendPrintIntent(path: String) {
        val printHelper = PrintHelper(this)
        printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
        printHelper.orientation = SCREEN_ORIENTATION_PORTRAIT

        try {
            val resolution = path.getImageResolution(this)
            if (resolution == null) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return
            }

            var requestedWidth = resolution.x
            var requestedHeight = resolution.y

            if (requestedWidth >= MAX_PRINT_SIDE_SIZE) {
                requestedHeight = (requestedHeight / (requestedWidth / MAX_PRINT_SIDE_SIZE.toFloat())).toInt()
                requestedWidth = MAX_PRINT_SIDE_SIZE
            } else if (requestedHeight >= MAX_PRINT_SIDE_SIZE) {
                requestedWidth = (requestedWidth / (requestedHeight / MAX_PRINT_SIDE_SIZE.toFloat())).toInt()
                requestedHeight = MAX_PRINT_SIDE_SIZE
            }

            val options = RequestOptions()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)

            Glide.with(this)
                .asBitmap()
                .load(path)
                .apply(options)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                        showErrorToast(e?.localizedMessage ?: "")
                        return false
                    }

                    override fun onResourceReady(
                        bitmap: Bitmap,
                        model: Any,
                        target: Target<Bitmap>,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        printHelper.printBitmap(path.getFilenameFromPath(), bitmap)
                        return false
                    }
                }).submit(requestedWidth, requestedHeight)
        } catch (e: Exception) {
        }
    }

    private fun restoreFile() {
        restoreRecycleBinPath(getCurrentPath()) {
            refreshViewPager()
        }
    }

    private fun resizeImage() {
        val oldPath = getCurrentPath()
        launchResizeImageDialog(oldPath)
    }

    private fun copyImageToClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java) as ClipboardManager

        val imagePath = getCurrentMedium()?.path ?: return
        val clip = ClipData.newUri(contentResolver, "Image", getFinalUriFromPath(imagePath, BuildConfig.APPLICATION_ID))
        clipboard.setPrimaryClip(clip)
    }

    private fun checkDeleteConfirmation() {
        if (getCurrentMedium() == null) {
            return
        }

        handleMediaManagementPrompt {
            if (config.isDeletePasswordProtectionOn) {
                handleDeletePasswordProtection {
                    deleteConfirmed(config.tempSkipRecycleBin)
                }
            } else if (config.tempSkipDeleteConfirmation || config.skipDeleteConfirmation) {
                deleteConfirmed(config.tempSkipRecycleBin)
            } else {
                askConfirmDelete()
            }
        }
    }

    private fun askConfirmDelete() {
        val fileDirItem = getCurrentMedium()?.toFileDirItem() ?: return
        val size = fileDirItem.getProperSize(this, countHidden = true).formatSize()
        val filename = "\"${getCurrentPath().getFilenameFromPath()}\""
        val filenameAndSize = "$filename ($size)"
        val isInRecycleBin = getCurrentMedium()!!.getIsInRecycleBin()

        val baseString = if (config.useRecycleBin && !config.tempSkipRecycleBin && !isInRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }

        val message = String.format(resources.getString(baseString), filenameAndSize)
        val showSkipRecycleBinOption = config.useRecycleBin && !isInRecycleBin

        DeleteWithRememberDialog(this, message, showSkipRecycleBinOption) { remember, skipRecycleBin ->
            config.tempSkipDeleteConfirmation = remember

            if (remember) {
                config.tempSkipRecycleBin = skipRecycleBin
            }

            deleteConfirmed(skipRecycleBin)
        }
    }

    private fun deleteConfirmed(skipRecycleBin: Boolean) {
        val currentMedium = getCurrentMedium()
        val path = currentMedium?.path ?: return
        if (getIsPathDirectory(path) || !path.isMediaFile()) {
            return
        }

        val fileDirItem = currentMedium.toFileDirItem()
        if (config.useRecycleBin && !skipRecycleBin && !getCurrentMedium()!!.getIsInRecycleBin()) {
            checkManageMediaOrHandleSAFDialogSdk30(fileDirItem.path) {
                if (!it) {
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }

                movePathsInRecycleBin(arrayListOf(path)) {
                    if (it) {
                        tryDeleteFileDirItem(fileDirItem, false, false) { deleted ->
                            if (deleted) {
                                FolderContentRevision.changed()
                                if (isPhotoProfile && !photoCatalogMode)
                                    org.fossify.gallery.helpers.PhotoContentRevision.changed()
                                hideDeletedMedium(fileDirItem.path)
                            } else {
                                refreshViewPager(true)
                            }
                        }
                    } else {
                        refreshViewPager(true)
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }
                }
            }
        } else {
            handleDeletion(fileDirItem)
        }
    }

    private fun handleDeletion(fileDirItem: FileDirItem) {
        checkManageMediaOrHandleSAFDialogSdk30(fileDirItem.path) {
            if (!it) {
                return@checkManageMediaOrHandleSAFDialogSdk30
            }

            tryDeleteFileDirItem(fileDirItem, false, true) { deleted ->
                if (deleted) {
                    FolderContentRevision.changed()
                    if (isPhotoProfile && !photoCatalogMode)
                        org.fossify.gallery.helpers.PhotoContentRevision.changed()
                    hideDeletedMedium(fileDirItem.path)
                } else {
                    refreshViewPager(true)
                }
            }
        }
    }

    /** Hide a path immediately, then prepare the replacement catalog and indexes on the publication worker. */
    /**
     * A successful local edit is a new catalog revision, not permission to
     * inherit the old path's access. Validate its visible window before the
     * replacement metadata becomes the active pager catalog.
     */
    private fun commitPhotoCatalogMutation(items: List<Medium>) {
        val revision = org.fossify.gallery.helpers.PhotoContentRevision.changed()
        invalidatePhotoCatalogForRequery()
        if (items.isEmpty()) { finish(); return }
        val updated = ArrayList(items)
        val position = mPos.coerceIn(0, updated.lastIndex)
        val radius = org.fossify.gallery.helpers.ViewerNeighborWindow.NEIGHBOR_RADIUS
        val candidates = updated.subList((position - radius).coerceAtLeast(0),
            (position + radius + 1).coerceAtMost(updated.size)).toList()
        val chosen = updated[position].path
        val request = ++photoPageAccessGeneration
        val profile = photoProfileId
        val session = photoSessionId
        val app = applicationContext
        photoPageAccessTask = try {
            ViewerAccessCoordinator.submit {
                val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                val safe = runCatching {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.validateNeighborWindow(
                        app, profile, session, candidates, chosen
                    ) { Thread.currentThread().isInterrupted }
                }.getOrNull()
                val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(updated)
                val pathIndex = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(updated)
                runOnUiThread {
                    if (request != photoPageAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    photoPageAccessTask = null
                    if (session != null &&
                        !org.fossify.gallery.helpers.PhotoProfileAccessSessionManager.isActive(app, session)) {
                        closeUnauthorizedFavorites()
                        return@runOnUiThread
                    }
                    if (safe == null || !safe.context.isCurrent(app) ||
                        safe.media.none { it.path == chosen } ||
                        revision != org.fossify.gallery.helpers.PhotoContentRevision.current()) {
                        // A move out of the selected sources may be legitimate, but
                        // it still requires a fresh complete, authorized reconciliation.
                        refreshViewPager(true)
                        return@runOnUiThread
                    }
                    photoCatalogRevision = revision
                    photoCatalogUpdating = false
                    photoNavigation.publish(false)
                    photoPageAccess.reset(revision)
                    photoPageAccess.approve(safe.media.map { it.path }, revision)
                    refreshUI(updated, true, positions, pathIndex)
                    validatePhotoPageWindow(mPos)
                    consumePhotoCatalogAdvance()
                    org.fossify.gallery.helpers.GalleryPerfTrace.end(
                        "viewer_catalog_mutation", started, updated.size)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            refreshViewPager(true)
            null
        }
    }

    private fun hideDeletedMedium(path: String) {
        org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(path)
        if (path !in mIgnoredPaths) mIgnoredPaths.add(path)
        if (isPhotoProfile && photoCatalogMode) {
            // Removal only: preserve the validated old ordering minus one item.
            // The local revision is published after fresh neighbor authorization.
            commitPhotoCatalogMutation(mMediaFiles.filter { it.path != path })
            return
        }
        val source = mMediaFiles
        val ignored = mIgnoredPaths.toHashSet()
        val request = ++mediaPublicationGeneration
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.queue.clear()
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val media = source.filterTo(ArrayList()) { it.path !in ignored }
            val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(media)
            val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(media)
            val authorized = media.mapTo(HashSet(media.size)) { it.path }
            runOnUiThread {
                if (request != mediaPublicationGeneration || isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                if (media.isEmpty()) {
                    deleteDirectoryIfEmpty()
                    finish()
                } else {
                    refreshUI(media, true, positions, paths, authorized)
                }
            }
        }
    }

    private fun isDirEmpty(media: ArrayList<Medium>): Boolean {
        return if (media.isEmpty()) {
            deleteDirectoryIfEmpty()
            finish()
            true
        } else {
            false
        }
    }

    private fun checkMediaManagementAndRename() {
        handleMediaManagementPrompt {
            renameFile()
        }
    }

    private fun renameFile() {
        val oldPath = getCurrentPath()

        val isSDOrOtgRootFolder = isAStorageRootFolder(oldPath.getParentPath()) && !oldPath.startsWith(internalStoragePath)
        if (isRPlus() && isSDOrOtgRootFolder && !isExternalStorageManager()) {
            toast(org.fossify.commons.R.string.rename_in_sd_card_system_restriction, Toast.LENGTH_LONG)
            return
        }

        RenameItemDialog(this, oldPath) {
            FolderContentRevision.changed()
            val newPath = it
            val renamedIndex = mMediaFiles.indexOfFirst { medium -> medium.path == oldPath }
            if (renamedIndex >= 0) {
                val replacement = mMediaFiles[renamedIndex].copy(
                    path = newPath,
                    name = newPath.getFilenameFromPath(),
                    parentPath = newPath.getParentPath()
                )
                val updated = ArrayList(mMediaFiles).apply { this[renamedIndex] = replacement }
                val token = activeFolderUnlockSessionId
                if (token != null && !FolderUnlockSessionManager.isMediaAuthorized(token, newPath, config::isFolderProtected)) {
                    authorizedViewerPaths = authorizedViewerPaths - oldPath
                    closeUnauthorizedFavorites()
                    return@RenameItemDialog
                }
                mPath = newPath
                if (token != null) authorizedViewerPaths = (authorizedViewerPaths - oldPath) + newPath
                if (mAreSlideShowMediaVisible || mRandomSlideshowStopped) {
                    val slideIndex = mSlideshowMedia.indexOfFirst { it.path == oldPath }
                    if (slideIndex >= 0) mSlideshowMedia[slideIndex] = replacement
                }
                if (isPhotoProfile) {
                    commitPhotoCatalogMutation(updated)
                    updateActionbarTitle()
                    ensureBackgroundThread { updateDBMediaPath(oldPath, newPath) }
                    return@RenameItemDialog
                }
                val generation = ++mediaPublicationGeneration
                mediaPublicationTask?.cancel(true)
                mediaPublicationTask = mediaPublicationExecutor.submit {
                    val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(updated)
                    val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(updated)
                    runOnUiThread {
                        if (generation != mediaPublicationGeneration || isFinishing || isDestroyed || !checkFavoriteAccess(newPath)) return@runOnUiThread
                        refreshUI(updated, false, positions, paths, authorizedViewerPaths)
                    }
                }
            }

            ensureBackgroundThread {
                updateDBMediaPath(oldPath, newPath)
            }
            updateActionbarTitle()
        }
    }

    private fun refreshViewPager(refetchPosition: Boolean = false) {
        if (!checkFavoriteAccess()) return
        org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(getCurrentPath())
        // A viewer requery may follow edits in external editors or providers.
        // Invalidating a return checkpoint is cheaper than showing stale media.
        FolderContentRevision.changed()
        if (isPhotoProfile) {
            invalidatePhotoCatalogForRequery()
            org.fossify.gallery.helpers.PhotoContentRevision.changed()
        }
        val isRandomSorting = config.getFolderSorting(mDirectory) and SORT_BY_RANDOM != 0
        if (isPhotoProfile || !isRandomSorting || isExternalIntent()) {
            val request = ++viewerLoadGeneration
            viewerLoadTask?.stopFetching()
            viewerLoadTask = GetMediaAsynctask(
                applicationContext, mDirectory, isPickImage = false, isPickVideo = false, showAll = mShowAll,
                cachedMediaFallback = if (mShowAll) mMediaFiles.map { it.copy() } else emptyList(),
                onPartialFailure = {
                    if (request == viewerLoadGeneration && !isFinishing && !isDestroyed && checkFavoriteAccess()) toast(R.string.media_load_partial_error)
                },
                favoritePathsOverride = null,
                favoriteSessionId = if (isFavoriteRequest) favoriteSessionId else null,
                photoProfileId = photoProfileId,
                photoSessionId = photoSessionId,
                photoAnchorPath = getCurrentPath(),
                onPhotoResult = { if (isPhotoProfile && request == viewerLoadGeneration) {
                    photoCatalogUpdating = false
                    consumePhotoCatalogAdvance()
                } },
                onFailure = { if (isPhotoProfile) {
                    // Keep the currently displayed selection when a recoverable
                    // rescan fails, but leave protected catalog pages fail-closed.
                    showErrorToast(it)
                } }
            ) {
                if (request == viewerLoadGeneration && !isFinishing && !isDestroyed && checkFavoriteAccess()) {
                    if (isPhotoProfile) {
                        // The loader's COMPLETE path has freshly checked the whole
                        // catalog. Old per-page approvals must never survive it.
                        photoCatalogUpdating = false
                        consumePhotoCatalogAdvance()
                        return@GetMediaAsynctask
                    }
                    gotMedia(it, refetchViewPagerPosition = refetchPosition, fullCatalog = viewerLoadTask?.scanResult?.complete == true)
                }
            }.also { it.execute() }
        }
    }

    private fun gotMedia(
        thumbnailItems: ArrayList<ThumbnailItem>,
        ignorePlayingVideos: Boolean = false,
        refetchViewPagerPosition: Boolean = false,
        fullCatalog: Boolean = false
    ) {
        if (!checkFavoriteAccess()) return
        val request = ++mediaPublicationGeneration
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.queue.clear()
        val source = thumbnailItems.toList()
        val previous = mMediaFiles
        val ignored = mIgnoredPaths.toHashSet()
        val favoriteRequest = isFavoriteRequest
        val favoriteToken = favoriteSessionId
        val priorFolderRevision = publishedFolderRevision
        val priorPhotoRevision = publishedPhotoRevision
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val media = source.asSequence().filter {
                it is Medium && it.path !in ignored &&
                    (!favoriteRequest || FavoriteAccessSessionManager.isCapturedPath(favoriteToken, it.path))
            }.map { it as Medium }.toCollection(ArrayList())
            val positionIndex = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(media)
            val pathIndex = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(media)
            val authorizedPaths = media.mapTo(HashSet(media.size)) { it.path }
            val revisionsUnchanged = priorFolderRevision == FolderContentRevision.current() &&
                priorPhotoRevision == org.fossify.gallery.helpers.PhotoContentRevision.current()
            val unchanged = revisionsUnchanged && org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.sameCatalog(previous, media)
            runOnUiThread {
                if (request != mediaPublicationGeneration || isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                if (isDirEmpty(media)) return@runOnUiThread
                if (!isPhotoProfile && progressiveInternalViewer) photoNavigation.publish(fullCatalog)
                if (unchanged) {
                    if (progressiveInternalViewer && pendingPagerPublication == null) updatePhotoBoundaryWait()
                    if (pendingPagerPublication != null) {
                        pendingPagerPublication = pendingPagerPublication?.copy(
                            media = media, positions = positionIndex, generation = request,
                            folderRevision = FolderContentRevision.current(),
                            photoRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
                        )
                    } else if (fullCatalog) {
                        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_full_list_ready", media.size)
                    }
                    return@runOnUiThread
                }
                val isPlaying = (getCurrentFragment() as? VideoFragment)?.mIsPlaying == true
                // Internal catalog expansion preserves the current fragment and player.
                if (!ignorePlayingVideos && isPlaying && !isExternalIntent() && !progressiveInternalViewer) return@runOnUiThread
                val publication = SwipeMediaPublication(
                    media, positionIndex, pathIndex, authorizedPaths, refetchViewPagerPosition,
                    fullCatalog, request, FolderContentRevision.current(),
                    org.fossify.gallery.helpers.PhotoContentRevision.current()
                )
                if (!isExternalIntent() && !mIsSlideshowActive &&
                    swipePublicationGate.deferIfMoving(publication)) {
                    org.fossify.gallery.helpers.GalleryPerfTrace.event(
                        "viewer_catalog_deferred_during_swipe", media.size)
                    return@runOnUiThread
                }
                applySwipeMediaPublication(publication)
            }
        }
    }

    /** Only apply an authorized, current result at an animation-safe point. */
    private fun applySwipeMediaPublication(publication: SwipeMediaPublication) {
        if (publication.generation != mediaPublicationGeneration || isFinishing || isDestroyed ||
            !checkFavoriteAccess()) return
        if (publication.folderRevision != FolderContentRevision.current() ||
            publication.photoRevision != org.fossify.gallery.helpers.PhotoContentRevision.current()) {
            // Never apply a stale ordering/authorization snapshot after mutations.
            if (isPhotoProfile) consumePhotoCatalogAdvance()
            else refreshViewPager(true)
            return
        }
        val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        refreshUI(
            publication.media, publication.refetchPosition, publication.positionIndex,
            publication.pathIndex, publication.authorizedPaths
        )
        if (publication.fullCatalog && photoCatalogMode && pendingPagerPublication == null) {
            validatePhotoPageWindow(mPos)
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.end(
            "viewer_main_thread_publish", started, publication.media.size)
        if (publication.fullCatalog && pendingPagerPublication == null) {
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_full_list_ready", publication.media.size)
        }
    }

    private fun refreshUI(
        media: ArrayList<Medium>,
        refetchViewPagerPosition: Boolean,
        positionIndex: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>? = null,
        pathIndex: Map<String, Int>? = null,
        authorizedPaths: Set<String>? = null
    ) {
        // Capture the currently displayed path before replacing the backing list.
        // The original click path is only a fallback; background stages must not
        // pull the user back after they have already swiped to another medium.
        val currentPathBeforeUpdate = getCurrentPath().ifEmpty { mPath }
        val previousPosition = mPos
        mMediaFiles = media
        viewerMediaPathIndex = pathIndex ?: emptyMap()
        publishedFolderRevision = FolderContentRevision.current()
        publishedPhotoRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
        if (activeFolderUnlockSessionId != null) {
            authorizedViewerPaths = authorizedPaths ?: media.mapTo(HashSet(media.size)) { it.path }
        }

        if (refetchViewPagerPosition || mPos == -1) {
            mPos = pathIndex?.let {
                org.fossify.gallery.helpers.ViewerPositionPolicy.resolve(it, currentPathBeforeUpdate, previousPosition, media.size)
            } ?: previousPosition.coerceIn(0, media.lastIndex.coerceAtLeast(0))
        }

        updateActionbarTitle()
        if (mIsSlideshowActive) {
            val generation = ++slideshowUpdateGeneration
            val latest = media
            val previousOrder = mSlideshowMedia
            val random = config.slideshowRandomOrder
            val includeVideos = config.slideshowIncludeVideos
            val includeGifs = config.slideshowIncludeGIFs
            mediaPublicationExecutor.queue.clear()
            mediaPublicationTask = mediaPublicationExecutor.submit {
                val fresh = latest.filter { PhotoPlaybackPolicy.canPlay(it.type, includeVideos, includeGifs) }
                val prepared = if (random) {
                    val byPath = fresh.associateBy { it.path }
                    val retainedPaths = HashSet<String>(previousOrder.size)
                    val retained = previousOrder.mapNotNull { old -> byPath[old.path]?.also { retainedPaths.add(it.path) } }
                    (retained + fresh.filter { it.path !in retainedPaths }).toMutableList()
                } else fresh.toMutableList()
                runOnUiThread {
                    if (generation != slideshowUpdateGeneration || !mIsSlideshowActive || isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                    val livePath = getCurrentPath()
                    mSlideshowMedia = prepared
                    if (prepared.isEmpty()) { stopSlideshow(); finish(); return@runOnUiThread }
                    mPos = prepared.indexOfFirst { it.path == livePath }.takeIf { it >= 0 }
                        ?: mPos.coerceIn(0, prepared.lastIndex)
                    updatePagerItems(prepared)
                    scheduleSwipe()
                }
            }
        } else updatePagerItems(mMediaFiles, positionIndex)

        refreshMenuItems()
        checkOrientation()
        initBottomActions()
    }

    private fun getPositionInList(items: List<Medium>): Int {
        mPos = 0
        val portraitPath = getPortraitPath()
        val portraitName = portraitPath.substringAfterLast('/')
        if (portraitPath.isNotEmpty()) {
            return items.indexOfFirst { it.name == portraitName }.takeIf { it >= 0 } ?: 0
        }
        for ((i, medium) in items.withIndex()) {
            if (medium.path.equals(mPath, true)) {
                return i
            }
        }
        return mPos
    }

    private fun deleteDirectoryIfEmpty() {
        if (isPhotoProfile) return
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mDirectory, mDirectory.getFilenameFromPath(), File(mDirectory).isDirectory)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                        scanPathRecursively(mDirectory)
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun checkOrientation() {
        if (!mIsOrientationLocked && config.screenRotation == ROTATE_BY_ASPECT_RATIO) {
            val medium = getCurrentMedium()?.copy() ?: return
            val identity = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.identity(medium)
            val request = ++orientationRequestGeneration
            orientationTask?.cancel(true)
            orientationExecutor.queue.clear()
            orientationTask = orientationExecutor.submit {
                val flipSides = runCatching {
                    val exif = ExifInterface(medium.path)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
                    orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                        orientation == ExifInterface.ORIENTATION_ROTATE_270
                }.getOrDefault(false)
                val resolution = runCatching { applicationContext.getResolution(medium.path) }.getOrNull() ?: return@submit
                val width = if (flipSides) resolution.y else resolution.x
                val height = if (flipSides) resolution.x else resolution.y
                val requested = when {
                    width > height -> SCREEN_ORIENTATION_LANDSCAPE
                    width < height -> SCREEN_ORIENTATION_PORTRAIT
                    else -> return@submit
                }
                runOnUiThread {
                    if (request != orientationRequestGeneration || isFinishing || isDestroyed ||
                        getCurrentMedium()?.let { org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.identity(it) } != identity ||
                        mIsOrientationLocked || config.screenRotation != ROTATE_BY_ASPECT_RATIO) return@runOnUiThread
                    if (requestedOrientation != requested) requestedOrientation = requested
                }
            }
        }
    }

    override fun fragmentClicked() {
        mIsFullScreen = !mIsFullScreen
        checkSystemUI()
        fullscreenToggled()
    }

    override fun videoEnded(): Boolean {
        if (mIsSlideshowActive) {
            swipeToNextMedium()
        }
        return mIsSlideshowActive
    }

    override fun isSlideShowActive() = mIsSlideshowActive

    override fun isFullScreen() = mIsFullScreen

    override fun imageRendered(path: String) {
        if (path == viewerInitialSelectedPath && !selectedImageRenderedLogged) {
            selectedImageRenderedLogged = true
            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                "viewer_selected_image_rendered", viewerSelectedImageStarted, 1
            )
        } else if (path != viewerInitialSelectedPath && renderedAdjacentPaths.add(path) && adjacentReadyStarted != 0L) {
            org.fossify.gallery.helpers.GalleryPerfTrace.end(
                "viewer_adjacent_image_rendered", adjacentReadyStarted, 1
            )
        }
    }

    override fun goToPrevItem() {
        if (binding.viewPager.currentItem == 0 && requestViewerBoundary(false)) return
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
        checkOrientation()
    }

    override fun goToNextItem() {
        if (binding.viewPager.currentItem == getCurrentMedia().lastIndex && requestViewerBoundary(true)) return
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
        checkOrientation()
    }

    override fun launchViewVideoIntent(path: String) {
        if (!checkFavoriteAccess(path)) return
        hideKeyboard()
        ensureBackgroundThread {
            val newUri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID) ?: return@ensureBackgroundThread
            val mimeType = getUriMimeType(path, newUri)
            Intent().apply {
                action = Intent.ACTION_VIEW
                setDataAndType(newUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(IS_FROM_GALLERY, true)
                putExtra(REAL_FILE_PATH, path)
                putExtra(SHOW_PREV_ITEM, binding.viewPager.currentItem != 0)
                putExtra(SHOW_NEXT_ITEM, binding.viewPager.currentItem != mMediaFiles.lastIndex)

                try {
                    startActivityForResult(this, REQUEST_VIEW_VIDEO)
                } catch (e: ActivityNotFoundException) {
                    if (!tryGenericMimeType(this, mimeType, newUri)) {
                        toast(org.fossify.commons.R.string.no_app_found)
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }
    }

    private fun checkSystemUI() {
        if (mIsFullScreen) {
            hideSystemUI()
        } else {
            stopSlideshow()
            showSystemUI()
        }
    }

    private fun fullscreenToggled() {
        binding.viewPager.adapter?.let {
            (it as MyPagerAdapter).toggleFullscreen(mIsFullScreen)
            listOf(binding.topShadow, binding.bottomActions.root, binding.mediumViewerAppbar).forEach {
                org.fossify.gallery.helpers.ViewerChromeMotion.show(it, !mIsFullScreen)
            }
        }
    }

    private fun updateActionbarTitle() {
        runOnUiThread {
            val medium = getCurrentMedium()
            if (medium != null) {
                binding.mediumViewerToolbar.title = medium.path.getFilenameFromPath()
            }
        }
    }

    private fun getCurrentMedium(): Medium? {
        return if (getCurrentMedia().isEmpty() || mPos == -1) {
            null
        } else {
            getCurrentMedia()[min(mPos, getCurrentMedia().lastIndex)]
        }
    }

    private fun getCurrentMedia() = if (mAreSlideShowMediaVisible || mRandomSlideshowStopped) mSlideshowMedia else mMediaFiles

    private fun getCurrentPath() = getCurrentMedium()?.path ?: ""

    private fun fullCatalogPosition(adapterPosition: Int): Int {
        if (!mAreSlideShowMediaVisible && !mRandomSlideshowStopped) return adapterPosition
        val path = getCurrentMedia().getOrNull(adapterPosition)?.path ?: return -1
        return viewerMediaPathIndex[path] ?: mMediaFiles.indexOfFirst { it.path == path }
    }

    /** Already verified neighbors are reused; one short worker request at a time. */
    private fun validatePhotoPageWindow(position: Int) {
        if (!photoCatalogMode || photoCatalogUpdating || position !in mMediaFiles.indices ||
            isFinishing || isDestroyed) return
        val revision = photoCatalogRevision
        if (revision != org.fossify.gallery.helpers.PhotoContentRevision.current()) {
            invalidatePhotoCatalogForRequery()
            refreshViewPager(true)
            return
        }
        val radius = org.fossify.gallery.helpers.ViewerNeighborWindow.NEIGHBOR_RADIUS
        val start = (position - radius).coerceAtLeast(0)
        val end = (position + radius + 1).coerceAtMost(mMediaFiles.size)
        val window = mMediaFiles.subList(start, end)
        val pending = photoPageAccess.missing(window.map { it.path }, revision)
        if (pending.isEmpty()) {
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_page_access_cache_hit")
            return
        }
        if (photoPageAccessTask != null) {
            // Do not cancel a useful overlapping check on every scroll callback.
            // A distant jump supersedes it; a neighbor movement queues its target.
            if (kotlin.math.abs(position - photoPageInFlightCenter) <= radius + 1) {
                photoPageQueuedCenter = position
                return
            }
            photoPageAccessGeneration++
            ViewerAccessCoordinator.cancel(photoPageAccessTask)
            photoPageAccessTask = null
            photoPageInFlight = emptySet()
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_page_access_cancelled")
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_page_access_cache_miss", pending.size)
        val candidates = window.filter { it.path in pending }.toList()
        val target = mMediaFiles[position].path.takeIf { it in pending } ?: candidates.first().path
        val request = ++photoPageAccessGeneration
        photoPageInFlight = candidates.mapTo(HashSet()) { it.path }
        photoPageInFlightCenter = position
        val profile = photoProfileId
        val session = photoSessionId
        val app = applicationContext
        photoPageAccessTask = try {
            ViewerAccessCoordinator.submit {
                val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                val safe = runCatching {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.validateNeighborWindow(
                        app, profile, session, candidates, target
                    ) { Thread.currentThread().isInterrupted }
                }.getOrNull()
                runOnUiThread {
                    if (request != photoPageAccessGeneration || !photoCatalogMode ||
                        isFinishing || isDestroyed) return@runOnUiThread
                    photoPageAccessTask = null
                    photoPageInFlight = emptySet()
                    val next = photoPageQueuedCenter
                    photoPageQueuedCenter = null
                    if (!org.fossify.gallery.helpers.PhotoProfileAccessSessionManager.isActive(app, session) &&
                        session != null) {
                        closeUnauthorizedFavorites()
                        return@runOnUiThread
                    }
                    if (revision != org.fossify.gallery.helpers.PhotoContentRevision.current() ||
                        safe == null || !safe.context.isCurrent(app)) {
                        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate(profile)
                        invalidatePhotoCatalogForRequery()
                        refreshViewPager(true)
                        return@runOnUiThread
                    }
                    val approved = safe.media.map { it.path }
                    if (target !in approved) {
                        // Failed file/protection checks are not an EOF and never
                        // grant access to the rejected metadata.
                        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.invalidate(profile)
                        invalidatePhotoCatalogForRequery()
                        refreshViewPager(true)
                        return@runOnUiThread
                    }
                    photoPageAccess.approve(approved, revision)
                    // Permission evidence is recorded immediately; placeholders
                    // are replaced only after the interactive swipe settles.
                    if (swipePublicationGate.isMoving) pendingPageAccessRefresh = true
                    else activePagerAdapter?.onAccessWindowUpdated()
                    org.fossify.gallery.helpers.GalleryPerfTrace.end(
                        "viewer_page_window_validation", started, approved.size)
                    if (next != null) validatePhotoPageWindow(next)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            photoPageAccessTask = null
            photoPageInFlight = emptySet()
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_page_access_wait")
            // A busy bounded executor must not turn a recoverable wait into an
            // authorization failure; a new scroll/idle callback can retry.
            null
        }
    }

    private fun invalidatePhotoCatalogForRequery() {
        photoNavigation.invalidate()
        viewerLoading.setLoading(false)
        photoCatalogComplete = false
        photoFallbackStarted = false
        photoLastExpandKey = null
        photoExpandGeneration++
        ViewerAccessCoordinator.cancel(photoPartialExpandTask)
        photoPartialExpandTask = null
        photoCatalogUpdateGeneration++
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        photoCatalogUpdateTask = null
        photoCatalogAdvanceQueued = false
        photoPageAccessGeneration++
        ViewerAccessCoordinator.cancel(photoPageAccessTask)
        photoPageAccessTask = null
        photoPageInFlight = emptySet()
        photoPageQueuedCenter = null
        photoCatalogUpdating = true
        photoPageAccess.reset(-1L)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        if (!isPhotoProfile || positionOffset <= 0.12f || swipePageOrigin < 0) return
        // A touch swipe always has a stable origin. Prefetch the page toward
        // which the finger is moving, once per target, rather than doing work
        // on every pixel callback (including the wrong direction on backswipes).
        val target = if (position < swipePageOrigin) position else position + 1
        if (target == swipePrefetchTarget || target !in getCurrentMedia().indices) return
        swipePrefetchTarget = target
        val fullPosition = fullCatalogPosition(target)
        validatePhotoPageWindow(fullPosition)
        requestPhotoPartialExpand(fullPosition)
    }

    override fun onPageSelected(position: Int) {
        if (!checkFavoriteAccess(getCurrentMedia().getOrNull(position)?.path)) return
        if (progressiveInternalViewer) {
            if (photoNavigation.pending?.anchor != getCurrentMedia().getOrNull(position)?.path) photoNavigation.cancel()
            viewerLoading.setLoading(photoNavigation.pending != null)
            if (photoNavigation.pending == null) finishViewerBoundaryTrace()
        }
        if (isPhotoProfile) {
            photoLastDirection = position >= mPos
            org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.focus(photoProfileId, photoSessionId,
                getCurrentMedia().getOrNull(position)?.path.orEmpty())
            if (photoNavigation.pending?.anchor != getCurrentMedia().getOrNull(position)?.path) photoNavigation.cancel()
            viewerLoading.setLoading(photoNavigation.pending != null)
            if (photoCatalogMode) validatePhotoPageWindow(fullCatalogPosition(position))
            requestPhotoPartialExpand(fullCatalogPosition(position))
        }
        if (mPos != position) {
            mPos = position
            updateActionbarTitle()
            if (swipePublicationGate.isMoving) pendingSwipeMenuRefresh = true
            else refreshMenuItems()
            if (mIsSlideshowActive) scheduleSwipe()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            swipePageOrigin = binding.viewPager.currentItem
            swipePrefetchTarget = -1
        }
        val wasMoving = swipePublicationGate.isMoving
        val ready = swipePublicationGate.onScrollMoving(state != ViewPager.SCROLL_STATE_IDLE)
        if (state != ViewPager.SCROLL_STATE_IDLE) return
        swipePageOrigin = -1
        swipePrefetchTarget = -1
        // Last-wins catalog reconciliation occurs after the natural ViewPager
        // slide, so its non-animated position correction is no longer visible.
        ready?.let(::applySwipeMediaPublication)
        if (pendingPageAccessRefresh) {
            pendingPageAccessRefresh = false
            activePagerAdapter?.onAccessWindowUpdated()
        }
        if (pendingSwipeMenuRefresh || wasMoving) {
            pendingSwipeMenuRefresh = false
            refreshMenuItems()
        }
        if (getCurrentMedium() != null) {
            if (isPhotoProfile) {
                if (photoCatalogMode) validatePhotoPageWindow(fullCatalogPosition(mPos))
                requestPhotoPartialExpand(fullCatalogPosition(mPos))
            }
            checkOrientation()
            if (mIsSlideshowActive) scheduleSwipe()
        }
    }

    private fun isExternalIntent(): Boolean {
        return !intent.getBooleanExtra(IS_FROM_GALLERY, false)
    }
}


