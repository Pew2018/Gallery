package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Guards a display-only sort of rows already published by the active folder request. */
internal object MediaSortPreviewPolicy {
    fun canPreview(
        physicalFolder: Boolean,
        ordinarySource: Boolean,
        scanInProgress: Boolean,
        snapshotValid: Boolean,
        requestCurrent: Boolean,
        accessCurrent: Boolean,
        adapterMatchesSource: Boolean,
        rows: List<ThumbnailItem>
    ): Boolean = physicalFolder && ordinarySource && scanInProgress && snapshotValid && requestCurrent &&
        accessCurrent && adapterMatchesSource && rows.isNotEmpty() && rows.all { it is Medium }
}
