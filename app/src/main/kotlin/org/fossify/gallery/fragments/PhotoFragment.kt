package org.fossify.gallery.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
import androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE
import androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE
import androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION
import com.alexvasilkov.gestures.GestureController
import com.alexvasilkov.gestures.State
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.DecoderFactory
import com.davemorrissey.labs.subscaleview.ImageDecoder
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.avif.AVIFDrawable
import com.github.penfeizhou.animation.webp.WebPDrawable
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import it.sephiroth.android.library.exif2.ExifInterface
import org.apache.sanselan.common.byteSources.ByteSourceInputStream
import org.apache.sanselan.formats.jpeg.JpegImageParser
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.isWebP
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.portrait
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.PhotoActivity
import org.fossify.gallery.activities.PhotoVideoActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.adapters.PortraitPhotosAdapter
import org.fossify.gallery.databinding.PagerPhotoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.sendFakeClick
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.HIGH_TILE_DPI
import org.fossify.gallery.helpers.LOW_TILE_DPI
import org.fossify.gallery.helpers.MAX_ZOOM_EQUALITY_TOLERANCE
import org.fossify.gallery.helpers.MediaSharedSurface
import org.fossify.gallery.helpers.PhotoViewerTransition
import org.fossify.gallery.helpers.StaticPhotoEntryGeometry
import org.fossify.gallery.helpers.StaticPhotoEntryView
import org.fossify.gallery.helpers.StaticPhotoHeader
import org.fossify.gallery.helpers.StaticPhotoSource
import org.fossify.gallery.helpers.StaticPhotoViewGeometry
import org.fossify.gallery.helpers.MediaTransitionGeometry
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.MyGlideImageDecoder
import org.fossify.gallery.helpers.NORMAL_TILE_DPI
import org.fossify.gallery.helpers.PicassoRegionDecoder
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.helpers.WEIRD_TILE_DPI
import org.fossify.gallery.models.Medium
import org.fossify.gallery.svg.SvgSoftwareLayerSetter
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

class PhotoFragment : ViewPagerFragment() {
    private val DEFAULT_DOUBLE_TAP_ZOOM = 2f
    private val ZOOMABLE_VIEW_LOAD_DELAY = 100L
    private val SAME_ASPECT_RATIO_THRESHOLD = 0.01

    // devices with good displays, but the rest of the hardware not good enough for them
    private val WEIRD_DEVICES = arrayListOf(
        "motorola xt1685",
        "google nexus 5x"
    )

    var mCurrentRotationDegrees = 0
    private var mIsFragmentVisible = false
    private var mIsFullscreen = false
    private var mWasInit = false
    private var mIsPanorama = false
    private var mIsSubsamplingVisible = false    // checking view.visibility is unreliable, use an extra variable for it
    private var mShouldResetImage = false
    private var mCurrentPortraitPhotoPath = ""
    private var mOriginalPath = ""
    private var mImageOrientation = -1
    private var mLoadZoomableViewHandler = Handler()
    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mCurrentGestureViewZoom = 1f
    private var mInitialZoom = 1f
    private var mSubsamplingImageReady = false
    private var mSubsamplingImageLoading = false
    private var mHasInitialZoom = false
    private var mReportedImagePath: String? = null
    private var detailsGeneration = 0L

    private var imageRequestGeneration = 0L
    private var deliveredImage: Drawable? = null
    private var hasPicassoRequest = false
    private var staticPhotoHeader: Pair<String, StaticPhotoHeader>? = null
    private var staticGlideGeneration = -1L
    private var staticGlideSignature: String? = null

    private fun beginImageRequest(): Long {
        val request = ++imageRequestGeneration
        deliveredImage = null
        mReportedImagePath = null
        if (hasPicassoRequest) {
            hasPicassoRequest = false
            Picasso.get().cancelRequest(binding.gesturesView)
        }
        return request
    }

    private fun reportImageRendered(resource: Drawable? = deliveredImage) {
        if (resource == null) return
        deliveredImage = resource
        val path = mMedium.path
        if (mReportedImagePath == path || !mIsFragmentVisible) return
        val request = imageRequestGeneration
        val root = binding.root
        val image = binding.gesturesView
        image.post {
            if (request == imageRequestGeneration && view === root && mIsFragmentVisible &&
                mReportedImagePath != path && image.drawable === resource && mMedium.path == path) {
                mReportedImagePath = path
                listener?.imageRendered(path, this)
            }
        }
    }

    internal fun entryRenderer(): View? = if (view != null && ::binding.isInitialized &&
        deliveredImage != null && binding.gesturesView.drawable === deliveredImage) binding.gesturesView else null

    internal fun takeOverSharedEntry(proxy: View) = sharedEntryImageGeneration == imageRequestGeneration &&
        entryRenderer()?.isAttachedToWindow == true && sharedSurface?.takeOver(proxy) == true

    private var sharedSurface: MediaSharedSurface? = null
    private var sharedEntryImageGeneration = -1L
    internal fun prepareStaticPhotoEntry(
        path: String, source: StaticPhotoSource, onRejected: (String) -> Unit,
    ): StaticPhotoEntryView? {
        fun reject(reason: String): StaticPhotoEntryView? {
            onRejected(reason)
            return null
        }

        if (!mIsFragmentVisible || view == null || !::mMedium.isInitialized || mMedium.path != path ||
            source.path != path || mReportedImagePath != path) return reject("selection")
        if (source.signature != staticGlideSignature ||
            source.invalidation != org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.current(path)) {
            return reject("content_version")
        }
        if (mCurrentRotationDegrees != 0 || (mHasInitialZoom && !gestureImageIsAtFit()) || mIsSubsamplingVisible) {
            return reject("renderer_transform")
        }
        if (staticGlideGeneration != imageRequestGeneration) return reject("glide_generation")
        val header = staticPhotoHeader?.takeIf { it.first == getFilePathToShow() }?.second ?: return reject("image_header")
        val renderer = entryRenderer() as? ImageView ?: return reject("renderer")
        val drawable = renderer.drawable as? BitmapDrawable ?: return reject("renderer_drawable")
        val target = StaticPhotoViewGeometry.read(renderer, onRejected = onRejected) ?: return null
        val photo = source.thumbnail.project(source.view.pixels, header) ?: return reject("thumbnail_projection")
        if (!MediaTransitionGeometry.isUsable(photo.rect())) return reject("thumbnail_pixels")
        if (!photo.matchesOrientedAspect(header) || !target.pixels.matchesOrientedAspect(header)) {
            return reject("oriented_aspect")
        }
        val surface = sharedSurface ?: MediaSharedSurface(binding.root).also { sharedSurface = it }
        return surface.prepareStaticEntry(drawable, StaticPhotoEntryGeometry(source, target, photo), renderer)?.also {
            sharedEntryImageGeneration = imageRequestGeneration
        }
    }

    internal fun validatesStaticPhotoHandoff(proxy: StaticPhotoEntryView): Boolean {
        val renderer = entryRenderer() as? ImageView ?: return false
        return sharedSurface?.currentView === proxy && mIsFragmentVisible && mMedium.path == proxy.geometry.source.path &&
            sharedEntryImageGeneration == imageRequestGeneration && staticGlideGeneration == imageRequestGeneration &&
            proxy.geometry.source.invalidation == org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.current(mMedium.path) &&
            renderer.isAttachedToWindow &&
                StaticPhotoViewGeometry.read(renderer)?.nearForHandoff(proxy.geometry.target) == true
    }

    private var subsamplingGeneration = 0L

    /** Share pixels, not the full-screen renderer and its black letterboxes. */
    fun sharedElementViewFor(path: String, deferTakeover: Boolean = false): View? {
        fun reject(reason: Int): View? {
            org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_transition_photo_unavailable", reason)
            return null
        }
        if (!mIsFragmentVisible || view == null || !::mMedium.isInitialized || mMedium.path != path) return reject(1)
        if (mReportedImagePath != path) return reject(2)
        if (mCurrentRotationDegrees != 0) return reject(3)
        if (!gestureImageIsAtFit()) return reject(4)
        if (binding.gifViewFrame.visibility == View.VISIBLE) return reject(5)
        val drawable = binding.gesturesView.drawable ?: return reject(6)
        val gestureBounds = MediaTransitionGeometry.imageBounds(binding.gesturesView) ?: return reject(7)
        val bounds = if (mIsSubsamplingVisible) {
            val tiles = binding.subsamplingView
            if (!tiles.isZoomedOut()) return reject(8)
            val quarterTurn = tiles.orientation == 90 || tiles.orientation == 270
            val width = if (quarterTurn) tiles.sHeight else tiles.sWidth
            val height = if (quarterTurn) tiles.sWidth else tiles.sHeight
            val start = tiles.sourceToViewCoord(PointF(0f, 0f)) ?: return reject(9)
            val end = tiles.sourceToViewCoord(PointF(width.toFloat(), height.toFloat())) ?: return reject(9)
            val tileBounds = RectF(start.x, start.y, end.x, end.y).apply {
                offset(tiles.left.toFloat(), tiles.top.toFloat())
            }
            // The Glide drawable can stand in for the tile renderer only at
            // its matching fitted rectangle. Zoomed/panned media fades out.
            if (!MediaTransitionGeometry.sameBounds(gestureBounds, tileBounds)) return reject(9)
            tileBounds
        } else gestureBounds
        val surface = sharedSurface ?: MediaSharedSurface(binding.root).also { sharedSurface = it }
        return surface.prepare(drawable, bounds, binding.gesturesView, binding.subsamplingView,
            deferTakeover = deferTakeover,
            photoHeader = sharedPhotoHeader(drawable))
            ?.also { if (deferTakeover) sharedEntryImageGeneration = imageRequestGeneration }
    }

    private fun sharedPhotoHeader(drawable: Drawable): StaticPhotoHeader? {
        if (mIsSubsamplingVisible || drawable !is BitmapDrawable) return null
        if (staticGlideGeneration != imageRequestGeneration) return null
        return staticPhotoHeader?.takeIf { it.first == getFilePathToShow() }?.second
    }

    internal fun clearSharedEntrySurface() {
        sharedSurface?.clear()
        sharedSurface = null
    }

    fun finishSharedTransition() {
        clearSharedEntrySurface()
        if (view != null && mIsFragmentVisible) {
            revealReadySubsamplingImage()
            scheduleZoomableView()
        }
    }

    /** Horizontal paging stays with this pager only while the photo is at fitted scale. */
    fun canPageHorizontallyFromCurrentPhoto(): Boolean =
        mCurrentRotationDegrees == 0 && when {
            binding.gifViewFrame.visibility == View.VISIBLE -> binding.gifViewFrame.controller.state.zoom <= 1.001f
            mIsSubsamplingVisible -> binding.subsamplingView.isZoomedOut()
            else -> abs(mCurrentGestureViewZoom - mInitialZoom) < MAX_ZOOM_EQUALITY_TOLERANCE
        }

    /** Expose the source grid around the moving photo until the shared bounds settle. */
    fun setTransitionCanvasTransparent(transparent: Boolean) {
        if (!this::binding.isInitialized) return
        val color = if (transparent) Color.TRANSPARENT else Color.BLACK
        binding.root.setBackgroundColor(color)
        binding.gesturesView.setBackgroundColor(color)
        binding.subsamplingView.setBackgroundColor(color)
        binding.gifViewFrame.setBackgroundColor(color)
    }

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredAllowDeepZoomableImages = false
    private var mStoredShowHighestQuality = false
    private var mStoredExtendedDetails = 0

    private lateinit var mView: ViewGroup
    private lateinit var binding: PagerPhotoItemBinding
    private lateinit var mMedium: Medium

    override fun updateMedium(medium: Medium) {
        if (!::mMedium.isInitialized || medium.path != mOriginalPath) return
        mMedium = medium
        super.updateMedium(medium)
        if (this::binding.isInitialized && view != null) initExtendedDetails()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        binding = PagerPhotoItemBinding.inflate(inflater, container, false)
        mView = binding.root
        // Commons' reusable pager item does not declare a solid media surface. Keep
        // transparent/letterboxed image regions black while preserving image pixels.
        mView.setBackgroundColor(Color.BLACK)
        binding.gesturesView.setBackgroundColor(Color.BLACK)
        binding.subsamplingView.setBackgroundColor(Color.BLACK)
        binding.gifViewFrame.setBackgroundColor(Color.BLACK)
        if ((activity as? org.fossify.gallery.activities.ViewPagerActivity)
                ?.shouldKeepPhotoCanvasTransparent() == true) {
            setTransitionCanvasTransparent(true)
        }
        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mOriginalPath = mMedium.path

        binding.apply {
            subsamplingView.setOnClickListener { photoClicked() }
            gesturesView.setOnClickListener { photoClicked() }
            gifView.setOnClickListener { photoClicked() }
            instantPrevItem.setOnClickListener { listener?.goToPrevItem() }
            instantNextItem.setOnClickListener { listener?.goToNextItem() }
            panoramaOutline.setOnClickListener { openPanorama() }

            instantPrevItem.parentView = container
            instantNextItem.parentView = container

            photoBrightnessController.initialize(activity, slideInfo, true, container, singleTap = { x, y ->
                mView.apply {
                    if (subsamplingView.isVisible()) {
                        subsamplingView.sendFakeClick(x, y)
                    } else {
                        gesturesView.sendFakeClick(x, y)
                    }
                }
            })

            gifView.setOnTouchListener { v, event -> handlePhotoDismissTouch(v, event) }

            setupGesturesViewStateListener()
            gesturesView.setOnTouchListener { v, event ->
                handlePhotoDismissTouch(v, event)
            }

            subsamplingView.setOnTouchListener { v, event ->
                handlePhotoDismissTouch(v, event)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.photoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        checkScreenDimensions()
        storeStateVariables()
        if (!mIsFragmentVisible && activity is PhotoActivity) {
            mIsFragmentVisible = true
        }

        if (mMedium.path.startsWith("content://") && !mMedium.path.startsWith("content://mms/")) {
            mMedium.path = requireContext().getRealPathFromURI(Uri.parse(mOriginalPath)) ?: mMedium.path
            if (isRPlus() && !isExternalStorageManager() && mMedium.path.startsWith("/storage/") && mMedium.isHidden()) {
                mMedium.path = mOriginalPath
            }

            if (mMedium.path.isEmpty()) {
                var out: FileOutputStream? = null
                try {
                    var inputStream = requireContext().contentResolver.openInputStream(Uri.parse(mOriginalPath))
                    val exif = ExifInterface()
                    exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                    val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                    val orientation = tag?.getValueAsInt(-1) ?: -1
                    inputStream = requireContext().contentResolver.openInputStream(Uri.parse(mOriginalPath))
                    val original = BitmapFactory.decodeStream(inputStream)
                    val rotated = rotateViaMatrix(original, orientation)
                    exif.setTagValue(ExifInterface.TAG_ORIENTATION, 1)
                    exif.removeCompressedThumbnail()

                    val file = File(requireContext().externalCacheDir, Uri.parse(mOriginalPath).lastPathSegment)
                    out = FileOutputStream(file)
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    mMedium.path = file.absolutePath
                } catch (e: Exception) {
                    requireActivity().toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return mView
                } finally {
                    out?.close()
                }
            }
        }

        mIsFullscreen = listener?.isFullScreen() == true
        if (mIsFullscreen) {
            binding.bottomActionsDummy.beGone()
        }
        loadImage()
        initExtendedDetails()
        mWasInit = true
        updateInstantSwitchWidths()

        // TODO: Implement panorama using a FOSS library
        // ensureBackgroundThread {
        //      checkIfPanorama()
        // }

        return mView
    }

    private fun handlePhotoDismissTouch(view: View, event: MotionEvent): Boolean {
        if (activity !is ViewPagerActivity) {
            handleEvent(event)
            return false
        }
        return handleMediaDismissTouch(view, event) {
            mCurrentRotationDegrees == 0 && when (view) {
                binding.gesturesView -> abs(mCurrentGestureViewZoom - mInitialZoom) < MAX_ZOOM_EQUALITY_TOLERANCE
                binding.subsamplingView -> binding.subsamplingView.isZoomedOut()
                binding.gifView -> binding.gifViewFrame.controller.state.zoom <= 1.001f
                else -> false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        val config = requireContext().config
        if (mWasInit && (config.showExtendedDetails != mStoredShowExtendedDetails || config.extendedDetails != mStoredExtendedDetails)) {
            initExtendedDetails()
        }

        if (mWasInit) {
            if (config.allowZoomingImages != mStoredAllowDeepZoomableImages || config.showHighestQuality != mStoredShowHighestQuality) {
                mIsSubsamplingVisible = false
                mSubsamplingImageLoading = false
                mSubsamplingImageReady = false
                binding.subsamplingView.animate().cancel()
                subsamplingGeneration++
                binding.subsamplingView.recycle()
                binding.subsamplingView.beGone()
                loadImage()
            } else if (mMedium.isGIF()) {
                loadGif()
            } else if (mIsSubsamplingVisible && mShouldResetImage) {
                binding.subsamplingView.onGlobalLayout {
                    binding.subsamplingView.resetView()
                }
            }
            mShouldResetImage = false
        }

        val keepScreenOn = config.keepScreenOn
        val allowPhotoGestures = config.allowPhotoGestures
        val allowInstantChange = config.allowInstantChange

        binding.apply {
            photoBrightnessController.beVisibleIf(allowPhotoGestures)
            instantPrevItem.beVisibleIf(allowInstantChange)
            instantNextItem.beVisibleIf(allowInstantChange)
        }

        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        reapplyColorModeIfNeeded()
        storeStateVariables()
    }

    override fun onDestroyView() {
        beginImageRequest()
        subsamplingGeneration++
        clearSharedEntrySurface()
        org.fossify.gallery.helpers.ViewerChromeMotion.cancel(binding.root)
        detailsGeneration++
        super.onDestroyView()
        if (activity?.isDestroyed == false) {
            binding.subsamplingView.recycle()

            try {
                if (context != null) {
                    Glide.with(requireContext()).clear(binding.gesturesView)
                }
            } catch (ignored: Exception) {
            }
        }

        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!mWasInit) {
            return
        }

        // avoid GIFs being skewed, played in wrong aspect ratio
        if (mMedium.isGIF()) {
            mView.onGlobalLayout {
                if (activity != null) {
                    measureScreen()
                    Handler().postDelayed({
                        binding.gifViewFrame.controller.resetState()
                        loadGif()
                    }, 50)
                }
            }
        } else {
            hideZoomableView()
            loadImage()
        }

        measureScreen()
        initExtendedDetails()
        updateInstantSwitchWidths()
        mShouldResetImage = true
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        mIsFragmentVisible = menuVisible
        if (mWasInit) {
            if (menuVisible) reportImageRendered()
            val isNotAnimatedContent =
                !mMedium.isGIF() && !mMedium.isApng() && !mMedium.isAvif() && !mMedium.isWebP()
            if (isNotAnimatedContent) {
                photoFragmentVisibilityChanged(menuVisible)
            }
        }
    }

    private fun storeStateVariables() {
        requireContext().config.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredAllowDeepZoomableImages = allowZoomingImages
            mStoredShowHighestQuality = showHighestQuality
            mStoredExtendedDetails = extendedDetails
        }
    }

    private fun checkScreenDimensions() {
        if (mScreenWidth == 0 || mScreenHeight == 0) {
            measureScreen()
        }
    }

    private fun measureScreen() {
        val metrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getRealMetrics(metrics)
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels
    }

    private fun photoFragmentVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            applyProperColorMode(binding.gesturesView.drawable)
            if (mSubsamplingImageReady) revealReadySubsamplingImage() else scheduleZoomableView()
        } else {
            hideZoomableView()
            ColorModeHelper.resetColorMode(activity)
        }
    }

    private fun degreesForRotation(orientation: Int) = when (orientation) {
        ORIENTATION_ROTATE_270, ORIENTATION_TRANSPOSE -> 270
        ORIENTATION_ROTATE_180 -> 180
        ORIENTATION_ROTATE_90, ORIENTATION_TRANSVERSE -> 90
        else -> 0
    }

    private fun gestureImageIsAtFit(): Boolean =
        org.fossify.gallery.helpers.SubsamplingHandoffPolicy.isAtFit(
            mCurrentGestureViewZoom, mInitialZoom, mHasInitialZoom, MAX_ZOOM_EQUALITY_TOLERANCE.toFloat()
        )

    private fun rotateViaMatrix(original: Bitmap, orientation: Int): Bitmap {
        val degrees = degreesForRotation(orientation).toFloat()
        return if (degrees == 0f) {
            original
        } else {
            val matrix = Matrix()
            matrix.setRotate(degrees)
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }
    }

    private fun loadImage() {
        val request = beginImageRequest()
        val root = binding.root
        mHasInitialZoom = false
        checkScreenDimensions()

        if (mMedium.isPortrait() && context != null) {
            showPortraitStripe()
        }

        val entryPath = activity?.intent?.takeIf { it.getBooleanExtra(PhotoViewerTransition.EXTRA_STATIC_PHOTO, false) }
            ?.getStringExtra(PhotoViewerTransition.EXTRA_PATH)
        val staticPath = getFilePathToShow().takeIf { entryPath == mMedium.path }
        // Read the header before starting Glide: loadWithGlide derives its signature
        // from staticPhotoHeader, and starting the request first would bump
        // imageRequestGeneration and discard this header callback.
        ensureBackgroundThread {
            val orientation = getImageOrientation()
            val header = staticPath?.let { path -> StaticPhotoHeader.read(path, orientation)?.let { path to it } }
            activity?.runOnUiThread {
                if (request != imageRequestGeneration || binding.root !== root) return@runOnUiThread
                staticPhotoHeader = header
                mImageOrientation = orientation
                when {
                    mMedium.isGIF() -> loadGif()
                    mMedium.isSVG() -> loadSVG()
                    mMedium.isApng() -> loadAPNG()
                    mMedium.isAvif() -> loadAVIF()
                    else -> loadBitmap()
                }
            }
        }
    }

    private fun loadGif() {
        try {
            val pathToLoad = getPathToLoad(mMedium)
            val source = if (pathToLoad.startsWith("content://") || pathToLoad.startsWith("file://")) {
                InputSource.UriSource(requireContext().contentResolver, Uri.parse(pathToLoad))
            } else {
                InputSource.FileSource(pathToLoad)
            }

            binding.apply {
                gesturesView.beGone()
                gifViewFrame.beVisible()
                ensureBackgroundThread {
                    gifView.setInputSource(source)
                }
            }
        } catch (e: Exception) {
            loadBitmap()
        } catch (e: OutOfMemoryError) {
            loadBitmap()
        }
    }

    private fun loadSVG() {
        if (context != null) {
            Glide.with(requireContext())
                .`as`(PictureDrawable::class.java)
                .listener(SvgSoftwareLayerSetter())
                .signature(mMedium.getKey())
                .load(mMedium.path)
                .into(binding.gesturesView)
        }
    }

    private fun loadAPNG() {
        if (context != null) {
            val drawable = APNGDrawable.fromFile(mMedium.path)
            binding.gesturesView.setImageDrawable(drawable)
            reportImageRendered(drawable)
        }
    }

    private fun loadAVIF() {
        if (context != null) {
            val drawable = AVIFDrawable.fromFile(mMedium.path)
            if (drawable.intrinsicWidth == 0 || drawable.intrinsicHeight == 0) {
                loadBitmap()
                return
            }

            binding.gesturesView.setImageDrawable(drawable)
            reportImageRendered(drawable)
        }
    }

    private fun loadBitmap(addZoomableView: Boolean = true) {
        val request = beginImageRequest()
        mHasInitialZoom = false
        if (context == null) return
        val path = getFilePathToShow()
        if (path.isWebP()) {
            val drawable = WebPDrawable.fromFile(path)
            if (drawable.intrinsicWidth == 0) {
                loadWithGlide(path, addZoomableView, request)
            } else {
                binding.gesturesView.setImageDrawable(drawable)
                reportImageRendered(drawable)
            }
        } else {
            loadWithGlide(path, addZoomableView, request)
        }
    }

    private fun loadWithGlide(path: String, addZoomableView: Boolean, request: Long) {
        val source = (activity as? ViewPagerActivity)?.staticPhotoEntrySource(mMedium.path)
        val verifiedSignature = source?.takeIf {
            val version = staticPhotoHeader?.takeIf { header -> header.first == path }?.second?.fileVersion
            version?.matchesGrid(it) == true &&
                version.invalidation == org.fossify.gallery.helpers.ViewerContentInvalidationRegistry.current(mMedium.path)
        }?.signature
        val priority = if (mIsFragmentVisible) Priority.IMMEDIATE else Priority.NORMAL
        val options = RequestOptions()
            .signature(verifiedSignature?.let { com.bumptech.glide.signature.ObjectKey(it) } ?: mMedium.getKey())
            .format(DecodeFormat.PREFER_ARGB_8888)
            .priority(priority)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .fitCenter()
            .run {
                if (mCurrentRotationDegrees != 0) {
                    transform(Rotate(mCurrentRotationDegrees))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                } else {
                    this
                }
            }

        // Only the entry photo, whose identity and file version were checked against the
        // Grid source, gets a proportional preview. The renderer-ready gate stays exactly
        // where it is: this preview *is* a renderer frame with the same signature,
        // fit-center transform and aspect, so the shared proxy geometry remains derived
        // from the real view instead of guessed. The full decode keeps running and
        // replaces the drawable in place - no lease, handoff or timeout is bypassed.
        val previewEnabled = org.fossify.gallery.helpers.ViewerEntryPreviewPolicy.allow(
            isEntryMedia = source != null,
            hasVerifiedGridSource = verifiedSignature != null,
            rotationDegrees = mCurrentRotationDegrees,
            subsamplingVisible = mIsSubsamplingVisible,
        )
        val requestBuilder = Glide.with(requireContext())
            .load(path)
            .apply(options)
        if (previewEnabled) {
            requestBuilder.thumbnail(org.fossify.gallery.helpers.ViewerEntryPreviewPolicy.PREVIEW_SIZE_MULTIPLIER)
            org.fossify.gallery.helpers.GalleryPerfTrace.event(
                "viewer_entry_preview_strategy",
                (org.fossify.gallery.helpers.ViewerEntryPreviewPolicy.PREVIEW_SIZE_MULTIPLIER * 100f).toInt())
        }
        requestBuilder.listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    if (request != imageRequestGeneration) return true
                    resetColorModeIfVisible()
                    if (activity != null && !activity!!.isDestroyed && !activity!!.isFinishing) {
                        tryLoadingWithPicasso(addZoomableView, request)
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (request != imageRequestGeneration) return true
                    // Glide reports the size-multiplied preview first, then the full
                    // decode. Both go through the same renderer pipeline: no second
                    // readiness path, no changed geometry, no lease to hand over.
                    // `reportImageRendered` deduplicates the frame notification to the
                    // Activity, so the entry gate still releases exactly once.
                    staticGlideGeneration = request
                    staticGlideSignature = verifiedSignature
                    applyProperColorMode(resource)
                    val allowZoomingImages = context?.config?.allowZoomingImages ?: true
                    binding.gesturesView.controller.settings.isZoomEnabled = mMedium.isRaw() || mCurrentRotationDegrees != 0 || allowZoomingImages == false
                    if (mIsFragmentVisible && addZoomableView) {
                        scheduleZoomableView()
                    }
                    reportImageRendered(resource)
                    return false
                }
            }).into(binding.gesturesView)
    }

    private fun tryLoadingWithPicasso(addZoomableView: Boolean, request: Long) {
        var pathToLoad = if (getFilePathToShow().startsWith("content://")) getFilePathToShow() else "file://${getFilePathToShow()}"
        pathToLoad = pathToLoad.replace("%", "%25").replace("#", "%23")

        try {
            val picasso = Picasso.get()
                .load(pathToLoad)
                .centerInside()
                .stableKey(mMedium.getSignature())
                .resize(mScreenWidth, mScreenHeight)

            if (mCurrentRotationDegrees != 0) {
                picasso.rotate(mCurrentRotationDegrees.toFloat())
            } else {
                degreesForRotation(mImageOrientation).toFloat()
            }

            hasPicassoRequest = true
            picasso.into(binding.gesturesView, object : Callback {
                override fun onSuccess() {
                    if (request != imageRequestGeneration) return
                    hasPicassoRequest = false
                    applyProperColorMode(binding.gesturesView.drawable)
                    binding.gesturesView.controller.settings.isZoomEnabled =
                        mMedium.isRaw() || mCurrentRotationDegrees != 0 || context?.config?.allowZoomingImages == false
                    if (mIsFragmentVisible && addZoomableView) {
                        scheduleZoomableView()
                    }
                    reportImageRendered(binding.gesturesView.drawable)
                }

                override fun onError(e: Exception?) {
                    if (request != imageRequestGeneration) return
                    hasPicassoRequest = false
                    resetColorModeIfVisible()
                    if (mMedium.path != mOriginalPath) {
                        mMedium.path = mOriginalPath
                        loadImage()
                        // TODO: Implement panorama using a FOSS library
                        // checkIfPanorama()
                    } else {
                        showImageLoadError(request)
                    }
                }
            })
        } catch (_: Exception) {
            hasPicassoRequest = false
            showImageLoadError(request)
        }
    }

    private fun showImageLoadError(request: Long) {
        if (request != imageRequestGeneration || view !== binding.root) return
        listener?.entryMediaLoadFailed(mMedium.path, this)
        binding.errorMessageHolder.errorMessage.apply {
            setTextColor(Color.WHITE)
            fadeIn()
        }
    }

    private fun setupGesturesViewStateListener() {
        binding.gesturesView.controller.addOnStateChangeListener(object : GestureController.OnStateChangeListener {
            override fun onStateChanged(state: State) {
                val settings = binding.gesturesView.controller.settings
                if (settings.hasImageSize() && settings.hasViewportSize() && !mHasInitialZoom) {
                    val zoomByWidth = settings.viewportWidth.toFloat() / settings.imageWidth
                    val zoomByHeight = settings.viewportHeight.toFloat() / settings.imageHeight
                    val fitZoom = maxOf(zoomByWidth, zoomByHeight)
                    mInitialZoom = state.zoom
                    var target = fitZoom
                    if (abs(target - mInitialZoom) < MAX_ZOOM_EQUALITY_TOLERANCE) {
                        target = mInitialZoom * DEFAULT_DOUBLE_TAP_ZOOM
                    }
                    settings.doubleTapZoom = target.coerceAtMost(settings.maxZoom)
                    mHasInitialZoom = true
                }

                mCurrentGestureViewZoom = state.zoom
                if (mSubsamplingImageReady) {
                    revealReadySubsamplingImage()
                } else if (!mSubsamplingImageLoading && !mIsSubsamplingVisible && mIsFragmentVisible) {
                    scheduleZoomableView()
                }
            }
        })
    }

    private fun showPortraitStripe() {
        val files = File(mMedium.parentPath).listFiles()?.toMutableList() as? ArrayList<File>
        if (files != null) {
            val screenWidth = requireContext().realScreenSize.x
            val itemWidth =
                resources.getDimension(R.dimen.portrait_photos_stripe_height).toInt() + resources.getDimension(org.fossify.commons.R.dimen.one_dp)
                    .toInt()
            val sideWidth = screenWidth / 2 - itemWidth / 2
            val fakeItemsCnt = ceil(sideWidth / itemWidth.toDouble()).toInt()

            val paths = fillPhotoPaths(files, fakeItemsCnt)
            var curWidth = itemWidth
            while (curWidth < screenWidth) {
                curWidth += itemWidth
            }

            val sideElementWidth = curWidth - screenWidth
            val adapter = PortraitPhotosAdapter(requireContext(), paths, sideElementWidth) { position, x ->
                if (mIsFullscreen) {
                    return@PortraitPhotosAdapter
                }

                binding.photoPortraitStripe.smoothScrollBy((x + itemWidth / 2) - screenWidth / 2, 0)
                if (paths[position] != mCurrentPortraitPhotoPath) {
                    mCurrentPortraitPhotoPath = paths[position]
                    hideZoomableView()
                    loadBitmap()
                }
            }

            binding.photoPortraitStripe.adapter = adapter

            val coverIndex = getCoverImageIndex(paths)
            if (coverIndex != -1) {
                mCurrentPortraitPhotoPath = paths[coverIndex]
                setupStripeUpListener(adapter, screenWidth, itemWidth)

                binding.photoPortraitStripe.onGlobalLayout {
                    binding.photoPortraitStripe.scrollBy((coverIndex - fakeItemsCnt) * itemWidth, 0)
                    adapter.setCurrentPhoto(coverIndex)
                    binding.photoPortraitStripeWrapper.beVisible()
                    if (mIsFullscreen) {
                        binding.photoPortraitStripeWrapper.alpha = 0f
                    }
                }
            }
        }
    }

    private fun fillPhotoPaths(files: ArrayList<File>, fakeItemsCnt: Int): ArrayList<String> {
        val paths = ArrayList<String>()
        for (i in 0 until fakeItemsCnt) {
            paths.add("")
        }

        files.forEach {
            paths.add(it.absolutePath)
        }

        for (i in 0 until fakeItemsCnt) {
            paths.add("")
        }
        return paths
    }

    private fun getCoverImageIndex(paths: ArrayList<String>): Int {
        var coverIndex = -1
        paths.forEachIndexed { index, path ->
            if (path.contains("cover", true)) {
                coverIndex = index
            }
        }

        if (coverIndex == -1) {
            paths.forEachIndexed { index, path ->
                if (path.isNotEmpty()) {
                    coverIndex = index
                }
            }
        }
        return coverIndex
    }

    private fun setupStripeUpListener(adapter: PortraitPhotosAdapter, screenWidth: Int, itemWidth: Int) {
        binding.photoPortraitStripe.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                var closestIndex = -1
                var closestDistance = Integer.MAX_VALUE
                val center = screenWidth / 2
                for ((key, value) in adapter.views) {
                    val distance = Math.abs(value.x.toInt() + itemWidth / 2 - center)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestIndex = key
                    }
                }

                Handler().postDelayed({
                    adapter.performClickOn(closestIndex)
                }, 100)
            }
            false
        }
    }

    private fun getFilePathToShow() = if (mMedium.isPortrait()) mCurrentPortraitPhotoPath else getPathToLoad(mMedium)

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    private fun scheduleZoomableView() {
        if ((activity as? org.fossify.gallery.activities.ViewPagerActivity)?.isMediaTransitionRunning() == true) return
        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        mLoadZoomableViewHandler.postDelayed({
            if (mIsFragmentVisible && context?.config?.allowZoomingImages == true &&
                (mMedium.isImage() || mMedium.isPortrait()) && !mIsSubsamplingVisible) {
                if (mSubsamplingImageReady) revealReadySubsamplingImage()
                else if (!mSubsamplingImageLoading && gestureImageIsAtFit()) addZoomableView()
            }
        }, ZOOMABLE_VIEW_LOAD_DELAY)
    }

    private fun addZoomableView() {
        if ((activity as? org.fossify.gallery.activities.ViewPagerActivity)?.isMediaTransitionRunning() == true ||
            mSubsamplingImageLoading || mIsSubsamplingVisible || !gestureImageIsAtFit()) return
        val rotation = degreesForRotation(mImageOrientation)
        val generation = ++subsamplingGeneration
        val root = binding.root
        mSubsamplingImageLoading = true
        mSubsamplingImageReady = false
        val config = requireContext().config
        val showHighestQuality = config.showHighestQuality
        val minTileDpi = if (showHighestQuality) -1 else getMinTileDpi()

        val bitmapDecoder = object : DecoderFactory<ImageDecoder> {
            override fun make() = MyGlideImageDecoder(rotation, mMedium.getKey())
        }

        val regionDecoder = object : DecoderFactory<ImageRegionDecoder> {
            override fun make() = PicassoRegionDecoder(showHighestQuality, mScreenWidth, mScreenHeight, minTileDpi)
        }

        var newOrientation = (rotation + mCurrentRotationDegrees) % 360
        if (newOrientation < 0) {
            newOrientation += 360
        }

        binding.subsamplingView.apply {
            setMaxTileSize(if (showHighestQuality) Integer.MAX_VALUE else 4096)
            setMinimumTileDpi(minTileDpi)
            background = ColorDrawable(Color.TRANSPARENT)
            bitmapDecoderFactory = bitmapDecoder
            regionDecoderFactory = regionDecoder
            maxScale = 10f
            // Decode offscreen while Glide remains the interactive renderer.
            // This prevents a first-frame zoom reset and avoids intercepting a
            // user's gesture before the matching high-resolution image is ready.
            visibility = View.INVISIBLE
            rotationEnabled = config.allowRotatingWithGestures
            isOneToOneZoomEnabled = config.allowOneToOneZoom
            orientation = newOrientation
            alpha = 0f
            mSubsamplingImageReady = false

            onImageEventListener = object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {
                    if (generation != subsamplingGeneration || view !== root) return
                    // Async decoder readiness must not overwrite the black letterbox
                    // with the current (potentially light) gallery theme surface.
                    background = ColorDrawable(Color.BLACK)

                    val useWidth = if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sHeight else sWidth
                    val useHeight = if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                    mSubsamplingImageLoading = false
                    mSubsamplingImageReady = true
                    revealReadySubsamplingImage()
                }

                override fun onImageLoadError(e: Exception) {
                    if (generation != subsamplingGeneration || view !== root) return
                    binding.gesturesView.controller.settings.isZoomEnabled = true
                    background = ColorDrawable(Color.TRANSPARENT)
                    animate().cancel()
                    alpha = 0f
                    mIsSubsamplingVisible = false
                    mSubsamplingImageLoading = false
                    mSubsamplingImageReady = false
                    beGone()
                }

                override fun onImageRotation(degrees: Int) {
                    if (generation != subsamplingGeneration || view !== root) return
                    val fullRotation = (rotation + degrees) % 360
                    val useWidth = if (fullRotation == 90 || fullRotation == 270) sHeight else sWidth
                    val useHeight = if (fullRotation == 90 || fullRotation == 270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                    mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
                    loadBitmap(false)

                    // ugly, but it works
                    (activity as? ViewPagerActivity)?.refreshMenuItems()
                    (activity as? PhotoVideoActivity)?.refreshMenuItems()
                }

                override fun onUpEvent() {
                    if (generation != subsamplingGeneration || view !== root) return
                    mShouldResetImage = false
                }
            }
            // Install listeners before setImage: cached/small sources may report
            // readiness immediately. Keep the Glide frame underneath until the
            // matching fit-center Subsampling render is ready.
            setImage(getFilePathToShow())
        }
    }

    private fun revealReadySubsamplingImage() {
        if (!mSubsamplingImageReady || mSubsamplingImageLoading || mIsSubsamplingVisible ||
            !gestureImageIsAtFit() || !mIsFragmentVisible ||
            (activity as? ViewPagerActivity)?.isMediaTransitionRunning() == true ||
            !this::binding.isInitialized || binding.subsamplingView.visibility == View.GONE) return
        mIsSubsamplingVisible = true
        binding.subsamplingView.visibility = View.VISIBLE
        binding.subsamplingView.animate().cancel()
        binding.subsamplingView.alpha = 0f
        binding.subsamplingView.animate().alpha(1f).setDuration(90L).start()
    }

    private fun getMinTileDpi(): Int {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        val device = "${Build.BRAND} ${Build.MODEL}".lowercase(Locale.getDefault())
        return when {
            WEIRD_DEVICES.contains(device) -> WEIRD_TILE_DPI
            averageDpi > 400 -> HIGH_TILE_DPI
            averageDpi > 300 -> NORMAL_TILE_DPI
            else -> LOW_TILE_DPI
        }
    }

    private fun checkIfPanorama() {
        mIsPanorama = try {
            if (mMedium.path.startsWith("content:/")) {
                requireContext().contentResolver.openInputStream(Uri.parse(mMedium.path))
            } else {
                File(mMedium.path).inputStream()
            }.use {
                val imageParser = JpegImageParser().getXmpXml(ByteSourceInputStream(it, mMedium.name), HashMap<String, Any>())
                imageParser.contains("GPano:UsePanoramaViewer=\"True\"", true) ||
                    imageParser.contains("<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>", true) ||
                    imageParser.contains("GPano:FullPanoWidthPixels=") ||
                    imageParser.contains("GPano:ProjectionType>Equirectangular")
            }
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }

        activity?.runOnUiThread {
            binding.panoramaOutline.beVisibleIf(mIsPanorama)
            if (mIsFullscreen) {
                binding.panoramaOutline.alpha = 0f
            }
        }
    }

    private fun getImageOrientation(): Int {
        val defaultOrientation = -1
        var orient = defaultOrientation

        try {
            val path = getFilePathToShow()
            orient = if (path.startsWith("content:/")) {
                val inputStream = requireContext().contentResolver.openInputStream(Uri.parse(path))
                val exif = ExifInterface()
                exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                tag?.getValueAsInt(defaultOrientation) ?: defaultOrientation
            } else {
                val exif = androidx.exifinterface.media.ExifInterface(path)
                exif.getAttributeInt(TAG_ORIENTATION, defaultOrientation)
            }

            if (orient == defaultOrientation || requireContext().isPathOnOTG(getFilePathToShow())) {
                val uri = if (path.startsWith("content:/")) Uri.parse(path) else Uri.fromFile(File(path))
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val exif2 = ExifInterface()
                exif2.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                orient = exif2.getTag(ExifInterface.TAG_ORIENTATION)?.getValueAsInt(defaultOrientation) ?: defaultOrientation
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
        return orient
    }

    private fun getDoubleTapZoomScale(width: Int, height: Int): Float {
        val bitmapAspectRatio = height / width.toFloat()
        val screenAspectRatio = mScreenHeight / mScreenWidth.toFloat()

        return if (context == null || Math.abs(bitmapAspectRatio - screenAspectRatio) < SAME_ASPECT_RATIO_THRESHOLD) {
            DEFAULT_DOUBLE_TAP_ZOOM
        } else if (requireContext().portrait && bitmapAspectRatio <= screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else if (requireContext().portrait && bitmapAspectRatio > screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio >= screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio < screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else {
            DEFAULT_DOUBLE_TAP_ZOOM
        }
    }

    fun rotateImageViewBy(degrees: Int) {
        if (mIsSubsamplingVisible) {
            binding.subsamplingView.rotateBy(degrees)
        } else {
            hideZoomableView()
            mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
            mIsSubsamplingVisible = false
            loadBitmap()
        }
    }

    private fun initExtendedDetails() {
        val generation = ++detailsGeneration
        val medium = mMedium.copy()
        if (requireContext().config.showExtendedDetails) {
            ensureBackgroundThread {
                val details = getMediumExtendedDetails(medium)
                activity?.runOnUiThread {
                    if (generation != detailsGeneration || !isAdded || view == null || mMedium.path != medium.path || context?.config?.showExtendedDetails != true) return@runOnUiThread
                    binding.photoDetails.apply {
                        text = details
                        beVisibleIf(text.isNotEmpty())
                        val hideExtendedDetails = context?.config?.hideExtendedDetails == true
                        alpha = if (!hideExtendedDetails || !mIsFullscreen) 1f else 0f
                        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(this)
                    }
                }
            }
        } else {
            binding.photoDetails.beGone()
        }
    }

    private fun hideZoomableView() {
        if (context?.config?.allowZoomingImages == true) {
            mIsSubsamplingVisible = false
            mSubsamplingImageLoading = false
            mSubsamplingImageReady = false
            binding.subsamplingView.animate().cancel()
            binding.subsamplingView.alpha = 1f
            subsamplingGeneration++
            binding.subsamplingView.recycle()
            binding.subsamplingView.beGone()
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun photoClicked() {
        listener?.fragmentClicked()
    }

    private fun updateInstantSwitchWidths() {
        binding.instantPrevItem.layoutParams.width = mScreenWidth / 7
        binding.instantNextItem.layoutParams.width = mScreenWidth / 7
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        this.mIsFullscreen = isFullscreen
        binding.apply {
            photoDetails.apply {
                if (mStoredShowExtendedDetails && context != null && resources != null) {
                    if (mStoredHideExtendedDetails) {
                        let { org.fossify.gallery.helpers.ViewerChromeMotion.show(it, !isFullscreen) }
                    }
                }
            }

            org.fossify.gallery.helpers.ViewerChromeMotion.show(bottomActionsDummy, !isFullscreen)

            if (mIsPanorama) {
                panoramaOutline.let { org.fossify.gallery.helpers.ViewerChromeMotion.show(it, !isFullscreen) }
                panoramaOutline.isClickable = !isFullscreen
            }

            if (mWasInit && mMedium.isPortrait()) {
                photoPortraitStripeWrapper.let { org.fossify.gallery.helpers.ViewerChromeMotion.show(it, !isFullscreen) }
            }
        }
    }

    private fun applyProperColorMode(resource: Drawable?) {
        if (mIsFragmentVisible && activity != null) {
            ColorModeHelper.setColorModeForImage(
                activity = requireActivity(),
                bitmap = (resource as? BitmapDrawable)?.bitmap ?: resource?.toBitmapOrNull(),
                ultraHdr = context?.config?.ultraHdrRendering ?: true
            )
        }
    }

    private fun resetColorModeIfVisible() {
        if (mIsFragmentVisible) {
            ColorModeHelper.resetColorMode(activity)
        }
    }

    private fun reapplyColorModeIfNeeded() {
        if (mWasInit && mIsFragmentVisible) {
            val drawable = binding.gesturesView.drawable
            if (drawable != null && binding.gesturesView.isVisible()) {
                applyProperColorMode(drawable)
            } else {
                resetColorModeIfVisible()
            }
        }
    }
}
