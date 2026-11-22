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
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(33, viewing, false))
        assertFalse(ViewerBackOwnership.platformOwnsEdgeBack(33, viewing, false))
    }

    @Test
    fun android14StablePageIsPlatformOwned() {
        // V01/V02: a stable Viewer page hands the whole-window return to the system.
        assertTrue(ViewerBackOwnership.platformOwnsEdgeBack(34, viewing, false))
        assertTrue(ViewerBackOwnership.platformOwnsEdgeBack(36, viewing, false))
        assertFalse(ViewerBackOwnership.appOwnsEdgeBack(36, viewing, false))
    }

    @Test
    fun inAppStepKeepsAppOwnershipOnAndroid16() {
        // An entry, an interactive dismiss, a drag in flight, or a committed return
        // must finish inside the app before the window is released.
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, entering, false))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, interactive, false))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, viewing, true))
        assertTrue(ViewerBackOwnership.appOwnsEdgeBack(36, returning, false))
    }

    @Test
    fun dragGestureIsAnInAppStepRegardlessOfSdk() {
        assertTrue(ViewerBackOwnership.hasInAppStep(viewing, true))
        assertFalse(ViewerBackOwnership.hasInAppStep(viewing, false))
        assertTrue(ViewerBackOwnership.hasInAppStep(interactive, false))
    }

    @Test
    fun ownershipIsExactlyOneOwner() {
        for (sdk in listOf(28, 33, 34, 35, 36)) {
            for (state in MediaTransitionSessionCoordinator.State.entries) {
                for (drag in listOf(false, true)) {
                    val platform = ViewerBackOwnership.platformOwnsEdgeBack(sdk, state, drag)
                    val app = ViewerBackOwnership.appOwnsEdgeBack(sdk, state, drag)
                    assertTrue("exactly one owner sdk=$sdk state=$state drag=$drag", platform xor app)
                }
            }
        }
    }
}
