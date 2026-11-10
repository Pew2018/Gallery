@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package org.fossify.gallery.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.DataSource as GlideDataSource
import com.bumptech.glide.load.engine.GlideException
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getVideoResolution
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.setDrawablesRelativeWithIntrinsicBounds
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.VideoActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getActionBarHeight
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.helpers.OxygenClassicMotion
import org.fossify.gallery.extensions.getFriendlyMessage
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.parseFileChannel
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.VideoPreviewFrameHandoff
import org.fossify.gallery.helpers.EXOPLAYER_MAX_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_MIN_BUFFER_MS
import org.fossify.gallery.helpers.FAST_FORWARD_VIDEO_MS
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.interfaces.PlaybackSpeedListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.MediaSideScroll
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import kotlin.math.abs

class VideoFragment : ViewPagerFragment(), TextureView.SurfaceTextureListener,
    SeekBar.OnSeekBarChangeListener, PlaybackSpeedListener {
    companion object {
        private const val PROGRESS = "progress"
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TOUCH_HOLD_DURATION_MS = 500L
        private const val TOUCH_HOLD_SPEED_MULTIPLIER = 2.0f
        private const val TOUCH_SLOP_DIVIDER = 3
    }

    private var mIsFullscreen = false
    private var mWasFragmentInit = false
    private var mIsPanorama = false
    private var mIsFragmentVisible = false
    private var mIsDragged = false
    private var mWasVideoStarted = false
    private var mWasPlayerInited = false
    private var mWasLastPositionRestored = false
    private var mPlayOnPrepared = false
    private var mIsPlayerPrepared = false
    private var mCurrTime = 0L
    private var mDuration = 0L
    private var mPositionWhenInit = 0L
    private var mPositionAtPause = 0L
    var mIsPlaying = false

    private var mExoPlayer: ExoPlayer? = null
    private var mVideoSurface: Surface? = null
    private var mVideoSize = Point(1, 1)
    private val mTimerHandler = Handler(Looper.getMainLooper())
    private val mProgressRunnable = object : Runnable {
        override fun run() {
            val player = mExoPlayer
            if (player != null && !mIsDragged && mIsPlaying) {
                mCurrTime = player.currentPosition
                mSeekBar.progress = mCurrTime.toInt()
                mCurrTimeView.text = mCurrTime.getFormattedDuration()
            }
            mTimerHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredBottomActions = true
    private var mStoredExtendedDetails = 0
    private var mStoredRememberLastVideoPosition = false
    private var mOriginalPlaybackSpeed = 1f
    private var mIsLongPressActive = false
    private var mHasAudio = true

    private val mTouchHoldRunnable = Runnable {
        mView.parent.requestDisallowInterceptTouchEvent(true)
        // This code runs after the delay, only if the user is still holding down.
        mIsLongPressActive = true
        mOriginalPlaybackSpeed = mExoPlayer?.playbackParameters?.speed ?: mConfig.playbackSpeed
        mView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updatePlaybackSpeed(TOUCH_HOLD_SPEED_MULTIPLIER)

        mPlaybackSpeedPill.fadeIn()
    }

    private lateinit var mTimeHolder: View
    private lateinit var mBrightnessSideScroll: MediaSideScroll
    private lateinit var mVolumeSideScroll: MediaSideScroll
    private lateinit var binding: PagerVideoItemBinding
    private var videoPreviewFrameHandoff: VideoPreviewFrameHandoff? = null

    private var sharedSurface: org.fossify.gallery.helpers.MediaSharedSurface? = null
    private var previewReady = false
    private var sharedTransitionRunning = false

    /** Use a frozen, content-only frame throughout the transition. */
    fun sharedElementViewFor(path: String): View? {
        if (!::mMedium.isInitialized || mMedium.path != path || !mIsFragmentVisible ||
            !isAdded || view == null || !previewReady ||
            binding.videoSurfaceFrame.controller.state.zoom != 1f) return null
        val drawable = if (binding.videoPreview.visibility != View.VISIBLE && mTextureView.isAvailable) {
            // Texture readback is limited to the already displayed preview dimensions.
            val bounds = org.fossify.gallery.helpers.MediaTransitionGeometry.fit(
                binding.root.width, binding.root.height, mVideoSize.x, mVideoSize.y) ?: return null
            val frame = runCatching { mTextureView.getBitmap(bounds.width().toInt().coerceAtLeast(1),
                bounds.height().toInt().coerceAtLeast(1)) }.getOrNull() ?: return null
            BitmapDrawable(resources, frame)
        } else binding.videoPreview.drawable ?: return null
        val bounds = org.fossify.gallery.helpers.MediaTransitionGeometry.fit(binding.root.width,
            binding.root.height, drawable.intrinsicWidth, drawable.intrinsicHeight) ?: return null
        sharedTransitionRunning = true
        videoPreviewFrameHandoff?.setTransitionRunning(true)
        val surface = sharedSurface ?: org.fossify.gallery.helpers.MediaSharedSurface(binding.root).also { sharedSurface = it }
        return surface.prepare(drawable, bounds, binding.videoPreview, binding.videoSurfaceFrame)
    }

    fun finishSharedTransition() {
        sharedSurface?.clear()
        sharedSurface = null
        sharedTransitionRunning = false
        videoPreviewFrameHandoff?.setTransitionRunning(false)
        if (view != null) setVideoSize()
    }

    fun setSharedChromeVisible(show: Boolean, animate: Boolean) {
        if (!this::binding.isInitialized) return
        val alpha = if (show && !mIsFullscreen) 1f else 0f
        val duration = if (show) OxygenClassicMotion.VIEWER_CHROME_ENTER_MS
            else OxygenClassicMotion.VIEWER_CHROME_EXIT_MS
        listOf(binding.videoPlayOutline, binding.bottomVideoTimeHolder.root, binding.videoDetails).forEach {
            it.animate().cancel()
            if (animate) it.animate().alpha(alpha).setDuration(duration)
                .setInterpolator(OxygenClassicMotion.easing()).start()
            else it.alpha = alpha
        }
    }

    private fun loadPreview() {
        val path = mMedium.path
        Glide.with(this).load(path).signature(mMedium.getKey())
            .fitCenter().dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?,
                    dataSource: GlideDataSource, isFirstResource: Boolean): Boolean {
                    binding.videoPreview.post {
                        if (view != null && mMedium.path == path && binding.videoPreview.drawable === resource) {
                            previewReady = true
                            if (mVideoSize.x <= 1 || mVideoSize.y <= 1) {
                                mVideoSize = Point(resource.intrinsicWidth, resource.intrinsicHeight)
                                setVideoSize()
                            }
                            if (mIsFragmentVisible) listener?.imageRendered(path)
                        }
                    }
                    return false
                }
            }).into(binding.videoPreview)
    }
    private lateinit var mView: View
    private lateinit var mMedium: Medium
    private lateinit var mConfig: Config
    private lateinit var mTextureView: TextureView
    private lateinit var mCurrTimeView: TextView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mSeekBar: SeekBar
    private lateinit var mPlaybackSpeedPill: TextView
    private var mTouchSlop = 0
    private var detailsGeneration = 0L
    private var mInitialX = 0f
    private var mInitialY = 0f

    override fun updateMedium(medium: Medium) {
        if (!::mMedium.isInitialized || medium.path != mMedium.path) return
        mMedium = medium
        super.updateMedium(medium)
        if (this::binding.isInitialized && view != null) checkExtendedDetails()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mConfig = context.config
        mTouchSlop = (ViewConfiguration.get(context).scaledTouchSlop) / TOUCH_SLOP_DIVIDER
        binding = PagerVideoItemBinding.inflate(inflater, container, false).apply {
            panoramaOutline.setOnClickListener { openPanorama() }
            bottomVideoTimeHolder.videoCurrTime.setOnClickListener { skip(false) }
            bottomVideoTimeHolder.videoDuration.setOnClickListener { skip(true) }
            videoHolder.setOnClickListener { toggleFullscreen() }
            bottomVideoTimeHolder.videoPlaybackSpeed.setOnClickListener { showPlaybackSpeedPicker() }
            bottomVideoTimeHolder.videoToggleMute.setOnClickListener {
                mConfig.muteVideos = !mConfig.muteVideos
                updatePlayerMuteState(showToast = true)
            }

            videoSurfaceFrame.controller.settings.swallowDoubleTaps = true

            videoPlayOutline.setOnClickListener {
                if (mConfig.gestureVideoPlayer) activity.launchGesturePlayer(mMedium.path) else togglePlayPause()
            }

            mPlayPauseButton = bottomVideoTimeHolder.videoTogglePlayPause
            mPlayPauseButton.setOnClickListener {
                togglePlayPause()
            }

            mSeekBar = bottomVideoTimeHolder.videoSeekbar
            val videoAccent = OxygenThemeEngine.snapshot(context, mConfig).interactiveAccent
            mSeekBar.progressTintList = ColorStateList.valueOf(videoAccent)
            mSeekBar.progressBackgroundTintList = ColorStateList.valueOf(0x80FFFFFF.toInt())
            mSeekBar.thumbTintList = ColorStateList.valueOf(videoAccent)
            mPlaybackSpeedPill = playbackSpeedPill
            mSeekBar.setOnSeekBarChangeListener(this@VideoFragment)
            // adding an empty click listener just to avoid ripple animation at toggling fullscreen
            mSeekBar.setOnClickListener { }

            mTimeHolder = bottomVideoTimeHolder.videoTimeHolder
            mCurrTimeView = bottomVideoTimeHolder.videoCurrTime
            mBrightnessSideScroll = videoBrightnessController
            mVolumeSideScroll = videoVolumeController
            mBrightnessSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mVolumeSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mTextureView = videoSurface
            mTextureView.surfaceTextureListener = this@VideoFragment

            val gestureDetector =
                GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mConfig.allowInstantChange) {
                            toggleFullscreen()
                            return true
                        }

                        val viewWidth = root.width
                        val instantWidth = viewWidth / 7
                        val clickedX = e.rawX
                        when {
                            clickedX <= instantWidth -> listener?.goToPrevItem()
                            clickedX >= viewWidth - instantWidth -> listener?.goToNextItem()
                            else -> toggleFullscreen()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        handleDoubleTap(e.rawX)
                        return true
                    }
                })

            videoSurfaceFrame.setOnTouchListener { view, event ->
                if (videoSurfaceFrame.controller.state.zoom == 1f) {
                    handleEvent(event)
                }
                handleTouchHoldEvent(event)
                if (mIsLongPressActive) {
                    return@setOnTouchListener true
                }

                gestureDetector.onTouchEvent(event)
                false
            }
        }
        if ((activity as? org.fossify.gallery.activities.ViewPagerActivity)?.isMediaTransitionRunning() == true) {
            setSharedChromeVisible(false, false)
        }
        videoPreviewFrameHandoff = VideoPreviewFrameHandoff(binding.videoPreview).also {
            it.setTransitionRunning((activity as? org.fossify.gallery.activities.ViewPagerActivity)?.isMediaTransitionRunning() == true)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())

            val pillTopMargin = system.top + resources.getActionBarHeight(context) +
                resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()
            (mPlaybackSpeedPill.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                setMargins(
                    0, pillTopMargin, 0, 0
                )
            }

            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        mView = binding.root
        binding.videoSurfaceFrame.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) setVideoSize()
        }

        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        storeStateVariables()
        loadPreview()

        // setMenuVisibility is not called at VideoActivity (third party intent)
        if (!mIsFragmentVisible && activity is VideoActivity) {
            mIsFragmentVisible = true
        }

        mIsFullscreen = listener?.isFullScreen() == true
        initTimeHolder()
        // checkIfPanorama() TODO: Implement panorama using a FOSS library

        val resolutionPath = mMedium.path
        ensureBackgroundThread {
            val size = activity.getVideoResolution(resolutionPath)
            activity.runOnUiThread {
                if (view != null && mMedium.path == resolutionPath && size != null && size.x > 0 && size.y > 0) {
                    mVideoSize = size
                    setVideoSize()
                }
            }
        }

        if (mIsPanorama) {
            binding.apply {
                panoramaOutline.beVisible()
                videoPlayOutline.beGone()
                mVolumeSideScroll.beGone()
                mBrightnessSideScroll.beGone()
                loadPreview()
            }
        }

        if (!mIsPanorama) {
            if (savedInstanceState != null) {
                mCurrTime = savedInstanceState.getLong(PROGRESS, 0L)
            }

            mWasFragmentInit = true
            setVideoSize()

            binding.apply {
                mBrightnessSideScroll.initialize(
                    activity,
                    slideInfo,
                    true,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToPrevItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(false)
                    })
                mVolumeSideScroll.initialize(
                    activity,
                    slideInfo,
                    false,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToNextItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(true)
                    })

                videoSurface.onGlobalLayout {
                    if (mIsFragmentVisible && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                        playVideo()
                    }
                }
            }
        }

        setupVideoDuration()
        if (mStoredRememberLastVideoPosition) {
            restoreLastVideoSavedPosition()
        }

        return mView
    }

    override fun onResume() {
        super.onResume()
        mConfig =
            requireContext().config      // make sure we get a new config, in case the user changed something in the app settings
        requireActivity().updateTextColors(binding.videoHolder)
        val allowVideoGestures = mConfig.allowVideoGestures
        mTextureView.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        binding.videoSurfaceFrame.beGoneIf(mTextureView.isGone())

        mVolumeSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)
        mBrightnessSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)

        checkExtendedDetails()
        initTimeHolder()
        storeStateVariables()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        pauseVideo()
        if (mStoredRememberLastVideoPosition && mIsFragmentVisible && mWasVideoStarted) {
            saveVideoProgress()
        }
    }

    override fun onDestroyView() {
        sharedSurface?.clear()
        sharedSurface = null
        previewReady = false
        Glide.with(this).clear(binding.videoPreview)
        org.fossify.gallery.helpers.ViewerChromeMotion.cancel(binding.root)
        detailsGeneration++
        // Fragment views are discarded on rotation too. Never keep their player,
        // timer or video Surface alive across view destruction.
        cleanup()
        mTextureView.surfaceTextureListener = null
        videoPreviewFrameHandoff?.clear()
        videoPreviewFrameHandoff = null
        super.onDestroyView()
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (mIsFragmentVisible && !menuVisible) {
            pauseVideo()
        }

        mIsFragmentVisible = menuVisible
        if (menuVisible && previewReady) binding.videoPreview.post {
            if (view != null && mIsFragmentVisible) listener?.imageRendered(mMedium.path)
        }
        val shouldPlayVideo = mWasFragmentInit && menuVisible && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer
        if (shouldPlayVideo) playVideo()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setVideoSize()
        binding.videoSurfaceFrame.onGlobalLayout {
            binding.videoSurfaceFrame.controller.resetState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(PROGRESS, mCurrTime)
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredExtendedDetails = extendedDetails
            mStoredBottomActions = bottomActions
            mStoredRememberLastVideoPosition = rememberLastVideoPosition
        }
    }

    private fun saveVideoProgress() {
        if (!videoEnded()) {
            if (mExoPlayer != null) {
                mConfig.saveLastVideoPosition(
                    mMedium.path,
                    mExoPlayer!!.currentPosition.toInt() / 1000
                )
            } else {
                mConfig.saveLastVideoPosition(mMedium.path, mPositionAtPause.toInt() / 1000)
            }
        }
    }

    private fun restoreLastVideoSavedPosition() {
        val seconds = mConfig.getLastVideoPosition(mMedium.path)
        if (seconds > 0) {
            mPositionAtPause = seconds * 1000L
            setPosition(seconds * 1000L)
        }
    }

    private fun setupTimeHolder() {
        mSeekBar.max = mDuration.toInt()
        binding.bottomVideoTimeHolder.videoDuration.text = mDuration.getFormattedDuration()
        setupTimer()
    }

    private fun setupTimer() {
        // Repeated duration updates must not schedule parallel 250-ms loops.
        mTimerHandler.removeCallbacks(mProgressRunnable)
        mTimerHandler.post(mProgressRunnable)
    }

    private fun initExoPlayer() {
        val shouldSkipInit = activity == null || mConfig.gestureVideoPlayer || mIsPanorama || mExoPlayer != null
        if (shouldSkipInit) return

        val isContentUri = mMedium.path.startsWith("content://")
        val uri = if (isContentUri) Uri.parse(mMedium.path) else Uri.fromFile(File(mMedium.path))
        val dataSpec = DataSpec(uri)
        val fileDataSource = if (isContentUri) {
            ContentDataSource(requireContext())
        } else {
            FileDataSource()
        }

        try {
            fileDataSource.open(dataSpec)
        } catch (e: Exception) {
            fileDataSource.close()
            activity?.showErrorToast(e)
            return
        }

        val factory = DataSource.Factory { fileDataSource }
        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(fileDataSource.uri!!))

        fileDataSource.close()

        mPlayOnPrepared = true

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MAX_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        mExoPlayer = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(DefaultMediaSourceFactory(requireContext()))
            .setSeekParameters(SeekParameters.EXACT)
            .setLoadControl(loadControl)
            .build()
            .apply {
                if (mConfig.loopVideos && listener?.isSlideShowActive() == false) {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
                setPlaybackSpeed(mConfig.playbackSpeed)
                setMediaSource(mediaSource)
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), false
                )
                prepare()

                mTextureView.surfaceTexture?.let { attachVideoSurface(this, it) }

                initListeners()
            }

        updatePlayerMuteState()
    }

    private fun ExoPlayer.initListeners() {
        val listenerPlayer = this
        videoPreviewFrameHandoff?.bind(listenerPlayer)
        addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                @Player.DiscontinuityReason reason: Int,
            ) {
                // Reset progress views when video loops.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mSeekBar.progress = 0
                    mCurrTimeView.text = 0.getFormattedDuration()
                }
            }

            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> videoPrepared()
                    Player.STATE_ENDED -> videoCompleted()
                }
            }

            override fun onRenderedFirstFrame() {
                // Keep the preview visible until the decoder really displays a frame.
                // Player.STATE_READY alone does not guarantee that the Surface has one.
                if (mExoPlayer === listenerPlayer && view != null) {
                    videoPreviewFrameHandoff?.onRenderedFirstFrame(listenerPlayer)
                    // A fast decoder must not release the shared transition before its
                    // stable preview has loaded. loadPreview owns that readiness signal.
                    org.fossify.gallery.helpers.GalleryPerfTrace.event("viewer_video_first_frame")
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                mVideoSize.x = videoSize.width
                mVideoSize.y = (videoSize.height / videoSize.pixelWidthHeightRatio).toInt()
                setVideoSize()
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (mExoPlayer !== listenerPlayer || view == null) return
                binding.errorMessageHolder.errorMessage.apply {
                    if (error != null) {
                        // Keep the cover behind the error message if the decoder
                        // never produced a visible frame.
                        videoPreviewFrameHandoff?.keepCoverForError(listenerPlayer)
                        binding.videoPlayOutline.beGone()
                        text = error.getFriendlyMessage(context)
                        setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                        fadeIn()
                    } else {
                        beGone()
                        // A retry remains covered until onRenderedFirstFrame arrives.
                        videoPreviewFrameHandoff?.keepCoverForError(listenerPlayer)
                        binding.videoPlayOutline.beVisible()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                mHasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
                updatePlayerMuteState()
            }
        })
    }

    private fun toggleFullscreen() {
        listener?.fragmentClicked()
    }

    private fun handleDoubleTap(x: Float) {
        val viewWidth = mView.width
        val instantWidth = viewWidth / 7
        when {
            x <= instantWidth -> doSkip(false)
            x >= viewWidth - instantWidth -> doSkip(true)
            else -> togglePlayPause()
        }
    }

    private fun checkExtendedDetails() {
        val generation = ++detailsGeneration
        val medium = mMedium.copy()
        if (mConfig.showExtendedDetails) {
            ensureBackgroundThread {
                val details = getMediumExtendedDetails(medium)
                activity?.runOnUiThread {
                    if (generation != detailsGeneration || !isAdded || view == null || mMedium.path != medium.path || !mConfig.showExtendedDetails) return@runOnUiThread
                    binding.videoDetails.apply {
                        text = details
                        beVisibleIf(text.isNotEmpty())
                        alpha = if (!mConfig.hideExtendedDetails || !mIsFullscreen) 1f else 0f
                        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(this)
                    }
                }
            }
        } else {
            binding.videoDetails.beGone()
        }
    }

    private fun initTimeHolder() {
        mTimeHolder.beGoneIf(mIsFullscreen)
        mTimeHolder.alpha = if (mIsFullscreen) 0f else 1f
        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(mTimeHolder)
    }

    private fun checkIfPanorama() {
        try {
            val fis = FileInputStream(File(mMedium.path))
            fis.use {
                requireContext().parseFileChannel(mMedium.path, it.channel, 0, 0, 0) {
                    mIsPanorama = true
                }
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
    }

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        mIsFullscreen = isFullscreen

        mSeekBar.setOnSeekBarChangeListener(if (mIsFullscreen) null else this)
        arrayOf(
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoDuration,
            binding.bottomVideoTimeHolder.videoTogglePlayPause,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute
        ).forEach {
            it.isClickable = !mIsFullscreen
        }

        org.fossify.gallery.helpers.ViewerChromeMotion.show(mTimeHolder, !isFullscreen)
        org.fossify.gallery.helpers.ViewerChromeMotion.show(binding.bottomActionsDummy, !isFullscreen)

        binding.videoDetails.apply {
            if (mStoredShowExtendedDetails && context != null && resources != null) {
                if (mStoredHideExtendedDetails) {
                    let { org.fossify.gallery.helpers.ViewerChromeMotion.show(it, !isFullscreen) }
                }
            }
        }
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        childFragmentManager.beginTransaction().add(fragment, fragment::class.java.simpleName)
            .commit()
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag as? Boolean) {
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag = isSlow

            val drawableId =
                if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            context?.let {
                binding.bottomVideoTimeHolder.videoPlaybackSpeed
                    .setDrawablesRelativeWithIntrinsicBounds(
                        AppCompatResources.getDrawable(it, drawableId)
                    )
            }
        }

        @SuppressLint("SetTextI18n")
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(speed)}x"
        mExoPlayer?.setPlaybackSpeed(speed)
    }

    private fun skip(forward: Boolean) {
        if (mIsPanorama) {
            return
        } else if (mExoPlayer == null) {
            playVideo()
            return
        }

        mPositionAtPause = 0L
        doSkip(forward)
    }

    private fun doSkip(forward: Boolean) {
        if (mExoPlayer == null) {
            return
        }

        val curr = mExoPlayer!!.currentPosition
        var newPosition =
            if (forward) curr + FAST_FORWARD_VIDEO_MS else curr - FAST_FORWARD_VIDEO_MS
        newPosition = newPosition.coerceIn(0, maxOf(mExoPlayer!!.duration, 0))
        setPosition(newPosition)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val newPosition = progress.toLong()
            if (mExoPlayer != null) {
                if (!mWasPlayerInited) {
                    mPositionWhenInit = newPosition
                }
                setPosition(newPosition)
            }

            if (mExoPlayer == null) {
                mPositionAtPause = newPosition
                playVideo()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (mExoPlayer == null) {
            return
        }

        mExoPlayer!!.playWhenReady = false
        mIsDragged = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (mIsPanorama) {
            openPanorama()
            return
        }

        if (mExoPlayer == null) {
            return
        }

        if (mIsPlaying) {
            mExoPlayer!!.playWhenReady = true
        }

        mIsDragged = false
    }

    private fun togglePlayPause() {
        if (activity == null || !isAdded) {
            return
        }

        if (mIsPlaying) {
            pauseVideo()
        } else {
            playVideo()
        }
    }

    private fun updatePlayerMuteState(showToast: Boolean = false) {
        val isMuted = mConfig.muteVideos
        if (mHasAudio) {
            if (isMuted) mExoPlayer?.mute() else mExoPlayer?.unmute()
        } else if (showToast && mWasVideoStarted) {
            activity?.toast(R.string.video_no_sound)
        }

        val drawableId = when {
            !mHasAudio -> R.drawable.ic_vector_no_sound
            isMuted -> R.drawable.ic_vector_speaker_off
            else -> R.drawable.ic_vector_speaker_on
        }

        binding.bottomVideoTimeHolder.videoToggleMute.setImageResource(drawableId)
    }

    /** Lifecycle and media identity can change between page selection and playback. */
    fun playSlideshowVideo(expectedPath: String): Boolean {
        if (!isResumed || view == null || !::mMedium.isInitialized || !mWasFragmentInit ||
            mMedium.path != expectedPath || listener?.isSlideShowActive() != true) return false
        playVideo()
        return true
    }

    fun playVideo() {
        if (mExoPlayer == null) {
            initExoPlayer()
            return
        }

        // The preview is removed by onRenderedFirstFrame, not when playback
        // is requested, so slow decoders do not expose a blank black Surface.
        val wasEnded = videoEnded()
        if (wasEnded) {
            setPosition(0)
        }

        if (mStoredRememberLastVideoPosition && !mWasLastPositionRestored) {
            mWasLastPositionRestored = true
            restoreLastVideoSavedPosition()
        }

        if (!wasEnded || !mConfig.loopVideos) {
            mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_pause_outline_vector)
        }

        if (!mWasVideoStarted) {
            binding.videoPlayOutline.beGone()
            mPlayPauseButton.beVisible()
            binding.bottomVideoTimeHolder.videoToggleMute.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
                "${DecimalFormat("#.##").format(mConfig.playbackSpeed)}x"
        }

        mWasVideoStarted = true
        if (mIsPlayerPrepared) {
            mIsPlaying = true
        }
        mExoPlayer?.playWhenReady = true
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun pauseVideo() {
        if (mExoPlayer == null) {
            return
        }

        mIsPlaying = false
        if (!videoEnded()) {
            mExoPlayer?.playWhenReady = false
        }

        mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mPositionAtPause = mExoPlayer?.currentPosition ?: 0L
    }

    private fun videoEnded(): Boolean {
        val currentPos = mExoPlayer?.currentPosition ?: 0
        val duration = mExoPlayer?.duration ?: 0
        return currentPos != 0L && currentPos >= duration
    }

    private fun setPosition(milliseconds: Long) {
        mExoPlayer?.seekTo(milliseconds)
        mSeekBar.progress = milliseconds.toInt()
        mCurrTimeView.text = milliseconds.getFormattedDuration()

        if (!mIsPlaying) {
            mPositionAtPause = milliseconds
        }
    }

    private fun setupVideoDuration() {
        ensureBackgroundThread {
            mDuration = context?.getDuration(mMedium.path)?.times(1000L)?.coerceAtLeast(0L) ?: 0L

            activity?.runOnUiThread {
                setupTimeHolder()
                setPosition(0)
            }
        }
    }

    private fun videoPrepared() {
        if (mDuration == 0L) {
            mDuration = mExoPlayer!!.duration
            setupTimeHolder()
            setPosition(mCurrTime)

            if (mIsFragmentVisible && (mConfig.autoplayVideos)) {
                playVideo()
            }
        }

        if (mPositionWhenInit != 0L && !mWasPlayerInited) {
            setPosition(mPositionWhenInit)
            mPositionWhenInit = 0
        }

        mIsPlayerPrepared = true
        if (mPlayOnPrepared && !mIsPlaying) {
            if (mPositionAtPause != 0L) {
                mExoPlayer?.seekTo(mPositionAtPause)
                mPositionAtPause = 0L
            }
            updatePlaybackSpeed(mConfig.playbackSpeed)
            playVideo()
        }
        mWasPlayerInited = true
        mPlayOnPrepared = false
    }

    private fun videoCompleted() {
        if (!isAdded || mExoPlayer == null) {
            return
        }

        mCurrTime = mExoPlayer!!.duration
        if (listener?.videoEnded() == false && mConfig.loopVideos) {
            playVideo()
        } else {
            mSeekBar.progress = mSeekBar.max
            mCurrTimeView.text = mDuration.getFormattedDuration()
            pauseVideo()
        }
    }

    private fun cleanup() {
        mTimerHandler.removeCallbacksAndMessages(null)
        pauseVideo()
        mExoPlayer?.clearVideoSurface()
        mVideoSurface?.release()
        mVideoSurface = null
        releaseExoPlayer()

        if (mWasFragmentInit) {
            mCurrTimeView.text = 0.getFormattedDuration()
            mSeekBar.progress = 0
        }
    }

    private fun releaseExoPlayer() {
        mIsPlayerPrepared = false
        mExoPlayer?.apply {
            stop()
            release()
        }
        mExoPlayer = null
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun attachVideoSurface(player: ExoPlayer, texture: SurfaceTexture) {
        player.clearVideoSurface()
        mVideoSurface?.release()
        mVideoSurface = Surface(texture).also { player.setVideoSurface(it) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mExoPlayer?.clearVideoSurface()
        mVideoSurface?.release()
        mVideoSurface = null
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mExoPlayer?.let { attachVideoSurface(it, surface) }
    }

    private fun setVideoSize() {
        if (activity == null || mConfig.gestureVideoPlayer || sharedTransitionRunning ||
            mVideoSize.x <= 1 || mVideoSize.y <= 1) return
        val bounds = org.fossify.gallery.helpers.MediaTransitionGeometry.fit(
            binding.videoSurfaceFrame.width, binding.videoSurfaceFrame.height, mVideoSize.x, mVideoSize.y) ?: return
        mTextureView.layoutParams = mTextureView.layoutParams.apply {
            width = bounds.width().toInt().coerceAtLeast(1)
            height = bounds.height().toInt().coerceAtLeast(1)
        }
    }

    private fun handleTouchHoldEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mIsPlaying && event.pointerCount == 1) {
                    mInitialX = event.x
                    mInitialY = event.y
                    mTimerHandler.postDelayed(mTouchHoldRunnable, TOUCH_HOLD_DURATION_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - mInitialX)
                val deltaY = abs(event.y - mInitialY)
                if (!mIsLongPressActive && (deltaX > mTouchSlop || deltaY > mTouchSlop)) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mIsLongPressActive) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                stopHoldSpeedMultiplierGesture()
            }
        }
    }

    private fun stopHoldSpeedMultiplierGesture() {
        if (mIsLongPressActive) {
            updatePlaybackSpeed(mOriginalPlaybackSpeed)
            mIsLongPressActive = false
            mPlaybackSpeedPill.fadeOut()
        }
    }
}
