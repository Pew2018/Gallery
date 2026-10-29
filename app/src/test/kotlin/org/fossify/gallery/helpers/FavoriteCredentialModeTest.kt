package org.fossify.gallery.helpers

import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
