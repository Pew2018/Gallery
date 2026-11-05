package org.fossify.gallery.helpers

import android.app.Application
import org.fossify.gallery.models.Medium
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class Phase15GridEvidenceTest {
    private fun medium(index: Int) = Medium(null, "$index.jpg", "/gallery/$index.jpg", "/gallery", 1, 1, 1, TYPE_IMAGES, 0, false, 0, 0)
    private fun contract(query: String = "", sorting: Int = 2, session: String? = null, revision: Long = 0L) =
        ViewerGridOrderStore.contract(sorting, TYPE_IMAGES, false, query, session, revision)

    @Test fun b01Committed1070IsDistinctFromUncommitted3434AndFinal5285() {
        val committed = (0 until 1070).map(::medium)
        val pending = (0 until 3434).map(::medium)
        val id = ViewerGridOrderStore.offer("profile", committed[1007].path, committed,
            version = 9, contract = contract(), includeCandidates = true, revision = 0)!!
        val evidence = ViewerGridOrderStore.takeEvidence(id, "profile", committed[1007].path, contract())!!
        assertEquals(1070, evidence.paths.size)
        assertEquals(9L, evidence.version)
        assertNotEquals(pending.size, evidence.paths.size)
        val full = (0 until 5285).map(::medium)
        assertEquals(5285, ViewerGridOrderPolicy.reconcilePhotoCatalog(evidence.paths, committed, full,
            false, complete = true) { it.path }.first.size)
        assertNull(ViewerGridOrderStore.takeEvidence(id, "profile", committed[1007].path, contract()))
    }

    @Test fun b07ChangedSourceSearchSortSessionOrRevisionRejectsOldEvidence() {
        val media = listOf(medium(1))
        listOf(contract(query = "new"), contract(sorting = 4), contract(session = "revoked"), contract(revision = 1)).forEach { changed ->
            val id = ViewerGridOrderStore.offer("profile", media[0].path, media, contract = contract())!!
            assertNull(ViewerGridOrderStore.takeEvidence(id, "profile", media[0].path, changed))
        }
        val id = ViewerGridOrderStore.offer("profile", media[0].path, media, contract = contract())!!
        assertNull(ViewerGridOrderStore.takeEvidence(id, "other", media[0].path, contract()))
    }

    @Test fun evidenceOwnsMetadataAndNeverBecomesAnAccessCapability() {
        val media = listOf(medium(1))
        val id = ViewerGridOrderStore.offer("profile", media[0].path, media,
            contract = contract(), includeCandidates = true)!!
        media[0].name = "mutated"
        val evidence = ViewerGridOrderStore.takeEvidence(id, "profile", media[0].path, contract())!!
        assertEquals("1.jpg", evidence.candidates.single().name)
        val token = FolderUnlockSessionManager.createSession("/gallery")
        FolderUnlockSessionManager.revokeSession(token)
        assertFalse(FolderUnlockSessionManager.isMediaAuthorized(token, evidence.paths.single()) { true })
    }

    @Test fun diagnosticsOffStillExecutesEveryMetadataReadAndPropagatesFailure() {
        FolderRefreshTrace.enabled = false
        var reads = 0
        repeat(100) { FolderRefreshTrace.measure(1, "file_metadata") { reads++ } }
        assertEquals(100, reads)
        try {
            FolderRefreshTrace.measure(1, "file_metadata") { throw java.io.IOException("unreadable") }
            fail("Exception must propagate")
        } catch (_: java.io.IOException) { }
    }

    @Test fun diagnosticsOnDoesNotCreateTwoRecordsPerFile() {
        FolderRefreshTrace.enabled = true
        FolderRefreshTrace.clear()
        try {
            repeat(100) { FolderRefreshTrace.measure(1, "file_metadata") { it } }
            assertEquals("", FolderRefreshTrace.dump())
        } finally { FolderRefreshTrace.enabled = false }
    }
}
