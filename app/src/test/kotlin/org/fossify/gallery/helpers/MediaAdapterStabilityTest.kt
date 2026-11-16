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

        fun reenter(path: String, id: Long = 1L, launch: Long = launchId) {
            activity.onActivityReenter(Activity.RESULT_OK, Intent()
                .putExtra(PhotoViewerTransition.EXTRA_RETURN_PATH, path)
                .putExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, id)
                .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, launch))
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

        fun sourceTimeout(): Runnable {
            val lease = type.getDeclaredField("viewerSourceLease").apply { isAccessible = true }.get(activity)
            return lease.javaClass.getDeclaredField("nameTimeout").apply { isAccessible = true }.get(lease) as Runnable
        }

        fun launch(path: String) {
            type.getDeclaredMethod("launchPhotoViewerWithSharedElement", Intent::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(activity, Intent(activity, org.fossify.gallery.activities.ViewPagerActivity::class.java), path)
        }

        fun terminal(transition: android.transition.Transition = activity.window.sharedElementReenterTransition, cancelled: Boolean = false) {
            val phases = if (cancelled) listOf("start", "cancel", "end") else listOf("start", "end")
            phases.forEach { phase ->
                android.transition.Transition::class.java.getDeclaredMethod(phase)
                    .apply { isAccessible = true }.invoke(transition)
            }
        }

        fun result(path: String, id: Long = 1L, launch: Long = launchId) {
            type.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
                .apply { isAccessible = true }.invoke(activity, PhotoViewerTransition.REQUEST_CODE, Activity.RESULT_OK, Intent()
                    .putExtra(PhotoViewerTransition.EXTRA_RETURN_PATH, path)
                    .putExtra(ViewerReturnDiagnostics.EXTRA_REQUEST_ID, id)
                    .putExtra(PhotoViewerTransition.EXTRA_LAUNCH, launch))

        }

        fun frame() {
            layout()
            draw()
            val observer = grid.viewTreeObserver
            observer.dispatchOnDraw()
            if (android.os.Build.VERSION.SDK_INT >= 29 && grid.isHardwareAccelerated) {
                // A Robolectric traversal has no render thread to acknowledge the buffer.
                @Suppress("UNCHECKED_CAST")
                val callbacks = observer.javaClass.getDeclaredMethod("captureFrameCommitCallbacks")
                    .apply { isAccessible = true }.invoke(observer) as? List<Runnable>
                callbacks?.forEach(Runnable::run)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
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
        assertTrue(fixture.postponed)
        fixture.timeout()
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
}
