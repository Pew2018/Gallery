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
}
