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
 * [BackEventCompat.swipeEdge] is forwarded as a signed direction (+1 left edge,
 * -1 right edge, 0 unknown) so the Viewer can add a small, continuous side bias while
 * the finger is still down. The bias is a visual hint only: it starts at zero progress
 * and never becomes a second exit path.
 */
internal class ViewerBackGesture(
    private val beginInteractive: (edgeSign: Float) -> Boolean,
    private val renderProgress: (progress: Float, edgeSign: Float) -> Unit,
    private val cancelInteractive: () -> Unit,
    private val commit: () -> Unit,
) : OnBackPressedCallback(true) {
    private var interactive = false
    private var edgeSign = 0f

    /** True while a gesture drives the existing interactive return surface. */
    val ownsInteractiveReturn get() = interactive

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        edgeSign = edgeSign(backEvent.swipeEdge)
        interactive = beginInteractive(edgeSign)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (!interactive) return
        // Some platform versions refine the edge after the start callback.
        edgeSign = edgeSign(backEvent.swipeEdge)
        renderProgress(backEvent.progress.coerceIn(0f, 1f), edgeSign)
    }

    override fun handleOnBackCancelled() {
        if (interactive) cancelInteractive()
        interactive = false
        edgeSign = 0f
    }

    override fun handleOnBackPressed() {
        // Also the only entry before API 34, where no progress is delivered.
        interactive = false
        edgeSign = 0f
        commit()
    }

    private fun edgeSign(swipeEdge: Int): Float = when (swipeEdge) {
        BackEventCompat.EDGE_RIGHT -> -1f
        BackEventCompat.EDGE_LEFT -> 1f
        else -> 0f
    }
}
