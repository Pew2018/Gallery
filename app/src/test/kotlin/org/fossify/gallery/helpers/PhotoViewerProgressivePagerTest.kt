package org.fossify.gallery.helpers

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.adapters.ViewerPagerHost
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.ProgressivePhotoViewPager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual pager/adapter updates and synthetic edge drag; no bitmap decoding or production Activity authentication. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoViewerProgressivePagerTest {
    class Page : ViewPagerFragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = FrameLayout(requireContext())
        override fun fullscreenToggled(isFullscreen: Boolean) = Unit
    }

    @Test fun folderFiveThenPartialThenCompleteContinuesBothDirections() = exerciseProgressiveSource(ViewerSourceType.FOLDER)
    @Test fun favoritesBeyondCacheCapacityContinueBothDirections() = exerciseProgressiveSource(ViewerSourceType.FAVORITES)
    @Test fun photoProfileRetainsProgressiveNavigation() = exerciseProgressiveSource(ViewerSourceType.PHOTO_PROFILE)

    private fun exerciseProgressiveSource(source: ViewerSourceType) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val pager = ProgressivePhotoViewPager(activity, Robolectric.buildAttributeSet().build()).apply { id = View.generateViewId() }
            activity.setContentView(pager)
            val count = if (source == ViewerSourceType.FAVORITES) 13001 else 5000
            val all = (0 until count).map { Medium(null, "$it.jpg", "/Gallery/$it.jpg", "/Gallery",
                it.toLong(), it.toLong(), 1L, TYPE_IMAGES, 0, false, 0L, 0L) }
            var published = all.subList(2498, 2503)
            var livePath = all[2502].path
            val navigation = PhotoViewerCatalogCoordinator().apply { publish(false) }
            val host = object : ViewerPagerHost {
                override fun mayLoadFavoritePath(path: String) = true
                override fun fragmentClicked() = Unit
                override fun videoEnded() = true
                override fun goToPrevItem() = Unit
                override fun goToNextItem() = Unit
                override fun launchViewVideoIntent(path: String) = Unit
                override fun isSlideShowActive() = false
                override fun isFullScreen() = false
            }
            val adapter = MyPagerAdapter(host, activity.supportFragmentManager, published, fragmentFactory = { Page() })
            pager.adapter = adapter
            pager.setCurrentItem(4, false)
            pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) { livePath = published[position].path }
            })
            pager.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            pager.layout(0, 0, 1080, 1920)
            var requests = 0
            pager.onUnknownBoundary = { forward -> requests++; navigation.request(livePath, forward) }
            fun drag(from: Float, to: Float) {
                listOf(MotionEvent.ACTION_DOWN to from, MotionEvent.ACTION_MOVE to ((from + to) / 2), MotionEvent.ACTION_UP to to)
                    .forEachIndexed { index, (action, x) ->
                        val event = MotionEvent.obtain(100L, 100L + index * 100L, action, x, 800f, 0)
                        pager.dispatchTouchEvent(event)
                        event.recycle()
                    }
            }
            drag(800f, 200f)
            assertEquals(1, requests)
            assertTrue(navigation.pending!!.forward)
            assertEquals(5, adapter.count)
            assertNull(navigation.destination(published.map { it.path }, livePath))

            fun publish(items: List<Medium>, complete: Boolean) {
                val started = System.nanoTime()
                val anchor = livePath
                published = items
                navigation.publish(complete)
                pager.removeOnPageChangeListenerForTest()
                adapter.updateMedia(items)
                val retained = items.indexOfFirst { it.path == anchor }
                pager.setCurrentItem(retained, false)
                livePath = anchor
                val target = navigation.destination(items.map { it.path }, livePath)
                if (target != null) { pager.setCurrentItem(target, false); livePath = items[target].path }
                println("Viewer simulated publication source=$source count=${items.size} ms=${(System.nanoTime() - started) / 1_000_000.0}")
            }
            publish(all.subList(2440, 2560), false)
            assertEquals(all[2503].path, livePath)
            assertEquals(120, adapter.count)
            repeat(20) { pager.setCurrentItem(pager.currentItem + 1, false) }
            livePath = published[pager.currentItem].path
            val beforeComplete = livePath
            publish(all, true)
            assertEquals(beforeComplete, livePath)
            assertEquals(count, adapter.count)
            repeat(20) { pager.setCurrentItem(pager.currentItem - 1, false) }
            assertEquals(all[2503].path, published[pager.currentItem].path)
            assertFalse(navigation.request(all.last().path, true))
        } finally { controller.pause().stop().destroy() }
    }

    // Avoid fake Activity state: the test restores the real path explicitly during publication,
    // just as ViewPagerActivity removes its listener while replacing the backing catalog.
    private fun ViewPager.removeOnPageChangeListenerForTest() { clearOnPageChangeListeners() }
}
