package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class PhotoSessionViewerCatalogStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var profile: PhotoProfile

    private fun <T> worker(action: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        return try { executor.submit<T> { action() }.get() } finally { executor.shutdownNow() }
    }

    private fun media(count: Int): List<Medium> = (0 until count).map { index ->
        Medium(null, "photo_" + index + ".jpg", "/Gallery/photo_" + index + ".jpg",
            "/Gallery", index.toLong(), index.toLong(), 2L, TYPE_IMAGES, 0, false, 0L, 0L)
    }

    private fun ready(items: List<Medium>, needsUnlock: Boolean = false): PhotoLoadResult {
        val request = worker { PhotoProfileLoadContext.capture(app, profile.id, null)!! }
        return PhotoLoadResult(request, PhotoLoadStage.COMPLETE, items,
            PhotoAccessSummary(needsUnlock, !needsUnlock))
    }

    @Before fun setup() {
        PhotoSessionViewerCatalogStore.invalidate()
        profile = PhotoProfile(UUID.randomUUID().toString(), "Photos",
            listOf(PhotoSource(PhotoSourceType.FOLDER, "/Gallery")))
        PhotoProfileRepository.of(app).save(profile)
    }

    @After fun teardown() {
        PhotoSessionViewerCatalogStore.invalidate()
        PhotoProfileRepository.of(app).delete(profile.id)
    }

    @Test fun tenThousandEntryCatalogIsCompleteAndReusableFromDifferentClickedPositions() {
        val original = media(10_000)
        val id = worker { PhotoSessionViewerCatalogStore.prepare(app, ready(original)) }
        assertNotNull(id)
        val first = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, original[2_499].path) }
        val second = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, original[9_998].path) }
        assertNotNull(first)
        assertSame("Repeat opens must reuse one process catalog", first!!.media, second!!.media)
        assertEquals(10_000, first.media.size)
        assertEquals(2_499, first.pathIndex[original[2_499].path])
        assertEquals(9_998, second.pathIndex[original[9_998].path])
        assertEquals(id, worker { PhotoSessionViewerCatalogStore.handoffId(app, profile.id, null) })
        original[2_499].name = "mutated-after-publication"
        assertEquals("photo_2499.jpg", first.media[2_499].name)
    }

    @Test fun incompleteOrLockedResultsNeverBecomeCompleteHandoffs() {
        val items = media(100)
        assertNull(worker { PhotoSessionViewerCatalogStore.prepare(app,
            ready(items).copy(stage = PhotoLoadStage.INDEXED)) })
        assertNull(worker { PhotoSessionViewerCatalogStore.prepare(app, ready(items, needsUnlock = true)) })
        assertNull(worker { PhotoSessionViewerCatalogStore.handoffId(app, profile.id, null) })
    }

    @Test fun wrongProfileSessionIdAndOldHandoffCannotAccessCatalog() {
        val items = media(5_000)
        val id = worker { PhotoSessionViewerCatalogStore.prepare(app, ready(items)) }!!
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, "external-fake-id", profile.id, null, items[0].path) })
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, id, "other-profile", null, items[0].path) })
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, "replayed-session", items[0].path) })
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, "/Gallery/unknown.jpg") })
        assertNotNull(worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, items[4_999].path) })
        PhotoProfileAccessSessionManager.revokeAll("catalog_test")
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, items[0].path) })
    }

    @Test fun indexedPreviewCanHandoffAndGrowIntoFullCatalogWithSameIdentity() {
        val all = media(5_000)
        val finalResult = ready(all)
        val preview = finalResult.copy(stage = PhotoLoadStage.INDEXED,
            media = all.subList(2_498, 2_503))
        worker { PhotoSessionViewerCatalogStore.offerPartial(app, preview) }
        val id = worker { PhotoSessionViewerCatalogStore.handoffId(app, profile.id, null) }
        assertNotNull(id)
        val staged = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, all[2_500].path) }
        assertNotNull(staged)
        assertFalse(staged!!.isComplete)
        assertEquals(5, staged.media.size)
        assertEquals(2, staged.pathIndex[all[2_500].path])
        worker { PhotoSessionViewerCatalogStore.prepare(app, finalResult) }
        val complete = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, all[2_500].path) }
        assertNotNull(complete)
        assertTrue(complete!!.isComplete)
        assertEquals(5_000, complete.media.size)
        assertEquals(2_500, complete.pathIndex[all[2_500].path])
        assertEquals(20, (2_500 until 2_520).count { complete.media[it].path == all[it].path })
        assertEquals(20, (2_480 until 2_500).count { complete.media[it].path == all[it].path })
    }

    @Test fun overTwentyThousandUsesSegmentWindowsAndCanRecenterWithoutRescanning() {
        val all = media(20_501)
        val result = ready(all)
        worker { PhotoSessionViewerCatalogStore.focus(profile.id, null, all[10_000].path) }
        val id = worker { PhotoSessionViewerCatalogStore.prepare(app, result) }!!
        val middle = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, all[10_000].path) }!!
        assertFalse(middle.isComplete)
        assertTrue(middle.media.size <= 241)
        assertEquals(PhotoViewerCatalogCoordinator.Boundary.HAS_MORE, middle.before)
        assertTrue(worker { PhotoSessionViewerCatalogStore.requestExpand(app, profile.id, null, middle.media.last().path, true) })
        val grown = worker { PhotoSessionViewerCatalogStore.latest(app, profile.id, null, all[10_000].path) }!!
        assertTrue(grown.media.size > middle.media.size)
        val end = worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, all.last().path) }!!
        assertEquals(PhotoViewerCatalogCoordinator.Boundary.END, end.after)
        assertTrue(end.media.size <= 241)
        PhotoProfileAccessSessionManager.revokeAll("segment_test")
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, id, profile.id, null, all.last().path) })
    }

    @Test fun viewerCanAtomicallyReuseAnActiveGridProducerAndStaleFinishCannotClearNewOne() {
        val first = ready(media(5)).context
        assertTrue(worker { PhotoSessionViewerCatalogStore.beginLoading(first, app) })
        val sameScope = first.copy()
        assertFalse(worker { PhotoSessionViewerCatalogStore.beginLoading(sameScope, app, reuseActive = true) })
        assertTrue(worker { PhotoSessionViewerCatalogStore.isLoading(app, profile.id, null) })
        worker { PhotoSessionViewerCatalogStore.finishLoading(sameScope) }
        assertTrue(worker { PhotoSessionViewerCatalogStore.isLoading(app, profile.id, null) })
        worker { PhotoSessionViewerCatalogStore.finishLoading(first) }
        assertFalse(worker { PhotoSessionViewerCatalogStore.isLoading(app, profile.id, null) })
    }

    @Test fun laterSmallerPreviewCannotDiscardExpandedOrFilesystemMedia() {
        val all = media(120)
        val result = ready(all)
        worker { PhotoSessionViewerCatalogStore.offerPartial(app, result.copy(stage = PhotoLoadStage.FILESYSTEM)) }
        worker { PhotoSessionViewerCatalogStore.offerPartial(app, result.copy(stage = PhotoLoadStage.CACHE_PREVIEW, media = all.take(5))) }
        val catalog = worker { PhotoSessionViewerCatalogStore.latest(app, profile.id, null, all.last().path) }!!
        assertEquals(120, catalog.media.size)
        assertFalse(catalog.isComplete)
        assertEquals(PhotoViewerCatalogCoordinator.Boundary.UNKNOWN, catalog.after)
    }

    @Test fun indexAndFilesystemStageMergeKeepsTheCanonicalSortedOrder() {
        val all = media(8)
        val request = ready(all)
        val expected = ArrayList(all)
        MediaFetcher(app).sortMedia(expected, request.context.scope.sorting)
        worker { PhotoSessionViewerCatalogStore.offerPartial(app,
            request.copy(stage = PhotoLoadStage.INDEXED,
                media = listOf(expected[0], expected[2], expected[4], expected[6]))) }
        worker { PhotoSessionViewerCatalogStore.offerPartial(app,
            request.copy(stage = PhotoLoadStage.FILESYSTEM,
                media = listOf(expected[1], expected[3], expected[5], expected[7]))) }
        val latest = worker { PhotoSessionViewerCatalogStore.latest(app, profile.id, null, expected[3].path) }!!
        assertEquals(expected.map { it.path }, latest.media.map { it.path })
    }

    @Test fun sourceChangesAndMediaRevisionInvalidateOldCompleteOrder() {
        val items = media(100)
        val first = worker { PhotoSessionViewerCatalogStore.prepare(app, ready(items)) }!!
        PhotoProfileRepository.of(app).updateExisting(profile.copy(
            sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Different"))))
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, first, profile.id, null, items[0].path) })
        val next = worker { PhotoSessionViewerCatalogStore.prepare(app, ready(items)) }!!
        PhotoContentRevision.changed()
        assertNull(worker { PhotoSessionViewerCatalogStore.take(app, next, profile.id, null, items[0].path) })
    }
}
