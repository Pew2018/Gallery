package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PhotoProfileTest {
    private val folder = PhotoSource(PhotoSourceType.FOLDER, "/DCIM/Camera")
    private val favorites = PhotoSource(PhotoSourceType.FAVORITES)
    private fun profile(name: String = "Camera") = PhotoProfile(UUID.randomUUID().toString(), name, listOf(folder, favorites))
    private fun store(): PhotoProfileStore {
        var saved = ""
        return PhotoProfileStore({ saved }, { saved = it })
    }
    @Test fun serializationPreservesStableIdsUnicodeAndIndependentSources() {
        val a = profile("日常摄影")
        val b = profile("日常摄影").copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Pictures/Street")))
        val state = PhotoProfileState(profiles = listOf(a, b), selectedId = b.id)
        assertEquals(state, PhotoProfileCodec.decode(PhotoProfileCodec.encode(state)))
    }
    @Test fun createEditSelectDeleteNeverChangesAnotherProfile() {
        val store = store()
        val a = profile(); val b = profile()
        store.save(a); store.save(b)
        assertEquals(b.id, store.state().current?.id)
        store.save(a.copy(name = "New", sources = listOf(folder)))
        assertEquals("Saving an existing ID through the create path must retain its documented selection rule", a.id, store.state().selectedId)
        store.select(b.id)
        store.updateExisting(a.copy(name = "Renamed", sources = listOf(folder)))
        assertEquals("Editing another profile must not activate it", b.id, store.state().selectedId)
        store.updateExisting(b.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Pictures/Street"))))
        assertEquals("Editing the current profile must keep it selected", b.id, store.state().selectedId)
        assertEquals(b.copy(sources = listOf(PhotoSource(PhotoSourceType.FOLDER, "/Pictures/Street"))),
            store.state().profiles.first { it.id == b.id })
        assertEquals(listOf(PhotoSource(PhotoSourceType.FOLDER, "/Pictures/Street")), store.state().current?.sources)
        store.select(b.id)
        assertEquals(b.id, store.state().selectedId)
        store.delete(b.id)
        assertEquals(a.id, store.state().current?.id)
        store.delete(a.id)
        assertNull(store.state().current)
        assertNull(store.state().selectedId)
    }
    @Test fun cancellationHasNoWriteAndInvalidSelectionDoesNotBroadenScope() {
        var persisted = ""
        var writes = 0
        val store = PhotoProfileStore({ persisted }, { persisted = it; writes++ })
        val a = profile()
        store.save(a)
        val savedValue = persisted
        val savedWrites = writes
        val draft = a.copy(name = "Unsaved", sources = listOf(favorites))
        assertNotEquals(a, draft)
        // Dismissing an editor leaves its draft detached from persisted state.
        assertEquals(savedValue, persisted)
        assertEquals(savedWrites, writes)
        store.select("missing")
        assertEquals(a, store.state().current)
        assertEquals(a, store.state().profiles.single())
        assertTrue(runCatching { store.updateExisting(a.copy(id = "invalid")) }.isFailure)
        assertEquals("Invalid UUIDs must not be written", savedWrites, writes)
    }
    @Test fun duplicateSourcesNormalizeWithoutLosingCaseOrDifferentFolders() {
        val sources = PhotoProfileCodec.sources(listOf(folder, folder.copy(path = "/DCIM/Camera/"), favorites,
            PhotoSource(PhotoSourceType.FAVORITES, "/ignored"), PhotoSource(PhotoSourceType.FOLDER, "content://tree/a"),
            PhotoSource(PhotoSourceType.FOLDER, "/DCIM/camera")))
        assertEquals(listOf(folder, favorites, PhotoSource(PhotoSourceType.FOLDER, "/DCIM/camera")), sources)
    }
    @Test fun corruptedAndFutureDataFailClosed() {
        listOf("", "{broken", "null", "{}", "[]", "{\"version\":2,\"profiles\":[]}").forEach {
            assertTrue(PhotoProfileCodec.decode(it).profiles.isEmpty())
        }
    }
    @Test fun missingSelectedIdFallsBackToExistingProfile() {
        val a = profile()
        val state = PhotoProfileCodec.decode(PhotoProfileCodec.encode(PhotoProfileState(profiles = listOf(a), selectedId = "missing")))
        assertEquals(a, state.current)
        assertEquals(a.id, state.selectedId)
    }
    @Test fun deletingNonCurrentPreservesSelectionAndDeletingCurrentFallsBackSafely() {
        val store = store()
        val first = profile("First")
        val second = profile("Second")
        store.save(first)
        store.save(second)
        store.select(first.id)
        store.delete(second.id)
        assertEquals(first.id, store.state().selectedId)
        store.save(second)
        store.select(second.id)
        store.delete(second.id)
        assertEquals(first.id, store.state().selectedId)
        store.delete(first.id)
        assertTrue(store.state().profiles.isEmpty())
        assertNull(store.state().selectedId)
    }
    @Test fun invalidSelectionIsIgnoredAndManagementDiffOnlyReloadsForCurrentMediaChanges() {
        val first = profile("First")
        val second = profile("Second")
        val before = PhotoProfileState(profiles = listOf(first, second), selectedId = first.id)
        assertEquals(before, PhotoProfileCodec.decode(PhotoProfileCodec.encode(before)))
        val renamedOther = before.copy(profiles = listOf(first, second.copy(name = "Renamed")))
        val otherChanges = photoProfileStateChanges(before, renamedOther)
        assertTrue(otherChanges.changed)
        assertFalse(otherChanges.reloadMedia)
        assertFalse(otherChanges.refreshCurrentDisplay)
        val renamedCurrent = before.copy(profiles = listOf(first.copy(name = "Renamed"), second))
        val currentNameChanges = photoProfileStateChanges(before, renamedCurrent)
        assertFalse(currentNameChanges.reloadMedia)
        assertTrue(currentNameChanges.refreshCurrentDisplay)
        val changedSources = before.copy(profiles = listOf(first.copy(sources = listOf(favorites)), second))
        assertTrue(photoProfileStateChanges(before, changedSources).reloadMedia)
        assertTrue(photoProfileStateChanges(before, before.copy(selectedId = second.id)).reloadMedia)
        val store = PhotoProfileStore({ PhotoProfileCodec.encode(before) }, {})
        store.select("not-a-uuid")
        assertEquals(first.id, store.state().selectedId)
    }
    @Test fun invalidNameAndEmptySourcesAreRejectedBeforePersistence() {
        val store = store(); val a = profile(); store.save(a)
        listOf(a.copy(name = "  "), a.copy(sources = emptyList())).forEach {
            assertTrue(runCatching { store.save(it) }.isFailure)
            assertEquals(a, store.state().current)
        }
    }
    @Test fun foldersAreDirectOnlyAndFavoritesRemainDynamic() {
        val folders = setOf("/DCIM/Camera", "/Pictures/Street")
        assertTrue(PhotoProfilePolicy.includes("/DCIM/Camera/a.jpg", folders, emptySet(), false))
        assertTrue(PhotoProfilePolicy.includes("/Pictures/Street/a.jpg", folders, emptySet(), false))
        assertFalse(PhotoProfilePolicy.includes("/DCIM/Camera/Sub/a.jpg", folders, emptySet(), false))
        val favorite = "/Edited/a.jpg"
        assertTrue(PhotoProfilePolicy.includes(favorite, folders, setOf(favorite), true))
        assertFalse(PhotoProfilePolicy.includes(favorite, folders, emptySet(), true))
        assertFalse(PhotoProfilePolicy.includes(favorite, folders, setOf(favorite), false))
        assertFalse(PhotoProfilePolicy.includes("/Other/a.jpg", folders, emptySet(), false))
    }
    @Test fun overlappingSourcesDeduplicateExactMediaPaths() {
        assertEquals(listOf("/a.jpg", "/b.jpg", "/A.jpg"), PhotoProfilePolicy.unique(listOf("/a.jpg", "/b.jpg", "/a.jpg", "/A.jpg")) { it })
    }
    @Test fun unauthorizedFolderAndFavoriteOverlapIsAlwaysDenied() {
        val path = "/Locked/a.jpg"
        assertFalse(PhotoProfilePolicy.includes(path, setOf("/Locked"), setOf(path), true) { false })
        assertTrue(PhotoProfilePolicy.includes(path, setOf("/Locked"), setOf(path), true) { true })
    }
    @Test fun lateCancelledRequestsCannotOverwriteNewSelection() {
        val requests = FavoriteMediaLoadRequests()
        val a = requests.begin(); val b = requests.begin()
        assertFalse(requests.isCurrent(a)); assertTrue(requests.isCurrent(b))
        requests.invalidate()
        assertFalse(requests.isCurrent(b))
    }
    @Test fun sourcePickerDraftKeepsCrossDirectoryChoicesAndCancelDoesNotMutateOriginal() {
        val original = setOf("/DCIM/Camera", org.fossify.commons.helpers.FAVORITES)
        val draft = PhotoSourceSelectionDraft(original)
        draft.toggle("/Pictures/Street")
        draft.toggle("/DCIM/Camera/Child")
        draft.toggle("/DCIM/Camera")
        assertEquals(setOf("/Pictures/Street", "/DCIM/Camera/Child", org.fossify.commons.helpers.FAVORITES), draft.snapshot())
        assertEquals(setOf("/DCIM/Camera", org.fossify.commons.helpers.FAVORITES), original)
    }
    @Test fun sourcePickerDraftDeduplicatesSlashVariantsAndTreatsFavoritesSeparately() {
        val draft = PhotoSourceSelectionDraft(listOf("/DCIM/Camera/", "/DCIM/Camera", org.fossify.commons.helpers.FAVORITES))
        assertEquals(setOf("/DCIM/Camera", org.fossify.commons.helpers.FAVORITES), draft.snapshot())
        draft.toggle("/DCIM/Camera/")
        assertEquals(setOf(org.fossify.commons.helpers.FAVORITES), draft.snapshot())
    }
    @Test fun sourcePickerIndexesNestedFoldersAndFavoritesAsIndependentCandidates() {
        val directories = listOf("/DCIM/Camera", "/DCIM/Camera/Trips", "/Pictures/Camera",
            "/DCIM/Camera/", org.fossify.commons.helpers.FAVORITES, "recycle_bin", "content://tree/private")
            .map { path -> org.fossify.gallery.models.Directory().apply { this.path = path; name = path.substringAfterLast('/') } }
        val candidates = photoSourceCandidates(directories, PhotoSourceSelectionDraft(emptyList()))
        assertEquals(listOf("/DCIM/Camera", "/DCIM/Camera/Trips", "/Pictures/Camera",
            org.fossify.commons.helpers.FAVORITES), candidates.map { it.path })
    }
    @Test fun sourcePickerProtectsThumbnailWhenAnyFolderAncestorIsLocked() {
        val locked = setOf("/storage/emulated/0/Private")
        assertTrue(hasProtectedFolderBoundary("/storage/emulated/0/Private/Camera", locked::contains))
        assertTrue(hasProtectedFolderBoundary("/storage/emulated/0/Private", locked::contains))
        assertFalse(hasProtectedFolderBoundary("/storage/emulated/0/DCIM/Camera", locked::contains))
        assertFalse(hasProtectedFolderBoundary("content://tree/private", locked::contains))
    }
    @Test fun lateSourceStatusCannotReplaceTheNewestRequestOrUpdateAfterClose() {
        val requests = PhotoSourceStatusRequests()
        val sources = listOf(folder)
        val old = requests.begin(sources, 0, 1)!!
        val current = requests.begin(listOf(favorites), 0, 1)!!
        assertFalse(requests.isCurrent(old))
        assertTrue(requests.isCurrent(current))
        requests.invalidate()
        assertFalse(requests.isCurrent(current))
    }

    @Test fun equivalentInFlightSourceStatusChecksAreCoalescedButStateChangesSupersedeThem() {
        val requests = PhotoSourceStatusRequests()
        val snapshot = listOf(folder, favorites)
        val first = requests.begin(snapshot, 4, 1)!!
        assertNull(requests.begin(snapshot.toList(), 4, 1))
        val changedAuth = requests.begin(snapshot, 5, 1)!!
        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(changedAuth))
        requests.finish(first)
        assertTrue(requests.isCurrent(changedAuth))
        requests.finish(changedAuth)
        assertNull(requests.begin(snapshot, 5, 1))
        assertNotNull(requests.begin(snapshot, 5, 2))
    }
}
