package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerSearchPolicyTest {
    @Test fun fullViewerSearchStaysWithinTheGridSearchScopeAndKeepsStableRankOrder() {
        val result = ViewerSearchPolicy.filter(listOf(
            medium("holiday-1.jpg"), medium("trip.jpg"), medium("holiday.jpg"), medium("other.jpg")
        ), "hol")
        assertEquals(listOf("holiday-1.jpg", "holiday.jpg"), result.map { it.name })
    }

    private fun medium(name: String) = Medium(
        null, name, "/gallery/$name", "/gallery", 1, 1, 1, 1, 0, false, 0, 1
    )
}
