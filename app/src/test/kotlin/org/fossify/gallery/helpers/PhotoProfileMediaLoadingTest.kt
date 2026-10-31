package org.fossify.gallery.helpers

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.models.DateTaken
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowContentResolver
import java.nio.file.Files
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoProfileMediaLoadingTest {
    private lateinit var provider: IndexedMediaProvider
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        GalleryDatabase.destroyInstance()
        PhotoProfileMediaCache.invalidate()
        PhotoViewerSnapshotStore.clear()
        provider = Robolectric.buildContentProvider(IndexedMediaProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun close() { provider.db.close(); GalleryDatabase.destroyInstance() }
    private fun <T> background(block: () -> T): T {
        val worker = Executors.newSingleThreadExecutor()
        return try { worker.submit<T> { block() }.get() } finally { worker.shutdownNow() }
    }

    @Test fun oneCompleteQueryReusesMetadataAndCustomDatesAndSupplementsUnindexedFiles() {
        val root = Files.createTempDirectory("photo-loading")
        try {
            val indexed = Files.write(root.resolve("indexed.jpg"), byteArrayOf(1, 2, 3)).toString()
            val unindexed = Files.write(root.resolve("new.jpg"), byteArrayOf(4, 5, 6)).toString()
            val child = Files.createDirectory(root.resolve("child"))
            Files.write(child.resolve("nested.jpg"), byteArrayOf(1))
            provider.add(indexed, size = 321, taken = 4000)
            provider.add(child.resolve("nested.jpg").toString())
            val result = background {
                context.dateTakensDB.insertAll(listOf(DateTaken(null, indexed, "indexed.jpg", root.toString(), 9999, 0, 0)))
                MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(setOf(root.toString()), emptySet(), arrayListOf(indexed))
            }.associateBy { it.path }
            assertEquals(setOf(indexed, unindexed), result.keys)
            assertEquals(321L, result.getValue(indexed).size)
            assertEquals(9999L, result.getValue(indexed).taken)
            assertEquals(12000L, result.getValue(indexed).modified)
            assertTrue(result.getValue(indexed).isFavorite)
            assertEquals(3L, result.getValue(unindexed).size)
            assertEquals(1, provider.queries.size)
            assertEquals(7, provider.projections.single().size)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun favoritesUseExactFilesAndDoNotEnumerateTheirParents() {
        val root = Files.createTempDirectory("photo-favorites")
        try {
            val wanted = Files.write(root.resolve("favorite.jpg"), byteArrayOf(1)).toString()
            val missingIndex = Files.write(root.resolve("unindexed.jpg"), byteArrayOf(2)).toString()
            val other = Files.write(root.resolve("other.jpg"), byteArrayOf(3)).toString()
            provider.add(wanted)
            provider.add(other)
            val result = background { MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(
                emptySet(), setOf(wanted, missingIndex), arrayListOf(wanted, missingIndex)) }
            assertEquals(setOf(wanted, missingIndex), result.map { it.path }.toSet())
            assertTrue(result.all { it.isFavorite })
            assertEquals(1, provider.queries.size)
            assertTrue(provider.queries.single()!!.contains(" IN ("))
            assertFalse(provider.queries.single()!!.contains("LIKE"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test @Config(sdk = [31], shadows = [ScopedEnvironment::class]) fun scopedIndexSurvivesInaccessibleFileApiAndStillAddsUnindexedFiles() {
        val root = Files.createTempDirectory("photo-scoped")
        try {
            val indexedOnly = root.resolve("indexed-only.jpg").toString()
            val fileOnly = Files.write(root.resolve("file-only.jpg"), byteArrayOf(1)).toString()
            provider.add(indexedOnly)
            val result = background { MediaFetcher(context, TYPE_IMAGES).getPhotoProfileMedia(
                setOf(root.toString()), emptySet(), arrayListOf()) }
            assertEquals(setOf(indexedOnly, fileOnly), result.map { it.path }.toSet())
        } finally { root.toFile().deleteRecursively() }
    }

    private fun profile(folder: String): String {
        val id = java.util.UUID.randomUUID().toString()
        PhotoProfileRepository.of(context).save(PhotoProfile(id, "Test", listOf(PhotoSource(PhotoSourceType.FOLDER, folder))))
        return id
    }

    @Test fun indexedStagePrecedesSupplementAndSecondEntryUsesFreshlyValidatedCache() {
        val root = Files.createTempDirectory("photo-stages")
        try {
            val path = Files.write(root.resolve("indexed.jpg"), byteArrayOf(1)).toString()
            val added = root.resolve("added-after-index.jpg")
            provider.add(path)
            val id = profile(root.toString())
            val stages = ArrayList<PhotoLoadStage>()
            val first = background {
                PhotoProfileMediaLoader(context).loadStaged(id, true) { stage ->
                    stages.add(stage.stage)
                    assertEquals(listOf(path), stage.media.map { it.path })
                    if (stage.stage == PhotoLoadStage.INDEXED) Files.write(added, byteArrayOf(2))
                }
            }
            assertEquals(listOf(PhotoLoadStage.INDEXED), stages)
            assertEquals(PhotoLoadStage.COMPLETE, first.stage)
            assertEquals(setOf(path, added.toString()), first.media.map { it.path }.toSet())
            assertTrue(first.access.publicOnly)
            val restored = ArrayList<PhotoLoadStage>()
            background { PhotoProfileMediaLoader(context).loadStaged(id, true) { restored.add(it.stage) } }
            assertEquals(PhotoLoadStage.CACHE_PREVIEW, restored.first())
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun unrelatedPreferencesAndRenameDoNotInvalidateButSourceChangeDoes() {
        val id = profile("/Camera")
        val scope = PhotoProfileScope.capture(context, id, null)!!
        context.getSharedPrefs().edit().putBoolean("unrelated_test_preference", true).commit()
        assertTrue(scope.isCurrent(context))
        val old = PhotoProfileRepository.of(context).state().current!!
        PhotoProfileRepository.of(context).updateExisting(old.copy(name = "Renamed"))
        assertTrue(scope.isCurrent(context))
        PhotoProfileRepository.of(context).updateExisting(old.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Other"))))
        assertFalse(scope.isCurrent(context))
    }

    @Test fun switchingSourcesDuringIndexedCallbackDiscardsRequestInsteadOfPublishingEmptySuccess() {
        val root = Files.createTempDirectory("photo-invalidation")
        try {
            val path = Files.write(root.resolve("a.jpg"), byteArrayOf(1)).toString()
            provider.add(path)
            val id = profile(root.toString())
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                background { PhotoProfileMediaLoader(context).loadStaged(id, true) {
                    val old = PhotoProfileRepository.of(context).state().current!!
                    PhotoProfileRepository.of(context).updateExisting(old.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Other"))))
                } }
            }.also { assertTrue(it.cause is PhotoLoadInvalidatedException) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun finalBatchRechecksSymlinkTargetAndNeverCachesProtectedSource() {
        val root = Files.createTempDirectory("photo-stage-link")
        try {
            val public = Files.createDirectory(root.resolve("public"))
            val private = Files.createDirectory(root.resolve("private"))
            val a = Files.write(public.resolve("a.jpg"), byteArrayOf(1))
            val secret = Files.write(private.resolve("secret.jpg"), byteArrayOf(2))
            val link = Files.createSymbolicLink(public.resolve("link.jpg"), a)
            context.config.addFolderProtection(private.toString(), "test-secret", org.fossify.commons.helpers.PROTECTION_PIN)
            provider.add(link.toString())
            val id = profile(public.toString())
            val result = background { PhotoProfileMediaLoader(context).loadStaged(id, true) { stage ->
                if (stage.stage == PhotoLoadStage.INDEXED) {
                    assertTrue(stage.media.any { it.path == link.toString() })
                    Files.delete(link)
                    Files.createSymbolicLink(link, secret)
                }
            } }
            assertFalse(result.media.any { it.path == link.toString() })
            assertTrue(result.access.needsUnlock)
            assertFalse(result.access.publicOnly)
            assertNull(PhotoProfileMediaCache.read(result.context))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun boundedPreviewCoversAnchorAndMetadataCacheCopiesItemsAndHonorsRevision() {
        val id = profile("/Camera")
        val scope = PhotoProfileScope.capture(context, id, null)!!
        val request = PhotoProfileLoadContext(scope, PhotoContentRevision.current(), emptySet())
        val media = (0..299).map { n -> Medium(null, "$n.jpg", "/Camera/$n.jpg", "/Camera", 1, n.toLong(), 1, TYPE_IMAGES, 0, false, 0, 0) }
        val preview = PhotoProfileMediaCache.preview(media, "/Camera/200.jpg", 80)
        assertEquals(80, preview.size)
        assertTrue(preview.any { it.path == "/Camera/200.jpg" })
        PhotoProfileMediaCache.offer(PhotoLoadResult(request, PhotoLoadStage.COMPLETE, media, PhotoAccessSummary(false, true)))
        val read = PhotoProfileMediaCache.read(request)!!
        read.first().name = "Changed"
        assertEquals("0.jpg", PhotoProfileMediaCache.read(request)!!.first().name)
        PhotoContentRevision.changed()
        assertNull(PhotoProfileMediaCache.read(request))
    }

    @Test fun viewerSnapshotCopiesMetadataAndContentMutationInvalidatesPendingHandoff() {
        val root = Files.createTempDirectory("photo-viewer-snapshot")
        try {
            val path = Files.write(root.resolve("a.jpg"), byteArrayOf(1)).toString()
            val id = profile(root.toString())
            val item = Medium(null, "a.jpg", path, root.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            val token = PhotoViewerSnapshotStore.offer(context, id, null, listOf(item))!!
            item.name = "Mutated"
            val taken = PhotoViewerSnapshotStore.take(context, token, id, null, path)!!
            assertEquals("a.jpg", taken.single().name)
            val validated = background { PhotoViewerSnapshotStore.validate(context, id, taken, path) }
            assertNotNull(validated)
            val pending = PhotoViewerSnapshotStore.offer(context, id, null, taken)!!
            PhotoContentRevision.changed()
            assertNull(PhotoViewerSnapshotStore.take(context, pending, id, null, path))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerSnapshotRechecksCanonicalTargetAndRejectsDeletedClickedFile() {
        val root = Files.createTempDirectory("photo-viewer-link")
        try {
            val public = Files.createDirectory(root.resolve("public"))
            val private = Files.createDirectory(root.resolve("private"))
            val file = Files.write(public.resolve("a.jpg"), byteArrayOf(1))
            val secret = Files.write(private.resolve("secret.jpg"), byteArrayOf(2))
            val alias = Files.createSymbolicLink(public.resolve("link.jpg"), file)
            val id = profile(public.toString())
            context.config.addFolderProtection(private.toString(), "test-secret", org.fossify.commons.helpers.PROTECTION_PIN)
            val item = Medium(null, "link.jpg", alias.toString(), public.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            val token = PhotoViewerSnapshotStore.offer(context, id, null, listOf(item))!!
            val taken = PhotoViewerSnapshotStore.take(context, token, id, null, alias.toString())!!
            Files.delete(alias)
            Files.createSymbolicLink(alias, secret)
            assertNull(background { PhotoViewerSnapshotStore.validate(context, id, taken, alias.toString()) })
            Files.delete(alias)
            assertNull(background { PhotoViewerSnapshotStore.validate(context, id, taken, alias.toString()) })
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerHandoffRejectsScopeChange() {
        val root = Files.createTempDirectory("photo-viewer-scope")
        try {
            val path = Files.write(root.resolve("a.jpg"), byteArrayOf(1)).toString()
            val id = profile(root.toString())
            val item = Medium(null, "a.jpg", path, root.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            val token = PhotoViewerSnapshotStore.offer(context, id, null, listOf(item))!!
            val old = PhotoProfileRepository.of(context).state().current!!
            PhotoProfileRepository.of(context).updateExisting(old.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Other"))))
            assertNull(PhotoViewerSnapshotStore.take(context, token, id, null, path))
            assertNull(background { PhotoViewerSnapshotStore.validate(context, id, listOf(item), path) })
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerFirstFrameDoesNotQueryOrScanTheRemainingProfile() {
        val root = Files.createTempDirectory("photo-first-frame")
        try {
            val path = Files.write(root.resolve("selected.jpg"), byteArrayOf(1)).toString()
            // An unrelated broken symbolic link must not delay or invalidate this image.
            Files.createSymbolicLink(root.resolve("broken.jpg"), root.resolve("missing.jpg"))
            val id = profile(root.toString())
            val item = Medium(null, "selected.jpg", path, root.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            val first = background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) }!!
            assertEquals(listOf(path), first.media.map { it.path })
            assertEquals(PhotoLoadStage.INDEXED, first.stage)
            assertTrue(provider.queries.isEmpty())
            Files.delete(java.nio.file.Paths.get(path))
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) })
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerFirstFrameRechecksNoMediaWithoutAGlobalQuery() {
        val root = Files.createTempDirectory("photo-first-nomedia")
        try {
            val folder = Files.createDirectory(root.resolve("camera"))
            val path = Files.write(folder.resolve("a.jpg"), byteArrayOf(1)).toString()
            val id = profile(folder.toString())
            val item = Medium(null, "a.jpg", path, folder.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            assertNotNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) })
            Files.write(root.resolve(".nomedia"), byteArrayOf())
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) })
            assertTrue(provider.queries.isEmpty())
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerFirstFrameRejectsProtectedSymlinkAndWrongSource() {
        val root = Files.createTempDirectory("photo-first-security")
        try {
            val public = Files.createDirectory(root.resolve("public"))
            val private = Files.createDirectory(root.resolve("private"))
            val target = Files.write(private.resolve("secret.jpg"), byteArrayOf(1))
            val alias = Files.createSymbolicLink(public.resolve("alias.jpg"), target)
            val id = profile(public.toString())
            context.config.addFolderProtection(private.toString(), "secret", org.fossify.commons.helpers.PROTECTION_PIN)
            val item = Medium(null, "alias.jpg", alias.toString(), public.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) })
            val outside = item.copy(path = target.toString(), parentPath = private.toString())
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, outside) })
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, "expired-session", item) })
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun viewerFirstFrameCancellationAndScopeMutationFailClosed() {
        val root = Files.createTempDirectory("photo-first-cancel")
        try {
            val path = Files.write(root.resolve("a.jpg"), byteArrayOf(1)).toString()
            val id = profile(root.toString())
            val item = Medium(null, "a.jpg", path, root.toString(), 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) { true } })
            val first = background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) }!!
            val old = PhotoProfileRepository.of(context).state().current!!
            PhotoProfileRepository.of(context).updateExisting(old.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Other"))))
            assertFalse(first.context.isCurrent(context))
            assertNull(background { PhotoViewerSnapshotStore.firstFrame(context, id, null, item) })
        } finally { root.toFile().deleteRecursively() }
    }

    /** Robolectric has no default volume for Environment's API-30 manager query. */
    @Implements(android.os.Environment::class)
    class ScopedEnvironment : ShadowEnvironment() {
        companion object {
            @JvmStatic @Implementation(minSdk = 30)
            fun isExternalStorageManager(): Boolean = false
        }
    }

    class IndexedMediaProvider : ContentProvider() {
        lateinit var db: SQLiteDatabase
        val queries = ArrayList<String?>()
        val projections = ArrayList<List<String>>()
        override fun onCreate(): Boolean {
            db = SQLiteDatabase.create(null)
            db.execSQL("CREATE TABLE media (_id INTEGER PRIMARY KEY, _display_name TEXT, _data TEXT, date_modified INTEGER, datetaken INTEGER, _size INTEGER, duration INTEGER)")
            return true
        }
        fun add(path: String, size: Long = 10, taken: Long = 4000) {
            db.execSQL("INSERT INTO media (_display_name, _data, date_modified, datetaken, _size, duration) VALUES (?, ?, 12, ?, ?, 0)",
                arrayOf(path.substringAfterLast('/'), path, taken, size))
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries.add(selection)
            projections.add(projection?.toList().orEmpty())
            return db.query("media", projection, selection, selectionArgs, null, null, sortOrder)
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}

