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
    @Test fun readyCurrentVideoCanStartPlaybackAfterDeferredInitialization() {
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
                listener = object : org.fossify.gallery.fragments.ViewPagerFragment.FragmentListener {
                    override fun fragmentClicked() {}
                    override fun videoEnded() = true
                    override fun goToPrevItem() {}
                    override fun goToNextItem() {}
                    override fun launchViewVideoIntent(path: String) {}
                    override fun isSlideShowActive() = true
                    override fun isFullScreen() = false
                }
            }
            activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
            assertFalse(fragment.playSlideshowVideo("/pictures/video.mp4"))
            // The native decoder is outside Robolectric; observe the actual Player command.
            var playbackRequested = false
            val player = java.lang.reflect.Proxy.newProxyInstance(
                androidx.media3.exoplayer.ExoPlayer::class.java.classLoader,
                arrayOf(androidx.media3.exoplayer.ExoPlayer::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "setPlayWhenReady" -> { playbackRequested = args?.first() == true; null }
                    "getCurrentPosition", "getDuration" -> 0L
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        java.lang.Float.TYPE -> 0f
                        java.lang.Double.TYPE -> 0.0
                        else -> null
                    }
                }
            }
            fun set(name: String, value: Any) {
                VideoFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
            }
            set("mExoPlayer", player)
            set("mWasFragmentInit", true)
            set("mWasVideoStarted", true)
            set("mWasLastPositionRestored", true)
            assertFalse(fragment.playSlideshowVideo("/pictures/stale.mp4"))
            assertFalse(playbackRequested)
            assertTrue(fragment.playSlideshowVideo("/pictures/video.mp4"))
            assertTrue(playbackRequested)
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            assertFalse(fragment.playSlideshowVideo("/pictures/video.mp4"))
        } finally { controller.pause().stop().destroy() }
    }

}
