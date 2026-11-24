package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.PagerPhotoItemBinding
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The renderer-binding half of the Phase 2.2 quality handoff (E group).
 *
 * E02/E03: the first renderer frame opens the entry gate exactly once per media,
 * and the full decode that later replaces the preview must not re-open it. The
 * upgrade stays observable through the resource-stage diagnostics instead
 * (`viewer_entry_resource_bound` records every binding; the entry notification does
 * not deduplicate that record).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28],
    shadows = [PhotoResourceStageTest.HostGifHandle::class])
class PhotoResourceStageTest {
    // These tests inflate the actual photo layout without decoding a GIF on the host.
    @org.robolectric.annotation.Implements(
        className = "pl.droidsonroids.gif.GifInfoHandle", isInAndroidSdk = false)
    class HostGifHandle {
        companion object {
            @JvmStatic @org.robolectric.annotation.Implementation fun __staticInitializer__() = Unit
        }
    }

    private class RecordingListener : ViewPagerFragment.FragmentListener {
        val rendered = mutableListOf<String>()
        override fun imageRendered(path: String, page: ViewPagerFragment) { rendered += path }
        override fun fragmentClicked() {}
        override fun videoEnded() = false
        override fun goToPrevItem() {}
        override fun goToNextItem() {}
        override fun launchViewVideoIntent(path: String) {}
        override fun isSlideShowActive() = false
        override fun isFullScreen() = false
    }

    private fun drawable(size: Int) = BitmapDrawable(null as android.content.res.Resources?,
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888))

    private fun report(page: PhotoFragment, resource: android.graphics.drawable.Drawable) {
        page.javaClass.getDeclaredMethod("reportImageRendered", android.graphics.drawable.Drawable::class.java)
            .apply { isAccessible = true }.invoke(page, resource)
    }

    private fun field(page: PhotoFragment, name: String, value: Any?) =
        page.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(page, value)

    private fun withPage(block: (PhotoFragment, PagerPhotoItemBinding, RecordingListener) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val binding = PagerPhotoItemBinding.inflate(activity.layoutInflater)
            activity.setContentView(binding.root)
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, 400, 400)
            val medium = Medium(null, "a.jpg", "/photos/a.jpg", "/photos", 10, 10, 100,
                TYPE_IMAGES, 0, false, 0, 1)
            val page = PhotoFragment()
            androidx.fragment.app.Fragment::class.java.getDeclaredField("mView")
                .apply { isAccessible = true }.set(page, binding.root)
            field(page, "binding", binding)
            field(page, "mMedium", medium)
            field(page, "mIsFragmentVisible", true)
            val listener = RecordingListener()
            page.listener = listener
            block(page, binding, listener)
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    fun e02TheFirstRendererFrameOpensTheEntryGateExactlyOnce() {
        withPage { page, binding, listener ->
            val preview = drawable(100)
            binding.gesturesView.setImageDrawable(preview)
            report(page, preview)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("/photos/a.jpg"), listener.rendered)
        }
    }

    @Test
    fun e03TheFullResourceUpgradeDoesNotReopenTheEntryGate() {
        withPage { page, binding, listener ->
            val preview = drawable(100)
            binding.gesturesView.setImageDrawable(preview)
            report(page, preview)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            // The full decode replaces the drawable in place.
            val full = drawable(400)
            binding.gesturesView.setImageDrawable(full)
            report(page, full)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals("the upgrade must not restart the shared entry",
                listOf("/photos/a.jpg"), listener.rendered)
        }
    }

    @Test
    fun aStalePageOrMediaCannotNotify() {
        withPage { page, binding, listener ->
            // The fragment is no longer visible: no notification.
            field(page, "mIsFragmentVisible", false)
            val drawable = drawable(100)
            binding.gesturesView.setImageDrawable(drawable)
            report(page, drawable)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, listener.rendered.size)
        }
    }
}
