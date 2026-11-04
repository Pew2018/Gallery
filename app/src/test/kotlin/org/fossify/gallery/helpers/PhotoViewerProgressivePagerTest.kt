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

    @Test fun partialStageWithStaleAnchorCannotReplaceTheLiveFragmentByIndex() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val pager = ProgressivePhotoViewPager(activity, Robolectric.buildAttributeSet().build()).apply { id = View.generateViewId() }
            activity.setContentView(pager)
            val grid = listOf("a", "b", "c", "d", "e").map { path ->
                Medium(null, path, "/Gallery/$path", "/Gallery", 1L, 1L, 1L, TYPE_IMAGES, 0, false, 0L, 0L)
            }
            var published = grid.subList(1, 4)
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
            pager.setCurrentItem(1, false) // C
            pager.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            pager.layout(0, 0, 1080, 1920)
            pager.setCurrentItem(2, false) // user has moved to D
            val live = adapter.getCurrentFragment(2) ?: throw AssertionError("D fragment should be instantiated")
            assertEquals("/Gallery/d", (live.arguments?.getSerializable(MEDIUM) as Medium).path)
            val livePath = (live.arguments?.getSerializable(MEDIUM) as Medium).path

            // The async stage captured C and no longer contains D. Production
            // publication guards keep the old adapter until a safe stage arrives.
            val partial = ViewerGridOrderPolicy.contiguousPartial(
                listOf("/Gallery/a", "/Gallery/b", "/Gallery/c", "/Gallery/d", "/Gallery/e"),
                listOf("/Gallery/a", "/Gallery/b", "/Gallery/c", "/Gallery/e"),
                "/Gallery/c") { it }
            if (partial.isNotEmpty() && partial.any { it == livePath }) {
                published = partial.map { path -> grid.first { it.path == path } }
                adapter.updateMedia(published)
            }

            assertEquals("/Gallery/d", (adapter.getCurrentFragment(2)?.arguments?.getSerializable(MEDIUM) as Medium).path)
            assertEquals(listOf("/Gallery/b", "/Gallery/c", "/Gallery/d"), adapter.media.map { it.path })
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun deleteDuringSingleItemProgressiveWindowWaitsForFreshAuthorizedNeighbor() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val pager = ProgressivePhotoViewPager(activity, Robolectric.buildAttributeSet().build()).apply {
                id = View.generateViewId()
            }
            activity.setContentView(pager)
            val ordered = listOf("A", "B", "C", "D").map { path ->
                Medium(null, "$path.jpg", "/Gallery/$path.jpg", "/Gallery", 1L, 1L, 1L,
                    TYPE_IMAGES, 0, false, 0L, 0L)
            }
            val clicked = ordered[2]
            val allowed = linkedSetOf(clicked.path)
            val host = object : ViewerPagerHost {
                override fun mayLoadFavoritePath(path: String) = path in allowed
                override fun fragmentClicked() = Unit
                override fun videoEnded() = true
                override fun goToPrevItem() = Unit
                override fun goToNextItem() = Unit
                override fun launchViewVideoIntent(path: String) = Unit
                override fun isSlideShowActive() = false
                override fun isFullScreen() = false
            }
            val adapter = MyPagerAdapter(host, activity.supportFragmentManager, listOf(clicked),
                fragmentFactory = { Page() })
            pager.adapter = adapter
            pager.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            pager.layout(0, 0, 1080, 1920)
            val oldFragment = adapter.getCurrentFragment(0) ?: throw AssertionError("Clicked fragment missing")
            assertEquals(clicked.path, (oldFragment.arguments?.getSerializable(MEDIUM) as Medium).path)

            // An empty one-item window is not proof that the full profile is empty.
            val noLocalReplacement = ViewerDeletionNavigationPolicy.resolve(
                listOf(clicked), emptyList(), clicked.path, clicked.path, null, 0,
                ordered.map { it.path }
            )
            assertNull(noLocalReplacement)
            assertFalse(ViewerDeletionNavigationPolicy.shouldFinish(false, emptyList()))
            assertSame("Do not replace the current container with an empty pager", adapter, pager.adapter)
            assertSame("Keep the already attached Fragment until replacement is validated",
                oldFragment, adapter.getCurrentFragment(0))

            val surviving = ordered.filterNot { it.path == clicked.path }
            val targetIndex = ViewerDeletionNavigationPolicy.resolve(
                listOf(clicked), surviving, clicked.path, clicked.path, null, 0,
                ordered.map { it.path }, allowFirstFreshFallback = true
            )!!
            assertEquals(ordered[1].path, surviving[targetIndex].path)
            allowed.clear()
            allowed.addAll(surviving.map { it.path }) // the target's fresh access window is now approved
            assertTrue(adapter.updateMedia(surviving))
            pager.setCurrentItem(targetIndex, false)

            assertSame("Recovery reuses the existing PagerAdapter", adapter, pager.adapter)
            assertEquals(surviving[targetIndex].path,
                (adapter.getCurrentFragment(targetIndex)?.arguments?.getSerializable(MEDIUM) as Medium).path)
            assertFalse(adapter.media.any { it.path == clicked.path })
        } finally { controller.pause().stop().destroy() }
    }

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

    @Test fun completeMixedCatalogKeepsCurrentFragmentAndReleasesDelayedNeighborPages() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val pager = ProgressivePhotoViewPager(activity, Robolectric.buildAttributeSet().build()).apply { id = View.generateViewId() }
            activity.setContentView(pager)
            val all = (0 until 60).map { Medium(null, "$it", "/Gallery/$it", "/Gallery",
                10L, 10L, 1L, if (it % 3 == 0) TYPE_VIDEOS else TYPE_IMAGES, 0, false, 0L, 0L) }
            var now = 100L
            val access = PhotoViewerPageAccessState(maxAgeMs = 50L, now = { now })
            access.reset(1L)
            access.approve(listOf(all[30].path), 1L)
            val host = object : ViewerPagerHost {
                override fun mayLoadFavoritePath(path: String) = access.contains(path, 1L)
                override fun fragmentClicked() = Unit
                override fun videoEnded() = true
                override fun goToPrevItem() = Unit
                override fun goToNextItem() = Unit
                override fun launchViewVideoIntent(path: String) = Unit
                override fun isSlideShowActive() = false
                override fun isFullScreen() = false
            }
            var created = 0
            val adapter = MyPagerAdapter(host, activity.supportFragmentManager, all,
                fragmentFactory = { created++; Page() })
            pager.adapter = adapter
            pager.setCurrentItem(30, false)
            pager.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            pager.layout(0, 0, 1080, 1920)
            val current = adapter.getCurrentFragment(30)
            assertNotNull(current)
            val before = created
            var notifications = 0
            adapter.registerDataSetObserver(object : android.database.DataSetObserver() {
                override fun onChanged() { notifications++ }
            })
            repeat(8) { assertFalse(adapter.updateMedia(all.map { it.copy() })) }
            assertEquals(0, notifications)
            assertEquals(before, created)
            assertSame(current, adapter.getCurrentFragment(30))
            // Neighbor metadata is present but cannot instantiate before fresh permission.
            access.approve((28..32).map { all[it].path }, 1L)
            adapter.onAccessWindowUpdated()
            assertSame(current, adapter.getCurrentFragment(30))
            val steps = (31..42).toList() + (41 downTo 18).toList()
            steps.forEach { position ->
                access.approve((position - 2..position + 2).filter { it in all.indices }.map { all[it].path }, 1L)
                adapter.onAccessWindowUpdated()
                pager.setCurrentItem(position, false)
                assertEquals(position, pager.currentItem)
                assertNotNull(adapter.getCurrentFragment(position))
                assertEquals(all[position].path, (adapter.getCurrentFragment(position)!!.arguments!!
                    .getSerializable(MEDIUM) as Medium).path)
            }
            now += 60L
            assertFalse(host.mayLoadFavoritePath(all[18].path))
            access.approve((16..20).map { all[it].path }, 1L)
            adapter.onAccessWindowUpdated()
            assertTrue(host.mayLoadFavoritePath(all[18].path))
            access.reset(2L)
            assertFalse(host.mayLoadFavoritePath(all[18].path))
            // Revocation is still enforced during a catalog reconciliation.
            adapter.updateMedia(all.dropLast(1))
            assertNull(adapter.getCurrentFragment(18))
        } finally { controller.pause().stop().destroy() }
    }

    // Avoid fake Activity state: the test restores the real path explicitly during publication,
    // just as ViewPagerActivity removes its listener while replacing the backing catalog.
    private fun ViewPager.removeOnPageChangeListenerForTest() { clearOnPageChangeListeners() }
}
