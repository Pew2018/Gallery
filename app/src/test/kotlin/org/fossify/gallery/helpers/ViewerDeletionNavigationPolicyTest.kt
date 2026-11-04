package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerDeletionNavigationPolicyTest {
    private fun medium(name: String) = Medium(null, name, "/gallery/$name", "/gallery", 1, 1, 1, 1, 0, false, 0, 0)
    private val abcd = listOf("A", "B", "C", "D").map { medium("$it.jpg") }

    private fun resolve(old: List<Medium>, fresh: List<Medium>, deleted: String,
        history: String? = null, current: String? = deleted, position: Int = old.indexOfFirst { it.path == deleted },
        order: List<String>? = null, complete: Boolean = false): String? =
        ViewerDeletionNavigationPolicy.resolve(old, fresh, deleted, current, history, position, order, complete)
            ?.let { fresh[it].path }

    @Test fun deletingCChoosesPreviousSortedPhoto() {
        assertEquals(abcd[1].path, resolve(abcd, abcd.filterNot { it.path == abcd[2].path }, abcd[2].path))
    }

    @Test fun deletingFirstPhotoChoosesNextAndDeletingLastChoosesPrevious() {
        assertEquals(abcd[1].path, resolve(abcd, abcd.drop(1), abcd[0].path))
        assertEquals(abcd[2].path, resolve(abcd, abcd.dropLast(1), abcd[3].path))
    }

    @Test fun currentPageChangedDuringPermissionPromptRemainsSelected() {
        val fresh = abcd.filterNot { it.path == abcd[2].path }
        assertEquals(abcd[3].path, resolve(abcd, fresh, abcd[2].path, current = abcd[3].path))
    }

    @Test fun lastVisitedPageWinsAndDeletedHistoryFallsBackToSortedNeighbor() {
        val fresh = abcd.filterNot { it.path == abcd[2].path }
        assertEquals(abcd[0].path, resolve(abcd, fresh, abcd[2].path, history = abcd[0].path))
        assertEquals(abcd[1].path, resolve(abcd, fresh, abcd[2].path, history = abcd[2].path))
    }

    @Test fun gridOrderFindsNeighborWhenProgressiveWindowContainsOnlyDeletedPhoto() {
        val onlyClicked = listOf(abcd[2])
        val fresh = listOf(abcd[0], abcd[1], abcd[3])
        assertEquals(abcd[1].path, resolve(onlyClicked, fresh, abcd[2].path,
            position = 0, order = abcd.map { it.path }, complete = true))
    }

    @Test fun partialEmptyWindowIsUnknownButCompleteEmptyCatalogCanFinish() {
        assertNull(resolve(listOf(abcd[0]), emptyList(), abcd[0].path, position = 0))
        assertFalse(ViewerDeletionNavigationPolicy.shouldFinish(false, emptyList()))
        assertTrue(ViewerDeletionNavigationPolicy.shouldFinish(true, emptyList()))
        assertFalse(ViewerDeletionNavigationPolicy.shouldFinish(true, emptyList(), needsUnlock = true))
    }

    @Test fun failedDeleteKeepsOriginalAndDoesNotCreateRecoveryTarget() {
        val original = abcd[2]
        val fresh = abcd.toList()
        assertEquals(original.path, resolve(abcd, fresh, deleted = "/missing.jpg", current = original.path))
    }
}
