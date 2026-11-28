package org.fossify.gallery.activities

import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.PhotoAccessEvent
import org.fossify.gallery.helpers.PhotoPlaybackPolicy

import org.fossify.gallery.helpers.FOLDER_UNLOCK_SESSION
import org.fossify.gallery.helpers.PhotoTransitionCompletionGate
import org.fossify.gallery.helpers.PhotoTransitionCompletionGate.Stage
import org.fossify.gallery.helpers.ViewerEntryDiagnostics
import org.fossify.gallery.helpers.GalleryDiagnosticLogger
import org.fossify.gallery.helpers.FrameEvidence
import org.fossify.gallery.helpers.ViewerReturnDiagnostics
import org.fossify.gallery.helpers.ViewerReturnProgress
import org.fossify.gallery.helpers.ViewerReturnVisualClock
import org.fossify.gallery.helpers.ViewerBarOwner
import org.fossify.gallery.helpers.ViewerSystemBarChoreography
import org.fossify.gallery.helpers.MediaTransitionHandoff
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator
import org.fossify.gallery.helpers.MediaReturnToolbarClip
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Resource
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.State
import org.fossify.gallery.helpers.MediaTransitionSessionCoordinator.Token
import org.fossify.gallery.helpers.PhotoViewerTransition
import org.fossify.gallery.helpers.StaticPhotoEntryView
import org.fossify.gallery.helpers.StaticPhotoEntryGeometry
import org.fossify.gallery.helpers.StaticPhotoViewGeometry
import org.fossify.gallery.helpers.MediaTransitionGeometry
import org.fossify.gallery.helpers.OxygenClassicMotion
import org.fossify.gallery.extensions.favoriteProtection
import org.fossify.gallery.helpers.FAVORITE_ACCESS_SESSION
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import org.fossify.gallery.helpers.FolderUnlockSessionManager
import org.fossify.gallery.helpers.FolderViewerSnapshotStore
import org.fossify.gallery.helpers.FolderScanObservation
import org.fossify.gallery.helpers.DirectoryAccessState
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.SharedElementCallback
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
import androidx.core.view.doOnPreDraw
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
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
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getSharedPrefs
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
import org.fossify.gallery.extensions.openGalleryBasicEditor
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
import org.fossify.gallery.helpers.ViewerMediaActionVisibility
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
    private lateinit var transitionSession: MediaTransitionSessionCoordinator
    private lateinit var entryLeases: MediaTransitionSessionCoordinator.Leases
    private lateinit var returnLeases: MediaTransitionSessionCoordinator.Leases
    private var entryReleaseDraw: androidx.core.view.OneShotPreDrawListener? = null
    private var entryDiagnosticDraw: androidx.core.view.OneShotPreDrawListener? = null
    private val mediaTransitionRunning get() = ::transitionSession.isInitialized &&
        (transitionSession.entryActive || transitionSession.returnOperation?.let(transitionSession::isReturning) == true)
    private var fadeOnlyPhotoFallback = false
    private var visualTitlePending = false
    private var interactiveDismissSurface: View? = null
    private var interactiveDismissPage: ViewPagerFragment? = null
    private var interactiveDismissPath: String? = null
    private var interactiveDismissDragActive = false
    private var interactiveDismissSettleAnimator: ValueAnimator? = null
    private var interactiveDismissProgress = 0f
    // Frame inherited by the current system back gesture; its progress restarts at zero.
    private var systemBackResumeProgress = 0f
    private var interactiveDismissDeltaXSlope = 0f
    private var interactiveDismissDeltaYSlope = 0f
    private var interactiveDismissGestureBaseX = 0f
    private var interactiveDismissGestureBaseY = 0f
    private var interactiveDismissGeneration = 0L
    private var photoTransitionProgressFrameCount = 0
    private var photoTransitionProgress = 0f
    private val photoTransitionProgressSamples = ArrayList<Float>(32)
    private val photoTransitionProgressTimes = ArrayList<Long>(32)
    private val photoTransitionFrameSamples = ArrayList<Pair<Float, android.graphics.Rect>>(32)
    private var photoTransitionMappedView: View? = null
    fun isMediaTransitionRunning() = mediaTransitionRunning || interactiveDismissDragActive

    fun shouldKeepPhotoCanvasTransparent() = isMediaTransitionRunning() || finishAfterSharedTransitionInProgress

    override var viewerSystemBarProgress = 1f
        private set
    override var viewerStatusSurfaceVisible = true
        private set

    /**
     * Who may paint this window's system bar surfaces, and on which clock.
     *
     * The previous boolean hold froze the surfaces for the whole committed return,
     * which parked them on the release blend (the exported log recorded
     * `heldProgress=0.49627578` and five other mid-progress values) and then jumped to
     * the Grid's colour when the window was removed. The choreography keeps the single
     * writer, but lets the settled return continue from the released frame on the same
     * remainder the scrim uses, so the last frame is already the page surface.
     */
    private val viewerBars = ViewerSystemBarChoreography()
    private var viewerBarToken = 0L

    private fun logSystemBarOwner(state: String, reason: String) {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "Viewer", "viewer_system_bar_owner_changed",
            viewerDiagnosticRequestId,
            mapOf(
                "transitionSessionId" to viewerReturnId,
                "owner" to viewerBars.owner.name,
                "state" to state,
                "reason" to reason,
                "statusBarVisible" to viewerStatusSurfaceVisible,
                "heldProgress" to viewerBars.releaseProgress,
                "progress" to viewerSystemBarProgress,
                "elapsedRealtimeMs" to android.os.SystemClock.elapsedRealtime(),
            )
        )
    }

    private fun logSystemBarReturnCompleted() {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "Viewer", "viewer_system_bar_return_completed",
            viewerDiagnosticRequestId,
            mapOf(
                "transitionSessionId" to viewerReturnId,
                "owner" to ViewerBarOwner.PAGE_HANDOFF.name,
                "heldProgress" to viewerBars.releaseProgress,
                "statusBarVisible" to viewerStatusSurfaceVisible,
                "elapsedRealtimeMs" to android.os.SystemClock.elapsedRealtime(),
            )
        )
    }

    private fun setSharedChromeAlpha(progress: Float) {
        val alpha = if (mIsFullScreen) 0f else progress
        listOf(binding.topShadow, binding.bottomActions.root, binding.mediumViewerAppbar).forEach {
            it.alpha = alpha
        }
        (getCurrentFragment() as? VideoFragment)?.setSharedChromeAlpha(alpha)
    }

    private fun renderSharedViewerLayers(progress: Float) {
        setSharedChromeAlpha(progress)
        renderSharedViewerCanvas(progress)
    }

    private fun renderSharedViewerCanvas(progress: Float, scrimAlpha: Float = progress) {
        binding.viewerTransitionScrim.alpha = scrimAlpha
        // One writer per frame is decided by [viewerBars]: entry and gesture frames may
        // blend, a committed return's own layers own the bars, and a handed-over window
        // writes nothing at all (the teardown restore used to snap them back to the
        // viewer surface there).
        val barProgress = viewerBars.canvasBarProgress(progress) ?: return
        writeViewerBarProgress(barProgress)
    }

    private fun writeViewerBarProgress(progress: Float) {
        if (viewerSystemBarProgress == progress) return
        viewerSystemBarProgress = progress
        updateViewerSystemBars()
    }

    private fun renderPhotoTransitionProgress(entry: PhotoTransitionCompletionGate, progress: Float) {
        if (!isCurrentEntry(entry)) return
        if (entry.startMotion()) {
            check(transitionSession.beginMotion(transitionSession.entry))
            photoSharedElementHandler.removeCallbacks(entryWatchdog)
            logMediaEntry("shared_animation_started")
        }
        if (entry.stage != Stage.SHARED) return
        photoTransitionProgressFrameCount++
        photoTransitionProgress = progress.coerceIn(0f, 1f)
        renderSharedViewerLayers(photoTransitionProgress)
        if (BuildConfig.DEBUG) {
            if (photoTransitionProgressSamples.size == 64) photoTransitionProgressSamples.removeAt(0)
            photoTransitionProgressSamples += photoTransitionProgress
            if (photoTransitionProgressTimes.size == 64) photoTransitionProgressTimes.removeAt(0)
            photoTransitionProgressTimes += android.os.SystemClock.uptimeMillis()
            // Keep diagnostics tied to the exact mapped surface instead of
            // re-resolving it by path while the fragment renderer is binding.
            val sharedSurface = photoTransitionMappedView
            val bounds = sharedSurface?.let(::mappedTransitionSurfaceBounds)
            if (bounds != null) {
                if (photoTransitionFrameSamples.size == 64) photoTransitionFrameSamples.removeAt(0)
                photoTransitionFrameSamples += photoTransitionProgress to bounds
            }
        }
    }

    private fun takeOverReturnChrome(page: ViewPagerFragment?) {
        val root = page?.view
        val standardChrome = listOf(binding.topShadow, binding.bottomActions.root, binding.mediumViewerAppbar) +
            listOfNotNull(root?.findViewById(R.id.video_play_outline), root?.findViewById(R.id.bottom_video_time_holder),
                root?.findViewById(R.id.video_details))
        val pageOverlays = listOf(R.id.photo_details, R.id.photo_portrait_stripe_wrapper, R.id.panorama_outline,
            R.id.slide_info, R.id.playback_speed_pill, R.id.bottom_actions_dummy)
            .mapNotNull { root?.findViewById<View>(it) }
        returnOverlayAlphas = (standardChrome + pageOverlays + binding.photoBoundaryLoading).associateWith {
            org.fossify.gallery.helpers.ViewerChromeMotion.cancel(it)
            it.alpha
        }
    }

    private fun renderSharedReturnLayers(remaining: Float) {
        val previousAlpha = binding.viewerTransitionScrim.alpha
        val scrimAlpha = returnScrimAlpha * remaining
        returnOverlayAlphas.forEach { (view, alpha) -> view.alpha = alpha * remaining }
        hideViewerToolbarWhenGridIsExposed(scrimAlpha)
        binding.viewerTransitionScrim.alpha = scrimAlpha
        // The bars continue from the released frame on the same remainder as the scrim,
        // so the surface reaches the page colour on the frame the Viewer content is gone.
        viewerBars.returnBarProgress(viewerBarToken, remaining)?.let(::writeViewerBarProgress)
        val alpha = binding.viewerTransitionScrim.alpha
        if (finishAfterSharedTransitionInProgress && !sharedReturnScrimFadeReported && alpha < previousAlpha &&
            ViewerEntryDiagnostics.enabled(viewerReturnId)) {
            sharedReturnScrimFadeReported = true
            ViewerReturnDiagnostics.event(viewerReturnId, "viewer_return_scrim_fade") {
                mapOf("launchId" to intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L),
                    "fromAlpha" to previousAlpha, "alpha" to alpha, "remaining" to remaining)
            }
        }
    }

    private fun renderSharedReturnProgress(operation: Token, progress: Float) {
        if (!transitionSession.isReturning(operation) ||
            sharedReturnTerminal || isFinishing || isDestroyed) return
        if (!sharedReturnAnimatorStarted) {
            sharedReturnAnimatorStarted = true
            ViewerReturnDiagnostics.event(viewerReturnId, "viewer_animator_started")
        }
        renderSharedReturnLayers(sharedReturnVisualClock.progress(progress))
    }

    private fun onSharedReturnTerminal(operation: Token, cancelled: Boolean) {
        if (isDestroyed || !transitionSession.finishViewerMotion(operation, cancelled)) return
        if (cancelled) {
            // The Viewer keeps the screen, so it takes the bars back at its steady
            // appearance. Only the bar appearance is touched here - the scrim and the
            // layer alphas still belong to the frame the gesture left.
            viewerBars.terminal(viewerBarToken, cancelled = true)?.let(::writeViewerBarProgress)
            viewerBars.settleViewer()
            logSystemBarOwner("restored", "return_cancelled")
            // A cancelled shared return must not leave finishAfterSharedTransition stuck
            // and the pager input lock held; restore VIEWING so the next back can close.
            // Run clip/animator cleanup; do not run PROXY/CHROME finish-time teardown —
            // the Viewer still owns the screen and those would detach the proxy.
            if (!isFinishing && !isDestroyed) {
                returnLeases.release(operation, Resource.INPUT)
                returnLeases.release(operation, Resource.CALLBACKS)
                returnLeases.release(operation, Resource.RETURN_TOOLBAR_CLIP)
                returnLeases.release(operation, Resource.INTERACTIVE_ANIMATOR)
                returnLeases.discardRemaining()
                transitionSession.abortCancelledReturn(operation)
                setEnterSharedElementCallback(null as SharedElementCallback?)
            }
        } else {
            // The settled return already wrote the page surface on its last frame; this
            // only covers a return that never produced one (fallback, no animator).
            viewerBars.terminal(viewerBarToken, cancelled = false)?.let(::writeViewerBarProgress)
            logSystemBarReturnCompleted()
        }
        ViewerReturnDiagnostics.event(viewerReturnId, if (cancelled) "viewer_transition_cancel" else "viewer_transition_end") {
            val proxy = sharedReturnView
            mapOf("animatorStarted" to sharedReturnAnimatorStarted, "finishing" to isFinishing,
                "launchId" to intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L),
                "proxyBounds" to proxy?.let(::mappedTransitionSurfaceBounds)?.toShortString(),
                "proxyAlpha" to proxy?.alpha,
                "proxyVisibleOnScreen" to proxy?.let(ViewerReturnDiagnostics::visibleOnScreen),
                "proxyDrawableSize" to ViewerReturnDiagnostics.drawableSize(proxy as? ImageView),
                "proxyScaleType" to (proxy as? ImageView)?.scaleType,
                "proxyMatrix" to (proxy as? ImageView)?.imageMatrix?.toShortString())
        }
        // The transition already rendered progress 1 as its final frame. Commit the
        // terminal state only when it is still missing, and never when no animator
        // drove the layers (the fallback owns that path).
        val terminalFrameRequired = sharedReturnVisualClock.terminal(cancelled)
        if (terminalFrameRequired && sharedReturnAnimatorStarted) renderSharedReturnLayers(0f)
        // Android still needs the proxy at its destination until the grid can draw it.
        // Release the Viewer leases once the framework calls finish or destroys this window.
    }

    private fun mappedTransitionSurfaceBounds(view: View): android.graphics.Rect? {
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return android.graphics.Rect().takeIf { view.getGlobalVisibleRect(it) }
        }
        val transformed = android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val matrix = android.graphics.Matrix()
        view.transformMatrixToGlobal(matrix)
        matrix.mapRect(transformed)
        if (transformed.isEmpty) return null
        return android.graphics.Rect(
            kotlin.math.floor(transformed.left).toInt(),
            kotlin.math.floor(transformed.top).toInt(),
            kotlin.math.ceil(transformed.right).toInt(),
            kotlin.math.ceil(transformed.bottom).toInt(),
        )
    }

    private fun setSharedTransitionCanvasTransparent() {
        binding.root.setBackgroundColor(Color.TRANSPARENT)
        binding.viewPager.setBackgroundColor(Color.TRANSPARENT)
        (getCurrentFragment() as? PhotoFragment)?.setTransitionCanvasTransparent(true)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun restoreBlackViewerCanvas() {
        binding.root.setBackgroundColor(Color.BLACK)
        binding.viewPager.setBackgroundColor(Color.BLACK)
        (getCurrentFragment() as? PhotoFragment)?.setTransitionCanvasTransparent(false)
        binding.viewerTransitionScrim.animate().cancel()
        binding.viewerTransitionScrim.visibility = View.GONE
        window.setBackgroundDrawableResource(android.R.color.black)
        window.decorView.setBackgroundColor(Color.BLACK)
    }

    private fun renderInteractiveDismissProgress(progress: Float) {
        val surface = interactiveDismissSurface ?: return
        val p = progress.coerceIn(0f, 1f)
        val scale = 1f - 0.34f * p
        surface.scaleX = scale
        surface.scaleY = scale
        surface.translationX = interactiveDismissDeltaXSlope * p
        surface.translationY = interactiveDismissDeltaYSlope * p
        setSharedChromeAlpha(1f - p)
        hideViewerToolbarWhenGridIsExposed(1f - p)
        renderSharedViewerCanvas(1f - p)
        interactiveDismissProgress = p
    }

    private fun hideViewerToolbarWhenGridIsExposed(scrimAlpha: Float) {
        if (scrimAlpha >= 1f) return
        val operation = transitionSession.returnOperation ?: return
        if (transitionSession.gridToolbarBottomOnScreen(operation) != null) binding.mediumViewerAppbar.alpha = 0f
    }

    private fun protectGridToolbarDuringReturn(operation: Token, proxy: View) {
        val clip = MediaReturnToolbarClip(proxy)
        val observer = proxy.viewTreeObserver
        val beforeDraw = android.view.ViewTreeObserver.OnPreDrawListener {
            if (transitionSession.isReturning(operation)) {
                val bottom = if (binding.viewerTransitionScrim.alpha < 1f)
                    transitionSession.gridToolbarBottomOnScreen(operation) else null
                clip.update(bottom)
            }
            true
        }
        if (!returnLeases.acquire(operation, Resource.RETURN_TOOLBAR_CLIP) {
                val activeObserver = if (observer.isAlive) observer else proxy.viewTreeObserver
                if (activeObserver.isAlive) activeObserver.removeOnPreDrawListener(beforeDraw)
                clip.update(null)
            }) return
        observer.addOnPreDrawListener(beforeDraw)
    }

    private fun clearInteractiveDismissDrag() {
        interactiveDismissSettleAnimator?.removeAllUpdateListeners()
        interactiveDismissSettleAnimator?.removeAllListeners()
        interactiveDismissSettleAnimator?.cancel()
        interactiveDismissSettleAnimator = null
        interactiveDismissGeneration++
        interactiveDismissSurface = null
        interactiveDismissPage = null
        interactiveDismissPath = null
        interactiveDismissDragActive = false
        interactiveDismissProgress = 0f
        interactiveDismissDeltaXSlope = 0f
        interactiveDismissDeltaYSlope = 0f
        interactiveDismissGestureBaseX = 0f
        interactiveDismissGestureBaseY = 0f
    }

    /**
     * System and predictive back start here. A settle-back animation that still owns
     * the surface is taken over at its current progress, and the new gesture continues
     * from that live frame: the platform restarts its own progress at zero, so the
     * resumed progress is kept as a base instead of being overwritten.
     */
    private fun beginSystemBackGesture(): Boolean {
        fun reject(reason: String): Boolean {
            // Rejection must be observable: an unexplained "no animation" report is
            // otherwise indistinguishable from the platform not delivering a gesture.
            org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
                viewerDiagnosticSessionId, "viewer_back_interactive_rejected") {
                mapOf("reason" to reason,
                    "transitionState" to transitionSession.state.name,
                    "outcome" to transitionSession.outcome?.name,
                    "operation" to transitionSession.returnOperation?.operationId,
                    "surface" to (interactiveDismissSurface != null),
                    "lifecycle" to (if (isFinishing) "finishing" else if (isDestroyed) "destroyed" else "alive"))
            }
            return false
        }
        if (isFinishing || isDestroyed) return reject(if (isFinishing) "finishing" else "destroyed")
        if (transitionSession.state == State.INTERACTIVE_RETURN) {
            val resumed = mediaDismissGestureBegin()
            if (resumed == null && interactiveDismissSurface == null) return reject("settle_takeover_unavailable")
            // The drag take-over above already cancelled the settle at this frame.
            systemBackResumeProgress = interactiveDismissProgress
        } else {
            if (transitionSession.state != State.VIEWING) return reject("state_${transitionSession.state.name}")
            if (!beginInteractiveDismiss()) return reject("interactive_dismiss_unavailable")
            systemBackResumeProgress = 0f
        }
        org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
            viewerDiagnosticSessionId, "viewer_back_interactive_accepted") {
            mapOf("transitionState" to transitionSession.state.name,
                "resumeProgress" to systemBackResumeProgress,
                "surface" to (interactiveDismissSurface != null))
        }
        // The back gesture has no vertical drag delta; the shared envelope stays centred.
        interactiveDismissDeltaXSlope = 0f
        interactiveDismissDeltaYSlope = 0f
        return true
    }

    private fun renderSystemBackProgress(progress: Float) {
        if (transitionSession.state != State.INTERACTIVE_RETURN || interactiveDismissSurface == null) return
        // A new gesture restarts the platform progress at zero while the frame it
        // inherited already has a non-zero progress. Consume only the remaining
        // interval so the takeover frame is continuous and the last part of the
        // drag still moves the envelope.
        renderInteractiveDismissProgress(ViewerReturnProgress.resume(systemBackResumeProgress, progress))
    }

    private fun cancelSystemBackGesture() {
        systemBackResumeProgress = 0f
        org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
            viewerDiagnosticSessionId, "viewer_back_cancelled") {
            mapOf("transitionState" to transitionSession.state.name,
                "progress" to interactiveDismissProgress)
        }
        if (transitionSession.state == State.INTERACTIVE_RETURN) mediaDismissSettled(false)
    }

    private fun commitSystemBack() {
        systemBackResumeProgress = 0f
        org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
            viewerDiagnosticSessionId, "viewer_back_committed") {
            mapOf("transitionState" to transitionSession.state.name,
                "dragActive" to interactiveDismissDragActive,
                "progress" to interactiveDismissProgress)
        }
        if (interactiveDismissDragActive && transitionSession.state == State.INTERACTIVE_RETURN) {
            // Same handoff as a released vertical drag: continue from the live frame.
            mediaDismissSettled(true)
        } else {
            requestViewerClose("system_back")
        }
    }

    override fun mediaDismissGestureBegin(): Float? {
        if (transitionSession.state != State.INTERACTIVE_RETURN) return null
        val animator = interactiveDismissSettleAnimator ?: return null
        val progress = interactiveDismissProgress
        interactiveDismissGeneration++
        animator.removeAllUpdateListeners()
        animator.removeAllListeners()
        animator.cancel()
        interactiveDismissSettleAnimator = null
        interactiveDismissGestureBaseX = interactiveDismissDeltaXSlope * progress
        interactiveDismissGestureBaseY = interactiveDismissDeltaYSlope * progress
        interactiveDismissDragActive = interactiveDismissSurface != null
        return progress
    }

    override fun mediaDismissProgress(progress: Float, deltaX: Float, deltaY: Float): Boolean {
        if (isFinishing || isDestroyed || (transitionSession.state != State.VIEWING &&
                transitionSession.state != State.INTERACTIVE_RETURN)) return false
        if (!beginInteractiveDismiss()) return false
        interactiveDismissDeltaXSlope = if (progress > 0f)
            (interactiveDismissGestureBaseX + deltaX) / progress else 0f
        interactiveDismissDeltaYSlope = if (progress > 0f)
            (interactiveDismissGestureBaseY + deltaY) / progress else 0f
        renderInteractiveDismissProgress(progress)
        return true
    }

    /**
     * Owns the shared surface and grid preview at progress 0 for both the vertical
     * dismiss gesture and the system back gesture. The caller supplies the mapping
     * from its own progress to the shared envelope.
     */
    private fun beginInteractiveDismiss(): Boolean {
        if (interactiveDismissSurface != null) return interactiveDismissDragActive
        val fragment = getCurrentFragment() ?: return false
        val operation = transitionSession.beginGesture() ?: return false
        val path = getCurrentPath()
        val surface = getCurrentSharedElementView(path) ?: run {
            // The predictive-back gesture cannot enter the shared return. The session
            // rolls back cleanly below; record why, so the device log can tell a
            // legitimate zoom/rotation state from a missing renderer frame.
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_return_gesture_unavailable",
                org.fossify.gallery.helpers.ViewerProxyRejection.legacyCode(
                    lastProxyRejection ?: org.fossify.gallery.helpers.ViewerProxyRejection.PROXY_PREPARE_REJECTED))
            transitionSession.cancelGesture(operation)
            return false
        }
        ownReturnSurface(operation, fragment)
        returnLeases.acquire(operation, Resource.INTERACTIVE_ANIMATOR, ::clearInteractiveDismissDrag)
        ownReturnChrome(operation, fragment)
        interactiveDismissSettleAnimator?.cancel()
        surface.animate().cancel()
        surface.pivotX = surface.width / 2f
        surface.pivotY = surface.height / 2f
        surface.scaleX = 1f
        surface.scaleY = 1f
        surface.translationX = 0f
        surface.translationY = 0f
        // Cache the content while only its position and scale are moving.
        surface.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.viewerTransitionScrim.setBackgroundColor(Color.BLACK)
        binding.viewerTransitionScrim.visibility = View.VISIBLE
        binding.viewerTransitionScrim.alpha = 1f
        viewerBars.startInteractive()
        interactiveDismissSurface = surface
        interactiveDismissPage = fragment
        interactiveDismissPath = path
        interactiveDismissDragActive = true
        interactiveDismissProgress = 0f
        interactiveDismissGestureBaseX = 0f
        interactiveDismissGestureBaseY = 0f
        transitionSession.showGridPreview(operation, path)
        setSharedTransitionCanvasTransparent()
        return true
    }

    override fun mediaDismissSettled(dismiss: Boolean) {
        val surface = interactiveDismissSurface ?: return
        val operation = transitionSession.returnOperation ?: return
        if (transitionSession.state != State.INTERACTIVE_RETURN ||
            !interactiveDismissDragActive || isDestroyed || isFinishing) return
        interactiveDismissSettleAnimator?.cancel()
        if (dismiss) {
            // Hand the live gesture transform directly to the Activity shared
            // element return so it can continue from the frame under the finger.
            interactiveDismissDragActive = false
            interactiveDismissSettleAnimator = null
            finish()
            return
        }
        val generation = ++interactiveDismissGeneration
        val start = interactiveDismissProgress
        val end = 0f
        val duration = ViewerReturnProgress.settleDurationMs(start)
        interactiveDismissSettleAnimator = ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener { animator ->
                if (transitionSession.state == State.INTERACTIVE_RETURN && transitionSession.returnOperation == operation &&
                    generation == interactiveDismissGeneration && surface === interactiveDismissSurface) {
                    renderInteractiveDismissProgress(animator.animatedValue as Float)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || generation != interactiveDismissGeneration) return
                    // A gesture that was already released by another path must still
                    // release its leases; a return that took over keeps them.
                    if (transitionSession.cancelGesture(operation) ||
                        transitionSession.outcome != null || transitionSession.state == State.VIEWING) {
                        // The gesture settled back to the Viewer: the bars are steady again.
                        viewerBars.cancelInteractive()
                        returnLeases.releaseAll()
                    }
                }
            })
            start()
        }
    }

    private fun finishMediaEntryTransition(entry: PhotoTransitionCompletionGate) {
        if (!isCurrentEntry(entry) || !entry.complete()) return
        check(transitionSession.entryFinished(transitionSession.entry))
        // The Viewer is the destination from here on: its bars are steady, not blending.
        viewerBars.settleViewer()
        val mappedPage = entryMappedPage
        entryLeases.releaseAll()
        logMediaEntry("entry_completed", if (photoSharedElementMapped) "shared" else "fallback")
        val pendingPublication = transitionPendingSwipePublication
        transitionPendingSwipePublication = null
        if (isFavoriteRequest) org.fossify.gallery.helpers.GalleryPerfTrace.event(
            "viewer_favorite_transition_publication_ready",
            pendingPublication?.media?.size ?: transitionPendingAdjacentMedia?.size ?: 0
        )
        val hasDeferredContent = pendingPublication != null ||
            transitionPendingAdjacentMedia != null ||
            (initialViewerPublicationPending && mMediaFiles.isNotEmpty())
        initialViewerPublicationPending = false
        initialViewerPublicationAwaitingAdapter = hasDeferredContent && activePagerAdapter == null
        supportFragmentManager.fragments.forEach { if (it !== mappedPage) finishSharedEntryPage(it) }
        // Transition end listeners run during the final shared-element frame.
        // Pager publication can synchronously create fragments and bind their
        // images, so queue that work after this frame has been drawn.
        if (pendingPublication != null || transitionPendingAdjacentMedia != null ||
            (initialViewerPublicationAwaitingAdapter && mMediaFiles.isNotEmpty())) {
            binding.root.postOnAnimation {
                if (transitionSession.outcome != null || finishAfterSharedTransitionInProgress ||
                    isFinishing || isDestroyed) return@postOnAnimation
                when {
                    pendingPublication != null -> {
                        transitionPendingAdjacentMedia = null
                        applySwipeMediaPublication(pendingPublication)
                    }
                    transitionPendingAdjacentMedia != null -> publishPendingAdjacentAfterTransition()
                    initialViewerPublicationAwaitingAdapter && mMediaFiles.isNotEmpty() -> {
                        gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
                        checkSlideshowOnEnter()
                    }
                }
            }
        }
    }

    private var mediaEntry: PhotoTransitionCompletionGate? = null
    private var entryFallbackAnimator: ValueAnimator? = null
    private var entryMappedPage: ViewPagerFragment? = null
    private var entryReadyReported = false
    private var entryFailedPage: ViewPagerFragment? = null
    private var entryShowsLoadStatus = false
    private var entryWaitingForRenderer = false

    private fun isCurrentEntry(entry: PhotoTransitionCompletionGate) = mediaEntry === entry && transitionSession.entryActive && entry.active &&
        !finishAfterSharedTransitionInProgress && !isFinishing && !isDestroyed

    private fun cancelMediaEntryCallbacks() {
        entryWaitingForRenderer = false
        entryReleaseDraw?.removeListener()
        entryReleaseDraw = null
        entryDiagnosticDraw?.removeListener()
        entryDiagnosticDraw = null
        photoSharedElementHandler.removeCallbacksAndMessages(null)
        entryFallbackAnimator?.removeAllListeners()
        entryFallbackAnimator?.removeAllUpdateListeners()
        entryFallbackAnimator?.cancel()
        entryFallbackAnimator = null
        photoTransitionMappedView = null
        entryMappedPage = null
    }

    private fun abandonMediaEntry(reason: String) {
        if (!transitionSession.entryActive) return
        transitionSession.cancel(reason)
        logMediaEntry("entry_cancelled", reason)
        entryLeases.releaseAll()
        transitionSession.clean()
    }

    private fun finishSharedEntryPage(page: androidx.fragment.app.Fragment?) {
        when (page) {
            is PhotoFragment -> page.finishSharedTransition()
            is VideoFragment -> page.finishSharedTransition()
        }
    }

    private fun clearSharedTransitionSurface(page: ViewPagerFragment?) {
        when (page) {
            is PhotoFragment -> page.clearSharedEntrySurface()
            is VideoFragment -> page.clearSharedEntrySurface()
        }
    }

    private var photoSharedElementMapped = false
    private var photoSharedElementFadeFallback = false
    private val finishAfterSharedTransitionInProgress get() = ::transitionSession.isInitialized &&
        (transitionSession.outcome != null || transitionSession.returnOperation?.let(transitionSession::isReturning) == true)
    private var returnScrimAlpha = 1f
    private var viewerReturnId = 0L

    /** Why the last shared-proxy attempt failed, for the return fallback diagnostics. */
    private var lastProxyRejection: String? = null

    private var viewerDiagnosticSessionId = 0L
    private var viewerRendererObserved = false
    private var sharedReturnView: View? = null
    private var sharedReturnPage: ViewPagerFragment? = null
    private var sharedReturnAnimatorStarted = false
    private val sharedReturnVisualClock = ViewerReturnVisualClock()
    private var sharedReturnScrimFadeReported = false
    private val sharedReturnTerminal get() = transitionSession.viewerMotionOutcome != null
    private var sharedReturnFinishRequested = false
    private var returnOverlayAlphas = emptyMap<View, Float>()
    private var photoSharedElementPath: String? = null
    private val photoSharedElementHandler = Handler()
    private val entryWatchdog = Runnable {
        mediaEntry?.let { startEntryFallback(it, "no_animator") }
    }
    private val entryReadyTimeout = Runnable {
        val entry = mediaEntry ?: return@Runnable
        if (isCurrentEntry(entry) && entry.awaitingRelease) {
            logMediaEntry("entry_timeout", "ready_or_layout")
            releasePhotoSharedElement(entry)
        }
    }
    private var viewerSelectedImageStarted = 0L
    private var selectedImageRenderedLogged = false
    private val renderedAdjacentPaths = HashSet<String>()
    private var mDirectory = ""
    private var mIsFullScreen = false
    private var mPos = -1
    private var diagnosticViewerOpened = false
    private val viewerDiagnosticStartedAt = android.os.SystemClock.elapsedRealtime()
    private var viewerDiagnosticRequestId: String? = null
    private var viewerDiagnosticScopeKey: String? = null
    private var viewerDiagnosticOpenMs = -1L
    private var viewerDiagnosticFirstPageBoundMs = -1L
    private var viewerDiagnosticPublicationCount = 0L
    private var viewerDiagnosticExpandCount = 0L
    private var viewerDiagnosticBoundaryStartedAt = 0L
    private var mShowAll = false
    private var lastVisitedPath: String? = null
    private var lastUserNavigationForward: Boolean? = null
    private var confirmedUserPagePath: String? = null
    private var userNavigationEpoch = 0L
    private var userSwipeInProgress = false
    private var explicitUserNavigationPending = false
    private var photoDeleteGeneration = 0L
    private val successfullyDeletedViewerPaths = LinkedHashSet<String>()
    private data class ViewerDeleteSnapshot(
        val deletedPath: String,
        val previousMedia: List<Medium>,
        val lastVisitedPath: String?,
        val previousPosition: Int,
        val orderedPaths: List<String>?,
        val lastMoveForward: Boolean?,
        val navigationEpoch: Long,
        val recoveryAnchorPath: String? = null,
        val profileId: String?,
        val sessionId: String?,
        val generation: Long,
        val revision: Long,
        val operationId: String = "delete-recovery-${android.os.SystemClock.elapsedRealtime()}"
    )
    private var photoDeleteRecovery: ViewerDeleteSnapshot? = null
    private var pendingViewerDeleteRecovery: ViewerDeleteSnapshot? = null

    private fun logViewerDeleteSnapshot(
        event: String,
        recovery: ViewerDeleteSnapshot,
        fields: Map<String, Any?> = emptyMap(),
        level: org.fossify.gallery.helpers.DiagnosticLevel = org.fossify.gallery.helpers.DiagnosticLevel.INFO
    ) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        val anonymousId = org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            level, "Viewer", event, recovery.operationId,
            fields = mapOf(
                "deletedMediaId" to anonymousId(recovery.deletedPath),
                "recoveryGeneration" to recovery.generation,
                "revision" to recovery.revision
            ) + fields
        )
    }
    /** The tapped grid's ordering evidence; never an authorization capability. */
    private var viewerGridOrder: List<String>? = null
    private var viewerGridEvidence: org.fossify.gallery.helpers.ViewerGridOrderStore.Evidence? = null
    private val folderScanGate = org.fossify.gallery.helpers.ViewerFolderScanGate()
    private val folderScanWatchdog = org.fossify.gallery.helpers.RefreshWatchdog()
    private var folderScanUnsubscribe: (() -> Unit)? = null
    private var expandCommittedFolder: (() -> Unit)? = null
    private var folderHandoffExpansionRunning = false
    private var folderCompletePreparing = false
    private var appliedViewerCatalogComplete = false

    private fun takeGridEvidence(source: String, selected: String, session: String?, photo: Boolean): List<String>? {
        val revision = if (photo) org.fossify.gallery.helpers.PhotoContentRevision.current() else FolderContentRevision.current()
        val contract = org.fossify.gallery.helpers.ViewerGridOrderStore.contract(
            config.getFolderSorting(if (photo) PhotoProfileMediaLoader.PATH else source),
            if (photo) config.photoMediaFilter else config.filterMedia, config.shouldShowHidden,
            viewerSearchQuery.orEmpty(), session, revision)
        val evidence = org.fossify.gallery.helpers.ViewerGridOrderStore.takeEvidence(
            intent.getStringExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID), source, selected, contract)
        viewerGridEvidence = evidence
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled())
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "Viewer", "viewer_grid_handoff_consume", viewerDiagnosticRequestId,
                mapOf("handoffId" to intent.getStringExtra(org.fossify.gallery.helpers.ViewerGridOrderStore.EXTRA_ID),
                    "gridVersion" to evidence?.version, "itemCount" to evidence?.paths?.size,
                    "revision" to revision, "scopeKey" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(source),
                    "contractAccepted" to (evidence != null)))
        return evidence?.paths
    }

    private var reusedFolderWatch: FolderScanObservation? = null
    private var reusedFolderAccess: DirectoryAccessState? = null
    private val folderChangeHandler = Handler(android.os.Looper.getMainLooper())

    private fun stopReusedFolderWatch() {
        reusedFolderWatch?.stop()
        reusedFolderWatch = null
        reusedFolderAccess = null
        folderChangeHandler.removeCallbacksAndMessages(null)
    }
    private var stagedInternalViewer = false
    private var mIsSlideshowActive = false
    private var activePagerAdapter: MyPagerAdapter? = null
    private var initialViewerPublicationPending = false
    private var initialViewerPublicationAwaitingAdapter = false
    private data class PendingPagerPublication(
        val media: List<Medium>,
        val positions: Map<org.fossify.gallery.helpers.ViewerMediumIdentity, Int>?,
        val generation: Long,
        val folderRevision: Long,
        val photoRevision: Long,
        val scope: String
    )
    private var pendingPagerPublication: PendingPagerPublication? = null
    private var transitionPendingAdjacentMedia: ArrayList<Medium>? = null

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
    private var transitionPendingSwipePublication: SwipeMediaPublication? = null
    private data class PendingFullCatalogRequest(
        val generation: Long,
        val folderRevision: Long,
        val photoRevision: Long
    )
    private var pendingFullCatalogRequest: PendingFullCatalogRequest? = null
    private var publishedFavoriteFullCatalogRevision: Pair<Long, Long>? = null
    private var swipePageOrigin = -1
    private var swipePrefetchTarget = -1
    private var pendingPageAccessRefresh = false
    private var pendingSwipeMenuRefresh = false
    /** An outward swipe at a provisional edge waits for the newest catalog. */
    private var deferredBoundaryDirection: Boolean? = null
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
    private val screenPrefetch = org.fossify.gallery.helpers.ViewerScreenPrefetchCoordinator()
    private val screenPrefetchHandler = Handler(android.os.Looper.getMainLooper())
    private val screenPrefetchStable = Runnable { beginScreenPrefetch() }
    private var screenPrefetchForward = true
    private var screenPrefetchGeneration = 0L
    private var screenPrefetchTarget: com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>? = null
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
        deferredBoundaryDirection = null
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

    private fun configureMediaTransitionSession() {
        val offeredEntry = MediaTransitionHandoff.read(intent)
        val sharedSession = MediaTransitionHandoff.claim(intent)
        transitionSession = sharedSession ?: MediaTransitionHandoff.create()
        entryLeases = transitionSession.leases()
        returnLeases = transitionSession.leases()
        // A recreated window cannot claim a second copy of the original platform entry.
        if (offeredEntry != null) {
            setResult(RESULT_CANCELED, MediaTransitionHandoff.write(Intent()
                .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L)),
                offeredEntry))
        }
        fadeOnlyPhotoFallback = intent.getBooleanExtra(PhotoViewerTransition.EXTRA_FADE_ONLY, false)
        if (fadeOnlyPhotoFallback) window.setWindowAnimations(0)
        photoSharedElementPath = intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH)
            ?.takeIf { OxygenClassicMotion.enabled(this) && (offeredEntry == null || sharedSession != null) }
        val entry = transitionSession.entryGate.takeIf { photoSharedElementPath != null }
        if (entry != null) {
            mediaEntry = entry
            entryLeases.acquire(transitionSession.entry, Resource.INPUT) { binding.viewPager.transitionInputLocked = false }
            entryLeases.acquire(transitionSession.entry, Resource.CHROME) {
                renderSharedViewerLayers(1f)
                restoreBlackViewerCanvas()
                binding.viewPager.alpha = 1f
            }
            entryLeases.acquire(transitionSession.entry, Resource.CALLBACKS, ::cancelMediaEntryCallbacks)
            org.fossify.gallery.helpers.OxygenClassicActivityMotion.configureShared(this)
            postponeEnterTransition()
            window.sharedElementEnterTransition = org.fossify.gallery.helpers.OxygenClassicActivityMotion.mediaTransition(
                PhotoViewerTransition.DURATION_MS,
                onProgress = { renderPhotoTransitionProgress(entry, it) },
                onTerminal = { cancelled -> onSharedEntryTerminal(entry, cancelled) },
                staticPhotoEntry = intent.getBooleanExtra(PhotoViewerTransition.EXTRA_STATIC_PHOTO, false),
            )
            setEnterSharedElementCallback(object : SharedElementCallback() {
                override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                    mapEntrySharedElement(entry, names, sharedElements)
                }

                override fun onSharedElementStart(names: MutableList<String>, elements: MutableList<View>, snapshots: MutableList<View>) {
                    captureEntrySharedElement(entry, names, elements)
                }

                override fun onRejectSharedElements(rejectedSharedElements: MutableList<View>) {
                    // Android 16 hands these views to us before setting up its default overlay fade.
                    if (mediaEntry === entry && (entry.active || entryShowsLoadStatus) &&
                        !finishAfterSharedTransitionInProgress && !isFinishing && !isDestroyed) {
                        rejectedSharedElements.clear()
                    }
                }
            })
            photoSharedElementHandler.postDelayed(entryReadyTimeout, PhotoViewerTransition.READY_TIMEOUT_MS)
        }
        if (entry == null) check(transitionSession.entryFinished(transitionSession.entry, withoutSharedEntry = true))
        binding.viewPager.transitionInputLocked = entry != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore media fragments before the current password/session
        // flow validates the viewer request.
        traceEntryCanvas("create_before")
        super.onCreate(null)
        val correlatedEntryId = ViewerEntryDiagnostics.entryId(
            intent.getStringExtra(ViewerEntryDiagnostics.EXTRA_DIAGNOSTIC_PROCESS), entryRequestId()
        )
        viewerDiagnosticSessionId = correlatedEntryId.takeIf { it != 0L } ?: ViewerEntryDiagnostics.newRequest()
        // Fragments read this id from the Activity intent (see PhotoFragment's
        // resource-stage events). Without writing it back every resource event
        // reported viewerSessionId=0 and could not be correlated with the session.
        intent.putExtra(ViewerEntryDiagnostics.EXTRA_VIEWER_SESSION_ID, viewerDiagnosticSessionId)
        configureMediaTransitionSession()
        // One back callback for system keys, accessibility and predictive gestures.
        // It only feeds the existing Session and dismiss surface; no second return path.
        onBackPressedDispatcher.addCallback(this, org.fossify.gallery.helpers.ViewerBackGesture(
            beginInteractive = ::beginSystemBackGesture,
            renderProgress = ::renderSystemBackProgress,
            cancelInteractive = ::cancelSystemBackGesture,
            commit = ::commitSystemBack,
        ))
        val entry = mediaEntry
        supportFragmentManager.registerFragmentLifecycleCallbacks(slideshowFragments, false)
        setContentView(binding.root)
        // The media canvas must not inherit the app's light theme while pages load
        // or the progressive adapter replaces deferred placeholders.
        if (entry != null) {
            setSharedTransitionCanvasTransparent()
            binding.viewerTransitionScrim.setBackgroundColor(Color.BLACK)
            binding.viewerTransitionScrim.visibility = View.VISIBLE
            binding.viewerTransitionScrim.alpha = 0f
        } else {
            binding.root.setBackgroundColor(Color.BLACK)
            binding.viewPager.setBackgroundColor(Color.BLACK)
        }
        if (entry != null) {
            viewerBars.startEntry()
            viewerSystemBarProgress = 0f
            setSharedChromeAlpha(0f)
        }
        refreshViewerSystemBars()
        traceEntryCanvas("create_canvas_ready")
        logMediaEntry("viewer_created", if (entry == null) "window_fade" else null)
        ViewerEntryDiagnostics.event(viewerDiagnosticSessionId, "viewer_launch_accepted") {
            mapOf("viewerSessionId" to viewerDiagnosticSessionId, "entryRequestId" to correlatedEntryId.takeIf { it != 0L },
                "runtimeSessionId" to transitionSession.entry.sessionId,
                "runtimeOperationId" to transitionSession.entry.operationId,
                "sharedCandidate" to (entry != null))
        }
        if (entry != null && ViewerEntryDiagnostics.enabled(entryRequestId())) {
            entryDiagnosticDraw = binding.root.doOnPreDraw {
                entryDiagnosticDraw = null
                if (isCurrentEntry(entry)) traceEntryCanvas("first_pre_draw", updateLifecycle = false)
            }
        }
        org.fossify.gallery.helpers.OxygenProgressStyle.apply(this, config, binding.photoBoundaryLoading)
        binding.viewPager.onUnknownBoundary = { forward -> requestViewerBoundary(forward) }
        binding.viewPager.canPageHorizontallyFromCurrentPhoto = {
            getCurrentPhotoFragment()?.canPageHorizontallyFromCurrentPhoto() == true
        }
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
                // A Favorite path can be removed while the session remains valid.
                // Drop any previously prepared/deferred full catalog before it can
                // republish a photo that has just lost its captured membership.
                mediaPublicationGeneration++
                mediaPublicationTask?.cancel(true)
                swipePublicationGate.clear()
                deferredBoundaryDirection = null
                pendingPageAccessRefresh = false
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

        if (entry != null) window.decorView.setBackgroundColor(Color.TRANSPARENT)
        else window.decorView.setBackgroundColor(Color.BLACK)
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
        traceEntryCanvas("create_after")
    }

    override fun onStart() {
        super.onStart()
        traceEntryCanvas("start_after")
    }

    override fun onResume() {
        super.onResume()
        if (!checkFavoriteAccess()) return
        OxygenToolbarStyle.applyViewerPopup(this, binding.mediumViewerToolbar)
        if (!hasPermission(getPermissionToRequest()) ||
            reusedFolderAccess?.let { it != DirectoryAccessState.capture(applicationContext) } == true) {
            finish()
            return
        }

        initBottomActions()
        mOriginalBrightness = window.updateBrightness(config.maxBrightness, mOriginalBrightness)
        setupOrientation()
        refreshMenuItems()

        val filename = getCurrentMedium()?.name ?: mPath.getFilenameFromPath()
        binding.mediumViewerToolbar.title = filename
        traceEntryCanvas("resume_after")
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
        traceEntryCanvas("post_resume_after")
    }

    override fun onPause() {
        super.onPause()
        stopSlideshow()
    }

    override fun onDestroy() {
        stopReusedFolderWatch()
        ViewerReturnDiagnostics.event(viewerReturnId, "viewer_destroyed") {
            mapOf("animatorStarted" to sharedReturnAnimatorStarted, "transitionTerminal" to sharedReturnTerminal)
        }
        abandonMediaEntry("destroyed")
        cancelScreenPrefetch()
        val returning = transitionSession.returnOperation
        if (returning != null && transitionSession.isReturning(returning) && !transitionSession.gridAttached) {
            concludeStandaloneReturn(returning)
        } else if (returning == null || transitionSession.state == State.INTERACTIVE_RETURN) {
            transitionSession.cancel("destroyed")
        }
        returnLeases.releaseAll()
        entryLeases.releaseAll()
        transitionSession.clean()
        // Drop this window's recorded bar ownership with the window itself.
        org.fossify.gallery.helpers.OxygenSystemBars.forget(this)
        setEnterSharedElementCallback(null as SharedElementCallback?)
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.INFO, "Viewer", "viewer_close",
            viewerDiagnosticRequestId,
            fields = mapOf("index" to mPos, "count" to runCatching { getCurrentMedia().size }.getOrDefault(0))
        )
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled() && viewerDiagnosticRequestId != null)
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaPipeline", "media_viewer_summary",
                viewerDiagnosticRequestId,
                mapOf(
                    "scopeKey" to viewerDiagnosticScopeKey,
                    "sourceType" to if (isPhotoProfile) "PHOTO_PROFILE" else if (isFavoriteRequest) "FAVORITES" else "FOLDER",
                    "viewerOpenMs" to viewerDiagnosticOpenMs,
                    "firstPageBoundMs" to viewerDiagnosticFirstPageBoundMs,
                    "viewerCatalogLoadMs" to -1,
                    "viewerExpandCount" to viewerDiagnosticExpandCount,
                    "viewerPublicationCount" to viewerDiagnosticPublicationCount,
                    "terminalState" to "CLOSED"
                )
            )
        photoDeleteGeneration++
        photoDeleteRecovery = null
        retryViewerScan = null
        expandCommittedFolder = null
        viewerGridEvidence = null
        folderScanUnsubscribe?.invoke()
        folderScanUnsubscribe = null
        folderScanGate.cancel()
        folderScanWatchdog.stop()
        finishViewerBoundaryTrace()
        viewerLoading.dispose()
        swipePublicationGate.clear()
        pendingPageAccessRefresh = false
        deferredBoundaryDirection = null
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
        ViewerEntryDiagnostics.event(viewerDiagnosticSessionId, "viewer_cleanup_completed") {
            mapOf("viewerSessionId" to viewerDiagnosticSessionId, "returnRequestId" to viewerReturnId.takeIf { it != 0L })
        }
    }

    // System back also reaches this directly on framework-dispatched back.
    override fun finishAfterTransition() {
        requestViewerClose("system_back")
    }

    private fun finishDuringMediaEntry(): Boolean {
        if (::transitionSession.isInitialized && transitionSession.entryActive) {
            // Close with one fade while the entry still owns the shared element.
            ViewerEntryDiagnostics.event(viewerDiagnosticSessionId, "viewer_closed_during_entry") { emptyMap() }
            photoSharedElementMapped = false
            abandonMediaEntry("closed")
            viewerBars.releaseToPage()
            super.finish()
            overridePendingTransition(0, if (OxygenClassicMotion.enabled(this)) R.anim.photo_viewer_fade_out else 0)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        if (!finishAfterSharedTransitionInProgress && !finishDuringMediaEntry()) super.onBackPressed()
    }

    override fun finish() {
        if (finishAfterSharedTransitionInProgress) {
            finishPlatformReturn()
            return
        }
        requestViewerClose("finish")
    }

    private fun requestViewerClose(reason: String) {
        fun reject(rejection: String): Unit = org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
            viewerDiagnosticSessionId, "viewer_close_rejected") {
            mapOf("reason" to rejection, "source" to reason,
                "transitionState" to if (::transitionSession.isInitialized) transitionSession.state.name else "uninitialized",
                "outcome" to if (::transitionSession.isInitialized) transitionSession.outcome?.name else null,
                "finishAfterSharedTransition" to finishAfterSharedTransitionInProgress,
                "lifecycle" to (if (isFinishing) "finishing" else if (isDestroyed) "destroyed" else "alive"))
        }
        if (isFinishing || isDestroyed || finishAfterSharedTransitionInProgress) {
            reject(if (finishAfterSharedTransitionInProgress) "finish_after_shared_transition" else "lifecycle")
            return
        }
        if (!::transitionSession.isInitialized) {
            super.finish()
            return
        }
        // Every exit path (shared return, fallback fade, close during entry) goes
        // through here: this window becomes the only legal writer of its bars from this
        // frame on, starting from the blend that is actually on screen. The released
        // value is what the return continues from - it is not a permanent freeze.
        if (!viewerBars.isReturning()) {
            viewerBarToken = viewerBars.beginReturn(viewerSystemBarProgress)
            logSystemBarOwner("committed", reason)
        }
        if (interactiveDismissDragActive) {
            mediaDismissSettled(dismiss = true)
            return
        }
        if (finishDuringMediaEntry()) return
        val returnPath = getCurrentPath()
        val page = getCurrentFragment()
        val dragSurface = interactiveDismissSurface
        if (dragSurface != null && (!dragSurface.isAttachedToWindow ||
                interactiveDismissPath != returnPath || interactiveDismissPage !== page)) {
            val gesture = transitionSession.returnOperation ?: return
            if (transitionSession.cancelGesture(gesture)) returnLeases.releaseAll()
        }
        val operation = transitionSession.beginReturn() ?: run {
            // A legitimate concurrent exit already owns the operation. Record the
            // merge instead of silently dropping the user's second back request.
            org.fossify.gallery.helpers.ViewerReturnDiagnostics.event(
                viewerDiagnosticSessionId, "viewer_close_merged") {
                mapOf("source" to reason, "transitionState" to transitionSession.state.name,
                    "outcome" to transitionSession.outcome?.name,
                    "operation" to transitionSession.returnOperation?.operationId,
                    "returnOperation" to transitionSession.returnOperation?.operationId)
            }
            return
        }
        viewerReturnId = ViewerEntryDiagnostics.newRequest()
        val motionEnabled = OxygenClassicMotion.enabled(this)
        val eligible = photoSharedElementMapped && motionEnabled && returnPath.isNotEmpty()
        val proxy = if (eligible) getCurrentSharedElementView(returnPath, returnRequestId = viewerReturnId) else null
        val canReturn = proxy?.isAttachedToWindow == true
        ViewerReturnDiagnostics.event(viewerReturnId, "viewer_requested") {
            mapOf(
                "reason" to reason, "kind" to if (getCurrentMedium()?.isVideo() == true) "video" else "image",
                "viewerSessionId" to viewerDiagnosticSessionId.takeIf { it != 0L },
                "runtimeSessionId" to operation.sessionId, "runtimeOperationId" to operation.operationId,
                "entryMapped" to photoSharedElementMapped, "motion" to motionEnabled,
                "hasSelection" to returnPath.isNotEmpty(), "canReturn" to canReturn,
                "hasProxy" to (proxy != null), "proxyAttached" to proxy?.isAttachedToWindow,
                "proxyRejection" to if (proxy == null) lastProxyRejection else null,
                "launchId" to intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L),
                "mediaId" to returnPath.takeIf(String::isNotEmpty)?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "window" to ViewerEntryDiagnostics.backgroundState(window.decorView.background),
                "decor" to ViewerEntryDiagnostics.viewState(window.decorView),
                "scrim" to ViewerEntryDiagnostics.viewState(binding.viewerTransitionScrim),
            )
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_transition_return", if (canReturn) 1 else 0)
        val result = Intent()
            .putExtra(PhotoViewerTransition.EXTRA_RETURN_PATH, returnPath.takeIf(String::isNotEmpty))
            .putStringArrayListExtra(PhotoViewerTransition.EXTRA_DELETED_PATHS,
                ArrayList(successfullyDeletedViewerPaths))
            .putExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, viewerReturnId)
            .putExtra(ViewerEntryDiagnostics.EXTRA_VIEWER_SESSION_ID, viewerDiagnosticSessionId)
            .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L))
        val offeredEntry = MediaTransitionHandoff.read(intent)
        setResult(RESULT_OK, MediaTransitionHandoff.write(result,
            if (offeredEntry != null && !transitionSession.viewerAttached) offeredEntry else operation))
        if (proxy != null && interactiveDismissSurface == null) ownReturnSurface(operation, page)
        if (!canReturn) {
            ViewerReturnDiagnostics.event(viewerReturnId, "viewer_return_fallback") {
                mapOf("eligible" to eligible, "proxyAttached" to false,
                    // Attribute the fallback: NOT_AT_FIT for a genuinely zoomed-in
                    // photo, RENDERER_NOT_READY for a missing frame, and so on -
                    // not every fallback is the same failure.
                    "proxyRejection" to (lastProxyRejection ?: if (eligible) "PROXY_DETACHED" else "NOT_ELIGIBLE"))
            }
            transitionSession.terminate(operation, State.FALLBACK, "viewer_no_proxy")
            returnLeases.releaseAll()
            transitionSession.clean()
            // No returning transition will render a last frame for this window.
            viewerBars.releaseToPage()
            super.finish()
            if ((fadeOnlyPhotoFallback || photoSharedElementFadeFallback || photoSharedElementMapped) && motionEnabled) {
                overridePendingTransition(0, R.anim.photo_viewer_fade_out)
            }
            return
        }
        if (interactiveDismissSurface == null) ownReturnChrome(operation, page)
        returnLeases.acquire(operation, Resource.INPUT) { binding.viewPager.transitionInputLocked = false }
        returnLeases.acquire(operation, Resource.CALLBACKS) { photoSharedElementHandler.removeCallbacksAndMessages(null) }
        if (!transitionSession.gridAttached) transitionSession.targetReady(operation)
        window.sharedElementReturnTransition = org.fossify.gallery.helpers.OxygenClassicActivityMotion.mediaTransition(
            PhotoViewerTransition.DURATION_MS,
            onProgress = { progress -> renderSharedReturnProgress(operation, progress) },
            onTerminal = { cancelled -> onSharedReturnTerminal(operation, cancelled) },
            onStart = {
                if (transitionSession.isReturning(operation)) ViewerReturnDiagnostics.event(viewerReturnId, "viewer_transition_start")
            },
            cropMediaToBounds = page is VideoFragment || page is PhotoFragment,
            photoReturnTarget = { transitionSession.photoReturnTarget(operation, returnPath) },
            forceMediaCropToBounds = {
                page is PhotoFragment && transitionSession.photoTargetIsCropped(operation, returnPath)
            },
        )
        photoSharedElementPath = returnPath
        sharedReturnView = proxy
        sharedReturnPage = page
        if (page is VideoFragment || page is PhotoFragment) protectGridToolbarDuringReturn(operation, proxy)
        // Keep the opacity already on screen while the grid prepares its target.
        returnScrimAlpha = if (proxy === interactiveDismissSurface) binding.viewerTransitionScrim.alpha else 1f
        binding.viewerTransitionScrim.setBackgroundColor(Color.BLACK)
        binding.viewerTransitionScrim.visibility = View.VISIBLE
        takeOverReturnChrome(page)
        sharedReturnVisualClock.begin()
        renderSharedReturnLayers(1f)
        ViewerReturnDiagnostics.event(viewerReturnId, "viewer_return_scrim_armed") {
            mapOf("launchId" to intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L),
                "alpha" to binding.viewerTransitionScrim.alpha,
                "systemBarProgress" to viewerBars.releaseProgress,
                "barOwner" to viewerBars.owner.name,
                "dragProgress" to interactiveDismissProgress)
        }
        setSharedTransitionCanvasTransparent()
        binding.viewPager.transitionInputLocked = true
        installViewerReturnCallback(operation)
        photoSharedElementHandler.postDelayed({
            if (!isFinishing && !isDestroyed && transitionSession.returnOperation == operation) {
                finishReturnFallback(operation, "viewer_return_timeout")
            }
        }, 2 * PhotoViewerTransition.READY_TIMEOUT_MS + PhotoViewerTransition.DURATION_MS)
        super.finishAfterTransition()
    }

    private fun finishPlatformReturn() {
        if (isFinishing || isDestroyed || sharedReturnFinishRequested) return
        val operation = transitionSession.returnOperation ?: return
        sharedReturnFinishRequested = true
        ViewerReturnDiagnostics.event(viewerReturnId, "viewer_platform_finish") {
            mapOf("animatorStarted" to sharedReturnAnimatorStarted, "transitionTerminal" to sharedReturnTerminal)
        }
        val interrupted = transitionSession.outcome == State.FALLBACK || transitionSession.outcome == State.CANCELLED
        if (sharedReturnView != null && (!sharedReturnAnimatorStarted || interrupted)) {
            // Android may call finish immediately or close after the grid rejected the
            // destination. Let its coordinator unwind before applying the fallback fade.
            photoSharedElementHandler.post {
                if (!isFinishing && !isDestroyed) {
                    finishReturnFallback(operation, "platform_finished_without_motion")
                }
            }
        } else {
            super.finish()
        }
    }

    private fun finishReturnFallback(operation: Token, reason: String) {
        if (transitionSession.outcome == null) transitionSession.terminate(operation, State.FALLBACK, reason)
        returnLeases.releaseAll()
        transitionSession.clean()
        // The fade-out replaces the returning transition, so no last frame will render
        // the page surface for this window.
        viewerBars.releaseToPage()
        super.finish()
        overridePendingTransition(0, if (OxygenClassicMotion.enabled(this)) R.anim.photo_viewer_fade_out else 0)
    }

    private fun concludeStandaloneReturn(operation: Token) {
        when {
            transitionSession.viewerMotionOutcome == State.CANCELLED -> transitionSession.terminate(operation, State.CANCELLED, "viewer_motion_cancelled")
            sharedReturnAnimatorStarted && sharedReturnTerminal && transitionSession.state == State.RETURNING ->
                transitionSession.terminate(operation, State.COMPLETE, "viewer_motion_ended")
            else -> transitionSession.terminate(operation, State.FALLBACK, "viewer_destroyed")
        }
    }

    private fun ownReturnSurface(operation: Token, page: ViewPagerFragment?) {
        returnLeases.acquire(operation, Resource.PROXY) {
            transitionSession.releaseGridPreview(operation)
            if (isFinishing || isDestroyed) clearSharedTransitionSurface(page)
            else finishSharedEntryPage(page)
            sharedReturnView = null
            sharedReturnPage = null
        }
    }

    private fun ownReturnChrome(operation: Token, page: ViewPagerFragment?) {
        takeOverReturnChrome(page)
        val alphas = returnOverlayAlphas
        returnLeases.acquire(operation, Resource.CHROME) {
            alphas.forEach { (view, alpha) -> view.alpha = alpha }
            returnOverlayAlphas = emptyMap()
            restoreBlackViewerCanvas()
            renderSharedViewerCanvas(1f)
        }
    }

    private fun installViewerReturnCallback(operation: Token) {
        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                if (transitionSession.isReturning(operation)) mapReturnSharedElement(names, sharedElements)
                else sharedElements.remove(PhotoViewerTransition.ELEMENT_NAME)
            }

            override fun onSharedElementStart(names: MutableList<String>, elements: MutableList<View>, snapshots: MutableList<View>) {
                if (transitionSession.isReturning(operation)) logReturnCapture("viewer_capture_start", elements)
            }

            override fun onSharedElementEnd(names: MutableList<String>, elements: MutableList<View>, snapshots: MutableList<View>) {
                if (transitionSession.isReturning(operation)) logReturnCapture("viewer_capture_end", elements)
            }
        })
    }

    private fun mapReturnSharedElement(names: List<String>, elements: MutableMap<String, View>) {
        val name = PhotoViewerTransition.ELEMENT_NAME
        val sameSelection = photoSharedElementPath == getCurrentPath() && sharedReturnPage === getCurrentFragment()
        val proxy = sharedReturnView?.takeIf { name in names && sameSelection && it.isAttachedToWindow }
        if (proxy == null) elements.remove(name) else {
            proxy.transitionName = name
            elements[name] = proxy
        }
        ViewerReturnDiagnostics.event(viewerReturnId, "viewer_map") {
            mapOf("requested" to (name in names), "matchesSelection" to sameSelection, "accepted" to (proxy != null))
        }
    }

    private fun logReturnCapture(event: String, elements: List<View>) {
        ViewerReturnDiagnostics.event(viewerReturnId, event) {
            mapOf("includesProxy" to elements.any { it === sharedReturnView }, "finishing" to isFinishing)
        }
    }

    fun refreshMenuItems() {
        val selectedMedium = getCurrentMedium()
        if (selectedMedium == null) {
            runOnUiThread { refreshMediaDependentActionVisibility(null) }
            return
        }
        selectedMedium.isFavorite = mFavoritePaths.contains(selectedMedium.path)
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0

        runOnUiThread {
            val currentMedium = getCurrentMedium()
            refreshMediaDependentActionVisibility(currentMedium)
            if (currentMedium == null) return@runOnUiThread
            val rotationDegrees = getCurrentPhotoFragment()?.mCurrentRotationDegrees ?: 0
            binding.mediumViewerToolbar.menu.apply {
                findItem(R.id.menu_show_on_map).isVisible = visibleBottomActions and BOTTOM_ACTION_SHOW_ON_MAP == 0
                findItem(R.id.menu_slideshow).isVisible = visibleBottomActions and BOTTOM_ACTION_SLIDESHOW == 0
                findItem(R.id.menu_properties).isVisible = visibleBottomActions and BOTTOM_ACTION_PROPERTIES == 0
                findItem(R.id.menu_delete).isVisible = visibleBottomActions and BOTTOM_ACTION_DELETE == 0
                findItem(R.id.menu_share).isVisible = visibleBottomActions and BOTTOM_ACTION_SHARE == 0
                findItem(R.id.menu_edit).isVisible = visibleBottomActions and BOTTOM_ACTION_EDIT == 0 && currentMedium.isImage() && !currentMedium.isSVG()
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
                R.id.menu_edit -> {
                    val medium = getCurrentMedium()
                    if (medium?.isImage() == true && !medium.isSVG()) openGalleryBasicEditor(getCurrentPath())
                    else toast(R.string.editor_unsupported_media)
                }
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
            requestViewerClose("toolbar")
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
        if (!diagnosticViewerOpened && org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
            diagnosticViewerOpened = true
            viewerDiagnosticRequestId = "vw-${java.util.UUID.randomUUID()}"
            val scope = when {
                isPhotoProfile -> photoProfileId ?: "UNKNOWN"
                isFavoriteRequest -> FAVORITES
                mDirectory.isNotEmpty() -> mDirectory
                else -> "UNKNOWN"
            }
            viewerDiagnosticScopeKey = org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(scope)
            viewerDiagnosticOpenMs = (android.os.SystemClock.elapsedRealtime() - viewerDiagnosticStartedAt).coerceAtLeast(0L)
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "Viewer", "viewer_open",
                viewerDiagnosticRequestId,
                fields = mapOf("scopeKey" to viewerDiagnosticScopeKey,
                    "mediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(savedPath),
                    "viewerOpenMs" to viewerDiagnosticOpenMs,
                    "collection" to if (isPhotoProfile) "photo_profile" else if (mDirectory == SHOW_ALL) "all_media" else "folder")
            )
        }
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

        ViewerEntryDiagnostics.event(viewerDiagnosticSessionId, "viewer_selection_resolved") {
            mapOf("mediaId" to GalleryDiagnosticLogger.anonymousId(mPath))
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
            if (firstContext != null && !firstContext.isCurrent(applicationContext)) {
                if (photoDeleteRecovery == null) finish()
                return
            }
            if (!checkFavoriteAccess()) { finish(); return }
            if (items.isEmpty()) {
                if (complete) finish()
                return
            }
            val publication = ++mediaPublicationGeneration
            mediaPublicationTask?.cancel(true)
            mediaPublicationExecutor.queue.clear()
            val previousOrder = mMediaFiles
            val liveAnchor = getCurrentPath().ifEmpty { clicked }
            mediaPublicationTask = mediaPublicationExecutor.submit {
                // The catalog is already sorted in the grid's committed order.
                // Never reshuffle a complete random Photos snapshot on viewer entry.
                val (scopedItems, inheritedOrder) = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcilePhotoCatalog(
                    viewerGridOrder, previousOrder,
                    if (preserveCatalogOrder) searchedViewerMedia(items)
                    else stabilizeRandomViewerOrder(searchedViewerMedia(items), previousOrder),
                    preparedCatalog?.ordinals?.isNotEmpty() == true, complete = complete,
                    random = config.getFolderSorting(PhotoProfileMediaLoader.PATH) and SORT_BY_RANDOM != 0) { it.path }
                val hasSegmentOrdinals = preparedCatalog?.ordinals?.isNotEmpty() == true
                val continuousItems = if (!complete && !hasSegmentOrdinals) {
                    org.fossify.gallery.helpers.ViewerGridOrderPolicy.contiguousPartial(
                        viewerGridOrder, scopedItems, liveAnchor
                    ) { it.path }
                } else scopedItems
                runOnUiThread {
                    if (publication != mediaPublicationGeneration || request != viewerLoadGeneration ||
                        isFinishing || isDestroyed || !checkFavoriteAccess()) return@runOnUiThread
                    if (!complete && !hasSegmentOrdinals && continuousItems.isEmpty()) {
                        // No committed grid ordinal (or no known contiguous run)
                        // means this partial snapshot cannot replace the live pager.
                        return@runOnUiThread
                    }
                    val currentPath = getCurrentPath().ifEmpty { clicked }
                    if (!complete && !hasSegmentOrdinals && continuousItems.none { it.path == currentPath }) {
                        // The user may have swiped while this stage was prepared.
                        // Keep that page and let the next stage classify its path.
                        return@runOnUiThread
                    }
                    if (continuousItems.isEmpty()) {
                        if (complete) finish()
                        return@runOnUiThread
                    }
                    if (!inheritedOrder) {
                        viewerGridOrder = null
                        org.fossify.gallery.helpers.GalleryPerfTrace.event(
                            "viewer_grid_order_authoritative_fallback", scopedItems.size,
                            preparedCatalog?.revision ?: org.fossify.gallery.helpers.PhotoContentRevision.current())
                    }
                    if (initialized) {
                        gotMedia(ArrayList<ThumbnailItem>(continuousItems), refetchViewPagerPosition = true, fullCatalog = complete)
                    } else {
                        mMediaFiles = ArrayList(continuousItems)
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
            if (!preparedCatalog.isComplete) photoNavigation.publish(false, preparedCatalog.before, preparedCatalog.after)
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
        val preview = org.fossify.gallery.helpers.ViewerOpeningWindow.photoPreview(
            viewerGridEvidence?.candidates, clicked) { it.path }
        if (preview != null) {
            // The grid already validated this cache preview. Do not shrink it to five neighbors.
            publish(preview, complete = false)
            loadFullCollection()
            return
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
        // Coalesce the lookup/validation work too, not just the final adapter update.
        if (swipePublicationGate.isMoving) {
            photoCatalogAdvanceQueued = true
            return
        }
        if (photoCatalogUpdateTask != null) { photoCatalogAdvanceQueued = true; return }
        val selectedPath = getCurrentPath().ifEmpty { mPath }
        val previouslyPublished = mMediaFiles.toList()
        val request = ++photoCatalogUpdateGeneration
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        val profile = photoProfileId
        val session = photoSessionId
        val app = applicationContext
        val liveAnchor = getCurrentPath().ifEmpty { selectedPath }
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
                val (orderedVisible, inheritedOrder) = if (candidate != null && selectedCheck != null) {
                    org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcilePhotoCatalog(
                        viewerGridOrder, previouslyPublished,
                        org.fossify.gallery.helpers.ViewerSearchPolicy.filter(
                            candidate.media, viewerSearchQuery.orEmpty()), candidate.ordinals.isNotEmpty(),
                        complete = candidate.isComplete,
                        random = config.getFolderSorting(PhotoProfileMediaLoader.PATH) and SORT_BY_RANDOM != 0) { it.path }
                } else emptyList<Medium>() to false
                val visible = if (candidate != null && !candidate.isComplete && candidate.ordinals.isEmpty()) {
                    org.fossify.gallery.helpers.ViewerGridOrderPolicy.contiguousPartial(
                        viewerGridOrder, orderedVisible, liveAnchor
                    ) { it.path }
                } else orderedVisible
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
                        if (!inheritedOrder && viewerGridOrder != null) {
                            viewerGridOrder = null
                            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                                "viewer_grid_order_authoritative_fallback", visible.size, candidate.revision)
                        }
                        if (!photoCatalogMode || photoCatalogRevision != candidate.revision) {
                            photoPageAccess.reset(candidate.revision)
                        }
                        photoCatalogMode = true
                        photoCatalogRevision = candidate.revision
                        photoCatalogComplete = candidate.isComplete
                        if (!candidate.isComplete) photoNavigation.publish(false, candidate.before, candidate.after)
                        photoFailureVisible = false
                        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_catalog_upgrade", visible.size)
                        photoPageAccess.approve(listOf(selectedPath), candidate.revision)
                        if (changed) {
                            // The producer already sorted this catalog in the grid's
                            // canonical order; don't prepend the initial five-page window.
                            gotMedia(ArrayList<ThumbnailItem>(visible), refetchViewPagerPosition = true,
                                fullCatalog = candidate.isComplete)
                        } else {
                            // COMPLETE is a state transition even when the visible order is identical.
                            updatePhotoBoundaryWait()
                            validatePhotoPageWindow(fullCatalogPosition(mPos))
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
        if (!progressiveInternalViewer || !checkFavoriteAccess()) return false
        // The complete catalog may be ready before it has been applied to the
        // pager. Do not misreport a temporary five-page edge as actual EOF.
        if (isPhotoProfile && swipePublicationGate.isMoving && swipePublicationGate.hasPending) {
            deferredBoundaryDirection = forward
            return true
        }
        if ((isPhotoProfile && photoCatalogComplete && appliedViewerCatalogComplete) || (!isPhotoProfile &&
            photoNavigation.state == org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.State.COMPLETE)) return false
        val path = getCurrentPath()
        if (photoFailureVisible) {
            appliedViewerCatalogComplete = false
            photoFailureVisible = false
            photoFallbackStarted = false
        }
        if (path.isEmpty() || !photoNavigation.request(path, forward)) return false
        if (viewerDiagnosticRequestId != null) viewerDiagnosticExpandCount++
        if (viewerBoundaryStarted == 0L) {
            viewerBoundaryStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
            viewerDiagnosticBoundaryStartedAt = android.os.SystemClock.elapsedRealtime()
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
        if (!isPhotoProfile && !isFavoriteRequest) expandCommittedFolder?.invoke()
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
                if (!isExternalIntent() && destination != binding.viewPager.currentItem) {
                    explicitUserNavigationPending = true
                }
                binding.viewPager.setCurrentItem(destination,
                    kotlin.math.abs(destination - binding.viewPager.currentItem) == 1 &&
                        org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
                validatePhotoPageWindow(fullCatalogPosition(destination))
            }
        }
    }

    private fun finishViewerBoundaryTrace() {
        if (viewerBoundaryStarted == 0L) return
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled() && viewerDiagnosticRequestId != null) {
            val duration = (android.os.SystemClock.elapsedRealtime() - viewerDiagnosticBoundaryStartedAt).coerceAtLeast(0L)
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "MediaPipeline", "media_viewer_expand",
                viewerDiagnosticRequestId, mapOf("boundaryWaitMs" to duration, "visibleCount" to activePagerAdapter?.count)
            )
        }
        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_boundary_wait", viewerBoundaryStarted, activePagerAdapter?.count)
        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_expand_finished", viewerBoundaryStarted, activePagerAdapter?.count)
        viewerBoundaryStarted = 0L
        viewerDiagnosticBoundaryStartedAt = 0L
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
        val groupingPath = when {
            isFavoriteRequest -> FAVORITES
            mShowAll -> SHOW_ALL
            else -> mDirectory
        }
        val grouped = MediaFetcher(applicationContext).groupMedia(ArrayList(matches), groupingPath)
        return grouped.filterIsInstance<Medium>()
    }

    /** Keeps an already exposed random neighborhood stable as any source completes. */
    private fun stabilizeRandomViewerOrder(media: List<Medium>, previous: List<Medium> = mMediaFiles): List<Medium> {
        val sortPath = when {
            isPhotoProfile -> PhotoProfileMediaLoader.PATH
            isFavoriteRequest -> FAVORITES
            mShowAll -> SHOW_ALL
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
        if (mediaTransitionRunning && activePagerAdapter != null && getCurrentFragment() != null && !entryWaitingForRenderer) {
            // Keep the opening frame light: the selected page is already ready,
            // and swiping stays locked until the shared image reaches fullscreen.
            // Install its validated neighbor window immediately after that motion.
            transitionPendingAdjacentMedia = ArrayList(media)
            return
        }
        gotMedia(ArrayList<ThumbnailItem>(media), refetchViewPagerPosition = true, fullCatalog = false)
        logPagerReady("viewer_adjacent_pager_ready", adjacentReadyStarted, media.size, mPath, requireMultiplePages = true)
    }

    private fun publishPendingAdjacentAfterTransition() {
        val pending = transitionPendingAdjacentMedia ?: return
        transitionPendingAdjacentMedia = null
        if (isFavoriteRequest) org.fossify.gallery.helpers.GalleryPerfTrace.event(
            "viewer_favorite_adjacent_after_transition", pending.size
        )
        if (!isFinishing && !isDestroyed && !appliedViewerCatalogComplete) publishAdjacent(pending)
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
        viewerGridOrder = takeGridEvidence(FAVORITES, path, favoriteSessionId, false)
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
                    completeSource: Boolean = true,
                    openingWindow: Boolean = false,
                    onOpeningResult: ((Boolean) -> Unit)? = null
                ) {
                    val stage = ++viewerStageGeneration
                    val validationStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val app = applicationContext
                    val previous = mMediaFiles
                    val liveAnchor = getCurrentPath().ifEmpty { path }
                    submitViewerAccess(request) {
                        val scoped = if (openingWindow) {
                            org.fossify.gallery.helpers.ViewerOpeningWindow.committed(items, liveAnchor) { it.path }
                        } else items
                        if (openingWindow && scoped.isEmpty()) {
                            runOnUiThread {
                                if (stage == viewerStageGeneration && request == viewerAccessGeneration &&
                                    load == viewerLoadGeneration && !isFinishing && !isDestroyed) onOpeningResult?.invoke(false)
                            }
                            return@submitViewerAccess
                        }
                        val candidates = if (completeSource) scoped else (scoped + previous).distinctBy { it.path }
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
                                else onOpeningResult?.invoke(false)
                                return@runOnUiThread
                            }
                            if (batch == null && !completeSource && FavoriteAccessSessionManager.isActive(token)) {
                                onOpeningResult?.invoke(false)
                                return@runOnUiThread
                            }
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
                                val safePartial = if (completeSource) searched else
                                    org.fossify.gallery.helpers.ViewerGridOrderPolicy.contiguousPartial(
                                        viewerGridOrder, searched, liveAnchor
                                    ) { it.path }
                                runOnUiThread {
                                    if (stage != viewerStageGeneration || publication != mediaPublicationGeneration || request != viewerAccessGeneration ||
                                        load != viewerLoadGeneration || isFinishing || isDestroyed ||
                                        !FavoriteAccessSessionManager.isPublicationCurrent(batch.validation)) return@runOnUiThread
                                    if (!completeSource && (safePartial.isEmpty() ||
                                            safePartial.none { it.path == getCurrentPath().ifEmpty { path } })) {
                                        // A missing path in a preview is UNKNOWN, and the live
                                        // page may have changed since this stage was prepared.
                                        onOpeningResult?.invoke(false)
                                        return@runOnUiThread
                                    }
                                    gotMedia(ArrayList<ThumbnailItem>(safePartial), refetchViewPagerPosition = true, fullCatalog = completeSource)
                                    onOpeningResult?.invoke(true)
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
                val openingCandidates = viewerGridEvidence?.takeIf {
                    it.revision == FolderContentRevision.current() && it.candidates.isNotEmpty()
                }?.candidates
                if (!openingCandidates.isNullOrEmpty()) {
                    val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    adjacentReadyStarted = started
                    publishValidatedFavorites(
                        openingCandidates, started, completeSource = false, openingWindow = true,
                        onOpeningResult = { published ->
                            if (published) loadCompleteFavorites() else validateNeighborsThenContinue(first)
                        }
                    )
                } else {
                    validateNeighborsThenContinue(first)
                }
            }
        }
    }

    /** Authentication has succeeded; only the clicked path gates the first image. */
    private fun startFolderViewer(existingToken: String?, authenticatedRoot: String?, onInvalid: () -> Unit) {
        folderScanGate.cancel()
        folderScanWatchdog.stop()
        folderHandoffExpansionRunning = false
        folderCompletePreparing = false
        expandCommittedFolder = null
        appliedViewerCatalogComplete = false
        photoNavigation.publish(false)
        val request = ++viewerAccessGeneration
        val load = ++viewerLoadGeneration
        val selectedStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        viewerLoadTask?.stopFetching()
        val path = mPath
        if (viewerGridOrder == null) {
            viewerGridOrder = takeGridEvidence(path.getParentPath(), path, existingToken, false)
        }
        stopReusedFolderWatch()
        val catalogId = intent.getStringExtra(FolderViewerSnapshotStore.EXTRA_ID)
            .takeIf { existingToken == null && !isExternalIntent() }
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
                    if (request != viewerAccessGeneration || load != viewerLoadGeneration || isFinishing || isDestroyed) return
                    val attempt = folderScanGate.begin() ?: return
                    viewerScanNeedsRetry = false
                    fun failed(error: Exception) {
                        if (!folderScanGate.finish(attempt) || load != viewerLoadGeneration || isFinishing || isDestroyed) return
                        folderScanWatchdog.stop()
                        viewerStageGeneration++
                        viewerScanNeedsRetry = true
                        folderCompletePreparing = false
                        folderHandoffExpansionRunning = false
                        photoNavigation.cancel()
                        photoNavigation.publish(false)
                        finishViewerBoundaryTrace()
                        viewerLoading.setLoading(false)
                        showErrorToast(error)
                    }
                    fun armDeadline() {
                        folderScanWatchdog.stop()
                        folderScanWatchdog.arm {
                            if (folderScanGate.current(attempt)) {
                                viewerLoadTask?.reportTimedOut()
                                failed(java.util.concurrent.TimeoutException("Viewer scan stalled; retry at boundary"))
                                viewerLoadTask?.stopFetching()
                            }
                        }
                    }
                    retryViewerScan = { scanRemaining() }
                    fun subscribeToGridScan(shared: org.fossify.gallery.asynctasks.GetMediaAsynctask): Boolean {
                        val found = LinkedHashMap<String, Medium>()
                        var lastKeys: List<String> = emptyList()
                        var lastAnchor = ""
                        val subscriber = object : org.fossify.gallery.helpers.FolderScanSubscriber {
                            override fun onPreview(stage: org.fossify.gallery.helpers.DisplayedSourceStage, rows: List<Medium>) {
                                if (stage != org.fossify.gallery.helpers.DisplayedSourceStage.INDEX_PREVIEW &&
                                    stage != org.fossify.gallery.helpers.DisplayedSourceStage.SCAN_PARTIAL) return
                                if (!folderScanGate.current(attempt) || request != viewerAccessGeneration ||
                                    load != viewerLoadGeneration || isFinishing || isDestroyed) return
                                rows.forEach { found.putIfAbsent(it.path, it) }
                                val anchor = getCurrentPath().ifEmpty { path }
                                // A full committed window cannot grow with later discoveries.
                                if (lastKeys.size == org.fossify.gallery.helpers.ViewerCommittedWindow.RADIUS * 2 + 1 &&
                                    anchor == lastAnchor) return
                                val window = org.fossify.gallery.helpers.ViewerSharedScanWindow.extend(
                                    viewerGridOrder, found, anchor)
                                val keys = window.map { it.path }
                                if (keys.isEmpty() || keys == lastKeys) return
                                lastKeys = keys
                                lastAnchor = anchor
                                publishFolderCandidates(request, load, token, path, window, completeSource = false)
                            }

                            override fun onComplete(media: List<org.fossify.gallery.models.ThumbnailItem>, result: org.fossify.gallery.helpers.GalleryScanResult) {
                                folderScanUnsubscribe?.invoke()
                                folderScanUnsubscribe = null
                                if (!folderScanGate.finish(attempt) || request != viewerAccessGeneration ||
                                    load != viewerLoadGeneration || isFinishing || isDestroyed) return
                                folderScanWatchdog.stop()
                                val fullMedia = media.filterIsInstance<Medium>()
                                viewerScanNeedsRetry = !result.complete
                                if (!result.complete) {
                                    val anchor = getCurrentPath().ifEmpty { path }
                                    val window = org.fossify.gallery.helpers.ViewerSharedScanWindow.extend(
                                        viewerGridOrder, fullMedia.associateBy { it.path }, anchor)
                                    if (window.isNotEmpty()) {
                                        publishFolderCandidates(request, load, token, path, window, completeSource = false)
                                    }
                                    return
                                }
                                folderCompletePreparing = true
                                val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                                publishFolderCandidates(request, load, token, path, fullMedia, completeSource = true) {
                                    logPagerReady("viewer_full_list_prepared", fullStarted, fullMedia.size)
                                }
                            }

                            override fun onFailure(error: Exception) {
                                folderScanUnsubscribe?.invoke()
                                folderScanUnsubscribe = null
                                failed(error)
                            }

                            override fun onCancelled() {
                                folderScanUnsubscribe?.invoke()
                                folderScanUnsubscribe = null
                                failed(java.util.concurrent.CancellationException("Viewer scan cancelled; retry at boundary"))
                            }
                        }
                        if (!shared.addFolderSubscriber(subscriber)) return false
                        folderScanUnsubscribe = { shared.removeFolderSubscriber(subscriber) }
                        return true
                    }
                    val shared = org.fossify.gallery.helpers.RunningFolderScan.current(mDirectory, token)
                    if (shared != null && subscribeToGridScan(shared)) return
                    val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    val fallbackStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    viewerLoadTask = GetMediaAsynctask(app, mDirectory, showAll = false,
                        folderSessionId = token, requestId = load, diagnosticLoadReason = "VIEWER",
                        folderPriorityPaths = org.fossify.gallery.helpers.ViewerCommittedWindow.around(
                            viewerGridOrder.orEmpty(), getCurrentPath().ifEmpty { path }) { it },
                        onScanCancelled = { failed(java.util.concurrent.CancellationException("Viewer scan cancelled; retry at boundary")) },
                        onFolderPreview = { result, candidates ->
                            if (folderScanGate.current(attempt) && request == viewerAccessGeneration && load == viewerLoadGeneration && result.accessCurrent()) {
                                if (folderScanGate.progress(attempt, candidates.size)) armDeadline()
                                publishFolderCandidates(request, load, token, path,
                                    candidates.filterIsInstance<Medium>(), completeSource = false)
                            }
                        },
                        onFailure = { failed(it) }
                    ) { items ->
                        if (folderScanGate.finish(attempt) && request == viewerAccessGeneration && load == viewerLoadGeneration && !isFinishing && !isDestroyed) {
                            folderScanWatchdog.stop()
                            val fullMedia = items.filterIsInstance<Medium>()
                            viewerScanNeedsRetry = viewerLoadTask?.scanResult?.complete != true
                            folderCompletePreparing = !viewerScanNeedsRetry
                            publishFolderCandidates(request, load, token, path, fullMedia, completeSource = !viewerScanNeedsRetry) {
                                logPagerReady("viewer_full_list_prepared", fullStarted, fullMedia.size)
                                org.fossify.gallery.helpers.GalleryPerfTrace.end(
                                    "viewer_full_scan_fallback", fallbackStarted, fullMedia.size
                                )
                            }
                        }
                    }
                    // A large but advancing directory must not be truncated by a total-time limit.
                    // Queue wait and lack of new accepted rows are bounded; every real advance renews it.
                    armDeadline()
                    try {
                        viewerLoadTask?.executeOnExecutor(org.fossify.gallery.helpers.GalleryMediaScanExecutor)
                    } catch (error: java.util.concurrent.RejectedExecutionException) {
                        viewerLoadTask?.reportRejectedExecution()
                        failed(error)
                        viewerLoadTask?.stopFetching()
                    }
                }
                var openingWindowInstalled = false
                fun completeFromCatalogOrScan() {
                    if (catalogId == null) {
                        if (!openingWindowInstalled) expandCommittedFolder?.invoke()
                        scanRemaining()
                        return
                    }
                    folderCompletePreparing = true
                    val fullStarted = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    fun rejected(reason: String) {
                        folderCompletePreparing = false
                        org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_miss_$reason", fullStarted)
                        if (!openingWindowInstalled) expandCommittedFolder?.invoke()
                        scanRemaining()
                    }
                    submitViewerAccess(request) {
                        val catalog = runCatching {
                            FolderViewerSnapshotStore.copyForViewer(app, catalogId, path.getParentPath(), path)
                        }.getOrNull()
                        runOnUiThread {
                            if (request != viewerAccessGeneration || load != viewerLoadGeneration ||
                                isFinishing || isDestroyed) return@runOnUiThread
                            if (catalog == null) {
                                rejected("stale_or_missing")
                                return@runOnUiThread
                            }
                            publishFolderCandidates(
                                request, load, token, path, catalog.items,
                                catalogFreshness = catalog.freshness,
                                onStale = { rejected("validation") }
                            ) {
                                org.fossify.gallery.helpers.GalleryPerfTrace.end("viewer_catalog_hit", fullStarted, catalog.items.size)
                                logPagerReady("viewer_full_list_prepared", fullStarted, catalog.items.size)
                                val access = catalog.freshness.checkpoint.access ?: return@publishFolderCandidates
                                val refresh = Runnable {
                                    if (request != viewerAccessGeneration || load != viewerLoadGeneration ||
                                        isFinishing || isDestroyed) return@Runnable
                                    if (access != DirectoryAccessState.capture(app) ||
                                        !FolderUnlockSessionManager.isActive(token)) {
                                        finish()
                                        return@Runnable
                                    }
                                    FolderContentRevision.changed()
                                    folderScanGate.cancel()
                                    folderScanWatchdog.stop()
                                    viewerStageGeneration++
                                    viewerLoadTask?.stopFetching("viewer_catalog_folder_changed")
                                    appliedViewerCatalogComplete = false
                                    folderCompletePreparing = false
                                    photoNavigation.publish(false)
                                    scanRemaining()
                                }
                                reusedFolderAccess = access
                                reusedFolderWatch = FolderScanObservation(File(catalog.freshness.checkpoint.canonicalFolder)) {
                                    folderChangeHandler.removeCallbacks(refresh)
                                    folderChangeHandler.postDelayed(refresh, 250L)
                                }
                                if (catalog.freshness.observing == false) folderChangeHandler.postDelayed(refresh, 250L)
                            }
                        }
                    }
                }
                expandCommittedFolder = {
                    val evidence = viewerGridEvidence
                    if (!folderCompletePreparing && !folderHandoffExpansionRunning && evidence != null &&
                        evidence.revision == FolderContentRevision.current() && evidence.candidates.isNotEmpty() &&
                        photoNavigation.state != org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.State.COMPLETE) {
                        val anchor = getCurrentPath().ifEmpty { path }
                        val candidates = org.fossify.gallery.helpers.ViewerCommittedWindow.around(evidence.candidates, anchor) { it.path }
                        if (candidates.isNotEmpty()) {
                            folderHandoffExpansionRunning = true
                            publishFolderCandidates(request, load, token, path, candidates, completeSource = false) {
                                folderHandoffExpansionRunning = false
                            }
                        }
                    }
                }
                val openingCandidates = viewerGridEvidence?.takeIf {
                    it.revision == FolderContentRevision.current() && it.candidates.isNotEmpty()
                }?.candidates
                fun validateNeighborWindowThenContinue() {
                    val window = neighborWindow
                    if (window == null) {
                        completeFromCatalogOrScan()
                        return
                    }
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
                if (!openingCandidates.isNullOrEmpty()) {
                    val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
                    adjacentReadyStarted = started
                    publishFolderCandidates(
                        request, load, token, path, openingCandidates, completeSource = false, openingWindow = true,
                        onOpeningResult = { published ->
                            openingWindowInstalled = published
                            if (published) completeFromCatalogOrScan() else validateNeighborWindowThenContinue()
                        }
                    )
                } else {
                    validateNeighborWindowThenContinue()
                }
            }
        }
    }

    /** Validate every candidate off the UI thread before exposing adjacent pages. */
    private fun publishFolderCandidates(
        request: Long, load: Long, token: String?, selectedPath: String,
        items: List<Medium>,
        catalogFreshness: FolderViewerSnapshotStore.Freshness? = null,
        onStale: (() -> Unit)? = null,
        completeSource: Boolean = true,
        openingWindow: Boolean = false,
        onOpeningResult: ((Boolean) -> Unit)? = null,
        onPublished: (() -> Unit)? = null
    ) {
        val stage = ++viewerStageGeneration
        val contentRevision = FolderContentRevision.current()
        val app = applicationContext
        val folder = selectedPath.getParentPath()
        val publishedOrder = mMediaFiles
        val liveAnchor = getCurrentPath().ifEmpty { selectedPath }
        submitViewerAccess(request) {
            val scopedItems = if (openingWindow) {
                org.fossify.gallery.helpers.ViewerOpeningWindow.committed(items, liveAnchor) { it.path }
            } else items
            if (openingWindow && scopedItems.isEmpty()) {
                runOnUiThread {
                    if (stage == viewerStageGeneration && request == viewerAccessGeneration && load == viewerLoadGeneration &&
                        !isFinishing && !isDestroyed) onOpeningResult?.invoke(false)
                }
                return@submitViewerAccess
            }
            val candidates = if (completeSource) scopedItems else
                (scopedItems + publishedOrder).distinctBy { it.path }
            val searchedItems = searchedViewerMedia(candidates)
            val directItems = searchedItems.filter {
                it.path.getParentPath() == folder ||
                    (it.type == TYPE_PORTRAITS && it.path.getParentPath().getParentPath() == folder)
            }
            val paths = LinkedHashSet<String>().apply { add(selectedPath); directItems.forEach { add(it.path) } }
            val authorized = runCatching {
                FolderUnlockSessionManager.authorizedPaths(token, paths, app.config::isFolderProtected)
            }.getOrDefault(emptySet())
            val safe = directItems.filter { item ->
                item.path in authorized && item.deletedTS == 0L && item.type and app.config.filterMedia != 0 &&
                    (app.config.shouldShowHidden || !item.name.startsWith('.')) &&
                    runCatching { File(item.path).isFile && File(item.path).canRead() }.getOrDefault(false)
            }.toCollection(ArrayList())
            if (!completeSource) MediaFetcher(app).sortMedia(safe, config.getFolderSorting(folder))
            val random = config.getFolderSorting(folder) and SORT_BY_RANDOM != 0
            val ordered = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcile(
                if (completeSource && !random && catalogFreshness == null) null else viewerGridOrder,
                publishedOrder, safe, random
            ) { it.path }
            val continuous = if (!completeSource) {
                org.fossify.gallery.helpers.ViewerGridOrderPolicy.contiguousPartial(
                    viewerGridOrder, ordered, liveAnchor
                ) { it.path }
            } else ordered
            val freshnessCurrent = catalogFreshness == null || (safe.size == directItems.size && runCatching {
                catalogFreshness.isCurrent(app, folder)
            }.getOrDefault(false))
            val directPaths = directItems.mapTo(HashSet(directItems.size)) { it.path }
            runOnUiThread {
                if (stage != viewerStageGeneration || request != viewerAccessGeneration || load != viewerLoadGeneration ||
                    isFinishing || isDestroyed) return@runOnUiThread
                if (!freshnessCurrent || contentRevision != FolderContentRevision.current() || catalogFreshness?.observing == false) {
                    onStale?.invoke()
                    return@runOnUiThread
                }
                folderHandoffExpansionRunning = false
                val currentPath = getCurrentPath().ifEmpty { selectedPath }
                val currentDenied = currentPath in directPaths && currentPath !in authorized
                if (!FolderUnlockSessionManager.isActive(token) || selectedPath !in authorized || currentDenied) {
                    finish(); return@runOnUiThread
                }
                if (!completeSource && (continuous.isEmpty() || continuous.none { it.path == currentPath })) {
                    // Do not let an older scan anchor turn a newly selected page
                    // into the item at the same integer position.
                    onOpeningResult?.invoke(false)
                    return@runOnUiThread
                }
                if (continuous.isEmpty()) { finish(); return@runOnUiThread }
                // A late scan must retain the photo the user has already swiped to.
                mPath = currentPath
                gotMedia(ArrayList<ThumbnailItem>(continuous), refetchViewPagerPosition = true, fullCatalog = completeSource)
                onPublished?.invoke()
                onOpeningResult?.invoke(true)
            }
        }
    }

    private fun authenticateViewerRequest() {
        showSystemUI()
        viewerStatusSurfaceVisible = true
        updateViewerSystemBars()
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
            viewerGridOrder = takeGridEvidence(
                PhotoProfileMediaLoader.PATH + profile.orEmpty() + session.orEmpty(), clicked, session, true)
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                if (viewerGridOrder == null) "viewer_grid_order_authoritative_fallback" else "viewer_grid_order_handoff",
                viewerGridOrder?.size)
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
            continueAfterFailedFolderCapability()
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
        // Keep the source grid in place until the selected viewer image has
        // reported a drawable. The bounded timeout handles unavailable media.
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

        val allMediaNeighbors = if (mShowAll && !isExternalIntent()) takeNeighborWindow(
            org.fossify.gallery.helpers.ViewerSourceType.FOLDER, SHOW_ALL
        ) else null
        if (allMediaNeighbors != null && mMediaFiles.isEmpty()) {
            val app = applicationContext
            val request = viewerAccessGeneration
            stagedInternalViewer = true
            retryViewerScan = { refreshViewPager(true) }
            submitViewerAccess(request) {
                val visibility = org.fossify.gallery.helpers.MediaPageVisibility(app, true) { path ->
                    FolderUnlockSessionManager.findProtectedAncestor(
                        path.getParentPath(), app.config::isFolderProtected
                    ) == null
                }
                val safe = allMediaNeighbors.items.filter { visibility.allows(it.path) && File(it.path).canRead() }
                runOnUiThread {
                    if (request != viewerAccessGeneration || isDestroyed || isFinishing) return@runOnUiThread
                    if (safe.any { it.path == mPath } && !appliedViewerCatalogComplete) publishAdjacent(safe)
                }
            }
        }

        scheduleInitialViewerPublication()

        // show the selected image asap, while loading the rest in the background to allow swiping between them. Might be needed at third party intents
        if (mMediaFiles.isEmpty() && mPath.isNotEmpty() && mDirectory != FAVORITES) {
            val filename = mPath.getFilenameFromPath()
            val folder = mPath.getParentPath()
            val type = getTypeFromPath(mPath)
            val medium = Medium(null, filename, mPath, folder, 0, 0, 0, type, 0, false, 0L, 0L)
            mMediaFiles.add(medium)
            gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
        }

        if (!isPhotoProfile && (!stagedInternalViewer || mShowAll)) refreshViewPager(true)
        binding.viewPager.offscreenPageLimit = 2

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

    internal fun scheduleInitialViewerPublication() {
        val initialPublication = mediaPublicationGeneration
        binding.viewPager.onGlobalLayout {
            // A ready catalog can arrive before the first layout. Its publication
            // owns initialization; a late singleton callback must not cancel it.
            if (!isFinishing && !isDestroyed && initialPublication == mediaPublicationGeneration && activePagerAdapter == null) {
                if (mMediaFiles.isNotEmpty()) {
                    // Publish the selected page while entry is postponed so
                    // capture can use the renderer's laid-out content bounds.
                    if (mediaTransitionRunning && mediaEntry?.active != true) {
                        initialViewerPublicationPending = true
                    } else {
                        gotMedia(mMediaFiles as ArrayList<ThumbnailItem>, refetchViewPagerPosition = true)
                        checkSlideshowOnEnter()
                    }
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
            if (pagerAdapter.count > 0) checkSlideshowOnEnter()
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
                    viewerStatusSurfaceVisible = false
                    updateViewerSystemBars()
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
            viewerStatusSurfaceVisible = true
            updateViewerSystemBars()
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
            viewerGridOrder = org.fossify.gallery.helpers.ViewerGridOrderPolicy.renamed(viewerGridOrder, oldPath, newPath)
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

    private fun getCurrentSharedElementView(path: String, deferTakeover: Boolean = false, returnRequestId: Long = 0L): View? {
        lastProxyRejection = null
        return when (val fragment = getCurrentFragment()) {
            is PhotoFragment -> {
                val proxy = fragment.sharedElementViewFor(path, deferTakeover)
                lastProxyRejection = if (proxy == null) fragment.lastProxyRejection else null
                proxy
            }
            is VideoFragment -> fragment.sharedElementViewFor(path, deferTakeover, returnRequestId)
            else -> null
        }
    }

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
        refreshMediaDependentActionVisibility(currentMedium)
        binding.bottomActions.bottomFavorite.beVisibleIf(visibleBottomActions and BOTTOM_ACTION_TOGGLE_FAVORITE != 0 && currentMedium?.getIsInRecycleBin() == false)
        binding.bottomActions.bottomFavorite.setOnLongClickListener { toast(R.string.toggle_favorite); true }
        binding.bottomActions.bottomFavorite.setOnClickListener {
            toggleFavorite()
        }

        binding.bottomActions.bottomEdit.setOnLongClickListener { toast(R.string.edit); true }
        binding.bottomActions.bottomEdit.setOnClickListener {
            val medium = getCurrentMedium()
            if (medium?.isImage() == true && !medium.isSVG()) openGalleryBasicEditor(getCurrentPath())
            else toast(R.string.editor_unsupported_media)
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

        binding.bottomActions.bottomResize.setOnLongClickListener { toast(org.fossify.commons.R.string.resize); true }
        binding.bottomActions.bottomResize.setOnClickListener {
            resizeImage()
        }
    }

    /** Recompute media-capability actions without reinstalling listeners or rebuilding the bar. */
    private fun refreshMediaDependentActionVisibility(medium: Medium?) {
        val visibleBottomActions = if (config.bottomActions) config.visibleBottomActions else 0
        ViewerMediaActionVisibility.apply(
            binding.bottomActions.bottomEdit,
            binding.bottomActions.bottomResize,
            binding.bottomActions.bottomRotate,
            visibleBottomActions,
            medium
        )

        val menu = binding.mediumViewerToolbar.menu
        val edit = menu.findItem(R.id.menu_edit)
        val resize = menu.findItem(R.id.menu_resize)
        val rotate = menu.findItem(R.id.menu_rotate)
        val copyToClipboard = menu.findItem(R.id.menu_copy_to_clipboard)
        val print = menu.findItem(R.id.menu_print)
        val saveAs = menu.findItem(R.id.menu_save_as)
        val rotationDegrees = if (medium?.isImage() == true) getCurrentPhotoFragment()?.mCurrentRotationDegrees ?: 0 else 0
        edit?.isVisible = visibleBottomActions and BOTTOM_ACTION_EDIT == 0 && medium?.isImage() == true && medium?.isSVG() == false
        resize?.isVisible = visibleBottomActions and BOTTOM_ACTION_RESIZE == 0 && medium?.isImage() == true
        rotate?.isVisible = visibleBottomActions and BOTTOM_ACTION_ROTATE == 0 && medium?.isImage() == true
        rotate?.setShowAsAction(
            if (rotationDegrees != 0) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_IF_ROOM
        )
        copyToClipboard?.isVisible = medium?.isImage() == true
        print?.isVisible = medium?.isImage() == true || medium?.isRaw() == true
        saveAs?.isVisible = rotationDegrees != 0
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
        val operationId = "delete-${android.os.SystemClock.elapsedRealtime()}"
        if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
            org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaOperation", "delete_requested", operationId,
                mapOf("count" to 1, "index" to mPos, "catalogCount" to getCurrentMedia().size,
                    "mediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(path),
                    "progressive" to progressiveInternalViewer, "recycleBin" to config.useRecycleBin,
                    "photoProfile" to isPhotoProfile,
                    "lastVisitedMediaId" to lastVisitedPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                    "navigationEpoch" to userNavigationEpoch,
                    "lastMoveForward" to lastUserNavigationForward,
                    "revision" to org.fossify.gallery.helpers.PhotoContentRevision.current())
            )
        }

        // Bind the deletion to the page and ordering that the user confirmed.
        // A later SAF / Manage Media prompt may outlive the current pager page.
        val deleteSnapshot = if (!isExternalIntent()) ViewerDeleteSnapshot(
            deletedPath = path,
            previousMedia = mMediaFiles.toList(),
            lastVisitedPath = lastVisitedPath,
            previousPosition = mPos,
            orderedPaths = viewerGridOrder?.toList(),
            lastMoveForward = lastUserNavigationForward,
            navigationEpoch = userNavigationEpoch,
            profileId = photoProfileId,
            sessionId = photoSessionId,
            generation = ++photoDeleteGeneration,
            revision = org.fossify.gallery.helpers.PhotoContentRevision.current(),
            operationId = operationId
        ) else null

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
                                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                                    org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaOperation", "delete_completed", operationId,
                                    mapOf("success" to true, "catalogCount" to getCurrentMedia().size,
                                        "mediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(fileDirItem.path),
                                        "photoProfile" to isPhotoProfile)
                                )
                                FolderContentRevision.changed()
                                hideDeletedMedium(fileDirItem.path, deleteSnapshot, photoRevisionAlreadyChanged = true)
                            } else {
                                if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                                    org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaOperation", "delete_failed", operationId,
                                    mapOf("success" to false)
                                )
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
            handleDeletion(fileDirItem, deleteSnapshot, operationId)
        }
    }

    private fun handleDeletion(fileDirItem: FileDirItem, deleteSnapshot: ViewerDeleteSnapshot?, operationId: String) {
        checkManageMediaOrHandleSAFDialogSdk30(fileDirItem.path) {
            if (!it) {
                return@checkManageMediaOrHandleSAFDialogSdk30
            }

            tryDeleteFileDirItem(fileDirItem, false, true) { deleted ->
                if (deleted) {
                    if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.INFO, "MediaOperation", "delete_completed", operationId,
                        mapOf("success" to true, "catalogCount" to getCurrentMedia().size,
                                        "mediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(fileDirItem.path),
                                        "photoProfile" to isPhotoProfile)
                    )
                    FolderContentRevision.changed()
                    hideDeletedMedium(fileDirItem.path, deleteSnapshot)
                } else {
                    if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                        org.fossify.gallery.helpers.DiagnosticLevel.WARN, "MediaOperation", "delete_failed", operationId,
                        mapOf("catalogCount" to getCurrentMedia().size)
                    )
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

    private fun hideDeletedMedium(
        path: String,
        deleteSnapshot: ViewerDeleteSnapshot? = null,
        photoRevisionAlreadyChanged: Boolean = false
    ) {
        successfullyDeletedViewerPaths.add(path)
        org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(path)
        if (path !in mIgnoredPaths) mIgnoredPaths.add(path)
        if (isPhotoProfile) {
            recoverAfterPhotoDeletion(path, deleteSnapshot, photoRevisionAlreadyChanged)
            return
        }

        // Keep the exact path history captured when deletion was confirmed. The
        // pager index can point at a different medium after the deleted row is removed.
        val recovery = deleteSnapshot?.takeIf {
            it.deletedPath == path && it.generation == photoDeleteGeneration
        }
        val source = mMediaFiles.toList()
        val ignored = mIgnoredPaths.toHashSet()
        val favoriteToken = favoriteSessionId
        val folderToken = activeFolderUnlockSessionId
        val sourceDirectory = mDirectory
        val recycleRoot = recycleBinPath
        val app = applicationContext
        val request = ++mediaPublicationGeneration
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.queue.clear()
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val media = source.filterTo(ArrayList()) { medium ->
                medium.path !in ignored &&
                    (!isFavoriteRequest || FavoriteAccessSessionManager.isCapturedPath(favoriteToken, medium.path)) &&
                    (folderToken == null || runCatching {
                        FolderUnlockSessionManager.isMediaAuthorized(folderToken, medium.path, app.config::isFolderProtected)
                    }.getOrDefault(false)) &&
                    (sourceDirectory != RECYCLE_BIN || medium.path.startsWith(recycleRoot))
            }
            val preferredUserPath = recovery?.let {
                org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
                    it.deletedPath, it.navigationEpoch, userNavigationEpoch, confirmedUserPagePath
                )
            }
            val targetPath = recovery?.let {
                org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.resolve(
                    it.previousMedia, media, path,
                    preferredUserPath ?: path,
                    it.lastVisitedPath, it.previousPosition,
                    it.orderedPaths, it.lastMoveForward
                )?.let { index -> media.getOrNull(index)?.path }
            }
            val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(media)
            val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(media)
            val authorized = media.mapTo(HashSet(media.size)) { it.path }
            runOnUiThread {
                if (request != mediaPublicationGeneration || isFinishing || isDestroyed ||
                    !checkFavoriteAccess()) return@runOnUiThread
                if (folderToken != null && !FolderUnlockSessionManager.isActive(folderToken)) {
                    finish()
                    return@runOnUiThread
                }
                if (recovery != null && targetPath == null) {
                    // The currently published window may contain only the deleted
                    // page. Requery the source before deciding that no safe neighbor exists.
                    pendingViewerDeleteRecovery = recovery
                    viewerLoading.setLoading(true)
                    refreshViewPager(true)
                } else if (media.isEmpty()) {
                    deleteDirectoryIfEmpty()
                    finish()
                } else {
                    val latestUserPath = recovery?.let {
                        org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
                            it.deletedPath, it.navigationEpoch, userNavigationEpoch, confirmedUserPagePath
                        )
                    }?.takeIf { it in paths }
                    pendingViewerDeleteRecovery = null
                    refreshUI(
                        media, true, positions, paths, authorized,
                        preferredPath = latestUserPath ?: targetPath
                    )
                }
            }
        }
    }

    private fun recoverAfterPhotoDeletion(
        deletedPath: String,
        snapshot: ViewerDeleteSnapshot?,
        photoRevisionAlreadyChanged: Boolean
    ) {
        val base = snapshot?.takeIf {
            it.deletedPath == deletedPath && it.profileId == photoProfileId &&
                it.sessionId == photoSessionId && it.generation == photoDeleteGeneration
        } ?: ViewerDeleteSnapshot(
            deletedPath, mMediaFiles.toList(), lastVisitedPath, mPos,
            viewerGridOrder?.toList(), lastUserNavigationForward, userNavigationEpoch,
            profileId = photoProfileId, sessionId = photoSessionId,
            generation = ++photoDeleteGeneration,
            revision = org.fossify.gallery.helpers.PhotoContentRevision.current(),
            operationId = snapshot?.operationId ?: "delete-recovery-${android.os.SystemClock.elapsedRealtime()}"
        )
        // Block catalog listeners before the content-revision invalidation can
        // queue an ordinary publication against the soon-to-be stale page.
        photoCatalogUpdating = true
        val revision = if (photoRevisionAlreadyChanged) {
            org.fossify.gallery.helpers.PhotoContentRevision.current()
        } else {
            org.fossify.gallery.helpers.PhotoContentRevision.changed()
        }
        invalidatePhotoCatalogForRequery()
        val anchor = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.recoveryAnchor(
            base.previousMedia, deletedPath, base.lastVisitedPath, base.previousPosition,
            base.orderedPaths, base.lastMoveForward
        )
        val recovery = base.copy(revision = revision, recoveryAnchorPath = anchor)
        photoDeleteRecovery = recovery
        logViewerDeleteSnapshot(
            "photo_delete_recovery_started", recovery,
            mapOf(
                "anchorMediaId" to anchor?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "previousIndex" to base.previousPosition,
                "previousCount" to base.previousMedia.size,
                "lastVisitedMediaId" to base.lastVisitedPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "gridOrderCount" to base.orderedPaths?.size,
                "lastMoveForward" to base.lastMoveForward,
                "navigationEpoch" to base.navigationEpoch,
                "revisionAlreadyChanged" to photoRevisionAlreadyChanged
            )
        )
        // The producer's old focus can point at the just-deleted file. Replace it
        // before the next staged scan reads focusPath() for filesystem batches.
        org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.focus(
            photoProfileId, photoSessionId, anchor.orEmpty())
        viewerLoading.setLoading(true)
        // Keep the existing Fragment attached and let the existing staged loader
        // produce the replacement. No empty/local-only list is published first.
        refreshViewPager(true, photoRevisionAlreadyChanged = true, catalogAlreadyInvalidated = true,
            invalidateDisplayedContent = false)
    }

    private fun isDirEmpty(media: ArrayList<Medium>, fullCatalog: Boolean): Boolean {
        return if (media.isEmpty()) {
            if (fullCatalog || !isPhotoProfile) {
                deleteDirectoryIfEmpty()
                finish()
                true
            } else false
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
                viewerGridOrder = org.fossify.gallery.helpers.ViewerGridOrderPolicy.renamed(viewerGridOrder, oldPath, newPath)
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

    private fun refreshViewPager(
        refetchPosition: Boolean = false,
        photoRevisionAlreadyChanged: Boolean = false,
        catalogAlreadyInvalidated: Boolean = false,
        invalidateDisplayedContent: Boolean = true
    ) {
        if (!checkFavoriteAccess()) return
        stopReusedFolderWatch()
        if (invalidateDisplayedContent) {
            org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.invalidate(getCurrentPath())
        }
        // A viewer requery may follow edits in external editors or providers.
        // Invalidating a return checkpoint is cheaper than showing stale media.
        FolderContentRevision.changed()
        if (isPhotoProfile) {
            if (!catalogAlreadyInvalidated) invalidatePhotoCatalogForRequery()
            if (!photoRevisionAlreadyChanged) org.fossify.gallery.helpers.PhotoContentRevision.changed()
        }
        val isRandomSorting = config.getFolderSorting(if (mShowAll) SHOW_ALL else mDirectory) and SORT_BY_RANDOM != 0
        if (isPhotoProfile || mShowAll || !isRandomSorting || isExternalIntent() || pendingViewerDeleteRecovery != null) {
            val request = ++viewerLoadGeneration
            if (mShowAll) viewerScanNeedsRetry = false
            viewerLoadTask?.stopFetching()
            viewerLoadTask = GetMediaAsynctask(
                applicationContext, mDirectory, isPickImage = false, isPickVideo = false, showAll = mShowAll,
                reuseAllMediaIndex = mShowAll,
                cachedMediaFallback = if (mShowAll) mMediaFiles.map { it.copy() } else emptyList(),
                onPartialFailure = {
                    if (request == viewerLoadGeneration && !isFinishing && !isDestroyed && checkFavoriteAccess()) toast(R.string.media_load_partial_error)
                },
                favoritePathsOverride = null,
                favoriteSessionId = if (isFavoriteRequest) favoriteSessionId else null,
                photoProfileId = photoProfileId,
                photoSessionId = photoSessionId,
                photoAnchorPath = if (isPhotoProfile && photoDeleteRecovery != null)
                    photoDeleteRecovery?.recoveryAnchorPath else getCurrentPath(),
                onPhotoResult = { result -> if (isPhotoProfile && request == viewerLoadGeneration) {
                    if (photoDeleteRecovery != null) recoverPhotoDeletionFromLoadResult(result)
                    else {
                        photoCatalogUpdating = false
                        consumePhotoCatalogAdvance()
                    }
                } },
                onFailure = {
                    if (request == viewerLoadGeneration && !isFinishing && !isDestroyed &&
                        pendingViewerDeleteRecovery != null) {
                        viewerLoading.setLoading(false)
                        viewerScanNeedsRetry = true
                        showErrorToast(it)
                    } else if (mShowAll && stagedInternalViewer && request == viewerLoadGeneration && !isFinishing) {
                        viewerScanNeedsRetry = true
                        photoNavigation.cancel()
                        viewerLoading.setLoading(false)
                        showErrorToast(it)
                    } else if (isPhotoProfile) {
                        // Keep the currently displayed selection when a recoverable
                        // rescan fails, but leave protected catalog pages fail-closed.
                        if (photoDeleteRecovery != null) viewerLoading.setLoading(false)
                        showErrorToast(it)
                    }
                }
            ) {
                if (request == viewerLoadGeneration && !isFinishing && !isDestroyed && checkFavoriteAccess()) {
                    if (isPhotoProfile) {
                        // The loader's COMPLETE path has freshly checked the whole
                        // catalog. Old per-page approvals must never survive it.
                        if (photoDeleteRecovery != null) return@GetMediaAsynctask
                        photoCatalogUpdating = false
                        consumePhotoCatalogAdvance()
                        return@GetMediaAsynctask
                    }
                    val fullCatalog = viewerLoadTask?.scanResult?.complete == true
                    if (mShowAll) viewerScanNeedsRetry = !fullCatalog
                    val pendingRecovery = pendingViewerDeleteRecovery
                    if (pendingRecovery != null) {
                        recoverViewerDeletionFromLoadResult(it, fullCatalog, request, pendingRecovery)
                        return@GetMediaAsynctask
                    }
                    val ordered = if (mShowAll) {
                        val shown = searchedViewerMedia(it.filterIsInstance<Medium>())
                        ArrayList<ThumbnailItem>(stabilizeRandomViewerOrder(shown))
                    } else it
                    gotMedia(ordered, refetchViewPagerPosition = refetchPosition, fullCatalog = fullCatalog)
                }
            }.also { it.execute() }
        }
    }

    /** Resolve non-profile deletion against the latest authorized source scan. */
    private fun recoverViewerDeletionFromLoadResult(
        items: ArrayList<ThumbnailItem>,
        fullCatalog: Boolean,
        request: Long,
        recovery: ViewerDeleteSnapshot
    ) {
        if (pendingViewerDeleteRecovery?.operationId != recovery.operationId) return
        val sourceDirectory = mDirectory
        val favoriteRequest = isFavoriteRequest
        val favoriteToken = favoriteSessionId
        val folderToken = activeFolderUnlockSessionId
        val recycleRoot = recycleBinPath
        val ignored = mIgnoredPaths.toHashSet()
        val preferredUserPath = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
            recovery.deletedPath, recovery.navigationEpoch, userNavigationEpoch, confirmedUserPagePath
        )
        val app = applicationContext
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.queue.clear()
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val visible = items.filterIsInstance<Medium>().filterTo(ArrayList()) { medium ->
                medium.path != recovery.deletedPath && medium.path !in ignored &&
                    (!favoriteRequest || FavoriteAccessSessionManager.isCapturedPath(favoriteToken, medium.path)) &&
                    (folderToken == null || runCatching {
                        FolderUnlockSessionManager.isMediaAuthorized(folderToken, medium.path, app.config::isFolderProtected)
                    }.getOrDefault(false)) &&
                    (sourceDirectory != RECYCLE_BIN || medium.path.startsWith(recycleRoot))
            }
            val candidates = searchedViewerMedia(visible).toCollection(ArrayList())
            val targetIndex = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.resolve(
                recovery.previousMedia, candidates, recovery.deletedPath,
                preferredUserPath ?: recovery.deletedPath,
                recovery.lastVisitedPath, recovery.previousPosition,
                recovery.orderedPaths, recovery.lastMoveForward
            )
            val targetPath = targetIndex?.let { candidates.getOrNull(it)?.path }
            val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(candidates)
            val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(candidates)
            val authorized = candidates.mapTo(HashSet(candidates.size)) { it.path }
            runOnUiThread {
                if (request != viewerLoadGeneration || isFinishing || isDestroyed ||
                    pendingViewerDeleteRecovery?.operationId != recovery.operationId ||
                    !checkFavoriteAccess()) return@runOnUiThread
                if (folderToken != null && !FolderUnlockSessionManager.isActive(folderToken)) {
                    pendingViewerDeleteRecovery = null
                    finish()
                    return@runOnUiThread
                }
                val latestUserPath = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
                    recovery.deletedPath, recovery.navigationEpoch, userNavigationEpoch, confirmedUserPagePath
                )?.takeIf { it in paths }
                val selectedPath = latestUserPath ?: targetPath
                if (selectedPath != null) {
                    pendingViewerDeleteRecovery = null
                    viewerLoading.setLoading(false)
                    if (progressiveInternalViewer) photoNavigation.publish(fullCatalog)
                    refreshUI(
                        candidates, true, positions, paths, authorized,
                        preferredPath = selectedPath
                    )
                    logViewerDeleteSnapshot(
                        "viewer_delete_restore_applied", recovery,
                        mapOf(
                            "stage" to if (fullCatalog) "complete" else "partial",
                            "candidateCount" to candidates.size,
                            "expectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(selectedPath)
                        )
                    )
                } else if (fullCatalog) {
                    pendingViewerDeleteRecovery = null
                    viewerLoading.setLoading(false)
                    if (candidates.isEmpty()) deleteDirectoryIfEmpty()
                    finish()
                } else {
                    // A partial scan cannot prove that the historical page or its
                    // neighbors are gone. Keep the existing pager attached and fail safe.
                    viewerLoading.setLoading(false)
                    viewerScanNeedsRetry = true
                    toast(R.string.media_load_partial_error)
                }
            }
        }
    }

    /** Resolve a deleted page against the fresh scoped result before publishing any replacement. */
    private fun recoverPhotoDeletionFromLoadResult(
        result: org.fossify.gallery.helpers.PhotoLoadResult,
        preferredCurrentPath: String? = null
    ) {
        val recovery = photoDeleteRecovery ?: return
        if (recovery.profileId != photoProfileId || recovery.sessionId != photoSessionId ||
            recovery.generation != photoDeleteGeneration || recovery.revision != result.context.revision) {
            logViewerDeleteSnapshot(
                "photo_delete_recovery_stage_ignored", recovery,
                mapOf("stage" to result.stage.name, "reason" to "request_mismatch", "candidateCount" to result.media.size),
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG
            )
            return
        }
        if (!result.context.isCurrent(applicationContext)) {
            logViewerDeleteSnapshot(
                "photo_delete_recovery_stage_ignored", recovery,
                mapOf("stage" to result.stage.name, "reason" to "stale_context", "candidateCount" to result.media.size),
                org.fossify.gallery.helpers.DiagnosticLevel.DEBUG
            )
            return
        }
        logViewerDeleteSnapshot(
            "photo_delete_recovery_stage", recovery,
            mapOf(
                "stage" to result.stage.name,
                "candidateCount" to result.media.size,
                "needsUnlock" to result.access.needsUnlock,
                "preferredCurrentMediaId" to preferredCurrentPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "lastVisitedMediaId" to recovery.lastVisitedPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "navigationEpoch" to userNavigationEpoch,
                "anchorMediaId" to recovery.recoveryAnchorPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId)
            ),
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG
        )
        if (org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.shouldFinish(
                result.stage == org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE,
                result.media, result.access.needsUnlock
            )) {
            logViewerDeleteSnapshot(
                "photo_delete_restore_finished_empty", recovery,
                mapOf("stage" to result.stage.name, "candidateCount" to result.media.size)
            )
            photoDeleteRecovery = null
            photoCatalogUpdating = false
            viewerLoading.setLoading(false)
            finish()
            return
        }
        if (result.access.needsUnlock && result.media.isEmpty()) {
            logViewerDeleteSnapshot(
                "photo_delete_recovery_waiting_for_unlock", recovery,
                mapOf("stage" to result.stage.name, "candidateCount" to 0)
            )
            // Mixed profiles can still publish separately validated public pages.
            // Only an entirely inaccessible result must wait for authorization.
            viewerLoading.setLoading(false)
            return
        }

        val app = applicationContext
        val confirmedAfterDelete = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
            recovery.deletedPath, recovery.navigationEpoch, userNavigationEpoch, confirmedUserPagePath)
        val livePathAtStart = preferredCurrentPath ?: confirmedAfterDelete ?: recovery.deletedPath
        val query = viewerSearchQuery.orEmpty()
        val pathOrder = recovery.orderedPaths ?: viewerGridOrder
        val candidate = result.media
        val ignoredPaths = mIgnoredPaths.toHashSet()
        val publicationRequest = ++photoCatalogUpdateGeneration
        ViewerAccessCoordinator.cancel(photoCatalogUpdateTask)
        val work = try {
            ViewerAccessCoordinator.submit {
                val scopedCandidate = candidate.filter { it.path != recovery.deletedPath && it.path !in ignoredPaths }
                val candidatePaths = scopedCandidate.mapTo(HashSet(scopedCandidate.size)) { it.path }
                val searchable = org.fossify.gallery.helpers.ViewerSearchPolicy.filter(scopedCandidate, query)
                val targetIndex = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.resolve(
                    recovery.previousMedia, searchable, recovery.deletedPath, livePathAtStart,
                    recovery.lastVisitedPath, recovery.previousPosition,
                    pathOrder, recovery.lastMoveForward
                )
                if (targetIndex == null) {
                    logViewerDeleteSnapshot(
                        "photo_delete_restore_unresolved", recovery,
                        mapOf(
                            "stage" to result.stage.name,
                            "candidateCount" to searchable.size,
                            "catalogComplete" to (result.stage == org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE),
                            "hasCurrentPath" to (livePathAtStart in candidatePaths),
                            "hasLastVisitedPath" to (recovery.lastVisitedPath in candidatePaths),
                            "gridOrderCount" to pathOrder?.size
                        )
                    )
                    if (result.stage == org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE &&
                        !result.access.needsUnlock) {
                        // A complete catalog with no provably nearby page must
                        // close safely, never jump to an arbitrary first row.
                        org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_delete_no_trusted_neighbor",
                            searchable.size)
                        runOnUiThread {
                            if (photoDeleteRecovery?.generation == recovery.generation && !isFinishing && !isDestroyed) {
                                photoDeleteRecovery = null
                                photoCatalogUpdating = false
                                viewerLoading.setLoading(false)
                                finish()
                            }
                        }
                    }
                    return@submit
                }
                val targetPath = searchable[targetIndex].path
                val catalog = org.fossify.gallery.helpers.PhotoSessionViewerCatalogStore.latest(
                    app, recovery.profileId, recovery.sessionId, targetPath
                )
                val rawWindow = catalog?.media
                    ?.takeIf { window -> window.any { it.path == targetPath } }
                    ?.filter { it.path != recovery.deletedPath && it.path !in ignoredPaths }
                    ?: run {
                        val start = (targetIndex - org.fossify.gallery.helpers.ViewerNeighborWindow.NEIGHBOR_RADIUS).coerceAtLeast(0)
                        val end = (targetIndex + org.fossify.gallery.helpers.ViewerNeighborWindow.NEIGHBOR_RADIUS + 1)
                            .coerceAtMost(searchable.size)
                        searchable.subList(start, end)
                    }
                val freshWindow = org.fossify.gallery.helpers.ViewerSearchPolicy.filter(rawWindow, query)
                val (orderedWindow, inheritedOrder) = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcilePhotoCatalog(
                    pathOrder, recovery.previousMedia, freshWindow,
                    catalog?.ordinals?.isNotEmpty() == true,
                    complete = catalog?.isComplete == true,
                    random = config.getFolderSorting(PhotoProfileMediaLoader.PATH) and SORT_BY_RANDOM != 0
                ) { it.path }
                val selectedIndex = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.resolve(
                    recovery.previousMedia, orderedWindow, recovery.deletedPath, livePathAtStart,
                    recovery.lastVisitedPath, recovery.previousPosition,
                    pathOrder, recovery.lastMoveForward
                ) ?: orderedWindow.indexOfFirst { it.path == targetPath }.takeIf { it >= 0 }
                if (selectedIndex == null || orderedWindow.isEmpty()) {
                    logViewerDeleteSnapshot(
                        "photo_delete_restore_rejected", recovery,
                        mapOf(
                            "reason" to "window_has_no_restorable_path",
                            "stage" to result.stage.name,
                            "initialTargetMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(targetPath),
                            "windowCount" to orderedWindow.size
                        )
                    )
                    return@submit
                }
                val selectedPath = orderedWindow[selectedIndex].path
                val selectionSource = when {
                    selectedPath == livePathAtStart && confirmedAfterDelete != null -> "post_delete_user"
                    selectedPath == livePathAtStart -> "current_path"
                    selectedPath == recovery.lastVisitedPath -> "last_visited"
                    pathOrder?.contains(selectedPath) == true -> "grid_order_neighbor"
                    else -> "viewer_window_neighbor"
                }
                logViewerDeleteSnapshot(
                    "photo_delete_restore_candidate", recovery,
                    mapOf(
                        "stage" to result.stage.name,
                        "initialTargetMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(targetPath),
                        "selectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(selectedPath),
                        "selectionSource" to selectionSource,
                        "candidateCount" to searchable.size,
                        "windowCount" to orderedWindow.size,
                        "selectedIndex" to selectedIndex,
                        "initialTargetIndex" to targetIndex,
                        "catalogComplete" to (catalog?.isComplete == true),
                        "gridOrderInherited" to inheritedOrder
                    )
                )
                val radius = org.fossify.gallery.helpers.ViewerNeighborWindow.NEIGHBOR_RADIUS
                val accessStart = (selectedIndex - radius).coerceAtLeast(0)
                val accessEnd = (selectedIndex + radius + 1).coerceAtMost(orderedWindow.size)
                val accessCandidates = orderedWindow.subList(accessStart, accessEnd).toList()
                val safe = runCatching {
                    org.fossify.gallery.helpers.PhotoViewerSnapshotStore.validateNeighborWindow(
                        app, recovery.profileId, recovery.sessionId, accessCandidates, selectedPath
                    ) { Thread.currentThread().isInterrupted }
                }.getOrNull()
                if (safe == null) {
                    logViewerDeleteSnapshot(
                        "photo_delete_restore_rejected", recovery,
                        mapOf(
                            "reason" to "neighbor_validation_failed",
                            "stage" to result.stage.name,
                            "selectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(selectedPath),
                            "windowCount" to orderedWindow.size
                        )
                    )
                    return@submit
                }
                val positions = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(orderedWindow)
                val paths = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(orderedWindow)
                runOnUiThread {
                    val currentRecovery = photoDeleteRecovery
                    if (currentRecovery?.generation != recovery.generation || publicationRequest != photoCatalogUpdateGeneration ||
                        isFinishing || isDestroyed ||
                        !safe.context.isCurrent(app) || recovery.revision != org.fossify.gallery.helpers.PhotoContentRevision.current()) return@runOnUiThread
                    if (recovery.sessionId != null &&
                        !org.fossify.gallery.helpers.PhotoProfileAccessSessionManager.isActive(app, recovery.sessionId)) {
                        closeUnauthorizedFavorites()
                        return@runOnUiThread
                    }
                    val userPath = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.postDeleteUserPath(
                        recovery.deletedPath, recovery.navigationEpoch, userNavigationEpoch,
                        confirmedUserPagePath)
                    if (userPath != null && userPath != selectedPath &&
                        userPath in candidatePaths && safe.media.none { it.path == userPath }) {
                        logViewerDeleteSnapshot(
                            "photo_delete_restore_retargeted", recovery,
                            mapOf(
                                "selectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(selectedPath),
                                "newUserMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(userPath),
                                "candidateCount" to candidatePaths.size
                            )
                        )
                        // A real newer gesture wins, but must receive its own
                        // validated page window before the pager can move there.
                        recoverPhotoDeletionFromLoadResult(result, userPath)
                        return@runOnUiThread
                    }
                    val chosenPath = userPath?.takeIf { path ->
                        safe.media.any { it.path == path }
                    } ?: selectedPath
                    if (safe.media.none { it.path == chosenPath }) {
                        logViewerDeleteSnapshot(
                            "photo_delete_restore_rejected", recovery,
                            mapOf(
                                "reason" to "chosen_path_not_authorized",
                                "selectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(chosenPath),
                                "validatedCount" to safe.media.size
                            )
                        )
                        return@runOnUiThread
                    }
                    photoCatalogMode = true
                    photoCatalogRevision = recovery.revision
                    photoCatalogUpdating = false
                    photoCatalogComplete = catalog?.isComplete == true
                    photoNavigation.publish(photoCatalogComplete, catalog?.before ?: org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.Boundary.UNKNOWN,
                        catalog?.after ?: org.fossify.gallery.helpers.PhotoViewerCatalogCoordinator.Boundary.UNKNOWN)
                    photoPageAccess.reset(recovery.revision)
                    photoPageAccess.approve(safe.media.map { it.path }, recovery.revision)
                    if (!inheritedOrder && pathOrder != null) {
                        viewerGridOrder = null
                        org.fossify.gallery.helpers.GalleryPerfTrace.event(
                            "viewer_grid_order_authoritative_fallback", orderedWindow.size, recovery.revision)
                    }
                    photoDeleteRecovery = null
                    viewerLoading.setLoading(false)
                    refreshUI(ArrayList(orderedWindow), true, positions, paths, preferredPath = chosenPath)
                    val pagerIndex = binding.viewPager.currentItem
                    val pagerPath = activePagerAdapter?.media?.getOrNull(pagerIndex)?.path
                    logViewerDeleteSnapshot(
                        "photo_delete_restore_applied", recovery,
                        mapOf(
                            "stage" to result.stage.name,
                            "expectedMediaId" to org.fossify.gallery.helpers.GalleryDiagnosticLogger.anonymousId(chosenPath),
                            "pagerMediaId" to pagerPath?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                            "expectedIndex" to mPos,
                            "pagerIndex" to pagerIndex,
                            "pagerCount" to (activePagerAdapter?.count ?: 0),
                            "matchesExpected" to (pagerPath == chosenPath),
                            "validatedCount" to safe.media.size
                        )
                    )
                    validatePhotoPageWindow(mPos)
                    consumePhotoCatalogAdvance()
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            photoDeleteRecovery?.let { active ->
                logViewerDeleteSnapshot(
                    "photo_delete_restore_rejected", active,
                    mapOf("reason" to "worker_capacity", "stage" to result.stage.name)
                )
            }
            viewerLoading.setLoading(false)
            showErrorToast(java.io.IOException("Photo viewer recovery is waiting for available worker capacity"))
            null
        }
        photoCatalogUpdateTask = work
    }

    private fun gotMedia(
        thumbnailItems: ArrayList<ThumbnailItem>,
        ignorePlayingVideos: Boolean = false,
        refetchViewPagerPosition: Boolean = false,
        fullCatalog: Boolean = false
    ) {
        if (!checkFavoriteAccess()) return
        val currentFolderRevision = FolderContentRevision.current()
        val currentPhotoRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
        val pendingFullRequest = pendingFullCatalogRequest ?: transitionPendingSwipePublication
            ?.takeIf { it.fullCatalog }
            ?.let { PendingFullCatalogRequest(it.generation, it.folderRevision, it.photoRevision) }
        if (isFavoriteRequest && !fullCatalog &&
            publishedFavoriteFullCatalogRevision == (currentFolderRevision to currentPhotoRevision)) {
            if (BuildConfig.DEBUG) org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_favorite_partial_catalog_ignored_after_complete",
                thumbnailItems.size
            )
            return
        }
        if (!fullCatalog && pendingFullRequest != null &&
            pendingFullRequest.generation == mediaPublicationGeneration &&
            pendingFullRequest.folderRevision == currentFolderRevision &&
            pendingFullRequest.photoRevision == currentPhotoRevision) {
            // A delayed first-frame/adjacent-page snapshot can arrive after the
            // complete catalog was requested. It carries less information and
            // must not cancel or invalidate that richer publication.
            if (isFavoriteRequest) org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_favorite_partial_catalog_ignored",
                thumbnailItems.size
            )
            return
        }
        if (fullCatalog) pendingFullCatalogRequest = null
        else if (pendingFullRequest != null) pendingFullCatalogRequest = null
        val request = ++mediaPublicationGeneration
        if (fullCatalog) pendingFullCatalogRequest = PendingFullCatalogRequest(
            request, currentFolderRevision, currentPhotoRevision
        )
        mediaPublicationTask?.cancel(true)
        mediaPublicationExecutor.queue.clear()
        val source = thumbnailItems.toList()
        val previous = mMediaFiles
        val ignored = mIgnoredPaths.toHashSet()
        val favoriteRequest = isFavoriteRequest
        val favoriteToken = favoriteSessionId
        val priorFolderRevision = publishedFolderRevision
        val priorPhotoRevision = publishedPhotoRevision
        val sourceFolderRevision = FolderContentRevision.current()
        val sourcePhotoRevision = org.fossify.gallery.helpers.PhotoContentRevision.current()
        mediaPublicationTask = mediaPublicationExecutor.submit {
            val media = source.asSequence().filter {
                it is Medium && it.path !in ignored &&
                    (!favoriteRequest || FavoriteAccessSessionManager.isCapturedPath(favoriteToken, it.path))
            }.map { it as Medium }.toCollection(ArrayList())
            if (isPhotoProfile && !isExternalIntent() && viewerGridOrder != null) {
                val ordered = org.fossify.gallery.helpers.ViewerGridOrderPolicy.reconcile(
                    viewerGridOrder, previous, media, false) { it.path }
                media.clear()
                media.addAll(ordered)
            }
            val positionIndex = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.positionIndex(media)
            val pathIndex = org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.pathIndex(media)
            val authorizedPaths = media.mapTo(HashSet(media.size)) { it.path }
            if (favoriteRequest) org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_favorite_catalog_prepared", media.size
            )
            val revisionsUnchanged = priorFolderRevision == FolderContentRevision.current() &&
                priorPhotoRevision == org.fossify.gallery.helpers.PhotoContentRevision.current()
            val unchanged = revisionsUnchanged && org.fossify.gallery.helpers.ViewerMediaUpdatePolicy.sameCatalog(previous, media)
            runOnUiThread {
                if (request != mediaPublicationGeneration || isFinishing || isDestroyed || !checkFavoriteAccess()) {
                    if (pendingFullCatalogRequest?.generation == request) pendingFullCatalogRequest = null
                    return@runOnUiThread
                }
                if (sourceFolderRevision != FolderContentRevision.current() ||
                    sourcePhotoRevision != org.fossify.gallery.helpers.PhotoContentRevision.current()) {
                    if (pendingFullCatalogRequest?.generation == request) pendingFullCatalogRequest = null
                    if (isPhotoProfile) consumePhotoCatalogAdvance() else refreshViewPager(true)
                    return@runOnUiThread
                }
                if (isDirEmpty(media, fullCatalog)) {
                    if (pendingFullCatalogRequest?.generation == request) pendingFullCatalogRequest = null
                    return@runOnUiThread
                }
                if (unchanged && activePagerAdapter != null) {
                    if (pendingFullCatalogRequest?.generation == request) pendingFullCatalogRequest = null
                    if (progressiveInternalViewer && fullCatalog) {
                        appliedViewerCatalogComplete = true
                        folderCompletePreparing = false
                        photoNavigation.publish(true)
                    }
                    if (favoriteRequest && fullCatalog) {
                        publishedFavoriteFullCatalogRevision =
                            sourceFolderRevision to sourcePhotoRevision
                    }
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
                    fullCatalog, request, sourceFolderRevision, sourcePhotoRevision
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
        if (isFavoriteRequest) org.fossify.gallery.helpers.GalleryPerfTrace.event(
            "viewer_favorite_catalog_apply", publication.media.size
        )
        if (publication.generation != mediaPublicationGeneration || isFinishing || isDestroyed ||
            !checkFavoriteAccess()) {
            if (pendingFullCatalogRequest?.generation == publication.generation) pendingFullCatalogRequest = null
            return
        }
        if (publication.folderRevision != FolderContentRevision.current() ||
            publication.photoRevision != org.fossify.gallery.helpers.PhotoContentRevision.current()) {
            // Never apply a stale ordering/authorization snapshot after mutations.
            if (pendingFullCatalogRequest?.generation == publication.generation) pendingFullCatalogRequest = null
            if (isPhotoProfile) consumePhotoCatalogAdvance()
            else refreshViewPager(true)
            return
        }
        if (mediaTransitionRunning && (mediaEntry?.active != true ||
                (activePagerAdapter != null && getCurrentFragment() != null && !entryWaitingForRenderer))) {
            // Only the initial publication may create a page while entry is
            // postponed. Keep that renderer alive through capture and handoff.
            transitionPendingSwipePublication = publication
            return
        }
        val livePath = getCurrentPath().ifEmpty { mPath }
        val retainedPath = if (livePath in publication.pathIndex) livePath else {
            if (!publication.fullCatalog) return
            val recovered = org.fossify.gallery.helpers.ViewerDeletionNavigationPolicy.resolve(
                mMediaFiles, publication.media, livePath, null, lastVisitedPath, mPos,
                viewerGridOrder, lastUserNavigationForward)
            if (recovered == null) { finish(); return }
            publication.media[recovered].path
        }
        if (activeFolderUnlockSessionId != null && !FolderUnlockSessionManager.isActive(activeFolderUnlockSessionId)) {
            finish(); return
        }
        val started = org.fossify.gallery.helpers.GalleryPerfTrace.begin()
        if (pendingFullCatalogRequest?.generation == publication.generation) pendingFullCatalogRequest = null
        if (publication.fullCatalog) transitionPendingAdjacentMedia = null
        if (isFavoriteRequest && publication.fullCatalog) {
            publishedFavoriteFullCatalogRevision = publication.folderRevision to publication.photoRevision
        }
        if (progressiveInternalViewer) {
            appliedViewerCatalogComplete = publication.fullCatalog
            if (publication.fullCatalog) folderCompletePreparing = false
            if (!isPhotoProfile || publication.fullCatalog) photoNavigation.publish(publication.fullCatalog)
        }
        refreshUI(
            publication.media, publication.refetchPosition, publication.positionIndex,
            publication.pathIndex, publication.authorizedPaths, preferredPath = retainedPath
        )
        if (initialViewerPublicationAwaitingAdapter && (activePagerAdapter?.count ?: 0) > 0) {
            initialViewerPublicationAwaitingAdapter = false
            if (!mediaTransitionRunning) binding.viewPager.transitionInputLocked = false
        }
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
        authorizedPaths: Set<String>? = null,
        preferredPath: String? = null
    ) {
        // Capture the currently displayed path before replacing the backing list.
        // The original click path is only a fallback; background stages must not
        // pull the user back after they have already swiped to another medium.
        val currentPathBeforeUpdate = preferredPath?.takeIf { path ->
            pathIndex?.containsKey(path) ?: media.any { it.path == path }
        }
            ?: getCurrentPath().ifEmpty { mPath }
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
            } ?: org.fossify.gallery.helpers.ViewerPositionPolicy.resolve(media, currentPathBeforeUpdate, previousPosition)
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
                    recordViewerDiagnosticPublication(prepared)
                    scheduleSwipe()
                }
            }
        } else {
            updatePagerItems(mMediaFiles, positionIndex)
            recordViewerDiagnosticPublication(mMediaFiles)
        }

        refreshMenuItems()
        checkOrientation()
        initBottomActions()
    }

    private fun recordViewerDiagnosticPublication(media: List<Medium>) {
        if (!org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) return
        val requestId = viewerDiagnosticRequestId ?: return
        viewerDiagnosticPublicationCount++
        org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
            org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "Viewer", "media_viewer_publication", requestId,
            fields = mapOf(
                "scopeKey" to viewerDiagnosticScopeKey,
                "viewerSessionId" to viewerDiagnosticSessionId.takeIf { it != 0L },
                "catalogState" to if (appliedViewerCatalogComplete) "FINAL"
                    else if (media.size > 1) "PARTIAL" else "PREVIEW",
                "publicationCount" to viewerDiagnosticPublicationCount,
                "itemCount" to media.size,
                "positionIndex" to mPos,
                "gridVersion" to viewerGridEvidence?.version,
                "handoffCount" to viewerGridEvidence?.paths?.size,
                "revision" to org.fossify.gallery.helpers.PhotoContentRevision.current(),
                "folderRevision" to FolderContentRevision.current(),
                "containsSelected" to media.any { it.path == getCurrentPath() },
                "mediaId" to getCurrentPath().let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "beforeId" to media.getOrNull(mPos - 1)?.path?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "afterId" to media.getOrNull(mPos + 1)?.path?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                "orderMode" to if (appliedViewerCatalogComplete &&
                    config.getFolderSorting(if (isPhotoProfile) PhotoProfileMediaLoader.PATH else mDirectory) and SORT_BY_RANDOM == 0)
                    "AUTHORITATIVE" else if (viewerGridOrder != null) "COMMITTED_GRID" else "FALLBACK"
            )
        )
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
        if (isMediaTransitionRunning()) return
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

    override fun imageRendered(path: String, page: ViewPagerFragment) {
        if (!viewerRendererObserved && ViewerEntryDiagnostics.enabled(viewerDiagnosticSessionId) &&
            !isFinishing && !isDestroyed && page === getCurrentFragment() && path == getCurrentPath()) {
            val renderer = currentEntryRenderer()
            viewerRendererObserved = true
            ViewerEntryDiagnostics.event(viewerDiagnosticSessionId, "viewer_first_renderer_signal") {
                mapOf("mediaId" to GalleryDiagnosticLogger.anonymousId(path),
                    "renderer" to when {
                        renderer == null -> "UNKNOWN"
                        page is VideoFragment && renderer is ImageView -> "VIDEO_PREVIEW"
                        else -> renderer.javaClass.simpleName
                    },
                    "drawableReady" to if (renderer is ImageView) renderer.drawable != null else "UNKNOWN",
                    "frameEvidence" to (if (renderer is ImageView) FrameEvidence.READY else FrameEvidence.UNKNOWN).name,
                    "presented" to FrameEvidence.READY.presented)
            }
        }
        val entry = mediaEntry
        if (entry != null && entryWaitingForRenderer && page === getCurrentFragment() &&
            path == intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH)) {
            entryReleaseDraw?.removeListener()
            entryReleaseDraw = binding.viewPager.doOnPreDraw {
                entryReleaseDraw = null
                if (page === getCurrentFragment() && path == getCurrentPath() &&
                    currentEntryRenderer()?.isAttachedToWindow == true) completeEntryFallbackWhenReady(entry)
            }
            binding.viewPager.invalidate()
        }
        if (entry != null && !isFinishing && !isDestroyed && page === getCurrentFragment() &&
            path == intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH)) {
            if (!entryReadyReported) {
                entryReadyReported = true
                logMediaEntry("media_ready")
            }
            entryFailedPage = null
            binding.viewerEntryStatus.visibility = View.GONE
            requestMediaEntryRelease(entry, "media_ready")
        }
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

    override fun entryMediaLoadFailed(path: String, page: ViewPagerFragment) {
        val entry = mediaEntry ?: return
        if (isFinishing || isDestroyed || page !== getCurrentFragment() ||
            path != intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH)) return
        entryFailedPage = page
        if (entryWaitingForRenderer) {
            entryWaitingForRenderer = false
            logMediaEntry("entry_renderer_failed", "load_failed")
            finishMediaEntryTransition(entry)
        }
        updateEntryLoadStatus()
        requestMediaEntryRelease(entry, "load_failed")
    }

    private fun requestMediaEntryRelease(entry: PhotoTransitionCompletionGate, reason: String) {
        if (!isCurrentEntry(entry) || !entry.requestRelease()) return
        logMediaEntry("entry_release_requested", reason)
        entryReleaseDraw = binding.viewPager.doOnPreDraw {
            entryReleaseDraw = null
            releasePhotoSharedElement(entry)
        }
        binding.viewPager.invalidate()
    }

    private fun releasePhotoSharedElement(entry: PhotoTransitionCompletionGate) {
        if (!isCurrentEntry(entry) || !entry.release()) return
        photoSharedElementHandler.removeCallbacks(entryReadyTimeout)
        logMediaEntry("entry_pre_draw")
        logMediaEntry("entry_released")
        (getCurrentFragment() as? PhotoFragment)?.setTransitionCanvasTransparent(true)
        photoSharedElementHandler.postDelayed(entryWatchdog, PhotoViewerTransition.DURATION_MS + PhotoViewerTransition.FADE_DURATION_MS)
        startPostponedEnterTransition()
    }

    internal fun staticPhotoEntrySource(path: String) =
        if (::transitionSession.isInitialized && intent.getBooleanExtra(PhotoViewerTransition.EXTRA_STATIC_PHOTO, false)) {
            transitionSession.readStaticPhotoSource(transitionSession.entry)?.takeIf { it.path == path }
        } else null

    private fun mapEntrySharedElement(entry: PhotoTransitionCompletionGate, names: List<String>, sharedElements: MutableMap<String, View>) {
        val name = PhotoViewerTransition.ELEMENT_NAME
        if (!isCurrentEntry(entry)) {
            sharedElements.remove(name)
            return
        }
        if (entry.stage == Stage.MAPPED || entry.stage == Stage.CAPTURED || entry.stage == Stage.SHARED) {
            photoTransitionMappedView?.takeIf { name in names && it.isAttachedToWindow && entryMappedPage === getCurrentFragment() }?.let {
                sharedElements[name] = it
            } ?: sharedElements.remove(name)
            return
        }
        val candidate = photoSharedElementPath?.takeIf { name in names && entry.stage == Stage.RELEASED }
            ?.let { path ->
                if (intent.getBooleanExtra(PhotoViewerTransition.EXTRA_STATIC_PHOTO, false)) {
                    val source = transitionSession.readStaticPhotoSource(transitionSession.entry)
                    val photo = getCurrentFragment() as? PhotoFragment
                    if (source == null || photo == null) {
                        logMediaEntry("static_target_rejected", "static_source_or_fragment_unavailable")
                        null
                    } else {
                        photo.prepareStaticPhotoEntry(path, source) { logMediaEntry("static_target_rejected", it) }
                    }
                } else {
                    getCurrentSharedElementView(path, deferTakeover = true)
                }
            }
        if (candidate != null && entry.map()) {
            candidate.transitionName = name
            sharedElements[name] = candidate
            photoTransitionMappedView = candidate
            val mappedPage = getCurrentFragment()
            entryMappedPage = mappedPage
            entryLeases.acquire(transitionSession.entry, Resource.RENDERER_HANDOFF) {
                if (transitionSession.outcome == null) finishSharedEntryPage(mappedPage)
            }
            entryLeases.acquire(transitionSession.entry, Resource.PROXY) { clearSharedTransitionSurface(mappedPage) }
            (candidate as? StaticPhotoEntryView)?.geometry?.let { geometry ->
                ViewerEntryDiagnostics.event(entryRequestId(), "static_photo_endpoints") {
                    mapOf("sourceContent" to geometry.sourcePhoto.rect().toShortString(),
                        "sourceClip" to geometry.source.view.visible.rect().toShortString(),
                        "targetContent" to geometry.target.pixels.rect().toShortString(),
                        "targetClip" to geometry.target.visible.rect().toShortString(),
                        "crop" to geometry.source.thumbnail.cropped, "radius" to geometry.source.thumbnail.radius)
                }
            }
            logMediaEntry("shared_map_accepted")
        } else {
            sharedElements.remove(name)
            rejectSharedEntry(entry, if (name in names) "target_unavailable" else "source_unavailable")
        }
    }

    private fun captureEntrySharedElement(entry: PhotoTransitionCompletionGate, names: List<String>, elements: List<View>) {
        if (!isCurrentEntry(entry) || entry.stage != Stage.MAPPED) return
        val proxy = photoTransitionMappedView ?: return
        val index = names.indexOf(PhotoViewerTransition.ELEMENT_NAME)
        if (elements.getOrNull(index) !== proxy || getCurrentFragment() !== entryMappedPage) {
            rejectSharedEntry(entry, "platform_rejected_target")
            return
        }
        if (proxy is StaticPhotoEntryView) {
            val source = transitionSession.readStaticPhotoSource(transitionSession.entry, requireConfirmed = true)
            val failure = when {
                source == null || !proxy.geometry.source.near(source) -> "static_source_capture_unconfirmed"
                StaticPhotoViewGeometry.layout(proxy)?.near(source.view.layout) != true -> "static_platform_source_mismatch"
                (entryMappedPage as? PhotoFragment)?.validatesStaticPhotoHandoff(proxy) != true -> "static_target_changed_at_capture"
                else -> null
            }
            if (failure != null) {
                rejectSharedEntry(entry, failure)
                return
            }
            proxy.showFrame(0f)
        }
        // AOSP shows accepted elements immediately before applying source state and calling
        // onSharedElementStart. This is the last synchronous point before start capture.
        val taken = when (val page = entryMappedPage) {
            is PhotoFragment -> page.takeOverSharedEntry(proxy)
            is VideoFragment -> page.takeOverSharedEntry(proxy)
            else -> false
        }
        if (taken && entry.capture()) {
            check(transitionSession.capture(transitionSession.entry))
            logMediaEntry("shared_start_captured")
        } else rejectSharedEntry(entry, "renderer_unavailable_at_capture")
    }

    private fun rejectSharedEntry(entry: PhotoTransitionCompletionGate, reason: String) {
        if (!entry.reject()) return
        logMediaEntry("shared_map_rejected", reason)
        photoSharedElementHandler.post { startEntryFallback(entry, reason) }
    }

    private fun onSharedEntryTerminal(entry: PhotoTransitionCompletionGate, cancelled: Boolean) {
        if (!isCurrentEntry(entry) || entry.stage == Stage.FALLBACK) return
        logMediaEntry(if (cancelled) "shared_transition_cancelled" else "shared_transition_ended")
        val staticProxy = photoTransitionMappedView as? StaticPhotoEntryView
        when {
            cancelled -> startEntryFallback(entry, "platform_cancelled")
            entry.stage != Stage.SHARED -> startEntryFallback(entry, "no_animator")
            entryMappedPage !== getCurrentFragment() || currentEntryRenderer()?.isAttachedToWindow != true ->
                startEntryFallback(entry, "renderer_lost")
            staticProxy != null && !staticProxy.animationCompleted -> startEntryFallback(entry, "static_geometry_not_animated")
            staticProxy != null && (entryMappedPage as? PhotoFragment)?.validatesStaticPhotoHandoff(staticProxy) != true ->
                startEntryFallback(entry, "handoff_geometry_changed")
            else -> {
                photoSharedElementMapped = true
                finishMediaEntryTransition(entry)
            }
        }
    }

    private fun startEntryFallback(entry: PhotoTransitionCompletionGate, reason: String) {
        if (!isCurrentEntry(entry) || !entry.startFallback()) return
        check(transitionSession.entryFallback(transitionSession.entry, reason))
        photoSharedElementHandler.removeCallbacks(entryWatchdog)
        photoSharedElementFadeFallback = true
        logMediaEntry("entry_fallback_started", reason)
        entryLeases.release(transitionSession.entry, Resource.PROXY)
        photoTransitionMappedView = null
        entryShowsLoadStatus = true
        val hasMedia = currentEntryRenderer()?.let {
            it.isAttachedToWindow && it.isShown && it.alpha > 0f && it.width > 0 && it.height > 0
        } == true
        if (!hasMedia) {
            if (entryFailedPage != null && entryFailedPage === getCurrentFragment()) {
                logMediaEntry("entry_renderer_failed", "load_failed")
                finishMediaEntryTransition(entry)
                updateEntryLoadStatus()
                return
            }
            entryWaitingForRenderer = true
            renderSharedViewerLayers(1f)
            logMediaEntry("entry_waiting_for_renderer", reason)
            // Free the publication gate while preserving the Session and input lease.
            val pending = transitionPendingSwipePublication
            transitionPendingSwipePublication = null
            if (pending != null) applySwipeMediaPublication(pending)
            transitionPendingAdjacentMedia?.let { adjacent ->
                transitionPendingAdjacentMedia = null
                publishAdjacent(adjacent)
            }
            updateEntryLoadStatus()
            return
        }
        completeEntryFallbackWhenReady(entry)
    }

    private fun completeEntryFallbackWhenReady(entry: PhotoTransitionCompletionGate) {
        if (!isCurrentEntry(entry) || entry.stage != Stage.FALLBACK) return
        entryWaitingForRenderer = false
        if (!OxygenClassicMotion.enabled(this)) {
            finishMediaEntryTransition(entry)
            updateEntryLoadStatus()
            return
        }

        val start = viewerSystemBarProgress
        binding.viewPager.alpha = 0.75f
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PhotoViewerTransition.FADE_DURATION_MS
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener {
                if (isCurrentEntry(entry) && entry.stage == Stage.FALLBACK) {
                    val progress = it.animatedValue as Float
                    renderSharedViewerLayers(start + (1f - start) * progress)
                    binding.viewPager.alpha = 0.75f + 0.25f * progress
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finishMediaEntryTransition(entry)
            })
        }
        entryFallbackAnimator = animator
        animator.start()
    }

    private fun currentEntryRenderer(): View? = when (val page = getCurrentFragment()) {
        is PhotoFragment -> page.entryRenderer()
        is VideoFragment -> page.entryRenderer()
        else -> null
    }

    private fun updateEntryLoadStatus(path: String = getCurrentPath()) {
        if (!entryShowsLoadStatus) return
        val page = getCurrentFragment()
        val original = intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH)
        val visible = (path.isEmpty() || path == original) && currentEntryRenderer() == null
        val failed = page != null && page === entryFailedPage
        binding.viewerEntryStatus.apply {
            visibility = if (visible && (!failed || page is VideoFragment)) View.VISIBLE else View.GONE
            if (visible) setText(if (failed) org.fossify.commons.R.string.unknown_error_occurred else R.string.viewer_entry_loading)
        }
    }

    private var entryLifecyclePhase: String? = null
    private var lastEntryCanvas: Map<String, Any?>? = null

    private fun entryRequestId(): Long {
        val id = intent.getLongExtra(PhotoViewerTransition.EXTRA_ENTRY_ID, 0L)
        val process = intent.getStringExtra(ViewerEntryDiagnostics.EXTRA_DIAGNOSTIC_PROCESS)
        return if (process == null) id else ViewerEntryDiagnostics.entryId(process, id)
    }

    private fun entryCanvasState(): Map<String, Any?> {
        val decor = window.peekDecorView()
        return mapOf(
            // The public Window API exposes the effective background through its Decor.
            "window" to ViewerEntryDiagnostics.backgroundState(decor?.background),
            "windowAlpha" to window.attributes.alpha,
            "decorDrawable" to decor?.background?.javaClass?.simpleName,
            "decor" to ViewerEntryDiagnostics.viewState(decor),
            "root" to ViewerEntryDiagnostics.viewState(decor?.findViewById(R.id.fragment_holder)),
            "pager" to ViewerEntryDiagnostics.viewState(decor?.findViewById(R.id.view_pager)),
            "scrim" to ViewerEntryDiagnostics.viewState(decor?.findViewById(R.id.viewer_transition_scrim)),
        )
    }

    internal fun traceEntryCanvas(checkpoint: String, updateLifecycle: Boolean = true) {
        val request = entryRequestId()
        if (!ViewerEntryDiagnostics.enabled(request)) return
        if (updateLifecycle) entryLifecyclePhase = checkpoint
        val state = entryCanvasState()
        if (lastEntryCanvas == state) return
        lastEntryCanvas = state
        ViewerEntryDiagnostics.event(request, "entry_canvas_changed") {
            state + mapOf("checkpoint" to checkpoint, "lifecycle" to entryLifecyclePhase, "stage" to mediaEntry?.stage?.name)
        }
    }

    private fun logMediaEntry(event: String, reason: String? = null) {
        ViewerEntryDiagnostics.event(entryRequestId(), event) {
            val renderer = currentEntryRenderer()
            val content = (renderer as? ImageView)?.let(org.fossify.gallery.helpers.MediaTransitionGeometry::imageBounds)
            val proxy = photoTransitionMappedView
            mapOf(
                "stage" to mediaEntry?.stage?.name,
                "reason" to reason,
                "media" to intent.getIntExtra(ViewerEntryDiagnostics.EXTRA_MEDIA_TYPE, 0),
                "source" to viewerSourceType.name,
                "matchesSelected" to (getCurrentPath() == photoSharedElementPath),
                "drawable" to ((renderer as? ImageView)?.drawable != null),
                "renderer" to ViewerEntryDiagnostics.viewState(renderer),
                "proxy" to ViewerEntryDiagnostics.viewState(proxy),
                "captures" to proxy?.let(::mappedTransitionSurfaceBounds)?.toShortString(),
                "contentBounds" to (content?.toShortString() ?: renderer?.let(::mappedTransitionSurfaceBounds)?.toShortString()),
                "lifecycle" to entryLifecyclePhase,
                // Distance from the tap, not from the previous log line: the entry
                // timeline is read as "how long after the click did this happen".
                "msSinceEntryRequest" to
                    org.fossify.gallery.helpers.ViewerEntryDiagnostics.elapsedSince(
                        org.fossify.gallery.helpers.ViewerEntryDiagnostics.requestedAt(intent)),
            ) + entryCanvasState()
        }
    }

    override fun goToPrevItem() {
        if (binding.viewPager.currentItem == 0 && requestViewerBoundary(false)) return
        if (!isExternalIntent() && binding.viewPager.currentItem > 0) explicitUserNavigationPending = true
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
        checkOrientation()
    }

    override fun goToNextItem() {
        if (binding.viewPager.currentItem == getCurrentMedia().lastIndex && requestViewerBoundary(true)) return
        if (!isExternalIntent() && binding.viewPager.currentItem < getCurrentMedia().lastIndex) {
            explicitUserNavigationPending = true
        }
        binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
        checkOrientation()
    }

    override fun launchViewVideoIntent(path: String) {
        if (!checkFavoriteAccess(path)) return
        hideKeyboard()
        ensureBackgroundThread {
            val newUri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID) ?: return@ensureBackgroundThread
            val mimeType = getUriMimeType(path, newUri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
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
    }

    private fun checkSystemUI() {
        viewerStatusSurfaceVisible = !mIsFullScreen
        updateViewerSystemBars()
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
        if (!checkFavoriteAccess(getCurrentMedia().getOrNull(position)?.path)) {
            refreshMediaDependentActionVisibility(null)
            return
        }
        updateEntryLoadStatus(getCurrentMedia().getOrNull(position)?.path.orEmpty())
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
            if (mPos >= 0) screenPrefetchForward = position > mPos
            val previousPath = getCurrentMedia().getOrNull(mPos)?.path
            val selectedPath = getCurrentMedia().getOrNull(position)?.path
            if (!isExternalIntent() && previousPath != null && selectedPath != null &&
                previousPath != selectedPath && (userSwipeInProgress || explicitUserNavigationPending)) {
                // Catalog replacement temporarily remaps integer positions. Only
                // real gestures / explicit navigation may change deletion history.
                lastVisitedPath = previousPath
                lastUserNavigationForward = position > mPos
                confirmedUserPagePath = selectedPath
                userNavigationEpoch++
            }
            explicitUserNavigationPending = false
            mPos = position
            // The Grid's return prefetch follows the media the Viewer is showing.
            org.fossify.gallery.helpers.ViewerCurrentMediaRegistry.report(selectedPath
                ?: getCurrentMedia().getOrNull(position)?.path)
            if (org.fossify.gallery.helpers.GalleryDiagnosticLogger.isEnabled()) {
                val selected = getCurrentMedia().getOrNull(position)
                if (viewerDiagnosticFirstPageBoundMs < 0L)
                    viewerDiagnosticFirstPageBoundMs = (android.os.SystemClock.elapsedRealtime() - viewerDiagnosticStartedAt).coerceAtLeast(0L)
                org.fossify.gallery.helpers.GalleryDiagnosticLogger.event(
                    org.fossify.gallery.helpers.DiagnosticLevel.DEBUG, "Viewer", "viewer_page_bound",
                    viewerDiagnosticRequestId,
                    fields = mapOf(
                        "index" to position, "count" to getCurrentMedia().size,
                        "launchId" to intent.getLongExtra(PhotoViewerTransition.EXTRA_LAUNCH, 0L),
                        "firstPageBoundMs" to viewerDiagnosticFirstPageBoundMs,
                        "viewerSessionId" to viewerDiagnosticSessionId.takeIf { it != 0L },
                        "mediaId" to selected?.path?.let(org.fossify.gallery.helpers.GalleryDiagnosticLogger::anonymousId),
                        "revision" to org.fossify.gallery.helpers.PhotoContentRevision.current(),
                        "collection" to if (isPhotoProfile) "photo_profile" else if (mDirectory == SHOW_ALL) "all_media" else "folder"
                    )
                )
            }
            if (swipePublicationGate.isMoving) visualTitlePending = true else updateActionbarTitle()
            // Page selection changes the capability state immediately, even when
            // catalog publication defers the rest of the toolbar refresh.
            refreshMediaDependentActionVisibility(getCurrentMedium())
            if (swipePublicationGate.isMoving) pendingSwipeMenuRefresh = true
            else refreshMenuItems()
            if (mIsSlideshowActive) scheduleSwipe()
            scheduleScreenPrefetch(position)
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            userSwipeInProgress = true
            swipePageOrigin = binding.viewPager.currentItem
            swipePrefetchTarget = -1
        }
        val wasMoving = swipePublicationGate.isMoving
        val ready = swipePublicationGate.onScrollMoving(state != ViewPager.SCROLL_STATE_IDLE)
        if (state != ViewPager.SCROLL_STATE_IDLE) return
        if (visualTitlePending || wasMoving) {
            visualTitlePending = false
            updateActionbarTitle()
        }
        userSwipeInProgress = false
        swipePageOrigin = -1
        swipePrefetchTarget = -1
        // Last-wins catalog reconciliation occurs after the natural ViewPager
        // slide, so its non-animated position correction is no longer visible.
        ready?.let(::applySwipeMediaPublication)
        if (photoCatalogAdvanceQueued) {
            photoCatalogAdvanceQueued = false
            consumePhotoCatalogAdvance()
        }
        if (pendingPageAccessRefresh) {
            pendingPageAccessRefresh = false
            activePagerAdapter?.onAccessWindowUpdated()
        }
        if (pendingSwipeMenuRefresh || wasMoving) {
            pendingSwipeMenuRefresh = false
            refreshMenuItems()
        }
        val requestedDirection = deferredBoundaryDirection
        deferredBoundaryDirection = null
        if (requestedDirection != null) {
            val target = binding.viewPager.currentItem + if (requestedDirection) 1 else -1
            if (target in 0 until (binding.viewPager.adapter?.count ?: 0)) {
                if (!isExternalIntent()) explicitUserNavigationPending = true
                binding.viewPager.setCurrentItem(
                    target, org.fossify.gallery.helpers.OxygenClassicMotion.enabled(this))
                return
            }
            requestViewerBoundary(requestedDirection)
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

    /** Decode only the next screen image in the last swipe direction, after the page stays put. */
    private fun scheduleScreenPrefetch(position: Int) {
        screenPrefetchHandler.removeCallbacks(screenPrefetchStable)
        val nextIndex = position + if (screenPrefetchForward) 1 else -1
        val next = getCurrentMedia().getOrNull(nextIndex)?.takeUnless { it.isVideo() || it.isGIF() }?.path
        val change = screenPrefetch.onTargetChanged(next, android.os.SystemClock.elapsedRealtime())
        if (change.cancelInFlight) cancelScreenPrefetch()
        val delay = change.armDelayMs ?: return
        if (isFinishing || isDestroyed) return
        screenPrefetchHandler.postDelayed(screenPrefetchStable, delay)
    }

    private fun beginScreenPrefetch() {
        val path = screenPrefetch.onStabilityElapsed(android.os.SystemClock.elapsedRealtime()) ?: return
        if (isFinishing || isDestroyed) return
        val width = binding.viewPager.width
        val height = binding.viewPager.height
        val medium = getCurrentMedia().firstOrNull { it.path == path }
        if (width <= 0 || height <= 0 || medium == null || medium.isVideo() || medium.isGIF()) {
            screenPrefetch.onFailed(path)
            return
        }
        val generation = ++screenPrefetchGeneration
        screenPrefetch.onStart(path)
        val target = object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>(width, height) {
            override fun onResourceReady(
                resource: android.graphics.drawable.Drawable,
                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?,
            ) {
                if (generation != screenPrefetchGeneration) return
                screenPrefetch.onReady(path)
                releaseScreenPrefetchTarget(this)
            }

            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                if (generation != screenPrefetchGeneration) return
                screenPrefetch.onFailed(path)
                releaseScreenPrefetchTarget(this)
            }

            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) = Unit
        }
        screenPrefetchTarget = target
        // Same engine key as PhotoFragment.loadWithGlide for an unrotated page:
        // signature, RESOURCE, fitCenter, and the viewer's measured size.
        // Priority stays below the visible page. Nothing is copied into the return-pixel reserve.
        val options = RequestOptions()
            .signature(medium.getKey())
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
            .priority(com.bumptech.glide.Priority.HIGH)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .fitCenter()
            .override(width, height)
        Glide.with(applicationContext).load(path).apply(options).into(target)
    }

    private fun cancelScreenPrefetch() {
        screenPrefetchHandler.removeCallbacks(screenPrefetchStable)
        val running = screenPrefetch.runningPath
        screenPrefetch.onCancelled(running)
        screenPrefetchGeneration++
        screenPrefetchTarget?.let { releaseScreenPrefetchTarget(it) }
    }

    private fun releaseScreenPrefetchTarget(target: com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>) {
        if (screenPrefetchTarget === target) screenPrefetchTarget = null
        screenPrefetchHandler.post {
            runCatching { Glide.with(applicationContext).clear(target) }
        }
    }

    private fun isExternalIntent(): Boolean {
        return !intent.getBooleanExtra(IS_FROM_GALLERY, false)
    }
}


