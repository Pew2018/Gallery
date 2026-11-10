package org.fossify.gallery.helpers

import android.app.Activity
import org.fossify.gallery.R

/** Installs the shared Activity Window transition contract for folder and Viewer layers. */
object OxygenClassicActivityMotion {
    fun isShared(activity: Activity): Boolean = activity.intent.hasExtra(PhotoViewerTransition.EXTRA_PATH) ||
        activity.intent.getBooleanExtra(AlbumPageTransition.EXTRA_ACTIVE, false)

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

    fun mediaTransition(durationMs: Long, onEnd: (() -> Unit)? = null): android.transition.Transition =
        android.transition.TransitionSet().apply {
            ordering = android.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(android.transition.ChangeBounds())
            addTransition(android.transition.ChangeImageTransform())
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
