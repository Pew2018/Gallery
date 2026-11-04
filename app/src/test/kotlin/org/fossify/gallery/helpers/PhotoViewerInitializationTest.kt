package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.activities.ViewPagerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** Drives the production Activity's actual first-layout callback in the adverse order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotoViewerInitializationTest {
    @Test fun firstLayoutCannotCancelAlreadyScheduledCompleteCatalog() {
        // Attached Activity, deliberately before onCreate/start: layout and worker
        // completion are controlled here rather than racing host scheduling.
        val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).get()
        ViewPagerActivity::class.java.getDeclaredField("mMediaFiles").apply { isAccessible = true }
            .set(activity, arrayListOf(org.fossify.gallery.models.Medium().apply {
                path = "/Gallery/clicked.jpg"; name = "clicked.jpg"; type = TYPE_IMAGES
            }))
        val generation = ViewPagerActivity::class.java.getDeclaredField("mediaPublicationGeneration")
            .apply { isAccessible = true }
        activity.scheduleInitialViewerPublication()
        generation.setLong(activity, 4L) // full catalog is now the latest publication
        val binding = ViewPagerActivity::class.java.getDeclaredMethod("getBinding")
            .apply { isAccessible = true }.invoke(activity) as org.fossify.gallery.databinding.ActivityMediumBinding
        binding.viewPager.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals("The singleton layout callback must not create a newer request", 4L, generation.getLong(activity))
    }
}
