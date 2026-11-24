package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import com.squareup.picasso.Picasso
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [MediaAdapterStabilityTest.HostWebpFactory::class])
class MediaAdapterStabilityTest {
    // These are request/binding tests with nonexistent local paths, not decoder tests.
    // Android's bundled ARM WebP library cannot be loaded in the host JVM.
    @Implements(className = "com.bumptech.glide.integration.webp.WebpBitmapFactory", isInAndroidSdk = false)
    class HostWebpFactory {
        companion object {
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
        }
    }

    private var testPicasso: Picasso? = null
    private var previousPicasso: Any? = null
    private val picassoSingleton get() = Picasso::class.java.getDeclaredField("singleton").apply { isAccessible = true }

    @Before fun installHostImages() {
        // Plain Application deliberately has no Picasso startup ContentProvider.
        previousPicasso = picassoSingleton.get(null)
        testPicasso = Picasso.Builder(RuntimeEnvironment.getApplication()).build()
        picassoSingleton.set(null, testPicasso)
    }

    @After fun releaseHostImages() {
        picassoSingleton.set(null, previousPicasso)
        testPicasso?.shutdown()
    }

    private fun item(name: String) = Medium(null, name, "/photos/$name", "/photos", 10, 10, 100,
        TYPE_IMAGES, 0, false, 0, 1)

    private fun withAdapter(path: String = "/photos", block: (MediaAdapter, MyRecyclerView, ArrayDeque<Runnable>) -> Unit) {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
            activity.setContentView(recycler)
            val tasks = ArrayDeque<Runnable>()
            val adapter = MediaAdapter(activity, arrayListOf(item("a.jpg")), null, false, false,
                path, recycler, Executor { tasks.addLast(it) }) {}
            recycler.adapter = adapter
            controller.visible()
            block(adapter, recycler, tasks)
        } finally { controller.pause().stop().destroy() }
    }

    private class ReturnGridFixture(rows: List<Medium>, columns: Int = 1, folder: String = "/photos") {
        val controller = Robolectric.buildActivity(MediaActivity::class.java)
        val activity = controller.get()
        val type = MediaActivity::class.java
        val grid: MyRecyclerView
        val adapter: MediaAdapter
        val tasks = ArrayDeque<Runnable>()
        private val transitionState = Activity::class.java.getDeclaredField("mActivityTransitionState")
            .apply { isAccessible = true }.get(activity)
        val postponed: Boolean
            get() = transitionState.javaClass.getDeclaredField("mIsEnterPostponed").apply { isAccessible = true }
                .getBoolean(transitionState)

        init {
            type.getDeclaredField("mPath").apply { isAccessible = true }.set(activity, folder)
            if (folder == PhotoProfileMediaLoader.PATH) {
                type.getDeclaredField("photoProfileId").apply { isAccessible = true }.set(activity, "test-profile")
            }
            activity.setTheme(R.style.AppTheme)
            activity.delegate.installViewFactory()
            val binding = type.getDeclaredMethod("getBinding")
                .apply { isAccessible = true }.invoke(activity) as ActivityMediaBinding
            activity.window.setContentView(binding.root)
            grid = binding.mediaGrid
            adapter = MediaAdapter(activity, ArrayList(rows), null, false, false, "/photos", grid,
                Executor { tasks.addLast(it) }) {}
            grid.layoutManager = if (columns == 1) LinearLayoutManager(activity)
                else androidx.recyclerview.widget.GridLayoutManager(activity, columns)
            grid.itemAnimator = null
            grid.adapter = adapter
            controller.visible()
            // This fixture attaches views without running MediaActivity.onCreate.
            // Simulate the ViewRoot marking the window visible before a real traversal.
            val attachment = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }.get(grid)
            attachment.javaClass.getDeclaredField("mWindowVisibility").apply { isAccessible = true }
                .setInt(attachment, View.VISIBLE)
            layout()
        }

        fun layout() {
            grid.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            grid.layout(0, 0, 320, 320)
        }

        val launchId: Long
            get() = type.getDeclaredField("viewerReturnLaunch").apply { isAccessible = true }.getLong(activity)

        private var session: MediaTransitionSessionCoordinator? = null
        private val operationsByLaunch = mutableMapOf<Long, MediaTransitionSessionCoordinator.Token>()
        private var requestId: Long? = null

        private fun beginReturn(id: Long): MediaTransitionSessionCoordinator.Token {
            if (session == null || requestId != null && requestId != id) {
                session = MediaTransitionSessionCoordinator().also {
                    it.attachGrid()
                    check(it.attachViewer())
                    check(it.entryFinished(it.entry, withoutSharedEntry = true))
                    type.getDeclaredField("transitionSession").apply { isAccessible = true }.set(activity, it)
                }
            }
            val current = requireNotNull(session)
            requestId = id
            if (current.state == MediaTransitionSessionCoordinator.State.PREPARING) {
                check(current.entryFinished(current.entry, withoutSharedEntry = true))
            }
            return (current.returnOperation?.takeIf { current.isReturning(it) || current.outcome != null }
                ?: requireNotNull(current.beginReturn())).also {
                operationsByLaunch[launchId] = it
            }
        }

        fun reenter(path: String, id: Long = 1L, launch: Long = launchId, deletedPaths: List<String> = emptyList()) {
            val intent = Intent()
                .putExtra(PhotoViewerTransition.EXTRA_RETURN_PATH, path)
                .putExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, id)
                .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, launch)
                .putStringArrayListExtra(PhotoViewerTransition.EXTRA_DELETED_PATHS, ArrayList(deletedPaths))
            activity.onActivityReenter(Activity.RESULT_OK, MediaTransitionHandoff.write(intent, beginReturn(id)))
        }

        fun thumbnail(position: Int, path: String): ImageView {
            val image = grid.findViewHolderForAdapterPosition(position)!!.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
            image.setImageDrawable(ColorDrawable(android.graphics.Color.BLUE))
            image.setTag(R.id.media_bound_path, path)
            image.setTag(R.id.thumbnail_request_key, "ready")
            image.setTag(R.id.thumbnail_loaded_key, "ready")
            layout()
            assertTrue(image.getGlobalVisibleRect(android.graphics.Rect()))
            return image
        }

        fun map(): View? {
            val elements = mutableMapOf<String, View>()
            type.getDeclaredMethod("mapViewerReturnTarget", MutableMap::class.java)
                .apply { isAccessible = true }.invoke(activity, elements)
            return elements[PhotoViewerTransition.ELEMENT_NAME]
        }

        fun platformMap(candidate: View): View? {
            val name = PhotoViewerTransition.ELEMENT_NAME
            val callback = Activity::class.java.getDeclaredField("mExitTransitionListener")
                .apply { isAccessible = true }.get(activity) as android.app.SharedElementCallback
            val elements = mutableMapOf(name to candidate)
            callback.onMapSharedElements(mutableListOf(name), elements)
            return elements[name]
        }

        fun drag(path: String): MediaTransitionSessionCoordinator.Token {
            val current = requireNotNull(session)
            if (current.state == MediaTransitionSessionCoordinator.State.PREPARING) {
                check(current.entryFinished(current.entry, withoutSharedEntry = true))
            }
            val token = requireNotNull(current.beginGesture())
            // Invoke the same operation-scoped border that the Viewer calls while the finger is down.
            current.javaClass.getDeclaredMethod("showGridPreview", token.javaClass, String::class.java)
                .invoke(current, token, path)
            return token
        }

        fun cancelDrag(token: MediaTransitionSessionCoordinator.Token) {
            check(requireNotNull(session).cancelGesture(token))
        }

        fun sourceTimeout(): Runnable {
            val lease = type.getDeclaredField("viewerSourceLease").apply { isAccessible = true }.get(activity)
            return lease.javaClass.getDeclaredField("nameTimeout").apply { isAccessible = true }.get(lease) as Runnable
        }

        fun completeEntry() {
            val current = requireNotNull(session)
            check(current.entryFinished(current.entry, withoutSharedEntry = true))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        fun launch(path: String) {
            val intent = Intent(activity, org.fossify.gallery.activities.ViewPagerActivity::class.java)
            type.getDeclaredMethod("launchPhotoViewerWithSharedElement", Intent::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(activity, intent, path)
            session = requireNotNull(MediaTransitionHandoff.claim(intent))
            requestId = null
        }

        fun terminal(transition: android.transition.Transition = activity.window.sharedElementReenterTransition, cancelled: Boolean = false) {
            val phases = if (cancelled) listOf("start", "cancel", "end") else listOf("start", "end")
            phases.forEach { phase ->
                android.transition.Transition::class.java.getDeclaredMethod(phase)
                    .apply { isAccessible = true }.invoke(transition)
            }
        }

        fun result(path: String?, id: Long = 1L, launch: Long = launchId, deletedPaths: List<String> = emptyList()) {
            val operation = operationsByLaunch[launch] ?: beginReturn(id)
            val intent = Intent()
                .putExtra(PhotoViewerTransition.EXTRA_RETURN_PATH, path)
                .putStringArrayListExtra(PhotoViewerTransition.EXTRA_DELETED_PATHS, ArrayList(deletedPaths))
                .putExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, id)
                .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, launch)
            type.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
                .apply { isAccessible = true }.invoke(activity, PhotoViewerTransition.REQUEST_CODE, Activity.RESULT_OK,
                    MediaTransitionHandoff.write(intent, operation))
        }

        fun frame() {
            layout()
            draw()
            val callbacks = captureFrameCommits()
            grid.viewTreeObserver.dispatchOnDraw()
            callbacks.forEach(Runnable::run)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }

        @Suppress("UNCHECKED_CAST")
        fun captureFrameCommits(): List<Runnable> {
            if (android.os.Build.VERSION.SDK_INT < 29 || !grid.isHardwareAccelerated) return emptyList()
            // A Robolectric traversal has no render thread to acknowledge the buffer.
            val observer = grid.viewTreeObserver
            return (observer.javaClass.getDeclaredMethod("captureFrameCommitCallbacks")
                .apply { isAccessible = true }.invoke(observer) as? List<Runnable>).orEmpty()
        }

        fun end() {
            type.getDeclaredMethod("endViewerReturn", String::class.java)
                .apply { isAccessible = true }.invoke(activity, "test_end")
        }

        fun timeout() {
            val request = type.getDeclaredField("viewerReturn").apply { isAccessible = true }.get(activity)
            val timeout = request.javaClass.getDeclaredField("timeout").apply { isAccessible = true }.get(request) as Runnable
            timeout.run()
        }

        fun draw() = grid.viewTreeObserver.dispatchOnPreDraw()
    }

    @Config(sdk = [36])
    @Test fun firstGridFrameCanCommitWithTheTargetHiddenAndLateCallbacksAreIgnored() {
        val app = RuntimeEnvironment.getApplication()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        GalleryDiagnosticLogger.initialize(app)
        GalleryDiagnosticLogger.setEnabled(app, true)
        try {
            val row = item("a.jpg")
            val fixture = ReturnGridFixture(listOf(row))
            val image = fixture.thumbnail(0, row.path)
            val attachment = View::class.java.getDeclaredField("mAttachInfo").apply { isAccessible = true }.get(fixture.grid)
            attachment.javaClass.getDeclaredField("mHardwareAccelerated").apply { isAccessible = true }
                .setBoolean(attachment, true)
            assertTrue(fixture.grid.isHardwareAccelerated)

            fun firstFrameEvents(): List<String> {
                var exported = ""
                GalleryDiagnosticLogger.snapshot { text, _ -> exported = text }
                val io = GalleryDiagnosticLogger::class.java.getDeclaredField("io").apply { isAccessible = true }
                    .get(GalleryDiagnosticLogger) as java.util.concurrent.ExecutorService
                io.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                return exported.lineSequence().filter { "event=grid_first_return_frame " in it }.toList()
            }

            fixture.launch(row.path)
            fixture.reenter(row.path, id = 901L)
            fixture.draw()
            assertSame(image, fixture.map())
            image.alpha = 0f
            image.transitionAlpha = 0f
            fixture.draw()
            assertTrue(firstFrameEvents().isEmpty())
            fixture.frame()
            fixture.frame()
            val event = firstFrameEvents().single()
            assertTrue("request=901 " in event)
            assertTrue("ack=frame_commit" in event)
            assertTrue("firstVisibleIndex=0" in event)
            assertTrue("targetAlpha=0.0" in event && "targetTransitionAlpha=0.0" in event)
            fixture.end()

            image.alpha = 1f
            image.transitionAlpha = 1f
            fixture.launch(row.path)
            fixture.reenter(row.path, id = 902L)
            fixture.draw()
            assertSame(image, fixture.map())
            fixture.draw()
            val obsoleteCallbacks = fixture.captureFrameCommits()
            assertEquals(1, obsoleteCallbacks.size)
            fixture.grid.viewTreeObserver.dispatchOnDraw()
            fixture.timeout()
            obsoleteCallbacks.forEach(Runnable::run)
            assertEquals(listOf(event), firstFrameEvents())
        } finally {
            GalleryDiagnosticLogger.setEnabled(app, false)
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Config(sdk = [36])
    @Test fun sourceExitRetiresTheNameAndReturnsOnlyTheLastSelection() {
        val rows = listOf(item("a.jpg"), item("b.mp4").copy(type = TYPE_VIDEOS), item("c.jpg"),
            item("d.mp4").copy(type = TYPE_VIDEOS))
        val scopes = listOf("/photos", org.fossify.commons.helpers.FAVORITES, PhotoProfileMediaLoader.PATH)
        for ((sourceIndex, returnIndex) in listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 1 to 3)) {
            val fixture = ReturnGridFixture(rows, columns = 3, folder = scopes[returnIndex % scopes.size])
            val source = fixture.thumbnail(sourceIndex, rows[sourceIndex].path)
            val destination = if (sourceIndex == returnIndex) source else fixture.thumbnail(returnIndex, rows[returnIndex].path)
            val sourceDrawable = source.drawable
            val holder = fixture.grid.findViewHolderForAdapterPosition(sourceIndex)!!
            fixture.launch(rows[sourceIndex].path)
            assertEquals(PhotoViewerTransition.ELEMENT_NAME, source.transitionName)
            assertSame(source, fixture.platformMap(source))
            assertFalse(holder.isRecyclable)
            fixture.terminal(fixture.activity.window.sharedElementExitTransition, cancelled = returnIndex == 1)
            assertNull(source.transitionName)
            assertFalse("Android can still hide its captured source after exit ends", holder.isRecyclable)

            fixture.reenter(rows[returnIndex].path)
            fixture.draw()
            assertSame(destination, fixture.platformMap(source))
            assertEquals(PhotoViewerTransition.ELEMENT_NAME, destination.transitionName)
            if (sourceIndex != returnIndex) {
                assertNull(source.transitionName)
                assertTrue(holder.isRecyclable)
            }
            assertEquals(1f, source.alpha, 0f)
            assertEquals(1f, source.transitionAlpha, 0f)
            assertSame(sourceDrawable, source.drawable)
            assertEquals(rows[sourceIndex].path, source.getTag(R.id.media_bound_path))
            fixture.terminal()
            fixture.result(rows[returnIndex].path)
            destination.transitionAlpha = 0f
            fixture.frame()
            assertEquals(PhotoViewerTransition.ELEMENT_NAME, destination.transitionName)
            destination.transitionAlpha = 1f
            fixture.frame()
            assertNull("Target ${rows[returnIndex].name} remained named after drawing", destination.transitionName)
            assertTrue(holder.isRecyclable)
        }
    }

    @Test fun sourceTimeoutAndOldExitCannotTouchAReboundHolderInTheNextLaunch() {
        val rows = listOf(item("a.jpg"), item("b.jpg"))
        val fixture = ReturnGridFixture(rows, columns = 2)
        val source = fixture.thumbnail(0, rows[0].path)
        val destination = fixture.thumbnail(1, rows[1].path)
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!! as MyRecyclerViewAdapter.ViewHolder
        fixture.launch(rows[0].path)
        val oldExit = fixture.activity.window.sharedElementExitTransition
        val timeout = fixture.sourceTimeout()
        timeout.run()
        assertNull(source.transitionName)
        assertFalse(holder.isRecyclable)

        fixture.reenter(rows[1].path)
        fixture.draw()
        assertSame(destination, fixture.platformMap(source))
        assertTrue(holder.isRecyclable)
        fixture.end()
        fixture.result(rows[1].path)
        fixture.adapter.onViewRecycled(holder)
        assertNull(source.transitionName)
        assertNull(source.drawable)
        val rebound = rows[0].copy(path = "/photos/new.jpg", name = "new.jpg")
        fixture.adapter.updateMedia(arrayListOf(rebound, rows[1]))
        drain(fixture.tasks)
        fixture.layout()
        val currentSource = fixture.thumbnail(0, rebound.path)
        val replacement = currentSource.drawable
        fixture.launch(rebound.path)
        val currentHolder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.terminal(oldExit, cancelled = true)
        timeout.run()
        assertSame(currentSource, fixture.platformMap(currentSource))
        assertEquals(PhotoViewerTransition.ELEMENT_NAME, currentSource.transitionName)
        assertEquals(rebound.path, currentSource.getTag(R.id.media_bound_path))
        assertEquals(1f, currentSource.alpha, 0f)
        assertSame(replacement, currentSource.drawable)
        assertFalse(currentHolder.isRecyclable)
    }

    @Config(sdk = [36])
    @Test fun aLoadedButTransparentTargetDoesNotStartSharedReturn() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        image.alpha = 0f
        fixture.reenter(row.path)
        fixture.draw()
        assertTrue(fixture.postponed)
        image.alpha = 1f
        image.transitionAlpha = 0f
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        fixture.draw()
        assertTrue(fixture.postponed)
        image.transitionAlpha = 1f
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        fixture.draw()
        assertFalse(fixture.postponed)
        assertSame(image, fixture.map())
        fixture.end()
    }

    @Test fun drawnReturnTargetMustRestoreItsOriginalImageTransform() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val bitmap = android.graphics.Bitmap.createBitmap(32, 64, android.graphics.Bitmap.Config.ARGB_8888)
        image.setImageDrawable(android.graphics.drawable.BitmapDrawable(image.resources, bitmap))
        image.scaleType = ImageView.ScaleType.MATRIX
        val original = android.graphics.Matrix().apply { setScale(2f, 2f) }
        image.imageMatrix = original
        fixture.layout()
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.launch(row.path)
        fixture.reenter(row.path)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.terminal()
        fixture.result(row.path)

        val temporary = android.graphics.Matrix().apply { setScale(3f, 3f) }
        image.imageMatrix = temporary
        assertEquals(temporary, image.imageMatrix)
        fixture.frame()
        assertFalse("Visible pixels in a temporary shared-element transform are not the final thumbnail", holder.isRecyclable)

        image.imageMatrix = original
        fixture.frame()
        assertTrue(holder.isRecyclable)
        assertNull(image.transitionName)
    }

    @Test fun viewerReentryDoesNotReleaseWhenReadyThumbnailIsReboundBeforePredraw() {
        val target = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(target))
        val image = fixture.thumbnail(0, target.path)
        fixture.reenter(target.path)
        assertTrue(fixture.postponed)
        image.setTag(R.id.media_bound_path, "/photos/other.jpg")
        fixture.draw()
        assertTrue("A rebound thumbnail must not release Android's postponed return", fixture.postponed)
    }

    @Test fun returnRemapsTheSelectedVisiblePhotoAndRejectsBoundsThatChangedAfterPredraw() {
        val rows = listOf(item("a.jpg"), item("b.jpg"))
        val fixture = ReturnGridFixture(rows)
        fixture.grid.scrollToPosition(1)
        fixture.layout()
        val image = fixture.thumbnail(1, rows[1].path)
        fixture.reenter(rows[1].path)
        fixture.draw()
        assertFalse(fixture.postponed)
        assertSame(image, fixture.map())
        image.translationY = 200f
        assertNull(fixture.map())
    }

    @Test fun returnWaitsForLayoutAndPixelsForTargetsAboveAndBelowTheViewport() {
        val rows = List(7) { item("photo-$it.jpg") }
        for (target in listOf(0, rows.lastIndex)) {
            val fixture = ReturnGridFixture(rows)
            fixture.grid.scrollToPosition(3)
            fixture.layout()
            assertNull(fixture.grid.findViewHolderForAdapterPosition(target))
            fixture.reenter(rows[target].path)
            assertTrue(fixture.postponed)
            fixture.draw()
            assertTrue(fixture.postponed)
            fixture.layout()
            val image = fixture.grid.findViewHolderForAdapterPosition(target)!!.itemView
                .findViewById<ImageView>(R.id.medium_thumbnail)
            image.setTag(R.id.thumbnail_loaded_key, null)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
            fixture.draw()
            assertTrue(fixture.postponed)
            fixture.thumbnail(target, rows[target].path)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
            fixture.draw()
            assertFalse(fixture.postponed)
            assertSame(image, fixture.map())
            fixture.end()
        }
    }

    @Test fun returnRechecksAdapterVersionAndOldCallbacksCannotReleaseTheNextRequest() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        fixture.thumbnail(0, row.path)
        fixture.reenter(row.path)
        val version = fixture.adapter.committedVersion
        fixture.adapter.updateMedia(arrayListOf(row.copy(id = 17)))
        assertTrue(fixture.adapter.committedVersion > version)
        fixture.draw()
        assertTrue(fixture.postponed)

        val absent = "/photos/absent.jpg"
        fixture.reenter(absent, id = 2L)
        fixture.draw()
        assertFalse(fixture.postponed)
        assertNull(fixture.map())
    }

    @Test fun aTargetRemovedByTheCommittedSearchDoesNotMap() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        fixture.reenter(row.path)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.type.getDeclaredField("mCommittedSearchText").apply { isAccessible = true }
            .set(fixture.activity, "different query")
        assertNull(fixture.map())
        assertTrue(fixture.grid.findViewHolderForAdapterPosition(0)!!.isRecyclable)
    }

    @Test fun returningThumbnailHoldsAnInFlightDiffUntilTransitionFinishes() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        fixture.thumbnail(0, row.path)
        val version = fixture.adapter.committedVersion
        val updated = row.copy(size = row.size + 50)
        fixture.adapter.updateMedia(arrayListOf(updated))
        assertEquals(1, fixture.tasks.size)

        fixture.reenter(row.path)
        fixture.draw()
        assertNotNull(fixture.map())
        drain(fixture.tasks)
        assertEquals(version, fixture.adapter.committedVersion)

        fixture.end()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        drain(fixture.tasks)
        assertTrue(fixture.adapter.committedVersion > version)
        assertEquals(updated.size, (fixture.adapter.media.single() as Medium).size)
        assertTrue(fixture.grid.findViewHolderForAdapterPosition(0)?.isRecyclable ?: true)
    }

    @Config(sdk = [36])
    @Test fun fiftyViewerPausesRejoinOneOrdinaryFolderRequestAndRevocationCancelsIt() {
        val fixture = ReturnGridFixture(listOf(item("a.jpg")))
        fun field(name: String) = fixture.type.getDeclaredField(name).apply { isAccessible = true }
        val requests = field("mediaLoadRequests").get(fixture.activity) as FavoriteMediaLoadRequests
        val request = requests.begin()
        field("mediaRefreshRequest").setLong(fixture.activity, request)
        val app = RuntimeEnvironment.getApplication()
        val task = org.fossify.gallery.asynctasks.GetMediaAsynctask(app, "/photos",
            isPickImage = false, isPickVideo = false, showAll = false) {}
        field("mCurrAsyncTask").set(fixture.activity, task)
        val checkpoint = FolderReturnCheckpoint(
            "/photos", "sort", mapOf("external" to "1:2"), 1, FolderContentRevision.current(), 0
        )
        val watcher = FolderScanObservation(app.cacheDir)
        var accessCurrent = true
        fun taskField(name: String) = task.javaClass.getDeclaredField(name).apply { isAccessible = true }
        taskField("viewerScanCheckpoint").set(task, checkpoint)
        taskField("viewerFolderObservation").set(task, watcher)
        taskField("pageAccessCurrent").set(task, { accessCurrent })
        val detach = fixture.type.getDeclaredMethod("detachFolderViewerScan").apply { isAccessible = true }
        val retained = field("retainedFolderScan")
        try {
            fun rejoin(): Boolean {
                field("mWasFullscreenViewOpen").setBoolean(fixture.activity, true)
                assertEquals(true, detach.invoke(fixture.activity))
                val scan = requireNotNull(retained.get(fixture.activity))
                val resume = fixture.type.getDeclaredMethod("resumeFolderViewerScan",
                    scan.javaClass, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                return resume.invoke(fixture.activity, scan, true) as Boolean
            }
            val version = fixture.adapter.committedVersion
            repeat(50) {
                assertTrue(rejoin())
                assertNull(retained.get(fixture.activity))
                assertSame(task, field("mCurrAsyncTask").get(fixture.activity))
                assertTrue(requests.isCurrent(request))
                assertEquals(version, fixture.adapter.committedVersion)
            }
            accessCurrent = false
            assertFalse(rejoin())
            assertFalse(requests.isCurrent(request))
            assertTrue(task.isCancelled)
            assertNull(fixture.grid.adapter)
            assertFalse(watcher.unchanged)
        } finally {
            watcher.stop()
        }
    }

    @Test fun profileReturnMapsTheLastSelectionAfterLargeProgressiveCatalogReplacement() {
        val initial = List(160) { item("preview-$it.jpg") }
        val partial = initial + List(113) { item("partial-$it.jpg") }
        val complete = List(4708) { item("final-$it.jpg") } + partial
        val a = initial.first()
        val b = partial[219]
        val fixture = ReturnGridFixture(initial, columns = 2, folder = PhotoProfileMediaLoader.PATH)
        val photoScroll = fixture.type.getDeclaredField("photoScroll").apply { isAccessible = true }
            .get(fixture.activity) as FavoritesScrollRestorer
        photoScroll.remember(fixture.grid, initial.map { it.path })
        assertEquals(a.path, photoScroll.pending?.path)
        fixture.thumbnail(0, a.path)
        fixture.launch(a.path)
        // This case tests publication while viewing, after the entry has released its source.
        fixture.completeEntry()
        fixture.adapter.updateMedia(ArrayList<ThumbnailItem>(partial))
        drain(fixture.tasks)
        fixture.layout()
        val partialVersion = fixture.adapter.committedVersion
        fixture.adapter.updateMedia(ArrayList<ThumbnailItem>(complete))
        drain(fixture.tasks)
        fixture.layout()
        assertEquals(partialVersion + 1, fixture.adapter.committedVersion)

        val position = complete.indexOf(b)
        assertNull(fixture.grid.findViewHolderForAdapterPosition(position))
        fixture.reenter(b.path)
        assertNull("The pre-Viewer anchor must be retired before resolving the last selection", photoScroll.pending)
        fixture.layout()
        val target = fixture.thumbnail(position, b.path)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        // A real traversal measures any asynchronous thumbnail invalidation before pre-draw.
        fixture.layout()
        fixture.draw()
        assertSame(target, fixture.map())
        assertEquals(b.path, photoScroll.pending?.path)
        fixture.end()
    }

    @Test fun synchronousGridTransitionKeepsTheExactThumbnailUntilViewerResultAndVisibleDraw() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val drawable = image.drawable
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.launch(row.path)
        fixture.reenter(row.path)
        fixture.draw()
        assertSame(image, fixture.map())

        val version = fixture.adapter.committedVersion
        val updated = row.copy(size = row.size + 50)
        fixture.adapter.updateMedia(arrayListOf(updated))
        fixture.terminal()
        fixture.frame()
        drain(fixture.tasks)
        assertFalse("A grid Transition can finish before the Viewer", holder.isRecyclable)
        assertEquals(version, fixture.adapter.committedVersion)
        assertSame(drawable, image.drawable)

        image.alpha = 0f // The platform still owns the thumbnail's visibility.
        fixture.result(row.path)
        fixture.frame()
        assertFalse(holder.isRecyclable)
        assertEquals(PhotoViewerTransition.ELEMENT_NAME, image.transitionName)

        image.alpha = 1f
        assertTrue(image.isShown)
        assertEquals(View.VISIBLE, image.windowVisibility)
        fixture.draw()
        assertFalse("Pre-draw can be cancelled and is not a drawing acknowledgement", holder.isRecyclable)
        fixture.frame()
        drain(fixture.tasks)
        assertTrue(holder.isRecyclable)
        assertNull(image.transitionName)
        assertEquals(version + 1, fixture.adapter.committedVersion)
        assertEquals(updated.size, (fixture.adapter.media.single() as Medium).size)
    }

    @Test fun staleViewerResultAndCancelledTransitionCannotReleaseTheNextLaunch() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.launch(row.path)
        val previousLaunch = fixture.launchId
        val previousTransition = fixture.activity.window.sharedElementReenterTransition
        fixture.reenter(row.path, id = 0L)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.end()

        fixture.launch(row.path)
        fixture.reenter(row.path, id = 0L)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.terminal(previousTransition, cancelled = true)
        fixture.result(row.path, id = 0L, launch = previousLaunch)
        fixture.frame()
        assertFalse(holder.isRecyclable)

        // The current result can also precede the local transition's termination.
        fixture.result(row.path, id = 0L)
        fixture.frame()
        assertFalse(holder.isRecyclable)
        fixture.terminal(cancelled = true)
        fixture.frame()
        assertTrue(holder.isRecyclable)
        fixture.result(row.path, id = 0L)
        fixture.frame()
        assertNull(image.transitionName)
        assertSame(holder, fixture.grid.findViewHolderForAdapterPosition(0))
    }

    @Config(sdk = [36])
    @Test fun whileFingerIsDownSwitchedTargetAloneMakesRoomForTheProxy() {
        val rows = listOf(item("a.jpg"), item("b.jpg"))
        val fixture = ReturnGridFixture(rows, columns = 2)
        val source = fixture.thumbnail(0, rows[0].path)
        val target = fixture.thumbnail(1, rows[1].path)
        fixture.launch(rows[0].path)
        // AOSP's outbound coordinator has hidden the original source thumbnail.
        source.alpha = 0f
        source.transitionAlpha = 0f

        val first = fixture.drag(rows[1].path)
        assertEquals(1f, source.alpha, 0f)
        assertEquals(1f, source.transitionAlpha, 0f)
        assertEquals(0f, target.alpha, 0f)
        fixture.cancelDrag(first)
        assertEquals(1f, target.alpha, 0f)

        val second = fixture.drag(rows[1].path)
        assertNotEquals(first, second)
        fixture.reenter(rows[1].path)
        fixture.draw()
        assertSame(target, fixture.map())
        assertEquals(1f, target.alpha, 0f)
        fixture.end()
        assertEquals(1f, source.alpha, 0f)
    }

    @Test fun recyclingTheHeldDestinationClearsItsAlphaBeforeTheNextBinding() {
        val rows = listOf(item("a.jpg"), item("b.jpg"))
        val fixture = ReturnGridFixture(rows, columns = 2)
        fixture.thumbnail(0, rows[0].path)
        val target = fixture.thumbnail(1, rows[1].path)
        fixture.launch(rows[0].path)
        val token = fixture.drag(rows[1].path)
        assertEquals(0f, target.alpha, 0f)

        val holder = fixture.grid.findViewHolderForAdapterPosition(1) as MyRecyclerViewAdapter.ViewHolder
        fixture.adapter.onViewRecycled(holder)
        assertEquals(1f, target.alpha, 0f)
        target.alpha = .5f
        fixture.cancelDrag(token)
        assertEquals(.5f, target.alpha, 0f)
    }

    @Test fun absentPartialTargetUnblocksTheGridWithoutWaitingForTheSharedTimeout() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row), columns = 2)
        fixture.thumbnail(0, row.path)
        val target = "/photos/b.jpg"
        fixture.reenter(target)
        assertFalse(fixture.postponed)
        assertNull(fixture.map())
        val session = fixture.type.getDeclaredField("transitionSession").apply { isAccessible = true }
            .get(fixture.activity) as MediaTransitionSessionCoordinator
        assertEquals(MediaTransitionSessionCoordinator.State.FALLBACK, session.outcome)
        fixture.result(target)
        val pending = fixture.type.getDeclaredField("pendingViewerReturnPath").apply { isAccessible = true }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1_500))
        assertEquals(target, pending.get(fixture.activity))
        val retryAfterCommit = fixture.type.getDeclaredMethod("retryFallbackViewerPositionAfterCommit")
            .apply { isAccessible = true }
        fixture.adapter.updateMedia(arrayListOf(row, item("b.jpg")), onCommitted = {
            retryAfterCommit.invoke(fixture.activity)
        })
        drain(fixture.tasks)
        fixture.layout()
        assertEquals(2, fixture.adapter.itemCount)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(64))
        assertNull(pending.get(fixture.activity))
        assertFalse(fixture.postponed)
    }

    @Test fun latePartialTargetDoesNotTakeOverAfterTheFastScrollerStarts() {
        val row = item("a.jpg")
        val target = item("b.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        fixture.reenter(target.path)
        fixture.result(target.path)
        val pending = fixture.type.getDeclaredField("pendingViewerReturnPath").apply { isAccessible = true }
        assertEquals(target.path, pending.get(fixture.activity))

        val fastScroll = fixture.type.getDeclaredMethod("onPhotoFastScrollStateChanged", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        fastScroll.invoke(fixture.activity, true)
        fastScroll.invoke(fixture.activity, false)
        fixture.adapter.updateMedia(arrayListOf(row, target))
        fixture.type.getDeclaredMethod("retryFallbackViewerPositionAfterCommit")
            .apply { isAccessible = true }.invoke(fixture.activity)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(pending.get(fixture.activity))
        assertEquals(0, (fixture.grid.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
    }

    @Test fun resultWithoutReenterClearsTheLaunchPath() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        fixture.thumbnail(0, row.path)
        fixture.launch(row.path)
        fixture.result(null)
        val pending = fixture.activity.javaClass.getDeclaredField("pendingViewerReturnPath")
            .apply { isAccessible = true }
        assertNull("a closed Viewer cannot leave its original launch path pending", pending.get(fixture.activity))
    }

    @Test fun resultBeforeReenterDoesNotAcquireAHolderOrPublicationHold() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.result(row.path, id = 0L)
        fixture.reenter(row.path, id = 0L)
        fixture.frame()
        assertFalse(fixture.postponed)
        assertTrue(holder.isRecyclable)
        assertNull(image.transitionName)
        assertNull(fixture.map())
    }

    @Test fun aReplacedDrawableCannotAcknowledgeThePreviousThumbnailHandoff() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.launch(row.path)
        fixture.reenter(row.path)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.terminal()
        fixture.result(row.path)

        val replacement = ColorDrawable(android.graphics.Color.GREEN)
        image.setImageDrawable(replacement)
        fixture.layout()
        fixture.frame()
        assertTrue(holder.isRecyclable)
        assertSame(replacement, image.drawable)
        assertNull(image.transitionName)
    }

    @Test fun anUndrawnMappedTargetTimesOutAndReleasesItsHolder() {
        val row = item("a.jpg")
        val fixture = ReturnGridFixture(listOf(row))
        val image = fixture.thumbnail(0, row.path)
        val holder = fixture.grid.findViewHolderForAdapterPosition(0)!!
        fixture.launch(row.path)
        fixture.reenter(row.path)
        fixture.draw()
        assertSame(image, fixture.map())
        fixture.terminal()
        fixture.result(row.path)
        assertFalse(holder.isRecyclable)
        fixture.timeout()
        fixture.frame()
        assertTrue(holder.isRecyclable)
        assertNull(image.transitionName)
    }

    private fun drain(tasks: ArrayDeque<Runnable>) {
        repeat(100) {
            if (tasks.isEmpty()) return
            tasks.removeFirst().run()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        fail("Diff queue failed to drain")
    }

    @Test fun layoutAndDatabaseFieldsAreNotThumbnailContent() {
        val before = item("a.jpg")
        assertTrue(MediaUpdatePolicy.sameContent(before, before.copy(id = 8, gridPosition = 100, mediaStoreId = 9)))
        assertFalse(MediaUpdatePolicy.sameContent(before, before.copy(modified = 11)))
        assertTrue(MediaUpdatePolicy.durationOnly(before, before.copy(videoDuration = 30)))
        assertFalse(MediaUpdatePolicy.durationOnly(before, before.copy(videoDuration = 30, size = 101)))
    }

    @Test fun staleSelectionWithNoCurrentMediaDoesNotOpenDeleteConfirmation() = withAdapter { adapter, _, _ ->
        val keysField = MyRecyclerViewAdapter::class.java.getDeclaredField("selectedKeys").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val selectedKeys = keysField.get(adapter) as MutableSet<Int>
        selectedKeys += "stale-path".hashCode()

        val confirm = MediaAdapter::class.java.getDeclaredMethod("askConfirmDelete").apply { isAccessible = true }
        confirm.invoke(adapter)

        assertTrue(adapter.media.filterIsInstance<Medium>().isNotEmpty())
        assertTrue("The stale selection exits safely", selectedKeys.isEmpty())
    }

    @Test fun displayedSnapshotComparisonKeepsOrderAndSectionChangesVisible() {
        val first = arrayListOf<ThumbnailItem>(item("a.jpg"), ThumbnailSection("May", "2026-05"))
        val internalRefresh = arrayListOf<ThumbnailItem>(
            item("a.jpg").copy(id = 42, mediaStoreId = 99, gridPosition = 2),
            ThumbnailSection("May", "2026-05")
        )
        val moved = arrayListOf<ThumbnailItem>(ThumbnailSection("May", "2026-05"), item("a.jpg"))
        val changedSection = arrayListOf<ThumbnailItem>(item("a.jpg"), ThumbnailSection("June", "2026-06"))

        assertTrue(MediaUpdatePolicy.sameDisplayedContent(first, internalRefresh))
        assertFalse(MediaUpdatePolicy.sameDisplayedContent(first, moved))
        assertFalse(MediaUpdatePolicy.sameDisplayedContent(first, changedSection))
    }

    @Test fun confirmedViewerDeletionReachesSourceGridBeforeReturnTargetIsMapped() {
        val rows = listOf(item("a.jpg"), item("b.jpg"), item("c.jpg"))
        val fixture = ReturnGridFixture(rows, columns = 2)
        fixture.reenter("b.jpg", deletedPaths = listOf("/photos/a.jpg"))
        drain(fixture.tasks)
        assertEquals(listOf("b.jpg", "c.jpg"), fixture.adapter.media.filterIsInstance<Medium>().map { it.name })
        assertEquals("The surviving return identity stays in the source grid", 0,
            fixture.adapter.media.filterIsInstance<Medium>().indexOfFirst { it.path == "/photos/b.jpg" })
    }

    @Test fun confirmedViewerDeletionWithoutReturnPathStillRemovesLastGridItem() {
        val fixture = ReturnGridFixture(listOf(item("only.jpg")))
        fixture.result(path = null, deletedPaths = listOf("/photos/only.jpg"))
        drain(fixture.tasks)
        assertTrue(fixture.adapter.media.filterIsInstance<Medium>().isEmpty())
    }

    @Test fun deletedPathFilteringDropsEmptyGroupingHeaders() {
        val rows = arrayListOf<ThumbnailItem>(item("a.jpg"), ThumbnailSection("March", "2026-03"), item("b.jpg"))
        val onlySectionMedia = MediaUpdatePolicy.withoutDeletedPaths(rows, setOf("/photos/a.jpg"))
        assertEquals(listOf("March", "b.jpg"), onlySectionMedia.map {
            when (it) { is Medium -> it.name; is ThumbnailSection -> it.title; else -> "unexpected" }
        })
        assertTrue(MediaUpdatePolicy.withoutDeletedPaths(rows, setOf("/photos/a.jpg", "/photos/b.jpg")).isEmpty())
    }

    @Test fun adapterCommitsNewSortOrderWithoutRecreatingAdapter() = withAdapter { adapter, recycler, tasks ->
        val before = adapter
        adapter.updateMedia(arrayListOf(item("z.jpg"), item("a.jpg")), preserveScroll = true)
        drain(tasks)
        assertSame("Sorting keeps the existing adapter attached", before, recycler.adapter)
        assertEquals(listOf("z.jpg", "a.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun largePureReorderCommitsWithoutAnExpensiveContentDiff() = withAdapter { adapter, recycler, tasks ->
        val original = ArrayList<ThumbnailItem>(5_000)
        repeat(5_000) { original += item("photo-$it.jpg") }
        adapter.updateMedia(original, preserveScroll = false)
        drain(tasks)
        assertEquals(5_000, adapter.media.size)

        val versionBefore = adapter.committedVersion
        var committed = false
        val sorted = ArrayList<ThumbnailItem>(original.asReversed())
        adapter.updateMedia(sorted, preserveScroll = true, onCommitted = { committed = true })

        assertTrue("A sort of the same plain media set must not queue a quadratic DiffUtil calculation", tasks.isEmpty())
        assertTrue("The order-only update commits synchronously on the UI thread", committed)
        assertEquals(versionBefore + 1, adapter.committedVersion)
        assertSame(adapter, recycler.adapter)
        assertEquals("photo-4999.jpg", (adapter.media.first() as Medium).name)
        assertEquals("photo-0.jpg", (adapter.media.last() as Medium).name)
    }

    @Test fun coalescedAtoBtoABurstCommitsOnlyTheLastUserSnapshot() = withAdapter { adapter, _, tasks ->
        val a = arrayListOf<ThumbnailItem>(item("a.jpg"), item("b.jpg"), item("c.jpg"))
        val b = arrayListOf<ThumbnailItem>(item("c.jpg"), item("b.jpg"), item("a.jpg"))
        val commits = ArrayList<String>()

        adapter.updateMedia(b, preserveScroll = false, onCommitted = { commits += "B" })
        adapter.updateMedia(a, preserveScroll = false, onCommitted = { commits += "A" })
        adapter.updateMedia(b, preserveScroll = false, onCommitted = { commits += "B2" })
        adapter.updateMedia(a, preserveScroll = false, onCommitted = { commits += "A2" })

        drain(tasks)

        assertEquals(listOf("A2"), commits)
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun searchNarrowingAndClearDuringAnInflightDiffCommitsTheLatestFullSnapshot() =
        withAdapter { adapter, _, tasks ->
            val full = ArrayList<ThumbnailItem>((0 until 200).map { item("image-$it.jpg") })
            adapter.updateMedia(full, preserveScroll = false)
            drain(tasks)
            val firstQuery = ArrayList<ThumbnailItem>(full.filterIsInstance<Medium>().take(120))
            val secondQuery = ArrayList<ThumbnailItem>(full.filterIsInstance<Medium>().take(12))
            val commits = ArrayList<String>()

            adapter.updateMedia(firstQuery, preserveScroll = false, onCommitted = { commits += "first" })
            adapter.updateMedia(secondQuery, preserveScroll = false, onCommitted = { commits += "second" })
            adapter.updateMedia(full, preserveScroll = false, onCommitted = { commits += "clear" })
            drain(tasks)

            assertEquals(listOf("clear"), commits)
            assertEquals(full.map { (it as Medium).path }, adapter.media.filterIsInstance<Medium>().map { it.path })
        }

    @Test fun groupedSortRowsRemainOnTheDiffUtilPath() = withAdapter { adapter, _, tasks ->
        val grouped = arrayListOf<ThumbnailItem>(
            ThumbnailSection("June", "2026-06"), item("a.jpg"), item("b.jpg")
        )
        adapter.updateMedia(grouped, preserveScroll = false)
        drain(tasks)
        val reorderedWithinSection = arrayListOf<ThumbnailItem>(
            ThumbnailSection("June", "2026-06"), item("b.jpg"), item("a.jpg")
        )

        adapter.updateMedia(reorderedWithinSection, preserveScroll = false)

        assertEquals("Grouped rows are not flattened into the plain-list shortcut", 1, tasks.size)
        drain(tasks)
        assertEquals(listOf("b.jpg", "a.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun persistenceAndGridPositionChangesDoNotScheduleDiffOrNotifyRows() = withAdapter { adapter, _, tasks ->
        var notifications = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { notifications++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { notifications++ }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { notifications++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { notifications++ }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { notifications++ }
        })
        val refreshed = item("a.jpg").copy(id = 42, mediaStoreId = 99, gridPosition = 17)

        adapter.updateMedia(arrayListOf(refreshed), preserveScroll = false)

        assertTrue("No background DiffUtil work should be queued", tasks.isEmpty())
        assertEquals("No RecyclerView operation should be dispatched", 0, notifications)
        assertEquals(42L, (adapter.media.single() as Medium).id)
        assertEquals(99L, (adapter.media.single() as Medium).mediaStoreId)
    }

    @Test fun prefixPreservingScanGrowthUsesAppendCommitWithoutDiff() = withAdapter { adapter, _, tasks ->
        val inserted = mutableListOf<Pair<Int, Int>>()
        var fullChanges = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                inserted += positionStart to itemCount
            }
            override fun onChanged() { fullChanges++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { fullChanges++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { fullChanges++ }
        })

        adapter.updateMedia(arrayListOf(item("a.jpg"), item("b.jpg")), preserveScroll = true,
            diagnosticSnapshotStage = "PARTIAL")

        assertTrue("A prefix-preserving stage must not queue DiffUtil", tasks.isEmpty())
        assertEquals(listOf(1 to 1), inserted)
        assertEquals("No existing row may be rebound or replaced", 0, fullChanges)
        assertEquals(listOf("a.jpg", "b.jpg"),
            adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun dateDescendingHeadInsertionIsNeverMistakenForAnAppend() = withAdapter { adapter, _, tasks ->
        adapter.updateMedia(arrayListOf(item("newest.jpg"), item("a.jpg")), preserveScroll = true,
            diagnosticSnapshotStage = "PARTIAL")

        assertEquals("Inserting before the visible prefix must still diff", 1, tasks.size)
        drain(tasks)
        assertEquals(listOf("newest.jpg", "a.jpg"),
            adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun identicalPartialSnapshotStaysANoOp() = withAdapter { adapter, _, tasks ->
        var notifications = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { notifications++ }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { notifications++ }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { notifications++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { notifications++ }
        })

        adapter.updateMedia(arrayListOf(item("a.jpg")), preserveScroll = true,
            diagnosticSnapshotStage = "PARTIAL")

        assertTrue("An unchanged preview must not schedule a diff", tasks.isEmpty())
        assertEquals("An unchanged preview must not notify RecyclerView", 0, notifications)
    }

    @Test fun realMetadataAndOrderChangesStillScheduleDiffs() = withAdapter { adapter, _, tasks ->
        adapter.updateMedia(arrayListOf(item("a.jpg").copy(modified = 11)), preserveScroll = false)
        assertEquals(1, tasks.size)
        drain(tasks)

        adapter.updateMedia(arrayListOf(item("b.jpg"), item("a.jpg").copy(modified = 11)), preserveScroll = false)
        assertEquals(1, tasks.size)
        drain(tasks)

        assertEquals(listOf("b.jpg", "a.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun burstKeepsOneDiffAndNewestPendingSnapshot() = withAdapter { adapter, recycler, tasks ->
        assertNull("Refreshes must not animate the whole grid", recycler.itemAnimator)
        val incoming = arrayListOf<ThumbnailItem>(item("first.jpg"))
        adapter.updateMedia(incoming, preserveScroll = false)
        incoming.clear()
        repeat(30) { adapter.updateMedia(arrayListOf(item("$it.jpg")), preserveScroll = false) }
        assertEquals(1, tasks.size)
        drain(tasks)
        assertEquals(listOf("29.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun fastScrollKeepsNewestPartialToCompleteSnapshotUntilRelease() = withAdapter { adapter, recycler, tasks ->
        adapter.setFastScrollActive(true)
        adapter.updateMedia(arrayListOf(item("partial.jpg")), preserveScroll = true,
            diagnosticSnapshotStage = "SCAN_PARTIAL")
        adapter.updateMedia(arrayListOf(item("complete.jpg")), preserveScroll = true,
            diagnosticSnapshotStage = "COMPLETE")

        assertEquals("The grid remains stable throughout the thumb session", listOf("a.jpg"),
            adapter.media.filterIsInstance<Medium>().map { it.name })
        assertSame(adapter, recycler.adapter)
        assertTrue(tasks.isEmpty())

        adapter.setFastScrollActive(false, submitPending = false)
        adapter.submitPendingUpdateAfterFastScroll()
        drain(tasks)

        assertEquals(listOf("complete.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
    }

    @Test fun secondDragKeepsNewDiffPendingAndRestoresFinalPathInBothMediaScopes() {
        for (path in listOf("/photos", PhotoProfileMediaLoader.PATH)) withAdapter(path) { adapter, recycler, tasks ->
            val manager = recycler.layoutManager as LinearLayoutManager
            fun layout(draw: Boolean = true) {
                val exact = View.MeasureSpec.EXACTLY
                recycler.measure(View.MeasureSpec.makeMeasureSpec(480, exact),
                    View.MeasureSpec.makeMeasureSpec(600, exact))
                recycler.layout(0, 0, 480, 600)
                if (draw) recycler.viewTreeObserver.dispatchOnPreDraw()
            }
            val full = ArrayList<ThumbnailItem>((0 until 80).map { item("image-$it.jpg") })
            adapter.updateMedia(full, preserveScroll = false)
            drain(tasks)
            layout()
            val commits = mutableListOf<String>()
            adapter.updateMedia(ArrayList(full.drop(1)), onCommitted = { commits += "in-flight" })
            adapter.setFastScrollActive(true)
            manager.scrollToPositionWithOffset(20, -23)
            layout()
            drain(tasks)
            assertTrue(commits.isEmpty())
            adapter.updateMedia(ArrayList(full.drop(2)), onCommitted = { commits += "partial" })
            adapter.updateMedia(ArrayList(full.drop(3)), onCommitted = { commits += "complete" })
            adapter.setFastScrollActive(false)
            drain(tasks)

            // A second thumb gesture arrives before the first restore is laid out.
            adapter.setFastScrollActive(true)
            layout()
            manager.scrollToPositionWithOffset(35, -17)
            layout()
            val position = manager.findFirstVisibleItemPosition()
            val anchor = adapter.media[position] as Medium
            val offset = manager.getDecoratedTop(requireNotNull(manager.findViewByPosition(position)))
            val latest = ArrayList<ThumbnailItem>(adapter.media.drop(5) + item("new.jpg"))
            adapter.updateMedia(latest, onCommitted = { commits += "latest" })
            adapter.setFastScrollActive(false)
            drain(tasks)
            layout()

            assertEquals(listOf("complete", "latest"), commits)
            assertEquals(latest, adapter.media)
            val restored = requireNotNull(manager.findViewByPosition(adapter.media.indexOf(anchor)))
            assertEquals(offset, manager.getDecoratedTop(restored))
        }
    }

    @Test fun confirmedDeletionSupersedesAnInFlightProgressiveDiffByPath() = withAdapter { adapter, _, tasks ->
        val removedNotifications = ArrayList<Pair<Int, Int>>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                removedNotifications += positionStart to itemCount
            }
        })
        var scanIsCurrent = true
        adapter.updateMedia(arrayListOf<ThumbnailItem>(
            item("a.jpg"), ThumbnailSection("March", "2026-03"), item("b.jpg")
        ), preserveScroll = false, isCurrent = { scanIsCurrent })
        assertEquals("The first progressive diff is in flight", 1, tasks.size)

        // A newer deletion succeeds while the previous scan diff is still computing.
        // The old request expires and the adapter schedules a path-based update.
        scanIsCurrent = false
        adapter.removeMediaPaths(setOf("/photos/a.jpg"))
        drain(tasks)

        assertFalse(adapter.media.filterIsInstance<Medium>().any { it.path in setOf("/photos/a.jpg", "/photos/b.jpg") })
        assertTrue("An expired scan stage must not restore stale rows", adapter.media.none { it is Medium })
        assertTrue("No stale positional remove notification is emitted", removedNotifications.isNotEmpty())
        removedNotifications.forEach { (position, count) ->
            assertTrue(position >= 0)
            assertTrue(count > 0)
        }
    }

    @Test fun confirmedDeletionKeepsUnselectedRowsAndUsesDiffNotifications() = withAdapter { adapter, _, tasks ->
        adapter.updateMedia(arrayListOf<ThumbnailItem>(item("a.jpg"), item("b.jpg"), item("c.jpg")),
            preserveScroll = false)
        drain(tasks)
        var changedAll = 0
        var removedRows = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { changedAll++ }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { removedRows += itemCount }
        })

        adapter.removeMediaPaths(setOf("/photos/b.jpg", "/photos/stale.jpg"))
        drain(tasks)

        assertEquals(listOf("a.jpg", "c.jpg"), adapter.media.filterIsInstance<Medium>().map { it.name })
        assertEquals(0, changedAll)
        assertEquals(1, removedRows)
    }

    @Test fun scrollingDefersCatalogAndIdleCommitsNewestResult() = withAdapter { adapter, recycler, tasks ->
        val setState = RecyclerView::class.java.getDeclaredMethod("setScrollState", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        setState.invoke(recycler, RecyclerView.SCROLL_STATE_DRAGGING)
        adapter.updateMedia(arrayListOf(item("old.jpg")), preserveScroll = false)
        adapter.updateMedia(arrayListOf(item("new.jpg")), preserveScroll = false)
        assertTrue(tasks.isEmpty())
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        setState.invoke(recycler, RecyclerView.SCROLL_STATE_IDLE)
        drain(tasks)
        assertEquals("new.jpg", (adapter.media.single() as Medium).name)
    }

    @Test fun authoritativeDiffSurvivesLayoutAndFlingWithoutRecomputing() = withAdapter { adapter, recycler, tasks ->
        val manager = recycler.layoutManager as LinearLayoutManager
        val setState = RecyclerView::class.java.getDeclaredMethod("setScrollState", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val layoutCounter = RecyclerView::class.java.getDeclaredField("mLayoutOrScrollCounter")
            .apply { isAccessible = true }
        fun layout() {
            recycler.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 320, 320)
        }
        val preview = ArrayList<ThumbnailItem>((0 until 1454).map { item("preview-$it.jpg") })
        val complete = ArrayList<ThumbnailItem>((0 until 1626).map { item("added-$it.jpg") } + preview)
        adapter.updateMedia(preview, preserveScroll = false)
        drain(tasks)
        manager.scrollToPositionWithOffset(325, -17)
        layout()
        val anchor = manager.findFirstVisibleItemPosition()
        val anchorView = requireNotNull(manager.findViewByPosition(anchor))
        val offset = manager.getDecoratedTop(anchorView)
        val image = anchorView.findViewById<ImageView>(R.id.medium_thumbnail)
        // Model a completed image request without the host decoder racing an error
        // for the fixture's nonexistent media path.
        com.bumptech.glide.Glide.with(recycler.context).clear(image)
        image.setTag(R.id.thumbnail_loaded_key, image.getTag(R.id.thumbnail_request_key))
        val drawable = ColorDrawable(android.graphics.Color.BLUE)
        image.setImageDrawable(drawable)
        val height = image.height
        val version = adapter.committedVersion

        var commits = 0
        adapter.updateMedia(complete, onCommitted = { commits++ },
            diagnosticSnapshotStage = DisplayedSourceStage.SCAN_COMPLETE.name)
        setState.invoke(recycler, RecyclerView.SCROLL_STATE_SETTLING)
        assertEquals(1, tasks.size)
        layoutCounter.setInt(recycler, 1)
        try {
            tasks.removeFirst().run()
            assertEquals(version, adapter.committedVersion)
            assertSame(drawable, image.drawable)
        } finally {
            layoutCounter.setInt(recycler, 0)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        assertEquals(1, commits)
        assertEquals(version + 1, adapter.committedVersion)
        assertTrue("The completed diff must not be scheduled again", tasks.isEmpty())

        layout()
        val moved = complete.indexOf(preview[anchor])
        val restored = requireNotNull(manager.findViewByPosition(moved))
        assertEquals(offset, manager.getDecoratedTop(restored))
        val restoredImage = restored.findViewById<ImageView>(R.id.medium_thumbnail)
        assertEquals((preview[anchor] as Medium).path, restoredImage.getTag(R.id.media_bound_path))
        assertEquals(height, restoredImage.height)
        assertSame(drawable, restoredImage.drawable)
    }

    @Test fun expiredRequestAndDetachedAdapterCannotPublish() = withAdapter { adapter, recycler, tasks ->
        var current = true
        adapter.updateMedia(arrayListOf(item("stale.jpg")), isCurrent = { current })
        current = false
        drain(tasks)
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        adapter.updateMedia(arrayListOf(item("detached.jpg")))
        recycler.adapter = null
        drain(tasks)
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
    }

    @Test fun durationUpdateUsesPayloadAndDoesNotQueueCatalogDiff() = withAdapter { adapter, _, tasks ->
        var changes = 0
        var payload: Any? = null
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, value: Any?) {
                changes += itemCount; payload = value
            }
        })
        adapter.updateVideoDurations(listOf(item("a.jpg").copy(videoDuration = 30)))
        assertEquals(1, changes)
        assertNotNull(payload)
        assertTrue(tasks.isEmpty())
        assertEquals(30, (adapter.media.single() as Medium).videoDuration)
        adapter.updateVideoDurations(listOf(item("a.jpg").copy(videoDuration = 30)))
        assertEquals(1, changes)
    }

    @Test fun heldDiffCannotBlockOrOverwriteNewSortAndSearch() = withAdapter { adapter, recycler, tasks ->
        for (count in listOf(2012, 5000, 11023)) {
            val full = ArrayList<ThumbnailItem>((0 until count).map { item("photo-$it.jpg") })
            adapter.updateMedia(full, preserveScroll = false)
            drain(tasks)
            // Identity order unchanged: metadata diff deliberately held by executor.
            val changed = ArrayList<ThumbnailItem>(full.map { (it as Medium).copy(modified = 20) })
            adapter.updateMedia(changed)
            assertEquals(1, tasks.size)
            val blocked = tasks.removeFirst()
            val sorted = ArrayList<ThumbnailItem>(changed.asReversed())
            var sortCommitted = false
            adapter.updateMedia(sorted, updateReason = MediaUpdateReason.USER_SORT,
                effectiveSorting = org.fossify.commons.helpers.SORT_BY_SIZE, onCommitted = { sortCommitted = true })
            assertTrue("Sort commits BEFORE releasing the old diff", sortCommitted)
            assertEquals(sorted, adapter.media)
            assertEquals(org.fossify.commons.helpers.SORT_BY_SIZE, adapter.sorting)
            val matches = ArrayList<ThumbnailItem>(sorted.take(12))
            var searchCommitted = false
            adapter.updateMedia(matches, preserveScroll = false, updateReason = MediaUpdateReason.USER_SEARCH,
                onCommitted = { searchCommitted = true })
            assertTrue("Search commits BEFORE releasing the old diff", searchCommitted)
            assertEquals(matches, adapter.media)
            assertEquals(12, adapter.itemCount)
            assertSame(adapter, recycler.adapter)
            val version = adapter.committedVersion
            blocked.run()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(version, adapter.committedVersion)
            assertEquals(matches, adapter.media)
            assertTrue(tasks.isEmpty())
            adapter.updateMedia(full, preserveScroll = false, updateReason = MediaUpdateReason.USER_SEARCH)
            drain(tasks)
            assertEquals(count, adapter.itemCount)
        }
    }

    @Test fun largeFinalPermutationAndSearchClearDoNotQueueUnboundedDiffWork() = withAdapter { adapter, _, tasks ->
        val full = ArrayList<ThumbnailItem>((0 until 11023).map { item("photo-$it.jpg") })
        adapter.updateMedia(full, preserveScroll = false)
        drain(tasks)
        val final = ArrayList<ThumbnailItem>(full.asReversed().map { (it as Medium).copy(modified = 20) })
        adapter.updateMedia(final)
        assertTrue(tasks.isEmpty())
        assertEquals(final, adapter.media)
        for (query in listOf("1", "10", "1", "")) {
            val expected = ArrayList<ThumbnailItem>(final.filter { (it as Medium).name.contains(query) })
            adapter.updateMedia(expected, preserveScroll = false, updateReason = MediaUpdateReason.USER_SEARCH)
            drain(tasks)
            assertEquals(expected, adapter.media)
        }
    }

    @Test fun recordHostCommitCostsForLargeLists() = withAdapter { adapter, _, tasks ->
        val rows = mutableListOf("scenario,count,submitToCommitMs,queuedDiffs,finalCount")
        fun measure(label: String, data: ArrayList<ThumbnailItem>, reason: MediaUpdateReason) {
            val start = System.nanoTime()
            var elapsed = -1.0
            adapter.updateMedia(data, preserveScroll = false, updateReason = reason,
                onCommitted = { elapsed = (System.nanoTime() - start) / 1_000_000.0 })
            val queued = tasks.size
            drain(tasks)
            assertTrue(elapsed >= 0)
            assertEquals(data, adapter.media)
            rows += "$label,${data.size},$elapsed,$queued,${adapter.itemCount}"
        }
        for (count in listOf(2012, 5000, 11023)) {
            val full = ArrayList<ThumbnailItem>((0 until count).map { item("$it.jpg") })
            measure("initial", full, MediaUpdateReason.SCAN)
            measure("sort", ArrayList(full.asReversed()), MediaUpdateReason.USER_SORT)
            if (count == 11023) {
                measure("final_changed_order_and_metadata",
                    ArrayList(full.map { (it as Medium).copy(modified = 30) }), MediaUpdateReason.SCAN)
                measure("search_narrow", ArrayList(full.take(120)), MediaUpdateReason.USER_SEARCH)
                measure("search_clear", full, MediaUpdateReason.USER_SEARCH)
                measure("metadata_only", ArrayList(full.map { (it as Medium).copy(modified = 40) }),
                    MediaUpdateReason.SCAN)
            }
        }
        val report = java.io.File("build/reports/ui-regression/media-commit-host.csv")
        report.parentFile.mkdirs()
        report.writeText(rows.joinToString("\n") + "\n")
        println(rows.joinToString("\n"))
    }

    @Test fun permissionLossBlocksFastReplacementAndOldDiff() = withAdapter { adapter, recycler, tasks ->
        var access = true
        adapter.updateMedia(arrayListOf(item("old.jpg")), isCurrent = { access })
        val blocked = tasks.removeFirst()
        access = false
        adapter.updateMedia(ArrayList((0 until 11023).map { item("$it.jpg") }),
            updateReason = MediaUpdateReason.USER_SEARCH, isCurrent = { access })
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        assertSame(adapter, recycler.adapter)
        // The older request also loses authorization before its callback.
        blocked.run()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
    }

    @Test fun destroyedActivityCannotReceiveTheHeldDiff() {
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).setup()
        val activity = controller.get()
        val recycler = MyRecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
        activity.setContentView(recycler)
        val tasks = ArrayDeque<Runnable>()
        val adapter = MediaAdapter(activity, arrayListOf(item("a.jpg")), null, false, false,
            "/photos", recycler, Executor { tasks.addLast(it) }) {}
        recycler.adapter = adapter
        adapter.updateMedia(arrayListOf(item("late.jpg")))
        assertEquals(1, tasks.size)
        controller.pause().stop().destroy()
        tasks.removeFirst().run()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a.jpg", (adapter.media.single() as Medium).name)
        assertEquals(1L, adapter.committedVersion)
    }

    @Test fun rejectedExecutorRecoversOnceWithoutLeavingTheAdapterStuck() = withAdapter { _, recycler, tasks ->
        val activity = recycler.context as SimpleActivity
        val rejecting = MediaAdapter(activity, arrayListOf(item("a.jpg")), null, false, false,
            "/photos", recycler, Executor { throw java.util.concurrent.RejectedExecutionException() }) {}
        recycler.adapter = rejecting
        rejecting.updateMedia(arrayListOf(item("new.jpg")))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("new.jpg", (rejecting.media.single() as Medium).name)
        assertTrue(tasks.isEmpty())
    }

    @Test fun metadataDiffAndGroupedReplacementKeepTheirStrategies() = withAdapter { adapter, _, tasks ->
        val full = ArrayList<ThumbnailItem>((0 until 11023).map { item("$it.jpg") })
        adapter.updateMedia(full, preserveScroll = false)
        drain(tasks)
        adapter.updateMedia(ArrayList(full.map { (it as Medium).copy(modified = 30) }))
        assertEquals(1, tasks.size)
        val blocked = tasks.removeFirst()
        val grouped = arrayListOf<ThumbnailItem>(ThumbnailSection("June", "june"), item("x.jpg"))
        adapter.updateMedia(grouped, updateReason = MediaUpdateReason.USER_SEARCH)
        assertEquals(grouped, adapter.media)
        assertTrue(adapter.media.first() is ThumbnailSection)
        blocked.run()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(grouped, adapter.media)
    }

    @Test fun thumbDragOnlyStartsRequestsForTheSettledViewport() = withAdapter { adapter, recycler, tasks ->
        val layoutManager = recycler.layoutManager as LinearLayoutManager
        val rows = ArrayList<ThumbnailItem>((0 until 3080).map { item("photo-$it.jpg") })
        adapter.updateMedia(rows, preserveScroll = false)
        drain(tasks)
        val version = adapter.committedVersion

        fun jump(position: Int): ImageView {
            layoutManager.scrollToPositionWithOffset(position, 0)
            recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 480, 800)
            return recycler.findViewHolderForAdapterPosition(position)!!.itemView.findViewById(R.id.medium_thumbnail)
        }
        fun request(image: ImageView) = com.bumptech.glide.request.target.DrawableImageViewTarget(image).request

        jump(0)
        adapter.setFastScrollActive(true)
        val intermediate = jump(2700)
        assertNull("Do not submit Glide for a transient thumb position", request(intermediate))
        val landing = jump(1200)
        assertNull(request(landing))
        adapter.setFastScrollActive(false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        assertEquals("/photos/photo-1200.jpg", landing.getTag(R.id.media_bound_path))
        assertNotNull("Release must start the current visible request", request(landing))
        adapter.setFastScrollActive(true)
        val paused = jump(1700)
        assertNull(request(paused))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(64))
        val pausedRequest = request(paused)
        assertNotNull("A thumb pause still loads its visible viewport", pausedRequest)
        adapter.setFastScrollActive(false)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        assertSame(pausedRequest, request(paused))
        assertEquals(version, adapter.committedVersion)
        assertTrue(tasks.isEmpty())
    }

    @Test fun filterProjectionPreservesTheAttachedSurvivorsDrawable() = withAdapter { adapter, recycler, tasks ->
        val video = item("retained.mp4").copy(type = TYPE_VIDEOS)
        val rows = arrayListOf<ThumbnailItem>(video, item("excluded.jpg"))
        adapter.updateMedia(rows, preserveScroll = false)
        drain(tasks)
        fun layout() {
            recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 480, 800)
        }
        layout()
        val image = recycler.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
        com.bumptech.glide.Glide.with(recycler.context).clear(image)
        val drawable = ColorDrawable(android.graphics.Color.BLUE)
        val key = requireNotNull(image.getTag(R.id.thumbnail_request_key))
        image.setTag(R.id.thumbnail_loaded_key, key)
        image.setImageDrawable(drawable)

        val filtered = MediaUpdatePolicy.retainMedia(rows) { it.type and TYPE_VIDEOS != 0 }
        adapter.updateMedia(filtered, updateReason = MediaUpdateReason.USER_FILTER)
        assertTrue("A filter should publish without queuing DiffUtil", tasks.isEmpty())
        layout()

        val retained = recycler.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
        assertSame(adapter, recycler.adapter)
        assertEquals(listOf(video), adapter.media)
        assertEquals(video.path, retained.getTag(R.id.media_bound_path))
        assertSame(key, retained.getTag(R.id.thumbnail_request_key))
        assertSame(drawable, retained.drawable)
    }

    @Test fun distantVisibleHoldersStartWithHighPriorityWithoutAListUpdate() = withAdapter { adapter, recycler, tasks ->
        val layoutManager = recycler.layoutManager as LinearLayoutManager
        adapter.updateMedia(ArrayList((0 until 3080).map { item("photo-$it.jpg") }), preserveScroll = false)
        drain(tasks)
        val version = adapter.committedVersion
        layoutManager.scrollToPositionWithOffset(2600, 0)
        recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 480, 800)

        val holder = recycler.findViewHolderForAdapterPosition(2600)!!
        val image = holder.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
        val request = com.bumptech.glide.request.target.DrawableImageViewTarget(image).request!!
        val priority = request.javaClass.getDeclaredField("priority").apply { isAccessible = true }.get(request)
        assertTrue(image.height > 0)
        assertEquals("/photos/photo-2600.jpg", image.getTag(R.id.media_bound_path))
        assertEquals(version, adapter.committedVersion)
        assertTrue(tasks.isEmpty())
        assertEquals(com.bumptech.glide.Priority.HIGH, priority)
    }

    @Test fun oldGlideCallbacksCannotPublishAfterAToBToARebind() = withAdapter { adapter, recycler, tasks ->
        val rows = arrayListOf<ThumbnailItem>(item("a.jpg"), item("b.jpg"))
        adapter.updateMedia(rows, preserveScroll = false)
        drain(tasks)
        recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 480, 800)
        val holder = recycler.findViewHolderForAdapterPosition(0) as MyRecyclerViewAdapter.ViewHolder
        val image = holder.itemView.findViewById<ImageView>(R.id.medium_thumbnail)
        val target = com.bumptech.glide.request.target.DrawableImageViewTarget(image)

        fun bind(position: Int): Pair<Any, com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>> {
            adapter.onBindViewHolder(holder, position)
            val request = target.request!!
            val field = request.javaClass.getDeclaredField("requestListeners").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val listener = (field.get(request) as List<com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>>).last()
            return image.getTag(R.id.thumbnail_request_key) to listener
        }

        val firstA = bind(0)
        val b = bind(1)
        val currentA = bind(0)
        assertEquals(firstA.first, currentA.first)
        assertNotSame(firstA.first, currentA.first)
        val stale = ColorDrawable(android.graphics.Color.RED)
        for (old in listOf(firstA, b)) {
            assertTrue("Stale delivery must be consumed before Glide updates the ImageView",
                old.second.onResourceReady(stale, "old", target, com.bumptech.glide.load.DataSource.LOCAL, true))
        }
        assertSame(currentA.first, image.getTag(R.id.thumbnail_request_key))
        assertNotSame(stale, image.drawable)
        assertNull(image.getTag(R.id.thumbnail_loaded_key))
    }

    @Test fun unchangedFullBindKeepsDrawableAndRequestToken() = withAdapter { adapter, recycler, _ ->
        val holder = adapter.onCreateViewHolder(recycler, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val image = holder.itemView.findViewById<android.widget.ImageView>(R.id.medium_thumbnail)
        val token = image.getTag(R.id.thumbnail_request_key)
        val displayed = ColorDrawable(android.graphics.Color.RED)
        image.setImageDrawable(displayed)
        adapter.onBindViewHolder(holder, 0)
        assertSame(token, image.getTag(R.id.thumbnail_request_key))
        assertSame(displayed, image.drawable)
        adapter.onViewRecycled(holder)
        assertNull(image.getTag(R.id.thumbnail_request_key))
    }

    @Test fun untouchedInitialLoadPinsNewEarlierRowsUntilUserIntentChanges() = withAdapter { adapter, recycler, tasks ->
        val manager = recycler.layoutManager as LinearLayoutManager
        fun frame() {
            recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 480, 600)
            recycler.viewTreeObserver.dispatchOnPreDraw()
        }
        val preview = ArrayList<ThumbnailItem>((0 until 20).map { item("old-$it.jpg") })
        adapter.updateMedia(preview, preserveScroll = false)
        drain(tasks)
        frame()
        var untouched = true
        val finalRows = ArrayList<ThumbnailItem>((0 until 3).map { item("earlier-$it.jpg") } + preview)
        adapter.updateMedia(finalRows, diagnosticSnapshotStage = "SCAN_COMPLETE", pinToStart = { untouched })
        drain(tasks)
        repeat(3) { frame() }
        assertEquals("new sorted predecessors belong on screen", 0, manager.findFirstVisibleItemPosition())
        manager.scrollToPositionWithOffset(6, 0)
        frame()
        untouched = false
        val before = (adapter.media[manager.findFirstVisibleItemPosition()] as Medium).path
        adapter.updateMedia(ArrayList(listOf(item("new.jpg")) + finalRows), pinToStart = { untouched })
        drain(tasks)
        repeat(3) { frame() }
        assertEquals("a user scroll supersedes the entry top intent", before,
            (adapter.media[manager.findFirstVisibleItemPosition()] as Medium).path)
    }

    @Test fun initialSourceRemainsStableUntilTheViewerFinishesEntry() {
        val rows = List(30) { item("source-$it.jpg") }
        val fixture = ReturnGridFixture(rows, columns = 2)
        fixture.thumbnail(0, rows[0].path)
        fixture.launch(rows[0].path)
        // onStop releases the source Holder before the Viewer necessarily finishes entry.
        fixture.type.getDeclaredMethod("releaseViewerSourceLease").apply { isAccessible = true }
            .invoke(fixture.activity)
        val version = fixture.adapter.committedVersion
        val complete = ArrayList<ThumbnailItem>(List(300) { item("new-$it.jpg") } + rows)
        fixture.adapter.updateMedia(complete)
        drain(fixture.tasks)
        assertEquals("entry must keep its exact source catalog", version, fixture.adapter.committedVersion)
        fixture.completeEntry()
        drain(fixture.tasks)
        fixture.layout()
        assertEquals(complete, fixture.adapter.media)
        assertEquals(version + 1, fixture.adapter.committedVersion)
    }

    @Test fun userScrollCancelsAnInitialTopOffsetAlreadyQueuedForLayout() = verifyQueuedTopCancellation(false)

    @Test fun fastScrollCancelsAnInitialTopOffsetWhileRecyclerRemainsIdle() = verifyQueuedTopCancellation(true)

    private fun verifyQueuedTopCancellation(fastScroll: Boolean) = withAdapter { adapter, recycler, tasks ->
        val manager = recycler.layoutManager as LinearLayoutManager
        val rows = ArrayList<ThumbnailItem>((0 until 40).map { item("row-$it.jpg") })
        adapter.updateMedia(rows, preserveScroll = false)
        drain(tasks)
        fun frame() {
            recycler.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            recycler.layout(0, 0, 480, 600)
            recycler.viewTreeObserver.dispatchOnPreDraw()
        }
        frame()
        manager.scrollToPositionWithOffset(10, 0)
        frame()
        // A pure SCAN reorder commits synchronously; do not drain the Looper,
        // which would consume the requested layout before the simulated gesture.
        adapter.updateMedia(ArrayList(rows.asReversed()), pinToStart = { true })
        val pending = MediaAdapter::class.java.getDeclaredField("initialTopLayoutPending").apply { isAccessible = true }
        assertTrue("the zero offset must still be awaiting layout", pending.getBoolean(adapter))
        val scroll = MediaAdapter::class.java.getDeclaredField("scrollUpdates").apply { isAccessible = true }
            .get(adapter) as RecyclerView.OnScrollListener
        if (fastScroll) adapter.setFastScrollActive(true)
        else scroll.onScrollStateChanged(recycler, RecyclerView.SCROLL_STATE_DRAGGING)
        frame()
        assertTrue("a new gesture must retire the queued zero offset", manager.findFirstVisibleItemPosition() > 0)
    }
}
