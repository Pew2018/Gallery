package org.fossify.gallery.helpers

import android.app.Activity
import android.animation.ValueAnimator
import android.view.ViewGroup
import android.transition.TransitionValues
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

    fun mediaTransition(
        durationMs: Long,
        onEnd: (() -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): android.transition.Transition =
        android.transition.TransitionSet().apply {
            ordering = android.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(android.transition.ChangeBounds())
            // Drag-to-dismiss leaves the shared photo surface translated and scaled.
            // Capture those properties so the reverse handoff can start at the live frame.
            addTransition(android.transition.ChangeTransform())
            addTransition(android.transition.ChangeImageTransform())
            if (onProgress != null) addTransition(SharedElementProgress(durationMs, onProgress))
            duration = durationMs
            interpolator = OxygenClassicMotion.easing()
            if (onEnd != null) addListener(object : android.transition.Transition.TransitionListener {
                private var ended = false
                private fun complete() { if (!ended) { ended = true; onEnd() } }
                override fun onTransitionStart(transition: android.transition.Transition) = Unit
                override fun onTransitionEnd(transition: android.transition.Transition) = complete()
                override fun onTransitionCancel(transition: android.transition.Transition) = complete()
                override fun onTransitionPause(transition: android.transition.Transition) = Unit
                override fun onTransitionResume(transition: android.transition.Transition) = Unit
            })
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
