package org.fossify.gallery.helpers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.2 wiring: the two P0 fixes are only real if the Activity calls the policy
 * *before* it mutates the request state, and if the worker receives the displayed
 * baseline. Behavioural proof for the policies themselves lives in
 * `PhotoRefreshBaselineTest`, `PhotoLoadAdmissionTest` and `PhotoSnapshotAdmissionTest`;
 * the device traces behind them are in
 * `docs/performance/root-navigation-photo-publication-phase1.2.md`.
 */
class Phase12PhotoPublicationWiringTest {
    private val source = File("src/main/kotlin/org/fossify/gallery")
    private val activity = source.resolve("activities/MediaActivity.kt").readText()
    private val loader = source.resolve("helpers/PhotoProfileMediaLoader.kt").readText()
    private val task = source.resolve("asynctasks/GetMediaAsynctask.kt").readText()

    private fun photoRequest() = activity.substringAfter("private fun getPhotoProfileMedia(")
        .substringBefore("private fun activePhotoLoad(")

    @Test fun admissionRunsBeforeTheGenerationIsBumped() {
        val request = photoRequest()
        val admitted = request.indexOf("PhotoLoadAdmission.decide(")
        val generation = request.indexOf("mediaLoadRequests.begin()")
        assertTrue("admission is not called", admitted >= 0)
        assertTrue("generation is created before admission", generation > admitted)
    }

    @Test fun reuseKeepsTheRunningWorkerAndItsDiagnosticIdentity() {
        val reuse = photoRequest().substringAfter("PhotoLoadDecision.REUSE", "")
        assertTrue(reuse.isNotEmpty())
        assertTrue(activity.contains("private fun resumeActivePhotoRequest("))
        // The reuse branch returns before any cancellation or display reset.
        val branch = photoRequest().substringAfter("if (decision == org.fossify.gallery.helpers.PhotoLoadDecision.REUSE)")
            .substringBefore("if (decision == org.fossify.gallery.helpers.PhotoLoadDecision.COALESCE_REFRESH)")
        assertTrue(branch.contains("resumeActivePhotoRequest("))
        assertTrue(branch.contains("return"))
        assertFalse(branch.contains("stopFetching("))
        assertFalse(branch.contains("discardInaccessibleGrid()"))
    }

    @Test fun aCoalescedPullIsFollowedUpInsteadOfDropped() {
        assertTrue(photoRequest().contains("photoPendingRefresh = true"))
        assertTrue(photoRequest().contains("coalesced_into_running_refresh"))
        val followUp = activity.substringAfter("if (photoPendingRefresh) {")
            .substringBefore("getPhotoProfileMedia(org.fossify.gallery.helpers.PhotoLoadReason.REFRESH)")
        assertTrue(followUp.contains("photoPendingRefresh = false"))
        assertTrue(followUp.contains("isFinishing"))
    }

    @Test fun scrollingIsNotAnAccessCheckForTheRetainedGrid() {
        assertFalse("the scroll bypass is still part of the retention condition",
            photoRequest().contains("isMediaGridMoving"))
        assertTrue(photoRequest().contains("photoLoadedRevision == org.fossify.gallery.helpers.PhotoContentRevision.current()"))
    }

    @Test fun theRefreshBaselineIsCapturedFromTheDisplayedSourceList() {
        val request = photoRequest()
        assertTrue(request.contains("PhotoRefreshBaseline.capture("))
        assertTrue(request.contains("displayed = mMedia"))
        assertTrue(request.contains("searchActive = mLastSearchedText.isNotEmpty()"))
        assertTrue(request.contains("photoRefreshBaseline = baseline"))
    }

    @Test fun theWorkerPassesTheBaselineAndTheBrowseHeadroomToTheLoader() {
        assertTrue(task.contains("photoRefreshBaseline, photoDisplayedGridSize, photoBaselineComplete"))
        assertTrue(loader.contains("refreshBaseline?.takeIf { it.supports(request) }"))
        assertTrue(loader.contains("baseline.merged(discovered)"))
        // COMPLETE must never be built from the baseline.
        assertTrue(loader.substringAfter("val complete = result(PhotoLoadStage.COMPLETE, media)")
            .isNotEmpty())
        assertFalse(loader.substringAfter("val complete = result(PhotoLoadStage.COMPLETE, media)")
            .substringBefore("diagnostics?.apply").contains("baseline"))
    }

    @Test fun partialStagesCannotShrinkTheCommittedGrid() {
        assertTrue(activity.contains("if (result.stage != org.fossify.gallery.helpers.PhotoLoadStage.COMPLETE &&"))
        assertTrue(activity.contains("would_remove_displayed_media"))
    }

    @Test fun diagnosticsSeparateSelectionFromAdapterCommit() {
        for (event in listOf("photo_load_intent_received", "photo_load_request_admitted",
            "photo_load_request_reused", "photo_load_request_rejected",
            "photo_refresh_baseline_captured", "photo_grid_adapter_committed", "photo_ui_final_summary")) {
            assertTrue("missing $event", activity.contains(event))
        }
        for (event in listOf("photo_partial_reconciled", "photo_partial_publication_suppressed",
            "photo_refresh_baseline_rejected")) {
            assertTrue("missing $event", activity.contains(event) || loader.contains(event))
        }
        // The Adapter commit is reported by the commit callback, not by the selection.
        assertTrue(activity.substringAfter("logPhotoScrollEvent(\"photo_grid_publication_committed\"")
            .contains("publishMediaSnapshot("))
        assertTrue(activity.substringAfter("photoAdapterCommitCount++")
            .substringBefore("binding.mediaGrid.post { refreshPhotoBrowseHeadroom() }")
            .contains("photo_grid_adapter_committed"))
    }
}
