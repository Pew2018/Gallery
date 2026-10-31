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
class ViewPagerActivity : BaseViewerActivity(), ViewPager.OnPageChangeListener, ViewPagerFragment.FragmentListener {
    companion object {
        private const val REQUEST_VIEW_VIDEO = 1
        private const val SAVED_PATH = "current_path"
    }

    private var mPath = ""
    private var mDirectory = ""
    private var mIsFullScreen = false
    private var mPos = -1
    private var mShowAll = false
    private var initialPublicFolderSnapshot = false
    private var mIsSlideshowActive = false
    private var mPrevHashcode = 0

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
                mMediaFiles.clear()
                finish()
            }
        }
    }
    private val photoSessionId get() = intent.getStringExtra(PhotoProfileAccessSessionManager.EXTRA_SESSION)
    private var slideshowEntryConsumed = false
    private val photoProfileId get() = intent.getStringExtra(PhotoProfileMediaLoader.EXTRA_ID)
    private val isPhotoProfile get() = intent.hasExtra(PhotoProfileMediaLoader.EXTRA_ID)
    private val favoriteSessionId get() = intent.getStringExtra(FAVORITE_ACCESS_SESSION)
    private val isFavoriteRequest get() = intent.getBooleanExtra(SHOW_FAVORITES, false) || intent.hasExtra(FAVORITE_ACCESS_SESSION)
    private var viewerLoadGeneration = 0L
    private var viewerLoadTask: GetMediaAsynctask? = null
    @Volatile
    private var viewerAccessGeneration = 0L
    private var viewerAccessTask: Future<*>? = null
    private var activeFolderUnlockSessionId: String? = null
    private var ownedFolderUnlockSessionId: String? = null

    private fun closeUnauthorizedFavorites() {
        stopSlideshow()
        viewerLoadGeneration++
        viewerLoadTask?.stopFetching()
        binding.viewPager.adapter = null
        mMediaFiles.clear()
        mSlideshowMedia.clear()
        finish()
    }

    fun mayLoadFavoritePath(path: String): Boolean = checkFavoriteAccess(path)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore media fragments before the current password/session
        // flow validates the viewer request.
        super.onCreate(null)
        setContentView(binding.root)
        PhotoProfileAccessSessionManager.addListener(favoriteListenerId) { event ->
            if (event == PhotoAccessEvent.REVOKED && isPhotoProfile && photoSessionId != null) runOnUiThread {
                if (!isDestroyed && !isFinishing && isPhotoProfile && photoSessionId != null) closeUnauthorizedFavorites()
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
                    mSlideshowMedia.clear()
                    mAreSlideShowMediaVisible = false
                    mRandomSlideshowStopped = false
                    mMediaFiles.removeAll { !FavoriteAccessSessionManager.isCapturedPath(revoked, it.path) }
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
        if (!isPhotoProfile) (MediaActivity.mMedia.clone() as ArrayList<ThumbnailItem>).filterIsInstanceTo(mMediaFiles, Medium::class.java)

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

    override fun onPause() {
        super.onPause()
        stopSlideshow()
    }

    override fun onDestroy() {
        PhotoProfileAccessSessionManager.removeListener(favoriteListenerId)
        getSharedPrefs().unregisterOnSharedPreferenceChangeListener(favoriteProtectionPrefsListener)
        FavoriteAccessSessionManager.removeRevocationListener(favoriteListenerId)
        viewerAccessGeneration++
        ViewerAccessCoordinator.cancel(viewerAccessTask)
        FolderUnlockSessionManager.revokeSession(ownedFolderUnlockSessionId)
        viewerLoadGeneration++
        viewerLoadTask?.stopFetching()
        super.onDestroy()
        ColorModeHelper.resetColorMode(this)

        if (intent.extras?.containsKey(IS_VIEW_INTENT) == true) {
            config.temporarilyShowHidden = false
        }

        if (config.isThirdPartyIntent) {
            config.isThirdPartyIntent = false

            if (intent.extras == null || isExternalIntent()) {
                mMediaFiles.clear()
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
            mPrevHashcode = 0
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
        initialized: Boolean
    ) {
        val profile = photoProfileId
        val session = photoSessionId
        val clicked = mPath
        fun publish(items: List<Medium>) {
            if (request != viewerLoadGeneration || isFinishing || isDestroyed) return
            if (firstContext != null && !firstContext.isCurrent(applicationContext)) { finish(); return }
            if (!checkFavoriteAccess() || items.none { it.path == clicked }) { finish(); return }
            if (initialized) {
                gotMedia(ArrayList<ThumbnailItem>(items), refetchViewPagerPosition = true)
            } else {
                mMediaFiles = ArrayList(items)
                initContinue()
            }
        }
        if (cached != null && firstContext != null) {
            viewerAccessTask = try { ViewerAccessCoordinator.submit {
                val safe = runCatching {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.validate(applicationContext, profile, cached, clicked) {
                        Thread.currentThread().isInterrupted
                    }
                }.getOrNull()
                runOnUiThread {
                    if (request != viewerLoadGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (safe == null || !safe.context.isCurrent(applicationContext)) { finish(); return@runOnUiThread }
                    publish(safe.media)
                }
            } } catch (_: java.util.concurrent.RejectedExecutionException) { finish(); null }
        } else {
            viewerLoadTask = GetMediaAsynctask(applicationContext, PhotoProfileMediaLoader.PATH,
                showAll = false, photoProfileId = profile, photoSessionId = session,
                onFailure = { if (request == viewerLoadGeneration && !isFinishing && !isDestroyed) finish() }
            ) { items -> publish(items.filterIsInstance<Medium>()) }
                .also { it.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR) }
        }
    }

    private fun authenticateViewerRequest() {
        showSystemUI()
        if (isPhotoProfile) {
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
            val selected = cached?.firstOrNull { it.path == clicked } ?: Medium(
                null, clicked.getFilenameFromPath(), clicked, clicked.getParentPath(),
                0, 0, 0, getTypeFromPath(clicked), 0, false, 0L, 0L
            )
            viewerAccessTask = try { ViewerAccessCoordinator.submit {
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
                        binding.viewPager.postOnAnimation {
                            org.fossify.gallery.helpers.GalleryPerfTrace.end("photo_viewer_selected_ready", started, 1)
                        }
                    }
                    completePhotoViewerLoad(request, cached, first?.context, first != null)
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
            val request = ++viewerAccessGeneration
            val requestedMedia = mMediaFiles.toList()
            submitViewerAccess(request) {
                val batch = org.fossify.gallery.helpers.FavoriteAccessPolicy.validateItems(
                    favoriteSessionId,
                    requestedMedia,
                    { it.path },
                    requiredPaths = listOf(mPath)
                )
                runOnUiThread {
                    if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    if (batch == null || !FavoriteAccessSessionManager.isValidationCurrent(batch.validation)) {
                        closeUnauthorizedFavorites()
                        return@runOnUiThread
                    }
                    mMediaFiles = batch.items
                    initContinue()
                }
            }
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
                    val cached = runCatching {
                        org.fossify.gallery.helpers.FolderViewerSnapshotStore.take(
                            app, snapshotId, folder, path
                        )
                    }.getOrNull()
                    runOnUiThread {
                        if (request != viewerAccessGeneration || isFinishing || isDestroyed) return@runOnUiThread
                        if (cached != null) {
                            mMediaFiles = cached
                            initialPublicFolderSnapshot = true
                        }
                        continueAfterFailedFolderCapability()
                    }
                }
            }
        }
    }

    private fun unlockFolderAndFilter(protectedFolder: String) {
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
                initContinue()
            }
        }
    }

    private fun submitViewerAccess(request: Long, work: () -> Unit) {
        ViewerAccessCoordinator.cancel(viewerAccessTask)
        viewerAccessTask = try {
            ViewerAccessCoordinator.submit {
                if (request == viewerAccessGeneration && !Thread.currentThread().isInterrupted) work()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            if (request == viewerAccessGeneration) finish()
            null
        }
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

        if (!isPhotoProfile && !initialPublicFolderSnapshot) refreshViewPager(true)
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

    private fun updatePagerItems(media: MutableList<Medium>) {
        if (!checkFavoriteAccess()) return
        val pagerAdapter = MyPagerAdapter(this, supportFragmentManager, media)
        if (!isDestroyed) {
            pagerAdapter.shouldInitFragment = mPos < 5
            binding.viewPager.apply {
                // must remove the listener before changing adapter, otherwise it might cause `mPos` to be set to 0
                removeOnPageChangeListener(this@ViewPagerActivity)
                adapter = pagerAdapter
                pagerAdapter.shouldInitFragment = true
                addOnPageChangeListener(this@ViewPagerActivity)
                currentItem = mPos
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
        if (getMediaForSlideshow()) {
            binding.viewPager.onGlobalLayout {
                if (!isDestroyed) {
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

    private fun goToNextMedium(forward: Boolean) {
        val oldPosition = binding.viewPager.currentItem
        val newPosition = if (forward) oldPosition + 1 else oldPosition - 1
        if (newPosition == -1 || newPosition > binding.viewPager.adapter!!.count - 1) {
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
                binding.viewPager.setCurrentItem(binding.viewPager.adapter!!.count - 1, false)
            }
        } else {
            stopSlideshow()
            toast(R.string.slideshow_ended)
        }
    }

    private fun stopSlideshow() {
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
        if (mIsSlideshowActive) {
            if (getCurrentMedium()!!.isImage() || getCurrentMedium()!!.isGIF() || getCurrentMedium()!!.isPortrait()) {
                mSlideshowHandler.postDelayed({
                    if (mIsSlideshowActive && !isDestroyed) {
                        swipeToNextMedium()
                    }
                }, mSlideshowInterval * 1000L)
            } else {
                (getCurrentFragment() as? VideoFragment)!!.playVideo()
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

    private fun getMediaForSlideshow(): Boolean {
        if (!checkFavoriteAccess()) return false
        mSlideshowMedia = mMediaFiles.filter {
            PhotoPlaybackPolicy.canPlay(it.type, config.slideshowIncludeVideos, config.slideshowIncludeGIFs)
        }.toMutableList()

        if (config.slideshowRandomOrder) {
            mSlideshowMedia.shuffle()
            mPos = 0
        } else {
            mPath = getCurrentPath()
            mPos = getPositionInList(mSlideshowMedia)
        }

        return if (mSlideshowMedia.isEmpty()) {
            toast(R.string.no_media_for_slideshow)
            false
        } else {
            updatePagerItems(mSlideshowMedia)
            mAreSlideShowMediaVisible = true
            true
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
                refreshViewPager()
                updateFavoritePaths(fileDirItems, it)
            }
        }
    }

    private fun toggleFileVisibility(hide: Boolean, callback: (() -> Unit)? = null) {
        toggleFileVisibility(getCurrentPath(), hide) {
            val newFileName = it.getFilenameFromPath()
            binding.mediumViewerToolbar.title = newFileName

            if (isPhotoProfile) org.fossify.gallery.helpers.PhotoContentRevision.changed()
            FolderContentRevision.changed()
            getCurrentMedium()!!.apply {
                name = newFileName
                path = it
                getCurrentMedia()[mPos] = this
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
                        if (isPhotoProfile) refreshViewPager()
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

                if (isPhotoProfile) org.fossify.gallery.helpers.PhotoContentRevision.changed()
                FolderContentRevision.changed()
                mIgnoredPaths.add(fileDirItem.path)
                val media = mMediaFiles.filter { !mIgnoredPaths.contains(it.path) } as ArrayList<Medium>
                if (media.isNotEmpty()) {
                    runOnUiThread {
                        refreshUI(media, false)
                    }
                }

                if (media.size == 1) {
                    onPageSelected(0)
                }

                movePathsInRecycleBin(arrayListOf(path)) {
                    if (it) {
                        tryDeleteFileDirItem(fileDirItem, false, false) {
                            mIgnoredPaths.remove(fileDirItem.path)
                            if (media.isEmpty()) {
                                deleteDirectoryIfEmpty()
                                finish()
                            }
                        }
                    } else {
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

            if (isPhotoProfile) org.fossify.gallery.helpers.PhotoContentRevision.changed()
            FolderContentRevision.changed()
            mIgnoredPaths.add(fileDirItem.path)
            val media = mMediaFiles.filter { !mIgnoredPaths.contains(it.path) } as ArrayList<Medium>
            if (media.isNotEmpty()) {
                runOnUiThread {
                    refreshUI(media, false)
                }
            }

            if (media.size == 1) {
                onPageSelected(0)
            }

            tryDeleteFileDirItem(fileDirItem, false, true) {
                mIgnoredPaths.remove(fileDirItem.path)
                if (media.isEmpty()) {
                    deleteDirectoryIfEmpty()
                    finish()
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
            getCurrentMedia().getOrNull(mPos)?.apply {
                path = it
                name = it.getFilenameFromPath()
            }

            ensureBackgroundThread {
                updateDBMediaPath(oldPath, it)
            }
            updateActionbarTitle()
        }
    }

    private fun refreshViewPager(refetchPosition: Boolean = false) {
        if (!checkFavoriteAccess()) return
        // A viewer requery may follow edits in external editors or providers.
        // Invalidating a return checkpoint is cheaper than showing stale media.
        FolderContentRevision.changed()
        if (isPhotoProfile) org.fossify.gallery.helpers.PhotoContentRevision.changed()
        val isRandomSorting = config.getFolderSorting(mDirectory) and SORT_BY_RANDOM != 0
        if (isPhotoProfile || !isRandomSorting || isExternalIntent()) {
            val request = ++viewerLoadGeneration
            viewerLoadTask?.stopFetching()
            viewerLoadTask = GetMediaAsynctask(
                applicationContext, mDirectory, isPickImage = false, isPickVideo = false, showAll = mShowAll,
                favoritePathsOverride = null,
                favoriteSessionId = if (isFavoriteRequest) favoriteSessionId else null,
                photoProfileId = photoProfileId,
                photoSessionId = photoSessionId,
                onFailure = { if (isPhotoProfile) finish() }
            ) {
                if (request == viewerLoadGeneration && !isFinishing && !isDestroyed && checkFavoriteAccess()) {
                    gotMedia(it, refetchViewPagerPosition = refetchPosition)
                }
            }.also { it.execute() }
        }
    }

    private fun gotMedia(thumbnailItems: ArrayList<ThumbnailItem>, ignorePlayingVideos: Boolean = false, refetchViewPagerPosition: Boolean = false) {
        if (!checkFavoriteAccess()) return
        val media = thumbnailItems.asSequence().filter {
            it is Medium && !mIgnoredPaths.contains(it.path) &&
                (!isFavoriteRequest || FavoriteAccessSessionManager.isCapturedPath(favoriteSessionId, it.path))
        }.map { it as Medium }.toMutableList() as ArrayList<Medium>

        if (isDirEmpty(media) || media.hashCode() == mPrevHashcode) {
            return
        }

        val isPlaying = (getCurrentFragment() as? VideoFragment)?.mIsPlaying == true
        if (!ignorePlayingVideos && isPlaying && !isExternalIntent()) {
            return
        }

        refreshUI(media, refetchViewPagerPosition)
    }

    private fun refreshUI(media: ArrayList<Medium>, refetchViewPagerPosition: Boolean) {
        mPrevHashcode = media.hashCode()
        mMediaFiles = media

        if (refetchViewPagerPosition || mPos == -1) {
            mPos = getPositionInList(media)
            if (mPos == -1) {
                min(mPos, media.lastIndex)
            }
        }

        updateActionbarTitle()
        if (mIsSlideshowActive) {
            val currentPath = getCurrentPath()
            val fresh = mMediaFiles.filter { PhotoPlaybackPolicy.canPlay(it.type, config.slideshowIncludeVideos, config.slideshowIncludeGIFs) }
            mSlideshowMedia = if (config.slideshowRandomOrder) {
                val byPath = fresh.associateBy { it.path }
                (mSlideshowMedia.mapNotNull { byPath[it.path] } + fresh.filter { candidate -> mSlideshowMedia.none { it.path == candidate.path } }).toMutableList()
            } else fresh.toMutableList()
            if (mSlideshowMedia.isEmpty()) { stopSlideshow(); finish(); return }
            mPos = mSlideshowMedia.indexOfFirst { it.path == currentPath }.takeIf { it >= 0 }
                ?: mPos.coerceIn(0, mSlideshowMedia.lastIndex)
            updatePagerItems(mSlideshowMedia)
            scheduleSwipe()
        } else updatePagerItems(mMediaFiles.toMutableList())

        refreshMenuItems()
        checkOrientation()
        initBottomActions()
    }

    private fun getPositionInList(items: MutableList<Medium>): Int {
        mPos = 0
        for ((i, medium) in items.withIndex()) {
            val portraitPath = getPortraitPath()
            if (portraitPath != "") {
                val portraitPaths = File(portraitPath).parentFile?.list()
                if (portraitPaths != null) {
                    for (path in portraitPaths) {
                        if (medium.name == path) {
                            return i
                        }
                    }
                }
            } else if (medium.path.equals(mPath, true)) {
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
            var flipSides = false
            try {
                val pathToLoad = getCurrentPath()
                val exif = ExifInterface(pathToLoad)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
                flipSides = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
            } catch (e: Exception) {
            }
            val resolution = applicationContext.getResolution(getCurrentPath()) ?: return
            val width = if (flipSides) resolution.y else resolution.x
            val height = if (flipSides) resolution.x else resolution.y
            if (width > height) {
                requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
            } else if (width < height) {
                requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
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

    override fun goToPrevItem() {
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, false)
        checkOrientation()
    }

    override fun goToNextItem() {
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, false)
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
            val newAlpha = if (mIsFullScreen) 0f else 1f
            binding.topShadow.animate().alpha(newAlpha).start()
            binding.bottomActions.root.animate().alpha(newAlpha).withStartAction {
                binding.bottomActions.root.beVisible()
            }.withEndAction {
                binding.bottomActions.root.beVisibleIf(newAlpha == 1f)
            }.start()

            binding.mediumViewerAppbar.animate().alpha(newAlpha).withStartAction {
                binding.mediumViewerAppbar.beVisible()
            }.withEndAction {
                binding.mediumViewerAppbar.beVisibleIf(newAlpha == 1f)
            }.start()
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

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        if (!checkFavoriteAccess(getCurrentMedia().getOrNull(position)?.path)) return
        if (mPos != position) {
            mPos = position
            updateActionbarTitle()
            refreshMenuItems()
            scheduleSwipe()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_IDLE && getCurrentMedium() != null) {
            checkOrientation()
        }
    }

    private fun isExternalIntent(): Boolean {
        return !intent.getBooleanExtra(IS_FROM_GALLERY, false)
    }
}


