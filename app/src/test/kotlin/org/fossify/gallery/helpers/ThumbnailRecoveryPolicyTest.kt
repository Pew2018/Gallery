package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.squareup.picasso.Picasso
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.Executor

/**
 * Phase 1.1 settled-viewport thumbnail recovery.
 *
 * Releasing the fast scroller used to route through
 * `releaseMediaGridPublicationIfSafe`, which returns early whenever a publication is
 * still held or the grid is mid-touch. The adapter's paused fast-scroll state was
 * cleared *after* that guard, so a release that ran while the guard tripped left the
 * grid permanently paused: no holder would start a thumbnail request and the settled
 * viewport stayed blank until the user scrolled again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    shadows = [ThumbnailRecoveryPolicyTest.HostWebpFactory::class])
class ThumbnailRecoveryPolicyTest {
    @Implements(className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    private var testPicasso: Picasso? = null
    private var previousPicasso: Any? = null
    private val picassoSingleton
        get() = Picasso::class.java.getDeclaredField("singleton").apply { isAccessible = true }

    @Before fun installHostImages() {
        previousPicasso = picassoSingleton.get(null)
        testPicasso = Picasso.Builder(RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, testPicasso)
    }

    @After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        testPicasso?.shutdown()
    }

    private fun item(index: Int) = Medium(
        index.toLong() + 1, "photo-$index.jpg", "/photos/photo-$index.jpg", "/photos",
        10L, 10L, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    private class Fixture {
        val type = MediaActivity::class.java
        val controller = Robolectric.buildActivity(type)
        val activity: MediaActivity = controller.get()
        val binding: ActivityMediaBinding
        val adapter: MediaAdapter
        private val tasks = ArrayDeque<Runnable>()

        init {
            type.getDeclaredField("mPath").apply { isAccessible = true }.set(activity, "/photos")
            activity.setTheme(org.fossify.gallery.R.style.AppTheme)
            activity.delegate.installViewFactory()
            binding = type.getDeclaredMethod("getBinding").apply { isAccessible = true }
                .invoke(activity) as ActivityMediaBinding
            activity.window.setContentView(binding.root)
            adapter = MediaAdapter(activity, arrayListOf(), null, false, false, "/photos",
                binding.mediaGrid, Executor { tasks.addLast(it) }) {}
            binding.mediaGrid.layoutManager = LinearLayoutManager(activity)
            binding.mediaGrid.itemAnimator = null
            binding.mediaGrid.adapter = adapter
            controller.create().start().resume().visible()
            val attachInfo = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }
                .get(binding.mediaGrid)
            attachInfo.javaClass.getDeclaredField("mWindowVisibility").apply { isAccessible = true }
                .setInt(attachInfo, View.VISIBLE)
            layout()
        }

        fun layout() {
            binding.mediaGrid.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            binding.mediaGrid.layout(0, 0, 320, 320)
        }

        fun releasePublication() {
            type.getDeclaredMethod("releaseMediaGridPublicationIfSafe").apply { isAccessible = true }
                .invoke(activity)
        }

        fun adapterPaused(): Boolean =
            MediaAdapter::class.java.getDeclaredField("fastScrollActive").apply { isAccessible = true }
                .getBoolean(adapter)

        fun close() = controller.pause().stop().destroy()
    }

    @Test fun releasingTheThumbAlwaysClearsTheAdapterPause() {
        val fixture = Fixture()
        try {
            fixture.adapter.updateMedia(arrayListOf(item(0), item(1), item(2)), preserveScroll = false)
            fixture.layout()
            fixture.adapter.setFastScrollActive(true)
            assertTrue("the fast-scroll gesture must pause thumbnail work", fixture.adapterPaused())

            fixture.releasePublication()

            assertFalse("a released thumb must never leave the grid paused", fixture.adapterPaused())
        } finally {
            fixture.close()
        }
    }

    @Test fun aHeldPublicationNoLongerBlocksThumbnailRecovery() {
        val fixture = Fixture()
        try {
            fixture.adapter.updateMedia(arrayListOf(item(0), item(1)), preserveScroll = false)
            fixture.layout()
            fixture.adapter.setFastScrollActive(true)

            // The publication guard trips on a finishing Activity, exactly like a held
            // viewer return or a mid-touch release does on a live one.
            fixture.activity.finish()
            fixture.releasePublication()

            assertFalse("publication gating must not own the thumbnail pause state", fixture.adapterPaused())
        } finally {
            fixture.close()
        }
    }

    @Test fun aNonActiveReleaseIsANoOp() {
        val fixture = Fixture()
        try {
            fixture.adapter.updateMedia(arrayListOf(item(0)), preserveScroll = false)
            fixture.layout()
            assertFalse(fixture.adapterPaused())
            fixture.releasePublication()
            assertFalse("releasing twice must stay harmless", fixture.adapterPaused())
        } finally {
            fixture.close()
        }
    }
}
