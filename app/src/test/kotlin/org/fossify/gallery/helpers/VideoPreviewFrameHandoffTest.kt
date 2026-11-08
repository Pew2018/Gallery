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
    @Test fun coverRemainsUntilActivePlayerFrameAndIgnoresStalePlayerCallbacks() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val cover = FrameLayout(controller.get())
            val handoff = VideoPreviewFrameHandoff(cover)
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
}
