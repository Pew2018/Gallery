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
        order: List<String>? = null, directionForward: Boolean? = null): String? =
        ViewerDeletionNavigationPolicy.resolve(old, fresh, deleted, current, history, position, order, directionForward)
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

    @Test fun twoSwipeDirectionsRestoreActualPriorUserPage() {
        val fresh = abcd.filterNot { it.path == abcd[2].path }
        assertEquals("B -> C -> delete must return to B", abcd[1].path,
            resolve(abcd, fresh, abcd[2].path, history = abcd[1].path, directionForward = true))
        assertEquals("D -> C -> delete must return to D", abcd[3].path,
            resolve(abcd, fresh, abcd[2].path, history = abcd[3].path, directionForward = false))
    }

    @Test fun missingHistoryUsesTheLastRealSwipeDirection() {
        val fresh = abcd.filterNot { it.path == abcd[2].path }
        assertEquals(abcd[3].path, resolve(abcd, fresh, abcd[2].path, directionForward = false))
        assertEquals(abcd[1].path, resolve(abcd, fresh, abcd[2].path, directionForward = true))
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
            position = 0, order = abcd.map { it.path }))
        assertEquals(abcd[3].path, resolve(onlyClicked, fresh, abcd[2].path,
            position = 0, order = abcd.map { it.path }, directionForward = false))
    }

    @Test fun fiveThousandPhotoCatalogRestoresThePriorPathInBothDirections() {
        val all = (0 until 5000).map { medium("item_$it.jpg") }
        val deleted = all[3000]
        val onlyLoaded = all.subList(2998, 3003)
        val fresh = all.filterNot { it.path == deleted.path }
        assertEquals(all[2999].path, resolve(onlyLoaded, fresh, deleted.path,
            history = all[2999].path, directionForward = true))
        assertEquals(all[3001].path, resolve(onlyLoaded, fresh, deleted.path,
            history = all[3001].path, directionForward = false))
    }

    @Test fun noHistoryInFiveThousandPhotosNeverJumpsToTheFirstPhoto() {
        val all = (0 until 5000).map { medium("item_$it.jpg") }
        val clicked = all[3000]
        val fresh = all.filterNot { it.path == clicked.path }
        assertNull(resolve(listOf(clicked), fresh, clicked.path, position = 0))
    }

    @Test fun originalGridOrderCannotChooseAnUnrelatedDistantSurvivor() {
        val all = (0 until 5000).map { medium("item_$it.jpg") }
        val clicked = all[3000]
        val fresh = all.filterIndexed { index, _ -> index !in 2992..3008 }
        assertNull(resolve(listOf(clicked), fresh, clicked.path, position = 0,
            order = all.map { it.path }))
    }

    @Test fun recoveryAnchorCentersOnHistoryOrNearGridNeighborNotDeletedPath() {
        assertEquals(abcd[3].path, ViewerDeletionNavigationPolicy.recoveryAnchor(
            listOf(abcd[2]), abcd[2].path, abcd[3].path, 0, abcd.map { it.path }, false))
        assertEquals(abcd[1].path, ViewerDeletionNavigationPolicy.recoveryAnchor(
            listOf(abcd[2]), abcd[2].path, null, 0, abcd.map { it.path }, true))
        assertNull(ViewerDeletionNavigationPolicy.recoveryAnchor(
            listOf(abcd[2]), abcd[2].path, null, 0))
    }

    @Test fun syntheticPagerReindexCannotOverrideFrozenNavigation() {
        assertNull(ViewerDeletionNavigationPolicy.postDeleteUserPath(abcd[2].path, 10L, 10L, abcd[0].path))
        assertEquals(abcd[3].path,
            ViewerDeletionNavigationPolicy.postDeleteUserPath(abcd[2].path, 10L, 11L, abcd[3].path))
        assertNull(ViewerDeletionNavigationPolicy.postDeleteUserPath(abcd[2].path, 10L, 11L, abcd[2].path))
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
