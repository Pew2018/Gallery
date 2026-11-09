package org.fossify.gallery.helpers

import android.view.View

/** Short, cancellable motion for changing the contents of a grouped folder page. */
object GalleryEntryMotion {
    const val DURATION_MS = 180L
    private const val DISTANCE_DP = 18f

    fun enter(view: View, forward: Boolean, animationsEnabled: Boolean = OxygenClassicMotion.enabled(view.context)) {
        view.animate().cancel()
        view.alpha = 1f
        view.translationX = 0f
        if (!animationsEnabled || !view.isAttachedToWindow) return

        val distance = DISTANCE_DP * view.resources.displayMetrics.density * if (forward) 1f else -1f
        view.alpha = 0.84f
        view.translationX = distance
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(DURATION_MS)
            .setInterpolator(OxygenClassicMotion.easing())
            .withEndAction {
                view.alpha = 1f
                view.translationX = 0f
            }
            .start()
    }
}
