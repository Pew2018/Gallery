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

    @Test fun selectedRendererOrTimeoutReleasesEntryAndFastBackCancelsReturnCapture() {
        for (rendererReadyBeforeTimeout in listOf(true, false)) {
            val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).get()
            val type = ViewPagerActivity::class.java
            fun state(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
            val pending = state("pendingPhotoSharedElement")
            val running = state("mediaTransitionRunning")
            val selected = "/Gallery/selected.jpg"
            state("photoSharedElementPath").set(activity, selected)
            pending.setBoolean(activity, true)
            running.setBoolean(activity, true)

            activity.imageRendered("/Gallery/adjacent.jpg")
            assertTrue(pending.getBoolean(activity))
            if (rendererReadyBeforeTimeout) {
                activity.imageRendered(selected)
            } else {
                (state("photoSharedElementTimeout").get(activity) as Runnable).run()
                assertFalse(pending.getBoolean(activity))
                activity.imageRendered(selected)
            }
            assertFalse(pending.getBoolean(activity))
            assertTrue(running.getBoolean(activity))

            activity.onBackPressed()
            assertTrue(activity.isFinishing)
            assertFalse(running.getBoolean(activity))
            assertFalse(state("photoSharedElementMapped").getBoolean(activity))
        }
    }

    @Test fun lateEntryCompletionLeavesReturnCaptureRunning() {
        val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).get()
        val type = ViewPagerActivity::class.java
        val running = type.getDeclaredField("mediaTransitionRunning").apply { isAccessible = true }
        running.setBoolean(activity, true)
        type.getDeclaredField("finishAfterSharedTransitionInProgress").apply { isAccessible = true }
            .setBoolean(activity, true)

        type.getDeclaredMethod("finishMediaEntryTransition").apply { isAccessible = true }.invoke(activity)
        assertTrue(running.getBoolean(activity))
    }

    @Test fun sharedProgressMovesCanvasAndChromeTogetherAndReturnRejectsLateEntryFrames() {
        val activity = Robolectric.buildActivity(ViewPagerActivity::class.java).get()
        val type = ViewPagerActivity::class.java
        fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        fun frame(method: String, progress: Float) =
            type.getDeclaredMethod(method, Float::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, progress)
        val binding = type.getDeclaredMethod("getBinding").apply { isAccessible = true }
            .invoke(activity) as org.fossify.gallery.databinding.ActivityMediumBinding
        org.fossify.gallery.activities.BaseViewerActivity::class.java
            .getDeclaredMethod("refreshViewerSystemBars").apply { isAccessible = true }.invoke(activity)
        field("mediaTransitionRunning").setBoolean(activity, true)
        val canvas = binding.viewerTransitionScrim
        val chrome = binding.mediumViewerAppbar

        frame("renderPhotoTransitionProgress", 0.4f)
        assertEquals(0.4f, canvas.alpha, 0f)
        assertEquals(canvas.alpha, chrome.alpha, 0f)
        assertEquals(1f, binding.mediumViewerToolbar.alpha, 0f)

        field("finishAfterSharedTransitionInProgress").setBoolean(activity, true)
        field("returnViewerLayerProgress").setFloat(activity, 0.4f)
        frame("renderSharedReturnProgress", 0.5f)
        assertEquals(0.2f, canvas.alpha, 0f)
        assertEquals(canvas.alpha, chrome.alpha, 0f)

        frame("renderPhotoTransitionProgress", 1f)
        assertEquals(0.2f, canvas.alpha, 0f)
        frame("renderSharedReturnProgress", 1f)
        assertEquals(0f, canvas.alpha, 0f)
        assertEquals(canvas.alpha, chrome.alpha, 0f)
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
            val gate = field("mediaGridPublication").get(activity) as ViewerSwipePublicationGate<*>
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
            type.getDeclaredMethod("onPhotoFastScrollStateChanged", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, false)
            assertFalse(gate.hasPending)
            assertSame("Queued obsolete results never enter publishMedia", visible, field("mMedia").get(activity))
            assertEquals(1, adapter.itemCount)

            // Ordinary folders use the same session gate.
            field("mPath").set(activity, "/Gallery")
            type.getDeclaredMethod("onPhotoFastScrollStateChanged", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(activity, true)
            assertTrue(field("mediaFastScrollActive").getBoolean(activity))
            val adapterFastScroll = org.fossify.gallery.adapters.MediaAdapter::class.java
                .getDeclaredField("fastScrollActive").apply { isAccessible = true }
            assertTrue("Ordinary media adapters also reject stale in-flight commits",
                adapterFastScroll.getBoolean(adapter))
        } finally {
            binding.mediaGrid.adapter = null
            worker.shutdownNow()
            PhotoProfileRepository.of(app).delete(profile.id)
        }
    }

}
