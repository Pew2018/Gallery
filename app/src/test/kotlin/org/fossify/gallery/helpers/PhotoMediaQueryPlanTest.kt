package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoMediaQueryPlanTest {
    @Test fun scopedSelectionUsesOnlySelectedFolders() {
        val folders = linkedSetOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures")
        val (sql, args) = PhotoMediaQueryPlan.selection(folders, "_data")
        assertEquals("_data LIKE ? OR _data LIKE ?", sql)
        assertArrayEquals(arrayOf(
            "/storage/emulated/0/DCIM/Camera/%", "/storage/emulated/0/Pictures/%"
        ), args)
    }

    @Test fun directChildrenOnlyAndCaseSensitive() {
        val folders = setOf("/storage/emulated/0/DCIM/Camera")
        assertTrue(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/Camera/a.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/Camera/Child/b.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/DCIM/camera/a.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/Pictures/a.jpg", folders))
    }

    @Test fun largeSelectionFallsBackToSingleCursorAndExactFilter() {
        val folders = (0..81).map { "/storage/emulated/0/Pictures/$it" }.toSet()
        val (sql, args) = PhotoMediaQueryPlan.selection(folders, "_data")
        assertNull(sql)
        assertNull(args)
        assertTrue(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/Pictures/4/a.jpg", folders))
        assertFalse(PhotoMediaQueryPlan.includesDirectFile("/storage/emulated/0/Pictures/4/Sub/a.jpg", folders))
    }
}
