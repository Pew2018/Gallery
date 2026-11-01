package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerMediaUpdatePolicyTest {
    private fun medium(name: String, modified: Long = 10L, type: Int = 1) =
        Medium(null, name, "/gallery/$name", "/gallery", modified, 20L, 100L, type, 0, false, 0L, 0L)

    @Test fun prependingItemsMovesCurrentAnchorAndKeepsSurvivingIdentities() {
        val old = listOf(medium("a.jpg"), medium("b.jpg"), medium("c.jpg"))
        val fresh = listOf(medium("x.jpg")) + old

        val update = ViewerMediaUpdatePolicy.update(old, fresh, "/gallery/b.jpg", 1)

        assertEquals(2, update.currentPosition)
        assertEquals(old.map { ViewerMediaUpdatePolicy.identity(it) }.toSet(), update.survivingIdentities)
        assertTrue(update.invalidatedIdentities.isEmpty())
    }

    @Test fun deletingCurrentItemUsesBoundedNeighborFallback() {
        val old = listOf(medium("a.jpg"), medium("b.jpg"), medium("c.jpg"))
        val fresh = listOf(old[0], old[2])

        val update = ViewerMediaUpdatePolicy.update(old, fresh, "/gallery/b.jpg", 1)

        assertEquals(1, update.currentPosition)
        assertEquals(setOf(ViewerMediaUpdatePolicy.identity(old[1])), update.invalidatedIdentities)
    }

    @Test fun stableIdentityDoesNotIncludeMetadataButTypeAndPathRemainDistinct() {
        val old = medium("same.jpg", modified = 10L, type = 1)
        val changedContent = medium("same.jpg", modified = 11L, type = 1)
        val changedType = medium("same.jpg", modified = 10L, type = 2)

        assertEquals(ViewerMediaUpdatePolicy.identity(old), ViewerMediaUpdatePolicy.identity(changedContent))
        assertFalse(ViewerMediaUpdatePolicy.identity(old) == ViewerMediaUpdatePolicy.identity(changedType))
        assertFalse(ViewerMediaUpdatePolicy.identity(old) == ViewerMediaUpdatePolicy.identity(medium("renamed.jpg")))
        assertTrue(ViewerMediaUpdatePolicy.contentChanged(
            ViewerMediaUpdatePolicy.contentSignature(old), ViewerMediaUpdatePolicy.contentSignature(changedContent)
        ))
    }

    @Test fun provisionalMetadataCanBeEnrichedWithoutTreatingUnknownAsReplacement() {
        val provisional = Medium(null, "same.jpg", "/gallery/same.jpg", "/gallery", 0L, 0L, 0L, 1, 0, false, 0L, 0L)
        val complete = Medium(42L, "same.jpg", "/gallery/same.jpg", "/gallery", 11L, 123L, 400L, 1, 0, true, 0L, 1234L)
        val oldSignature = ViewerMediaUpdatePolicy.contentSignature(provisional)
        val freshSignature = ViewerMediaUpdatePolicy.contentSignature(complete)

        assertFalse(ViewerMediaUpdatePolicy.contentChanged(oldSignature, freshSignature))
        val merged = ViewerMediaUpdatePolicy.mergeMetadata(provisional, complete)
        assertEquals(42L, merged.id)
        assertEquals(123L, merged.taken)
        assertTrue(merged.isFavorite)
        assertEquals(400L, merged.size)
    }

    @Test fun samePathReplacementAndMediaTypeChangeInvalidateContent() {
        val old = medium("same.jpg", modified = 10L)
        val replacement = medium("same.jpg", modified = 11L)
        val oldSignature = ViewerMediaUpdatePolicy.contentSignature(old)
        assertTrue(ViewerMediaUpdatePolicy.contentChanged(oldSignature, ViewerMediaUpdatePolicy.contentSignature(replacement)))
    }

    @Test fun fullCatalogUpgradeKeepsSelectedItemPositionForLargeCollections() {
        val all = (0 until 10_000).map { medium("$it.jpg") }
        val selected = all[5_000]
        val local = listOf(all[4_999], selected, all[5_001])

        val update = ViewerMediaUpdatePolicy.update(local, all, selected.path, 1)

        assertEquals(5_000, update.currentPosition)
        assertEquals(3, update.survivingIdentities.size)
        assertEquals(0, update.invalidatedIdentities.size)
    }

    @Test fun unrelatedPathInvalidationDoesNotReloadUnchangedPage() {
        val a = medium("a.jpg")
        val b = medium("b.jpg")
        val beforeA = ViewerMediaUpdatePolicy.contentSignature(a)
        val beforeB = ViewerMediaUpdatePolicy.contentSignature(b)

        ViewerContentInvalidationRegistry.invalidate(b.path)

        assertFalse(ViewerMediaUpdatePolicy.contentChanged(beforeA, ViewerMediaUpdatePolicy.contentSignature(a)))
        assertTrue(ViewerMediaUpdatePolicy.contentChanged(beforeB, ViewerMediaUpdatePolicy.contentSignature(b)))
    }

    @Test fun globalCatalogRevisionDoesNotInvalidateIndividualPageSignature() {
        val a = medium("a.jpg")
        val before = ViewerMediaUpdatePolicy.contentSignature(a)

        FolderContentRevision.changed()
        PhotoContentRevision.changed()

        assertFalse(ViewerMediaUpdatePolicy.contentChanged(before, ViewerMediaUpdatePolicy.contentSignature(a)))
    }

    @Test fun metadataEnrichmentAndFavoriteFlagDoNotInvalidateDecodedContent() {
        val base = medium("a.jpg")
        val old = ViewerMediaUpdatePolicy.contentSignature(base)
        val enriched = base.copy(id = 42L, taken = 123L, isFavorite = true)

        assertFalse(ViewerMediaUpdatePolicy.contentChanged(old, ViewerMediaUpdatePolicy.contentSignature(enriched)))
    }

    @Test fun entirelyUnknownContentFactsFailConservatively() {
        val unknown = Medium(null, "a.jpg", "/gallery/a.jpg", "/gallery", 0L, 0L, 0L, 1, 0, false, 0L, 0L)

        assertTrue(ViewerMediaUpdatePolicy.contentChanged(
            ViewerMediaUpdatePolicy.contentSignature(unknown), ViewerMediaUpdatePolicy.contentSignature(unknown)
        ))
    }
}
