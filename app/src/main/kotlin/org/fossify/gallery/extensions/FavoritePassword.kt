package org.fossify.gallery.extensions

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import java.util.WeakHashMap
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.extensions.isBiometricAuthSupported
import org.fossify.commons.extensions.performSecurityCheck
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.FavoriteAccessSessionManager

private object FavoritePasswordSetupRequests {
    private val active = WeakHashMap<Activity, Boolean>()

    fun begin(activity: Activity): Boolean = synchronized(active) {
        if (active[activity] == true) false else {
            active[activity] = true
            true
        }
    }

    fun finish(activity: Activity) = synchronized(active) { active.remove(activity) }
}

/** Setup mode is deliberately delegated to Commons: PIN and pattern are confirmed there. */
internal fun Activity.ensureFavoritePasswordConfigured(onReady: (Config.FavoriteCredentialSnapshot?) -> Unit) {
    if (isFinishing || isDestroyed) return
    if (config.isFavoritePasswordProtectionOn) {
        onReady(config.favoriteCredentialSnapshot())
        return
    }

    if (!FavoritePasswordSetupRequests.begin(this)) {
        onReady(null)
        return
    }
    configureFavoritePassword { ready ->
        FavoritePasswordSetupRequests.finish(this)
        onReady(ready)
    }
}

/** Explicit setup/replacement. Persistence happens only after Commons confirms the credential. */
internal fun Activity.configureFavoritePassword(onReady: (Config.FavoriteCredentialSnapshot?) -> Unit) {
    if (isFinishing || isDestroyed) return
    SecurityDialog(this, "", SHOW_ALL_TABS, callback = setupResult@{ hash, type, success ->
        if (isFinishing || isDestroyed) return@setupResult
        if (!success) {
            onReady(null)
            return@setupResult
        }
        if (type == PROTECTION_FINGERPRINT) {
            // A biometric-only lock can become unusable after the user removes
            // enrolled biometrics. Require a separately confirmed PIN/pattern fallback.
            AlertDialog.Builder(this)
                .setTitle(org.fossify.gallery.R.string.favorite_password_title)
                .setMessage(org.fossify.gallery.R.string.favorite_password_fallback_setup)
                .setItems(arrayOf(getString(org.fossify.commons.R.string.pattern), getString(org.fossify.commons.R.string.pin))) { _, which ->
                    val fallbackType = if (which == 0) PROTECTION_PATTERN else PROTECTION_PIN
                    SecurityDialog(this, "", fallbackType, callback = fallbackResult@{ fallbackHash, confirmedType, fallbackSuccess ->
                        if (isFinishing || isDestroyed) return@fallbackResult
                        if (!fallbackSuccess || fallbackHash.isBlank() || confirmedType != fallbackType) {
                            onReady(null)
                            return@fallbackResult
                        }
                        config.saveFavoriteCredential(hash, type, fallbackHash, confirmedType)
                        FavoriteAccessSessionManager.revokeAll()
                        val credential = config.favoriteCredentialSnapshot()
                        onReady(credential.takeIf(config::isFavoriteCredentialCurrent))
                    })
                }
                .setOnCancelListener { onReady(null) }
                .show()
        } else {
            if (hash.isBlank() || type !in 0..1) {
                onReady(null)
                return@setupResult
            }
            config.saveFavoriteCredential(hash, type)
            FavoriteAccessSessionManager.revokeAll()
            val credential = config.favoriteCredentialSnapshot()
            onReady(credential.takeIf(config::isFavoriteCredentialCurrent))
        }
    })
}

/** A successful setup also satisfies the first entry; later entries verify the saved credential. */
internal fun Activity.authenticateFavoritePassword(onAuthenticated: (Config.FavoriteCredentialProof?) -> Unit) {
    if (isFinishing || isDestroyed) return
    if (!config.isFavoritePasswordProtectionOn) {
        ensureFavoritePasswordConfigured { credential ->
            onAuthenticated(credential?.let {
                Config.FavoriteCredentialProof(it, FavoriteAccessSessionManager.currentGeneration())
            })
        }
        return
    }

    val expectedCredential = config.favoriteCredentialSnapshot()
    val expectedGeneration = FavoriteAccessSessionManager.currentGeneration()
    fun complete(success: Boolean) {
        if (isFinishing || isDestroyed) return
        val valid = success && FavoriteAccessSessionManager.currentGeneration() == expectedGeneration &&
            config.isFavoriteCredentialCurrent(expectedCredential)
        onAuthenticated(expectedCredential.takeIf { valid }?.let {
            Config.FavoriteCredentialProof(it, expectedGeneration)
        })
    }

    if (config.favoriteProtectionType != PROTECTION_FINGERPRINT) {
        SecurityDialog(this, expectedCredential.hash, expectedCredential.type) { _, _, success ->
            complete(success)
        }
        return
    }

    val authenticateFallback: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            SecurityDialog(this, expectedCredential.fallbackHash, expectedCredential.fallbackType) { _, _, success ->
                complete(success)
            }
        }
    }
    if (!isBiometricAuthSupported()) {
        authenticateFallback()
    } else {
        AlertDialog.Builder(this)
            .setTitle(org.fossify.gallery.R.string.favorite_password_title)
            .setMessage(org.fossify.gallery.R.string.favorite_password_auth_choice)
            .setPositiveButton(org.fossify.gallery.R.string.favorite_password_use_biometric) { _, _ ->
                if (org.fossify.commons.helpers.isRPlus()) {
                    performSecurityCheck(
                        PROTECTION_FINGERPRINT,
                        "",
                        successCallback = { _, _ -> complete(true) },
                        failureCallback = { complete(false) }
                    )
                } else {
                    SecurityDialog(this, "", PROTECTION_FINGERPRINT) { _, _, success ->
                        complete(success)
                    }
                }
            }
            .setNegativeButton(org.fossify.gallery.R.string.favorite_password_use_fallback) { _, _ -> authenticateFallback() }
            .setOnCancelListener { complete(false) }
            .show()
    }
}
