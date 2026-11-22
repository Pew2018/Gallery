package org.fossify.gallery.helpers

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback

/**
 * Routes system and predictive back into the Viewer's existing Session return core.
 *
 * The callback is only enabled while the app owns an in-app back step. In a stable
 * Viewer page on Android 14+ it must stay disabled: an enabled default-priority
 * callback consumes the gesture and suppresses the platform's cross-Activity
 * window animation, which is the animation the user asked the Viewer to use. The
 * Activity flips [isEnabled] from [ViewerBackOwnership] as the page state changes.
 *
 * When enabled, progress still feeds the existing interactive envelope so an
 * already-running in-app step can settle. [swipeEdge] is taken from the official
 * BackEventCompat event instead of being inferred from a touch X coordinate.
 */
internal class ViewerBackGesture(
    private val beginInteractive: (Int) -> Boolean,
    private val renderProgress: (Float) -> Unit,
    private val cancelInteractive: () -> Unit,
    private val commit: () -> Unit,
) : OnBackPressedCallback(false) {
    private var interactive = false

    /** True while a gesture drives the existing interactive return surface. */
    val ownsInteractiveReturn get() = interactive

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        interactive = beginInteractive(backEvent.swipeEdge)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (!interactive) return
        renderProgress(backEvent.progress.coerceIn(0f, 1f))
    }

    override fun handleOnBackCancelled() {
        if (interactive) cancelInteractive()
        interactive = false
    }

    override fun handleOnBackPressed() {
        // Also the only entry before API 34, where no progress is delivered.
        interactive = false
        commit()
    }
}
