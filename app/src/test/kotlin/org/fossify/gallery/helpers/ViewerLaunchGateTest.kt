package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class ViewerLaunchGateTest {
    @Test fun duplicateAndDifferentTapsShareOneTicket() {
        val gate = ViewerLaunchGate()
        val first = requireNotNull(gate.begin(false))
        repeat(20) { assertNull(gate.begin(false)) }
        assertTrue(gate.consume(first))
        assertFalse(gate.consume(first))
        assertNull(gate.begin(true))
        assertNotNull(gate.begin(false))
    }

    @Test fun cancellationAndOutOfOrderCallbacksCannotReleaseNewTicket() {
        val gate = ViewerLaunchGate()
        val stale = requireNotNull(gate.begin(false))
        gate.cancel()
        val current = requireNotNull(gate.begin(false))
        assertFalse(gate.isCurrent(stale))
        assertFalse(gate.consume(stale))
        assertTrue(gate.isCurrent(current))
        assertTrue(gate.consume(current))
        assertFalse(gate.consume(stale))
    }
}
