package org.fossify.gallery.emulatortests

import android.graphics.Bitmap
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
        private var countA = 0

        @BeforeClass @JvmStatic fun buildMediaFixtures() {
            scale = InstrumentationRegistry.getArguments().getString("scale") ?: "smoke"
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
            val templates = (0 until 64).map { index ->
                val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.rgb(index * 17 % 255, index * 43 % 255, index * 71 % 255))
                val stream = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                bitmap.recycle()
                stream.toByteArray()
            }
            for ((source, count) in sizes) {
                val folder = File(root, source)
                assertTrue(folder.mkdirs() || folder.isDirectory)
                if (source == "N") File(folder, ".nomedia").writeText("")
                for (index in 0 until count) {
                    val filename = if (index % 4 == 0) "IMG_" + index + ".png" else
                        "IMG_" + index.toString().padStart(6, '0') + ".png"
                    val file = File(folder, filename)
                    file.writeBytes(templates[index % 64])
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
        if (scale == "smoke") return
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
}
