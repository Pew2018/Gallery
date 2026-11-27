package org.fossify.gallery.helpers

import android.app.Application
import android.os.Looper
import org.fossify.gallery.models.Medium
import org.fossify.gallery.activities.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RefreshReliabilityTest {
    @Test fun homeDirectoryScanDeadlineIsTwoAndAHalfMinutesWithoutChangingOtherWatchdogs() {
        assertEquals(150_000L, MainActivity.HOME_DIRECTORY_SCAN_TIMEOUT_MS)
        assertEquals(45_000L, RefreshWatchdog.TIMEOUT_MS)

        val watchdog = RefreshWatchdog()
        val expired = AtomicInteger()
        watchdog.arm(MainActivity.HOME_DIRECTORY_SCAN_TIMEOUT_MS) { expired.incrementAndGet() }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(149_999, TimeUnit.MILLISECONDS)
        assertEquals(0, expired.get())
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        assertEquals(1, expired.get())
    }

    @Test fun manualRefreshCancelsTheLongHomeDeadline() {
        val watchdog = RefreshWatchdog()
        val expired = AtomicInteger()
        watchdog.arm(MainActivity.HOME_DIRECTORY_SCAN_TIMEOUT_MS) { expired.incrementAndGet() }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(120, TimeUnit.SECONDS)
        watchdog.stop()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
        assertEquals(0, expired.get())
    }

    @Test fun watchdogExpiresExactlyOnceAndANewRequestGetsItsOwnDeadline() {
        val watchdog = RefreshWatchdog()
        val expired = AtomicInteger()
        watchdog.arm(100) { expired.incrementAndGet() }
        watchdog.arm(100) { fail("preview restarted the deadline") }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertEquals(1, expired.get())
        watchdog.stop()
        watchdog.arm(100) { expired.incrementAndGet() }
        watchdog.stop()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertEquals(1, expired.get())
    }
    @Test fun completedRefreshCannotTimeOutLater() {
        val watchdog = RefreshWatchdog()
        watchdog.arm(100) { fail("completed load timed out") }
        watchdog.stop()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
    }
    @Test fun twoBusyAlbumWorkersCannotBlockTheForegroundLane() {
        val entered = CountDownLatch(2); val release = CountDownLatch(1); val done = CountDownLatch(2)
        val fresh = CountDownLatch(1)
        try {
            repeat(2) { GalleryAlbumScanExecutor.execute { entered.countDown(); release.await(5, TimeUnit.SECONDS); done.countDown() } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            GalleryMediaScanExecutor.execute { fresh.countDown() }
            assertTrue("foreground folder refresh waited for album discovery", fresh.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }
    @Test fun pendingMetadataRetriesNeverOccupyFullAlbumWorkers() {
        val occupied = CountDownLatch(2)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val metadataDone = CountDownLatch(1)
        try {
            repeat(2) {
                GalleryAlbumScanExecutor.execute {
                    occupied.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    completed.countDown()
                }
            }
            assertTrue(occupied.await(2, TimeUnit.SECONDS))
            GalleryDirectoryMetadataExecutor.execute { metadataDone.countDown() }
            assertTrue("metadata retries were waiting behind full-album scans",
                metadataDone.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun aTimedOutScanOffersTheExistingRetryMessage() {
        assertEquals(org.fossify.gallery.R.string.folder_refresh_timeout,
            MediaLoadFailure.message(java.util.concurrent.TimeoutException("scan deadline")))
    }

    @Test fun canceledQueuedTaskIsPurgedBeforeAdmittingANewerTask() {
        val executor = BoundedScanExecutor("purge-test", 1, 1)
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val fresh = CountDownLatch(1)
        try {
            executor.execute { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val obsolete = FutureTask<Unit> { fail("obsolete queued scan executed") }
            executor.execute(obsolete)
            obsolete.cancel(true)
            executor.execute { fresh.countDown() }
            release.countDown()
            assertTrue(fresh.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdown() }
    }
    @Test fun rejectedBoundedScanHasAnExplicitFailedTerminalAndLaterRetryCanRun() {
        val executor = BoundedScanExecutor("rejection-test", workers = 1, queued = 1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val retried = CountDownLatch(1)
        val terminals = GalleryScanTerminalGate()
        terminals.begin(1L)
        try {
            executor.execute { started.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val cancelled = FutureTask<Unit> { fail("canceled queued scan executed") }
            executor.execute(cancelled)
            try {
                executor.execute { fail("a full bounded queue unexpectedly accepted work") }
                fail("expected bounded executor rejection")
            } catch (error: java.util.concurrent.RejectedExecutionException) {
                assertTrue(terminals.finish(1L, GalleryScanResult(GalleryScanState.FAILED, cause = error)))
            }
            assertEquals(GalleryScanState.FAILED, terminals.result(1L)?.state)
            cancelled.cancel(true)
            terminals.begin(2L)
            executor.execute { retried.countDown() }
            release.countDown()
            assertTrue(retried.await(2, TimeUnit.SECONDS))
            assertTrue(terminals.finish(2L, GalleryScanResult(GalleryScanState.COMPLETE)))
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    @Test fun aBlockedVideoCannotPreventEarlierDetailsOrBaseMediaFromBeingAvailable() {
        val executor = Executors.newSingleThreadExecutor()
        val blocked = CountDownLatch(1); val release = CountDownLatch(1); val first = CountDownLatch(1)
        val base = listOf(Medium().apply { path = "/album/first.mp4"; type = TYPE_VIDEOS },
            Medium().apply { path = "/album/slow.mp4"; type = TYPE_VIDEOS },
            Medium().apply { path = "/album/new.jpg"; type = TYPE_IMAGES })
        try {
            VideoDurationEnricher.enrich(base, { true }, { path ->
                if (path.endsWith("slow.mp4")) { blocked.countDown(); release.await(5, TimeUnit.SECONDS) }
                16
            }, { result ->
                assertEquals(base.map { it.path }, result.map { it.path })
                assertEquals(16, result.first().videoDuration)
                first.countDown()
            }, executor)
            assertTrue(first.await(2, TimeUnit.SECONDS))
            assertTrue(blocked.await(2, TimeUnit.SECONDS))
            assertEquals(0, base.first().videoDuration) // immutable base catalog
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun revokedDurationRequestCannotPublishAfterSlowMetadataReturns() {
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(1)
        val current = java.util.concurrent.atomic.AtomicBoolean(true)
        val published = AtomicInteger()
        try {
            VideoDurationEnricher.enrich(listOf(Medium().apply { path = "/album/slow.mp4"; type = TYPE_VIDEOS }), current::get,
                { entered.countDown(); release.await(5, TimeUnit.SECONDS); 16 }, { published.incrementAndGet() }, executor)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            current.set(false); release.countDown()
            executor.execute { done.countDown() }
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(0, published.get())
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun retainedRefreshLogIdentityDoesNotExposePaths() {
        val path = "/storage/emulated/0/private/new.jpg"
        val id = GalleryDiagnosticLogger.anonymousId(path)
        assertFalse(id.contains(path))
        assertFalse(id.contains("new.jpg"))
        assertEquals(id, GalleryDiagnosticLogger.anonymousId(path))
    }
}
