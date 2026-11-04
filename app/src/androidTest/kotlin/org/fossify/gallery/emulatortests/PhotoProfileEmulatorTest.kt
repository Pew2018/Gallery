package org.fossify.gallery.emulatortests

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.adapters.MyPagerAdapter
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.MEDIUM
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.uiautomator.UiDevice
import android.graphics.Color
import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID

/** REAL Android filesystem/MediaStore tests; NOT a PIN or real Viewer gesture test. */
@RunWith(AndroidJUnit4::class)
class PhotoProfileEmulatorTest {
    companion object {
        private lateinit var root: File
        private var scale = "smoke"
        private var suite = "smoke"
        private var countA = 0

        @BeforeClass @JvmStatic fun buildMediaFixtures() {
            scale = InstrumentationRegistry.getArguments().getString("scale") ?: "smoke"
            suite = InstrumentationRegistry.getArguments().getString("suite") ?: "smoke"
            if (Build.VERSION.SDK_INT >= 30) assertTrue(Environment.isExternalStorageManager())
            val sizes = when (scale) {
                "smoke" -> mapOf("A" to 64, "N" to 64, "P" to 64)
                "large" -> mapOf("A" to 12011, "N" to 12013, "P" to 12017)
                "segment" -> mapOf("A" to 20501, "N" to 96, "P" to 96)
                "ceiling" -> mapOf("A" to 50009, "N" to 96, "P" to 96)
                else -> throw AssertionError("Invalid scale: " + scale)
            }
            countA = sizes.getValue("A")
            root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryCI")

            // GitHub Actions may bulk-extract host-created, valid, uniquely
            // encoded PNG fixtures. Avoid 50k individual PNG compressions on
            // the emulator; still assert exact counts and add JPEG/SVG media.
            val imported = File(root, ".ci_import_ready")
            if (imported.isFile) {
                assertEquals("Wrong imported test scale", scale, imported.readText().trim())
                for ((source, count) in sizes) {
                    val folder = File(root, source)
                    assertTrue("Missing imported folder " + source, folder.isDirectory)
                    assertEquals("Partial imported PNG fixture " + source, count,
                        folder.listFiles().orEmpty().count { it.extension.equals("png", true) })
                    val jpegBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                    jpegBitmap.eraseColor(Color.rgb(source[0].code % 256, 91, 173))
                    val bytes = ByteArrayOutputStream()
                    check(jpegBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes))
                    jpegBitmap.recycle()
                    File(folder, "CI_sample.jpg").writeBytes(bytes.toByteArray())
                    File(folder, "CI_sample.svg").writeText(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\">" +
                            "<rect width=\"24\" height=\"24\" fill=\"#336699\"/></svg>"
                    )
                }
                assertTrue("Imported .nomedia absent", File(root, "N/.nomedia").isFile)
                Log.i("GalleryEmulatorFixture", "IMPORTED scale=" + scale + " roots=" + sizes)
                return
            }

            // Reversible source-qualified 32-bit identity, unique at 50,009 items.
            // Encode into the actual image pixels so filename/path correctness
            // cannot disguise stale or incorrectly bound drawable contents.
            val markerBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(32 * 32)
            fun markedPng(source: String, index: Int): ByteArray {
                val key = (source[0].code.toLong() shl 20) or (index + 1).toLong()
                for (y in 0 until 32) for (x in 0 until 32) {
                    val bit = (key ushr ((y / 4) * 8 + x / 4)) and 1L
                    pixels[y * 32 + x] = if (bit == 1L) Color.rgb(228, 42, 86)
                        else Color.rgb(26, 72, 184)
                }
                markerBitmap.setPixels(pixels, 0, 32, 0, 0, 32, 32)
                val bytes = ByteArrayOutputStream(180)
                check(markerBitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                return bytes.toByteArray()
            }
            for ((source, count) in sizes) {
                val folder = File(root, source)
                assertTrue(folder.mkdirs() || folder.isDirectory)
                if (source == "N") File(folder, ".nomedia").writeText("")
                for (index in 0 until count) {
                    val filename = if (index % 4 == 0) "IMG_" + index + ".png" else
                        "IMG_" + index.toString().padStart(6, '0') + ".png"
                    val file = File(folder, filename)
                    file.writeBytes(markedPng(source, index))
                    assertTrue(file.setLastModified(1578000000000L + ((index * 7919L + source[0].code) % 180000L) * 60000L))
                }
                assertEquals(count, folder.listFiles().orEmpty().count { it.extension.equals("png", true) })
                // Real decodable JPEG plus a valid SVG in every selected source.
                val jpgBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                jpgBitmap.eraseColor(Color.rgb(source[0].code % 256, 91, 173))
                val jpegBytes = ByteArrayOutputStream()
                check(jpgBitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegBytes))
                jpgBitmap.recycle()
                File(folder, "CI_sample.jpg").writeBytes(jpegBytes.toByteArray())
                File(folder, "CI_sample.svg").writeText(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\">" +
                        "<rect width=\"24\" height=\"24\" fill=\"#336699\"/></svg>"
                )

            }
            markerBitmap.recycle()
            assertTrue(File(root, "N/.nomedia").isFile)
        }
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val profileIds = ArrayList<String>()
    private var previousSorting = 0

    @Before fun before() { previousSorting = context.config.getFolderSorting(PhotoProfileMediaLoader.PATH) }
    @After fun after() {
        context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, previousSorting)
        val store = PhotoProfileRepository.of(context)
        profileIds.forEach(store::delete)
    }

    private fun load(sources: List<String>, sorting: Int, favorites: Boolean = false): List<Medium> {
        context.config.saveCustomSorting(PhotoProfileMediaLoader.PATH, sorting)
        val profile = PhotoProfile(UUID.randomUUID().toString(), "CI",
            sources.map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) } +
                (if (favorites) listOf(PhotoSource(PhotoSourceType.FAVORITES)) else emptyList()))
        PhotoProfileRepository.of(context).save(profile)
        profileIds.add(profile.id)
        return PhotoProfileMediaLoader(context).load(profile.id).filterIsInstance<Medium>()
    }

    private fun verify(sources: List<String>, actual: List<Medium>) {
        val roots = sources.map { File(root, it).canonicalPath }.toSet()
        val expected = sources.flatMap { name ->
            File(root, name).listFiles().orEmpty()
                .filter { it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "svg") }
                .map { it.canonicalPath }
        }.toSet()
        val paths = actual.map { it.path }
        assertEquals("Duplicate media", paths.size, paths.distinct().size)
        val missing = expected - paths.toSet()
        assertTrue("Missing " + missing.size + " media: " + missing.take(5), missing.isEmpty())
        assertTrue("Outside profile", actual.all { it.parentPath in roots })
    }

    @Test fun selectedNomediaAndPublicFolderCombinations() {
        val all = listOf(listOf("A"), listOf("N"), listOf("P"), listOf("A", "N"),
            listOf("A", "P"), listOf("N", "P"), listOf("A", "N", "P"))
        val chosen = if (scale == "smoke") all else listOf(all[0], all[1], all[3], all[6])
        for (sources in chosen) verify(sources, load(sources, SORT_BY_DATE_MODIFIED or SORT_DESCENDING))
    }

    @Test fun fifteenMediaOrderingModes() {
        val types = listOf(SORT_BY_NAME, SORT_BY_PATH, SORT_BY_SIZE, SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)
        val modes = if (scale == "smoke") types.flatMap { listOf(it, it or SORT_DESCENDING) } +
            listOf(SORT_BY_RANDOM, SORT_BY_NAME or SORT_USE_NUMERIC_VALUE,
                SORT_BY_NAME or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING)
            else listOf(SORT_BY_SIZE or SORT_DESCENDING, SORT_BY_DATE_MODIFIED or SORT_DESCENDING, SORT_BY_RANDOM)
        val source = if (scale == "smoke") listOf("A", "N") else listOf("A")
        for (mode in modes) {
            val media = load(source, mode)
            verify(source, media)
            if (mode and SORT_BY_RANDOM != 0) continue
            val numeric = mode and SORT_USE_NUMERIC_VALUE != 0
            fun compare(a: Medium, b: Medium): Int = when {
                mode and SORT_BY_NAME != 0 -> {
                    val x = a.name.lowercase(Locale.getDefault())
                    val y = b.name.lowercase(Locale.getDefault())
                    if (numeric) AlphanumericComparator().compare(x, y) else x.compareTo(y)
                }
                mode and SORT_BY_PATH != 0 -> {
                    val x = a.path.lowercase(Locale.getDefault())
                    val y = b.path.lowercase(Locale.getDefault())
                    if (numeric) AlphanumericComparator().compare(x, y) else x.compareTo(y)
                }
                mode and SORT_BY_SIZE != 0 -> a.size.compareTo(b.size)
                mode and SORT_BY_DATE_MODIFIED != 0 -> a.modified.compareTo(b.modified)
                else -> a.taken.compareTo(b.taken)
            }
            media.zipWithNext().forEachIndexed { index, pair ->
                val result = compare(pair.first, pair.second)
                assertTrue("Wrong order mode=" + mode + " index=" + index,
                    if (mode and SORT_DESCENDING != 0) result >= 0 else result <= 0)
            }
        }
    }

    @Test fun largeMediaBoundary() {
        org.junit.Assume.assumeTrue("large boundary requested", scale != "smoke")
        val media = load(listOf("A"), SORT_BY_NAME)
        verify(listOf("A"), media)
        assertTrue("Expected " + countA + ", found " + media.size, media.size >= countA)
    }

    @Test fun virtualFavoritesSourceOverlapNomediaVisibilityAndDynamicMembership() {
        val a = File(root, "A/IMG_0.png").canonicalPath
        val n = File(root, "N/IMG_000001.png").canonicalPath
        val p = File(root, "P/IMG_000002.png").canonicalPath
        val testPaths = listOf(a, n, p)
        testPaths.forEach { path -> assertTrue("Missing favorite fixture " + path, File(path).isFile) }
        val originals = context.getFavoritePaths().toSet()
        try {
            testPaths.forEach { path ->
                assertTrue("Could not favorite " + path, context.updateFavorite(path, true))
            }
            val favoritesOnly = load(emptyList(), SORT_BY_NAME, favorites = true).map { it.path }.toSet()
            assertTrue("Public A favorite missing", a in favoritesOnly)
            assertTrue("Public P favorite missing", p in favoritesOnly)
            assertFalse("A .nomedia source not explicitly selected must not be exposed via Favorites", n in favoritesOnly)
            val both = load(listOf("A", "N"), SORT_BY_NAME, favorites = true)
            val paths = both.map { it.path }
            assertEquals("Folder+Favorites overlap duplicated", paths.size, paths.distinct().size)
            assertTrue("N explicitly selected but missing favorite", n in paths)
            assertTrue("P favorite outside folders missing", p in paths)
            context.updateFavorite(p, false)
            val afterRemoval = load(emptyList(), SORT_BY_NAME, favorites = true).map { it.path }.toSet()
            assertFalse("Unfavorited media reappeared", p in afterRemoval)
        } finally {
            testPaths.filter { it !in originals }.forEach { context.updateFavorite(it, false) }
        }
    }

    /** Actual MediaActivity grid click -> ViewPagerActivity -> on-device pointer swipes.
     * Verifies the selected and adjacent Fragment identities against committed grid rows.
     * Screenshot evidence is stored on device and extracted by the workflow.
     * PIN and full rendered-pixel assertions remain separate unimplemented coverage.
     */
    @Test fun realGridClickViewerSwipeFollowsCommittedThumbOrder() {
        org.junit.Assume.assumeTrue("smoke case", suite == "smoke" && scale == "smoke")
        runViewerSequence(SORT_BY_NAME, listOf("A"), 6, "smoke")
    }

    @Test fun allFifteenSortModesRealUi() {
        org.junit.Assume.assumeTrue("15-mode real-UI matrix", suite == "sort_ui" && scale == "smoke")
        val bases = listOf(SORT_BY_NAME, SORT_BY_PATH, SORT_BY_SIZE,
            SORT_BY_DATE_MODIFIED, SORT_BY_DATE_TAKEN)
        val modes = bases.flatMap { listOf(it, it or SORT_DESCENDING) } +
            listOf(SORT_BY_RANDOM, SORT_BY_NAME or SORT_USE_NUMERIC_VALUE,
                SORT_BY_NAME or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE,
                SORT_BY_PATH or SORT_USE_NUMERIC_VALUE or SORT_DESCENDING)
        assertEquals(15, modes.size)
        modes.forEachIndexed { i, sorting ->
            runViewerSequence(sorting, listOf("A", "N"), 20, "sort" + i)
        }
    }

    @Test fun deepScrollActualUi() {
        org.junit.Assume.assumeTrue("12k deep-scroll suite", suite == "deep" && scale == "large")
        runViewerSequence(SORT_BY_DATE_MODIFIED or SORT_DESCENDING, listOf("A"), 9000, "ordinary-deep-A", true, normalFolder = true)
        runViewerSequence(SORT_BY_DATE_MODIFIED or SORT_DESCENDING, listOf("A", "N"), 12000, "profile-nomedia-deep", true)
        runViewerSequence(SORT_BY_RANDOM, listOf("A", "N", "P"), 20000, "deepMix", true)
    }

    @Test fun boundaryCatalogActualUi() {
        org.junit.Assume.assumeTrue("20k/50k segmented suite",
            suite == "boundary" && scale in listOf("segment", "ceiling"))
        val anchors = if (scale == "segment") listOf(9999, 10001, 19999)
            else listOf(10000, 20000, 30000, 49990)
        anchors.forEach { at ->
            runViewerSequence(SORT_BY_NAME, listOf("A"), at, scale + at, true)
        }
    }

    private fun runViewerSequence(sorting: Int, sources: List<String>,
        selectedOrdinal: Int, caseId: String, deepScroll: Boolean = false,
        normalFolder: Boolean = false) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val sortingPath = if (normalFolder) File(root, "A").canonicalPath else PhotoProfileMediaLoader.PATH
        if (!normalFolder) {
            val profile = PhotoProfile(UUID.randomUUID().toString(), "CI " + caseId,
                sources.map { PhotoSource(PhotoSourceType.FOLDER, File(root, it).canonicalPath) })
            PhotoProfileRepository.of(context).save(profile)
            profileIds.add(profile.id)
        }
        val previousScenarioSorting = context.config.getFolderSorting(sortingPath)
        context.config.saveCustomSorting(sortingPath, sorting)
        Log.i("GalleryEmulatorMatrix", "BEGIN " + caseId + " mode=" + sorting + " sources=" + sources)
        val monitor = Instrumentation.ActivityMonitor(ViewPagerActivity::class.java.name, null, false)
        inst.addMonitor(monitor)
        var mediaActivity: MediaActivity? = null
        var viewerActivity: ViewPagerActivity? = null
        try {
            val intent = Intent(context, MediaActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DIRECTORY, sortingPath)
            }
            mediaActivity = inst.startActivitySync(intent) as MediaActivity
            val grid = mediaActivity.findViewById<RecyclerView>(R.id.media_grid)
            var committed = emptyList<String>()
            var selectedRow = -1
            val expectedCount = sources.sumOf { name ->
                File(root, name).listFiles().orEmpty().count {
                    it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "svg")
                }
            }
            fun captureGrid(): Boolean {
                inst.runOnMainSync {
                    val adapter = grid.adapter as? MediaAdapter
                    val media = adapter?.media?.filterIsInstance<Medium>().orEmpty()
                    if (media.size >= expectedCount) {
                        committed = media.map { it.path }
                        val at = selectedOrdinal.coerceIn(2, (media.lastIndex - 2).coerceAtLeast(2))
                        val selected = media.mapIndexedNotNull { i, item ->
                            if (i >= 2 && i <= media.lastIndex - 2 && item.path.endsWith(".png"))
                                i to item else null
                        }.minByOrNull { kotlin.math.abs(it.first - at) }?.second
                        selectedRow = adapter?.media?.indexOfFirst { it is Medium && it.path == selected?.path } ?: -1
                    }
                }
                return committed.size >= expectedCount && selectedRow >= 0
            }
            awaitCondition("complete MediaActivity adapter " + caseId, 180000, ::captureGrid)
            var anchor = committed.firstOrNull { it == (
                (grid.adapter as? MediaAdapter)?.media?.getOrNull(selectedRow) as? Medium
            )?.path } ?: throw AssertionError("Clicked media not in committed grid")
            var clickedIndex = committed.indexOf(anchor)
            assertTrue("Need both neighbors around click", clickedIndex >= 2 && clickedIndex <= committed.lastIndex - 2)
            inst.runOnMainSync {
                grid.scrollToPosition(if (deepScroll) (selectedRow - 25).coerceAtLeast(0) else selectedRow)
            }
            inst.waitForIdleSync()
            if (deepScroll) {
                val pos = IntArray(2)
                var x = 0; var top = 0; var bottom = 0
                inst.runOnMainSync {
                    grid.getLocationOnScreen(pos)
                    x = pos[0] + grid.width / 2
                    top = pos[1] + grid.height / 4
                    bottom = pos[1] + grid.height * 3 / 4
                }
                repeat(3) {
                    assertTrue("Grid touch swipe failed", UiDevice.getInstance(inst).swipe(x, bottom, x, top, 30))
                }
                inst.waitForIdleSync()
                inst.runOnMainSync {
                    val visible = grid.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        ?: throw AssertionError("RecyclerView layout manager mismatch")
                    val first = visible.findFirstVisibleItemPosition()
                    val last = visible.findLastVisibleItemPosition()
                    val adapter = grid.adapter as? MediaAdapter
                    selectedRow = (first..last).firstOrNull {
                        it >= (first + last) / 2 && adapter?.media?.getOrNull(it) is Medium
                    } ?: (first..last).firstOrNull { adapter?.media?.getOrNull(it) is Medium }
                        ?: throw AssertionError("No visible media after grid swipe")
                    anchor = (adapter?.media?.getOrNull(selectedRow) as? Medium)?.path.orEmpty()
                }
                clickedIndex = committed.indexOf(anchor)
                assertTrue("Deep click missing from committed list " + anchor,
                    clickedIndex >= 2 && clickedIndex <= committed.lastIndex - 2)
            }
            var holder: RecyclerView.ViewHolder? = null
            awaitCondition("visible thumbnail to click", 7000) {
                inst.runOnMainSync { holder = grid.findViewHolderForAdapterPosition(selectedRow) }
                holder != null
            }
            var clicked = false
            inst.runOnMainSync { clicked = holder?.itemView?.performClick() == true }
            assertTrue("MediaAdapter click was not handled", clicked)
            viewerActivity = inst.waitForMonitorWithTimeout(monitor, 20000) as? ViewPagerActivity
            val viewer = viewerActivity ?: throw AssertionError("Grid tap did not launch ViewPagerActivity")
            fun currentIdentity(): Pair<String?, String?> {
                var paths: Pair<String?, String?> = null to null
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val catalog = adapter?.media?.getOrNull(index)?.path
                    val fragment = (adapter?.getCurrentFragment(index)?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    paths = catalog to fragment
                }
                return paths
            }
            fun capturePagerState(): String {
                var output = ""
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val selected = adapter?.media?.getOrNull(index)?.path
                    val currentFragment = adapter?.getCurrentFragment(index)
                    val fragmentPath = (currentFragment?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    val drawableReady = (currentFragment?.view?.findViewById<ImageView>(R.id.gestures_view))?.drawable != null
                    output = "item=" + index + " count=" + (adapter?.count ?: -1) +
                        " selected=" + selected + " fragment=" + fragmentPath +
                        " drawableReady=" + drawableReady +
                        " pixelMatch=" + displayedPixelsMatch(currentFragment?.view?.findViewById<ImageView>(R.id.gestures_view), selected.orEmpty()) +
                        " focus=" + viewer.hasWindowFocus() +
                        " topPackage=" + UiDevice.getInstance(inst).currentPackageName +
                        " shown=" + viewer.window.decorView.isShown +
                        " resumed=" + viewer.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }
                return output
            }
            fun pageRendered(path: String): Boolean {
                var ready = false
                inst.runOnMainSync {
                    val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                    val adapter = pager.adapter as? MyPagerAdapter
                    val index = pager.currentItem
                    val fragment = adapter?.getCurrentFragment(index)
                    val fragmentPath = (fragment?.arguments?.getSerializable(MEDIUM) as? Medium)?.path
                    val image = fragment?.view?.findViewById<ImageView>(R.id.gestures_view)
                    ready = viewer.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        viewer.window.decorView.isShown &&
                        UiDevice.getInstance(inst).currentPackageName == context.packageName &&
                        adapter != null && adapter.count > 0 &&
                        (scale != "segment" && scale != "ceiling" || adapter.count <= committed.size) &&
                        adapter.media.getOrNull(index)?.path == path && fragmentPath == path &&
                        displayedPixelsMatch(image, path)
                }
                return ready
            }
            fun expect(path: String, stage: String) {
                try {
                    awaitCondition(stage + " pager & rendered bitmap", 20000) { pageRendered(path) }
                } catch (failure: AssertionError) {
                    Log.e("GalleryEmulatorSwipe", stage + " expected=" + path +
                        " observed=" + capturePagerState(), failure)
                    runCatching {
                        val bitmap = inst.uiAutomation.takeScreenshot()
                        if (bitmap != null) {
                            val folder = File(context.filesDir, "gallery-ci-evidence")
                            folder.mkdirs()
                            FileOutputStream(File(folder, caseId + "-failure-" + stage + ".png")).use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                        }
                    }.onFailure { Log.e("GalleryEmulatorSwipe", "Could not capture failure screen", it) }
                    throw AssertionError(stage + " expected=" + path +
                        " observed=" + capturePagerState(), failure)
                }
                Log.i("GalleryEmulatorSwipe", stage + " expected=" + path +
                    " observed=" + capturePagerState())
                val bitmap = inst.uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val folder = File(context.filesDir, "gallery-ci-evidence")
                    folder.mkdirs()
                    FileOutputStream(File(folder, caseId + "-" + stage + ".png")).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            fun drag(forward: Boolean) {
                val pager = viewer.findViewById<ViewPager>(R.id.view_pager)
                val position = IntArray(2)
                var left = 0
                var right = 0
                var y = 0
                inst.runOnMainSync {
                    pager.getLocationOnScreen(position)
                    left = position[0] + (pager.width * 0.15f).toInt()
                    right = position[0] + (pager.width * 0.85f).toInt()
                    y = position[1] + pager.height / 2
                }
                val from = if (forward) right else left
                val to = if (forward) left else right
                Log.i("GalleryEmulatorSwipe", "swipe forward=" + forward +
                    " from=" + from + " to=" + to + " y=" + y +
                    " before=" + capturePagerState())
                assertTrue("UiAutomator could not perform swipe", UiDevice.getInstance(inst).swipe(from, y, to, y, 60))
                inst.waitForIdleSync()
                Log.i("GalleryEmulatorSwipe", "afterSwipe=" + capturePagerState())
            }
            Log.i("GalleryEmulatorMatrix", "GRID " + caseId + " count=" + committed.size + " anchor=" + clickedIndex + " path=" + anchor)
            expect(anchor, "initial")
            drag(true); expect(committed[clickedIndex + 1], "forward1")
            drag(true); expect(committed[clickedIndex + 2], "forward2")
            drag(false); expect(committed[clickedIndex + 1], "backward1")
            drag(false); expect(anchor, "backward2")
        } finally {
            viewerActivity?.runOnUiThread { viewerActivity?.finish() }
            mediaActivity?.runOnUiThread { mediaActivity?.finish() }
            inst.removeMonitor(monitor)
            inst.waitForIdleSync()
            context.config.saveCustomSorting(sortingPath, previousScenarioSorting)
            Log.i("GalleryEmulatorMatrix", "END " + caseId)
        }
    }

    /**
     * Compare the actual displayed drawable's per-cell pixel identity with
     * the decoded file, not merely Medium.path or a non-null drawable.
     */
    private fun displayedPixelsMatch(image: ImageView?, path: String): Boolean {
        if (image == null || image.drawable == null) return false
        if (!path.endsWith(".png", ignoreCase = true)) return true
        val expected = BitmapFactory.decodeFile(path) ?: return false
        return try {
            val rendered = image.drawable.toBitmap(32, 32)
            var matches = true
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val px = x * 4 + 2
                    val py = y * 4 + 2
                    val reference = expected.getPixel(px, py)
                    val actual = rendered.getPixel(px, py)
                    if (kotlin.math.abs(Color.red(reference) - Color.red(actual)) > 12 ||
                        kotlin.math.abs(Color.green(reference) - Color.green(actual)) > 12 ||
                        kotlin.math.abs(Color.blue(reference) - Color.blue(actual)) > 12) {
                        matches = false
                        break
                    }
                }
                if (!matches) break
            }
            matches
        } catch (error: Exception) {
            Log.w("GalleryEmulatorMatrix", "Drawable pixel comparison failed at " + path, error)
            false
        } finally {
            expected.recycle()
        }
    }

    private fun awaitCondition(name: String, timeoutMs: Long, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for " + name)
    }
}
