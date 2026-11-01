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
}
