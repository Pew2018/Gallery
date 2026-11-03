package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.models.Directory
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, manifest = Config.NONE,
    shadows = [PhotoProfileMediaLoadingTest.ScopedEnvironment::class])
class NoMediaCategoryRefreshTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: PhotoProfileMediaLoadingTest.IndexedMediaProvider
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        provider = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun cleanup() { provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun <T> background(block: () -> T): T {
        val pool = Executors.newSingleThreadExecutor()
        return try { pool.submit<T> { block() }.get(10, TimeUnit.SECONDS) } finally { pool.shutdownNow() }
    }
    private fun old(path: String, count: Int = 1) = Directory().apply {
        this.path = path; name = File(path).name; mediaCnt = count; tmb = "$path/old.jpg"
    }
    private fun scan(cached: List<Directory>, dots: Boolean = false, noMedia: Boolean = true,
        excluded: Boolean = false, showProtected: Boolean = true) = background {
        CategorizedFolderScanner.scan(context, cached, booleanArrayOf(dots, excluded, noMedia, showProtected), { false })
    }
    private fun photo(folder: File, name: String, modified: Long = 1000) = File(folder, name).apply {
        writeBytes(byteArrayOf(1, 2, 3)); setLastModified(modified)
    }

    @Test fun noMediaCategoryRescansCountCoverAndNewChildrenWithoutAnIndexOrGlobalHiddenSetting() {
        val root = Files.createTempDirectory("nomedia-category").toFile()
        try {
            File(root, ".nomedia").createNewFile()
            photo(root, "old.jpg")
            context.config.everShownFolders = setOf(root.path)
            context.config.saveCustomSorting(root.path, SORT_BY_DATE_MODIFIED or SORT_DESCENDING)
            val initial = scan(listOf(old(root.path)))
            assertEquals(1, initial.directories.single { it.path == root.path }.mediaCnt)
            val fresh = photo(root, "new.jpg", 2000)
            photo(root, ".private.jpg", 3000)
            val child = File(root, "new-child").apply { mkdir() }
            photo(child, "child.jpg")
            val before = context.config.shouldShowHidden
            val final = scan(initial.directories)
            val updated = final.directories.single { it.path == root.path }
            assertEquals(2, updated.mediaCnt)
            assertEquals(fresh.path, updated.tmb)
            assertTrue(updated.folderStatuses.contains(context.getString(org.fossify.gallery.R.string.folder_status_nomedia)))
            assertEquals(1, final.directories.single { it.path == child.path }.mediaCnt)
            assertEquals(before, context.config.shouldShowHidden)
            assertTrue(File(root, ".nomedia").isFile)
            fresh.delete(); File(root, "old.jpg").delete()
            assertFalse(scan(final.directories).directories.any { it.path == root.path })
        } finally { root.deleteRecursively() }
    }

    @Test fun directNoMediaFolderIgnoresStaleIndexedRowsAndStillFindsNewFiles() {
        val root = Files.createTempDirectory("nomedia-direct").toFile()
        try {
            File(root, ".nomedia").createNewFile()
            provider.add("${root.path}/deleted-indexed.jpg")
            val fresh = photo(root, "new.jpg")
            val loaded = background { MediaFetcher(context, reportReadFailures = true).getFilesFrom(
                root.path, false, false, true, true, true, arrayListOf(), false, hashMapOf(), hashMapOf(), null) }
            assertEquals(listOf(fresh.path), loaded.map { it.path })
            assertTrue("nomedia media load must not depend on provider rows", provider.queries.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun diskMarkersIncludeAncestorsAndRefreshAfterAddRemoveWhileNamedDirectoriesAreNotMarkers() {
        val root = Files.createTempDirectory("nomedia-marker").toFile()
        try {
            val child = File(root, "child").apply { mkdir() }
            File(root, ".nomedia").mkdir()
            assertFalse(NoMediaSnapshot().contains(child.path))
            File(root, ".nomedia").delete(); File(root, ".nomedia").createNewFile()
            assertTrue(NoMediaSnapshot().contains(child.path))
            assertTrue(root.path in background { context.getNoMediaFoldersSync(listOf(child.path)) })
            File(root, ".nomedia").delete()
            assertFalse(NoMediaSnapshot().contains(child.path))
            assertFalse(root.path in background { context.getNoMediaFoldersSync(listOf(child.path)) })
        } finally { root.deleteRecursively() }
    }

    @Test fun enumerationFailurePreservesOldCardAndIsNotReportedAsAConfirmedEmptyFolder() {
        val root = Files.createTempDirectory("nomedia-unreadable").toFile()
        try {
            File(root, ".nomedia").createNewFile()
            val missing = "${root.path}/no-longer-readable"
            val result = scan(listOf(old(missing, 7)))
            assertEquals(7, result.directories.single { it.path == missing }.mediaCnt)
            assertTrue(missing in result.failures)
        } finally { root.deleteRecursively() }
    }

    @Test fun dotExcludedNoMediaAndIncludedRulesRemainIndependent() {
        val root = Files.createTempDirectory("nomedia-policy").toFile()
        try {
            File(root, ".nomedia").createNewFile()
            val dot = File(root, ".hidden").apply { mkdir() }
            val markers = NoMediaSnapshot()
            val ordinary = FolderCategoryPolicy(false, false, false, true, emptySet(), emptySet())
            assertFalse(ordinary.allows(root.path, markers.contains(root.path), false))
            val noMediaOnly = ordinary.copy(noMedia = true)
            assertTrue(noMediaOnly.allows(root.path, markers.contains(root.path), false))
            assertFalse(noMediaOnly.allows(dot.path, markers.contains(dot.path), false))
            assertFalse(noMediaOnly.copy(excludedRoots = setOf(root.path)).allows(root.path, true, false))
            assertTrue(ordinary.copy(includedRoots = setOf(root.path)).allows(root.path, true, false))
            assertFalse(noMediaOnly.copy(showProtected = false).allows(root.path, true, true))
        } finally { root.deleteRecursively() }
    }

    @Test fun protectedAncestorIsNeverScannedAndItsCachedPrivateMetadataIsCleared() {
        val root = Files.createTempDirectory("nomedia-protected").toFile()
        try {
            File(root, ".nomedia").createNewFile(); photo(root, "private.jpg")
            val child = File(root, "child").apply { mkdir() }; photo(child, "private-child.jpg")
            context.config.addFolderProtection(root.path, "hash", PROTECTION_PIN)
            val result = scan(listOf(old(root.path, 8), old(child.path, 9)))
            result.directories.filter { it.path == root.path || it.path == child.path }.forEach {
                assertEquals(0, it.mediaCnt); assertEquals("", it.tmb)
            }
            assertFalse(scan(listOf(old(root.path)), showProtected = false).directories.any { it.path == root.path })
        } finally { root.deleteRecursively() }
    }

    @Test fun scopedVisibilityRecognizesAnUnindexedOwnMarkerAndOnlyExplicitInclusionOverridesIt() {
        val root = Files.createTempDirectory("nomedia-scoped-visibility").toFile()
        try {
            File(root, ".nomedia").mkdir()
            fun visible(included: MutableSet<String> = mutableSetOf()) = root.path.shouldFolderBeVisible(
                mutableSetOf(), included, false, hashMapOf()) { _, _ -> }
            assertTrue(visible())
            File(root, ".nomedia").delete(); File(root, ".nomedia").createNewFile()
            assertFalse(visible())
            assertTrue(visible(mutableSetOf(root.path)))
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationCannotPublishAPartialCategoryResult() {
        try {
            CategorizedFolderScanner.scan(context, emptyList(), booleanArrayOf(false, false, true, true), { true })
            fail("canceled category scan returned success")
        } catch (_: java.util.concurrent.CancellationException) { }
    }
    @Test fun refreshedKnownAlbumIsPublishedBeforeSlowRecursiveDiscovery() {
        val root = Files.createTempDirectory("nomedia-staged").toFile()
        val pool = Executors.newSingleThreadExecutor()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val seen = java.util.concurrent.atomic.AtomicReference<CategorizedFolderScanner.Result>()
        try {
            File(root, ".nomedia").createNewFile()
            photo(root, "old.jpg"); photo(root, "new.jpg")
            val work = pool.submit<CategorizedFolderScanner.Result> {
                CategorizedFolderScanner.scan(context, listOf(old(root.path)), booleanArrayOf(false, false, true, true), { false },
                    onProgress = { result -> if (result.directories.any { it.path == root.path && it.mediaCnt == 2 }) seen.set(result) },
                    discover = { _, _, _ -> entered.countDown(); release.await(5, TimeUnit.SECONDS); FolderDiscoveryResult(emptySet(), emptySet()) })
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNotNull("new media card waited for recursive discovery", seen.get())
            assertFalse(work.isDone)
            release.countDown()
            assertEquals(2, work.get(10, TimeUnit.SECONDS).directories.single { it.path == root.path }.mediaCnt)
        } finally { release.countDown(); pool.shutdownNow(); root.deleteRecursively() }
    }


    @Test fun incompleteDiscoveryIsReportedWithoutDroppingReadableCachedAlbums() {
        val root = Files.createTempDirectory("category-partial-discovery").toFile()
        try {
            File(root, ".nomedia").createNewFile()
            photo(root, "old.jpg")
            context.config.includedFolders = mutableSetOf(root.path)
            val result = background {
                CategorizedFolderScanner.scan(context, listOf(old(root.path)), booleanArrayOf(false, false, true, true),
                    { false }, discover = { _, _, _ ->
                        FolderDiscoveryResult(setOf(root.path), setOf("/unreadable"), truncated = true)
                    })
            }
            assertTrue("/unreadable" in result.failures)
            assertTrue("discovery" in result.failures)
            assertTrue(result.directories.any { it.path == root.path && it.mediaCnt == 1 })
        } finally { root.deleteRecursively() }
    }
}
