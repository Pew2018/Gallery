package org.fossify.gallery.helpers

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DirectoryGridItemAnimatorTest {
    @Test fun backgroundRefreshMovesAreSuppressedUnlessTheyComeFromUserReordering() {
        val animator = DirectoryGridItemAnimator()

        assertFalse(animator.supportsChangeAnimations)
        assertFalse(animator.consumeUserReorderMove(10L))

        animator.allowUserReorderMoves(listOf(10L, 11L))
        assertTrue(animator.consumeUserReorderMove(10L))
        assertFalse(animator.consumeUserReorderMove(10L))
        assertTrue(animator.consumeUserReorderMove(11L))
        assertFalse(animator.consumeUserReorderMove(12L))
    }

    @Test fun aNewDirectorySnapshotInvalidatesUnusedReorderAnimationPermission() {
        val animator = DirectoryGridItemAnimator()
        animator.allowUserReorderMoves(listOf(10L))

        animator.clearUserReorderMoves()

        assertFalse(animator.consumeUserReorderMove(10L))
    }
}
