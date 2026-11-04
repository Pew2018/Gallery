package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

/** Layout/database identity is not rendered thumbnail content. */
internal object MediaUpdatePolicy {
    /** Compare ordered rows by rendered content, ignoring persistence/layout-only fields. */
    fun sameDisplayedContent(before: List<ThumbnailItem>, after: List<ThumbnailItem>): Boolean =
        before.size == after.size && before.indices.all { index ->
            val oldItem = before[index]
            val newItem = after[index]
            when {
                oldItem is Medium && newItem is Medium ->
                    oldItem.path == newItem.path && sameContent(oldItem, newItem)
                oldItem is ThumbnailSection && newItem is ThumbnailSection -> oldItem == newItem
                else -> oldItem == newItem
            }
        }

    fun sameContent(before: Medium, after: Medium): Boolean =
        before.copy(id = null, gridPosition = 0, mediaStoreId = 0) ==
            after.copy(id = null, gridPosition = 0, mediaStoreId = 0)

    fun durationOnly(before: Medium, after: Medium): Boolean =
        before.videoDuration != after.videoDuration &&
            sameContent(before.copy(videoDuration = after.videoDuration), after)
}
