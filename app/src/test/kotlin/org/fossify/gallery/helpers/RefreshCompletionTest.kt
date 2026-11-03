package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RefreshCompletionTest {
    @Test fun emptyCompleteSourceAndPartialSourcesAreNotConfused() {
        assertEquals(GalleryScanState.COMPLETE, GalleryScanResult.sources(0, emptyMap()).state)
        val errors = mapOf("/missing" to IOException("unmounted"))
        assertEquals(GalleryScanState.PARTIAL, GalleryScanResult.sources(97, errors).state)
        assertEquals(GalleryScanState.FAILED, GalleryScanResult.sources(0, errors).state)
        assertEquals(GalleryScanState.PARTIAL, GalleryScanResult.sources(1, emptyMap(), false).state)
    }
    @Test fun timeoutAndCancellationWinAgainstLateWorkersExactlyOnce() {
        val gate = GalleryScanTerminalGate()
        gate.begin(1)
        assertTrue(gate.finish(1, GalleryScanResult(GalleryScanState.TIMED_OUT)))
        assertFalse(gate.finish(1, GalleryScanResult(GalleryScanState.COMPLETE)))
        gate.begin(2)
        assertFalse(gate.finish(1, GalleryScanResult(GalleryScanState.FAILED)))
        assertTrue(gate.finish(2, GalleryScanResult(GalleryScanState.CANCELLED)))
        assertFalse(gate.finish(2, GalleryScanResult(GalleryScanState.COMPLETE)))
        gate.begin(3)
        assertTrue(gate.finish(3, GalleryScanResult(GalleryScanState.COMPLETE)))
    }
    @Test fun sixtyQueuedPathsAreFairEvenWhenTheFirstBatchKeepsFailing() {
        var now = 0L
        val queue = MetadataRetryQueue({ now }, 100)
        repeat(60) { queue.add("/album$it") }
        val batches = queue.ready().chunked(24)
        assertEquals(listOf(24, 24, 12), batches.map { it.size })
        batches.flatten().forEachIndexed { i, path -> if (i < 24) queue.failed(path) else queue.remove(path) }
        assertEquals(24, queue.snapshot().size)
        assertTrue(queue.ready().isEmpty())
        queue.add("/later")
        assertEquals(listOf("/later"), queue.ready())
        now = 101
        assertEquals(25, queue.ready().size)
    }
}
