package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumHomeLayoutPolicyTest {
    @Test
    fun concatPositionsKeepSectionTitlesFullSpanAcrossSupportedColumnCounts() {
        listOf(1, 2, 3, 8, 11).forEach { columns ->
            val args = arrayOf(columns, 1, 1, 3, 1)
            fun span(position: Int, listMode: Boolean = false) = AlbumHomeSpanPolicy.spanSize(
                position, args[0], args[1], args[2], args[3], args[4], listMode
            )

            assertEquals("favorite ($columns columns)", columns, span(0))
            assertEquals("pinned header ($columns columns)", columns, span(1))
            assertEquals("first pinned tile ($columns columns)", 1, span(2))
            assertEquals("last pinned tile ($columns columns)", 1, span(4))
            assertEquals("other title ($columns columns)", columns, span(5))
            assertEquals("ordinary tile ($columns columns)", 1, span(6))
            assertEquals("list tile ($columns columns)", columns, span(2, listMode = true))
        }
    }

    @Test
    fun cardDensityKeepsTextAtLowDensityAndHidesCountsWhenCellsAreTiny() {
        val deviceWidthDp = 390f
        listOf(1, 2, 3, 8, 11).forEach { columns ->
            val spec = HomeCardLayoutPolicy.forWidthDp(deviceWidthDp / columns)
            assertTrue("album name has a compact readable size at $columns columns", spec.nameSize >= 9f)
            if (columns <= 8) assertTrue("count shown at $columns columns", spec.showCount)
            else assertFalse("count hidden at $columns columns", spec.showCount)
        }
    }

    @Test
    fun rapidExpandCollapseSequencesAlwaysSettleToAConsistentStripState() {
        var expanded = false
        repeat(20) {
            expanded = !expanded
            val settled = AlbumStripStatePolicy.settled(expanded, 164)
            if (expanded) {
                assertEquals(0, settled.height)
                assertEquals(0f, settled.alpha, 0f)
                assertFalse(settled.visible)
            } else {
                assertEquals(164, settled.height)
                assertEquals(1f, settled.alpha, 0f)
                assertTrue(settled.visible)
            }
        }
    }

    @Test
    fun nonPositiveCollapsedHeightCannotLeaveAnInvisibleNonZeroStrip() {
        val state = AlbumStripStatePolicy.settled(expanded = false, targetHeight = 0)
        assertTrue(state.visible)
        assertEquals(1, state.height)
        assertEquals(1f, state.alpha, 0f)
    }
}
