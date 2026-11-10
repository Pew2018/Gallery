package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTransitionCompletionGateTest {
    @Test fun returnCancelsDelayedEntryCompletion() {
        val gate = PhotoTransitionCompletionGate()
        val entryCompletion = gate.begin()
        assertNotNull(entryCompletion)
        assertNull("duplicate transition-end callbacks are ignored", gate.begin())

        gate.cancel()

        assertFalse("the stale entry callback cannot clear the return surface", gate.complete(entryCompletion!!))
        val nextEntryCompletion = gate.begin()
        assertNotNull(nextEntryCompletion)
        assertTrue(gate.complete(nextEntryCompletion!!))
    }

    @Test fun onlyCurrentCompletionCanRunOnce() {
        val gate = PhotoTransitionCompletionGate()
        val first = gate.begin()!!
        gate.cancel()
        val second = gate.begin()!!

        assertFalse(gate.complete(first))
        assertTrue(gate.complete(second))
        assertFalse(gate.complete(second))
    }
}
