package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Layout/database identity is not rendered thumbnail content. */
internal object MediaUpdatePolicy {
    fun sameContent(before: Medium, after: Medium): Boolean =
        before.copy(id = null, gridPosition = 0, mediaStoreId = 0) ==
            after.copy(id = null, gridPosition = 0, mediaStoreId = 0)

    fun durationOnly(before: Medium, after: Medium): Boolean =
        before.videoDuration != after.videoDuration &&
            sameContent(before.copy(videoDuration = after.videoDuration), after)
}
