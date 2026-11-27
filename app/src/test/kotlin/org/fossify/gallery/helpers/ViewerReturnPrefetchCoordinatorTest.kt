package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Viewer return prefetch decisions (P1-A / D group) and the §11.2 bookkeeping
 * matrix.
 *
 * §11.2 asks for a comparison of return-preparation profiles at 0/50/100/300/700 ms
 * of resource latency. Host tests cannot reproduce Glide's scheduling or a device
 * decode, so what is measured here is the *bookkeeping* a profile determines:
 * requests issued, duplicate requests, stale starts, cancellations and the resulting
 * prepared set - all driven by an injected clock. These numbers are not device
 * timings and are never reported as such.
 */
class ViewerReturnPrefetchCoordinatorTest {
    private val stability = ViewerReturnPrefetchCoordinator.STABILITY_MS

    // ---- D group ----

    @Test
    fun d01TheEntryMediaIsPreparedThroughItsOwnPageSettle() {
        val c = ViewerReturnPrefetchCoordinator()
        assertNull("nothing armed before the Viewer reports a page", c.pendingPath)
        // ViewPager reports page 0 as soon as it settles.
        val change = c.onPageChanged("/photos/0.jpg", nowMs = 0)
        assertEquals(stability, change.armDelayMs)
        assertFalse(change.cancelInFlight)
        assertEquals("/photos/0.jpg", c.onStabilityElapsed(stability))
        c.onStart("/photos/0.jpg")
        c.onFinished("/photos/0.jpg", stored = true)
        assertEquals("/photos/0.jpg", c.readyPath)
    }

    @Test
    fun d02TheTargetFollowsTheCurrentMedia() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/0.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/0.jpg")
        val change = c.onPageChanged("/photos/11.jpg", nowMs = 200)
        assertTrue("the old media's request must be cancelled", change.cancelInFlight)
        assertEquals("and the new page arms", stability, change.armDelayMs)
        assertEquals("/photos/11.jpg", c.onStabilityElapsed(200 + stability))
    }

    @Test
    fun d03RapidPagingStartsOnlyTheSettledPage() {
        val c = ViewerReturnPrefetchCoordinator()
        // A fling across 12 pages 60 ms apart: no page stays long enough to settle.
        repeat(12) { index ->
            c.onPageChanged("/photos/$index.jpg", nowMs = index * 60L)
            assertNull("an unstable page must not start", c.onStabilityElapsed(index * 60L + 40))
        }
        // A late stability callback from an earlier page must not start anything:
        // the current page has only been selected for 40 ms.
        assertNull("a stale settle cannot shorten the current page's window", c.onStabilityElapsed(700))
        // The user stops on the last page; only now does it settle and start once.
        assertEquals("/photos/11.jpg", c.onStabilityElapsed(660 + stability))
        c.onStart("/photos/11.jpg")
        assertNull("a second settle for the same page is a duplicate", c.onStabilityElapsed(900 + stability))
    }

    @Test
    fun d04EquivalentDuplicateRequestsAreSuppressed() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        // Re-selecting the page that is already being prepared must not re-arm.
        assertNull("an in-flight page is not re-requested", c.onPageChanged("/photos/a.jpg", 300).armDelayMs)
        assertNull(c.onStabilityElapsed(300 + stability))
    }

    @Test
    fun d04bAPreparedPageIsNotDecodedAgain() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        c.onFinished("/photos/a.jpg", stored = true)
        // Paging away and back: the reserve still holds the pixels, so no new request.
        c.onPageChanged("/photos/b.jpg", 400)
        val back = c.onPageChanged("/photos/a.jpg", 500)
        assertNull("a prepared page needs no second decode", back.armDelayMs)
        assertNull(c.onStabilityElapsed(500 + stability))
    }

    @Test
    fun d11LeavingTheViewerDropsEverything() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        c.reset()
        assertNull(c.pendingPath)
        assertNull(c.runningPath)
        assertNull(c.readyPath)
    }

    @Test
    fun d13AFailedPreparationDoesNotBlockTheReturn() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        c.onFailed("/photos/a.jpg")
        assertNull("a failed request is not remembered as prepared", c.readyPath)
        // The return path never consults the coordinator: it falls back to the
        // holder's own request. A later visit may retry.
        c.onPageChanged("/photos/b.jpg", 400)
        c.onPageChanged("/photos/a.jpg", 600)
        assertEquals("a retry after failure is allowed", "/photos/a.jpg", c.onStabilityElapsed(600 + stability))
    }

    @Test
    fun d16ACancelledRequestCannotBecomeThePreparedMedia() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        // The user moved on; the caller clears the target and reports the cancellation.
        assertTrue(c.onPageChanged("/photos/b.jpg", 300).cancelInFlight)
        c.onCancelled("/photos/a.jpg")
        assertNull(c.readyPath)
        // A late completion for the cancelled media is rejected by the caller's key
        // guard; the coordinator has already forgotten it.
        c.onFinished("/photos/a.jpg", stored = false)
        assertNull(c.readyPath)
        assertEquals("/photos/b.jpg", c.pendingPath)
    }

    @Test
    fun d17AtMostOneRequestExistsAtATime() {
        val c = ViewerReturnPrefetchCoordinator()
        c.onPageChanged("/photos/a.jpg", nowMs = 0)
        c.onStabilityElapsed(stability)
        c.onStart("/photos/a.jpg")
        assertEquals("/photos/a.jpg", c.runningPath)
        c.onPageChanged("/photos/b.jpg", 300)
        assertNull("starting the next one clears the previous", c.runningPath)
        c.onStabilityElapsed(300 + stability)
        c.onStart("/photos/b.jpg")
        assertEquals("/photos/b.jpg", c.runningPath)
    }

    // ---- §11.2 bookkeeping matrix ----

    /**
     * What one profile asked of the pipeline: how many requests were issued, how many
     * were duplicates of work already running, how many were issued for a page the
     * user had left, and how many pages ended prepared.
     */
    private data class Metrics(
        var starts: Int = 0,
        var duplicates: Int = 0,
        var staleStarts: Int = 0,
        var cancellations: Int = 0,
        var prepared: Int = 0,
    )

    /**
     * Twelve page changes 60 ms apart with a final settle, then the started request
     * becomes ready after [latencyMs]. Time is simulated; only the bookkeeping is
     * measured.
     */
    private fun profile(latencyMs: Long): Metrics {
        val c = ViewerReturnPrefetchCoordinator()
        val metrics = Metrics()
        var now = 0L
        var readyAt = -1L
        var startedPath: String? = null

        fun drainIfReady() {
            if (readyAt >= 0 && now >= readyAt) {
                val path = startedPath
                readyAt = -1
                if (path != null && c.runningPath == path) {
                    c.onFinished(path, stored = true)
                    metrics.prepared++
                }
            }
        }

        repeat(12) { index ->
            now += 60
            val path = "/photos/$index.jpg"
            val change = c.onPageChanged(path, now)
            if (change.cancelInFlight) {
                metrics.cancellations++
                c.onCancelled(startedPath)
            }
            drainIfReady()
            // Only the last page stays long enough to settle.
            if (index == 11) {
                now += (change.armDelayMs ?: stability)
                val start = c.onStabilityElapsed(now)
                if (start != null) {
                    val duplicate = c.runningPath != null
                    if (duplicate) metrics.duplicates++ else metrics.starts++
                    if (start != path) metrics.staleStarts++
                    c.onStart(start)
                    startedPath = start
                    readyAt = now + latencyMs
                }
            }
        }
        now = maxOf(now, readyAt)
        drainIfReady()
        return metrics
    }

    @Test
    fun elevenTwoEveryLatencyProfileIssuesExactlyOneRequestForTheSettledPage() {
        listOf(0L, 50L, 100L, 300L, 700L).forEach { latency ->
            val m = profile(latency)
            assertEquals("latency=${latency}ms: one request", 1, m.starts)
            assertEquals("latency=${latency}ms: no duplicate", 0, m.duplicates)
            assertEquals("latency=${latency}ms: no start for a left page", 0, m.staleStarts)
            assertEquals("latency=${latency}ms: no cancellation before the settle", 0, m.cancellations)
            assertEquals("latency=${latency}ms: prepared once", 1, m.prepared)
        }
    }

    @Test
    fun elevenTwoAnUnsettledPageStartsNothingRegardlessOfLatency() {
        listOf(0L, 50L, 100L, 300L, 700L).forEach { latency ->
            val c = ViewerReturnPrefetchCoordinator()
            var starts = 0
            var now = 0L
            repeat(12) { index ->
                now += 60
                val change = c.onPageChanged("/photos/$index.jpg", now)
                // The armed callback arrives before this page's window elapses,
                // however fast the resource itself would have decoded.
                if (change.armDelayMs != null && c.onStabilityElapsed(now + 40) != null) starts++
            }
            assertEquals("latency=$latency ms: an unsettled page must not start", 0, starts)
        }
    }
}
