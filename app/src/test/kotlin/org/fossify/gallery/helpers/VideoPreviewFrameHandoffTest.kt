package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VideoPreviewFrameHandoffTest {
    @Test fun firstDecodedFrameCannotReplaceCoverDuringSharedMotion() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val cover = FrameLayout(controller.get())
            val frames = ArrayDeque<Runnable>()
            var visibleFrames = 0
            val handoff = VideoPreviewFrameHandoff(cover, onFrameVisible = { visibleFrames++ }) { frames.addLast(it) }
            val player = Any()
            handoff.setTransitionRunning(true)
            handoff.bind(player)
            handoff.onRenderedFirstFrame(player)
            assertEquals(View.VISIBLE, cover.visibility)
            assertEquals(0, visibleFrames)
            handoff.setTransitionRunning(false)
            assertEquals(View.VISIBLE, cover.visibility)
            frames.removeFirst().run()
            assertEquals(View.VISIBLE, cover.visibility)
            frames.removeFirst().run()
            assertEquals(View.GONE, cover.visibility)
            assertEquals(1, visibleFrames)
            handoff.setTransitionRunning(true)
            assertEquals(View.VISIBLE, cover.visibility)
            handoff.keepCoverForError(player)
            handoff.setTransitionRunning(false)
            assertEquals(View.VISIBLE, cover.visibility)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun coverRemainsUntilActivePlayerFrameAndIgnoresStalePlayerCallbacks() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val cover = FrameLayout(controller.get())
            val frames = ArrayDeque<Runnable>()
            val handoff = VideoPreviewFrameHandoff(cover) { frames.addLast(it) }
            val previousPlayer = Any()
            val activePlayer = Any()

            handoff.bind(previousPlayer)
            assertEquals(View.VISIBLE, cover.visibility)
            handoff.bind(activePlayer)
            handoff.onRenderedFirstFrame(previousPlayer)
            assertEquals(View.VISIBLE, cover.visibility)

            handoff.keepCoverForError(activePlayer)
            assertEquals(View.VISIBLE, cover.visibility)
            handoff.onRenderedFirstFrame(activePlayer)
            assertEquals(View.VISIBLE, cover.visibility)
            frames.removeFirst().run()
            assertEquals(View.VISIBLE, cover.visibility)
            frames.removeFirst().run()
            assertEquals(View.GONE, cover.visibility)

            handoff.keepCoverForError(previousPlayer)
            assertEquals(View.GONE, cover.visibility)
            handoff.clear()
            handoff.onRenderedFirstFrame(activePlayer)
            assertEquals(View.GONE, cover.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun errorAndViewDestructionInvalidateAlreadyQueuedDisplayFrames() {
        val cover = View(org.robolectric.RuntimeEnvironment.getApplication())
        val frames = ArrayDeque<Runnable>()
        val handoff = VideoPreviewFrameHandoff(cover) { frames.addLast(it) }
        val player = Any()
        handoff.bind(player)
        handoff.onRenderedFirstFrame(player)
        frames.removeFirst().run()

        handoff.keepCoverForError(player)
        frames.removeFirst().run()
        assertEquals(View.VISIBLE, cover.visibility)

        handoff.onRenderedFirstFrame(player)
        frames.removeFirst().run()
        handoff.clear()
        frames.removeFirst().run()
        assertEquals(View.VISIBLE, cover.visibility)
    }

}
