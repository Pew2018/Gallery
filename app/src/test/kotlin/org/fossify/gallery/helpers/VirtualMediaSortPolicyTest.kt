package org.fossify.gallery.helpers

import org.fossify.commons.helpers.FAVORITES
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualMediaSortPolicyTest {
    private fun decide(
        source: String = FAVORITES,
        activityCurrent: Boolean = true,
        adapterMatches: Boolean = true,
        complete: Boolean = true,
        requestCurrent: Boolean = true,
        accessCurrent: Boolean = true,
        membersCurrent: Boolean? = true,
        metadata: Boolean = true,
        scanning: Boolean = false
    ) = VirtualMediaSortPolicy.decide(
        source, activityCurrent, adapterMatches, complete, requestCurrent, accessCurrent,
        membersCurrent, metadata, scanning
    )

    @Test fun completeVirtualSnapshotsAreReusableAndRefreshKeepsRunning() {
        assertEquals(VirtualMediaSortDecision.REUSE_COMPLETE, decide())
        assertEquals(VirtualMediaSortDecision.PREVIEW_COMPLETE_DURING_REFRESH, decide(scanning = true))
        assertEquals(VirtualMediaSortDecision.VERIFY_MEMBERS, decide(membersCurrent = null))
    }

    @Test fun unsafeOrIncompleteSnapshotsWaitOrReload() {
        assertEquals(VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH, decide(complete = false, scanning = true))
        assertEquals(VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH, decide(membersCurrent = false, scanning = true))
        assertEquals(VirtualMediaSortDecision.WAIT_FOR_ACTIVE_REFRESH, decide(requestCurrent = false, scanning = true))
        assertEquals(VirtualMediaSortDecision.RELOAD_REQUIRED, decide(complete = false))
        assertEquals(VirtualMediaSortDecision.RELOAD_REQUIRED, decide(metadata = false))
        assertEquals(VirtualMediaSortDecision.RELOAD_REQUIRED, decide(adapterMatches = false))
    }

    @Test fun revokedAccessIsASeparateOutcome() {
        assertEquals(VirtualMediaSortDecision.ACCESS_INVALID, decide(accessCurrent = false))
        assertEquals(VirtualMediaSortDecision.RELOAD_REQUIRED, decide(source = "/Camera"))
    }
}
