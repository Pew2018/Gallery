package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.databinding.BottomActionsBinding
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ViewerMediaActionVisibilityTest {
    private fun medium(type: Int, name: String) = Medium(
        null, name, "/gallery/$name", "/gallery", 1L, 1L, 20L, type,
        if (type == TYPE_VIDEOS) 1000 else 0, false, 0L, 0L
    )

    @Test fun actualBottomViewsTrackRepeatedMediaTypeChangesAndConfiguredActions() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val binding = BottomActionsBinding.inflate(controller.get().layoutInflater)
            val configured = BOTTOM_ACTION_EDIT or BOTTOM_ACTION_RESIZE or BOTTOM_ACTION_ROTATE

            fun apply(medium: Medium?, actions: Int = configured) {
                ViewerMediaActionVisibility.apply(
                    binding.bottomEdit, binding.bottomResize, binding.bottomRotate, actions, medium
                )
            }
            fun assertVisibility(edit: Int, resize: Int, rotate: Int) {
                assertEquals(edit, binding.bottomEdit.visibility)
                assertEquals(resize, binding.bottomResize.visibility)
                assertEquals(rotate, binding.bottomRotate.visibility)
            }

            val image = medium(TYPE_IMAGES, "photo.jpg")
            val video = medium(TYPE_VIDEOS, "clip.mp4")
            val svg = medium(TYPE_SVGS, "vector.svg")

            apply(image)
            assertVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE)
            apply(video)
            assertVisibility(View.GONE, View.GONE, View.GONE)
            apply(image)
            assertVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE)
            apply(video)
            apply(image)
            assertVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE)
            apply(svg)
            assertVisibility(View.GONE, View.GONE, View.GONE)
            apply(image)
            assertVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE)

            apply(image, BOTTOM_ACTION_EDIT) // Other actions are configured in the toolbar.
            assertVisibility(View.VISIBLE, View.GONE, View.GONE)
            apply(video, BOTTOM_ACTION_EDIT)
            assertVisibility(View.GONE, View.GONE, View.GONE)
            apply(image, 0) // Closed bottom bar or all actions assigned elsewhere.
            assertVisibility(View.GONE, View.GONE, View.GONE)
            apply(null)
            assertVisibility(View.GONE, View.GONE, View.GONE)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
