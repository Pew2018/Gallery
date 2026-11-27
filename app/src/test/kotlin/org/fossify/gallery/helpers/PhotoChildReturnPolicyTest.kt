package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three return branches of [PhotoChildReturnPolicy] for the Photos configuration page.
 *
 * The user-visible defect this pins: opening `PhotoProfileManageActivity` used to be
 * treated as leaving Photos, the in-flight scan was cancelled, and the return then
 * started a cold full scan behind an empty page. Keeping the scan is only safe while
 * the identity of the request is unchanged, so every term is asserted on its own.
 */
class PhotoChildReturnPolicyTest {
    private fun detaches(
        viewerTransition: Boolean = false,
        covered: Boolean = true,
        running: Boolean = true,
        displayed: String? = "scope",
        current: String? = "scope",
    ) = PhotoChildReturnPolicy.detachesScan(
        isPhotoProfile = true,
        viewerTransition = viewerTransition,
        childPageCovered = covered,
        scanRunning = running,
        displayedKey = displayed,
        currentKey = current,
    )

    @Test
    fun aRunningAuthorizedScanSurvivesTheCover() {
        assertTrue(detaches())
    }

    @Test
    fun aGenuineDepartureStillCancelsTheScan() {
        // Not a Photos page, or covered by nothing: the existing cancel path stays.
        assertFalse(detaches(covered = false))
        assertFalse(PhotoChildReturnPolicy.detachesScan(
            isPhotoProfile = false, viewerTransition = false, childPageCovered = true,
            scanRunning = true, displayedKey = "scope", currentKey = "scope"))
    }

    @Test
    fun theViewerKeepsItsOwnLifecycle() {
        // A viewer transition already retains the grid through photoViewerTransition.
        assertFalse(detaches(viewerTransition = true))
    }

    @Test
    fun aChangedScopeKeyIsNotCarriedAcrossTheCover() {
        assertFalse(detaches(displayed = "old", current = "new"))
    }

    @Test
    fun anAbsentKeyOrAStoppedWorkerIsNotRetained() {
        assertFalse(detaches(displayed = null))
        assertFalse(detaches(running = false))
    }

    @Test
    fun resultsAreHeldOnlyWhileThePageIsCovered() {
        assertTrue(PhotoChildReturnPolicy.holdsResult(childPageCovered = true, scanRetained = true))
        assertFalse(PhotoChildReturnPolicy.holdsResult(childPageCovered = false, scanRetained = true))
        assertFalse(PhotoChildReturnPolicy.holdsResult(childPageCovered = true, scanRetained = false))
    }

    @Test
    fun anUnchangedAuthorizedScanIsRejoined() {
        assertTrue(PhotoChildReturnPolicy.rejoins(
            returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
            revisionCurrent = true, accessCurrent = true, taskAlive = true))
    }

    @Test
    fun everyBrokenIdentityTermForcesAReload() {
        val broken = listOf(
            "not a child return" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = false, requestCurrent = true, keyUnchanged = true,
                revisionCurrent = true, accessCurrent = true, taskAlive = true),
            "superseded request" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = true, requestCurrent = false, keyUnchanged = true,
                revisionCurrent = true, accessCurrent = true, taskAlive = true),
            "moved scope key" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = true, requestCurrent = true, keyUnchanged = false,
                revisionCurrent = true, accessCurrent = true, taskAlive = true),
            "new content revision" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
                revisionCurrent = false, accessCurrent = true, taskAlive = true),
            "revoked access" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
                revisionCurrent = true, accessCurrent = false, taskAlive = true),
            "dead worker" to PhotoChildReturnPolicy.rejoins(
                returnedFromChildPage = true, requestCurrent = true, keyUnchanged = true,
                revisionCurrent = true, accessCurrent = true, taskAlive = false),
        )
        broken.forEach { (reason, rejoined) -> assertFalse(reason, rejoined) }
    }

    @Test
    fun discardIsTheExactComplementOfRejoin() {
        // A retained scan is never both reused and discarded, and never neither.
        val flags = listOf(true, false)
        val cases = flags.flatMap { request ->
            flags.flatMap { key ->
                flags.flatMap { revision ->
                    flags.flatMap { access ->
                        flags.map { alive -> listOf(request, key, revision, access, alive) }
                    }
                }
            }
        }
        cases.forEach { case ->
            val (request, key) = case
            val revision = case[2]
            val access = case[3]
            val alive = case[4]
            val reusable = request && key && revision && access && alive
            assertTrue(
                "rejoin/discard must partition identity $request/$key/$revision/$access/$alive",
                PhotoChildReturnPolicy.discards(request, key, revision, access, alive) != reusable,
            )
        }
    }
}
