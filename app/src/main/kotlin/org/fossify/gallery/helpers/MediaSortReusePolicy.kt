package org.fossify.gallery.helpers

/** Local re-sorting is safe only for a completed, current, ungrouped physical-folder snapshot. */
internal object MediaSortReusePolicy {
    fun canReuse(
        photoProfile: Boolean,
        showAll: Boolean,
        physicalFolder: Boolean,
        recycleBin: Boolean,
        scanInProgress: Boolean,
        requestCurrent: Boolean,
        scanComplete: Boolean,
        snapshotComplete: Boolean,
        accessCurrent: Boolean,
        simpleMediaRows: Boolean
    ): Boolean = !photoProfile && !showAll && physicalFolder && !recycleBin &&
        !scanInProgress && requestCurrent && scanComplete && snapshotComplete &&
        accessCurrent && simpleMediaRows
}
