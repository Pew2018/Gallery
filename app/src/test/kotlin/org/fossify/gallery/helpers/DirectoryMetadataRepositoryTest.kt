package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DirectoryMetadataRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var root: File
    private val readExecutor = Executors.newSingleThreadExecutor()
    private fun dbCount(): Int? = readExecutor.submit<Int?> {
        context.directoryDB.getByPath(root.path)?.mediaCnt
    }.get(5, TimeUnit.SECONDS)
    private fun commit(snapshot: DirectoryScanSnapshot): Boolean =
        readExecutor.submit<Boolean> {
            commit( snapshot)
        }.get(5, TimeUnit.SECONDS)
    private val scope get() = DirectoryScanScope(context.config.filterMedia, context.config.shouldShowHidden)
    @Before fun setup() {
        GalleryDatabase.destroyInstance()
        context.getSharedPrefs().edit().clear().commit()
        context.config.filterMedia = TYPE_IMAGES
        root = Files.createTempDirectory("directory-metadata").toFile()
        File(root, ".nomedia").createNewFile()
    }
    @After fun cleanup() { readExecutor.shutdownNow(); root.deleteRecursively(); GalleryDatabase.destroyInstance() }
    private fun medium(name: String): Medium {
        val file = File(root, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        return Medium().apply {
            this.name = name; path = file.path; parentPath = root.path
            type = TYPE_IMAGES; size = 3; modified = 1000L; taken = 1000L
        }
    }
    private fun snapshot(
        version: Long, media: List<Medium>, status: FolderReadStatus = FolderReadStatus.COMPLETE,
        scanScope: DirectoryScanScope = scope
    ): DirectoryScanSnapshot {
        val read = FolderMediaReadResult(ArrayList(media), status, "test", media.size, emptyMap())
        return DirectoryScanSnapshot(root.path, version, scanScope, read, media.map { it.copy() })
    }
    @Test fun missingRowIsInsertedAndFullScanSupportsDecreasesAndZero() {
        val a = medium("a.jpg")
        val b = medium("b.jpg")
        assertTrue(commit( snapshot(DirectoryMetadataRepository.begin(root.path), listOf(a, b))))
        assertEquals(2, dbCount())
        assertTrue(commit( snapshot(DirectoryMetadataRepository.begin(root.path), listOf(a))))
        assertEquals(1, dbCount())
        assertTrue(commit( snapshot(DirectoryMetadataRepository.begin(root.path), emptyList())))
        assertEquals(0, dbCount())
        assertTrue(root.isDirectory)
    }
    @Test fun partialAndMismatchedScopeCannotPoisonACompleteCount() {
        val a = medium("a.jpg")
        assertTrue(commit( snapshot(DirectoryMetadataRepository.begin(root.path), listOf(a))))
        assertFalse(commit( snapshot(
            DirectoryMetadataRepository.begin(root.path), emptyList(), FolderReadStatus.PARTIAL
        )))
        assertEquals(1, dbCount())
        assertFalse(commit( snapshot(
            DirectoryMetadataRepository.begin(root.path), emptyList(),
            scanScope = scope.copy(showDotFiles = !scope.showDotFiles)
        )))
        assertEquals(1, dbCount())
    }
    @Test fun lateOldScanCannotReplaceNewerCommittedResult() {
        val a = medium("a.jpg")
        val b = medium("b.jpg")
        val stale = DirectoryMetadataRepository.begin(root.path)
        val latest = DirectoryMetadataRepository.begin(root.path)
        assertTrue(commit( snapshot(latest, listOf(a, b))))
        assertFalse(commit( snapshot(stale, listOf(a))))
        assertEquals(2, dbCount())
    }
    @Test fun protectedDirectoryRequiresTheExactLiveFolderSession() {
        val a = medium("locked.jpg")
        context.config.addFolderProtection(root.path, "hash", PROTECTION_PIN)
        assertFalse(commit(
            snapshot(DirectoryMetadataRepository.begin(root.path), listOf(a))))
        assertNull(dbCount())
        val token = FolderUnlockSessionManager.createSession(root.path)
        try {
            assertTrue(commit(
                snapshot(DirectoryMetadataRepository.begin(root.path), listOf(a))
                    .copy(folderSessionId = token)))
            assertEquals(1, dbCount())
            FolderUnlockSessionManager.revokeSession(token)
            assertFalse(commit(
                snapshot(DirectoryMetadataRepository.begin(root.path), emptyList())
                    .copy(folderSessionId = token)))
            assertEquals(1, dbCount())
        } finally { FolderUnlockSessionManager.revokeSession(token) }
    }
    @Test fun invalidatedScanIsDeferredRatherThanPublished() {
        val a = medium("a.jpg")
        val ticket = DirectoryMetadataRepository.begin(root.path)
        assertFalse(commit(
            snapshot(ticket, listOf(a)).copy(isCurrent = { false })))
        assertTrue(root.path in DirectoryMetadataRepository.pendingPaths())
        assertNull(dbCount())
    }
}
