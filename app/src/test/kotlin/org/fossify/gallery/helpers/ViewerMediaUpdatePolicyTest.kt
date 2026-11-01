package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerMediaUpdatePolicyTest {
    private fun medium(name: String, modified: Long = 10L, type: Int = 1) =
        Medium(null, name, "/gallery/$name", "/gallery", modified, 20L, 100L, type, 0, false, 0L, 0L)

    @Test fun prependingItemsMovesCurrentAnchorAndKeepsSurvivingIdentities() {
        val old = listOf(medium("a.jpg"), medium("b.jpg"), medium("c.jpg"))
        val fresh = listOf(medium("x.jpg")) + old

        val update = ViewerMediaUpdatePolicy.update(old, fresh, "/gallery/b.jpg", 1)

        assertEquals(2, update.currentPosition)
        assertEquals(old.map { ViewerMediaUpdatePolicy.identity(it) }.toSet(), update.survivingIdentities)
        assertTrue(update.invalidatedIdentities.isEmpty())
    }

    @Test fun deletingCurrentItemUsesBoundedNeighborFallback() {
        val old = listOf(medium("a.jpg"), medium("b.jpg"), medium("c.jpg"))
        val fresh = listOf(old[0], old[2])

        val update = ViewerMediaUpdatePolicy.update(old, fresh, "/gallery/b.jpg", 1)

        assertEquals(1, update.currentPosition)
        assertEquals(setOf(ViewerMediaUpdatePolicy.identity(old[1])), update.invalidatedIdentities)
    }

    @Test fun contentAndMediaTypeChangesInvalidateTheOldFragmentIdentity() {
        val old = medium("same.jpg", modified = 10L, type = 1)
        val changedContent = medium("same.jpg", modified = 11L, type = 1)
        val changedType = medium("same.jpg", modified = 10L, type = 2)

        assertFalse(ViewerMediaUpdatePolicy.identity(old) == ViewerMediaUpdatePolicy.identity(changedContent))
        assertFalse(ViewerMediaUpdatePolicy.identity(old) == ViewerMediaUpdatePolicy.identity(changedType))
    }

    @Test fun fullCatalogUpgradeKeepsSelectedItemPositionForLargeCollections() {
        val all = (0 until 10_000).map { medium("$it.jpg") }
        val selected = all[5_000]
        val local = listOf(all[4_999], selected, all[5_001])

        val update = ViewerMediaUpdatePolicy.update(local, all, selected.path, 1)

        assertEquals(5_000, update.currentPosition)
        assertEquals(3, update.survivingIdentities.size)
        assertEquals(0, update.invalidatedIdentities.size)
    }
}
