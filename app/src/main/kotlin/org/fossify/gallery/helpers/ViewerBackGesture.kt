package org.fossify.gallery.helpers

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback

/**
 * Routes system and predictive back into the Viewer's existing Session return core.
 *
 * The callback has the default priority, so the platform does not animate the same
 * window: the Viewer's own interactive envelope and shared-element return stay the
 * only visual drivers. On platforms that do not deliver gesture progress the same
 * callback only receives the final back and closes through the unchanged path.
 *
 * The gesture start X is forwarded so the return can slide toward the swiped edge
 * instead of always shrinking in place: a left-edge back reveals the grid from the
 * left, a right-edge back from the right.
 */
internal class ViewerBackGesture(
    private val beginInteractive: (Float) -> Boolean,
    private val renderProgress: (Float) -> Unit,
    private val cancelInteractive: () -> Unit,
    private val commit: () -> Unit,
) : OnBackPressedCallback(true) {
    private var interactive = false

    /** True while a gesture drives the existing interactive return surface. */
    val ownsInteractiveReturn get() = interactive

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        interactive = beginInteractive(backEvent.touchX)
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
