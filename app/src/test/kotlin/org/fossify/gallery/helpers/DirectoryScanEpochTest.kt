package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryScanEpochTest {
    @Test fun retirementAndNewRequestsInvalidateOldCallbacks() {
        val epoch = DirectoryScanEpoch()
        val first = epoch.begin()
        assertTrue(epoch.isCurrent(first))
        assertTrue(epoch.currentVersion() == first)
        val second = epoch.begin()
        assertFalse(epoch.isCurrent(first))
        assertTrue(epoch.isCurrent(second))
        epoch.retire()
        assertFalse(epoch.isCurrent(second))
        assertTrue(epoch.currentVersion() > second)
    }
    @Test fun latestEpochAndFirstTerminalOwnDelayedUiPublication() {
        val epoch = DirectoryScanEpoch()
        val gate = GalleryScanTerminalGate()
        val pending = ArrayList<() -> Unit>()
        val published = ArrayList<String>()
        fun begin() = epoch.begin().also { gate.begin(it) }
        fun enqueue(request: Long, value: String) {
            pending.add {
                if (epoch.isCurrent(request) &&
                    gate.finish(request, GalleryScanResult(GalleryScanState.COMPLETE))) {
                    published.add(value)
                }
            }
        }
        val old = begin()
        enqueue(old, "obsolete")
        val fresh = begin()
        enqueue(fresh, "fresh")
        pending[0].invoke() // the older worker arrives after the newer request started
        assertTrue(published.isEmpty())
        pending[1].invoke()
        assertEquals(listOf("fresh"), published)

        val timedOut = begin()
        assertTrue(gate.finish(timedOut, GalleryScanResult(GalleryScanState.TIMED_OUT)))
        enqueue(timedOut, "too-late")
        pending[2].invoke()
        assertEquals(listOf("fresh"), published)
        assertEquals(GalleryScanState.TIMED_OUT, gate.result(timedOut)?.state)
    }

}
