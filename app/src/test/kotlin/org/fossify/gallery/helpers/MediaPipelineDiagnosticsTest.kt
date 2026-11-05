package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class MediaPipelineDiagnosticsTest {
    private data class CapturedEvent(
        val level: DiagnosticLevel, val module: String, val name: String,
        val request: Any?, val fields: Map<String, Any?>
    )

    private fun trace(clock: () -> Long = { 100L }) = MediaPipelineDiagnostics(
        "ml-test", "FOLDER", "INITIAL", "UNKNOWN", "scope-test", 17L, 10L, clock
    )

    @Test fun requestCountersAccumulateWithoutCrossRequestState() {
        val first = trace()
        val second = MediaPipelineDiagnostics(
            "ml-second", "FOLDER", "REFRESH", "UNKNOWN", "scope-second", 18L, 10L, clock = { 100L }
        )
        first.add("sortCount")
        first.add("sortMs", 11L)
        second.add("sortMs", 3L)
        assertEquals(1L, first.snapshot()["sortCount"])
        assertEquals(11L, first.snapshot()["sortMs"])
        assertEquals(3L, second.snapshot()["sortMs"])
    }

    @Test fun concurrentCountersAreNotLost() {
        val trace = trace()
        val workers = 8
        val iterations = 2_000
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)
        repeat(workers) {
            executor.execute {
                start.await()
                repeat(iterations) { trace.add("mediaStoreQueryCount") }
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        executor.shutdownNow()
        assertEquals((workers * iterations).toLong(), trace.snapshot()["mediaStoreQueryCount"])
    }

    @Test fun durationsClampClockRollbackAndAccumulateByStage() {
        var now = 90L
        val trace = trace { now }
        trace.recordDuration("diffMs", 100L)
        now = 120L
        trace.recordDuration("diffMs", 100L)
        assertEquals(20L, trace.snapshot()["diffMs"])
        assertEquals(2L, trace.snapshot()["diffCount"])
    }

    @Test fun anonymizedScopeDoesNotExposeRawInputAndIsSessionScoped() {
        val raw = "/storage/emulated/0/DCIM/private/IMG_1234.jpg"
        val firstSessionHash = GalleryDiagnosticLogger.anonymousId(raw)
        val secondSessionHash = GalleryDiagnosticLogger.anonymousId(raw)
        assertNotEquals(raw, firstSessionHash)
        assertFalse(firstSessionHash.contains("IMG_1234"))
        assertNotEquals(firstSessionHash, secondSessionHash)
        assertTrue(firstSessionHash.length <= 16)
    }

    @Test fun releaseOnlyPersistsFilteredStageWhenDiagnosticsAreEnabled() {
        assertFalse(GalleryPerfTrace.shouldMeasure("media_async_scan", debug = false, diagnosticsEnabled = false))
        assertTrue(GalleryPerfTrace.shouldMeasure("media_async_scan", debug = true, diagnosticsEnabled = false))
        assertTrue(GalleryPerfTrace.shouldMeasure("media_async_scan", debug = false, diagnosticsEnabled = true))
        assertTrue(GalleryPerfTrace.shouldPersist("media_async_scan", diagnosticsEnabled = true))
        assertFalse(GalleryPerfTrace.shouldPersist("media_async_scan", diagnosticsEnabled = false))
    }

    @Test fun summariesStayWithinLoggerFieldLimitAndShareRequestToken() {
        val events = mutableListOf<CapturedEvent>()
        val trace = MediaPipelineDiagnostics(
            "ml-summary", "FOLDER", "REFRESH", "SCOPED_STORAGE", "scope-hash", 19L, 10L,
            { 30L },
            { level, module, name, request, fields -> events += CapturedEvent(level, module, name, request, fields) }
        )
        trace.add("sortCount", 2)
        trace.add("sortMs", 18)
        trace.finish("PARTIAL", -2, 10, 3, 8, 7)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.request == "ml-summary" })
        assertTrue(events.all { it.fields.size <= 16 })
        val scan = events.first { it.name == "media_scan_summary" }
        assertEquals(0L, scan.fields["queueWaitMs"])
        assertEquals(18L, events.first { it.name == "media_sort_summary" }.fields["sortMs"])
        assertEquals(-1L, events.first { it.name == "media_thumbnail_summary" }.fields["thumbnailBindCount"])
        assertEquals("PARTIAL", events.first { it.name == "media_load_terminal" }.fields["terminalState"])
    }

    @Test fun duplicateTerminalIsRecordedWithoutChangingFirstTerminal() {
        val events = mutableListOf<CapturedEvent>()
        val trace = MediaPipelineDiagnostics(
            "ml-terminal", "FOLDER", "INITIAL", "UNKNOWN", "scope", 20L, 0L,
            { 5L },
            { level, module, name, request, fields -> events += CapturedEvent(level, module, name, request, fields) }
        )
        trace.finish("CANCELLED", 1, 2, 0, -1, 0)
        trace.finish("COMPLETE", 1, 2, 0, 10, 10)
        assertEquals(1, events.count { it.name == "media_load_terminal" })
        assertEquals("CANCELLED", events.first { it.name == "media_load_terminal" }.fields["terminalState"])
        assertEquals(1, events.count { it.name == "media_load_duplicate_terminal" })
    }

    @Test fun disabledDiagnosticsFactoryReturnsNoRequestCollector() {
        assertEquals(null, MediaPipelineDiagnostics.create("/private/path", "FOLDER", "INITIAL", 21L))
    }
}
