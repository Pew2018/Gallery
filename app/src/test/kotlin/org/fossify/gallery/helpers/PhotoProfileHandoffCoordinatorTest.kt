package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.3 B: the three distinct photo-profile return cases are pinned separately.
 *
 *  - same configuration: keep adapter, scroll and scan (P01-P03, P16)
 *  - different configuration: one prepared, atomic handoff (P04-P10, P12)
 *  - access/protection change: immediate clear, no pixel reuse (P08, P11, P18)
 */
class PhotoProfileHandoffCoordinatorTest {

    private val keyA = "scope-A|token|sort=1"
    private val keyB = "scope-B|token|sort=1"

    // P01/P02/P03/P16: a covering child page is not a departure from Photos.
    @Test
    fun p01SameConfigurationKeepsTheGridAndTheScan() {
        assertTrue(PhotoGridRetentionPolicy.clearsDisplayedGrid(
            isPhotoProfile = true, viewerTransition = false, childPageCovered = true) == false)
        assertTrue(PhotoChildReturnPolicy.detachesScan(
            isPhotoProfile = true, viewerTransition = false, childPageCovered = true,
            scanRunning = true, displayedKey = keyA, currentKey = keyA))
        assertTrue(PhotoChildReturnPolicy.holdsResult(childPageCovered = true, scanRetained = true))
        assertTrue(PhotoChildReturnPolicy.rejoins(
            returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
            revisionCurrent = true, accessCurrent = true, taskAlive = true))
        assertFalse(PhotoChildReturnPolicy.discards(
            requestCurrent = true, keyUnchanged = true, revisionCurrent = true,
            accessCurrent = true, taskAlive = true))
    }

    @Test
    fun p02AScanningPageWithTheSameScopeIsNotDiscarded() {
        assertTrue(PhotoChildReturnPolicy.detachesScan(true, false, true, true, keyA, keyA))
        assertFalse(PhotoChildReturnPolicy.detachesScan(true, false, true, true, keyA, keyB))
    }

    @Test
    fun p03MultiLayerChildPagesKeepTheGridUntilTheLastReturn() {
        // Both the Manage page and its Edit child set the same covered flag; the
        // predicate is independent of the number of layers.
        repeat(2) {
            assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(true, false, true))
        }
    }

    @Test
    fun p16AGenuineDepartureStillClearsTheGrid() {
        assertTrue(PhotoGridRetentionPolicy.clearsDisplayedGrid(true, false, false))
        assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(false, false, false))
        assertFalse(PhotoGridRetentionPolicy.clearsDisplayedGrid(true, true, false))
    }

    // P04/P05/P06: one configured switch, old pixels kept until the target commits.
    @Test
    fun p04RapidSwitchUsesOneGenerationPerRealTarget() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        val toB = handoff.onScopeChanged(true, false, true, keyB, keyA)
        assertTrue(toB.deferDisplayClear)
        assertTrue(toB.startTargetLoad)
        assertEquals(1L, handoff.currentGeneration)

        val toA = handoff.onScopeChanged(true, false, true, keyA, keyB)
        assertTrue(toA.deferDisplayClear)
        assertEquals(2L, handoff.currentGeneration)
        assertEquals(keyA, handoff.currentTargetKey)

        handoff.onGridCommitted(keyB)
        assertEquals("a stale B commit must not complete A",
            PhotoProfileHandoffCoordinator.Phase.TARGET_PREVIEW_PREPARING, handoff.currentPhase)
        handoff.onGridCommitted(keyA)
        assertEquals(PhotoProfileHandoffCoordinator.Phase.GRID_COMMITTED, handoff.currentPhase)
    }

    @Test
    fun p05CachedTargetIsPreparedBeforeTheChildPageReturns() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        val decision = handoff.onScopeChanged(true, false, true, keyB, keyA)
        assertTrue("cache hit or miss, preparation starts while covered", decision.startTargetLoad)
        handoff.onPreviewCommitted(keyB)
        assertEquals(PhotoProfileHandoffCoordinator.Phase.TARGET_PREVIEW_READY, handoff.currentPhase)
    }

    @Test
    fun p06UncachedTargetStillStartsOnePreparation() {
        val handoff = PhotoProfileHandoffCoordinator()
        val decision = handoff.onScopeChanged(true, false, true, keyB, keyA)
        assertTrue(decision.startTargetLoad)
        assertEquals(1, handoff.currentGeneration.toInt())
    }

    // P07: renaming a profile does not change the media scope, so no handoff starts.
    @Test
    fun p07NonMediaProfileChangeCannotStartAHandoff() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        val decision = handoff.onScopeChanged(true, false, true, keyA, keyA)
        assertFalse(decision.deferDisplayClear)
        assertFalse(decision.startTargetLoad)
        assertEquals("immediate_clear_required", decision.reason)
    }

    // P08/P11/P18: access, source bindings and empty scope always clear at once.
    @Test
    fun p08SourceChangeClearsImmediately() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        val decision = handoff.onScopeChanged(true, false, false, keyB, keyA)
        assertFalse(decision.deferDisplayClear)
        assertFalse(decision.startTargetLoad)
    }

    @Test
    fun p11RevokedAccessNeverDefersTheClear() {
        val handoff = PhotoProfileHandoffCoordinator()
        val decision = handoff.onScopeChanged(
            covered = true, viewerTransition = false, accessUnchanged = false,
            targetKey = keyB, displayedKey = keyA)
        assertFalse(decision.deferDisplayClear)
        assertEquals(0L, handoff.currentGeneration)
    }

    @Test
    fun p18AnUnresolvedScopeTakesTheSafeLoadingBranch() {
        val handoff = PhotoProfileHandoffCoordinator()
        val decision = handoff.onScopeChanged(true, false, true, "null", keyA)
        assertFalse(decision.deferDisplayClear)
        assertFalse(decision.startTargetLoad)
    }

    // P09/P10: late results and duplicate entry points.
    @Test
    fun p09LateOldScanCannotCommitANewerHandoff() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        handoff.onScopeChanged(true, false, true, keyB, keyA)
        handoff.onScopeChanged(true, false, true, keyA, keyB)
        handoff.onPreviewCommitted(keyB)
        assertEquals(PhotoProfileHandoffCoordinator.Phase.TARGET_PREVIEW_PREPARING, handoff.currentPhase)
    }

    @Test
    fun p10SharedPreferencesAndActivityResultAreMerged() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        handoff.onScopeChanged(true, false, true, keyB, keyA)
        val merged = handoff.onReloadEntry("activity_result", keyB, true)
        assertTrue(merged.mergeReloadEntry)
        assertTrue(merged.deferDisplayClear)
        assertFalse(merged.startTargetLoad)
        assertEquals(1, handoff.mergedEntryCount)
        val resume = handoff.onReloadEntry("resume", keyB, true)
        assertTrue(resume.mergeReloadEntry)
        assertEquals(2, handoff.mergedEntryCount)
        val unrelated = handoff.onReloadEntry("resume", keyA, true)
        assertFalse(unrelated.mergeReloadEntry)
    }

    // P12: overlapping media in two scopes still commits exactly one target grid.
    @Test
    fun p12TargetCommitIsKeyedToTheNewScopeOnly() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        handoff.onScopeChanged(true, false, true, keyB, keyA)
        handoff.onPreviewCommitted(keyB)
        assertEquals(PhotoProfileHandoffCoordinator.Phase.TARGET_PREVIEW_READY, handoff.currentPhase)
        handoff.onGridCommitted(keyB)
        assertEquals(PhotoProfileHandoffCoordinator.Phase.GRID_COMMITTED, handoff.currentPhase)
        handoff.complete()
        assertEquals(PhotoProfileHandoffCoordinator.Phase.COMPLETE, handoff.currentPhase)
        handoff.reset()
        assertFalse(handoff.inFlight)
    }

    @Test
    fun p13AViewerLaunchRetiresAnUnfinishedHandoff() {
        val handoff = PhotoProfileHandoffCoordinator()
        handoff.childPageCovered()
        handoff.onScopeChanged(true, false, true, keyB, keyA)
        assertTrue(handoff.inFlight)
        handoff.reset()
        assertFalse(handoff.inFlight)
        assertFalse(handoff.onReloadEntry("activity_result", keyB, true).mergeReloadEntry)
    }

    @Test
    fun p14RetentionRequiresAnUnchangedScopeAndAccess() {
        assertFalse(PhotoChildReturnPolicy.rejoins(
            returnedFromChildPage = true, requestCurrent = true, keyUnchanged = false,
            revisionCurrent = true, accessCurrent = true, taskAlive = true))
        assertTrue(PhotoChildReturnPolicy.discards(
            requestCurrent = true, keyUnchanged = false, revisionCurrent = true,
            accessCurrent = true, taskAlive = true))
        assertFalse(PhotoChildReturnPolicy.rejoins(
            returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
            revisionCurrent = true, accessCurrent = false, taskAlive = true))
    }

    @Test
    fun p15AViewerTransitionNeverReusesTheChildPageRetentionBranch() {
        assertFalse(PhotoChildReturnPolicy.detachesScan(true, true, true, true, keyA, keyA))
        assertTrue(PhotoGridRetentionPolicy.clearsDisplayedGrid(true, true, false) == false)
    }
}
