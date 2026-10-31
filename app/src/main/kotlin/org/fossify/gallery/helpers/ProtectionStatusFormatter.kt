package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.gallery.R

/** Display-only credential state. Credential hashes, proofs and tokens are never accepted. */
internal data class ProtectionStatusPresentation(
    val enabled: Boolean,
    val configured: Boolean,
    val biometricPrimary: Boolean,
    val primaryType: Int,
    val fallbackType: Int,
    val legacyProtection: Boolean = false
)

internal object ProtectionStatusFormatter {
    fun format(context: Context, state: ProtectionStatusPresentation): String {
        if (!state.enabled) {
            return context.getString(
                if (state.legacyProtection) R.string.favorite_password_legacy_status
                else R.string.protection_status_off
            )
        }
        if (!state.configured) return context.getString(R.string.protection_status_invalid)

        if (state.biometricPrimary) {
            return context.getString(
                R.string.protection_status_biometric_fallback,
                context.getString(typeLabel(state.fallbackType)),
            )
        }
        return context.getString(
            R.string.protection_status_primary,
            context.getString(typeLabel(state.primaryType)),
        )
    }

    private fun typeLabel(type: Int): Int = when (type) {
        PROTECTION_PIN -> R.string.protection_type_pin
        PROTECTION_PATTERN -> R.string.protection_type_pattern
        else -> R.string.protection_type_pattern
    }
}
