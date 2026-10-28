package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class FavoriteFolderLoadCoordinatorTest {
    private val ordinary = FavoriteFolderLoadKey(false, false, false, emptySet())
    private val hidden = FavoriteFolderLoadKey(true, false, false, emptySet())

    @Test fun firstLoadIsLoadedOrEmptyOnlyAfterCompletion() {
        val coordinator = FavoriteFolderLoadCoordinator()
        assertEquals(FavoriteFolderLoadStatus.Idle, coordinator.status)
        val first = coordinator.begin(ordinary)!!
        assertEquals(FavoriteFolderLoadStatus.Loading, coordinator.status)
        assertNull(coordinator.begin(ordinary))
        assertTrue(coordinator.complete(first, 2, false))
        assertEquals(FavoriteFolderLoadStatus.Loaded, coordinator.status)
        val empty = coordinator.begin(ordinary)!!
        assertTrue(coordinator.complete(empty, 0, false))
        assertEquals(FavoriteFolderLoadStatus.Empty, coordinator.status)
    }

    @Test fun changedToggleSupersedesEarlierResult() {
        val coordinator = FavoriteFolderLoadCoordinator()
        val older = coordinator.begin(ordinary)!!
        val latest = coordinator.begin(hidden)!!
        assertFalse(coordinator.complete(older, 3, false))
        assertEquals(FavoriteFolderLoadStatus.Loading, coordinator.status)
        assertTrue(coordinator.complete(latest, 1, false))
        assertEquals(FavoriteFolderLoadStatus.Loaded, coordinator.status)
    }

    @Test fun authorizationPendingAndCancellationAreRecoverable() {
        val coordinator = FavoriteFolderLoadCoordinator()
        val pending = coordinator.begin(ordinary.copy(authorizationPending = true))!!
        assertTrue(coordinator.complete(pending, 0, true))
        assertEquals(FavoriteFolderLoadStatus.AwaitingAuthentication, coordinator.status)
        val authorized = coordinator.begin(hidden, force = true)!!
        assertTrue(coordinator.complete(authorized, 2, false))
        assertEquals(FavoriteFolderLoadStatus.Loaded, coordinator.status)
        coordinator.invalidate() // Activity stop or canceled authentication
        assertEquals(FavoriteFolderLoadStatus.Idle, coordinator.status)
        assertFalse(coordinator.complete(authorized, 2, false))
    }

    @Test fun databaseFailureCanRetryWithoutStickingLoading() {
        val coordinator = FavoriteFolderLoadCoordinator()
        val failed = coordinator.begin(ordinary)!!
        assertTrue(coordinator.fail(failed))
        assertEquals(FavoriteFolderLoadStatus.Error, coordinator.status)
        val retry = coordinator.begin(ordinary)!!
        assertTrue(coordinator.complete(retry, 1, false))
        assertEquals(FavoriteFolderLoadStatus.Loaded, coordinator.status)
    }

    @Test fun retiredRequestsNeverPublishOrOverwriteNewError() {
        val coordinator = FavoriteFolderLoadCoordinator()
        val old = coordinator.begin(ordinary)!!
        coordinator.invalidate()
        assertFalse(coordinator.fail(old))
        val current = coordinator.begin(hidden)!!
        assertFalse(coordinator.complete(old, 4, false))
        assertTrue(coordinator.fail(current))
        assertFalse(coordinator.complete(current, 4, false))
        assertEquals(FavoriteFolderLoadStatus.Error, coordinator.status)
    }

    @Test fun forcedReloadDoesNotDependOnOldCompletion() {
        val coordinator = FavoriteFolderLoadCoordinator()
        val old = coordinator.begin(ordinary)!!
        val next = coordinator.begin(ordinary, force = true)!!
        assertFalse(coordinator.complete(old, 1, false))
        assertTrue(coordinator.complete(next, 1, false))
    }
}
