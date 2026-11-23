package org.fossify.gallery.helpers

import androidx.activity.BackEventCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The predictive-back edge is a visual hint for the Viewer, not a second exit path.
 * The callback must forward a stable signed direction together with the clamped
 * progress, and must not report progress after a cancel or commit.
 */
class ViewerBackGestureTest {
    private class Recorder {
        var begins = mutableListOf<Float>()
        var frames = mutableListOf<Pair<Float, Float>>()
        var cancels = 0
        var commits = 0
        val gesture = ViewerBackGesture(
            beginInteractive = { sign -> begins += sign; true },
            renderProgress = { progress, sign -> frames += progress to sign },
            cancelInteractive = { cancels++ },
            commit = { commits++ },
        )
    }

    @Test
    fun leftEdgeIsForwardAndRightEdgeIsBackward() {
        val left = Recorder()
        left.gesture.handleOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        assertEquals(listOf(1f), left.begins)
        left.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        assertEquals(listOf(0.5f to 1f), left.frames)

        val right = Recorder()
        right.gesture.handleOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT))
        assertEquals(listOf(-1f), right.begins)
        right.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 0.25f, BackEventCompat.EDGE_RIGHT))
        assertEquals(listOf(0.25f to -1f), right.frames)
    }

    @Test
    fun progressIsClampedAndIgnoredBeforeTheGestureBegins() {
        val recorder = Recorder()
        recorder.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        assertTrue("no gesture in progress", recorder.frames.isEmpty())
        recorder.gesture.handleOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        recorder.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, -2f, BackEventCompat.EDGE_LEFT))
        recorder.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 4f, BackEventCompat.EDGE_LEFT))
        assertEquals(listOf(0f to 1f, 1f to 1f), recorder.frames)
    }

    @Test
    fun cancelledGestureStopsRenderingAndNeverCommits() {
        val recorder = Recorder()
        recorder.gesture.handleOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        recorder.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 0.4f, BackEventCompat.EDGE_LEFT))
        recorder.gesture.handleOnBackCancelled()
        recorder.gesture.handleOnBackProgressed(BackEventCompat(0f, 0f, 0.8f, BackEventCompat.EDGE_LEFT))
        assertEquals(1, recorder.cancels)
        assertEquals(0, recorder.commits)
        assertEquals(1, recorder.frames.size)
        assertFalse(recorder.gesture.ownsInteractiveReturn)
    }

    @Test
    fun commitReleasesTheInteractiveOwnership() {
        val recorder = Recorder()
        recorder.gesture.handleOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT))
        assertTrue(recorder.gesture.ownsInteractiveReturn)
        recorder.gesture.handleOnBackPressed()
        assertEquals(1, recorder.commits)
        assertFalse(recorder.gesture.ownsInteractiveReturn)
    }
}
