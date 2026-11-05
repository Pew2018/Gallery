package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSortOperationGateTest {
    @Test fun t01RapidAtoBtoAWithReverseWorkerCompletionPublishesOnlyTheLastChoice() {
        val gate = MediaSortOperationGate()
        val firstA = gate.begin()
        val operationB = gate.begin()
        val lastA = gate.begin()
        val published = mutableListOf<String>()

        // Complete worker results in reverse submission order. Equal sort values
        // do not make the first A operation current again.
        listOf(lastA to "A-latest", operationB to "B-stale", firstA to "A-stale")
            .forEach { (operation, result) -> if (gate.isCurrent(operation)) published += result }
        assertTrue(gate.isCurrent(lastA))
        assertFalse(gate.isCurrent(operationB))
        assertFalse(gate.isCurrent(firstA))
        assertEquals(listOf("A-latest"), published)
    }

    @Test fun t06EachRandomRequestHasItsOwnGenerationEvenThoughTheModeIsUnchanged() {
        val gate = MediaSortOperationGate()
        val firstRandom = gate.begin()
        val secondRandom = gate.begin()

        assertFalse(gate.isCurrent(firstRandom))
        assertTrue(gate.isCurrent(secondRandom))
    }

    @Test fun t02t03t04t05RefreshViewerSearchOrAccessChangeInvalidatesPendingWork() {
        val gate = MediaSortOperationGate()
        val pendingSort = gate.begin()

        gate.invalidate() // refresh, pause into Viewer, search, or access loss
        assertFalse(gate.isCurrent(pendingSort))
        val afterRefresh = gate.begin()
        gate.invalidate() // query changed, Activity paused, or fallback scan began
        assertFalse(gate.isCurrent(afterRefresh))
    }
}
