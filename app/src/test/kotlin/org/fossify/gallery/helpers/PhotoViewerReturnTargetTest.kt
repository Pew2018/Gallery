package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewerReturnTargetTest {
    @Test fun onlyTheCurrentBoundMediaWithItsLoadedRequestCanBeAMappedReturnTarget() {
        val request = listOf("/media/current.jpg", 42L)

        assertTrue(PhotoViewerReturnTarget.isLoadedFor("/media/current.jpg", "/media/current.jpg", request, request))
        assertFalse(PhotoViewerReturnTarget.isLoadedFor("/media/current.jpg", "/media/previous.jpg", request, request))
        assertFalse(PhotoViewerReturnTarget.isLoadedFor("/media/current.jpg", "/media/current.jpg", null, request))
        assertFalse(PhotoViewerReturnTarget.isLoadedFor("/media/current.jpg", "/media/current.jpg", request, listOf("/media/old.jpg")))
        assertFalse(PhotoViewerReturnTarget.isLoadedFor("", "", request, request))
    }

    @Test fun returnTargetRequiresMeaningfulVisibleIntersection() {
        assertFalse(PhotoViewerReturnTarget.hasUsableVisibleArea(300, 300, 300, 10))
        assertFalse(PhotoViewerReturnTarget.hasUsableVisibleArea(300, 300, 0, 300))
        assertFalse(PhotoViewerReturnTarget.hasUsableVisibleArea(0, 300, 300, 300))
        assertTrue(PhotoViewerReturnTarget.hasUsableVisibleArea(300, 300, 300, 100))
        assertTrue(PhotoViewerReturnTarget.hasUsableVisibleArea(300, 300, 180, 180))
    }
}
