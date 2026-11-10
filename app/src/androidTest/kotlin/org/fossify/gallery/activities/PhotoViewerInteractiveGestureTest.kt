package org.fossify.gallery.activities

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
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
    fun downwardDragReversesRestoresAndDismissesFromTheCurrentPhotoFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val fixture = createFixture(context)
        val wasZoomingEnabled = context.config.allowZoomingImages
        try {
            // Keep this UI scenario on GestureImageView so asynchronous tile setup
            // does not obscure touch arbitration for the core transition.
            context.config.allowZoomingImages = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            instrumentation.startActivitySync(launch)
            SystemClock.sleep(1_000)
            val beforeViewer = instrumentation.uiAutomation.takeScreenshot()
                ?: error("Could not capture the source Gallery screen")
            saveScreenshot(context, "gallery-source-screen.png", beforeViewer)

            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, java.io.File(fixture.second).parent)
            }
            val source = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            val sourceThumbnail = awaitSourceThumbnail(instrumentation, source)
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

            val viewerIntent = Intent(source, ViewPagerActivity::class.java).apply {
                putExtra(PATH, fixture.second)
                putExtra(IS_FROM_GALLERY, true)
            }
            val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            val launchViewer = MediaActivity::class.java.getDeclaredMethod(
                "launchPhotoViewerWithSharedElement", Intent::class.java, String::class.java
            ).apply { isAccessible = true }
            instrumentation.runOnMainSync { launchViewer.invoke(source, viewerIntent, fixture.second) }
            val viewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                ?: error("MediaActivity did not launch ViewPagerActivity")
            instrumentation.removeMonitor(monitor)
            val initial = awaitFrame(instrumentation, viewer, requireProxy = false)
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
            val photoFragment = viewer.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                .first { it.isVisible && it.isResumed }
            assertNotNull("source thumbnail must be decoded before the shared transition",
                sourceThumbnail.getTag(R.id.thumbnail_loaded_key))
            assertEquals("source thumbnail request must match its decoded media",
                sourceThumbnail.getTag(R.id.thumbnail_request_key),
                sourceThumbnail.getTag(R.id.thumbnail_loaded_key))
            val touchHandler = photoFragment.javaClass.getDeclaredMethod(
                "handlePhotoDismissTouch", View::class.java, MotionEvent::class.java
            ).apply { isAccessible = true }
            instrumentation.runOnMainSync {
                val probe = photoFragment.sharedElementViewFor(fixture.second)
                assertNotNull("fixture photo should expose a shared content surface", probe)
                photoFragment.finishSharedTransition()
            }
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
            SystemClock.sleep(700)
            val restored = awaitFrame(instrumentation, viewer, requireProxy = false)
            assertTrue("a short drag should restore the real image", restored.imageAlpha > 0.99f)
            assertTrue("viewer should remain open after a short drag", !viewer.isFinishing)

            assertTrue("horizontal swipe must be injected on the emulator",
                injectHorizontalSwipe(instrumentation, initial.image))
            SystemClock.sleep(400)
            assertTrue("horizontal navigation must not trigger a vertical dismissal", !viewer.isFinishing)

            val dismissSwipe = startSwipe(instrumentation, photoFragment, touchHandler, initial.image,
                initial.image.width / 2f, initial.image.height / 2f,
                initial.image.height / 2f + 500f, 900)
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (!viewer.isFinishing && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            dismissSwipe.thread.join(5_000)
            assertTrue("dismiss drag must use device-level touchscreen injection",
                dismissSwipe.deviceInjectionWorked.get())
            assertTrue("a committed drag should dismiss the viewer", viewer.isFinishing)
            instrumentation.uiAutomation.takeScreenshot()?.let {
                saveScreenshot(context, "gallery-dismissed-screen.png", it)
                it.recycle()
            }
            SystemClock.sleep(500)

            // Reopen from the same real album thumbnail and exercise Android Back
            // while the shared transition may still be running.
            val returnMonitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { launchViewer.invoke(source, viewerIntent, fixture.second) }
            val reopenedViewer = instrumentation.waitForMonitorWithTimeout(returnMonitor, 10_000)
                as? ViewPagerActivity ?: error("Gallery did not reopen the selected photo")
            instrumentation.removeMonitor(returnMonitor)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            val backDeadline = SystemClock.uptimeMillis() + 5_000
            while (!reopenedViewer.isFinishing && SystemClock.uptimeMillis() < backDeadline) {
                SystemClock.sleep(50)
            }
            assertTrue("Android Back during entry must close cleanly without a competing transition",
                reopenedViewer.isFinishing)
            beforeViewer.recycle()
            sourceGrid.recycle()
        } finally {
            context.config.allowZoomingImages = wasZoomingEnabled
            context.contentResolver.delete(fixture.first, null, null)
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

    private fun awaitSourceThumbnail(
        instrumentation: android.app.Instrumentation,
        activity: MediaActivity,
    ): ImageView {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var thumbnail: ImageView? = null
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val grid = activity.findViewById<RecyclerView>(R.id.media_grid)
                thumbnail = (0 until (grid?.childCount ?: 0)).asSequence()
                    .mapNotNull { index -> grid?.getChildAt(index)?.findViewById<ImageView>(R.id.medium_thumbnail) }
                    .firstOrNull { it.drawable != null && it.isAttachedToWindow }
            }
            if (thumbnail != null) return thumbnail!!
            SystemClock.sleep(50)
        }
        error("test image did not appear in the MediaActivity grid")
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
                    useDeviceInjection = instrumentation.uiAutomation.injectInputEvent(event, true)
                    if (!useDeviceInjection) deviceInjectionWorked.set(false)
                    handled = useDeviceInjection
                }
                if (!useDeviceInjection) {
                    event.setLocation(x, y)
                    instrumentation.runOnMainSync {
                        handled = touchHandler.invoke(fragment, target, event) as Boolean
                    }
                }
                event.recycle()
                return handled
            }

            inject(MotionEvent.ACTION_DOWN, startY)
            val moveCount = (durationMs / 100).coerceAtLeast(1)
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
                if (step == 3) check(handled) { "Photo view did not claim the vertical drag" }
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

    private fun injectHorizontalSwipe(instrumentation: android.app.Instrumentation, target: View): Boolean {
        val location = IntArray(2)
        instrumentation.runOnMainSync { target.getLocationOnScreen(location) }
        val x = location[0] + target.width * 0.78f
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
                x - target.width * 0.56f * step / 6f, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            injected = instrumentation.uiAutomation.injectInputEvent(event, true) && injected
            event.recycle()
            SystemClock.sleep(45)
        }
        return injected
    }

    private fun createFixture(context: Context): Pair<Uri, String> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "gallery-interactive-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/GalleryInteractiveGestureTest-${UUID.randomUUID()}")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create emulator image fixture")
        val bitmap = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.RED
            drawRect(0f, 0f, 320f, 720f, paint)
            paint.color = Color.GREEN
            drawRect(320f, 0f, 640f, 720f, paint)
            paint.color = Color.YELLOW
            drawRect(640f, 0f, 960f, 720f, paint)
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
        val result = instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.READ_EXTERNAL_STORAGE")
        result.close()
    }

    private fun saveScreenshot(context: Context, name: String, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "GalleryGestureEvidence_${name}")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/GalleryInteractiveEvidence")
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
}
