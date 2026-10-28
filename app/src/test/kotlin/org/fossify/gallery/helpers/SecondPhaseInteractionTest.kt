package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class SecondPhaseInteractionTest {
    @Test fun dedicatedSearchExitIsIdempotent() {
        val gate = SearchExitGate()
        assertFalse(gate.exiting)
        assertTrue(gate.beginExit())
        assertTrue(gate.exiting)
        assertFalse(gate.beginExit())
    }

    @Test fun commonsTitleCountsOnlySelectedMedia() {
        assertEquals(1, SelectionCountFormatter.selectedCount("1 / 334"))
        assertEquals(3, SelectionCountFormatter.selectedCount("3 / 334"))
        assertEquals(334, SelectionCountFormatter.selectedCount("334 / 334"))
        assertNull(SelectionCountFormatter.selectedCount("Not selecting"))
        assertNull(SelectionCountFormatter.selectedCount("99999999999999999999 / 334"))
    }

    @Test fun deletePreferencesResetAtSessionEnd() {
        DeleteSessionState.reset()
        assertFalse(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
        DeleteSessionState.skipConfirmation = true
        DeleteSessionState.skipRecycleBin = true
        assertTrue(DeleteSessionState.skipConfirmation)
        DeleteSessionState.reset()
        assertFalse(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
    }
}
