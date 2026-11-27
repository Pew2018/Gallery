package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cached catalog belongs to an exact PhotoProfileScope, sorting included.
 * Its order can therefore feed the first frame without an O(n) missing filter
 * and re-sort; only random sorting and scope misses fall back to the full path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class CachedMediaPreviewWindowTest {
    private fun item(index: Int, id: Long = index.toLong()) = Medium(
        id, "photo-$index.jpg", "/photos/photo-$index.jpg", "/photos",
        1_000L - index, 1_000L - index, 10L, TYPE_IMAGES, 0, false, 0, 0
    )

    private fun catalog(size: Int) = (0 until size).map { item(it) }

    @Test fun cachedScopeOrderIsReusedWithoutWalkingTheWholeCatalog() {
        val cached = catalog(4_993)

        val selected = CachedMediaPreviewOrder.select(cached, SORT_BY_DATE_TAKEN, anchorPath = null)

        assertEquals(CachedMediaPreviewOrder.LIMIT, selected!!.size)
        assertEquals(cached.take(CachedMediaPreviewOrder.LIMIT).map { it.path }, selected.map { it.path })
    }

    @Test fun boundedWindowChecksOnlyTheRowsItCanShow() {
        val cached = catalog(4_993)
        var checks = 0

        val selected = CachedMediaPreviewOrder.select(
            cached, SORT_BY_DATE_TAKEN, anchorPath = null,
            visible = { checks++; true }
        )!!

        assertEquals(CachedMediaPreviewOrder.LIMIT, selected.size)
        assertEquals("The whole 4,993-row catalog must not be stat()-checked for a 160-row window",
            CachedMediaPreviewOrder.LIMIT, checks)
    }

    @Test fun missingRowsAreSkippedInsideTheBoundedWindow() {
        val cached = catalog(500)
        val missing = cached[3].path

        val selected = CachedMediaPreviewOrder.select(
            cached, SORT_BY_DATE_TAKEN, anchorPath = null, visible = { it.path != missing }
        )!!

        assertEquals(CachedMediaPreviewOrder.LIMIT, selected.size)
        assertTrue(selected.none { it.path == missing })
        assertEquals(cached.take(161).filter { it.path != missing }.map { it.path }, selected.map { it.path })
    }

    @Test fun viewerAnchorSelectsAWindowAroundTheVisiblePosition() {
        val cached = catalog(1_000)
        val anchor = cached[200]

        val selected = CachedMediaPreviewOrder.select(
            cached, SORT_BY_DATE_TAKEN, anchorPath = anchor.path, visible = { true }
        )!!

        // A quarter-window of leading context keeps the anchor on screen.
        assertEquals(cached[160].path, selected.first().path)
        assertEquals(CachedMediaPreviewOrder.LIMIT, selected.size)
    }

    @Test fun anchorNearTheEndFillsBackwardsInOriginalOrder() {
        val cached = catalog(400)
        val anchor = cached.last()

        val selected = CachedMediaPreviewOrder.select(
            cached, SORT_BY_DATE_TAKEN, anchorPath = anchor.path, visible = { true }
        )!!

        assertEquals(CachedMediaPreviewOrder.LIMIT, selected.size)
        assertEquals(cached[240].path, selected.first().path)
        assertEquals(anchor.path, selected.last().path)
        assertTrue(selected.map { it.id!! }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun randomSortingNeverPretendsTheCachedOrderIsCurrent() {
        assertNull(CachedMediaPreviewOrder.select(catalog(500), SORT_BY_RANDOM, anchorPath = null))
    }

    @Test fun emptyCacheAndNonPositiveLimitUseTheNormalPath() {
        assertNull(CachedMediaPreviewOrder.select(emptyList(), SORT_BY_DATE_TAKEN, anchorPath = null))
        assertNull(CachedMediaPreviewOrder.select(catalog(10), SORT_BY_DATE_TAKEN, anchorPath = null, limit = 0))
    }
}
