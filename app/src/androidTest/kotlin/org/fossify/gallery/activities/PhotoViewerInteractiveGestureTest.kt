package org.fossify.gallery.activities

import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.gallery.R
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.views.ProgressivePhotoViewPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises the real PhotoFragment touch listener and viewer rendering on-device. */
@RunWith(AndroidJUnit4::class)
class PhotoViewerInteractiveGestureTest {
    private data class Frame(
        val image: ImageView,
        val imageAlpha: Float,
        val scaleY: Float,
        val translationY: Float,
    )

    private class GestureCheckpoint {
        val reached = CountDownLatch(1)
        val resume = CountDownLatch(1)
    }

    private data class SwipeRun(val thread: Thread, val deviceInjectionWorked: AtomicBoolean)

    @Test
    fun folderCardEntersAlbumAndReturnsToTheSameCoverOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryAlbumTransitionTest-${UUID.randomUUID()}"
        val fixture = createFixture(context, album)
        val fixtureDirectory = java.io.File(fixture.second).parent
            ?: error("fixture image has no parent directory")
        val wasLastViewAll = context.config.lastMainViewAll
        var home: MainActivity? = null
        var media: MediaActivity? = null
        try {
            context.config.lastMainViewAll = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            launch.putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
            val homeMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            instrumentation.startActivitySync(launch)
            home = instrumentation.waitForMonitorWithTimeout(homeMonitor, 15_000) as? MainActivity
                ?: error("Gallery did not finish launching MainActivity")
            instrumentation.removeMonitor(homeMonitor)
            val homeActivity = home!!

            val folderCard = awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory)
            val cover = folderCard.findViewById<ImageView>(R.id.dir_thumbnail)
            assertNotNull("home album card should have its decoded cover", cover.drawable)
            assertEquals("home card cover should be bound to the fixture album",
                fixtureDirectory, cover.getTag(R.id.directory_bound_path))

            val monitor = instrumentation.addMonitor(MediaActivity::class.java.name, null, false)
            instrumentation.runOnMainSync {
                check(folderCard.performClick()) { "the album card did not accept a click" }
            }
            media = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? MediaActivity
                ?: error("clicking the home album card did not open MediaActivity")
            instrumentation.removeMonitor(monitor)
            val mediaActivity = media!!

            assertTrue("the album launch should use the shared cover transition",
                mediaActivity.intent.getBooleanExtra(
                    org.fossify.gallery.helpers.AlbumPageTransition.EXTRA_ACTIVE, false
                ))
            assertEquals("the transition should carry the matching folder cover path", fixture.second,
                mediaActivity.intent.getStringExtra(
                    org.fossify.gallery.helpers.AlbumPageTransition.EXTRA_COVER_PATH
                ))
            awaitSourceThumbnail(instrumentation, mediaActivity, fixture.second)
            val mappedField = MediaActivity::class.java.getDeclaredField("albumTransitionMapped")
                .apply { isAccessible = true }
            val mappingDeadline = SystemClock.uptimeMillis() + 2_000
            while (!mappedField.getBoolean(mediaActivity) && SystemClock.uptimeMillis() < mappingDeadline) {
                SystemClock.sleep(16)
            }
            assertTrue("the decoded album item should map to the shared cover", mappedField.getBoolean(mediaActivity))

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val returnDeadline = SystemClock.uptimeMillis() + 5_000
            var returnedCard: View? = null
            while (SystemClock.uptimeMillis() < returnDeadline && returnedCard == null) {
                returnedCard = runCatching {
                    awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory, 300)
                }.getOrNull()
                if (returnedCard == null) SystemClock.sleep(50)
            }
            assertNotNull("system Back should return to the same visible album card", returnedCard)
            assertTrue("the returned album cover should still be decoded",
                returnedCard!!.findViewById<ImageView>(R.id.dir_thumbnail).drawable != null)
            assertTrue("folder return should leave the original home Activity alive", !homeActivity.isFinishing)
        } finally {
            runCatching { media?.finish() }
            runCatching { home?.finish() }
            runCatching { context.contentResolver.delete(fixture.first, null, null) }
            context.config.lastMainViewAll = wasLastViewAll
        }
    }

    @Test
    fun downwardDragReversesRestoresAndDismissesFromTheCurrentPhotoFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val fixtureAlbum = "${Environment.DIRECTORY_PICTURES}/GalleryInteractiveGestureTest-${UUID.randomUUID()}"
        val fixture = createFixture(context, fixtureAlbum)
        val neighborFixture = createFixture(context, fixtureAlbum)
        val wasZoomingEnabled = context.config.allowZoomingImages
        val wasShowingAllMedia = context.config.showAll
        try {
            // Keep this UI scenario on GestureImageView so asynchronous tile setup
            // does not obscure touch arbitration for the core transition.
            context.config.allowZoomingImages = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            instrumentation.startActivitySync(launch)
            SystemClock.sleep(1_000)
            // MainActivity restores its remembered section on resume; pin the test
            // source to the temporary folder only after that startup restoration.
            context.config.showAll = false
            instrumentation.uiAutomation.executeShellCommand(
                "dumpsys gfxinfo ${context.packageName} reset"
            ).close()
            val beforeViewer = instrumentation.uiAutomation.takeScreenshot()
                ?: error("Could not capture the source Gallery screen")
            saveScreenshot(context, "gallery-source-screen.png", beforeViewer)

            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, java.io.File(fixture.second).parent)
            }
            val source = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            val sourceThumbnail = awaitSourceThumbnail(instrumentation, source, fixture.second)
            SystemClock.sleep(300)
            val sourceGrid = instrumentation.uiAutomation.takeScreenshot()
                ?: error("Could not capture the source album grid")
            val thumbnailLocation = IntArray(2)
            instrumentation.runOnMainSync { sourceThumbnail.getLocationOnScreen(thumbnailLocation) }
            val thumbnailPixel = sourceGrid.getPixel(
                thumbnailLocation[0] + sourceThumbnail.width / 2,
                thumbnailLocation[1] + sourceThumbnail.height / 2,
            )
            assertTrue("the source album screenshot should contain the decoded fixture thumbnail",
                Color.green(thumbnailPixel) > Color.red(thumbnailPixel) + 40)
            saveScreenshot(context, "gallery-source-grid.png", sourceGrid)

            val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            val openViewer = MediaActivity::class.java.getDeclaredMethod("openInViewPager", String::class.java)
                .apply { isAccessible = true }
            instrumentation.runOnMainSync { openViewer.invoke(source, fixture.second) }
            val viewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                ?: error("MediaActivity did not launch ViewPagerActivity")
            instrumentation.removeMonitor(monitor)
            val transitionSamples = awaitTransitionFrameSamples(instrumentation, viewer)
            val intermediateOpening = transitionSamples.filter { (progress, _) -> progress in 0.05f..0.95f }
            assertTrue("opening should expose multiple intermediate shared-element frames",
                intermediateOpening.size >= 2)
            assertTrue("opening bounds should grow between sampled frames",
                intermediateOpening.map { it.second.width() }.distinct().size >= 2)
            assertTrue("opening progress should advance monotonically",
                intermediateOpening.map { it.first }.zipWithNext().all { (a, b) -> b >= a })
            var initial = awaitFrame(instrumentation, viewer, requireProxy = false)
            instrumentation.uiAutomation.takeScreenshot()?.let {
                saveScreenshot(context, "gallery-opened-viewer.png", it)
                it.recycle()
            }
            // Drawable installation precedes PhotoFragment.imageRendered's posted
            // identity callback; wait for that handoff before attempting a proxy drag.
            SystemClock.sleep(500)
            instrumentation.uiAutomation.takeScreenshot()?.let {
                saveScreenshot(context, "gallery-fullscreen-viewer.png", it)
                it.recycle()
            }
            val progressFrameCount = ViewPagerActivity::class.java
                .getDeclaredField("photoTransitionProgressFrameCount").apply { isAccessible = true }
                .getInt(viewer)
            assertTrue("shared image bounds should drive opening scrim progress on the same transition clock",
                progressFrameCount > 0)
            var photoFragment = viewer.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                .first { it.isVisible && it.isResumed }
            val viewerMedia = viewer.javaClass.getDeclaredField("mMediaFiles").apply { isAccessible = true }
                .get(viewer) as List<*>
            val viewerPaths = viewerMedia.mapNotNull { medium ->
                runCatching { medium?.javaClass?.getMethod("getPath")?.invoke(medium) as? String }.getOrNull()
            }
            val viewerDirectory = viewer.javaClass.getDeclaredField("mDirectory").apply { isAccessible = true }
                .get(viewer) as String
            val viewerShowAll = viewer.javaClass.getDeclaredField("mShowAll").apply { isAccessible = true }
                .getBoolean(viewer)
            assertEquals("viewer should remain scoped to the source photo's folder",
                java.io.File(fixture.second).parent, viewerDirectory)
            assertTrue("folder handoff should not become Show All", !viewerShowAll)
            assertTrue("real Gallery launch should hand off the selected photo", fixture.second in viewerPaths)
            assertTrue("real Gallery launch should retain the adjacent album photo", neighborFixture.second in viewerPaths)
            assertEquals("test viewer catalog should contain only the generated album items", 2, viewerPaths.size)
            val currentPathMethod = viewer.javaClass.getDeclaredMethod("getCurrentPath").apply { isAccessible = true }
            val startingPath = currentPathMethod.invoke(viewer) as String
            assertEquals("viewer should start on the tapped item", fixture.second, startingPath)
            instrumentation.runOnMainSync {
                val sourceSurface = photoFragment.sharedElementViewFor(fixture.second)
                assertNotNull("selected photo should expose its shared content surface", sourceSurface)
                photoFragment.finishSharedTransition()
            }
            val pager = viewer.findViewById<ProgressivePhotoViewPager>(R.id.view_pager)
            val pagerReadyDeadline = SystemClock.uptimeMillis() + 5_000
            while ((pager.adapter?.count ?: 0) < 2 || pager.transitionInputLocked) {
                if (SystemClock.uptimeMillis() >= pagerReadyDeadline) break
                SystemClock.sleep(50)
            }
            assertEquals("both handoff photos should be published before horizontal paging", 2,
                pager.adapter?.count)
            assertTrue("entry transition should release horizontal touch input", !pager.transitionInputLocked)
            val startPosition = viewerPaths.indexOf(startingPath)
            val neighborPosition = viewerPaths.indexOf(neighborFixture.second)
            val swipeRight = neighborPosition < startPosition
            assertTrue("horizontal page input should be injected on the emulator",
                injectHorizontalSwipe(instrumentation, initial.image, swipeRight))
            fun awaitPageChange(): String {
                val deadline = SystemClock.uptimeMillis() + 1_500
                var livePath = currentPathMethod.invoke(viewer) as String
                while (livePath == startingPath && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50)
                    livePath = currentPathMethod.invoke(viewer) as String
                }
                return livePath
            }
            var currentPath = awaitPageChange()
            if (currentPath == startingPath) {
                assertTrue("reverse horizontal page input should also be injected",
                    injectHorizontalSwipe(instrumentation, initial.image, !swipeRight))
                currentPath = awaitPageChange()
            }
            assertEquals("horizontal paging should select the generated neighbor photo",
                neighborFixture.second, currentPath)
            // Legacy ViewPager exposes current item but not its scroll state;
            // allow the measured page settling animation to finish before the
            // next device-level gesture begins.
            SystemClock.sleep(700)
            photoFragment = viewer.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                .first { it.isVisible && it.isResumed }
            initial = awaitFrame(instrumentation, viewer, requireProxy = false)
            assertNotNull("source thumbnail must be decoded before the shared transition",
                sourceThumbnail.getTag(R.id.thumbnail_loaded_key))
            assertEquals("source thumbnail request must match its decoded media",
                sourceThumbnail.getTag(R.id.thumbnail_request_key),
                sourceThumbnail.getTag(R.id.thumbnail_loaded_key))
            val touchHandler = photoFragment.javaClass.getDeclaredMethod(
                "handlePhotoDismissTouch", View::class.java, MotionEvent::class.java
            ).apply { isAccessible = true }
            val checkpoints = List(3) { GestureCheckpoint() }
            val shortSwipe = startSwipe(instrumentation, photoFragment, touchHandler, initial.image,
                initial.image.width / 2f, initial.image.height / 2f,
                initial.image.height / 2f + 220f, 3_000, checkpoints)
            val downFrame = captureCheckpoint(instrumentation, viewer, context, checkpoints[0], 1)
            assertEquals(0f, downFrame.imageAlpha, 0.01f)
            assertTrue("drag should shrink the content proxy", downFrame.scaleY < 1f)
            assertTrue("drag should move the content proxy with the pointer", downFrame.translationY > 40f)
            val reversedFrame = captureCheckpoint(instrumentation, viewer, context, checkpoints[1], 2)
            assertTrue("upward finger reversal should move and enlarge the proxy immediately",
                reversedFrame.translationY < downFrame.translationY && reversedFrame.scaleY > downFrame.scaleY)
            val downwardAgainFrame = captureCheckpoint(instrumentation, viewer, context, checkpoints[2], 3)
            assertTrue("moving down again should resume the photo’s downward motion",
                downwardAgainFrame.translationY > reversedFrame.translationY &&
                    downwardAgainFrame.scaleY < reversedFrame.scaleY)
            shortSwipe.thread.join(5_000)
            assertTrue("short drag must use device-level touchscreen injection",
                shortSwipe.deviceInjectionWorked.get())
            captureFrameStats(instrumentation, context)
            SystemClock.sleep(700)
            val restored = awaitFrame(instrumentation, viewer, requireProxy = false)
            assertTrue("a short drag should restore the real image", restored.imageAlpha > 0.99f)
            assertTrue("viewer should remain open after a short drag", !viewer.isFinishing)

            // Keep frame profiling free from checkpoint screenshots and their
            // pauses; use a separate continuous device-injected drag.
            instrumentation.uiAutomation.executeShellCommand(
                "dumpsys gfxinfo ${context.packageName} reset"
            ).close()
            val profileSwipe = startSwipe(instrumentation, photoFragment, touchHandler, initial.image,
                initial.image.width / 2f, initial.image.height / 2f,
                initial.image.height / 2f + 220f, 900)
            profileSwipe.thread.join(10_000)
            assertTrue("profile drag input should finish on the emulator", !profileSwipe.thread.isAlive)
            assertTrue("profile drag should use device-level touchscreen injection",
                profileSwipe.deviceInjectionWorked.get())
            SystemClock.sleep(500)
            captureFrameStats(instrumentation, context)
            assertTrue("profile drag should settle back without closing the viewer", !viewer.isFinishing)

            val initialZoom = photoFragment.javaClass.getDeclaredField("mInitialZoom").apply {
                isAccessible = true
            }.getFloat(photoFragment)
            val currentZoomField = photoFragment.javaClass.getDeclaredField("mCurrentGestureViewZoom").apply {
                isAccessible = true
            }
            assertTrue("double-tap should be injected on the emulator", injectDoubleTap(instrumentation, initial.image))
            val zoomDeadline = SystemClock.uptimeMillis() + 3_000
            while (currentZoomField.getFloat(photoFragment) <= initialZoom + 0.1f &&
                SystemClock.uptimeMillis() < zoomDeadline) SystemClock.sleep(50)
            assertTrue("the real photo renderer should enter a zoomed state",
                currentZoomField.getFloat(photoFragment) > initialZoom + 0.1f)
            assertTrue("zoomed-image drag should be injected on the emulator",
                injectVerticalSwipe(instrumentation, initial.image, 180f))
            SystemClock.sleep(400)
            assertTrue("a downward drag on a zoomed image must not dismiss the viewer", !viewer.isFinishing)
            assertTrue("the renderer should remain zoomed after the attempted dismissal",
                currentZoomField.getFloat(photoFragment) > initialZoom + 0.1f)
            assertTrue("double-tap should restore the image before dismissal testing",
                injectDoubleTap(instrumentation, initial.image))
            val resetDeadline = SystemClock.uptimeMillis() + 3_000
            while (currentZoomField.getFloat(photoFragment) > initialZoom + 0.1f &&
                SystemClock.uptimeMillis() < resetDeadline) SystemClock.sleep(50)
            assertTrue("photo renderer should return to its fit zoom",
                currentZoomField.getFloat(photoFragment) <= initialZoom + 0.1f)
            SystemClock.sleep(400)
            assertTrue("viewer should be idle before the committed drag",
                !viewer.isMediaTransitionRunning())

            val dismissSwipe = startSwipe(instrumentation, photoFragment, touchHandler, initial.image,
                initial.image.width / 2f, initial.image.height / 2f,
                initial.image.height / 2f + 500f, 900)
            dismissSwipe.thread.join(10_000)
            assertTrue("dismiss input stream should finish before checking the settle", !dismissSwipe.thread.isAlive)
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (!viewer.isFinishing && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertTrue("dismiss drag must use device-level touchscreen injection",
                dismissSwipe.deviceInjectionWorked.get())
            assertTrue("a committed drag should dismiss the viewer", viewer.isFinishing)
            val dismissProgress = ViewPagerActivity::class.java
                .getDeclaredField("interactivePhotoProgress").apply { isAccessible = true }
                .getFloat(viewer)
            assertTrue(
                "a committed drag must hand its live transform to the shared return before an arbitrary settle endpoint",
                dismissProgress in 0.05f..0.99f,
            )
            val returnPath = viewer.javaClass.getDeclaredField("photoSharedElementPath").apply { isAccessible = true }
                .get(viewer) as? String
            assertEquals("dismissal should target the photo selected after horizontal paging",
                neighborFixture.second, returnPath)
            SystemClock.sleep(500)
            val returnedThumbnail = awaitSourceThumbnail(instrumentation, source, neighborFixture.second)
            assertEquals("return target thumbnail request should be fully loaded after dismissal",
                returnedThumbnail.getTag(R.id.thumbnail_request_key),
                returnedThumbnail.getTag(R.id.thumbnail_loaded_key))
            val returnedGrid = instrumentation.uiAutomation.takeScreenshot()
                ?: error("Could not capture the returned album grid")
            val returnedLocation = IntArray(2)
            instrumentation.runOnMainSync { returnedThumbnail.getLocationOnScreen(returnedLocation) }
            val returnedPixel = returnedGrid.getPixel(
                returnedLocation[0] + returnedThumbnail.width / 2,
                returnedLocation[1] + returnedThumbnail.height / 2,
            )
            assertTrue("selected thumbnail should be visible again after its shared return",
                Color.green(returnedPixel) > Color.red(returnedPixel) + 40)
            saveScreenshot(context, "gallery-dismissed-screen.png", returnedGrid)
            returnedGrid.recycle()

            // Reopen from the same real album thumbnail and exercise Android Back
            // while the shared transition may still be running.
            val returnMonitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { openViewer.invoke(source, fixture.second) }
            val reopenedViewer = instrumentation.waitForMonitorWithTimeout(returnMonitor, 10_000)
                as? ViewPagerActivity ?: error("Gallery did not reopen the selected photo")
            instrumentation.removeMonitor(returnMonitor)
            val viewerFocusDeadline = SystemClock.uptimeMillis() + 3_000
            while (!reopenedViewer.hasWindowFocus() && SystemClock.uptimeMillis() < viewerFocusDeadline) {
                SystemClock.sleep(25)
            }
            assertTrue("reopened viewer should receive Android Back", reopenedViewer.hasWindowFocus())
            assertTrue("reopened viewer should still be entering before Android Back",
                reopenedViewer.isMediaTransitionRunning())
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            val backDeadline = SystemClock.uptimeMillis() + 5_000
            while (!reopenedViewer.isFinishing && SystemClock.uptimeMillis() < backDeadline) {
                SystemClock.sleep(50)
            }
            assertTrue("Android Back during entry must close cleanly without a competing transition",
                reopenedViewer.isFinishing)
            val launchInProgress = MediaActivity::class.java.getDeclaredField("viewerLaunchInProgress")
                .apply { isAccessible = true }
            val sourceReadyDeadline = SystemClock.uptimeMillis() + 5_000
            while ((launchInProgress.getBoolean(source) || !source.hasWindowFocus()) &&
                SystemClock.uptimeMillis() < sourceReadyDeadline) SystemClock.sleep(50)
            assertTrue("source activity should be ready before reopening another viewer",
                !launchInProgress.getBoolean(source))

            // Feed the resumed source Activity a return result for a path that
            // is absent from its adapter. This must clear the pending target
            // and release the postponed transition rather than waiting for or
            // mapping to a different thumbnail.
            val missingReturn = Intent().putExtra(
                org.fossify.gallery.helpers.PhotoViewerTransition.EXTRA_RETURN_PATH,
                "${java.io.File(fixture.second).parent}/missing-target.jpg",
            )
            instrumentation.runOnMainSync {
                source.onActivityReenter(android.app.Activity.RESULT_OK, missingReturn)
            }
            val pendingReturn = MediaActivity::class.java.getDeclaredField("pendingViewerReturnPath")
                .apply { isAccessible = true }
            assertEquals("missing return path should not remain pending", null, pendingReturn.get(source))
            assertTrue("missing return path should not finish the source album", !source.isFinishing)

            val toolbarMonitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { openViewer.invoke(source, fixture.second) }
            val toolbarViewer = instrumentation.waitForMonitorWithTimeout(toolbarMonitor, 10_000)
                as? ViewPagerActivity ?: error("Gallery did not open for toolbar Back")
            instrumentation.removeMonitor(toolbarMonitor)
            awaitFrame(instrumentation, toolbarViewer, requireProxy = false)
            SystemClock.sleep(500)
            instrumentation.runOnMainSync {
                val toolbar = toolbarViewer.findViewById<ViewGroup>(R.id.medium_viewer_toolbar)
                assertNotNull("viewer toolbar should be attached", toolbar)
                assertTrue("toolbar should expose its navigation button", toolbar.childCount > 0)
                toolbar.getChildAt(0).performClick()
            }
            val toolbarBackDeadline = SystemClock.uptimeMillis() + 5_000
            while (!toolbarViewer.isFinishing && SystemClock.uptimeMillis() < toolbarBackDeadline) {
                SystemClock.sleep(50)
            }
            assertTrue("toolbar Back should close the viewer through the shared return path",
                toolbarViewer.isFinishing)
            beforeViewer.recycle()
            sourceGrid.recycle()
        } finally {
            context.config.allowZoomingImages = wasZoomingEnabled
            context.config.showAll = wasShowingAllMedia
            context.contentResolver.delete(fixture.first, null, null)
            runCatching { context.contentResolver.delete(neighborFixture.first, null, null) }
        }
    }

    @Test
    fun viewerPreservesLandscapePortraitSquareAndPanoramaRatiosOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryAspectRatioTest-${UUID.randomUUID()}"
        val dimensions = listOf(960 to 540, 540 to 960, 720 to 720, 1440 to 480)
        val fixtures = dimensions.map { (width, height) -> createFixture(context, album, width, height) }
        val wasZoomingEnabled = context.config.allowZoomingImages
        val wasShowingAllMedia = context.config.showAll
        var source: MediaActivity? = null
        try {
            context.config.allowZoomingImages = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            instrumentation.startActivitySync(launch)
            SystemClock.sleep(800)
            context.config.showAll = false

            val firstPath = fixtures.first().second
            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, java.io.File(firstPath).parent)
            }
            val sourceActivity = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            source = sourceActivity
            awaitSourceThumbnail(instrumentation, sourceActivity, firstPath)
            val openViewer = MediaActivity::class.java
                .getDeclaredMethod("openInViewPager", String::class.java)
                .apply { isAccessible = true }

            fixtures.zip(dimensions).forEachIndexed { index, (fixture, size) ->
                val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
                instrumentation.runOnMainSync { openViewer.invoke(sourceActivity, fixture.second) }
                val viewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                    ?: error("Gallery did not open aspect-ratio fixture $index")
                instrumentation.removeMonitor(monitor)

                val opening = awaitTransitionFrameSamples(instrumentation, viewer)
                val intermediate = opening.filter { (progress, _) -> progress in 0.05f..0.95f }
                val progressFrameCount = ViewPagerActivity::class.java
                    .getDeclaredField("photoTransitionProgressFrameCount")
                    .apply { isAccessible = true }
                    .getInt(viewer)
                assertTrue("fixture $index should run the shared-element progress callback", progressFrameCount > 0)
                if (intermediate.size >= 2) {
                    assertTrue("fixture $index bounds should grow through its opening transition",
                        intermediate.zipWithNext().all { (a, b) -> b.second.width() >= a.second.width() })
                }

                val frame = awaitFrame(instrumentation, viewer, requireProxy = false)
                var renderedRatio = 0f
                instrumentation.runOnMainSync {
                    val drawable = frame.image.drawable
                    val bounds = RectF(0f, 0f,
                        drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
                    frame.image.imageMatrix.mapRect(bounds)
                    renderedRatio = bounds.width() / bounds.height()
                }
                val expectedRatio = size.first.toFloat() / size.second
                assertTrue(
                    "fixture $index should render at the source aspect ratio; expected=$expectedRatio actual=$renderedRatio",
                    kotlin.math.abs(renderedRatio / expectedRatio - 1f) < 0.02f,
                )

                instrumentation.runOnMainSync { viewer.finish() }
                val returnDeadline = SystemClock.uptimeMillis() + 5_000
                while ((!sourceActivity.hasWindowFocus() || sourceActivity.isFinishing) &&
                    SystemClock.uptimeMillis() < returnDeadline) {
                    SystemClock.sleep(50)
                }
                assertTrue("source album should resume after fixture $index", sourceActivity.hasWindowFocus())
                if (index < fixtures.lastIndex) {
                    awaitSourceThumbnail(instrumentation, sourceActivity, fixtures[index + 1].second)
                    // Focus can return before Android finishes the reverse shared
                    // element animation. Let that transition release the source
                    // launch guard before opening the next fixture.
                    SystemClock.sleep(org.fossify.gallery.helpers.PhotoViewerTransition.DURATION_MS + 100)
                }
            }
        } finally {
            context.config.allowZoomingImages = wasZoomingEnabled
            context.config.showAll = wasShowingAllMedia
            fixtures.forEach { runCatching { context.contentResolver.delete(it.first, null, null) } }
            source?.let { instrumentation.runOnMainSync { it.finish() } }
        }
    }

    private fun awaitFrame(
        instrumentation: android.app.Instrumentation,
        activity: ViewPagerActivity,
        requireProxy: Boolean,
    ): Frame {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var frame: Frame? = null
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                    .firstOrNull { it.isVisible && it.isResumed }
                val root = fragment?.view
                val image = root?.findViewById<ImageView>(R.id.gestures_view)
                val holder = root?.findViewById<ViewGroup>(R.id.photo_holder)
                if (image?.drawable != null && holder != null) {
                    val proxy = (holder.getChildAt(holder.childCount - 1) as? ImageView)
                        ?.takeIf { it !== image && it.drawable != null }
                    if (!requireProxy || proxy != null) {
                        frame = Frame(image, image.alpha, proxy?.scaleY ?: 1f,
                            proxy?.translationY ?: 0f)
                    }
                }
            }
            if (frame != null) return frame!!
            SystemClock.sleep(50)
        }
        assertNotNull("viewer photo did not reach the expected render state", frame)
        error("viewer photo did not reach the expected render state")
    }

    private fun awaitTransitionFrameSamples(
        instrumentation: android.app.Instrumentation,
        activity: ViewPagerActivity,
    ): List<Pair<Float, android.graphics.Rect>> {
        val samplesField = ViewPagerActivity::class.java
            .getDeclaredField("photoTransitionFrameSamples").apply { isAccessible = true }
        val deadline = SystemClock.uptimeMillis() + 3_000
        var latest = emptyList<Pair<Float, android.graphics.Rect>>()
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                @Suppress("UNCHECKED_CAST")
                latest = (samplesField.get(activity) as List<Pair<Float, android.graphics.Rect>>).map { (p, rect) ->
                    p to android.graphics.Rect(rect)
                }
            }
            if (latest.lastOrNull()?.first == 1f) break
            SystemClock.sleep(16)
        }
        return latest
    }

    private fun awaitSourceThumbnail(
        instrumentation: android.app.Instrumentation,
        activity: MediaActivity,
        path: String,
    ): ImageView {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var thumbnail: ImageView? = null
        var observedGrid = "not attached"
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val grid = activity.findViewById<RecyclerView>(R.id.media_grid)
                val visible = (0 until (grid?.childCount ?: 0)).asSequence()
                    .mapNotNull { index -> grid?.getChildAt(index)?.findViewById<ImageView>(R.id.medium_thumbnail) }
                    .toList()
                observedGrid = "requested=${activity.intent.getStringExtra(DIRECTORY)} " +
                    "children=${grid?.childCount} items=${grid?.adapter?.itemCount} " +
                    "visible=${visible.map { "${it.tag}:${it.drawable != null}:${it.isAttachedToWindow}" }}"
                thumbnail = visible.firstOrNull { it.drawable != null && it.isAttachedToWindow && it.tag == path }
            }
            if (thumbnail != null) return thumbnail!!
            SystemClock.sleep(50)
        }
        error("test image did not appear in the MediaActivity grid: path=$path $observedGrid")
    }

    private fun awaitDirectoryCard(
        instrumentation: android.app.Instrumentation,
        activity: MainActivity,
        path: String,
        timeoutMs: Long = 20_000,
    ): View {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var card: View? = null
        var observed = "directory grid not ready"
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val grid = activity.findViewById<RecyclerView>(R.id.directories_grid)
                val adapter = grid?.adapter
                val dirs = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    adapter?.javaClass?.getMethod("getDirs")?.invoke(adapter) as? List<Any?>
                }.getOrNull().orEmpty()
                val position = dirs.indexOfFirst { directory ->
                    val itemPath = runCatching {
                        directory?.javaClass?.getMethod("getPath")?.invoke(directory) as? String
                    }.getOrNull()
                    itemPath != null && runCatching {
                        java.io.File(itemPath).canonicalPath == java.io.File(path).canonicalPath
                    }.getOrDefault(false)
                }
                val holder = if (position >= 0) grid?.findViewHolderForAdapterPosition(position) else null
                val item = holder?.itemView
                val image = item?.findViewById<ImageView>(R.id.dir_thumbnail)
                observed = "items=${adapter?.itemCount} position=$position " +
                    "children=${grid?.childCount} cover=${image?.drawable != null} " +
                    "bound=${image?.getTag(R.id.directory_bound_path)} target=$path"
                if (item != null && image?.isAttachedToWindow == true && image.drawable != null &&
                    runCatching {
                        java.io.File(image.getTag(R.id.directory_bound_path) as String).canonicalPath ==
                            java.io.File(path).canonicalPath
                    }.getOrDefault(false)) {
                    card = item
                }
            }
            if (card != null) return card!!
            SystemClock.sleep(50)
        }
        error("album card did not become visible with a decoded cover: $observed")
    }

    private fun captureCheckpoint(
        instrumentation: android.app.Instrumentation,
        activity: ViewPagerActivity,
        context: Context,
        checkpoint: GestureCheckpoint,
        number: Int,
    ): Frame {
        assertTrue("touch stream did not reach gesture checkpoint $number",
            checkpoint.reached.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val frame = awaitFrame(instrumentation, activity, requireProxy = true)
        instrumentation.uiAutomation.takeScreenshot()?.let {
            saveScreenshot(context, "gallery-live-drag-phase-$number.png", it)
            assertTrue("source-grid pixels should remain visible behind the moving photo",
                Color.red(it.getPixel(12, 300)) > 0)
            it.recycle()
        }
        checkpoint.resume.countDown()
        return frame
    }

    private fun startSwipe(
        instrumentation: android.app.Instrumentation,
        fragment: PhotoFragment,
        touchHandler: java.lang.reflect.Method,
        target: View,
        x: Float,
        startY: Float,
        endY: Float,
        durationMs: Int,
        checkpoints: List<GestureCheckpoint> = emptyList(),
    ): SwipeRun {
        val deviceInjectionWorked = AtomicBoolean(true)
        val thread = Thread {
            val downTime = SystemClock.uptimeMillis()
            val location = IntArray(2)
            instrumentation.runOnMainSync { target.getLocationOnScreen(location) }
            val screenX = location[0] + x
            var useDeviceInjection = true
            fun inject(action: Int, y: Float): Boolean {
                val eventTime = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(downTime, eventTime, action,
                    screenX, location[1] + y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                var handled = false
                if (useDeviceInjection) {
                    val accepted = instrumentation.uiAutomation.injectInputEvent(event, true)
                    if (!accepted && fragment.activity?.isFinishing != true) {
                        deviceInjectionWorked.set(false)
                    }
                    useDeviceInjection = accepted
                    handled = accepted
                }
                if (!useDeviceInjection && fragment.activity?.isFinishing != true) {
                    event.setLocation(x, y)
                    instrumentation.runOnMainSync {
                        handled = touchHandler.invoke(fragment, target, event) as Boolean
                    }
                }
                event.recycle()
                return handled
            }

            inject(MotionEvent.ACTION_DOWN, startY)
            // Match a real touchscreen stream closely enough to exercise each
            // rendered frame instead of making the image jump every 100 ms.
            val moveCount = (durationMs / 16).coerceAtLeast(1)
            val phaseLength = (moveCount / 3).coerceAtLeast(1)
            val reversalY = startY + (endY - startY) * 0.45f
            val finalY = startY + (endY - startY) * 0.85f
            for (step in 1..moveCount) {
                SystemClock.sleep((durationMs / moveCount).toLong())
                val y = when {
                    checkpoints.size < 3 -> startY + (endY - startY) * step / moveCount
                    step <= phaseLength -> startY + (endY - startY) * step / phaseLength
                    step <= phaseLength * 2 -> endY + (reversalY - endY) * (step - phaseLength) / phaseLength
                    else -> reversalY + (finalY - reversalY) * (step - phaseLength * 2) / (moveCount - phaseLength * 2)
                }
                val handled = inject(MotionEvent.ACTION_MOVE, y)
                if ((checkpoints.size >= 3 && step == phaseLength) || (checkpoints.size < 3 && step == 3)) {
                    check(handled) { "Photo view did not claim the vertical drag" }
                }
                val checkpointIndex = when (step) {
                    phaseLength -> 0
                    phaseLength * 2 -> 1
                    moveCount -> 2
                    else -> -1
                }
                if (checkpointIndex >= 0 && checkpointIndex < checkpoints.size) {
                    val checkpoint = checkpoints[checkpointIndex]
                    checkpoint.reached.countDown()
                    check(checkpoint.resume.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        "gesture checkpoint $checkpointIndex was not released"
                    }
                }
            }
            inject(MotionEvent.ACTION_UP, endY)
            android.util.Log.i("GalleryGestureTest",
                "touchInjection=${deviceInjectionWorked.get()} target=${target.javaClass.simpleName}")
        }
        thread.start()
        return SwipeRun(thread, deviceInjectionWorked)
    }

    private fun injectHorizontalSwipe(
        instrumentation: android.app.Instrumentation,
        target: View,
        swipeRight: Boolean = false,
    ): Boolean {
        val location = IntArray(2)
        instrumentation.runOnMainSync { target.getLocationOnScreen(location) }
        val startX = location[0] + target.width * (if (swipeRight) 0.1f else 0.9f)
        val y = location[1] + target.height * 0.5f
        val downTime = SystemClock.uptimeMillis()
        var injected = true
        for (step in 0..6) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                6 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                startX + (if (swipeRight) 1f else -1f) * target.width * 0.8f * step / 6f, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            injected = instrumentation.uiAutomation.injectInputEvent(event, true) && injected
            event.recycle()
            SystemClock.sleep(45)
        }
        return injected
    }

    private fun injectDoubleTap(instrumentation: android.app.Instrumentation, target: View): Boolean {
        val location = IntArray(2)
        instrumentation.runOnMainSync { target.getLocationOnScreen(location) }
        val x = location[0] + target.width * 0.5f
        val y = location[1] + target.height * 0.5f
        var injected = true
        repeat(2) {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            injected = instrumentation.uiAutomation.injectInputEvent(down, true) && injected
            down.recycle()
            SystemClock.sleep(45)
            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            injected = instrumentation.uiAutomation.injectInputEvent(up, true) && injected
            up.recycle()
            if (it == 0) SystemClock.sleep(80)
        }
        return injected
    }

    private fun injectVerticalSwipe(
        instrumentation: android.app.Instrumentation,
        target: View,
        distance: Float,
    ): Boolean {
        val location = IntArray(2)
        instrumentation.runOnMainSync { target.getLocationOnScreen(location) }
        val x = location[0] + target.width * 0.5f
        val startY = location[1] + target.height * 0.5f
        val downTime = SystemClock.uptimeMillis()
        var injected = true
        for (step in 0..6) {
            val action = when (step) {
                0 -> MotionEvent.ACTION_DOWN
                6 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x, startY + distance * step / 6f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            injected = instrumentation.uiAutomation.injectInputEvent(event, true) && injected
            event.recycle()
            SystemClock.sleep(45)
        }
        return injected
    }

    private fun createFixture(
        context: Context,
        relativePath: String,
        width: Int = 960,
        height: Int = 720,
    ): Pair<Uri, String> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "gallery-interactive-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create emulator image fixture")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.RED
            drawRect(0f, 0f, width / 3f, height.toFloat(), paint)
            paint.color = Color.GREEN
            drawRect(width / 3f, 0f, width * 2f / 3f, height.toFloat(), paint)
            paint.color = Color.YELLOW
            drawRect(width * 2f / 3f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "could not write image fixture" }
        } ?: error("Could not open emulator image fixture")
        bitmap.recycle()
        val path = resolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: error("The emulator did not expose the test image path")
        return uri to path
    }

    private fun grantMediaPermission(context: Context) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildList {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            }
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} $permission"
            ).close()
        }
    }

    private fun saveScreenshot(context: Context, name: String, bitmap: Bitmap) {
        val evidenceName = "GalleryGestureEvidence_${UUID.randomUUID()}_$name"
        val evidenceFolder = "${Environment.DIRECTORY_PICTURES}/GalleryInteractiveEvidence"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, evidenceName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, evidenceFolder)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create screenshot evidence entry")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "could not save $name" }
        } ?: error("Could not write screenshot evidence")
        val path = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        android.util.Log.i("GalleryGestureTest", "evidence=${path ?: uri}")
    }

    private fun captureFrameStats(instrumentation: android.app.Instrumentation, context: Context) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "dumpsys gfxinfo ${context.packageName} framestats"
        )
        val stats = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        check(stats.contains("Graphics info for pid")) { "Gallery gfxinfo did not report an active process" }
        val name = "GalleryGestureEvidence_gfxinfo-framestats.txt"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GalleryInteractiveEvidence")
        }
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/GalleryInteractiveEvidence/"),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), cursor.getLong(idColumn)),
                    null,
                    null,
                )
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: error("Could not create emulator gfxinfo artifact")
        context.contentResolver.openOutputStream(uri)?.use { it.write(stats.toByteArray()) }
            ?: error("Could not save emulator gfxinfo artifact")
        android.util.Log.i("GalleryGestureTest", "gfxinfo=$uri bytes=${stats.length}")
    }
}
