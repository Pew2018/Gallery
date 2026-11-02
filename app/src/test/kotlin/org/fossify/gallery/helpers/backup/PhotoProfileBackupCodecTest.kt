package org.fossify.gallery.helpers.backup

import org.fossify.gallery.helpers.*
import org.junit.Assert.*
import org.junit.Test

class PhotoProfileBackupCodecTest {
    private val a = "d4dfe496-4e61-4e85-a01b-18e967e59a53"
    private val b = "70619f70-d47d-4bc1-83bf-c42a9f45d011"
    private val c = "a38ec583-e99f-448e-a3b6-72cf36be5e99"
    private fun profile(id: String = a, name: String = "旅行照片", path: String = "/storage/offline/旅行") =
        PhotoProfile(id, name, listOf(PhotoSource(PhotoSourceType.FOLDER, path), PhotoSource(PhotoSourceType.FAVORITES)))
    private fun state(vararg profiles: PhotoProfile, selected: String? = profiles.firstOrNull()?.id) =
        PhotoProfileState(profiles = profiles.toList(), selectedId = selected)

    @Test fun singleMixedChineseOfflineProfileRoundTrip() {
        val value = state(profile())
        assertEquals(value, PhotoProfileBackupCodec.decodeStrict(PhotoProfileBackupCodec.encode(value)))
    }
    @Test fun multipleProfilesPreserveIdsAndSelection() {
        val value = state(profile(), profile(b, "工作"), selected = b)
        assertEquals(value, PhotoProfileBackupCodec.decodeStrict(PhotoProfileBackupCodec.encode(value)))
    }
    @Test fun emptyCollectionIsAValidExplicitBackup() {
        assertEquals(PhotoProfileState(), PhotoProfileBackupCodec.decodeStrict("{\"version\":1,\"profiles\":[],\"selectedId\":null}"))
    }
    @Test fun sameIdAndContentAreSkippedWithoutWriting() {
        var persisted = PhotoProfileCodec.encode(state(profile()))
        var writes = 0
        val store = PhotoProfileStore({ persisted }, { persisted = it; writes++ })
        val result = store.importState(state(profile())).result
        assertEquals(0, result.imported); assertEquals(1, result.existing); assertEquals(0, writes)
    }
    @Test fun conflictingIdsCreateOnlyOneCopyAcrossRepeatedImports() {
        val local = state(profile(name = "本地"))
        val incoming = state(profile())
        val merged = PhotoProfileBackupCodec.merge(local, incoming) { b }
        assertEquals(a, merged.state.selectedId)
        assertEquals("本地", merged.state.profiles.first().name)
        assertEquals(b, merged.state.profiles.last().id)
        assertEquals(1, merged.result.conflicts)
        val repeated = PhotoProfileBackupCodec.merge(merged.state, incoming) { c }
        assertEquals(merged.state, repeated.state); assertEquals(1, repeated.result.existing)
    }
    @Test fun equivalentExistingConflictCopyIsSkippedDespiteSourceOrder() {
        val local = state(profile(name = "本地"), profile(b).let { it.copy(sources = it.sources.reversed()) })
        val merged = PhotoProfileBackupCodec.merge(local, state(profile()))
        assertEquals(local, merged.state); assertEquals(1, merged.result.existing)
    }
    @Test fun distinctUuidEquivalentContentIsSkipped() {
        val merged = PhotoProfileBackupCodec.merge(state(profile()), state(profile(b)))
        assertEquals(listOf(a), merged.state.profiles.map { it.id }); assertEquals(0, merged.result.imported)
    }
    @Test fun selectedIncomingIsUsedOnlyIfNoLocalValidSelection() {
        val incoming = state(profile(), profile(b, "工作"), selected = b)
        assertEquals(b, PhotoProfileBackupCodec.merge(PhotoProfileState(), incoming).state.selectedId)
        assertEquals(c, PhotoProfileBackupCodec.merge(state(profile(c, "正在浏览")), incoming).state.selectedId)
    }
    @Test fun newProfilesAreWrittenOnceAndExistingSelectionIsNotChanged() {
        var text = PhotoProfileCodec.encode(state(profile()))
        var writes = 0
        val store = PhotoProfileStore({ text }, { text = it; writes++ })
        store.importState(state(profile(b, "二"), profile(c, "三"), selected = c))
        assertEquals(1, writes); assertEquals(a, store.state().selectedId)
        assertEquals(listOf(a, b, c), store.state().profiles.map { it.id })
    }
    @Test fun malformedFileCannotChangePersistence() {
        var text = PhotoProfileCodec.encode(state(profile()))
        val before = text
        val store = PhotoProfileStore({ text }, { text = it })
        assertThrows(IllegalArgumentException::class.java) {
            store.importState(PhotoProfileBackupCodec.decodeStrict("broken"))
        }
        assertEquals(before, text)
    }
    @Test fun invalidUuidAndDuplicateUuidRejected() {
        val encoded = PhotoProfileCodec.encode(state(profile()))
        assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(encoded.replace(a, "1-1-1-1-1")) }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(state(profile(), profile())))
        }
    }
    @Test fun invalidFoldersAndUnknownSourceTypeRejected() {
        for (path in listOf("relative", "/", "////", "/a/../", "/a/./b", "content://tree/one", "/a\u0000b", "/a\tb", "/" + "a".repeat(4096))) {
            assertThrows(path, IllegalArgumentException::class.java) {
                PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(state(profile(path = path))))
            }
        }
        val json = PhotoProfileCodec.encode(state(profile())).replace("FOLDER", "OTHER")
        assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(json) }
    }
    @Test fun blankNamesMissingFieldsWrongTypesAndSourcesRejected() {
        val good = PhotoProfileCodec.encode(state(profile()))
        for (bad in listOf(good.replace("旅行照片", " "), good.replace("\"name\":", "\"missing\":"),
            good.replace("\"sources\":", "\"missing\":"), good.replace("\"profiles\":", "\"missing\":"),
            good.replace("\"FAVORITES\"", "true"))) {
            assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(state(PhotoProfile(a, "空来源", emptyList()))))
        }
    }
    @Test fun unsupportedVersionAndInvalidSelectedIdRejected() {
        val good = PhotoProfileCodec.encode(state(profile()))
        assertThrows(UnsupportedPhotoBackupVersion::class.java) { PhotoProfileBackupCodec.decodeStrict(good.replace("\"version\":1", "\"version\":2")) }
        assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(good.replace("\"selectedId\":\"$a\"", "\"selectedId\":\"$b\"")) }
    }
    @Test fun favoritesMustNotCarryFolderPath() {
        val value = state(PhotoProfile(a, "收藏", listOf(PhotoSource(PhotoSourceType.FAVORITES, "/private"))))
        assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(value)) }
    }
    @Test fun runtimeDecoderStillToleratesCorruption() {
        assertEquals(PhotoProfileState(), PhotoProfileCodec.decode("broken"))
    }
    @Test fun credentialsAndBindingsCannotBeRestoredOrExported() {
        val good = PhotoProfileCodec.encode(state(profile()))
        val injected = good.dropLast(1) + ",\"photo_source_bindings\":{\"/private\":\"authorized\"},\"photo_protect_password_hash\":\"secret\"}"
        val exported = PhotoProfileBackupCodec.encode(PhotoProfileBackupCodec.decodeStrict(injected))
        assertFalse(exported.contains("secret")); assertFalse(exported.contains("photo_source_bindings"))
    }
    @Test fun profileAndSourceCountLimitsAreEnforced() {
        val many = PhotoProfileState(profiles = (1..1001).map { profile(java.util.UUID.randomUUID().toString()) })
        assertThrows(IllegalArgumentException::class.java) { PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(many)) }
        val sources = (1..1001).map { PhotoSource(PhotoSourceType.FOLDER, "/a/$it") }
        assertThrows(IllegalArgumentException::class.java) {
            PhotoProfileBackupCodec.decodeStrict(PhotoProfileCodec.encode(state(PhotoProfile(a, "多来源", sources))))
        }
    }

    @Test fun storedExportRoundTripsNormalAndExplicitEmptyStates() {
        val original = state(profile(), profile(b, "工作"), selected = b)
        assertEquals(original, PhotoProfileBackupCodec.decodeStrict(PhotoProfileBackupCodec.encodeStored(PhotoProfileCodec.encode(original))))
        assertEquals(PhotoProfileState(), PhotoProfileBackupCodec.decodeStrict(PhotoProfileBackupCodec.encodeStored("")))
        assertEquals(PhotoProfileState(), PhotoProfileBackupCodec.decodeStrict(PhotoProfileBackupCodec.encodeStored(PhotoProfileCodec.encode(PhotoProfileState()))))
    }
    @Test fun storedExportRejectsLostRowsMalformedJsonDuplicateUuidAndVersionBeforeWriting() {
        val good = PhotoProfileCodec.encode(state(profile()))
        val partial = good.replace("\"profiles\":[", "\"profiles\":[{\"id\":\"broken\"},")
        assertEquals(1, PhotoProfileCodec.decode(partial).profiles.size) // runtime tolerance stays intact
        for (raw in listOf(partial, "{broken", " ", good.replace("\"version\":1", "\"version\":2"),
            PhotoProfileCodec.encode(state(profile(), profile())))) {
            var closed = false
            val output = object : java.io.ByteArrayOutputStream() { override fun close() { closed = true } }
            assertThrows(InvalidStoredPhotoProfileBackup::class.java) {
                BackupIo.writeText(output) { PhotoProfileBackupCodec.encodeStored(raw) }
            }
            assertEquals(0, output.size())
            assertTrue(closed)
        }
    }

}
