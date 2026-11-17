package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.*
import org.junit.Test

class ViewerDeletionLedgerTest {
    private val a = Medium(null, "a.jpg", "/photos/a.jpg", "/photos", 10, 10, 100,
        TYPE_IMAGES, 0, false, 0, 1)
    private val b = a.copy(name = "b.jpg", path = "/photos/b.jpg")
    private val aReplacement = a.copy(modified = 20, size = 200, mediaStoreId = 2L)

    @Test fun latePreviewAndFinalCannotResurrectDeletedRow() {
        val ledger = ViewerDeletionLedger()
        ledger.confirm(1, setOf(a.path), 10)
        assertEquals(emptySet<String>(), ledger.restorationCandidates(listOf(a), 10))
        assertEquals(listOf(b), ledger.filter(listOf(a, b), 10, setOf(a.path)))
        assertEquals(listOf(b), ledger.filter(listOf(b), 11, emptySet()))
        assertEquals(listOf(b), ledger.filter(listOf(a, b), 12, emptySet()))
    }

    @Test fun laterSourceReadCanRestoreSameIdentityOrReplaceIt() {
        for (restored in listOf(a, aReplacement)) {
            val ledger = ViewerDeletionLedger()
            ledger.confirm(1, setOf(a.path), 10)
            assertEquals(setOf(a.path), ledger.restorationCandidates(listOf(restored, b), 11))
            assertEquals(listOf(b), ledger.filter(listOf(restored, b), 11, emptySet()))
            assertEquals(listOf(restored, b), ledger.filter(listOf(restored, b), 11, setOf(a.path)))
        }
    }

    @Test fun groupedBatchAndDuplicateResultKeepIndependentBarriers() {
        val ledger = ViewerDeletionLedger()
        assertEquals(setOf(a.path, b.path), ledger.confirm(1, setOf(a.path, b.path), 10))
        assertEquals(emptyList<Medium>(), ledger.filter(
            listOf(ThumbnailSection("June", "june"), a, b), 10, emptySet()))
        assertEquals(listOf(a), ledger.filter(listOf(a, b), 11, setOf(a.path)))
        assertFalse(ledger.hasNew(1, setOf(a.path, b.path)))
        assertEquals(emptySet<String>(), ledger.confirm(1, setOf(a.path), 11))
        assertEquals(listOf(a), ledger.filter(listOf(a, b), 11, emptySet()))
        assertEquals(setOf(a.path), ledger.confirm(2, setOf(a.path), 12))
        assertEquals(emptyList<Medium>(), ledger.filter(listOf(a, b), 12, setOf(a.path)))
        assertFalse(ledger.hasNew(1, setOf(a.path)))
    }
}
