package org.fossify.gallery.helpers

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoProfileReturnProbeTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var root: Path
    private lateinit var profile: PhotoProfile

    private fun <T> worker(work: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        return try { executor.submit<T> { work() }.get() } finally { executor.shutdownNow() }
    }

    @Before fun setup() {
        PhotoProfileReturnProbe.clear()
        root = Files.createTempDirectory("return-photo-source")
        profile = PhotoProfile(UUID.randomUUID().toString(), "Photos",
            listOf(PhotoSource(PhotoSourceType.FOLDER, root.toString())))
        PhotoProfileRepository.of(app).save(profile)
    }

    @After fun teardown() {
        PhotoProfileReturnProbe.clear()
        PhotoProfileRepository.of(app).delete(profile.id)
        root.toFile().deleteRecursively()
    }

    private fun record() = worker {
        val context = PhotoProfileLoadContext.capture(app, profile.id, null)!!
        PhotoProfileReturnProbe.record(app, PhotoLoadResult(context, PhotoLoadStage.COMPLETE,
            emptyList(), PhotoAccessSummary(false, true)))
        context.scope
    }

    @Test fun unchangedFolderRetainsGridButNoMediaNewChildForcesReconciliation() {
        Files.write(root.resolve(".nomedia"), byteArrayOf())
        Files.write(root.resolve("old.jpg"), byteArrayOf(1))
        val scope = record()
        assertFalse(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
        Files.write(root.resolve("new.jpg"), byteArrayOf(2))
        assertTrue(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
    }

    @Test fun sameNamedFileReplacementWithNewSizeIsDetected() {
        Files.write(root.resolve(".nomedia"), byteArrayOf())
        Files.write(root.resolve("same.jpg"), byteArrayOf(1))
        val scope = record()
        assertFalse(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
        Files.write(root.resolve("same.jpg"), byteArrayOf(1, 2, 3, 4))
        assertTrue(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
    }

    @Test fun renameWithSameItemCountAndExternalDeleteAreDetected() {
        Files.write(root.resolve("a.jpg"), byteArrayOf(1))
        Files.write(root.resolve("b.jpg"), byteArrayOf(2))
        val scope = record()
        assertFalse(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
        Files.move(root.resolve("b.jpg"), root.resolve("c.jpg"))
        assertTrue(worker { PhotoProfileReturnProbe.needsReconcile(app, scope) })
        val laterScope = record()
        Files.delete(root.resolve("a.jpg"))
        assertTrue(worker { PhotoProfileReturnProbe.needsReconcile(app, laterScope) })
    }
}
