package org.fossify.gallery.helpers

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteSessionLifecycleTest {
    private val lifecycle = DeleteSessionLifecycle()

    @Before fun setUp() = DeleteSessionState.reset()
    @After fun tearDown() = DeleteSessionState.reset()

    private fun remember(skipRecycleBin: Boolean = true) {
        DeleteSessionState.skipConfirmation = true
        DeleteSessionState.skipRecycleBin = skipRecycleBin
    }

    @Test fun switchingRootTabsAndReturningToProfileKeepsBothChoices() {
        val profile = Any()
        val albums = Any()
        val favorites = Any()
        val returningProfile = Any()
        lifecycle.onCreated(profile)
        remember()
        // GalleryBottomBar starts the replacement before finishing the source.
        lifecycle.onCreated(albums)
        lifecycle.onDestroyed(profile, false)
        lifecycle.onCreated(favorites)
        lifecycle.onDestroyed(albums, false)
        lifecycle.onCreated(returningProfile)
        lifecycle.onDestroyed(favorites, false)
        assertTrue(DeleteSessionState.skipConfirmation)
        assertTrue(DeleteSessionState.skipRecycleBin)
    }

    @Test fun openingAndClosingViewerKeepsMoveToBinChoice() {
        val grid = Any()
        val viewer = Any()
        lifecycle.onCreated(grid)
        remember(skipRecycleBin = false)
        lifecycle.onCreated(viewer)
        lifecycle.onDestroyed(viewer, false)
        assertTrue(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
    }

    @Test fun lastActivityConfigurationRecreationKeepsChoices() {
        val activity = Any()
        lifecycle.onCreated(activity)
        remember()
        lifecycle.onDestroyed(activity, true)
        lifecycle.onCreated(Any())
        assertTrue(DeleteSessionState.skipConfirmation)
        assertTrue(DeleteSessionState.skipRecycleBin)
    }

    @Test fun replacementCreatedBeforeConfigurationDestroyKeepsChoices() {
        val old = Any()
        lifecycle.onCreated(old)
        remember()
        lifecycle.onCreated(Any())
        lifecycle.onDestroyed(old, true)
        assertTrue(DeleteSessionState.skipConfirmation)
        assertTrue(DeleteSessionState.skipRecycleBin)
    }

    @Test fun finishingAllPagesResetsEvenWhenProcessRemainsAlive() {
        val grid = Any()
        val viewer = Any()
        lifecycle.onCreated(grid)
        lifecycle.onCreated(viewer)
        remember()
        lifecycle.onDestroyed(grid, false)
        assertTrue(DeleteSessionState.skipConfirmation)
        lifecycle.onDestroyed(viewer, false)
        assertFalse(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
        lifecycle.onCreated(Any())
        assertFalse(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
    }

    @Test fun firstLaunchCannotInheritOldInMemoryChoices() {
        remember()
        lifecycle.onCreated(Any())
        assertFalse(DeleteSessionState.skipConfirmation)
        assertFalse(DeleteSessionState.skipRecycleBin)
    }

    @Test fun unrelatedDestroyCannotEndActiveSession() {
        lifecycle.onCreated(Any())
        remember()
        lifecycle.onDestroyed(Any(), false)
        assertTrue(DeleteSessionState.skipConfirmation)
        assertTrue(DeleteSessionState.skipRecycleBin)
    }
}
