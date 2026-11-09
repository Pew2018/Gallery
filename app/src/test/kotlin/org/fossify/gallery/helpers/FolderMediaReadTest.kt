package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, manifest = Config.NONE,
    shadows = [PhotoProfileMediaLoadingTest.ScopedEnvironment::class])
class FolderMediaReadTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var root: File
    private lateinit var provider: PhotoProfileMediaLoadingTest.IndexedMediaProvider
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        root = Files.createTempDirectory("folder-read").toFile()
        File(root, ".nomedia").createNewFile()
        provider = Robolectric.buildContentProvider(PhotoProfileMediaLoadingTest.IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun cleanup() { root.deleteRecursively(); provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun photo(name: String) = File(root, name).apply { writeBytes(byteArrayOf(1, 2)) }
    private fun old(path: String) = Medium().apply { this.path = path; parentPath = File(path).parent!!; name = File(path).name; type = TYPE_IMAGES }
    private fun read(access: FolderFileAccess = DeviceFolderFileAccess(context), path: String = root.path): FolderMediaReadResult {
        val pool = Executors.newSingleThreadExecutor()
        return try { pool.submit<FolderMediaReadResult> {
            MediaFetcher(context, reportReadFailures = true).apply { folderFileAccess = access }.readFolder(
                path, false, false, true, true, true, arrayListOf(), false, hashMapOf(), hashMapOf(), null)
        }.get(10, TimeUnit.SECONDS) } finally { pool.shutdownNow() }
    }
    private fun fake(files: List<File> = emptyList(), limited: Boolean = false,
        metadata: (File) -> FolderFileMetadata = { FolderFileMetadata(2, 1, true, true) }) = object : FolderFileAccess {
        override fun enumerate(folder: String) = FolderEnumeration(files, "test", limited)
        override fun metadata(file: File) = metadata.invoke(file)
    }

    @Test fun unknownProviderFieldsAreNotCompleteAndConfirmedZeroIsData() {
        val file = photo("provider.jpg")
        val unknown = read(fake(listOf(file), metadata = { FolderFileMetadata(null, null, true, true) }))
        assertEquals(FolderReadStatus.COMPLETE, unknown.status)
        val stats = unknown.stats!!
        assertTrue(stats.has(org.fossify.gallery.models.DirectoryStats.COUNT))
        assertFalse(stats.has(org.fossify.gallery.models.DirectoryStats.SIZE))
        assertFalse(stats.has(org.fossify.gallery.models.DirectoryStats.MODIFIED))
        assertFalse(stats.has(org.fossify.gallery.models.DirectoryStats.TAKEN))
        val zero = read(fake(listOf(file), metadata = { FolderFileMetadata(2, 0, true, true) })).stats!!
        assertTrue(zero.has(org.fossify.gallery.models.DirectoryStats.MODIFIED))
        assertEquals(0L, zero.minModified)
        assertEquals(0L, zero.maxModified)
    }

    @Test fun verifiedFileModificationWinsOverStaleIndexMapAndCustomDateStillWins() {
        val file = photo("date.jpg")
        var reads = 0
        val pool = Executors.newSingleThreadExecutor()
        try {
            val result = pool.submit<FolderMediaReadResult> {
                MediaFetcher(context, reportReadFailures = true).apply {
                    folderFileAccess = fake(listOf(file), metadata = { reads++; FolderFileMetadata(2, 1, true, true) })
                }.readFolder(root.path, false, false, true, true, true, arrayListOf(), false,
                    hashMapOf(file.path to 999L), hashMapOf(file.path to 17L), null)
            }.get(10, TimeUnit.SECONDS)
            assertEquals(1, reads)
            assertEquals(1L, result.media.single().modified)
            assertEquals(17L, result.media.single().taken)
            assertFalse(result.media.single().takenIsFallback)
        } finally { pool.shutdownNow() }
    }

    @Test fun copiedJpgIsReadWithoutIndexAndAncestorMarkerDoesNotHideIt() {
        val child = File(root, "child").apply { mkdir() }
        val fresh = File(child, "NEW.JPG").apply { writeBytes(byteArrayOf(1)) }
        val result = read(path = child.path)
        assertEquals(FolderReadStatus.COMPLETE, result.status)
        assertEquals(listOf(fresh.path), result.media.map { it.path })
        assertEquals(1, result.enumerated)
        assertTrue(provider.queries.isEmpty())
    }
    @Test fun nullDirectoryEnumerationIsFailedAndKeepsOldPresentation() {
        val missing = File(root, "missing").path
        val result = read(path = missing)
        assertEquals(FolderReadStatus.FAILED, result.status)
        val cached = old("$missing/old.jpg")
        assertEquals(listOf(cached), result.presentation(listOf(cached)) { true })
    }
    @Test fun throwingEnumerationIsFailedRatherThanEmptySuccess() {
        val result = read(object : FolderFileAccess {
            override fun enumerate(folder: String): FolderEnumeration = throw IOException("offline")
            override fun metadata(file: File): FolderFileMetadata = error("unexpected metadata read")
        })
        assertEquals(FolderReadStatus.FAILED, result.status)
        assertNotNull(result.error)
    }
    @Test fun explicitLimitedVisibilityMergesNewAndOldWithoutCallingThemAllConfirmed() {
        val fresh = photo("new.jpg")
        val cached = old("${root.path}/old.jpg")
        val result = read(fake(listOf(fresh), limited = true))
        assertEquals(FolderReadStatus.PARTIAL, result.status)
        assertEquals(setOf(fresh.path, cached.path), result.presentation(listOf(cached)) { true }.map { it.path }.toSet())
        assertEquals(setOf(fresh.path), result.confirmedPaths)
        assertEquals(1, result.reasons["limited_visibility"])
    }
    @Test fun successfulNonemptyEnumerationDoesNotInventEvidenceOfMissingFiles() {
        val result = read(fake(listOf(photo("visible.jpg"))))
        assertEquals(FolderReadStatus.COMPLETE, result.status)
        assertFalse(result.reasons.containsKey("limited_visibility"))
    }
    @Test fun unknownReadableSizeZeroAndDisappearanceHaveDifferentOutcomes() {
        val unknown = photo("unknown.jpg"); val zero = photo("zero.jpg"); val gone = photo("gone.jpg")
        val result = read(fake(listOf(unknown, zero, gone)) {
            when (it) {
                unknown -> FolderFileMetadata(null, 1, true, true)
                zero -> FolderFileMetadata(0, 1, true, true)
                else -> throw IOException("vanished")
            }
        })
        assertEquals(FolderReadStatus.PARTIAL, result.status)
        assertEquals(listOf(unknown.path), result.media.map { it.path })
        assertEquals(0L, result.media.single().size)
        assertEquals(1, result.reasons["unknown_size_readable"])
        assertEquals(1, result.reasons["zero_bytes"])
        assertEquals(1, result.reasons["metadata_failed"])
    }
    @Test fun unreadableMetadataDoesNotBecomeARealZeroByteFile() {
        val result = read(fake(listOf(photo("denied.jpg"))) { FolderFileMetadata(0, 0, false, true) })
        assertEquals(FolderReadStatus.PARTIAL, result.status)
        assertEquals(1, result.reasons["unreadable"])
        assertNull(result.reasons["zero_bytes"])
    }
    @Test fun dotAndDisabledMediaTypesStayFiltered() {
        val result = read(fake(listOf(photo(".secret.jpg"), photo("movie.mp4"), photo("new.jpg"), photo("note.txt"))))
        assertEquals(listOf("new.jpg"), result.media.map { it.name })
        assertEquals(1, result.reasons["type_filtered"])
        assertEquals(1, result.reasons["dot_filtered"])
        assertEquals(1, result.reasons["unsupported"])
    }
    @Test fun revokedGrantCannotPublishEvenACachedMerge() {
        var current = true
        val result = read(object : FolderFileAccess {
            override fun enumerate(folder: String) = FolderEnumeration(listOf(photo("fresh.jpg")), "persisted-tree", current = { current })
            override fun metadata(file: File) = FolderFileMetadata(1, 1, true, true)
        })
        current = false
        try { result.presentation(listOf(old("${root.path}/old.jpg"))) { true }; fail("revoked grant published") }
        catch (_: SecurityException) { }
    }
    @Test fun partialMergeCannotRestoreUnauthorizedOldMedia() {
        val fresh = photo("new.jpg")
        val result = read(fake(listOf(fresh), true))
        assertEquals(listOf(fresh.path), result.presentation(listOf(old("${root.path}/private.jpg"))) {
            it == fresh.path
        }.map { it.path })
    }
    @Test fun securityFailureIsAccessRequired() {
        val result = read(object : FolderFileAccess {
            override fun enumerate(folder: String): FolderEnumeration = throw SecurityException("revoked")
            override fun metadata(file: File): FolderFileMetadata = error("unexpected")
        })
        assertEquals(FolderReadStatus.ACCESS_REQUIRED, result.status)
    }
    @Test fun actualZeroByteJpgIsExcluded() {
        File(root, "zero.jpg").createNewFile()
        val result = read()
        assertEquals(FolderReadStatus.COMPLETE, result.status)
        assertTrue(result.media.isEmpty())
        assertEquals(1, result.reasons["zero_bytes"])
    }
}
