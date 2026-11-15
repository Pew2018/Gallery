package org.fossify.gallery.fragments

import android.app.Application
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.helpers.MediaTransitionGeometry
import org.fossify.gallery.helpers.VideoPreviewFrameHandoff
import org.junit.Assert.assertEquals
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
            type.getDeclaredField("binding").apply { isAccessible = true }.set(fragment, binding)
            type.getDeclaredField("mTextureView").apply { isAccessible = true }.set(fragment, binding.videoSurface)
            val setViewport = type.getDeclaredMethod("layoutVideoViewport", Pair::class.java)
                .apply { isAccessible = true }
            val textureBounds = type.getDeclaredMethod("displayedTextureBounds").apply { isAccessible = true }
            val canvasWidth = 401
            val canvasHeight = 801
            val from = MediaTransitionGeometry.fit(canvasWidth, canvasHeight, 1f)!!
            val to = MediaTransitionGeometry.fit(canvasWidth, canvasHeight, 2f)!!
            val frames = ArrayDeque<Runnable>()
            val handoff = VideoPreviewFrameHandoff(binding.videoPreview) { frames.addLast(it) }
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
            handoff.setTransitionRunning(false)
            assertEquals(View.GONE, binding.videoPreview.visibility)
            assertEquals(beforeFirstFrame, textureBounds.invoke(fragment))
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
