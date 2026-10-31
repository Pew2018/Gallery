package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test

class PhotoProfileEnhancementsTest {
    @Test fun revokedBackgroundAuthenticationCannotRecreateCapability() {
        val sessions = PhotoAccessCapability()
        val pending = sessions.generation()
        sessions.revoke()
        assertNull(sessions.create(pending) { true })
        assertFalse(sessions.active(null))
    }
    @Test fun passwordChangeInvalidatesExistingTokenAndNewAuthenticationReplacesIt() {
        val sessions = PhotoAccessCapability()
        var credentialCurrent = true
        val old = sessions.create(sessions.generation()) { credentialCurrent }!!
        assertTrue(sessions.active(old))
        assertFalse(sessions.active("caller-controlled"))
        credentialCurrent = false
        assertFalse(sessions.active(old))
        assertNull(sessions.create(sessions.generation()) { false })
        val fresh = sessions.create(sessions.generation()) { true }!!
        assertFalse(sessions.active(old))
        assertTrue(sessions.active(fresh))
        sessions.revoke()
        assertFalse(sessions.active(fresh))
    }
    @Test fun allNestedLocksNeedBindingsAndFreshAuthentication() {
        val a = FavoriteAccessSessionManager.Protection(1, "first")
        val b = FavoriteAccessSessionManager.Protection(0, "second")
        val locks = mapOf("/private" to a, "/private/nested" to b)
        val first = mapOf("/private" to PhotoProfileAccessSessionManager.fingerprint(a.type, a.hash))
        assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(locks, first, true))
        val complete = first + ("/private/nested" to PhotoProfileAccessSessionManager.fingerprint(b.type, b.hash))
        assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(locks, complete, false))
        assertTrue(PhotoProfileAccessSessionManager.bindingsAuthorize(locks, complete, true))
        assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(locks + ("/private" to a.copy(hash = "changed")), complete, true))
        assertTrue(PhotoProfileAccessSessionManager.bindingsAuthorize(emptyMap(), emptyMap(), false))
    }
    @Test fun biometricsRequireAConfiguredRecoveryCredential() {
        val fingerprint = org.fossify.commons.helpers.PROTECTION_FINGERPRINT
        assertFalse(Config.PhotoCredentialSnapshot(true, "", fingerprint, "", 0).isConfigured)
        assertTrue(Config.PhotoCredentialSnapshot(true, "", fingerprint, "recovery", 1).isConfigured)
        assertFalse(Config.PhotoCredentialSnapshot(true, "", 1, "", 0).isConfigured)
        assertTrue(Config.PhotoCredentialSnapshot(true, "pin", 1, "", 0).isConfigured)
    }
    @Test fun slideshowHonorsVideoAndGifSettingsAndExcludesUnsupportedFormats() {
        assertTrue(PhotoPlaybackPolicy.canPlay(TYPE_IMAGES, false, false))
        assertTrue(PhotoPlaybackPolicy.canPlay(TYPE_PORTRAITS, false, false))
        assertFalse(PhotoPlaybackPolicy.canPlay(TYPE_VIDEOS, false, true))
        assertTrue(PhotoPlaybackPolicy.canPlay(TYPE_VIDEOS, true, false))
        assertFalse(PhotoPlaybackPolicy.canPlay(TYPE_GIFS, true, false))
        assertTrue(PhotoPlaybackPolicy.canPlay(TYPE_GIFS, false, true))
        assertFalse(PhotoPlaybackPolicy.canPlay(TYPE_RAWS, true, true))
        assertFalse(PhotoPlaybackPolicy.canPlay(TYPE_SVGS, true, true))
    }
    @Test fun photoScopeChangesOnlyWithPublishedContent() {
        assertEquals(ToolbarQuickScope.HOME, PhotoPlaybackPolicy.toolbarScope(false))
        assertEquals(ToolbarQuickScope.FOLDER, PhotoPlaybackPolicy.toolbarScope(true))
        val saved = listOf(ToolbarQuickActions.FILTER, ToolbarQuickActions.SORT, ToolbarQuickActions.HIDDEN)
        assertEquals(listOf(ToolbarQuickActions.FILTER, ToolbarQuickActions.SORT),
            ToolbarQuickActions.plan(ToolbarQuickScope.FOLDER, saved, 5) { it != ToolbarQuickActions.HIDDEN })
        assertEquals(3, saved.size)
    }
    @Test fun mixedMediaSourcesRemainDirectAndDeduplicated() {
        val paths = listOf("/Camera/a.jpg", "/Camera/b.mp4", "/Other/fav.mp4", "/Camera/Sub/no.mp4", "/Other/unselected.jpg", "/Camera/b.mp4")
        val actual = PhotoProfilePolicy.unique(paths.filter {
            PhotoProfilePolicy.includes(it, setOf("/Camera"), setOf("/Other/fav.mp4"), true)
        }) { it }
        assertEquals(listOf("/Camera/a.jpg", "/Camera/b.mp4", "/Other/fav.mp4"), actual)
    }
    @Test fun fileSymlinkCannotHideAnAdditionalProtectionBoundary() {
        val root = java.nio.file.Files.createTempDirectory("photo-profile-link")
        try {
            val publicFolder = java.nio.file.Files.createDirectory(root.resolve("public"))
            val privateFolder = java.nio.file.Files.createDirectory(root.resolve("private"))
            val target = java.nio.file.Files.write(privateFolder.resolve("secret.jpg"), byteArrayOf(1, 2, 3))
            val link = java.nio.file.Files.createSymbolicLink(publicFolder.resolve("alias.jpg"), target)
            val lock = FavoriteAccessSessionManager.Protection(1, "source-password")
            val snapshot = FavoriteAccessSessionManager.captureSnapshot(listOf(link.toString())) {
                if (it == privateFolder.toString()) lock else null
            }
            val actualLocks = snapshot.entries.getValue(link.toString()).locks
            assertEquals(lock, actualLocks[privateFolder.toString()])
            assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(actualLocks, emptyMap(), true))
            assertFalse(PhotoProfileAccessSessionManager.bindingsAuthorize(actualLocks,
                mapOf(privateFolder.toString() to PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)), false))
        } finally { root.toFile().deleteRecursively() }
    }


    @Test fun onlyVerifiedSameTypeNonemptyPinOrPatternCanBeReused() {
        val pin = org.fossify.commons.helpers.PROTECTION_PIN
        val pattern = org.fossify.commons.helpers.PROTECTION_PATTERN
        val fingerprint = org.fossify.commons.helpers.PROTECTION_FINGERPRINT
        val evidence = PhotoVerifiedCredential(pin, "verified-secret")
        assertTrue(evidence.matches(pin, "verified-secret"))
        assertFalse(evidence.matches(pin, "different"))
        assertFalse(evidence.matches(pattern, "verified-secret"))
        assertFalse(evidence.matches(fingerprint, "verified-secret"))
        assertFalse(PhotoVerifiedCredential(pin, "").matches(pin, ""))
        assertFalse(PhotoVerifiedCredential(fingerprint, "").matches(fingerprint, ""))
        assertFalse(evidence.toString().contains("verified-secret"))
    }

    @Test fun requestsAcrossPhotoMenuEmptyStateAndSettingsShareOneLease() {
        val requests = PhotoAuthRequests()
        val media = Any()
        val settings = Any()
        val first = requests.begin(media, 0)!!
        assertNull(requests.begin(media, 0))
        assertNull(requests.begin(settings, 0))
        assertFalse(requests.current(first, settings))
        requests.finish(first)
        assertNotNull(requests.begin(settings, 0))
        assertFalse(requests.current(first, media))
    }

    @Test fun duplicateAndOldStepCallbacksCannotCompleteNewChallenges() {
        val requests = PhotoAuthRequests()
        val owner = Any()
        val request = requests.begin(owner, 0)!!
        val old = request.callback()
        val fresh = request.callback()
        assertFalse(old())
        assertTrue(fresh())
        assertFalse(fresh())
        val cancelled = request.callback()
        requests.cancelOwner(owner)
        assertFalse(cancelled())
        val next = requests.begin(owner, 0)!!
        requests.finish(request)
        assertTrue(requests.current(next, owner))
    }

    @Test fun newGenerationRejectsOldOwnerAndCallbacks() {
        val requests = PhotoAuthRequests()
        val owner = Any()
        val old = requests.begin(owner, 4)!!
        val late = old.callback()
        val replacement = requests.begin(owner, 5)!!
        assertFalse(late())
        assertFalse(requests.current(old, owner))
        assertTrue(requests.current(replacement, owner))
        assertFalse(old.credentialSaved(4, 5))
    }

    @Test fun credentialSetupCanOnlyAdvanceItsOwnGeneration() {
        val requests = PhotoAuthRequests()
        val owner = Any()
        val request = requests.begin(owner, 2)!!
        assertFalse(request.credentialSaved(1, 3))
        assertTrue(request.credentialSaved(2, 3))
        assertEquals(3L, request.generation)
        requests.cancelOwner(owner)
        assertFalse(request.credentialSaved(3, 4))
    }

    @Test fun bindingGroupsCommitTogetherAndNeverPublishPartialGrants() {
        val favorites = PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING
        val required = mapOf("/one" to "shared", "/two" to "shared", favorites to "different")
        val previous = mapOf("/existing" to "bound")
        val transaction = PhotoBindingTransaction(required, previous)
        transaction.approve(mapOf("/one" to "shared", "/two" to "shared"))
        assertNull(transaction.complete(required, true))
        assertEquals(mapOf("/existing" to "bound"), previous)
        transaction.approve(mapOf(favorites to "different"))
        assertNull(transaction.complete(required, false))
        assertNull(transaction.complete(required + ("/new-parent" to "new-secret"), true))
        assertEquals(previous + required, transaction.complete(required, true))
    }

    @Test fun identicalPhotoSourceAndFavoritePinCanSatisfyTheWholeScope() {
        val evidence = PhotoVerifiedCredential(1, "shared")
        val identities = mapOf("/one" to (1 to "shared"), "/two" to (1 to "shared"),
            PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING to (1 to "shared"))
        val required = identities.mapValues { (_, value) -> PhotoProfileAccessSessionManager.fingerprint(value.first, value.second) }
        val transaction = PhotoBindingTransaction(required, emptyMap())
        val reusable = identities.filterValues { evidence.matches(it.first, it.second) }.keys
        transaction.approve(required.filterKeys { it in reusable })
        assertEquals(required, transaction.complete(required, true))
    }

    @Test fun previouslyBoundScopesStillNeedFreshCapabilityAndChangedLocksNeedApproval() {
        val required = mapOf("/one" to "old")
        val transaction = PhotoBindingTransaction(required, required)
        assertNotNull(transaction.complete(required, true))
        assertNull(transaction.complete(required, false))
        assertNull(transaction.complete(mapOf("/one" to "changed"), true))
        val capability = PhotoAccessCapability()
        val token = capability.create(capability.generation()) { true }!!
        assertTrue(capability.active(token)) // same capability accompanies viewer intents
        capability.revoke()
        assertFalse(capability.active(token))
    }

    @Test fun displayPreferencesDoNotRevokePhotoAccessButExactCredentialKeysDo() {
        listOf("view_type_folders", "dir_column_cnt", "scroll_horizontally", "directory_sorting",
            "photo_media_filter", "favorite_folder_sorting", "unrelated_protection_label").forEach {
            assertFalse(it, PhotoProtectionChanges.credentialsChanged(it))
        }
        listOf("photo_protect_password_hash", "photo_protect_fallback_protection_type",
            FAVORITE_PASSWORD_HASH, FAVORITE_BIOMETRIC_PRIMARY,
            org.fossify.commons.helpers.PROTECTED_FOLDER_HASH + "/root").forEach {
            assertTrue(it, PhotoProtectionChanges.credentialsChanged(it))
        }
        assertTrue(PhotoProtectionChanges.credentialsChanged(null))
        assertFalse(PhotoProtectionChanges.viewerScopeChanged("favorite_folder_sorting"))
    }

    @Test fun pickerDraftIsIndependentNormalizedAndSurvivesNavigation() {
        val original = PhotoSourceSelectionDraft(listOf("/Camera/", org.fossify.commons.helpers.FAVORITES))
        val draft = PhotoSourceSelectionDraft(original.snapshot())
        assertTrue(draft.contains("/Camera"))
        assertTrue(draft.contains("/Camera/"))
        draft.toggle("/Camera/")
        draft.toggle("/Other")
        draft.toggle(org.fossify.commons.helpers.FAVORITES)
        draft.toggle(org.fossify.commons.helpers.FAVORITES)
        assertEquals(setOf("/Other", org.fossify.commons.helpers.FAVORITES), draft.snapshot())
        assertEquals(setOf("/Camera", org.fossify.commons.helpers.FAVORITES), original.snapshot())
        assertFalse(PhotoSourceSelectionDraft(emptyList()).contains("/Camera"))
    }

    @Test fun thumbnailPolicyProtectsAncestorsCanonicalTargetsAndKeepsUrisAsUris() {
        assertTrue(photoSourceThumbnailProtected("/Private/Child", "", { it == "/Private" }))
        assertTrue(photoSourceThumbnailProtected("/Public", "/Private/image.jpg", { it == "/Private" }))
        assertFalse(photoSourceThumbnailProtected("/Public", "content://media/external/images/5", { false }))
        val root = java.nio.file.Files.createTempDirectory("picker-boundary")
        try {
            val privateFolder = java.nio.file.Files.createDirectory(root.resolve("private"))
            val alias = java.nio.file.Files.createSymbolicLink(root.resolve("public"), privateFolder)
            assertTrue(photoSourceThumbnailProtected(alias.toString(), "", { it == privateFolder.toString() }))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun sourceGridColumnsRespondOnlyToAvailableWidth() {
        assertEquals(2, photoSourceColumnCount(280))
        assertEquals(2, photoSourceColumnCount(420))
        assertEquals(3, photoSourceColumnCount(520))
        assertEquals(4, photoSourceColumnCount(1200))
    }

}
