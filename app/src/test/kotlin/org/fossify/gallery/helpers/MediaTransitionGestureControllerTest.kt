package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransitionGestureControllerTest {
    private fun controller() = MediaTransitionGestureController(
        touchSlop = 12f,
        dismissDistance = 600f,
    )

    @Test
    fun verticalDragTracksReversalAndDismissesByDistance() {
        val controller = controller()
        controller.down(pointerId = 7, x = 100f, y = 100f, eligible = true)

        val first = controller.move(pointerId = 7, x = 104f, y = 220f)
        assertNotNull(first)
        assertTrue(first!!.began)
        assertEquals(0.2f, first.progress, 0.001f)

        val reversed = controller.move(pointerId = 7, x = 102f, y = 160f)
        assertNotNull(reversed)
        assertFalse(reversed!!.began)
        assertEquals(0.1f, reversed.progress, 0.001f)

        val enough = controller.move(pointerId = 7, x = 102f, y = 340f)
        assertNotNull(enough)
        assertEquals(MediaTransitionGestureController.Release.DISMISS, controller.release(7, 0f))
    }

    @Test
    fun horizontalSwipeNeverClaimsPhotoDismissGesture() {
        val controller = controller()
        controller.down(pointerId = 1, x = 100f, y = 100f, eligible = true)

        assertNull(controller.move(pointerId = 1, x = 180f, y = 125f))
        assertFalse(controller.isDragging())
        assertEquals(MediaTransitionGestureController.Release.IGNORE, controller.release(1, 0f))
    }

    @Test
    fun shortSlowDragRestoresButFastDownwardFlingDismisses() {
        val controller = controller()
        controller.down(pointerId = 2, x = 0f, y = 0f, eligible = true)
        assertNotNull(controller.move(pointerId = 2, x = 0f, y = 100f))
        assertEquals(MediaTransitionGestureController.Release.RESTORE, controller.release(2, 0f))

        controller.down(pointerId = 2, x = 0f, y = 0f, eligible = true)
        assertNotNull(controller.move(pointerId = 2, x = 0f, y = 30f))
        assertEquals(MediaTransitionGestureController.Release.DISMISS, controller.release(2, 1_300f))
    }

    @Test
    fun ineligibleAndMultiPointerGesturesDoNotDismiss() {
        val controller = controller()
        controller.down(pointerId = 3, x = 0f, y = 0f, eligible = false)
        assertNull(controller.move(pointerId = 3, x = 0f, y = 300f))
        assertEquals(MediaTransitionGestureController.Release.IGNORE, controller.release(3, 2_000f))

        controller.down(pointerId = 3, x = 0f, y = 0f, eligible = true)
        assertNotNull(controller.move(pointerId = 3, x = 0f, y = 80f))
        assertEquals(MediaTransitionGestureController.Release.RESTORE, controller.pointerChanged())
        assertEquals(MediaTransitionGestureController.Release.IGNORE, controller.release(3, 2_000f))
    }

    @Test
    fun cancelledDragRestoresFromItsCurrentFrame() {
        val controller = controller()
        controller.down(pointerId = 4, x = 0f, y = 0f, eligible = true)
        assertNotNull(controller.move(pointerId = 4, x = 0f, y = 250f))

        assertEquals(MediaTransitionGestureController.Release.RESTORE, controller.cancel())
        assertEquals(MediaTransitionGestureController.Release.IGNORE, controller.cancel())
    }

    @Test
    fun aNewGestureCanTakeOverASettlingFrameAndReverseIt() {
        val controller = controller()
        controller.down(pointerId = 8, x = 100f, y = 300f, eligible = true, baseProgress = 0.6f)

        val reversed = controller.move(pointerId = 8, x = 98f, y = 90f)
        assertNotNull(reversed)
        assertEquals(0.25f, reversed!!.progress, 0.001f)
        assertEquals(MediaTransitionGestureController.Release.RESTORE, controller.release(8, 0f))
    }
}
