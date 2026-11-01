package org.fossify.gallery.helpers

import android.app.Application
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SlideshowVideoReadinessTest {
    @Test fun pendingFragmentCannotStartPlaybackOrDereferenceUninitializedViews() {
        val fragment = VideoFragment()
        assertFalse(fragment.playSlideshowVideo("/pictures/video.mp4"))
    }

    @Test fun resumedButDeferredPageAndDestroyedViewCannotStartPlayback() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val container = FrameLayout(activity).apply { id = View.generateViewId() }
            activity.setContentView(container)
            val fragment = VideoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(MEDIUM, Medium().apply { path = "/pictures/video.mp4"; type = TYPE_VIDEOS })
                    putBoolean(SHOULD_INIT_FRAGMENT, false)
                }
            }
            activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
            assertTrue(fragment.isResumed)
            assertFalse(fragment.playSlideshowVideo("/pictures/video.mp4"))
            assertFalse(fragment.playSlideshowVideo("/pictures/another.mp4"))
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            assertFalse(fragment.playSlideshowVideo("/pictures/video.mp4"))
        } finally { controller.pause().stop().destroy() }
    }
}
