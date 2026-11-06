package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.activities.ViewPagerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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

    @Test fun actualPhotoGridDefersIdleFastScrollPublicationsBeforeSnapshotWork() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val profile = PhotoProfile(java.util.UUID.randomUUID().toString(), "grid",
            listOf(PhotoSource(PhotoSourceType.FOLDER, "/Gallery")))
        PhotoProfileRepository.of(app).save(profile)
        val controller = Robolectric.buildActivity(org.fossify.gallery.activities.MediaActivity::class.java)
        val activity = controller.get()
        activity.setTheme(org.fossify.gallery.R.style.AppTheme)
        activity.delegate.installViewFactory()
        val type = org.fossify.gallery.activities.MediaActivity::class.java
        fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        field("mPath").set(activity, PhotoProfileMediaLoader.PATH)
        field("photoProfileId").set(activity, profile.id)
        val item = org.fossify.gallery.models.Medium().apply {
            path = "/Gallery/current.jpg"; name = "current.jpg"; parentPath = "/Gallery"; this.type = TYPE_IMAGES
        }
        val visible = arrayListOf<org.fossify.gallery.models.ThumbnailItem>(item)
        field("mMedia").set(activity, visible)
        val binding = type.getDeclaredMethod("getBinding").apply { isAccessible = true }.invoke(activity)
            as org.fossify.gallery.databinding.ActivityMediaBinding
        val adapter = org.fossify.gallery.adapters.MediaAdapter(activity, ArrayList(visible), null,
            false, false, PhotoProfileMediaLoader.PATH, binding.mediaGrid) { }
        binding.mediaGrid.adapter = adapter
        assertEquals(androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE, binding.mediaGrid.scrollState)
        type.getDeclaredMethod("onPhotoFastScrollStateChanged", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        assertEquals("Fast-scroll intent is independent from RecyclerView state",
            androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE, binding.mediaGrid.scrollState)
        val requests = field("mediaLoadRequests").get(activity) as FavoriteMediaLoadRequests
        val request = requests.begin()
        val key = type.getDeclaredMethod("photoDisplayKey").apply { isAccessible = true }.invoke(activity) as String
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val context = worker.submit<PhotoProfileLoadContext> { PhotoProfileLoadContext.capture(app, profile.id, null)!! }.get()
            val initial = PhotoLoadResult(context, PhotoLoadStage.INDEXED, listOf(item), PhotoAccessSummary(false, true))
            activity.submitPhotoGridResult(initial, request, key)
            val gate = field("photoGridPublication").get(activity) as ViewerSwipePublicationGate<*>
            assertTrue(gate.isMoving)
            assertTrue(gate.hasPending)
            val firstPending = gate.javaClass.getDeclaredField("latest").apply { isAccessible = true }.get(gate)
            activity.submitPhotoGridResult(initial.copy(stage = PhotoLoadStage.COMPLETE,
                media = listOf(item, item.copy(path = "/Gallery/new.jpg"))), request, key)
            val finalPending = gate.javaClass.getDeclaredField("latest").apply { isAccessible = true }.get(gate)
            assertNotSame(firstPending, finalPending)
            activity.submitPhotoGridResult(initial, request, key)
            assertSame("Late INDEXED cannot replace queued COMPLETE", finalPending,
                gate.javaClass.getDeclaredField("latest").apply { isAccessible = true }.get(gate))
            assertSame(visible, field("mMedia").get(activity))
            assertEquals(1, adapter.itemCount)
            PhotoContentRevision.changed()
            @Suppress("UNCHECKED_CAST")
            (gate.onScrollMoving(false) as (() -> Unit)?)?.invoke()
            assertSame("Queued obsolete results never enter publishMedia", visible, field("mMedia").get(activity))
            assertEquals(1, adapter.itemCount)
        } finally {
            binding.mediaGrid.adapter = null
            worker.shutdownNow()
            PhotoProfileRepository.of(app).delete(profile.id)
        }
    }

}
