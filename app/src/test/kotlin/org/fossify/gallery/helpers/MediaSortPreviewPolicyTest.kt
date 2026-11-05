package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSortPreviewPolicyTest {
    private fun row(name: String): Medium = Medium(null, name, "/folder/$name", "/folder", 1, 1,
        1, 1, 0, false, 0, 1)

    private fun allowed(rows: List<ThumbnailItem> = listOf(row("a.jpg")), access: Boolean = true) =
        MediaSortPreviewPolicy.canPreview(true, true, true, true, access, true, rows)

    @Test fun partialPreviewRequiresCurrentAuthorizedOrdinaryFolderRows() {
        assertTrue(allowed())
        assertFalse(MediaSortPreviewPolicy.canPreview(true, true, false, true, true, true, listOf(row("a.jpg"))))
        assertFalse(MediaSortPreviewPolicy.canPreview(true, true, true, false, true, true, listOf(row("a.jpg"))))
        assertFalse(allowed(access = false))
        assertFalse(MediaSortPreviewPolicy.canPreview(false, true, true, true, true, true, listOf(row("a.jpg"))))
        assertFalse(MediaSortPreviewPolicy.canPreview(true, false, true, true, true, true, listOf(row("a.jpg"))))
    }

    @Test fun groupedRowsAreNotTreatedAsPlainMediaDuringPreviewSort() {
        assertFalse(allowed(listOf(row("a.jpg"), ThumbnailSection("June", "2026-06"))))
    }
}
