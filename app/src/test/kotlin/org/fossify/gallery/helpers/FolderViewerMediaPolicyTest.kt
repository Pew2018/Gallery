package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderViewerMediaPolicyTest {
    @Test fun partialRandomHandoffGainsMissingPagesWithoutReorderingSurvivors() {
        assertEquals(listOf("b", "a", "c"),
            FolderViewerMediaPolicy.reconcile(listOf("b", "a"), listOf("a", "b", "c"), true) { it })
    }

    @Test fun freshMembershipRemovesDeletedPagesAndDeduplicatesSources() {
        assertEquals(listOf("b", "c"),
            FolderViewerMediaPolicy.reconcile(listOf("deleted", "b"), listOf("b", "c", "b"), true) { it })
    }

    @Test fun ordinarySortingUsesFreshOrderRatherThanSelectedOnlySeed() {
        assertEquals(listOf("a", "b", "c"),
            FolderViewerMediaPolicy.reconcile(listOf("c"), listOf("a", "b", "c"), false) { it })
    }

    @Test fun fullRandomResultKeepsThePublishedWindowStableAfterTheUserSwipes() {
        val local = listOf("b", "c", "a", "d", "e")
        val beforeFullResult = FolderViewerMediaPolicy.reconcile(emptyList(), local, random = true) { it }
        assertEquals(3, ViewerPositionPolicy.resolve(
            beforeFullResult.map(::medium), "d", previousPosition = 0
        ))

        val fullRandomResult = listOf("x", "a", "c", "d", "b", "e", "y")
        val published = FolderViewerMediaPolicy.reconcile(beforeFullResult, fullRandomResult, random = true) { it }
        val position = ViewerPositionPolicy.resolve(published.map(::medium), "d", previousPosition = 1)

        assertEquals(listOf("b", "c", "a", "d", "e", "x", "y"), published)
        assertEquals("d", published[position])
    }
    private fun medium(path: String) = org.fossify.gallery.models.Medium(
        null, path, "/gallery/$path", "/gallery", 1, 1, 1, 1, 0, false, 0, 1
    )
}
