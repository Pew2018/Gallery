package org.fossify.gallery.helpers

/** Search results belong to the displayed snapshot, which may outlive a refresh request. */
internal object MediaSearchRequestPolicy {
    fun canPublish(snapshotRequest: Long, displayedRequest: Long, activityCurrent: Boolean,
                   accessCurrent: Boolean): Boolean = snapshotRequest >= 0L &&
        snapshotRequest == displayedRequest && activityCurrent && accessCurrent
}
