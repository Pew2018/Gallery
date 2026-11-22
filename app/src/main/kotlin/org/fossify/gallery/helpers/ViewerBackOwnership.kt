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
 * Two independent facts must be kept apart:
 *
 *  - "the app does not consume the back" — [hasInAppStep]; and
 *  - "the system will actually run a cross-Activity animation" — [platformOwnsEdgeBack].
 *
 * The second additionally requires the window to qualify: BackNavigationController
 * drops to TYPE_CALLBACK when the top activity does not occlude its parent
 * (translucent/floating window) or was launched with a scene transition
 * (ActivityRecord.mHasSceneTransition is final and set from the launch options).
 * The Viewer now satisfies both: its theme is opaque and on Android 14+ the grid
 * starts it without a scene-transition bundle, driving the photo entry itself.
 * Passing [windowEligible] is still a *necessary* condition, not proof the
 * animation ran — that stays a device-level validation.
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

    /**
     * True only when the platform is *eligible* to run the whole-window return:
     * new enough platform, stable page, no drag in flight, and a window the
     * WindowManager considers occluding. Callers must not log this as "the
     * system animated" — eligibility does not prove the BackNavigation type the
     * server finally selected.
     */
    fun platformOwnsEdgeBack(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
        windowEligible: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && windowEligible &&
        !hasInAppStep(state, dragActive)

    fun appOwnsEdgeBack(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
        windowEligible: Boolean,
    ): Boolean = !platformOwnsEdgeBack(sdkInt, state, dragActive, windowEligible)

    /**
     * Whether a back commit runs the app-side return visual. This is true only
     * when there is no in-app step and the platform *may* take over; it stays
     * true even when the window is ineligible, because the one thing the app must
     * never do is stack its own shared-element return on top of a transition the
     * platform is already playing (or a plain finish).
     */
    fun releaseWithoutAppVisual(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasInAppStep(state, dragActive)

    /**
     * Whether the app arms its back callback. This is intentionally *not* the
     * negation of [platformOwnsEdgeBack]: on an ineligible window (translucent
     * theme, scene-transition launch) a stable page must still NOT consume the
     * gesture, or the app image-scaling preview the user rejected would return.
     * The callback is armed only where the app truly has an in-app step, plus
     * every pre-14 platform where no system preview exists at all.
     */
    fun armAppBackCallback(
        sdkInt: Int,
        state: MediaTransitionSessionCoordinator.State,
        dragActive: Boolean,
    ): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || hasInAppStep(state, dragActive)
}
