package org.fossify.gallery.helpers

import android.app.Application
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * The Grid-side half of the Phase 2.2 return prefetch (D group).
 *
 * The decisive property is D05: a prefetched request is only useful if its Glide
 * engine key equals the key the returning holder will issue. The parity is verified
 * against the real binding pipeline - the request identity stamped on a bound
 * holder's thumbnail must equal what [MediaAdapter.returnPrefetchRequest] builds
 * for the same media.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    shadows = [ViewerReturnPrefetchPolicyTest.HostWebpFactory::class])
class ViewerReturnPrefetchPolicyTest {
    // These are request-identity tests with nonexistent local paths, not decoder
    // tests. Android's bundled ARM WebP library cannot be loaded in the host JVM.
    @org.robolectric.annotation.Implements(
        className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @org.robolectric.annotation.Implementation fun __staticInitializer__() = Unit
        }
    }

    // Plain Application deliberately has no Picasso startup ContentProvider, but a
    // bound holder cancels its Picasso request on rebind.
    private var testPicasso: com.squareup.picasso.Picasso? = null
    private var previousPicasso: Any? = null
    private val picassoSingleton get() = com.squareup.picasso.Picasso::class.java
        .getDeclaredField("singleton").apply { isAccessible = true }

    @org.junit.Before fun installHostImages() {
        previousPicasso = picassoSingleton.get(null)
        testPicasso = com.squareup.picasso.Picasso.Builder(
            org.robolectric.RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, testPicasso)
    }

    @org.junit.After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        testPicasso?.shutdown()
    }

    private fun medium(name: String, type: Int = TYPE_IMAGES) =
        Medium(null, name, "/photos/$name", "/photos", 10, 10, 100, type, 0, false, 0, 1)

    private fun withAdapter(block: (MediaAdapter, MyRecyclerView, ArrayDeque<Runnable>) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val tasks = ArrayDeque<Runnable>()
            val adapter = MediaAdapter(activity, arrayListOf(medium("a.jpg"), medium("b.mp4", TYPE_VIDEOS)),
                null, false, false, "/photos", recycler, Executor { tasks.addLast(it) }) {}
            recycler.adapter = adapter
            controller.visible()
            block(adapter, recycler, tasks)
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    fun d05ThePrefetchRequestKeyMatchesTheHoldersBoundRequestIdentity() {
        withAdapter { adapter, recycler, _ ->
            val medium = adapter.media.filterIsInstance<Medium>().first { it.path == "/photos/a.jpg" }
            val prefetch = adapter.returnPrefetchRequest(medium, 400, 400)
            assertNotNull(prefetch)
            // Force a bind so the holder stamps its own request identity.
            recycler.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 400, 1200)
            val holder = recycler.findViewHolderForAdapterPosition(0)
            assertNotNull("the first item must be bound for the parity check", holder)
            val thumbnail = holder!!.itemView.findViewById<android.widget.ImageView>(R.id.medium_thumbnail)
            val boundKey = thumbnail.getTag(R.id.thumbnail_request_key)
            assertNotNull("the bound holder must carry its request identity", boundKey)
            assertEquals("prefetch key must equal the holder's request key",
                boundKey, prefetch!!.first)
        }
    }

    @Test
    fun d08VideoAndImageMediaBuildDifferentPrefetchIdentities() {
        withAdapter { adapter, _, _ ->
            val image = adapter.returnPrefetchRequest(medium("a.jpg"), 400, 400)!!
            val video = adapter.returnPrefetchRequest(medium("a.mp4", TYPE_VIDEOS), 400, 400)!!
            assertNotEquals(image.first, video.first)
        }
    }

    @Test
    fun d06UnusableSizesAreRejectedInsteadOfGuessing() {
        withAdapter { adapter, _, _ ->
            val medium = adapter.media.filterIsInstance<Medium>().first()
            assertNull(adapter.returnPrefetchRequest(medium, 0, 400))
            assertNull(adapter.returnPrefetchRequest(medium, 400, -1))
        }
    }

    /**
     * C17: building and running a preview request must not disturb the holder's own
     * binding - the grid's normal load path is untouched, so a failed or cancelled
     * prefetch can never block the return's standard decode.
     */
    @Test
    fun c17BuildingAPrefetchDoesNotDisturbTheHoldersBinding() {
        withAdapter { adapter, recycler, _ ->
            val medium = adapter.media.filterIsInstance<Medium>().first { it.path == "/photos/a.jpg" }
            val prefetch = adapter.returnPrefetchRequest(medium, 400, 400)!!
            recycler.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 400, 1200)
            val holder = requireNotNull(recycler.findViewHolderForAdapterPosition(0))
            val thumbnail = holder.itemView.findViewById<android.widget.ImageView>(R.id.medium_thumbnail)
            // The holder still carries its own identity and binding, and the prefetch
            // key equals it: the preview work never re-stamps or clears the holder.
            assertEquals("/photos/a.jpg", thumbnail.getTag(R.id.media_bound_path))
            assertEquals(prefetch.first, thumbnail.getTag(R.id.thumbnail_request_key))
            assertNotNull("the holder's own request is still the one it will read",
                thumbnail.getTag(R.id.media_thumbnail_load))
        }
    }

    /**
     * D12: pixels prepared while browsing are consumed by the return instead of a
     * second decode - the coordinator's `prepared` state and the reserve hit agree.
     */
    @Test
    fun d12APreparedReturnResourceIsReusedByTheReturn() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val coordinator = ViewerReturnPrefetchCoordinator()
        val key = listOf("/photos/a.jpg", "sig", 1, true, 1, false, false, false)
        coordinator.onPageChanged("/photos/a.jpg", nowMs = 0)
        assertEquals("/photos/a.jpg",
            coordinator.onStabilityElapsed(ViewerReturnPrefetchCoordinator.STABILITY_MS))
        coordinator.onStart("/photos/a.jpg")
        assertTrue(reserve.remember(key, android.graphics.drawable.BitmapDrawable(
            null, android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888))))
        coordinator.onFinished("/photos/a.jpg", stored = true)
        val served = reserve.take(key, 64, 64)
        assertNotNull("the return must take the prepared pixels", served)
        assertEquals(0, reserve.size)
        assertEquals("/photos/a.jpg", coordinator.readyPath)
    }

    @Test
    fun d02TheRegistryFollowsTheViewersCurrentMedia() {
        val seen = mutableListOf<String?>()
        val owner = Any()
        ViewerCurrentMediaRegistry.observe(owner) { seen += it }
        try {
            ViewerCurrentMediaRegistry.report("/a.jpg")
            ViewerCurrentMediaRegistry.report("/b.jpg")
            assertEquals(listOf("/a.jpg", "/b.jpg"), seen)
            assertEquals("/b.jpg", ViewerCurrentMediaRegistry.current())
        } finally {
            ViewerCurrentMediaRegistry.remove(owner)
            ViewerCurrentMediaRegistry.reset()
        }
    }

    @Test
    fun d03AnUnchangedReportDoesNotNotifyAndRemovalStopsTheObserver() {
        val seen = mutableListOf<String?>()
        val owner = Any()
        ViewerCurrentMediaRegistry.observe(owner) { seen += it }
        try {
            ViewerCurrentMediaRegistry.report("/a.jpg")
            ViewerCurrentMediaRegistry.report("/a.jpg")
            assertEquals("rapid settle on the same page must not retrigger", listOf("/a.jpg"), seen)
            ViewerCurrentMediaRegistry.remove(owner)
            ViewerCurrentMediaRegistry.report("/c.jpg")
            assertEquals(listOf("/a.jpg"), seen)
        } finally {
            ViewerCurrentMediaRegistry.reset()
        }
    }

    @Test
    fun d17ASecondObserverReplacesTheFirstForTheSameOwner() {
        val first = mutableListOf<String?>()
        val second = mutableListOf<String?>()
        val owner = Any()
        ViewerCurrentMediaRegistry.observe(owner) { first += it }
        ViewerCurrentMediaRegistry.observe(owner) { second += it }
        try {
            ViewerCurrentMediaRegistry.report("/a.jpg")
            assertTrue(first.isEmpty())
            assertEquals(listOf("/a.jpg"), second)
        } finally {
            ViewerCurrentMediaRegistry.reset()
        }
    }

    @Test
    fun resetDropsEverythingWhenTheViewerGoesAway() {
        val seen = mutableListOf<String?>()
        val owner = Any()
        ViewerCurrentMediaRegistry.observe(owner) { seen += it }
        ViewerCurrentMediaRegistry.report("/a.jpg")
        ViewerCurrentMediaRegistry.reset()
        assertNull(ViewerCurrentMediaRegistry.current())
        ViewerCurrentMediaRegistry.report("/b.jpg")
        assertEquals("no observer survives a reset", listOf("/a.jpg"), seen)
    }

    /**
     * Fifty page changes through the real registry and reserve (task §11.3): holdings
     * stay bounded, the observer set stays at one, and every reservation is either
     * consumed by its own return or released with the session.
     */
    @Test
    fun elevenThreeFiftyPageChangesKeepHoldingsBounded() {
        val reserve = ViewerReturnPixelReserve(capacity = 3)
        val owner = Any()
        val seen = mutableListOf<String?>()
        ViewerCurrentMediaRegistry.observe(owner) { seen += it }
        fun pixels() = android.graphics.drawable.BitmapDrawable(null as android.content.res.Resources?,
            android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888))
        try {
            repeat(50) { index ->
                val path = "/photos/$index.jpg"
                ViewerCurrentMediaRegistry.report(path)
                val key = listOf(path, "sig-$index", 1, true, 1, false, false, false)
                reserve.remember(key, pixels(), owner = owner)
                // Every third return consumes the reservation it was prepared for.
                if (index % 3 == 0) {
                    assertNotNull("prepared media must be servable",
                        reserve.take(key, 64, 64))
                }
                assertTrue(reserve.size <= ViewerReturnPixelReserve.DEFAULT_CAPACITY)
                assertTrue(reserve.reservedBytes <= ViewerReturnPixelReserve.MAX_TOTAL_BYTES)
            }
            assertEquals("one notification per page change", 50, seen.size)
            reserve.clear(owner = owner)
            assertEquals("the session release empties the reserve", 0, reserve.size)
        } finally {
            ViewerCurrentMediaRegistry.remove(owner)
            ViewerCurrentMediaRegistry.reset()
        }
    }
}
