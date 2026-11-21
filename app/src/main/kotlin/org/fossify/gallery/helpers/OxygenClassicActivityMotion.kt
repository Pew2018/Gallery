package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Matrix
import android.graphics.Point
import android.os.Build
import android.transition.ChangeImageTransform
import android.transition.TransitionValues
import android.view.ViewGroup
import android.widget.ImageView
import org.fossify.gallery.R

/** Installs the shared Activity Window transition contract for folder and Viewer layers. */
object OxygenClassicActivityMotion {
    fun isShared(activity: Activity): Boolean = activity.intent.hasExtra(PhotoViewerTransition.EXTRA_PATH)

    fun configureShared(activity: Activity) {
        activity.window.apply {
            setWindowAnimations(0)
            enterTransition = null
            exitTransition = null
            reenterTransition = null
            returnTransition = null
            // The content proxy is already a standalone ImageView above the
            // renderer. Keeping it in the scene hierarchy avoids the extra
            // overlay snapshot that can flash beside the still-visible window
            // content during the cross-Activity return on Android 10.
            sharedElementsUseOverlay = false
            transitionBackgroundFadeDuration = 0L
        }
    }

    internal fun mediaTransition(
        durationMs: Long,
        onEnd: (() -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null,
        onTerminal: ((cancelled: Boolean) -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        cropMediaToBounds: Boolean = false,
        forceMediaCropToBounds: (() -> Boolean)? = null,
        staticPhotoEntry: Boolean = false,
        photoReturnTarget: (() -> StaticPhotoReturnTarget?)? = null,
    ): android.transition.Transition =
        android.transition.TransitionSet().apply {
            ordering = android.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(android.transition.ChangeBounds())
            // Drag-to-dismiss leaves the shared media surface translated and scaled.
            // Capture those properties so the reverse handoff can start at the live frame.
            addTransition(android.transition.ChangeTransform())
            addTransition(when {
                staticPhotoEntry -> StaticPhotoEntryTransform()
                cropMediaToBounds && Build.VERSION.SDK_INT >= 29 ->
                    MediaReturnCrop(forceMediaCropToBounds, photoReturnTarget)
                else -> ChangeImageTransform()
            })
            if (onProgress != null) addTransition(SharedElementProgress(durationMs, onProgress))
            duration = durationMs
            interpolator = OxygenClassicMotion.easing()
            if (onEnd != null || onTerminal != null || onStart != null) addListener(object : android.transition.Transition.TransitionListener {
                private var ended = false
                private fun complete(cancelled: Boolean) {
                    if (ended) return
                    ended = true
                    onEnd?.invoke()
                    onTerminal?.invoke(cancelled)
                }
                override fun onTransitionStart(transition: android.transition.Transition) { onStart?.invoke() }
                override fun onTransitionEnd(transition: android.transition.Transition) = complete(false)
                override fun onTransitionCancel(transition: android.transition.Transition) = complete(true)
                override fun onTransitionPause(transition: android.transition.Transition) = Unit
                override fun onTransitionResume(transition: android.transition.Transition) = Unit
            })
        }

    private class StaticPhotoEntryTransform : android.transition.Transition() {
        override fun captureStartValues(transitionValues: TransitionValues) = capture(transitionValues, false)

        override fun captureEndValues(transitionValues: TransitionValues) = capture(transitionValues, true)

        private fun capture(values: TransitionValues, end: Boolean) {
            val proxy = values.view as? StaticPhotoEntryView ?: return
            val expected = if (end) proxy.geometry.target.pixels else proxy.geometry.source.view.layout
            if (StaticPhotoViewGeometry.layout(proxy)?.near(expected) == true) values.values[END] = end
        }

        override fun getTransitionProperties() = arrayOf(END)

        override fun createAnimator(
            sceneRoot: ViewGroup,
            startValues: TransitionValues?,
            endValues: TransitionValues?,
        ): ValueAnimator? {
            val proxy = endValues?.view as? StaticPhotoEntryView ?: return null
            if (startValues?.view !== proxy || startValues.values[END] != false || endValues.values[END] != true) return null
            return ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { proxy.animateFrame(it.animatedValue as Float) }
            }
        }

        private companion object {
            const val END = "gallery:staticPhotoEntry:end"
        }
    }

    @androidx.annotation.RequiresApi(29)
    private class MediaReturnCrop(
        private val forceCrop: (() -> Boolean)?,
        private val photoTarget: (() -> StaticPhotoReturnTarget?)?,
    ) : ChangeImageTransform() {
        override fun captureStartValues(transitionValues: TransitionValues) {
            super.captureStartValues(transitionValues)
            captureViewport(transitionValues)
            (transitionValues.view as? MeasuredPhotoReturnView)?.let { proxy ->
                StaticPhotoViewGeometry.read(proxy)?.let { transitionValues.values[PHOTO_START] = it }
            }
        }

        override fun captureEndValues(transitionValues: TransitionValues) {
            super.captureEndValues(transitionValues)
            captureViewport(transitionValues)
        }

        private fun captureViewport(values: TransitionValues) {
            val image = values.view as? ImageView ?: return
            if ((image.scaleType == ImageView.ScaleType.CENTER_CROP || forceCrop?.invoke() == true) &&
                image.width > 0 && image.height > 0) {
                values.values[VIEWPORT] = Point(image.width, image.height)
            }
        }

        override fun createAnimator(
            sceneRoot: ViewGroup,
            startValues: TransitionValues?,
            endValues: TransitionValues?,
        ): android.animation.Animator? {
            val photo = endValues?.view as? MeasuredPhotoReturnView
            val origin = startValues?.values?.get(PHOTO_START) as? PhotoViewGeometry
            val destination = photoTarget?.invoke()
            if (photo != null && origin != null && destination != null) {
                if (photo.prepare(origin, destination)) {
                    return ValueAnimator.ofFloat(0f, 1f).apply {
                        addUpdateListener { photo.showFrame(it.animatedValue as Float) }
                    }
                }
            }
            val start = startValues?.values?.get(VIEWPORT) as? Point
            val end = endValues?.values?.get(VIEWPORT) as? Point
            val image = endValues?.view as? ImageView
            val drawable = image?.drawable
            if (start == null || end == null || image == null || drawable == null ||
                drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                return super.createAnimator(sceneRoot, startValues, endValues)
            }
            val matrix = Matrix()
            val contentWidth = drawable.intrinsicWidth
            val contentHeight = drawable.intrinsicHeight
            // Derive the crop from the current aperture and this media frame.
            // The destination thumbnail can have different drawable dimensions.
            fun applyCrop(fraction: Float) {
                val width = start.x + (end.x - start.x) * fraction
                val height = start.y + (end.y - start.y) * fraction
                MediaTransitionGeometry.centerCrop(matrix, width, height, contentWidth, contentHeight)
                image.animateTransform(matrix)
            }
            applyCrop(0f)
            return ValueAnimator.ofFloat(0f, 1f).apply {
                addUpdateListener { applyCrop(it.animatedFraction) }
            }
        }

        private companion object {
            const val VIEWPORT = "gallery_video_return_viewport"
            const val PHOTO_START = "gallery:photoReturn:start"
        }
    }

    /** Drives the viewer scrim on the same transition clock as shared image geometry. */
    private class SharedElementProgress(
        durationMs: Long,
        private val onProgress: (Float) -> Unit,
    ) : android.transition.Transition() {
        init {
            addTarget(PhotoViewerTransition.ELEMENT_NAME)
            duration = durationMs
            interpolator = OxygenClassicMotion.easing()
        }

        override fun captureStartValues(transitionValues: TransitionValues) {
            transitionValues.values[PROGRESS] = 0f
        }

        override fun captureEndValues(transitionValues: TransitionValues) {
            transitionValues.values[PROGRESS] = 1f
        }

        override fun getTransitionProperties() = arrayOf(PROGRESS)

        override fun createAnimator(
            sceneRoot: ViewGroup,
            startValues: TransitionValues?,
            endValues: TransitionValues?,
        ): android.animation.Animator? {
            if (startValues == null || endValues == null) return null
            return ValueAnimator.ofFloat(
                startValues.values[PROGRESS] as Float,
                endValues.values[PROGRESS] as Float,
            ).apply {
                duration = this@SharedElementProgress.duration
                interpolator = this@SharedElementProgress.interpolator ?: OxygenClassicMotion.easing()
                addUpdateListener { onProgress(it.animatedValue as Float) }
            }
        }

        private companion object {
            const val PROGRESS = "oxygen_classic_shared_element_progress"
        }
    }

    fun install(activity: Activity) {
        if (isShared(activity)) configureShared(activity)
        else if (GalleryBottomBar.isRootTransition(activity)) {
            GalleryBottomBar.restoreRootWindow(activity)
        } else activity.window.setWindowAnimations(R.style.OxygenClassicPageWindow)
    }

    fun applyClose(activity: Activity) {
        if (isShared(activity)) return
        if (GalleryBottomBar.isRetiringRoot(activity)) {
            GalleryBottomBar.restoreRootWindow(activity)
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.overridePendingTransition(
            R.anim.oxygen_classic_page_close_enter,
            R.anim.oxygen_classic_page_close_exit
        )
    }
}
