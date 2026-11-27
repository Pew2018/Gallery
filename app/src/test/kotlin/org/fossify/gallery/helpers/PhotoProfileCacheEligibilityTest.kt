package org.fossify.gallery.helpers

import android.app.Application
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowSystemClock
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Phase 1.1 cache eligibility.
 *
 * The Phase 1 hot-cache path never fired on a Pixel 8 Pro with all-files access:
 * [PhotoProfileCatalogCheckpoint.capture] returns null there, so the complete-reuse
 * check failed and deleted the only ordered copy of the catalog. Every entry then
 * paid the full Room filter again (938-2416 ms for ~5000 rows).
 *
 * These tests pin the two levels apart: preview eligibility must survive a missing
 * or changed checkpoint, while authorization and freshness must still be enforced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], manifest = Config.NONE, application = Application::class,
    shadows = [PhotoProfileCacheEligibilityTest.FullAccessEnvironment::class])
class PhotoProfileCacheEligibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun prepare() {
        GalleryDatabase.destroyInstance()
        PhotoProfileMediaCache.invalidate()
    }

    @After fun close() {
        PhotoProfileMediaCache.invalidate()
        GalleryDatabase.destroyInstance()
    }

    private fun profile(folder: String, vararg sources: PhotoSource): String {
        val id = UUID.randomUUID().toString()
        val list = if (sources.isEmpty()) listOf(PhotoSource(PhotoSourceType.FOLDER, folder)) else sources.toList()
        PhotoProfileRepository.of(context).save(PhotoProfile(id, "Test", list))
        return id
    }

    private fun request(profileId: String, revision: Long = PhotoContentRevision.current(),
                        favorites: Set<String> = emptySet()): PhotoProfileLoadContext? {
        val scope = PhotoProfileScope.capture(context, profileId, null) ?: return null
        return PhotoProfileLoadContext(scope, revision, favorites)
    }

    private fun catalog(size: Int, folder: String): List<Medium> = (0 until size).map { index ->
        Medium(index.toLong() + 1, "photo-$index.jpg", "$folder/photo-$index.jpg", folder,
            1_000L - index, 1_000L - index, 10L, TYPE_IMAGES, 0, false, 0, index.toLong() + 1)
    }

    private fun offer(request: PhotoProfileLoadContext, media: List<Medium>,
                      stage: PhotoLoadStage = PhotoLoadStage.COMPLETE,
                      checkpoint: PhotoProfileCatalogCheckpoint? = null): Boolean =
        PhotoProfileMediaCache.offer(
            context, PhotoLoadResult(request, stage, media, PhotoAccessSummary(false, true)), checkpoint)

    // A01: ALL_FILES, no checkpoint, otherwise valid.
    @Test fun fullAccessCatalogWithoutCheckpointIsNotEvictedFromThePreviewPath() {
        val root = Files.createTempDirectory("cache-allfiles")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertNull("the test environment must behave as ALL_FILES",
                PhotoProfileCatalogCheckpoint.capture(context, req.scope))

            assertFalse("complete reuse must stay unavailable without a checkpoint", offer(req, catalog(300, root.toString())))

            val (verdict, reusable) = PhotoProfileMediaCache.reuse(req, context)
            assertTrue("the metadata is still a safe preview source", verdict.previewEligible)
            assertFalse(verdict.completeReusable)
            assertEquals(PhotoCacheMissReason.CHECKPOINT_UNAVAILABLE, verdict.missReason)
            assertFalse("a missing checkpoint must never delete the entry", verdict.eviction)
            assertNull(reusable)
            assertEquals(300, verdict.cacheRows)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A02: the preview read immediately after the failed complete reuse still hits.
    @Test fun previewReadImmediatelyAfterACompletenessMissReturnsTheOriginalOrder() {
        val root = Files.createTempDirectory("cache-allfiles-read")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            val media = catalog(300, root.toString())
            assertFalse(offer(req, media))
            PhotoProfileMediaCache.reuse(req, context)

            val ordered = PhotoProfileMediaCache.readOrdered(req)
            assertNotNull("the ordered cache must survive a completeness miss", ordered)
            assertEquals(media.map { it.path }, ordered!!.map { it.path })

            val copy = PhotoProfileMediaCache.read(req)!!
            assertEquals(media.map { it.path }, copy.map { it.path })
            copy.first().name = "Mutated"
            assertEquals("read() must hand out copies", "photo-0.jpg",
                PhotoProfileMediaCache.read(req)!!.first().name)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A03: a scoped public folder with a real checkpoint keeps complete reuse.
    @Test @Config(sdk = [31], manifest = Config.NONE, application = Application::class,
        shadows = [ScopedEnvironment::class, ScopedMediaStore::class])
    fun checkpointBackedCatalogStillReusesCompletely() {
        val root = Files.createTempDirectory("cache-scoped")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            val checkpoint = PhotoProfileCatalogCheckpoint.capture(context, req.scope)
            assertNotNull("a public scoped folder must expose a checkpoint", checkpoint)
            assertTrue(offer(req, catalog(64, root.toString()), checkpoint = checkpoint))

            val (verdict, reusable) = PhotoProfileMediaCache.reuse(req, context)
            assertTrue(verdict.previewEligible)
            assertTrue(verdict.completeReusable)
            assertEquals(PhotoCacheMissReason.NONE, verdict.missReason)
            assertEquals(64, reusable!!.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A04: a changed checkpoint downgrades completeness but keeps the preview.
    @Test @Config(sdk = [31], manifest = Config.NONE, application = Application::class,
        shadows = [ScopedEnvironment::class, ScopedMediaStore::class])
    fun changedCheckpointKeepsThePreviewButForbidsCompleteReuse() {
        val root = Files.createTempDirectory("cache-checkpoint-change")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            val checkpoint = PhotoProfileCatalogCheckpoint.capture(context, req.scope)!!
            assertTrue(offer(req, catalog(48, root.toString()), checkpoint = checkpoint))

            root.toFile().setLastModified(root.toFile().lastModified() + 60_000L)

            val (verdict, reusable) = PhotoProfileMediaCache.reuse(req, context)
            assertTrue("a stale completeness proof is not an authorization failure", verdict.previewEligible)
            assertFalse(verdict.completeReusable)
            assertEquals(PhotoCacheMissReason.CHECKPOINT_CHANGED, verdict.missReason)
            assertNull(reusable)
            assertNotNull("the preview source must still be readable", PhotoProfileMediaCache.readOrdered(req))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A05: a different scope is rejected.
    @Test fun scopeChangeRejectsTheCachedCatalog() {
        val root = Files.createTempDirectory("cache-scope")
        val other = Files.createTempDirectory("cache-scope-other")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse(offer(req, catalog(32, root.toString())))

            val old = PhotoProfileRepository.of(context).state().profiles.first { it.id == id }
            PhotoProfileRepository.of(context).updateExisting(
                old.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, other.toString())))
            )
            val changed = request(id)!!
            assertNull(PhotoProfileMediaCache.readOrdered(changed))
            val (verdict, reusable) = PhotoProfileMediaCache.reuse(changed, context)
            assertEquals(PhotoCacheMissReason.SCOPE_MISMATCH, verdict.missReason)
            assertFalse(verdict.previewEligible)
            assertNull(reusable)
        } finally {
            root.toFile().deleteRecursively()
            other.toFile().deleteRecursively()
        }
    }

    // A05/A06: a sorting change belongs to the scope; a revision change expires the entry.
    @Test fun sortingAndRevisionChangesAreRejectedAndTheRevisionEntryIsDropped() {
        val root = Files.createTempDirectory("cache-revision")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse(offer(req, catalog(24, root.toString())))

            val staleRevision = PhotoProfileLoadContext(req.scope, req.revision - 1, req.favorites)
            val (revisionVerdict, _) = PhotoProfileMediaCache.reuse(staleRevision, context)
            assertEquals(PhotoCacheMissReason.CONTENT_REVISION_CHANGED, revisionVerdict.missReason)
            assertTrue("a proven-stale entry may be dropped", revisionVerdict.eviction)
            assertNull("the evicted entry is gone for later requests too",
                PhotoProfileMediaCache.readOrdered(req))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A06: favorite relationship changes invalidate the entry.
    @Test fun favoriteRelationshipChangeIsRejected() {
        val root = Files.createTempDirectory("cache-favorites")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse(offer(req, catalog(16, root.toString())))

            val changed = PhotoProfileLoadContext(req.scope, req.revision, setOf("$root/favorite.jpg"))
            val (verdict, _) = PhotoProfileMediaCache.reuse(changed, context)
            assertEquals(PhotoCacheMissReason.FAVORITES_CHANGED, verdict.missReason)
            assertFalse(verdict.previewEligible)
            assertNull(PhotoProfileMediaCache.readOrdered(changed))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A07: age expiry evicts normally.
    @Test fun expiredCatalogIsEvicted() {
        val root = Files.createTempDirectory("cache-expiry")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse(offer(req, catalog(8, root.toString())))
            ShadowSystemClock.advanceBy(Duration.ofSeconds(61))

            val (verdict, _) = PhotoProfileMediaCache.reuse(req, context)
            assertEquals(PhotoCacheMissReason.EXPIRED, verdict.missReason)
            assertTrue(verdict.eviction)
            assertNull(PhotoProfileMediaCache.readOrdered(req))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A09: a superseded request must not delete the entry a newer load produced.
    @Test fun aSupersededRequestDoesNotEvictTheNewerEntry() {
        val root = Files.createTempDirectory("cache-superseded")
        try {
            val id = profile(root.toString())
            val first = request(id)!!
            assertFalse(offer(first, catalog(20, root.toString())))

            // A newer load replaced the entry with a different favorite relation.
            val newer = PhotoProfileLoadContext(first.scope, first.revision, setOf("$root/new.jpg"))
            assertFalse(offer(newer, catalog(21, root.toString())))

            val (oldVerdict, _) = PhotoProfileMediaCache.reuse(first, context)
            assertEquals(PhotoCacheMissReason.FAVORITES_CHANGED, oldVerdict.missReason)
            assertFalse("an outdated request must not drop a newer entry", oldVerdict.eviction)

            val (newVerdict, _) = PhotoProfileMediaCache.reuse(newer, context)
            assertTrue(newVerdict.previewEligible)
            assertEquals(21, PhotoProfileMediaCache.readOrdered(newer)!!.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A09: concurrent readers and replacements never observe a torn entry.
    @Test fun concurrentReadReplaceAndInvalidateStayConsistent() {
        val root = Files.createTempDirectory("cache-concurrent")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse(offer(req, catalog(500, root.toString())))
            val pool = Executors.newFixedThreadPool(6)
            val start = CountDownLatch(1)
            val failures = java.util.Collections.synchronizedList(ArrayList<Throwable>())
            repeat(4) {
                pool.execute {
                    try {
                        start.await()
                        repeat(50) {
                            val ordered = PhotoProfileMediaCache.readOrdered(req) ?: return@repeat
                            assertTrue(ordered.size == 500)
                        }
                    } catch (error: Throwable) { failures.add(error) }
                }
            }
            pool.execute {
                try {
                    start.await()
                    repeat(20) { assertFalse(offer(req, catalog(500, root.toString()))) }
                } catch (error: Throwable) { failures.add(error) }
            }
            pool.execute {
                try {
                    start.await()
                    repeat(5) { PhotoProfileMediaCache.invalidate(id) }
                } catch (error: Throwable) { failures.add(error) }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
            assertTrue(failures.joinToString(), failures.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // A08/A10: protected results and partial scans are never stored as a complete catalog.
    @Test fun partialAndProtectedResultsAreNeverCached() {
        val root = Files.createTempDirectory("cache-partial")
        try {
            val id = profile(root.toString())
            val req = request(id)!!
            assertFalse("a PARTIAL scan must not become the complete cache",
                offer(req, catalog(10, root.toString()), stage = PhotoLoadStage.FILESYSTEM))
            assertNull(PhotoProfileMediaCache.readOrdered(req))

            PhotoProfileMediaCache.offer(
                context,
                PhotoLoadResult(req, PhotoLoadStage.COMPLETE, catalog(10, root.toString()),
                    PhotoAccessSummary(needsUnlock = true, publicOnly = false))
            )
            assertNull("a protected result must never be cached", PhotoProfileMediaCache.readOrdered(req))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    /** All-files access: the state in which the Phase 1 fast path silently disabled itself. */
    @Implements(Environment::class)
    class FullAccessEnvironment : ShadowEnvironment() {
        companion object {
            @JvmStatic @Implementation(minSdk = 30)
            fun isExternalStorageManager(): Boolean = true
        }
    }

    @Implements(Environment::class)
    class ScopedEnvironment : ShadowEnvironment() {
        companion object {
            @JvmStatic @Implementation(minSdk = 30)
            fun isExternalStorageManager(): Boolean = false
        }
    }

    @Implements(MediaStore::class)
    class ScopedMediaStore {
        companion object {
            @JvmStatic @Implementation(minSdk = 29)
            fun getExternalVolumeNames(context: Context): Set<String> = setOf("external")

            @JvmStatic @Implementation(minSdk = 29)
            fun getVersion(context: Context, volumeName: String): String = "cache-eligibility-version"

            @JvmStatic @Implementation(minSdk = 30)
            fun getGeneration(context: Context, volumeName: String): Long = 7L
        }
    }
}
