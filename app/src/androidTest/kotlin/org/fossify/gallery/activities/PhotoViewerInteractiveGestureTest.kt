package org.fossify.gallery.activities

import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoriteFoldersDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GalleryNavigation
import org.fossify.gallery.helpers.GallerySection
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.PhotoProfile
import org.fossify.gallery.helpers.PhotoProfileMediaCache
import org.fossify.gallery.helpers.PhotoProfileMediaLoader
import org.fossify.gallery.helpers.PhotoProfileRepository
import org.fossify.gallery.helpers.PhotoViewerTransition
import org.fossify.gallery.helpers.PhotoSource
import org.fossify.gallery.helpers.PhotoSourceType
import org.fossify.gallery.models.FavoriteFolder
import org.fossify.gallery.models.Medium
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
    fun partialMediaPublicationKeepsGridVisibleUntilTheRangeIsFinal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryFastScrollerTest-${UUID.randomUUID()}"
        val fixtures = List(40) { createFixture(context, album) }
        val wasShowingAllMedia = context.config.showAll
        var source: MediaActivity? = null
        try {
            context.config.showAll = false

            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, java.io.File(fixtures.first().second).parent)
            }
            val activity = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            source = activity
            awaitAnySourceThumbnail(instrumentation, activity, fixtures.mapTo(HashSet()) { it.second })

            val scroller = activity.findViewById<View>(R.id.media_fastscroller)
            val thumb = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
            val headerProgress = MediaActivity::class.java.getDeclaredField("mediaHeaderProgress")
                .apply { isAccessible = true }
            val setMediaLoading = MediaActivity::class.java.getDeclaredMethod(
                "setMediaLoading", Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            instrumentation.runOnMainSync {
                setMediaLoading.invoke(activity, true)
            }
            assertEquals("background discovery after visible content must not show the header line",
                View.GONE, (headerProgress.get(activity) as View).visibility)
            val refresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(
                R.id.media_refresh_layout
            )
            instrumentation.runOnMainSync {
                refresh.isRefreshing = true
                setMediaLoading.invoke(activity, true)
            }
            assertTrue("manual refresh keeps its direct swipe feedback", refresh.isRefreshing)
            assertEquals("manual refresh also avoids the indeterminate header line",
                View.GONE, (headerProgress.get(activity) as View).visibility)
            instrumentation.runOnMainSync { refresh.isRefreshing = false }
            val ready = MediaActivity::class.java.getDeclaredMethod(
                "updateMediaFastScrollerVisibility", Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val final = MediaActivity::class.java.getDeclaredField("displayedMediaIsFinal")
                .apply { isAccessible = true }
            val scanning = MediaActivity::class.java.getDeclaredField("mIsGettingMedia")
                .apply { isAccessible = true }
            instrumentation.runOnMainSync {
                final.setBoolean(activity, false)
                scanning.setBoolean(activity, true)
                ready.invoke(activity, true)
            }
            instrumentation.waitForIdleSync()
            assertEquals("the media grid remains visible while its scroll range is provisional",
                View.VISIBLE, scroller.visibility)
            assertEquals("a scrollable partial result exposes the thumb", View.VISIBLE, thumb.visibility)
            assertEquals("a scrollable partial result exposes the track", View.VISIBLE, track.visibility)
            assertTrue("fast scrolling is enabled as soon as the current range can scroll",
                activity.findViewById<com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller>(
                    R.id.media_fastscroller
                ).isFastScrollEnabled)

            instrumentation.runOnMainSync {
                final.setBoolean(activity, true)
                ready.invoke(activity, true)
            }
            instrumentation.waitForIdleSync()
            assertEquals("a refresh keeps the previous grid visible", View.VISIBLE, scroller.visibility)
            assertEquals("a refresh retains the valid scroll range", View.VISIBLE, thumb.visibility)

            instrumentation.runOnMainSync {
                scanning.setBoolean(activity, false)
                ready.invoke(activity, true)
            }
            instrumentation.waitForIdleSync()
            assertEquals("the complete non-empty media range should expose the fast scroller",
                View.VISIBLE, scroller.visibility)
            assertEquals("the final thumb should be visible", View.VISIBLE, thumb.visibility)
            assertEquals("the final track should be visible", View.VISIBLE, track.visibility)
        } finally {
            context.config.showAll = wasShowingAllMedia
            source?.let { instrumentation.runOnMainSync { it.finish() } }
            fixtures.forEach { fixture -> runCatching { context.contentResolver.delete(fixture.first, null, null) } }
        }
    }

    @Test
    fun fastScrollerThumbDragMovesARealScrollableGrid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryFastScrollerDrag-${UUID.randomUUID()}"
        val fixtures = List(60) { createFixture(context, album) }
        var activity: MediaActivity? = null
        try {
            val intent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, java.io.File(fixtures.first().second).parent)
            }
            activity = instrumentation.startActivitySync(intent) as MediaActivity
            val source = activity!!
            awaitAnySourceThumbnail(instrumentation, source, fixtures.mapTo(HashSet()) { it.second })
            val grid = source.findViewById<RecyclerView>(R.id.media_grid)
            val scroller = source.findViewById<com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller>(
                R.id.media_fastscroller
            )
            val thumb = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
            val deadline = SystemClock.uptimeMillis() + 15_000
            var scrollable = false
            while (!scrollable && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    scrollable = grid.computeVerticalScrollRange() > grid.computeVerticalScrollExtent()
                }
                if (!scrollable) SystemClock.sleep(100)
            }
            assertTrue("the 60-file fixture grid should have a real scroll range", scrollable)
            instrumentation.runOnMainSync { grid.scrollToPosition(0) }
            instrumentation.waitForIdleSync()

            val thumbLocation = IntArray(2)
            val trackLocation = IntArray(2)
            val gridLocation = IntArray(2)
            instrumentation.runOnMainSync {
                thumb.getLocationOnScreen(thumbLocation)
                track.getLocationOnScreen(trackLocation)
                grid.getLocationOnScreen(gridLocation)
            }
            assertTrue("the track begins within the grid viewport", trackLocation[1] >= gridLocation[1])
            assertTrue("the track ends within the grid viewport",
                trackLocation[1] + track.height <= gridLocation[1] + grid.height)
            assertTrue("the track remains at the grid's right edge",
                trackLocation[0] + track.width <= gridLocation[0] + grid.width)
            val expectedTouchWidth = (28 * source.resources.displayMetrics.density + 0.5f).toInt()
            assertEquals("the handle uses the 28dp hit target without widening its drawable",
                expectedTouchWidth, thumb.width)
            val startX = thumbLocation[0] + thumb.width / 2f
            val startY = thumbLocation[1] + thumb.height / 2f
            val endY = (trackLocation[1] + track.height - thumb.height / 2f).toFloat()
            var originalOffset = 0
            instrumentation.runOnMainSync { originalOffset = grid.computeVerticalScrollOffset() }
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int, y: Float): Boolean {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, startX, y, 0)
                    .apply { this.source = InputDevice.SOURCE_TOUCHSCREEN }
                val accepted = instrumentation.uiAutomation.injectInputEvent(event, true)
                event.recycle()
                return accepted
            }
            assertTrue("touch down should reach the visible thumb", inject(MotionEvent.ACTION_DOWN, startY))
            repeat(8) { step ->
                SystemClock.sleep(24)
                val y = startY + (endY - startY) * (step + 1) / 8f
                assertTrue("thumb move should be accepted", inject(MotionEvent.ACTION_MOVE, y))
            }
            assertTrue("thumb release should be accepted", inject(MotionEvent.ACTION_UP, endY))
            instrumentation.waitForIdleSync()
            val finalOffset = grid.computeVerticalScrollOffset()
            assertTrue("dragging the thumb must move the RecyclerView; before=$originalOffset after=$finalOffset",
                finalOffset > originalOffset + grid.height / 2)
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            fixtures.forEach { fixture -> runCatching { context.contentResolver.delete(fixture.first, null, null) } }
        }
    }

    @Test
    fun folderCardUsesPageMotionAndReturnsToTheSameAlbumOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryAlbumTransitionTest-${UUID.randomUUID()}"
        val fixtures = List(5) { createFixture(context, album) }
        val fixture = fixtures.first()
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
            val expectedCoverPath = (homeActivity.findViewById<RecyclerView>(R.id.directories_grid).adapter
                as DirectoryAdapter).dirs.first { it.path == fixtureDirectory }.tmb
            assertNotNull("home album card should have its decoded cover", cover.drawable)
            assertEquals("home card cover should be bound to the fixture album",
                fixtureDirectory, cover.getTag(R.id.directory_bound_path))
            assertTrue("the selected album cover should be one of the generated fixture images",
                fixtures.any { it.second == expectedCoverPath })

            repeat(20) { cycle ->
                val visibleCard = awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory)
                val monitor = instrumentation.addMonitor(MediaActivity::class.java.name, null, false)
                instrumentation.runOnMainSync {
                    check(visibleCard.performClick()) { "album card did not accept click on cycle $cycle" }
                }
                media = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? MediaActivity
                    ?: error("clicking the home album card did not open MediaActivity on cycle $cycle")
                instrumentation.removeMonitor(monitor)
                val mediaActivity = media!!

                assertTrue("folder navigation should not depend on a cover shared element",
                    !mediaActivity.intent.getBooleanExtra(
                        org.fossify.gallery.helpers.AlbumPageTransition.EXTRA_ACTIVE, false
                    ))
                awaitSourceThumbnail(instrumentation, mediaActivity, expectedCoverPath)
                instrumentation.runOnMainSync {
                    val grid = mediaActivity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.media_grid)
                    val visibleRows = (0 until grid.childCount).map(grid::getChildAt)
                    assertTrue("the fixture album should show multiple media rows", visibleRows.size > 1)
                    assertTrue("entering an album should keep every visible row fully opaque",
                        visibleRows.all { it.alpha == 1f })
                }

                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                val returnDeadline = SystemClock.uptimeMillis() + 5_000
                var returnedCard: View? = null
                while (SystemClock.uptimeMillis() < returnDeadline && returnedCard == null) {
                    returnedCard = runCatching {
                        awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory, 300)
                    }.getOrNull()
                    if (returnedCard == null) SystemClock.sleep(50)
                }
                assertNotNull("system Back should return to the same album card on cycle $cycle", returnedCard)
                assertTrue("returned album cover should still be decoded on cycle $cycle",
                    returnedCard!!.findViewById<ImageView>(R.id.dir_thumbnail).drawable != null)
                assertTrue("folder return should keep the original home Activity alive", !homeActivity.isFinishing)
            }
        } finally {
            runCatching { media?.finish() }
            runCatching { home?.finish() }
            fixtures.forEach { fixtureItem ->
                runCatching { context.contentResolver.delete(fixtureItem.first, null, null) }
            }
            context.config.lastMainViewAll = wasLastViewAll
        }
    }

    @Test
    fun favoriteFolderCardUsesPageMotionAndReturnsToTheSameAlbumOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryFavoriteAlbumTransitionTest-${UUID.randomUUID()}"
        val fixture = createFixture(context, album)
        val fixtureDirectory = java.io.File(fixture.second).parent
            ?: error("fixture image has no parent directory")
        val wasLastViewAll = context.config.lastMainViewAll
        var home: MainActivity? = null
        var media: MediaActivity? = null
        context.favoriteFoldersDB.insert(FavoriteFolder(fixtureDirectory))
        try {
            context.config.lastMainViewAll = false
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: error("Gallery launcher activity is unavailable")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            launch.putExtra(MainActivity.EXTRA_SHOW_FOLDERS, true)
            launch.putExtra(MainActivity.EXTRA_FOLDER_FAVORITES, true)
            launch.putExtra(GalleryNavigation.EXTRA_SECTION, GallerySection.FAVORITES.name)
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            instrumentation.startActivitySync(launch)
            home = instrumentation.waitForMonitorWithTimeout(monitor, 15_000) as? MainActivity
                ?: error("Gallery did not finish launching the favorite-folder view")
            instrumentation.removeMonitor(monitor)

            val homeActivity = home!!
            val folderCard = awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory)
            val cover = folderCard.findViewById<ImageView>(R.id.dir_thumbnail)
            assertNotNull("favorite album card should have its decoded cover", cover.drawable)
            assertEquals("favorite card should be bound to the fixture album",
                fixtureDirectory, cover.getTag(R.id.directory_bound_path))

            val mediaMonitor = instrumentation.addMonitor(MediaActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { folderCard.performClick() }
            media = instrumentation.waitForMonitorWithTimeout(mediaMonitor, 15_000) as? MediaActivity
                ?: error("clicking the favorite album card did not open MediaActivity")
            instrumentation.removeMonitor(mediaMonitor)
            val mediaActivity = media!!
            assertTrue("favorite folder navigation should not depend on a cover shared element",
                !mediaActivity.intent.getBooleanExtra(
                    org.fossify.gallery.helpers.AlbumPageTransition.EXTRA_ACTIVE, false
                ))
            awaitSourceThumbnail(instrumentation, mediaActivity, fixture.second)

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val returnedCardDeadline = SystemClock.uptimeMillis() + 5_000
            var returnedCard: View? = null
            while (returnedCard == null && SystemClock.uptimeMillis() < returnedCardDeadline) {
                returnedCard = runCatching {
                    awaitDirectoryCard(instrumentation, homeActivity, fixtureDirectory, 300)
                }.getOrNull()
                if (returnedCard == null) SystemClock.sleep(50)
            }
            assertNotNull("favorite-folder Back should return to its original visible card", returnedCard)
            assertTrue("favorite return should leave the original MainActivity alive", !homeActivity.isFinishing)
        } finally {
            runCatching { media?.finish() }
            runCatching { home?.finish() }
            context.favoriteFoldersDB.delete(fixtureDirectory)
            runCatching { context.contentResolver.delete(fixture.first, null, null) }
            context.config.lastMainViewAll = wasLastViewAll
        }
    }

    @Test
    fun favoritePhotoViewerReturnsToTheLastSwipedThumbnailOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryFavoritePhotoTransitionTest-${UUID.randomUUID()}"
        val fixtures = List(3) { createFixture(context, album) }
        val fixturePaths = fixtures.mapTo(HashSet()) { it.second }
        var source: MediaActivity? = null
        var viewer: ViewPagerActivity? = null
        try {
            fixtures.forEach { (_, path) ->
                assertTrue("test fixture should enter the real favorites collection",
                    context.updateFavorite(path, true))
            }
            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, FAVORITES)
                putExtra(SHOW_FAVORITES, true)
                putExtra(GalleryNavigation.EXTRA_SECTION, GallerySection.FAVORITES.name)
            }
            val sourceActivity = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            source = sourceActivity
            val (initialPath, sourceThumbnail) = awaitAnySourceThumbnail(
                instrumentation, sourceActivity, fixturePaths
            )
            val adapter = MediaActivity::class.java.getDeclaredMethod("getMediaAdapter")
                .apply { isAccessible = true }.invoke(sourceActivity)
            val orderedPaths = (adapter.javaClass.getMethod("getMedia").invoke(adapter) as List<*>)
                .mapNotNull { row ->
                    runCatching { row?.javaClass?.getMethod("getPath")?.invoke(row) as? String }.getOrNull()
                }
            assertEquals("favorites grid should contain only the three synthetic test photos",
                fixturePaths, orderedPaths.toSet())

            val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            val openViewer = MediaActivity::class.java.getDeclaredMethod("openInViewPager", String::class.java)
                .apply { isAccessible = true }
            instrumentation.runOnMainSync { openViewer.invoke(sourceActivity, initialPath) }
            viewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                ?: error("favorite photo did not open ViewPagerActivity")
            instrumentation.removeMonitor(monitor)
            val viewerActivity = viewer!!
            awaitFrame(instrumentation, viewerActivity, requireProxy = false)
            val currentPathMethod = viewerActivity.javaClass.getDeclaredMethod("getCurrentPath")
                .apply { isAccessible = true }
            assertEquals("viewer should open the selected favorite", initialPath,
                currentPathMethod.invoke(viewerActivity))

            val viewerMediaField = viewerActivity.javaClass.getDeclaredField("mMediaFiles")
                .apply { isAccessible = true }
            val pager = viewerActivity.findViewById<ProgressivePhotoViewPager>(R.id.view_pager)
            var viewerPaths = emptyList<String>()
            var adapterCount = 0
            var transitionInputLocked = true
            val pagerReadyDeadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < pagerReadyDeadline) {
                instrumentation.runOnMainSync {
                    val viewerMedia = viewerMediaField.get(viewerActivity) as List<*>
                    viewerPaths = viewerMedia.mapNotNull { medium ->
                        runCatching { medium?.javaClass?.getMethod("getPath")?.invoke(medium) as? String }.getOrNull()
                    }
                    adapterCount = pager.adapter?.count ?: 0
                    transitionInputLocked = pager.transitionInputLocked
                }
                if (viewerPaths.size == fixtures.size && adapterCount == fixtures.size && !transitionInputLocked) break
                SystemClock.sleep(50)
            }
            var publicationState = ""
            instrumentation.runOnMainSync {
                fun state(name: String): Any? = runCatching {
                    viewerActivity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(viewerActivity)
                }.getOrNull()
                listOf(
                    "mediaTransitionRunning=${state("mediaTransitionRunning")}",
                    "pendingAdjacent=${(state("transitionPendingAdjacentMedia") as? List<*>)?.size}",
                    "pendingSwipe=${state("transitionPendingSwipePublication")}",
                    "initialPending=${state("initialViewerPublicationPending")}",
                    "awaitingAdapter=${state("initialViewerPublicationAwaitingAdapter")}",
                    "adapter=${state("activePagerAdapter")}",
                    "publicationGeneration=${state("mediaPublicationGeneration")}",
                    "pendingPager=${state("pendingPagerPublication")}",
                    "scanRetry=${state("viewerScanNeedsRetry")}",
                    "photoCatalogMode=${state("photoCatalogMode")}",
                    "catalogComplete=${state("appliedViewerCatalogComplete")}",
                ).joinToString().also { publicationState = it }
            }
            assertEquals("favorite Viewer should publish all authorized test photos; " +
                "paths=$viewerPaths adapter=$adapterCount inputLocked=$transitionInputLocked $publicationState",
                fixtures.size, viewerPaths.size)
            assertEquals("favorite Viewer should retain the same synthetic photo identities",
                fixturePaths, viewerPaths.toSet())
            val startPosition = viewerPaths.indexOf(initialPath)
            val direction = if (startPosition < viewerPaths.lastIndex) 1 else -1
            val expectedReturnPath = viewerPaths[startPosition + direction]
            assertEquals("favorite Viewer should publish its complete catalog before paging",
                fixtures.size, adapterCount)
            assertTrue("favorite Viewer entry transition should release horizontal input",
                !transitionInputLocked)
            val frame = awaitFrame(instrumentation, viewerActivity, requireProxy = false)
            assertTrue("favorite viewer swipe should be injected",
                injectHorizontalSwipe(instrumentation, frame.image, swipeRight = direction < 0))
            val pageDeadline = SystemClock.uptimeMillis() + 3_000
            while (currentPathMethod.invoke(viewerActivity) == initialPath &&
                SystemClock.uptimeMillis() < pageDeadline) SystemClock.sleep(25)
            val swipedPath = currentPathMethod.invoke(viewerActivity)
            assertEquals("favorite swipe should select the adjacent catalog item; " +
                "viewerPaths=$viewerPaths, start=$initialPath, actual=$swipedPath",
                expectedReturnPath, swipedPath)

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val returnDeadline = SystemClock.uptimeMillis() + 5_000
            while (!viewerActivity.isFinishing && SystemClock.uptimeMillis() < returnDeadline) {
                SystemClock.sleep(25)
            }
            assertTrue("system Back should finish the favorite viewer", viewerActivity.isFinishing)
            val returnedThumbnail = awaitSourceThumbnail(instrumentation, sourceActivity, expectedReturnPath)
            assertEquals("favorite return image should remain bound to the viewer's last photo",
                expectedReturnPath, returnedThumbnail.getTag(R.id.media_bound_path))
            assertEquals("favorite return drawable should match its current request",
                returnedThumbnail.getTag(R.id.thumbnail_request_key),
                returnedThumbnail.getTag(R.id.thumbnail_loaded_key))
            assertTrue("favorites source should remain alive after shared return", !sourceActivity.isFinishing)
        } finally {
            runCatching { viewer?.finish() }
            runCatching { source?.finish() }
            fixturePaths.forEach(context.favoritesDB::deleteFavoritePath)
            fixtures.forEach { fixture ->
                runCatching { context.contentResolver.delete(fixture.first, null, null) }
            }
        }
    }

    @Test
    fun customPhotoProfileReturnsToTheLastSwipedMediaIdentityOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val album = "${Environment.DIRECTORY_PICTURES}/GalleryPhotoProfileReturnTest-${UUID.randomUUID()}"
        val fixtures = List(41) { createFixture(context, album) }
        val fixturePaths = fixtures.mapTo(HashSet()) { it.second }
        val store = PhotoProfileRepository.of(context)
        val previousState = store.state()
        val profileId = UUID.randomUUID().toString()
        val wasShowingAllMedia = context.config.showAll
        var source: MediaActivity? = null
        var viewer: ViewPagerActivity? = null
        try {
            store.save(PhotoProfile(profileId, "Viewer return test", listOf(
                PhotoSource(PhotoSourceType.FOLDER, java.io.File(fixtures.first().second).parent)
            )))
            context.config.showAll = false
            val sourceIntent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, PhotoProfileMediaLoader.PATH)
                putExtra(GalleryNavigation.EXTRA_SECTION, GallerySection.PHOTOS.name)
            }
            val sourceMonitor = instrumentation.addMonitor(MediaActivity::class.java.name, null, false)
            context.startActivity(sourceIntent)
            val sourceActivity = instrumentation.waitForMonitorWithTimeout(sourceMonitor, 15_000)
                as? MediaActivity ?: error("custom photo profile did not launch MediaActivity")
            instrumentation.removeMonitor(sourceMonitor)
            source = sourceActivity
            awaitAnySourceThumbnail(instrumentation, sourceActivity, fixturePaths)
            // Start near the center of the 41-item catalog so the fixed left-swipe
            // sequence has enough adjacent media to move through on API 29 as well.
            val sourcePaths = arrayListOf<String>()
            instrumentation.runOnMainSync {
                val adapter = MediaActivity::class.java.getDeclaredMethod("getMediaAdapter")
                    .apply { isAccessible = true }.invoke(sourceActivity)
                val rows = adapter.javaClass.getMethod("getMedia").invoke(adapter) as List<*>
                rows.mapNotNullTo(sourcePaths) { row ->
                    val path = runCatching {
                        row?.javaClass?.getMethod("getPath")?.invoke(row) as? String
                    }.getOrNull()
                    path?.takeIf { it in fixturePaths }
                }
            }
            assertEquals("custom profile should expose every generated source photo", fixtures.size, sourcePaths.size)
            val initialPath = sourcePaths[sourcePaths.size / 2]
            instrumentation.runOnMainSync {
                val grid = sourceActivity.findViewById<RecyclerView>(R.id.media_grid)
                val adapter = grid.adapter
                val position = (adapter as? MediaAdapter)?.media?.indexOfFirst {
                    (it as? Medium)?.path == initialPath
                } ?: -1
                assertTrue("middle source thumbnail should remain in the current custom profile", position >= 0)
                grid.scrollToPosition(position)
            }
            awaitSourceThumbnail(instrumentation, sourceActivity, initialPath)
            val openViewer = MediaActivity::class.java.getDeclaredMethod("openInViewPager", String::class.java)
                .apply { isAccessible = true }
            val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { openViewer.invoke(sourceActivity, initialPath) }
            val photoViewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                ?: error("custom photo profile did not open the Viewer")
            instrumentation.removeMonitor(monitor)
            viewer = photoViewer
            assertEquals("a decoded visible source thumbnail should start the shared photo transition",
                initialPath, photoViewer.intent.getStringExtra(PhotoViewerTransition.EXTRA_PATH))
            awaitFrame(instrumentation, photoViewer, requireProxy = false)
            instrumentation.runOnMainSync {
                photoViewer.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                    .first { it.isVisible && it.isResumed }.finishSharedTransition()
            }

            val viewerPaths = photoViewer.javaClass.getDeclaredField("mMediaFiles")
                .apply { isAccessible = true }.get(photoViewer).let { rows ->
                    (rows as List<*>).mapNotNull { row ->
                        runCatching { row?.javaClass?.getMethod("getPath")?.invoke(row) as? String }.getOrNull()
                    }
                }
            assertEquals("custom profile should publish every generated source photo", fixtures.size, viewerPaths.size)
            assertTrue("custom profile Viewer catalog should contain the generated source identities",
                fixturePaths.all { it in viewerPaths })
            val currentPath = photoViewer.javaClass.getDeclaredMethod("getCurrentPath").apply { isAccessible = true }
            assertEquals("custom profile viewer should begin on the tapped media identity",
                initialPath, currentPath.invoke(photoViewer))
            val pager = photoViewer.findViewById<ProgressivePhotoViewPager>(R.id.view_pager)
            val pagerDeadline = SystemClock.uptimeMillis() + 5_000
            while (pager.transitionInputLocked && SystemClock.uptimeMillis() < pagerDeadline) {
                SystemClock.sleep(25)
            }
            assertTrue("custom profile swipe input should unlock after the shared entry transition",
                !pager.transitionInputLocked)
            var lastDisplayedPath = initialPath
            val visitedPaths = hashSetOf(initialPath)
            repeat(20) { swipeIndex ->
                val frame = awaitFrame(instrumentation, photoViewer, requireProxy = false)
                injectHorizontalSwipe(instrumentation, frame.image, swipeRight = false)
                fun waitForPageChange() {
                    val deadline = SystemClock.uptimeMillis() + 2_000
                    while (currentPath.invoke(photoViewer) == lastDisplayedPath && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(25)
                    }
                }
                waitForPageChange()
                if (currentPath.invoke(photoViewer) == lastDisplayedPath) {
                    // Accepted injection does not guarantee that ViewPager consumed
                    // the stream while its previous page was still settling on API 29.
                    // Retry only when the Viewer still has the same media identity.
                    val retryFrame = awaitFrame(instrumentation, photoViewer, requireProxy = false)
                    injectHorizontalSwipe(instrumentation, retryFrame.image, swipeRight = false)
                    waitForPageChange()
                }
                lastDisplayedPath = currentPath.invoke(photoViewer) as? String
                    ?: error("custom profile swipe ${swipeIndex + 1} lost its current media identity")
                assertTrue("custom profile swipe ${swipeIndex + 1} should select a different catalog item",
                    lastDisplayedPath in fixturePaths && visitedPaths.add(lastDisplayedPath))
                SystemClock.sleep(320)
            }
            val expectedReturnPath = currentPath.invoke(photoViewer) as? String
                ?: error("custom profile Viewer did not retain its final media identity")
            assertEquals("twenty profile swipes should end on the last displayed media identity",
                lastDisplayedPath, expectedReturnPath)

            instrumentation.runOnMainSync {
                val sourceGrid = sourceActivity.findViewById<RecyclerView>(R.id.media_grid)
                val sourceAdapter = sourceGrid.adapter as MediaAdapter
                val targetPosition = sourceAdapter.media.indexOfFirst {
                    (it as? Medium)?.path == expectedReturnPath
                }
                assertTrue("the last Viewer item should still exist in the profile grid", targetPosition >= 0)
                assertTrue("the return fixture should exercise an offscreen thumbnail",
                    sourceGrid.findViewHolderForAdapterPosition(targetPosition) == null)
            }

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val returned = awaitSourceThumbnail(instrumentation, sourceActivity, expectedReturnPath)
            assertEquals("profile return thumbnail must be bound to the final Viewer media path",
                expectedReturnPath, returned.tag)
            assertEquals("profile return thumbnail must carry the same bound media identity",
                expectedReturnPath, returned.getTag(R.id.media_bound_path))
            assertEquals("profile return must leave the custom Photos grid alive", PhotoProfileMediaLoader.PATH,
                MediaActivity::class.java.getDeclaredField("mPath").apply { isAccessible = true }
                    .get(sourceActivity))
        } finally {
            runCatching { viewer?.finish() }
            runCatching { source?.finish() }
            store.delete(profileId)
            previousState.selectedId?.let(store::select)
            PhotoProfileMediaCache.invalidate()
            context.config.showAll = wasShowingAllMedia
            fixtures.forEach { fixture -> runCatching { context.contentResolver.delete(fixture.first, null, null) } }
        }
    }

    @Test
    fun downwardDragReversesRestoresAndDismissesFromTheCurrentPhotoFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantMediaPermission(context)
        val fixtureAlbum = "${Environment.DIRECTORY_PICTURES}/GalleryInteractiveGestureTest-${UUID.randomUUID()}"
        // Keep enough adjacent media on both sides of the initial item to
        // exercise a long real swipe sequence and finish with an offscreen
        // return target, regardless of the device's MediaStore sort order.
        val fixtures = List(41) { createFixture(context, fixtureAlbum) }
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
                putExtra(DIRECTORY, java.io.File(fixtures.first().second).parent)
            }
            val source = instrumentation.startActivitySync(sourceIntent) as MediaActivity
            val (initialPath, sourceThumbnail) = awaitAnySourceThumbnail(
                instrumentation, source, fixtures.mapTo(HashSet()) { it.second }
            )
            val thumbnailLocation = IntArray(2)
            instrumentation.runOnMainSync { sourceThumbnail.getLocationOnScreen(thumbnailLocation) }
            var sourceGrid: Bitmap? = null
            var thumbnailPixel = Color.TRANSPARENT
            val sourceScreenshotDeadline = SystemClock.uptimeMillis() + 3_000
            while (SystemClock.uptimeMillis() < sourceScreenshotDeadline) {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    ?: error("Could not capture the source album grid")
                thumbnailPixel = screenshot.getPixel(
                    thumbnailLocation[0] + sourceThumbnail.width / 2,
                    thumbnailLocation[1] + sourceThumbnail.height / 2,
                )
                if (Color.green(thumbnailPixel) > Color.red(thumbnailPixel) + 40) {
                    sourceGrid = screenshot
                    break
                }
                screenshot.recycle()
                SystemClock.sleep(60)
            }
            assertTrue("the source album screenshot should contain the decoded fixture thumbnail; " +
                "sampledPixel=#${Integer.toHexString(thumbnailPixel)}",
                sourceGrid != null)
            saveScreenshot(context, "gallery-source-grid.png", sourceGrid!!)

            val monitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            val openViewer = MediaActivity::class.java.getDeclaredMethod("openInViewPager", String::class.java)
                .apply { isAccessible = true }
            instrumentation.runOnMainSync { openViewer.invoke(source, initialPath) }
            val viewer = instrumentation.waitForMonitorWithTimeout(monitor, 10_000) as? ViewPagerActivity
                ?: error("MediaActivity did not launch ViewPagerActivity")
            instrumentation.removeMonitor(monitor)
            val transitionSamples = awaitTransitionFrameSamples(instrumentation, viewer)
            val gfxInfo = instrumentation.uiAutomation.executeShellCommand(
                "dumpsys gfxinfo ${context.packageName} framestats"
            ).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }
            android.util.Log.i("GalleryGestureTest", "transition-gfxinfo=${gfxInfo.takeLast(12_000)}")
            val intermediateOpening = transitionSamples.filter { (progress, _) -> progress in 0.05f..0.95f }
            var transitionState = "unavailable"
            instrumentation.runOnMainSync {
                val mapped = ViewPagerActivity::class.java.getDeclaredField("photoSharedElementMapped")
                    .apply { isAccessible = true }.getBoolean(viewer)
                val fallback = ViewPagerActivity::class.java.getDeclaredField("photoSharedElementFadeFallback")
                    .apply { isAccessible = true }.getBoolean(viewer)
                val frameCount = ViewPagerActivity::class.java.getDeclaredField("photoTransitionProgressFrameCount")
                    .apply { isAccessible = true }.getInt(viewer)
                @Suppress("UNCHECKED_CAST")
                val progressSamples = ViewPagerActivity::class.java.getDeclaredField("photoTransitionProgressSamples")
                    .apply { isAccessible = true }.get(viewer) as List<Float>
                val progressTimes = ViewPagerActivity::class.java.getDeclaredField("photoTransitionProgressTimes")
                    .apply { isAccessible = true }.get(viewer) as List<Long>
                transitionState = "mapped=$mapped,fadeFallback=$fallback,progressFrames=$frameCount," +
                    "progressSamples=${progressSamples.joinToString { "%.3f".format(java.util.Locale.ROOT, it) }}," +
                    "progressTimes=${progressTimes.joinToString()}"
            }
            assertTrue(
                "opening should expose multiple intermediate shared-element frames; samples=" +
                    transitionSamples.joinToString { (progress, bounds) ->
                    "${"%.3f".format(java.util.Locale.ROOT, progress)}:${bounds.width()}x${bounds.height()}"
                    } + "; $transitionState",
                intermediateOpening.size >= 2,
            )
            assertTrue("opening bounds should grow between sampled frames; samples=" +
                intermediateOpening.joinToString { (progress, bounds) ->
                    "${"%.3f".format(java.util.Locale.ROOT, progress)}:${bounds.width()}x${bounds.height()}"
                } + "; $transitionState",
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
                java.io.File(fixtures.first().second).parent, viewerDirectory)
            assertTrue("folder handoff should not become Show All", !viewerShowAll)
            assertTrue("real Gallery launch should hand off the selected photo", initialPath in viewerPaths)
            assertEquals("test viewer catalog should contain every generated album item", fixtures.size, viewerPaths.size)
            assertTrue("real Gallery launch should retain every generated album photo",
                fixtures.all { fixtureItem -> fixtureItem.second in viewerPaths })
            val currentPathMethod = viewer.javaClass.getDeclaredMethod("getCurrentPath").apply { isAccessible = true }
            val startingPath = currentPathMethod.invoke(viewer) as String
            assertEquals("viewer should start on the tapped item", initialPath, startingPath)
            instrumentation.runOnMainSync {
                val sourceSurface = photoFragment.sharedElementViewFor(initialPath)
                assertNotNull("selected photo should expose its shared content surface", sourceSurface)
                photoFragment.finishSharedTransition()
            }
            val pager = viewer.findViewById<ProgressivePhotoViewPager>(R.id.view_pager)
            val pagerReadyDeadline = SystemClock.uptimeMillis() + 5_000
            while ((pager.adapter?.count ?: 0) < 2 || pager.transitionInputLocked) {
                if (SystemClock.uptimeMillis() >= pagerReadyDeadline) break
                SystemClock.sleep(50)
            }
            assertEquals("the complete handoff catalog should be published before horizontal paging",
                fixtures.size, pager.adapter?.count)
            assertTrue("entry transition should release horizontal touch input", !pager.transitionInputLocked)
            val startPosition = viewerPaths.indexOf(startingPath)
            val direction = if (viewerPaths.lastIndex - startPosition >= 20) 1 else -1
            val returnPosition = startPosition + direction * 20
            assertTrue("the generated catalog should allow twenty consecutive swipes",
                returnPosition in viewerPaths.indices)
            var currentPath = startingPath
            repeat(20) { swipeIndex ->
                val expectedPath = viewerPaths[startPosition + direction * (swipeIndex + 1)]
                photoFragment = viewer.supportFragmentManager.fragments.filterIsInstance<PhotoFragment>()
                    .first { it.isVisible && it.isResumed }
                initial = awaitFrame(instrumentation, viewer, requireProxy = false)
                var swipeInjected = injectHorizontalSwipe(instrumentation, initial.image, swipeRight = direction < 0)
                val pageDeadline = SystemClock.uptimeMillis() + 2_000
                while (currentPath == (currentPathMethod.invoke(viewer) as String) &&
                    SystemClock.uptimeMillis() < pageDeadline) SystemClock.sleep(25)
                if (currentPath == (currentPathMethod.invoke(viewer) as String)) {
                    // The API 29 input channel can accept a stream while ViewPager is
                    // still settling and then ignore it. Retry only if the media identity
                    // has not advanced; every step still has to reach its adjacent item.
                    val retryFrame = awaitFrame(instrumentation, viewer, requireProxy = false)
                    swipeInjected = injectHorizontalSwipe(
                        instrumentation, retryFrame.image, swipeRight = direction < 0
                    ) || swipeInjected
                    val retryDeadline = SystemClock.uptimeMillis() + 2_000
                    while (currentPath == (currentPathMethod.invoke(viewer) as String) &&
                        SystemClock.uptimeMillis() < retryDeadline) SystemClock.sleep(25)
                }
                assertTrue("horizontal swipe ${swipeIndex + 1} should be injected on the emulator", swipeInjected)
                currentPath = currentPathMethod.invoke(viewer) as String
                assertEquals("swipe ${swipeIndex + 1} should select the adjacent catalog identity",
                    expectedPath, currentPath)
                // Let the pager finish settling before injecting the next user swipe.
                SystemClock.sleep(350)
            }
            val expectedReturnPath = viewerPaths[returnPosition]
            assertEquals("twenty swipes should land on the expected stable media identity",
                expectedReturnPath, currentPath)
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
            val zoomDeadline = SystemClock.uptimeMillis() + 6_000
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
            val resetDeadline = SystemClock.uptimeMillis() + 6_000
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
                expectedReturnPath, returnPath)
            SystemClock.sleep(500)
            val returnedThumbnail = awaitSourceThumbnail(instrumentation, source, expectedReturnPath)
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
            instrumentation.runOnMainSync { openViewer.invoke(source, expectedReturnPath) }
            val reopenedViewer = instrumentation.waitForMonitorWithTimeout(returnMonitor, 10_000)
                as? ViewPagerActivity ?: error("Gallery did not reopen the selected photo")
            instrumentation.removeMonitor(returnMonitor)
            assertTrue("reopened viewer should still be entering before Android Back",
                reopenedViewer.isMediaTransitionRunning())
            assertTrue("system Back must be dispatched during viewer entry",
                instrumentation.uiAutomation.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                ))
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
                "${java.io.File(initialPath).parent}/missing-target.jpg",
            )
            instrumentation.runOnMainSync {
                source.onActivityReenter(android.app.Activity.RESULT_OK, missingReturn)
            }
            val pendingReturn = MediaActivity::class.java.getDeclaredField("pendingViewerReturnPath")
                .apply { isAccessible = true }
            assertEquals("missing return path should not remain pending", null, pendingReturn.get(source))
            assertTrue("missing return path should not finish the source album", !source.isFinishing)

            val toolbarMonitor = instrumentation.addMonitor(ViewPagerActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { openViewer.invoke(source, initialPath) }
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
            fixtures.forEach { fixtureItem ->
                runCatching { context.contentResolver.delete(fixtureItem.first, null, null) }
            }
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
                val mappedSharedElement = ViewPagerActivity::class.java
                    .getDeclaredField("photoSharedElementMapped").apply { isAccessible = true }.getBoolean(viewer)
                val fadeFallback = ViewPagerActivity::class.java
                    .getDeclaredField("photoSharedElementFadeFallback").apply { isAccessible = true }.getBoolean(viewer)
                assertTrue(
                    "fixture $index should run the shared-element progress callback; " +
                        "frames=$progressFrameCount,mapped=$mappedSharedElement,fadeFallback=$fadeFallback",
                    progressFrameCount > 0,
                )
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

                val transitionRunningField = ViewPagerActivity::class.java.getDeclaredField("mediaTransitionRunning")
                    .apply { isAccessible = true }
                val transitionDeadline = SystemClock.uptimeMillis() + 5_000
                var transitionRunning = true
                while (transitionRunning && SystemClock.uptimeMillis() < transitionDeadline) {
                    instrumentation.runOnMainSync { transitionRunning = transitionRunningField.getBoolean(viewer) }
                    if (transitionRunning) SystemClock.sleep(25)
                }
                assertTrue("fixture $index opening transition should settle before closing", !transitionRunning)

                instrumentation.runOnMainSync { viewer.finish() }
                val returnDeadline = SystemClock.uptimeMillis() + 5_000
                while ((!sourceActivity.hasWindowFocus() || sourceActivity.isFinishing) &&
                    SystemClock.uptimeMillis() < returnDeadline) {
                    SystemClock.sleep(50)
                }
                val reentryObserved = MediaActivity::class.java.getDeclaredField("viewerReentryObservedForCurrentLaunch")
                    .apply { isAccessible = true }.getBoolean(sourceActivity)
                val pendingReturnPath = MediaActivity::class.java.getDeclaredField("pendingViewerReturnPath")
                    .apply { isAccessible = true }.get(sourceActivity)
                val launchInProgress = MediaActivity::class.java.getDeclaredField("viewerLaunchInProgress")
                    .apply { isAccessible = true }.getBoolean(sourceActivity)
                val returnGeneration = MediaActivity::class.java.getDeclaredField("viewerReturnGeneration")
                    .apply { isAccessible = true }.getLong(sourceActivity)
                assertTrue(
                    "source album should resume after fixture $index; source=" +
                        "focus:${sourceActivity.hasWindowFocus()},finishing:${sourceActivity.isFinishing}," +
                        "destroyed:${sourceActivity.isDestroyed},state:${sourceActivity.lifecycle.currentState}; " +
                        "viewer=finishing:${viewer.isFinishing},destroyed:${viewer.isDestroyed}; " +
                        "reentry:$reentryObserved,returnGeneration:$returnGeneration," +
                        "pending:$pendingReturnPath,launching:$launchInProgress",
                    sourceActivity.hasWindowFocus(),
                )
                if (index < fixtures.lastIndex) {
                    awaitSourceThumbnail(instrumentation, sourceActivity, fixtures[index + 1].second)
                    val launchGuard = MediaActivity::class.java.getDeclaredField("viewerLaunchInProgress")
                        .apply { isAccessible = true }
                    val returnDeadline = SystemClock.uptimeMillis() + 5_000
                    while (launchGuard.getBoolean(sourceActivity) && SystemClock.uptimeMillis() < returnDeadline) {
                        SystemClock.sleep(25)
                    }
                    assertTrue("viewer result should release the source launch guard before the next fixture",
                        !launchGuard.getBoolean(sourceActivity))
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
                thumbnail = visible.firstOrNull {
                    it.drawable != null && it.isAttachedToWindow && it.tag == path &&
                        it.getTag(R.id.thumbnail_loaded_key) != null &&
                        it.getTag(R.id.thumbnail_loaded_key) == it.getTag(R.id.thumbnail_request_key)
                }
            }
            if (thumbnail != null) return thumbnail!!
            SystemClock.sleep(50)
        }
        error("test image did not appear in the MediaActivity grid: path=$path $observedGrid")
    }

    private fun awaitAnySourceThumbnail(
        instrumentation: android.app.Instrumentation,
        activity: MediaActivity,
        fixturePaths: Set<String>,
    ): Pair<String, ImageView> {
        val deadline = SystemClock.uptimeMillis() + 20_000
        var match: Pair<String, ImageView>? = null
        var observedState = "grid not sampled"
        while (SystemClock.uptimeMillis() < deadline && match == null) {
            instrumentation.runOnMainSync {
                val grid = activity.findViewById<RecyclerView>(R.id.media_grid)
                val visibleRows = ArrayList<String>()
                for (index in 0 until (grid?.childCount ?: 0)) {
                    val image = grid?.getChildAt(index)?.findViewById<ImageView>(R.id.medium_thumbnail)
                        ?: continue
                    val path = image.tag as? String ?: continue
                    visibleRows += "${path.substringAfterLast('/')}:${image.drawable != null}"
                    if (path in fixturePaths && image.isAttachedToWindow && image.drawable != null &&
                        image.getTag(R.id.thumbnail_loaded_key) != null &&
                        image.getTag(R.id.thumbnail_loaded_key) == image.getTag(R.id.thumbnail_request_key)) {
                        match = path to image
                        break
                    }
                }
                observedState = "adapterItems=${grid?.adapter?.itemCount}," +
                    "mediums=${MediaActivity.mMedia.count { it is Medium }}," +
                    "visible=$visibleRows," +
                    "readImages=${activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED}"
            }
            if (match == null) SystemClock.sleep(50)
        }
        return match ?: error("no generated photo became a loaded, visible source thumbnail; $observedState")
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
                val requestKey = image?.getTag(R.id.thumbnail_request_key)
                val loadedKey = image?.getTag(R.id.thumbnail_loaded_key)
                observed = "items=${adapter?.itemCount} position=$position " +
                    "children=${grid?.childCount} cover=${image?.drawable != null} " +
                    "loaded=${loadedKey != null && loadedKey == requestKey} " +
                    "bound=${image?.getTag(R.id.directory_bound_path)} target=$path"
                if (item != null && image?.isAttachedToWindow == true && image.drawable != null &&
                    requestKey != null && loadedKey == requestKey &&
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
            // Grant the full library permissions only. Granting the selected-media
            // permission directly can put API 34+ into the empty selected-items
            // state, which makes these synthetic full-library tests see no rows.
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
                else emptyList()
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE) +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
                else emptyList()
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
