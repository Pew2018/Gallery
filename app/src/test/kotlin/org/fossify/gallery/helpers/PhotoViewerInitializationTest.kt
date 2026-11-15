package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.databinding.ActivityMediumBinding
import org.fossify.gallery.databinding.PagerPhotoItemBinding
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.PhotoTransitionCompletionGate.Stage
import org.fossify.gallery.models.Medium
import org.fossify.gallery.activities.ViewPagerActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Drives the production Activity's actual first-layout callback in the adverse order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [PhotoViewerInitializationTest.HostGifHandle::class])
class PhotoViewerInitializationTest {
    // These tests inflate the actual photo layout without decoding a GIF on the host.
    @Implements(className = "pl.droidsonroids.gif.GifInfoHandle", isInAndroidSdk = false)
    class HostGifHandle {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

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

    @Test fun timeoutBeforeLayoutReleasesAndRejectedMappingClearsInputWithStableLoading() {
        val fixture = EntryFixture()
        val entry = fixture.entry
        fixture.call("requestMediaEntryRelease", args = arrayOf(entry, "load_failed"))
        assertEquals(Stage.RELEASE_REQUESTED, entry.stage)
        fixture.runCallback("entryReadyTimeout")
        fixture.binding.viewPager.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(Stage.RELEASED, entry.stage)
        val elements = linkedMapOf<String, View>(PhotoViewerTransition.ELEMENT_NAME to fixture.binding.viewPager)
        fixture.call("mapEntrySharedElement", args = arrayOf(entry, listOf(PhotoViewerTransition.ELEMENT_NAME), elements))
        assertEquals(Stage.REJECTED, entry.stage)
        assertTrue(elements.isEmpty())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        fixture.assertFallbackLoading()
        fixture.setPhoto().also {
            fixture.activity.imageRendered(fixture.path, it.page)
            assertEquals(View.GONE, fixture.binding.viewerEntryStatus.visibility)
        }
        fixture.runCallback("entryWatchdog")
        assertEquals(Stage.COMPLETE, entry.stage)
    }

    @Test fun selectedRendererWaitsForPredrawAndSharedCaptureBeforeOwningPixels() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val image = photo.binding.gesturesView
        val entry = fixture.entry
        fixture.activity.imageRendered(fixture.path, photo.page)
        assertEquals(Stage.RELEASE_REQUESTED, entry.stage)
        fixture.binding.viewPager.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(Stage.RELEASED, entry.stage)

        val name = PhotoViewerTransition.ELEMENT_NAME
        val elements = linkedMapOf<String, View>()
        fixture.call("mapEntrySharedElement", args = arrayOf(entry, listOf(name), elements))
        val proxy = elements.getValue(name)
        val fitted = MediaTransitionGeometry.imageBounds(image)!!
        assertEquals(fitted.width(), proxy.width.toFloat(), 1f)
        assertEquals(fitted.height(), proxy.height.toFloat(), 1f)
        assertEquals(1f, image.alpha, 0f)
        assertEquals(0f, proxy.alpha, 0f)

        fixture.call("renderPhotoTransitionProgress", args = arrayOf(entry, .2f))
        assertEquals(Stage.MAPPED, entry.stage)
        fixture.call("captureEntrySharedElement", args = arrayOf(entry, listOf(name), listOf(proxy)))
        assertEquals(Stage.CAPTURED, entry.stage)
        assertEquals(0f, image.alpha, 0f)
        assertEquals(1f, proxy.alpha, 0f)

        fixture.call("renderPhotoTransitionProgress", args = arrayOf(entry, .4f))
        assertEquals(Stage.SHARED, entry.stage)
        assertEquals(.4f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(.4f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, false))
        assertEquals(Stage.COMPLETE, entry.stage)
        assertFalse(fixture.binding.viewPager.transitionInputLocked)
        assertNull(proxy.parent)
        assertEquals(1f, image.alpha, 0f)
        assertEquals(View.GONE, fixture.binding.viewerEntryStatus.visibility)

        fixture.set("finishAfterSharedTransitionInProgress", true)
        fixture.set("mediaTransitionRunning", true)
        fixture.set("returnViewerLayerProgress", .4f)
        fixture.call("renderSharedReturnProgress", args = arrayOf(.5f))
        fixture.call("renderPhotoTransitionProgress", args = arrayOf(entry, 1f))
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, true))
        assertEquals(.2f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertTrue(fixture.activity.isMediaTransitionRunning())
    }

    @Test fun noAnimatorAfterCaptureFallsBackAndDisabledMotionFinishesImmediately() {
        val fixture = EntryFixture()
        fixture.runCallback("entryReadyTimeout")
        val photo = fixture.setPhoto()
        val name = PhotoViewerTransition.ELEMENT_NAME
        val elements = linkedMapOf<String, View>()
        fixture.call("mapEntrySharedElement", args = arrayOf(fixture.entry, listOf(name), elements))
        val proxy = elements.getValue(name)
        fixture.call("captureEntrySharedElement", args = arrayOf(fixture.entry, listOf(name), listOf(proxy)))
        assertEquals(0f, photo.binding.gesturesView.alpha, 0f)

        val resolver = fixture.activity.contentResolver
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            fixture.runCallback("entryWatchdog")
            fixture.call("onSharedEntryTerminal", args = arrayOf(fixture.entry, true))
            fixture.call("onSharedEntryTerminal", args = arrayOf(fixture.entry, false))
            assertEquals(Stage.COMPLETE, fixture.entry.stage)
            assertEquals(View.GONE, fixture.binding.viewerEntryStatus.visibility)
            assertEquals(1f, photo.binding.gesturesView.alpha, 0f)
            assertNull(proxy.parent)
            assertFalse(fixture.binding.viewPager.transitionInputLocked)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        }
    }

    @Test fun stalePageAndSamePathReloadCannotReleaseEntryAndFastBackInvalidatesPredraw() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        fixture.activity.imageRendered(fixture.path, PhotoFragment())
        assertEquals(Stage.WAITING, fixture.entry.stage)

        photo.call("beginImageRequest")
        photo.call("reportImageRendered", photo.binding.gesturesView.drawable)
        photo.call("beginImageRequest")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Stage.WAITING, fixture.entry.stage)

        photo.call("reportImageRendered", photo.binding.gesturesView.drawable)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Stage.RELEASE_REQUESTED, fixture.entry.stage)
        fixture.activity.onBackPressed()
        fixture.binding.viewPager.viewTreeObserver.dispatchOnPreDraw()
        fixture.runCallback("entryReadyTimeout")
        fixture.call("abandonMediaEntry", args = arrayOf("destroyed"))
        assertEquals(Stage.CANCELLED, fixture.entry.stage)
        assertTrue(fixture.activity.isFinishing)
        assertFalse(fixture.activity.isMediaTransitionRunning())
    }

    @Test fun enabledFallbackFadesContentAndCompletesWhenPlatformCancels() {
        val fixture = EntryFixture()
        val entry = fixture.entry
        fixture.setPhoto()
        fixture.runCallback("entryReadyTimeout")
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, true))
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, false))
        assertEquals(Stage.FALLBACK, entry.stage)
        assertEquals(.75f, fixture.binding.viewPager.alpha, 0f)

        val animator = fixture.field("entryFallbackAnimator") as android.animation.ValueAnimator
        animator.currentPlayTime = PhotoViewerTransition.FADE_DURATION_MS / 2
        assertTrue(fixture.binding.viewerTransitionScrim.alpha in 0.01f..0.99f)
        assertTrue(fixture.binding.viewPager.alpha > .75f)
        animator.end()
        assertEquals(Stage.COMPLETE, entry.stage)
        assertEquals(1f, fixture.binding.viewPager.alpha, 0f)
        assertFalse(fixture.binding.viewPager.transitionInputLocked)
    }

    @Test fun entryDiagnosticsAreLazyWhenOffAndExportsRedactPaths() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        GalleryDiagnosticLogger.initialize(app)
        try {
            GalleryDiagnosticLogger.setEnabled(app, false)
            assertEquals(0L, ViewerEntryDiagnostics.newRequest())
            ViewerEntryDiagnostics.event(1L, "should_not_log") { error("Disabled diagnostics must not evaluate View fields") }
            GalleryDiagnosticLogger.setEnabled(app, true)
            val request = ViewerEntryDiagnostics.newRequest()
            assertTrue(request > 0L)
            ViewerEntryDiagnostics.event(request, "entry_completed") {
                mapOf("stage" to "COMPLETE", "mediaPath" to "/storage/emulated/0/a.jpg", "reason" to "/storage/emulated/0/b.jpg")
            }
            var exported = ""
            GalleryDiagnosticLogger.snapshot { text, _ -> exported = text }
            val io = GalleryDiagnosticLogger::class.java.getDeclaredField("io").apply { isAccessible = true }
                .get(GalleryDiagnosticLogger) as java.util.concurrent.ExecutorService
            io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(exported.contains("module=ViewerEntry event=entry_completed"))
            assertTrue(exported.contains("request=$request"))
            assertFalse(exported.contains("should_not_log"))
            assertFalse(exported.contains("/storage"))
            assertFalse(exported.contains("mediaPath="))
        } finally {
            GalleryDiagnosticLogger.setEnabled(app, false)
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

    private class EntryFixture {
        val controller = Robolectric.buildActivity(ViewPagerActivity::class.java)
        val activity = controller.get()
        val entry = PhotoTransitionCompletionGate()
        val path = "/storage/emulated/0/Gallery/fixture-entry.jpg"
        val binding = call("getBinding") as ActivityMediumBinding

        init {
            activity.intent.putExtra(PhotoViewerTransition.EXTRA_PATH, path)
            activity.window.setContentView(binding.root)
            controller.visible()
            BaseViewerActivity::class.java.getDeclaredMethod("refreshViewerSystemBars").apply { isAccessible = true }.invoke(activity)
            set("mediaEntry", entry)
            set("photoSharedElementPath", path)
            set("mediaTransitionRunning", true)
            set("viewerSystemBarProgress", 0f)
            binding.viewPager.transitionInputLocked = true
        }

        data class TestPhoto(val page: PhotoFragment, val binding: PagerPhotoItemBinding) {
            fun call(name: String, vararg args: Any) =
                page.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
                    .apply { isAccessible = true }.invoke(page, *args)
        }

        fun setPhoto(): TestPhoto {
            val medium = Medium().apply { path = this@EntryFixture.path; name = "entry.jpg"; type = TYPE_IMAGES }
            val adapter = MyPagerAdapter(activity, activity.supportFragmentManager, listOf(medium))
            val page = PhotoFragment().apply { listener = this@EntryFixture.activity }
            @Suppress("UNCHECKED_CAST")
            val pages = adapter.javaClass.getDeclaredField("fragments").apply { isAccessible = true }.get(adapter)
                as MutableMap<ViewerMediumIdentity, ViewPagerFragment>
            pages[ViewerMediaUpdatePolicy.identity(medium)] = page
            // Keep ViewPager's population paused while the test supplies its rendered page.
            val firstLayout = androidx.viewpager.widget.ViewPager::class.java.getDeclaredField("mFirstLayout")
            firstLayout.isAccessible = true
            firstLayout.setBoolean(binding.viewPager, true)
            binding.viewPager.adapter = adapter
            androidx.viewpager.widget.ViewPager::class.java.getDeclaredField("mPopulatePending")
                .apply { isAccessible = true }.setBoolean(binding.viewPager, true)
            set("mMediaFiles", arrayListOf(medium))
            set("mPos", 0)
            val photo = PagerPhotoItemBinding.inflate(activity.layoutInflater)
            binding.root.addView(photo.root)
            photo.root.layout(0, 0, 400, 400)
            val image = photo.gesturesView
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.layout(0, 0, 400, 400)
            image.setImageBitmap(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))
            fun field(name: String, value: Any) =
                page.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(page, value)
            Fragment::class.java.getDeclaredField("mView").apply { isAccessible = true }.set(page, photo.root)
            field("binding", photo)
            field("mMedium", medium)
            field("mIsFragmentVisible", true)
            field("mHasInitialZoom", true)
            field("mInitialZoom", 1f)
            field("mCurrentGestureViewZoom", 1f)
            field("mReportedImagePath", path)
            field("deliveredImage", image.drawable)
            return TestPhoto(page, photo)
        }

        fun field(name: String): Any? =
            activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)

        fun set(name: String, value: Any) =
            activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)

        fun runCallback(name: String) {
            (activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity) as Runnable).run()
        }

        fun call(name: String, args: Array<Any> = emptyArray()): Any? =
            activity.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
                .apply { isAccessible = true }.invoke(activity, *args)

        fun assertFallbackLoading() {
            assertEquals(Stage.COMPLETE, entry.stage)
            assertFalse(activity.isMediaTransitionRunning())
            assertFalse(binding.viewPager.transitionInputLocked)
            assertEquals(Color.BLACK, (binding.root.background as ColorDrawable).color)
            assertEquals(View.GONE, binding.viewerTransitionScrim.visibility)
            assertEquals(View.VISIBLE, binding.viewerEntryStatus.visibility)
        }
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
