package org.fossify.gallery.adapters

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MyPagerAdapterLifecycleTest {
    private class Host : FragmentActivity(), ViewerPagerHost {
        override fun mayLoadFavoritePath(path: String) = true
        override fun fragmentClicked() {}
        override fun videoEnded() = true
        override fun goToPrevItem() {}
        override fun goToNextItem() {}
        override fun launchViewVideoIntent(path: String) {}
        override fun isSlideShowActive() = false
        override fun isFullScreen() = false
    }

    private class ProbeFragment : ViewPagerFragment() {
        var listenerReadyAtViewCreation = false
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
            listenerReadyAtViewCreation = listener != null
            return FrameLayout(requireContext())
        }
        override fun fullscreenToggled(isFullscreen: Boolean) {}
    }

    private fun medium(modified: Long, name: String = "a.jpg") =
        Medium(null, name, "/gallery/$name", "/gallery", modified, 20L, 100L, 1, 0, false, 0L, 0L)

    @Test fun listenerIsBoundBeforeSynchronousViewCreationAndMetadataUpdateReusesFragment() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        try {
            val host = controller.get()
            val container = FrameLayout(host).apply { id = View.generateViewId() }
            host.setContentView(container)
            val adapter = MyPagerAdapter(host, host.supportFragmentManager, listOf(medium(10L)), fragmentFactory = { ProbeFragment() })

            val first = adapter.instantiateItem(container, 0) as ProbeFragment
            assertTrue(first.listenerReadyAtViewCreation)
            assertSame(host, first.listener)

            val updated = medium(10L).apply { id = 81L; taken = 123L; isFavorite = true }
            adapter.updateMedia(listOf(updated))
            assertEquals(0, adapter.getItemPosition(first))
            val reused = adapter.instantiateItem(container, 0)
            assertSame(first, reused)
            val merged = first.arguments?.getSerializable(MEDIUM) as Medium
            assertEquals(81L, merged.id)
            assertEquals(123L, merged.taken)
            assertTrue(merged.isFavorite)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun knownContentChangeInvalidatesAndReplacesTheOldFragment() {
        val controller = Robolectric.buildActivity(Host::class.java).setup()
        try {
            val host = controller.get()
            val container = FrameLayout(host).apply { id = View.generateViewId() }
            host.setContentView(container)
            val adapter = MyPagerAdapter(host, host.supportFragmentManager, listOf(medium(10L)), fragmentFactory = { ProbeFragment() })
            val old = adapter.instantiateItem(container, 0) as ProbeFragment

            adapter.updateMedia(listOf(medium(11L)))
            assertEquals(androidx.viewpager.widget.PagerAdapter.POSITION_NONE, adapter.getItemPosition(old))
            adapter.destroyItem(container, 0, old)
            val fresh = adapter.instantiateItem(container, 0)
            assertNotSame(old, fresh)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
