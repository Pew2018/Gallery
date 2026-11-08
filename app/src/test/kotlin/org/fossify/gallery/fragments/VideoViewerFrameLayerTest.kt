package org.fossify.gallery.fragments

import android.app.Application
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VideoViewerFrameLayerTest {
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
