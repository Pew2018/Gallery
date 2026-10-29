package org.fossify.gallery.helpers

import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.fossify.commons.extensions.getSharedPrefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FavoriteCredentialModeTest {
    @Test fun enabledModeAndCredentialConfigurationAreIndependentStates() {
        val configuredButOff = Config.FavoriteCredentialSnapshot(
            enabled = false,
            hash = "test-pin-hash",
            type = PROTECTION_PIN,
            fallbackHash = "",
            fallbackType = PROTECTION_PATTERN
        )
        assertFalse(configuredButOff.enabled)
        assertTrue(configuredButOff.isConfigured)

        val enabledButInvalid = Config.FavoriteCredentialSnapshot(
            enabled = true,
            hash = "",
            type = PROTECTION_PIN,
            fallbackHash = "",
            fallbackType = PROTECTION_PATTERN
        )
        assertTrue(enabledButInvalid.enabled)
        assertFalse(enabledButInvalid.isConfigured)
    }

    @Test fun biometricModeSurvivesPreferenceWriteAndReadWithFallbackStoredSeparately() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPrefs()
        fun clearFavoriteKeys() {
            prefs.edit()
                .remove(FAVORITE_PASSWORD_ENABLED)
                .remove(FAVORITE_PASSWORD_HASH)
                .remove(FAVORITE_PROTECTION_TYPE)
                .remove(FAVORITE_FALLBACK_PASSWORD_HASH)
                .remove(FAVORITE_FALLBACK_PROTECTION_TYPE)
                .commit()
        }
        clearFavoriteKeys()
        try {
            val saved = Config.newInstance(context)
                .saveFavoriteBiometricCredential("fallback-hash", PROTECTION_PIN)
            assertNotNull(saved)

            val reloaded = Config.newInstance(context).favoriteCredentialSnapshot()
            assertTrue(reloaded.enabled)
            assertTrue(reloaded.isConfigured)
            assertEquals(PROTECTION_FINGERPRINT, reloaded.type)
            assertEquals("", reloaded.hash)
            assertEquals(PROTECTION_PIN, reloaded.fallbackType)
            assertEquals("fallback-hash", reloaded.fallbackHash)
            assertTrue(Config.newInstance(context).isFavoriteCredentialCurrent(reloaded))
        } finally {
            clearFavoriteKeys()
        }
    }

    @Test fun biometricCredentialRequiresSeparateFallbackWithoutChangingMainType() {
        val biometric = Config.FavoriteCredentialSnapshot(
            enabled = true,
            hash = "",
            type = PROTECTION_FINGERPRINT,
            fallbackHash = "test-fallback-hash",
            fallbackType = PROTECTION_PATTERN
        )
        assertTrue(biometric.enabled)
        assertTrue(biometric.isConfigured)
        assertTrue(biometric.fallbackHash.isNotBlank())
        assertTrue(biometric.fallbackType == PROTECTION_PATTERN)
        assertTrue(biometric.type == PROTECTION_FINGERPRINT)
    }
}
