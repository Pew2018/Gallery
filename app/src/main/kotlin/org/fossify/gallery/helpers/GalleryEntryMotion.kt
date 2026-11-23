package org.fossify.gallery.helpers

import android.view.View

/**
 * Short, cancellable motion for changing the contents of a grouped folder page.
 *
 * One page, one owner: entering a nested folder only moves the grid container. It must
 * never run together with a window animation, because the page itself is already on
 * screen and a second fade would read as the list loading twice.
 */
object GalleryEntryMotion {
    const val DURATION_MS = OxygenClassicMotion.NESTED_FOLDER_MS
    private const val DISTANCE_DP = OxygenClassicMotion.NESTED_FOLDER_DISTANCE_DP
    private const val START_ALPHA = OxygenClassicMotion.NESTED_FOLDER_START_ALPHA

    fun enter(view: View, forward: Boolean, animationsEnabled: Boolean = OxygenClassicMotion.enabled(view.context)) {
        // Cancel first: a fast back gesture can re-enter while the previous animator
        // still holds translation, and a queued end action would then reset a newer run.
        view.animate().cancel()
        view.alpha = 1f
        view.translationX = 0f
        if (!animationsEnabled || !view.isAttachedToWindow) return

        val distance = DISTANCE_DP * view.resources.displayMetrics.density *
            OxygenClassicMotion.horizontalDirection(forward, view)
        view.alpha = START_ALPHA
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
