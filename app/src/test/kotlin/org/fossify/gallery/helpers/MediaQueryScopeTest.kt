package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQueryScopeTest {
    private val camera = "/storage/emulated/0/DCIM/Camera"

    @Test
    fun scopedOrdinaryFolderUsesBoundedQuery() {
        assertTrue(MediaQueryScope.shouldUseFolderQuery(false, camera, true, false))
    }

    @Test
    fun specialViewsAndUnsupportedModesKeepLegacyPath() {
        assertFalse(MediaQueryScope.shouldUseFolderQuery(true, camera, true, false))
        assertFalse(MediaQueryScope.shouldUseFolderQuery(false, camera, false, false))
        assertFalse(MediaQueryScope.shouldUseFolderQuery(false, camera, true, true))
        assertFalse(MediaQueryScope.shouldUseFolderQuery(false, "", true, false))
        assertFalse(MediaQueryScope.shouldUseFolderQuery(false, "favorites", true, false))
        assertFalse(MediaQueryScope.shouldUseFolderQuery(false, PhotoProfileMediaLoader.PATH, true, false))
    }
}
