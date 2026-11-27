package org.fossify.gallery.fragments

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import org.fossify.gallery.helpers.MediaTransitionGeometry
import org.fossify.gallery.helpers.VideoPreviewFrameHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VideoViewerFrameLayerTest {
    @Test fun returnCropKeepsVideoUniformAndFillsTheChangingAperture() {
        val matrix = Matrix()
        val frame = RectF(0f, 0f, 180f, 320f)
        listOf(180f to 320f, 150f to 220f, 120f to 120f).forEach { (width, height) ->
            MediaTransitionGeometry.centerCrop(matrix, width, height, 180, 320)
            val content = RectF(frame)
            matrix.mapRect(content)
            assertEquals(frame.width() / frame.height(), content.width() / content.height(), .001f)
            assertEquals(width / 2f, content.centerX(), .001f)
            assertEquals(height / 2f, content.centerY(), .001f)
            assertEquals(1f, minOf(content.width() / width, content.height() / height), .001f)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [29])
    fun loadedCoverAndTextureShareTheLaidOutRectBeforeAndAfterFirstFrame() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val binding = PagerVideoItemBinding.inflate(controller.get().layoutInflater)
            controller.get().setContentView(binding.root)
            val fragment = VideoFragment()
            val type = VideoFragment::class.java
            fun videoField(name: String, value: Any) = type.getDeclaredField(name)
                .apply { isAccessible = true }.set(fragment, value)
            fun fragmentField(name: String, value: Any) = Fragment::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.set(fragment, value)
            val activity = controller.get()
            val host = FragmentManager::class.java.getDeclaredField("mHost").apply { isAccessible = true }
                .get(activity.supportFragmentManager)
            fragmentField("mView", binding.root)
            fragmentField("mHost", host)
            fragmentField("mAdded", true)
            val path = "/Gallery/test.mp4"
            videoField("mMedium", Medium().apply { this.path = path })
            videoField("mIsFragmentVisible", true)
            videoField("binding", binding)
            videoField("mTextureView", binding.videoSurface)
            videoField("mConfig", activity.config)
            binding.videoPreview.setImageBitmap(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))
            val setViewport = type.getDeclaredMethod("layoutVideoViewport", Pair::class.java)
                .apply { isAccessible = true }
            val textureBounds = type.getDeclaredMethod("displayedTextureBounds").apply { isAccessible = true }
            val canvasWidth = 401
            val canvasHeight = 801
            val from = MediaTransitionGeometry.fit(canvasWidth, canvasHeight, 1f)!!
            val to = MediaTransitionGeometry.fit(canvasWidth, canvasHeight, 2f)!!
            val frames = ArrayDeque<Runnable>()
            val handoff = VideoPreviewFrameHandoff(binding.videoPreview) { frames.addLast(it) }
            videoField("videoPreviewFrameHandoff", handoff)
            val player = Any()
            handoff.setTransitionRunning(true)
            handoff.bind(player)
            handoff.onRenderedFirstFrame(player)
            frames.removeFirst().run()
            frames.removeFirst().run()

            listOf(0f, .5f, 1f).forEach { progress ->
                val size = MediaTransitionGeometry.viewportSize(from, to, progress)
                setViewport.invoke(fragment, size)
                binding.root.measure(
                    View.MeasureSpec.makeMeasureSpec(canvasWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(canvasHeight, View.MeasureSpec.EXACTLY),
                )
                binding.root.layout(0, 0, canvasWidth, canvasHeight)
                val cover = binding.videoPreview
                val coverBounds = RectF(cover.left.toFloat(), cover.top.toFloat(), cover.right.toFloat(), cover.bottom.toFloat())
                val frameBounds = textureBounds.invoke(fragment) as RectF
                assertEquals(size.first.toFloat(), coverBounds.width(), 0f)
                assertEquals(size.second.toFloat(), coverBounds.height(), 0f)
                assertTrue("Cover $coverBounds and decoded frame $frameBounds", MediaTransitionGeometry.sameBounds(coverBounds, frameBounds))
                assertEquals(View.VISIBLE, cover.visibility)
            }
            val beforeFirstFrame = textureBounds.invoke(fragment) as RectF
            val cover = binding.videoPreview
            assertNull(fragment.sharedElementViewFor(path, deferTakeover = true))
            videoField("previewReady", true)
            val proxy = fragment.sharedElementViewFor(path, deferTakeover = true)
            assertNotNull(proxy)
            checkNotNull(proxy)
            val proxyBounds = RectF(proxy.left.toFloat(), proxy.top.toFloat(), proxy.right.toFloat(), proxy.bottom.toFloat())
            assertTrue(MediaTransitionGeometry.sameBounds(proxyBounds, beforeFirstFrame))
            assertEquals(1f, cover.alpha, 0f)
            assertTrue(fragment.takeOverSharedEntry(proxy))
            assertEquals(0f, cover.alpha, 0f)
            assertEquals(1f, proxy.alpha, 0f)
            assertEquals(View.VISIBLE, cover.visibility)
            fragment.finishSharedTransition()
            assertNull(proxy.parent)
            assertEquals(View.GONE, cover.visibility)
            assertEquals(beforeFirstFrame, textureBounds.invoke(fragment))

            // Return an unplayed video using its real decoded cover and keep the
            // cover protected if its first frame becomes ready during the return.
            handoff.bind(player)
            val returnProxy = fragment.sharedElementViewFor(path) as android.widget.ImageView
            assertEquals(beforeFirstFrame, RectF(returnProxy.left.toFloat(), returnProxy.top.toFloat(),
                returnProxy.right.toFloat(), returnProxy.bottom.toFloat()))
            handoff.onRenderedFirstFrame(player)
            frames.removeFirst().run()
            frames.removeFirst().run()
            assertEquals(View.VISIBLE, cover.visibility)
            assertEquals(0f, cover.transitionAlpha, 0f)
            assertEquals(0f, binding.videoSurfaceFrame.transitionAlpha, 0f)
            assertNotNull(returnProxy.drawable)
            fragment.finishSharedTransition()
            assertNull(returnProxy.parent)
            assertNull(returnProxy.drawable)
            assertEquals(1f, cover.transitionAlpha, 0f)
            assertEquals(View.GONE, cover.visibility)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun previewIsAboveTextureFrameAndViewerControlsRemainAbovePreview() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val binding = PagerVideoItemBinding.inflate(controller.get().layoutInflater)
            val root = binding.root as ViewGroup
            assertTrue(root.indexOfChild(binding.videoSurfaceFrame) < root.indexOfChild(binding.videoPreview))
            assertTrue(root.indexOfChild(binding.videoPreview) < root.indexOfChild(binding.videoVolumeController))
            assertTrue(root.indexOfChild(binding.videoPreview) < root.indexOfChild(binding.videoPlayOutline))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
