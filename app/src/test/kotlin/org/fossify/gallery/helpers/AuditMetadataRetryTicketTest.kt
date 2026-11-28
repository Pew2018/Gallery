package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AuditMetadataRetryTicketTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var root: File
    private val readExecutor = Executors.newSingleThreadExecutor()

    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        root = Files.createTempDirectory("metadata-retry").toFile()
        File(root, ".nomedia").createNewFile()
        File(root, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
    }

    @After fun cleanup() {
        readExecutor.shutdownNow()
        root.deleteRecursively()
        GalleryDatabase.destroyInstance()
    }

    private fun commit(snapshot: DirectoryScanSnapshot): Boolean =
        readExecutor.submit<Boolean> {
            DirectoryMetadataRepository.commit(context, snapshot)
        }.get(5, TimeUnit.SECONDS)

    /** The metadata executor only queues one task; wait without requiring a free slot up front. */
    private fun awaitMetadataIdle() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            val idle = CountDownLatch(1)
            try {
                GalleryDirectoryMetadataExecutor.execute { idle.countDown() }
                if (idle.await(15, TimeUnit.SECONDS)) return
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                Thread.sleep(25)
            }
        }
        fail("metadata executor did not drain")
    }

    @Test fun refreshPendingPathsDoesNotReplaceAnActiveTicket() {
        val versionA = DirectoryMetadataRepository.begin(root.path)
        assertEquals(versionA, DirectoryMetadataRepository.activeRequestVersion(root.path))

        // Queue a stats retry while the foreground ticket is still live. The
        // retry must skip begin() rather than steal version A.
        context.config.directorySorting = SORT_BY_SIZE
        DirectoryMetadataRepository.ensureStatistics(
            context,
            listOf(Directory().apply { path = root.path }),
        )
        awaitMetadataIdle()

        assertEquals(
            "retry must not allocate a newer ticket over an in-flight scan",
            versionA,
            DirectoryMetadataRepository.activeRequestVersion(root.path),
        )

        val media = listOf(
            Medium().apply {
                name = "a.jpg"; path = File(root, "a.jpg").path; parentPath = root.path
                type = TYPE_IMAGES; size = 3; modified = 10; taken = 10
            }
        )
        val scope = DirectoryScanScope(context.config.filterMedia, context.config.shouldShowHidden)
        val read = FolderMediaReadResult(ArrayList(media), FolderReadStatus.COMPLETE, "test", media.size, emptyMap())
        assertTrue(
            commit(DirectoryScanSnapshot(root.path, versionA, scope, read, media.map { it.copy() }))
        )
        assertEquals(1, readExecutor.submit<Int?> {
            context.directoryDB.getByPath(root.path)?.mediaCnt
        }.get(5, TimeUnit.SECONDS))

        val versionB = DirectoryMetadataRepository.begin(root.path)
        assertNotEquals(versionA, versionB)
        assertFalse(
            commit(DirectoryScanSnapshot(root.path, versionA, scope, read, media.map { it.copy() }))
        )
        assertTrue(
            commit(DirectoryScanSnapshot(root.path, versionB, scope, read, media.map { it.copy() }))
        )
    }

    @Test fun deferredTicketCanBeRetriedByRefreshPendingPaths() {
        val abandoned = DirectoryMetadataRepository.begin(root.path)
        DirectoryMetadataRepository.defer(root.path, abandoned)
        assertNull(DirectoryMetadataRepository.activeRequestVersion(root.path))
        assertTrue(root.path in DirectoryMetadataRepository.pendingPaths())

        DirectoryMetadataRepository.refreshPendingPaths(context)
        awaitMetadataIdle()

        assertEquals(1, readExecutor.submit<Int?> {
            context.directoryDB.getByPath(root.path)?.mediaCnt
        }.get(5, TimeUnit.SECONDS))
    }
}
