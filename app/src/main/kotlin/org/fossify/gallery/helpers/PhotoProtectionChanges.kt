package org.fossify.gallery.helpers

import org.fossify.commons.helpers.PROTECTED_FOLDER_HASH
import org.fossify.commons.helpers.PROTECTED_FOLDER_TYPE

internal object PhotoProtectionChanges {
    private val credentials = setOf(
        "photo_protect_password_enabled", "photo_protect_password_hash", "photo_protect_protection_type",
        "photo_protect_biometric_primary", "photo_protect_fallback_password_hash", "photo_protect_fallback_protection_type",
        FAVORITE_PASSWORD_ENABLED, FAVORITE_PASSWORD_HASH, FAVORITE_PROTECTION_TYPE, FAVORITE_BIOMETRIC_PRIMARY,
        FAVORITE_FALLBACK_PASSWORD_HASH, FAVORITE_FALLBACK_PROTECTION_TYPE
    )
    fun credentialsChanged(key: String?): Boolean = key == null || key in credentials ||
        key.startsWith(PROTECTED_FOLDER_HASH) || key.startsWith(PROTECTED_FOLDER_TYPE)
    fun viewerScopeChanged(key: String?): Boolean = credentialsChanged(key) ||
        key == PhotoProfileRepository.KEY || key == "photo_source_bindings" ||
        key == EXCLUDED_FOLDERS || key == SHOW_HIDDEN_MEDIA
}
