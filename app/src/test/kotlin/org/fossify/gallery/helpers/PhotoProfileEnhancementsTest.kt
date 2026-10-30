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
}
