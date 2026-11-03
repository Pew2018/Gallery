package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoProfileSourceScanPolicyTest {
    @Test fun lockedRootIsSkippedBeforeIndexAndFileEnumeration() {
        val visited = mutableListOf<String>()
        val plan = PhotoProfileSourceScanPolicy.select(
            linkedSetOf("/photos/public", "/photos/.nomedia-selected", "/photos/locked"),
            favoritesSelected = false,
            folderPermitted = { path -> visited += path; path != "/photos/locked" },
            favoritesPermitted = { error("No virtual favorites source") }
        )
        assertEquals(setOf("/photos/public", "/photos/.nomedia-selected"), plan.permittedFolders)
        assertEquals(setOf("/photos/locked"), plan.blockedFolders)
        assertTrue(plan.hasBlockedSources)
        assertEquals(3, visited.size)
    }

    @Test fun sourceReadFailureDeniesOnlyThatSource() {
        val plan = PhotoProfileSourceScanPolicy.select(
            listOf("/public", "/broken"), false,
            folderPermitted = { if (it == "/broken") throw SecurityException("unreadable") else true },
            favoritesPermitted = { false }
        )
        assertEquals(setOf("/public"), plan.permittedFolders)
        assertEquals(setOf("/broken"), plan.blockedFolders)
    }

    @Test fun lockedVirtualFavoritesDoNotSuppressPublicFolders() {
        val plan = PhotoProfileSourceScanPolicy.select(
            listOf("/camera"), true,
            folderPermitted = { true },
            favoritesPermitted = { false }
        )
        assertEquals(setOf("/camera"), plan.permittedFolders)
        assertFalse(plan.favoritesPermitted)
        assertTrue(plan.hasBlockedSources)
    }

    @Test fun cancellationStopsPlanWithoutGrantingAdditionalSources() {
        assertThrows(PhotoLoadInvalidatedException::class.java) {
            PhotoProfileSourceScanPolicy.select(
                listOf("/camera", "/locked"), false,
                folderPermitted = { true },
                favoritesPermitted = { false },
                cancelled = { true }
            )
        }
    }
}
