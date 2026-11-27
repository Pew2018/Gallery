package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class ViewerSwipePublicationGateTest {
    @Test fun keepsOnlyNewestCatalogAcrossDraggingAndSettling() {
        val gate = ViewerSwipePublicationGate<String>()
        assertNull(gate.onScrollMoving(true))
        assertTrue(gate.deferIfMoving("partial"))
        assertTrue(gate.deferIfMoving("complete"))
        assertTrue(gate.hasPending)
        assertEquals("complete", gate.onScrollMoving(false))
        assertFalse(gate.hasPending)
        assertNull(gate.onScrollMoving(false))
        assertFalse(gate.deferIfMoving("idle"))
    }

    @Test fun cancelDropsPendingCatalogOnAuthorizationRevocation() {
        val gate = ViewerSwipePublicationGate<Int>()
        gate.onScrollMoving(true)
        assertTrue(gate.deferIfMoving(100))
        gate.clear()
        assertFalse(gate.isMoving)
        assertNull(gate.onScrollMoving(false))
    }

    @Test fun aNewSwipeStartsWithNoOldDeferredPublication() {
        val gate = ViewerSwipePublicationGate<Int>()
        gate.onScrollMoving(true)
        gate.deferIfMoving(1)
        assertEquals(1, gate.onScrollMoving(false))
        gate.onScrollMoving(true)
        assertNull(gate.onScrollMoving(false))
    }

    @Test fun gridDeferralCoalescesBeforeExpensiveSnapshotAndMenuPreparation() {
        val gate = ViewerSwipePublicationGate<() -> Unit>()
        gate.onScrollMoving(true)
        var preparations = 0
        var last = -1
        repeat(100) { stage ->
            assertTrue(gate.deferIfMoving { preparations++; last = stage })
        }
        assertEquals(0, preparations)
        gate.onScrollMoving(false)?.invoke()
        assertEquals(1, preparations)
        assertEquals(99, last)
        gate.onScrollMoving(true)
        gate.deferIfMoving { preparations++ }
        gate.clear() // scope/revision/access invalidation must discard work immediately
        gate.onScrollMoving(false)?.invoke()
        assertEquals(1, preparations)
    }

    @Test fun laterPreviewCannotReplaceQueuedFinalAndNextRequestStartsFresh() {
        val gate = ViewerSwipePublicationGate<Int>()
        gate.onScrollMoving(true)
        for (stage in listOf(0, 1, 3, 2)) {
            assertTrue(gate.deferIfMoving(stage) { stage >= it })
        }
        assertEquals(3, gate.onScrollMoving(false))

        gate.onScrollMoving(true)
        gate.deferIfMoving(3)
        gate.clear()
        gate.onScrollMoving(true)
        assertTrue(gate.deferIfMoving(1) { 1 >= it })
        assertEquals(1, gate.onScrollMoving(false))
    }

}
