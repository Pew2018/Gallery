package org.fossify.gallery.helpers

import com.bumptech.glide.Priority
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerScreenPrefetchCoordinatorTest {
    private val coordinator = ViewerScreenPrefetchCoordinator(stabilityMs = 150L)

    @Test fun settledPageStartsOnePrefetchAndASecondElapsedDoesNot() {
        val change = coordinator.onTargetChanged("/next.jpg", nowMs = 0L)
        assertEquals(150L, change.armDelayMs)
        assertFalse(change.cancelInFlight)
        assertNull(coordinator.onStabilityElapsed(149L))
        assertEquals("/next.jpg", coordinator.onStabilityElapsed(150L))
        coordinator.onStart("/next.jpg")
        assertEquals("/next.jpg", coordinator.runningPath)
        assertNull(coordinator.onStabilityElapsed(400L))
        coordinator.onReady("/next.jpg")
        assertNull(coordinator.runningPath)
        assertNull(coordinator.onTargetChanged("/next.jpg", 500L).armDelayMs)
    }

    @Test fun fastPagingDoesNotStackDecodes() {
        coordinator.onTargetChanged("/a.jpg", 0L)
        coordinator.onTargetChanged("/b.jpg", 40L)
        coordinator.onTargetChanged("/c.jpg", 80L)
        assertNull(coordinator.onStabilityElapsed(180L))
        assertEquals("/c.jpg", coordinator.onStabilityElapsed(230L))
        coordinator.onStart("/c.jpg")
        assertNull(coordinator.onStabilityElapsed(500L))
        assertEquals("/c.jpg", coordinator.runningPath)
    }

    @Test fun reverseSwipeCancelsThePreviousDirection() {
        val forward = coordinator.onTargetChanged("/forward.jpg", 0L)
        assertEquals(150L, forward.armDelayMs)
        coordinator.onStart("/forward.jpg")
        val reverse = coordinator.onTargetChanged("/back.jpg", 200L)
        assertTrue(reverse.cancelInFlight)
        assertNull(coordinator.runningPath)
        coordinator.onCancelled("/forward.jpg")
        assertEquals("/back.jpg", coordinator.onStabilityElapsed(350L))
    }

    @Test fun leavingTheViewerDropsTheTarget() {
        coordinator.onTargetChanged("/next.jpg", 0L)
        coordinator.onStart("/next.jpg")
        coordinator.reset()
        assertNull(coordinator.runningPath)
        assertNull(coordinator.targetPath)
        assertNull(coordinator.onStabilityElapsed(1_000L))
    }

    @Test fun visiblePageStaysImmediateAndRotationStaysUncached() {
        val fragment = File("src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt").readText()
        val glide = fragment.substringAfter("private fun loadWithGlide(").substringBefore("private fun tryLoadingWithPicasso")
        assertTrue(glide.contains("Priority.IMMEDIATE"))
        assertTrue(glide.contains("DiskCacheStrategy.RESOURCE"))
        assertTrue(glide.contains(".fitCenter()"))
        assertTrue(glide.contains("mCurrentRotationDegrees != 0"))
        assertTrue(glide.contains("DiskCacheStrategy.NONE"))
        assertFalse(glide.contains("SIZE_ORIGINAL"))
        // Glide declares IMMEDIATE before HIGH, and treats the earlier constant as more urgent.
        assertTrue(Priority.IMMEDIATE.ordinal < Priority.HIGH.ordinal)
        val activity = File("src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt").readText()
        val prefetch = activity.substringAfter("private fun beginScreenPrefetch(").substringBefore("private fun cancelScreenPrefetch(")
        assertTrue(prefetch.contains("Priority.HIGH"))
        assertTrue(prefetch.contains("DiskCacheStrategy.RESOURCE"))
        assertTrue(prefetch.contains(".fitCenter()"))
        assertTrue(prefetch.contains(".override(width, height)"))
        assertFalse(prefetch.contains("SIZE_ORIGINAL"))
        assertFalse(prefetch.contains("ViewerReturnPixelReserve"))
        assertFalse(prefetch.contains("ViewerPrefetchLease"))
        assertTrue(activity.contains("binding.viewPager.offscreenPageLimit = 2"))
    }
}
