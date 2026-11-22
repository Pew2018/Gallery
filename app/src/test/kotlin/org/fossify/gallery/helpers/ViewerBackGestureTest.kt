package org.fossify.gallery.helpers

import androidx.activity.BackEventCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the callback's ownership and single-responsibility contract. Robolectric
 * drives a synthetic BackEventCompat; it does not prove any system-composited frame.
 * Visual gesture behaviour stays PENDING_DEVICE_VALIDATION.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ViewerBackGestureTest {

    private class Recorder {
        var edges = mutableListOf<Int>()
        var progress = mutableListOf<Float>()
        var cancels = 0
        var commits = 0
    }

    private fun gesture(recorder: Recorder, beginResult: Boolean = true): ViewerBackGesture =
        ViewerBackGesture(
            beginInteractive = { edge ->
                recorder.edges += edge
                beginResult
            },
            renderProgress = { recorder.progress += it },
            cancelInteractive = { recorder.cancels++ },
            commit = { recorder.commits++ },
        )

    @Test
    fun callbackStartsDisabledSoPlatformOwnsBack() {
        assertFalse("must not swallow back by default", gesture(Recorder()).isEnabled)
    }

    @Test
    fun disabledCallbackIgnoresNothingBecauseDispatcherSkipsIt() {
        // The dispatcher never calls a disabled callback. Enabled state is the only
        // ownership switch; this test documents that no hidden side effects run.
        val recorder = Recorder()
        val callback = gesture(recorder)
        assertFalse(callback.isEnabled)
        callback.isEnabled = true
        assertTrue(callback.isEnabled)
        callback.isEnabled = false
        assertEquals(0, recorder.commits)
    }

    @Test
    fun swipeEdgeIsForwardedFromTheOfficialEvent() {
        val recorder = Recorder()
        val callback = gesture(recorder).also { it.isEnabled = true }
        callback.handleOnBackStarted(
            BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)
        )
        assertEquals(listOf(BackEventCompat.EDGE_LEFT), recorder.edges)
    }

    @Test
    fun progressIsClampedAndForwarded() {
        val recorder = Recorder()
        val callback = gesture(recorder).also { it.isEnabled = true }
        callback.handleOnBackStarted(
            BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_RIGHT)
        )
        callback.handleOnBackProgressed(
            BackEventCompat(0f, 0f, 1.7f, BackEventCompat.EDGE_RIGHT)
        )
        assertEquals(listOf(1.0f), recorder.progress)
    }

    @Test
    fun cancelDoesNotCommit() {
        val recorder = Recorder()
        val callback = gesture(recorder).also { it.isEnabled = true }
        callback.handleOnBackStarted(
            BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)
        )
        callback.handleOnBackProgressed(
            BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT)
        )
        callback.handleOnBackCancelled()
        assertEquals(1, recorder.cancels)
        assertEquals(0, recorder.commits)
    }

    @Test
    fun pressedCommitsExactlyOnceEvenWithoutProgress() {
        // V03/V04: a completed back publishes one result; cancel never does.
        val recorder = Recorder()
        val callback = gesture(recorder).also { it.isEnabled = true }
        callback.handleOnBackPressed()
        assertEquals(1, recorder.commits)
        assertEquals(0, recorder.cancels)
    }

    @Test
    fun failedInteractiveBeginKeepsCallbackIdleOnProgress() {
        val recorder = Recorder()
        val callback = gesture(recorder, beginResult = false).also { it.isEnabled = true }
        callback.handleOnBackStarted(
            BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)
        )
        callback.handleOnBackProgressed(
            BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)
        )
        assertTrue(recorder.progress.isEmpty())
    }
}
