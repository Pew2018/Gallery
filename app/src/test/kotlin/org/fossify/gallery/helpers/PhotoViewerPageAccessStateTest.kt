package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoViewerPageAccessStateTest {
    @Test fun oneThousandForwardAndBackwardPagesNeverGrowBeyondBound() {
        var now = 1_000L
        val access = PhotoViewerPageAccessState(capacity = 16, maxAgeMs = 15_000L) { now }
        access.reset(7L)
        for (page in 0 until 1000) {
            access.approve((page..page + 4).map { "photo-$it" }, 7L)
            assertTrue(access.contains("photo-$page", 7L))
            assertTrue(access.size() <= 16)
            now += 2L
        }
        assertFalse(access.contains("photo-0", 7L))
        assertTrue(access.contains("photo-999", 7L))
        for (page in 999 downTo 0) {
            access.approve((page..page + 4).map { "photo-$it" }, 7L)
            assertTrue(access.size() <= 16)
            now += 2L
        }
        assertTrue(access.contains("photo-0", 7L))
    }

    @Test fun overlapReuseDirectionChangeAndOldGenerationAreSafe() {
        val access = PhotoViewerPageAccessState(capacity = 8) { 100L }
        access.reset(5)
        assertTrue(access.approve(listOf("a", "b", "c", "d"), 5))
        assertEquals(listOf("e"), access.missing(listOf("b", "c", "d", "e"), 5))
        assertTrue(access.approve(listOf("e"), 5))
        assertTrue(access.missing(listOf("d", "e"), 5).isEmpty())
        assertFalse(access.approve(listOf("forged"), 4))
        assertFalse(access.contains("forged", 5))
        access.reset(6)
        assertFalse(access.contains("e", 6))
        assertFalse(access.approve(listOf("late-old-task"), 5))
        assertTrue(access.missing(listOf("late-old-task"), 6).isNotEmpty())
    }

    @Test fun shortLivedApprovalExpiresAndClearsOnRevocation() {
        var now = 50L
        val access = PhotoViewerPageAccessState(capacity = 8, maxAgeMs = 100L) { now }
        access.reset(2)
        assertTrue(access.approve(listOf("locked"), 2))
        now = 151L
        assertFalse(access.contains("locked", 2))
        access.approve(listOf("locked"), 2)
        access.reset(-1)
        assertFalse(access.contains("locked", 2))
    }
}
