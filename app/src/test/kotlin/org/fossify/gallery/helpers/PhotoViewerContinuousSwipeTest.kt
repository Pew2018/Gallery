package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.adapters.ViewerPagerHost
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises a real FragmentManager/PagerAdapter at positions far outside the
 * original five-item handoff. It does not decode photos or start MediaActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoViewerContinuousSwipeTest {
    @Test fun realAdapterKeepsFullCountAndCanInstantiateBothDirectionsFromMiddle() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val holder = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(holder)
        val photos = (0 until 5_000).map {
            Medium(null, "$it.jpg", "/Gallery/$it.jpg", "/Gallery",
                it.toLong(), it.toLong(), 1L, TYPE_IMAGES, 0, false, 0L, 0L)
        }
        val permissions = PhotoViewerPageAccessState(capacity = 16) { 500L }
        permissions.reset(7L)
        val host = object : ViewerPagerHost {
            override fun mayLoadFavoritePath(path: String) = permissions.contains(path, 7L)
            override fun fragmentClicked() = Unit
            override fun videoEnded() = true
            override fun goToPrevItem() = Unit
            override fun goToNextItem() = Unit
            override fun launchViewVideoIntent(path: String) = Unit
            override fun isSlideShowActive() = false
            override fun isFullScreen() = false
        }
        val adapter = MyPagerAdapter(host, activity.supportFragmentManager, photos,
            fragmentFactory = { PhotoViewerTestFragment() })
        assertEquals(5_000, adapter.count)

        // Each page is released as the viewport advances. No full-list requery
        // and no new bitmap/fragment for the other 4,999 entries are required.
        val steps = (2_500..2_520).toList() + (2_519 downTo 2_480).toList()
        steps.forEach { position ->
            permissions.approve((position - 2..position + 2)
                .filter { it in photos.indices }.map { photos[it].path }, 7L)
            val fragment = adapter.instantiateItem(holder, position)
            assertTrue(fragment is ViewPagerFragment)
            assertEquals(5_000, adapter.count)
            adapter.destroyItem(holder, position, fragment)
            assertTrue(permissions.size() <= 16)
        }
        activity.finish()
    }
}

/** FragmentManager can recreate this public top-level test page. */
class PhotoViewerTestFragment : ViewPagerFragment() {
    override fun fullscreenToggled(isFullscreen: Boolean) = Unit
}
