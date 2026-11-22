package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only contract for the Viewer back-ownership split. This proves which code
 * path is configured; it does NOT and cannot prove how the system compositor draws
 * the gesture. See docs/animation/predictive-back-root-cause/05-test-results.md.
 */
class ViewerBackOwnershipTest {
    private val viewing = MediaTransitionSessionCoordinator.State.VIEWING
    private val interactive = MediaTransitionSessionCoordinator.State.INTERACTIVE_RETURN
    private val entering = MediaTransitionSessionCoordinator.State.ENTERING
    private val returning = MediaTransitionSessionCoordinator.State.RETURNING

    @Test
    fun android13StablePageIsAppOwned() {
        // V15: no system progress animation below 14; the app must still handle back.
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(33, viewing, false, windowEligible = true))
        assertFalse(ViewerBackOwnership.platformOwnsEdgeBack(33, viewing, false, windowEligible = true))
    }

    @Test
    fun stableEligiblePageCanBePlatformOwned() {
        // An opaque, non-scene-transition window on 14+ hands the whole-window
        // return to the system. Eligibility is necessary, not sufficient: the
        // server still validates the activity record at gesture time.
        assertTrue(ViewerBackOwnership.platformOwnsEdgeBack(34, viewing, false, windowEligible = true))
        assertTrue(ViewerBackOwnership.platformOwnsEdgeBack(36, viewing, false, windowEligible = true))
        assertFalse(ViewerBackOwnership.appOwnsEdgeBack(36, viewing, false, windowEligible = true))
    }

    @Test
    fun ineligibleWindowNeverClaimsPlatformOwnership() {
        // A translucent or scene-transition-launched record downgrades BackNavigation
        // to TYPE_CALLBACK. The decision object must reflect that the platform does
        // not own the visual, even on a stable Android 14+ page.
        assertFalse(ViewerBackOwnership.platformOwnsEdgeBack(34, viewing, false, windowEligible = false))
        assertFalse(ViewerBackOwnership.platformOwnsEdgeBack(36, viewing, false, windowEligible = false))
    }

    @Test
    fun inAppStepKeepsAppOwnershipOnAndroid16() {
        // An entry, an interactive dismiss, a drag in flight, or a committed return
        // must finish inside the app before the window is released.
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, entering, false, windowEligible = true))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, interactive, false, windowEligible = true))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, viewing, true, windowEligible = true))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, returning, false, windowEligible = true))
    }

    @Test
    fun dragGestureIsAnInAppStepRegardlessOfSdk() {
        assertTrue(ViewerBackOwnership.hasInAppStep(viewing, true))
        assertFalse(ViewerBackOwnership.hasInAppStep(viewing, false))
        assertTrue(ViewerBackOwnership.hasInAppStep(interactive, false))
    }

    @Test
    fun callbackArmingIgnoresWindowEligibility() {
        // Arming answers "must the app consume this back itself". A stable page on
        // 14+ never arms, even when the window is ineligible - otherwise the
        // app-side image scaling the user rejected would replace a plain system
        // finish. Below 14 every page arms because no system preview exists.
        assertFalse(ViewerBackOwnership.armAppBackCallback(36, viewing, dragActive = false))
        assertFalse(ViewerBackOwnership.armAppBackCallback(36, viewing, dragActive = false))
        assertTrue(ViewerBackOwnership.armAppBackCallback(36, entering, dragActive = false))
        assertTrue(ViewerBackOwnership.armAppBackCallback(36, interactive, dragActive = false))
        assertTrue(ViewerBackOwnership.armAppBackCallback(36, viewing, dragActive = true))
        assertTrue(ViewerBackOwnership.armAppBackCallback(36, returning, dragActive = false))
        assertTrue(ViewerBackOwnership.armAppBackCallback(33, viewing, dragActive = false))
        assertTrue(ViewerBackOwnership.armAppBackCallback(28, viewing, dragActive = false))
    }

    @Test
    fun systemReleaseSkipsTheAppVisualEvenOnIneligibleWindows() {
        // The commit path must not install the app shared-element return when the
        // app has no in-app step; the platform may still downgrade the animation.
        assertTrue(ViewerBackOwnership.releaseWithoutAppVisual(36, viewing, dragActive = false))
        assertFalse(ViewerBackOwnership.releaseWithoutAppVisual(33, viewing, dragActive = false))
        assertFalse(ViewerBackOwnership.releaseWithoutAppVisual(36, entering, dragActive = false))
        assertFalse(ViewerBackOwnership.releaseWithoutAppVisual(36, viewing, dragActive = true))
        assertFalse(ViewerBackOwnership.releaseWithoutAppVisual(36, returning, dragActive = false))
    }

    @Test
    fun ownershipIsExactlyOneOwner() {
        for (sdk in listOf(28, 33, 34, 35, 36)) {
            for (state in MediaTransitionSessionCoordinator.State.entries) {
                for (drag in listOf(false, true)) {
                    for (eligible in listOf(false, true)) {
                        val platform = ViewerBackOwnership.platformOwnsEdgeBack(sdk, state, drag, eligible)
                        val app = ViewerBackOwnership.appOwnsEdgeBack(sdk, state, drag, eligible)
                        assertTrue(
                            "exactly one owner sdk=$sdk state=$state drag=$drag eligible=$eligible",
                            platform xor app,
                        )
                    }
                }
            }
        }
    }
}
