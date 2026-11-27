package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 1.2 B-group: a configuration switch creates a SCOPE_CHANGED request and the
 * following `onResume` asks for the same scope with ENTER. The device log shows both
 * workers reading the same 10 968 cached rows and both validating 160 candidates.
 *
 * The admission decision must be made before any generation/side effect is applied.
 */
class PhotoLoadAdmissionTest {
    private fun active(
        reason: PhotoLoadReason,
        key: String = "scope-a",
        revision: Long = 7L,
        access: Long = 3L,
        running: Boolean = true,
        current: Boolean = true,
        profileId: String? = "profile-1",
    ) = ActivePhotoLoad(
        request = 27L, reason = reason, scopeKey = key, profileId = profileId,
        revision = revision, accessVersion = access, running = running, current = current,
    )

    private fun decide(
        reason: PhotoLoadReason,
        active: ActivePhotoLoad?,
        key: String = "scope-a",
        revision: Long = 7L,
        access: Long = 3L,
        profileId: String? = "profile-1",
    ) = PhotoLoadAdmission.decide(
        reason = reason, scopeKey = key, profileId = profileId, revision = revision,
        accessVersion = access, active = active,
    )

    @Test fun b01_scopeChangedFollowedByResumeEnterReusesTheRunningWorker() {
        assertEquals(
            PhotoLoadDecision.REUSE,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.SCOPE_CHANGED))
        )
    }

    @Test fun b02_resumeEnterFollowedByConfigurationResultStartsTheNewScope() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.SCOPE_CHANGED, active(PhotoLoadReason.ENTER, key = "scope-old"), key = "scope-new")
        )
    }

    @Test fun b03_aStaleCallbackMayNotRestartTheCurrentLoad() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.ENTER, current = false))
        )
    }

    @Test fun b04_enterDuringAValidRefreshKeepsTheRefresh() {
        assertEquals(
            PhotoLoadDecision.REUSE,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.REFRESH))
        )
    }

    @Test fun b05_switchingBackToAStillNeedsItsOwnRequest() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.SCOPE_CHANGED, active(PhotoLoadReason.SCOPE_CHANGED, key = "scope-b"), key = "scope-a")
        )
    }

    @Test fun b06_aFinishedWorkerNeverAbsorbsANewRequest() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.SCOPE_CHANGED, running = false))
        )
    }

    @Test fun b07_repeatedPullToRefreshIsCoalescedNotIgnored() {
        assertEquals(
            PhotoLoadDecision.COALESCE_REFRESH,
            decide(PhotoLoadReason.REFRESH, active(PhotoLoadReason.REFRESH))
        )
    }

    @Test fun b07b_manualRefreshStillSupersedesACacheOnlyEntry() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.REFRESH, active(PhotoLoadReason.ENTER))
        )
    }

    @Test fun b08_returningFromAnUnchangedConfigurationKeepsTheScan() {
        assertEquals(
            PhotoLoadDecision.REUSE,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.ENTER))
        )
    }

    @Test fun b09_aNewContentRevisionNeedsItsOwnRequest() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.SCOPE_CHANGED), revision = 8L)
        )
    }

    @Test fun b10_revokedAccessNeedsItsOwnRequest() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.SCOPE_CHANGED), access = 4L)
        )
    }

    @Test fun b11_withoutAnActiveRequestTheIntentIsAdmitted() {
        assertEquals(PhotoLoadDecision.NEW, decide(PhotoLoadReason.ENTER, null))
    }

    @Test fun b12_theDecisionNeverDependsOnTheProfileIdAlone() {
        assertEquals(
            PhotoLoadDecision.NEW,
            decide(PhotoLoadReason.ENTER, active(PhotoLoadReason.SCOPE_CHANGED), profileId = "profile-2")
        )
    }
}
