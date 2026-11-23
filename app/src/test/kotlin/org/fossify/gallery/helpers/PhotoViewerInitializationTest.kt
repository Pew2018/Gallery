package org.fossify.gallery.helpers

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.math.roundToInt

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

    @Test fun sharedViewerCanvasStaysTransparentAcrossResume() {
        val controller = viewerWithTransitionIntent().create()
        val viewer = controller.get()
        val root = viewer.findViewById<View>(R.id.fragment_holder)
        val pager = viewer.findViewById<View>(R.id.view_pager)
        val scrim = viewer.findViewById<View>(R.id.viewer_transition_scrim)
        fun assertPrepared(stage: String) {
            listOf(viewer.window.decorView, root, pager).forEach {
                assertBackground(it, Color.TRANSPARENT, stage)
            }
            assertBackground(scrim, Color.BLACK, stage)
            assertEquals(stage, 0f, scrim.alpha, 0f)
            assertEquals(stage, View.VISIBLE, scrim.visibility)
        }
        try {
            assertPrepared("create")
            controller.start()
            assertPrepared("start")
            controller.resume()
            assertPrepared("resume")
            controller.postResume()
            assertPrepared("postResume")
            viewer.window.decorView.viewTreeObserver.dispatchOnPreDraw()
            assertPrepared("first pre-draw")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun disabledMotionKeepsTheViewerBlackAcrossResume() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val controller = viewerWithTransitionIntent().setup()
        try {
            val viewer = controller.get()
            assertFalse(viewer.isMediaTransitionRunning())
            assertBackground(viewer.window.decorView, Color.BLACK)
            assertBackground(viewer.findViewById(R.id.fragment_holder), Color.BLACK)
            assertBackground(viewer.findViewById(R.id.view_pager), Color.BLACK)
        } finally {
            controller.pause().stop().destroy()
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test fun ordinaryGalleryWindowStillReceivesTheClassicPageColor() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.config.oxygenThemeActivated = true
        listOf("light" to 0xFFFAFAFA.toInt(), "dark" to 0xFF212121.toInt()).forEach { (mode, color) ->
            app.config.oxygenAppearanceMode = mode
            val controller = Robolectric.buildActivity(SimpleActivity::class.java).create().start()
            try {
                val decor = controller.get().window.decorView
                decor.setBackgroundColor(Color.TRANSPARENT)
                controller.resume().postResume()
                assertBackground(decor, color, mode)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    private fun viewerWithTransitionIntent(): ActivityController<ViewPagerActivity> {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.config.oxygenThemeActivated = true
        app.config.oxygenAppearanceMode = "light"
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val path = "/storage/emulated/0/Gallery/entry.jpg"
        val intent = Intent(app, ViewPagerActivity::class.java)
            .putExtra(PATH, path)
            .putExtra(PhotoViewerTransition.EXTRA_PATH, path)
        return Robolectric.buildActivity(ViewPagerActivity::class.java, intent)
    }

    private fun assertBackground(view: View, color: Int, stage: String = "") {
        val background = view.background as ColorDrawable
        assertEquals(stage, color, background.color)
        assertEquals(stage, Color.alpha(color), background.alpha)
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

    @Test fun allMediaNeighborPublicationCanCreateTheSelectedPageDuringPostponedEntry() {
        val fixture = EntryFixture()
        val selected = Medium().apply { path = fixture.path; name = "entry.jpg"; type = TYPE_IMAGES }
        val neighbor = selected.copy(path = "/storage/emulated/0/Gallery/neighbor.jpg", name = "neighbor.jpg")
        fixture.set("mPath", fixture.path)
        fixture.call("setupOptionsMenu")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        fixture.set("mMediaFiles", arrayListOf(selected))
        androidx.viewpager.widget.ViewPager::class.java.getDeclaredField("mFirstLayout")
            .apply { isAccessible = true }.setBoolean(fixture.binding.viewPager, true)
        fixture.call("publishAdjacent", arrayOf(listOf(selected, neighbor)))
        (fixture.field("mediaPublicationTask") as java.util.concurrent.Future<*>).get(10, java.util.concurrent.TimeUnit.SECONDS)
        val main = Shadows.shadowOf(Looper.getMainLooper())
        var tasks = 0
        while (fixture.binding.viewPager.adapter == null && tasks++ < 30) main.runOneTask()
        assertNull("initial page publication cannot be held behind entry completion", fixture.field("transitionPendingAdjacentMedia"))
        assertNotNull("the selected page adapter exists while entry is still waiting", fixture.binding.viewPager.adapter)
        assertEquals(Stage.WAITING, fixture.entry.stage)
    }

    @Test fun identicalSelectedSnapshotStillBuildsTheFirstAdapter() {
        val fixture = EntryFixture()
        val selected = Medium().apply { path = fixture.path; name = "entry.jpg"; type = TYPE_IMAGES }
        fixture.set("mPath", fixture.path)
        fixture.call("setupOptionsMenu")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        fixture.set("mMediaFiles", arrayListOf(selected))
        fixture.set("publishedFolderRevision", FolderContentRevision.current())
        fixture.set("publishedPhotoRevision", PhotoContentRevision.current())
        androidx.viewpager.widget.ViewPager::class.java.getDeclaredField("mFirstLayout")
            .apply { isAccessible = true }.setBoolean(fixture.binding.viewPager, true)
        fixture.call("gotMedia", arrayOf(arrayListOf<org.fossify.gallery.models.ThumbnailItem>(selected), false, true, false))
        (fixture.field("mediaPublicationTask") as java.util.concurrent.Future<*>).get(10, java.util.concurrent.TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
        assertNotNull("unchanged catalog still needs its first rendered page", fixture.binding.viewPager.adapter)
    }

    @Test fun missingRendererFallbackKeepsTheSessionWaitingUntilLoadFailureIsReported() {
        val fixture = EntryFixture()
        fixture.entry.release()
        fixture.entry.map()
        fixture.call("startEntryFallback", arrayOf(fixture.entry, "renderer_missing"))
        fixture.assertFallbackLoading()
        val photo = fixture.setPhoto()
        fixture.activity.entryMediaLoadFailed(fixture.path, photo.page)
        assertEquals(Stage.COMPLETE, fixture.entry.stage)
        assertFalse(fixture.activity.isMediaTransitionRunning())
        assertFalse(fixture.binding.viewPager.transitionInputLocked)
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
            fixture.binding.viewPager.viewTreeObserver.dispatchOnPreDraw()
            (fixture.field("entryFallbackAnimator") as android.animation.ValueAnimator).end()
            assertEquals(View.GONE, fixture.binding.viewerEntryStatus.visibility)
        }
        fixture.runCallback("entryWatchdog")
        assertEquals(Stage.COMPLETE, entry.stage)
    }

    @Test fun selectedRendererWaitsForPredrawAndSharedCaptureBeforeOwningPixels() {
        val fixture = EntryFixture()
        fixture.call("setSharedTransitionCanvasTransparent")
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
        assertBackground(fixture.activity.window.decorView, Color.TRANSPARENT)
        assertEquals(.4f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(.4f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, false))
        assertEquals(Stage.COMPLETE, entry.stage)
        assertTrue(fixture.field("photoSharedElementMapped") as Boolean)
        assertFalse(fixture.binding.viewPager.transitionInputLocked)
        assertNull(proxy.parent)
        assertBackground(fixture.activity.window.decorView, Color.BLACK)
        assertBackground(fixture.binding.root, Color.BLACK)
        assertBackground(fixture.binding.viewPager, Color.BLACK)
        assertEquals(View.GONE, fixture.binding.viewerTransitionScrim.visibility)
        assertEquals(1f, image.alpha, 0f)
        assertEquals(View.GONE, fixture.binding.viewerEntryStatus.visibility)

        fixture.beginReturn()
        fixture.set("returnSystemBarProgress", .4f)
        fixture.call("renderSharedReturnProgress", args = arrayOf(.5f))
        fixture.call("renderPhotoTransitionProgress", args = arrayOf(entry, 1f))
        fixture.call("onSharedEntryTerminal", args = arrayOf(entry, true))
        assertEquals(.5f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(.2f, fixture.field("viewerSystemBarProgress") as Float, 0f)
        assertTrue(fixture.activity.isMediaTransitionRunning())
    }

    @Test fun ordinaryCloseCoversGridUntilThePlatformReturnStarts() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.set("photoSharedElementMapped", true)
        fixture.call("renderSharedViewerLayers", args = arrayOf(1f))
        fixture.call("restoreBlackViewerCanvas")

        fixture.activity.finishAfterTransition()

        assertTrue(fixture.call("getFinishAfterSharedTransitionInProgress") as Boolean)
        assertNotNull("mapped entry keeps the shared-element return", fixture.field("sharedReturnView"))
        assertBackground(fixture.activity.window.decorView, Color.TRANSPARENT)
        assertEquals(View.VISIBLE, fixture.binding.viewerTransitionScrim.visibility)
        assertEquals(1f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        fixture.call("renderSharedReturnProgress", args = arrayOf(.5f))
        assertEquals(.5f, fixture.binding.viewerTransitionScrim.alpha, 0f)
    }

    @Test fun continuousDragRevealsGridWithoutChangingOpacityWhenReleased() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        fixture.finishEntry()
        fixture.set("photoSharedElementMapped", true)
        val scrim = fixture.binding.viewerTransitionScrim

        fixture.activity.mediaDismissProgress(.4f, 0f, 150f)

        assertBackground(fixture.activity.window.decorView, Color.TRANSPARENT)
        assertEquals(.6f, scrim.alpha, 0f)
        assertEquals(.6f, fixture.field("viewerSystemBarProgress") as Float, 0f)
        assertEquals(.6f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        val surface = fixture.field("interactiveDismissSurface") as View
        assertEquals(150f, surface.translationY, .01f)

        fixture.activity.mediaDismissSettled(dismiss = true)

        assertTrue(fixture.call("getFinishAfterSharedTransitionInProgress") as Boolean)
        assertSame(surface, fixture.field("sharedReturnView"))
        assertSame(photo.binding.root, surface.parent)
        assertEquals(150f, surface.translationY, .01f)
        assertEquals(.6f, scrim.alpha, 0f)
        assertEquals(.6f, fixture.field("viewerSystemBarProgress") as Float, 0f)
        assertEquals(.6f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        fixture.call("renderSharedReturnProgress", args = arrayOf(.5f))
        assertEquals(.3f, scrim.alpha, 0f)
        // The committed return freezes the system bar appearance: the Grid the Viewer
        // returns to re-applies its own bars underneath the animating window, so tracking
        // the return clock here produced two writers for one surface (and an icon
        // polarity flip at the contrast crossing). Layers still follow the clock.
        assertEquals(.6f, fixture.field("viewerSystemBarProgress") as Float, 0f)
        assertEquals(.3f, fixture.binding.mediumViewerAppbar.alpha, 0f)
    }

    @Test fun resumedSystemBackGestureConsumesOnlyTheRemainingIntervalAndCancelsToFullViewer() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.set("photoSharedElementMapped", true)

        assertTrue(fixture.activity.mediaDismissProgress(.6f, 0f, 300f))
        // A cancelled drag is still settling when the platform starts a new gesture.
        fixture.activity.mediaDismissSettled(dismiss = false)
        val surface = fixture.field("interactiveDismissSurface") as View
        assertTrue(fixture.call("beginSystemBackGesture") as Boolean)
        assertEquals(.6f, fixture.field("systemBackResumeProgress") as Float, 0f)

        fixture.call("renderSystemBackProgress", arrayOf(0f))
        assertEquals(1f - .34f * .6f, surface.scaleX, .001f)

        // Half of the new gesture consumes half of the remaining 0.4 interval.
        // base + progress would have clamped to 1.0 and collapsed the image early.
        fixture.call("renderSystemBackProgress", arrayOf(.5f))
        assertEquals(1f - .34f * .8f, surface.scaleX, .001f)
        assertTrue(surface.scaleX > 1f - .34f)

        fixture.call("renderSystemBackProgress", arrayOf(1f))
        assertEquals(1f - .34f, surface.scaleX, .001f)

        fixture.call("cancelSystemBackGesture")
        val settle = fixture.field("interactiveDismissSettleAnimator") as android.animation.ValueAnimator
        settle.end()
        assertNull(fixture.field("interactiveDismissSurface"))
        assertEquals(MediaTransitionSessionCoordinator.State.VIEWING,
            (fixture.field("transitionSession") as MediaTransitionSessionCoordinator).state)
        assertBackground(fixture.activity.window.decorView, Color.BLACK)
        assertEquals(1f, fixture.field("viewerSystemBarProgress") as Float, 0f)
        assertEquals(View.GONE, fixture.binding.viewerTransitionScrim.visibility)
        assertEquals(1f, surface.scaleX, 0f)
        assertEquals(1f, surface.scaleY, 0f)
        assertEquals(0f, surface.translationX, 0f)
        assertEquals(0f, surface.translationY, 0f)
    }

    @Test fun terminalCommitIsNotAppliedTwiceAfterTheFinalProgressFrame() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val proxy = photo.page.sharedElementViewFor(fixture.path)!!
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        fixture.call("takeOverReturnChrome", args = arrayOf(photo.page))
        fixture.call("renderSharedReturnLayers", args = arrayOf(1f))
        val clock = fixture.field("sharedReturnVisualClock") as ViewerReturnVisualClock

        fixture.call("renderSharedReturnProgress", args = arrayOf(1f))
        assertEquals(0f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(0f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        assertFalse("the last progress frame already landed", clock.terminalApplied)

        fixture.call("onSharedReturnTerminal", args = arrayOf(false))
        assertTrue(clock.terminalApplied)
        assertEquals(0f, clock.remainder, 0f)
        fixture.call("onSharedReturnTerminal", args = arrayOf(false))
        fixture.call("renderSharedReturnProgress", args = arrayOf(0f))
        assertEquals(0f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(0f, fixture.binding.mediumViewerAppbar.alpha, 0f)
    }

    @Test fun visibleGridOwnsTheToolbarDuringDragAndCancelRestoresViewerChrome() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.attachGridToolbar()
        val toolbar = fixture.binding.mediumViewerAppbar
        toolbar.alpha = .8f

        assertTrue(fixture.activity.mediaDismissProgress(.4f, 0f, 150f))
        assertEquals(.6f, fixture.binding.viewerTransitionScrim.alpha, .001f)
        assertEquals(0f, toolbar.alpha, 0f)

        fixture.activity.mediaDismissSettled(dismiss = false)
        (fixture.field("interactiveDismissSettleAnimator") as android.animation.ValueAnimator).end()
        assertEquals(.8f, toolbar.alpha, 0f)
    }

    @Test fun visibleGridOwnsTheToolbarFromTheFirstExposedReturnFrame() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.attachGridToolbar()
        fixture.set("photoSharedElementMapped", true)
        fixture.call("renderSharedViewerLayers", args = arrayOf(1f))

        fixture.activity.finishAfterTransition()
        assertEquals(1f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        fixture.call("renderSharedReturnProgress", args = arrayOf(.1f))
        assertEquals(.9f, fixture.binding.viewerTransitionScrim.alpha, .001f)
        assertEquals(0f, fixture.binding.mediumViewerAppbar.alpha, 0f)
    }

    @Test fun replacingTheRendererDuringDragRetiresTheOldProxyBeforeReturn() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.set("photoSharedElementMapped", true)
        assertTrue(fixture.activity.mediaDismissProgress(.4f, 0f, 150f))
        val oldProxy = fixture.field("interactiveDismissSurface") as View
        val newPhoto = fixture.setPhoto()

        fixture.activity.mediaDismissSettled(dismiss = true)

        val returnProxy = fixture.field("sharedReturnView") as View
        assertNull(oldProxy.parent)
        assertEquals(View.LAYER_TYPE_NONE, oldProxy.layerType)
        assertSame(newPhoto.binding.root, returnProxy.parent)
        assertEquals(0f, returnProxy.translationY, 0f)
        assertEquals(1f, fixture.binding.viewerTransitionScrim.alpha, 0f)
    }

    @Test fun interruptedDragContinuesFromItsCurrentOpacityAndCancelRestoresViewer() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()

        fixture.activity.mediaDismissProgress(.4f, 0f, 150f)
        fixture.activity.mediaDismissSettled(dismiss = false)
        assertEquals(.4f, fixture.activity.mediaDismissGestureBegin()!!, 0f)
        fixture.activity.mediaDismissProgress(.45f, 0f, 25f)
        assertEquals(.55f, fixture.binding.viewerTransitionScrim.alpha, .001f)

        fixture.activity.mediaDismissSettled(dismiss = false)
        val settle = fixture.field("interactiveDismissSettleAnimator") as android.animation.ValueAnimator
        settle.end()
        assertEquals(View.GONE, fixture.binding.viewerTransitionScrim.visibility)
        assertBackground(fixture.activity.window.decorView, Color.BLACK)
        assertEquals(1f, fixture.field("viewerSystemBarProgress") as Float, 0f)
    }

    @Test fun sharedReturnFadesFromTheChromeAlreadyOnScreen() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val toolbar = fixture.binding.mediumViewerAppbar
        val actions = fixture.binding.bottomActions.root
        toolbar.alpha = .4f
        actions.alpha = 0f
        fixture.call("takeOverReturnChrome", args = arrayOf(photo.page))
        fixture.call("renderSharedReturnLayers", args = arrayOf(1f))
        assertEquals(.4f, toolbar.alpha, 0f)
        assertEquals(0f, actions.alpha, 0f)
        fixture.call("renderSharedReturnLayers", args = arrayOf(.5f))
        assertEquals(.2f, toolbar.alpha, 0f)
        assertEquals(0f, actions.alpha, 0f)
        fixture.call("renderSharedReturnLayers", args = arrayOf(0f))
        assertEquals(0f, toolbar.alpha, 0f)
    }

    @Test fun viewerEnterCallbackMapsCurrentPhotoForPlatformReturn() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val proxy = photo.page.sharedElementViewFor(fixture.path)!!
        fixture.set("photoSharedElementPath", fixture.path)
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        val name = PhotoViewerTransition.ELEMENT_NAME
        val elements = linkedMapOf<String, View>()

        val callbackField = android.app.Activity::class.java.getDeclaredField("mEnterTransitionListener")
            .apply { isAccessible = true }
        val entryCallback = callbackField.get(fixture.activity) as android.app.SharedElementCallback
        fixture.installReturnCallback()
        val returnCallback = callbackField.get(fixture.activity) as android.app.SharedElementCallback
        entryCallback.onMapSharedElements(mutableListOf(name), elements)
        assertTrue("A late entry callback must never map the return proxy", elements.isEmpty())
        returnCallback.onMapSharedElements(mutableListOf(name), elements)
        assertSame(proxy, elements[name])
    }

    @Test fun toolbarAndSystemBackIgnoreFurtherCloseRequestsDuringReturn() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val proxy = photo.page.sharedElementViewFor(fixture.path)!!
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        fixture.call("setupOptionsMenu")
        val toolbar = fixture.binding.mediumViewerToolbar
        val navigation = (0 until toolbar.childCount).map(toolbar::getChildAt)
            .filterIsInstance<android.widget.ImageButton>().single()
        assertTrue(navigation.performClick())
        fixture.activity.onBackPressed()
        fixture.activity.finishAfterTransition()
        assertFalse(fixture.activity.isFinishing)
        assertFalse(fixture.field("sharedReturnFinishRequested") as Boolean)
        assertNotNull(proxy.parent)
    }

    @Test fun sharedReturnAnimatorDrivesLayersAndLateCallbacksKeepTheFinalFrame() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val proxy = photo.page.sharedElementViewFor(fixture.path)!!
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        val transition = OxygenClassicActivityMotion.mediaTransition(PhotoViewerTransition.DURATION_MS,
            onProgress = { fixture.call("renderSharedReturnProgress", arrayOf(it)) }) as android.transition.TransitionSet
        val progress = transition.getTransitionAt(transition.transitionCount - 1)
        val start = android.transition.TransitionValues().apply { view = proxy }.also(progress::captureStartValues)
        val end = android.transition.TransitionValues().apply { view = proxy }.also(progress::captureEndValues)
        val animator = progress.createAnimator(fixture.binding.root, start, end) as android.animation.ValueAnimator
        animator.start()
        animator.setCurrentFraction(.5f)
        val mid = fixture.binding.viewerTransitionScrim.alpha
        assertTrue(mid > 0f && mid < 1f)
        animator.setCurrentFraction(.8f)
        val last = fixture.binding.viewerTransitionScrim.alpha
        assertTrue(last < mid)
        fixture.call("onSharedReturnTerminal", arrayOf(true))
        fixture.call("onSharedReturnTerminal", arrayOf(false))
        animator.end()
        assertEquals(last, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertNotNull(proxy.parent)
    }

    @Test fun normalReturnTerminalClearsViewerLayersButRetainsMediaUntilPlatformFinish() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val renderer = photo.binding.gesturesView
        val drawable = renderer.drawable
        val proxy = photo.page.sharedElementViewFor(fixture.path) as ImageView
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        photo.binding.photoDetails.apply { visibility = View.VISIBLE; alpha = .65f }
        fixture.call("takeOverReturnChrome", args = arrayOf(photo.page))
        fixture.call("setSharedTransitionCanvasTransparent")
        fixture.call("renderSharedReturnProgress", args = arrayOf(.55f))
        fixture.call("onSharedReturnTerminal", args = arrayOf(false))
        assertEquals(0f, fixture.binding.viewerTransitionScrim.alpha, 0f)
        assertEquals(0f, fixture.binding.mediumViewerAppbar.alpha, 0f)
        assertEquals(0f, photo.binding.photoDetails.alpha, 0f)
        assertBackground(fixture.activity.window.decorView, Color.TRANSPARENT)
        assertSame(photo.binding.root, proxy.parent)
        assertSame(drawable, renderer.drawable)
        assertEquals(0f, renderer.alpha, 0f)
        assertFalse(fixture.activity.isFinishing)
        fixture.activity.finish()
        fixture.activity.finish()
        assertTrue(fixture.activity.isFinishing)
        assertSame(photo.binding.root, proxy.parent)
    }

    @Test fun platformFinishWithoutAnimatorRestoresViewerBeforeFade() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val proxy = photo.page.sharedElementViewFor(fixture.path)!!
        fixture.set("sharedReturnView", proxy)
        fixture.set("sharedReturnPage", photo.page)
        fixture.beginReturn()
        // This fixture supplies a rendered fragment without starting FragmentManager.
        fixture.binding.viewPager.visibility = View.GONE
        fixture.activity.finish()
        fixture.activity.finish()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fixture.activity.isFinishing)
        assertBackground(fixture.binding.root, Color.BLACK)
        assertEquals(1f, photo.binding.gesturesView.alpha, 0f)
        assertNull(proxy.parent)
    }

    @Test fun backWithSystemAnimationsDisabledReturnsSelectionImmediately() {
        val fixture = EntryFixture()
        fixture.setPhoto()
        fixture.finishEntry()
        fixture.set("photoSharedElementMapped", true)
        val resolver = fixture.activity.contentResolver
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            fixture.activity.finishAfterTransition()
            assertTrue(fixture.activity.isFinishing)
            val result = Shadows.shadowOf(fixture.activity).resultIntent
            assertEquals(fixture.path, result.getStringExtra(PhotoViewerTransition.EXTRA_RETURN_PATH))
            assertNull(fixture.field("sharedReturnView"))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        }
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
            val viewer = Robolectric.buildActivity(ViewPagerActivity::class.java).get()
            viewer.intent.putExtra(PhotoViewerTransition.EXTRA_ENTRY_ID, request)
            viewer.window.decorView.setBackgroundColor(Color.TRANSPARENT)
            viewer.traceEntryCanvas("create")
            viewer.traceEntryCanvas("same")
            viewer.window.decorView.setBackgroundColor(Color.BLACK)
            viewer.traceEntryCanvas("resume")
            val returnFixture = EntryFixture()
            returnFixture.set("viewerReturnId", request)
            returnFixture.set("returnSystemBarProgress", .8f)
            returnFixture.call("renderSharedReturnLayers", args = arrayOf(1f))
            returnFixture.beginReturn()
            returnFixture.call("renderSharedReturnProgress", args = arrayOf(0f))
            returnFixture.call("renderSharedReturnProgress", args = arrayOf(.5f))
            returnFixture.call("renderSharedReturnProgress", args = arrayOf(.75f))
            var exported = ""
            GalleryDiagnosticLogger.snapshot { text, _ -> exported = text }
            val io = GalleryDiagnosticLogger::class.java.getDeclaredField("io").apply { isAccessible = true }
                .get(GalleryDiagnosticLogger) as java.util.concurrent.ExecutorService
            io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(exported.contains("module=ViewerEntry event=entry_completed"))
            assertTrue(exported.contains("request=$request"))
            assertFalse(exported.contains("should_not_log"))
            val canvas = exported.lineSequence().filter { "event=entry_canvas_changed" in it }.toList()
            assertEquals(2, canvas.size)
            assertTrue(canvas.any { "checkpoint=create" in it && "window=00000000/0" in it })
            assertTrue(canvas.any { "checkpoint=resume" in it && "window=ff000000/255" in it })
            val fade = exported.lineSequence().single { "event=viewer_return_scrim_fade " in it }
            assertTrue("request=$request " in fade && "fromAlpha=1.0" in fade && "alpha=0.5" in fade)
            assertFalse(exported.contains("/storage"))
            assertFalse(exported.contains("mediaPath="))
        } finally {
            GalleryDiagnosticLogger.setEnabled(app, false)
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

    @Test fun staticPhotoLoadReadsTheHeaderBeforeStartingTheRenderer() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()

        photo.call("loadImage")

        // The header read owns the first generation. Starting Glide on this frame
        // would bump imageRequestGeneration and make the header callback stale, which
        // also leaves staticGlideSignature null when prepareStaticPhotoEntry runs.
        assertEquals(1L, photo.field("imageRequestGeneration"))
        assertEquals(-1L, photo.field("staticGlideGeneration"))
        // Invalidate the pending header callback so it never starts Glide after the test.
        photo.call("beginImageRequest")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun missingHeaderSignatureRejectsTheStaticPhotoHandoff() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val source = staticPhotoSource(fixture, photo)
        val rejected = ArrayList<String>()
        photo.set("staticPhotoHeader", null)
        photo.set("staticGlideSignature", null)
        photo.set("staticGlideGeneration", 0L)

        assertNull(photo.page.prepareStaticPhotoEntry(fixture.path, source) { rejected += it })
        assertEquals(listOf("content_version"), rejected)
    }

    @Test fun staleRendererGenerationRejectsTheStaticPhotoHandoff() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val source = staticPhotoSource(fixture, photo)
        val rejected = ArrayList<String>()
        photo.set("staticGlideSignature", source.signature)
        photo.set("staticGlideGeneration", -1L)

        assertNull(photo.page.prepareStaticPhotoEntry(fixture.path, source) { rejected += it })
        assertEquals(listOf("glide_generation"), rejected)
    }

    @Test fun alignedHeaderSignatureAndGenerationAcceptTheStaticPhotoHandoff() {
        val fixture = EntryFixture()
        val photo = fixture.setPhoto()
        val source = staticPhotoSource(fixture, photo)
        val rejected = ArrayList<String>()
        photo.set("staticGlideSignature", source.signature)
        photo.set("staticGlideGeneration", 0L)

        val proxy = photo.page.prepareStaticPhotoEntry(fixture.path, source) { rejected += it }

        assertEquals(rejected.toString(), emptyList<String>(), rejected)
        assertNotNull(proxy)
    }

    private fun staticPhotoSource(fixture: EntryFixture, photo: EntryFixture.TestPhoto): StaticPhotoSource {
        val target = requireNotNull(StaticPhotoViewGeometry.read(photo.binding.gesturesView))
        val width = target.pixels.width.roundToInt()
        val height = target.pixels.height.roundToInt()
        val invalidation = ViewerContentInvalidationRegistry.current(fixture.path)
        val header = StaticPhotoHeader(width, height, 1, jpeg = true,
            fileVersion = StaticPhotoFileVersion(10L, 10L, invalidation))
        photo.set("staticPhotoHeader", fixture.path to header)
        return StaticPhotoSource(
            path = fixture.path,
            signature = "grid-signature",
            adapterVersion = 1L,
            thumbnail = StaticPhotoThumbnail(width, height, width, height, cropped = false, radius = 0),
            view = target,
            size = 10L,
            modified = 10L,
            invalidation = invalidation,
        )
    }

    private class EntryFixture(
        val controller: ActivityController<ViewPagerActivity> = Robolectric.buildActivity(ViewPagerActivity::class.java),
    ) {
        val activity = controller.get()
        val path = "/storage/emulated/0/Gallery/fixture-entry.jpg"
        val binding = call("getBinding") as ActivityMediumBinding
        private val session get() = field("transitionSession") as MediaTransitionSessionCoordinator
        val entry get() = session.entryGate

        init {
            activity.intent.putExtra(PhotoViewerTransition.EXTRA_PATH, path)
            if (field("transitionSession") == null) call("configureMediaTransitionSession")
            if (binding.root.parent == null) activity.window.setContentView(binding.root)
            controller.visible()
            BaseViewerActivity::class.java.getDeclaredMethod("refreshViewerSystemBars").apply { isAccessible = true }.invoke(activity)
            set("photoSharedElementPath", path)
            set("viewerSystemBarProgress", 0f)
        }

        private val gridToolbar = object : MediaTransitionSessionCoordinator.GridPreview {
            override fun show(token: MediaTransitionSessionCoordinator.Token, path: String) = Unit
            override fun release(token: MediaTransitionSessionCoordinator.Token) = Unit
            override fun toolbarBottomOnScreen(token: MediaTransitionSessionCoordinator.Token) = 100
        }

        fun attachGridToolbar() = session.attachGrid(gridToolbar)

        fun finishEntry() {
            if (!session.entryActive) return
            if (entry.awaitingRelease) check(entry.release())
            check(entry.startFallback())
            check(session.entryFallback(session.entry, "fixture"))
            call("finishMediaEntryTransition", arrayOf(entry))
        }

        fun beginReturn() {
            finishEntry()
            val operation = requireNotNull(session.beginReturn())
            val page = field("sharedReturnPage") as? ViewPagerFragment
            if (page != null) call("ownReturnSurface", arrayOf(operation, page))
        }

        fun installReturnCallback() {
            call("installViewerReturnCallback", arrayOf(requireNotNull(session.returnOperation)))
        }

        data class TestPhoto(val page: PhotoFragment, val binding: PagerPhotoItemBinding) {
            fun call(name: String, vararg args: Any) =
                page.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
                    .apply { isAccessible = true }.invoke(page, *args)

            fun set(name: String, value: Any?) =
                page.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(page, value)

            fun field(name: String): Any? =
                page.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(page)
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

        fun call(name: String, args: Array<Any> = emptyArray()): Any? {
            val parameters = if (name == "renderSharedReturnProgress" || name == "onSharedReturnTerminal") {
                arrayOf(requireNotNull(session.returnOperation), *args)
            } else args
            return activity.javaClass.declaredMethods.single { it.name == name && it.parameterCount == parameters.size }
                .apply { isAccessible = true }.invoke(activity, *parameters)
        }

        fun assertFallbackLoading() {
            assertEquals(Stage.FALLBACK, entry.stage)
            assertTrue(activity.isMediaTransitionRunning())
            assertTrue(field("entryWaitingForRenderer") as Boolean)
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
