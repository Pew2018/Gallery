package org.fossify.gallery.helpers

import org.fossify.commons.helpers.FAVORITES

/** Reasons the virtual collection sort path may reuse, defer, or reject a snapshot. */
internal enum class VirtualMediaSortDecision {
    REUSE_COMPLETE,
    PREVIEW_COMPLETE_DURING_REFRESH,
    VERIFY_MEMBERS,
    WAIT_FOR_ACTIVE_REFRESH,
    RELOAD_REQUIRED,
    ACCESS_INVALID
}

/** Narrow admission gate for sorting already published virtual collection rows. */
internal object VirtualMediaSortPolicy {
    fun decide(
        source: String,
        activityCurrent: Boolean,
        adapterMatchesSource: Boolean,
        displayedSnapshotComplete: Boolean,
        requestCurrent: Boolean,
        accessCurrent: Boolean,
        membersCurrent: Boolean?,
        requiredSortMetadataPresent: Boolean,
        scanInProgress: Boolean
    ): VirtualMediaSortDecision {
        if (source != FAVORITES && source != RECYCLE_BIN) return VirtualMediaSortDecision.RELOAD_REQUIRED
        if (!activityCurrent || !accessCurrent) return VirtualMediaSortDecision.ACCESS_INVALID
        if (!adapterMatchesSource || !displayedSnapshotComplete || !requiredSortMetadataPresent) {
            return if (scanInProgress) VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH
            else VirtualMediaSortDecision.RELOAD_REQUIRED
        }
        if (!requestCurrent) return if (scanInProgress) VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH
        else VirtualMediaSortDecision.RELOAD_REQUIRED
        if (membersCurrent == null) return VirtualMediaSortDecision.VERIFY_MEMBERS
        if (!membersCurrent) return if (scanInProgress) VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH
        else VirtualMediaSortDecision.RELOAD_REQUIRED
        return if (scanInProgress) VirtualMediaSortDecision.PREVIEW_COMPLETE_DURING_REFRESH
        else VirtualMediaSortDecision.REUSE_COMPLETE
    }
}
