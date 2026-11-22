package org.fossify.gallery.helpers

import android.os.Build

/**
 * Decides who owns the visual return of a full-screen Viewer edge-back.
 *
 * From Android 14 (API 34) the platform can animate a cross-Activity back: it
 * scales the *whole* outgoing window and reveals the real parent Activity behind
 * it. It only does so when no default-priority back callback consumes the event.
 * An enabled [androidx.activity.OnBackPressedCallback] therefore takes the gesture
 * away from the platform and leaves the app to draw its own preview.
 *
 * Gallery's own image scaling is still required in two cases, and only in those
 * cases may the app callback stay enabled:
 *  - Android 13 and below, where the platform has no cross-Activity preview; and
 *  - a genuine in-app step inside the Viewer (entry still running, an interactive
 *    dismiss in flight, or a return already committed). Those need the app to
 *    reach a stable frame before the window is released.
 *
 * Dragging the photo down is a touch gesture, not a system back; it is owned by
 * the media-dismiss controller and never consulted here.
 *
 * This is a pure decision object so the ownership split can be verified by JVM
 * tests. It deliberately makes no claim about how the compositor draws the
 * result; that remains a device-level validation.
 */
internal object ViewerBackOwnership {
    /**
     * True while the Viewer is doing something the app must finish for itself.
     * Only a stable VIEWING page has no in-app step and can be handed to the
     * platform.
     */
    fun hasInAppStep(state: MediaTransitionSessionCoordinator.State, dragActive: Boolean): Boolean =
        state != MediaTransitionSessionCoordinator.State.VIEWING || dragActive

    fun platformOwnsEdgeBack(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasInAppStep(state, dragActive)

    fun appOwnsEdgeBack(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
    ): Boolean = !platformOwnsEdgeBack(sdkInt, state, dragActive)
}
