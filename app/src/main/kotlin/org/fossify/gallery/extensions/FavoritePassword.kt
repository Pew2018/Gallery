package org.fossify.gallery.extensions

import android.app.Activity
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.ensureBackgroundThread
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.isBiometricAuthSupported
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
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
    val existing = config.favoriteCredentialSnapshot()
    if (config.isFavoritePasswordProtectionOn) {
        onReady(existing.takeIf { config.isFavoriteCredentialCurrent(it) })
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
            getAlertDialogBuilder()
                .setTitle(R.string.favorite_password_title)
                .setMessage(R.string.favorite_password_fallback_setup)
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

/** Preflight all added paths and give the user a useful explanation on refusal. */
internal fun Activity.ensureFavoriteAddAllowed(
    paths: Collection<String>,
    sourceAccessCheck: (String) -> Boolean,
    onAllowed: (Boolean) -> Unit
) {
    if (isFinishing || isDestroyed) return
    ensureBackgroundThread {
        val snapshot = runCatching {
            FavoriteAccessSessionManager.captureSnapshot(paths, applicationContext::favoriteProtection)
        }.getOrNull()
        val protectedPaths = snapshot?.entries?.filterValues { it.locks.isNotEmpty() }?.keys.orEmpty()
        val allowedBySource = snapshot != null && protectedPaths.all(sourceAccessCheck)
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            when {
                snapshot == null -> {
                    toast(R.string.favorite_add_blocked_changed)
                    onAllowed(false)
                }
                protectedPaths.isNotEmpty() && !config.isFavoritePasswordProtectionOn -> {
                    getAlertDialogBuilder()
                        .setTitle(R.string.favorite_password_title)
                        .setMessage(R.string.favorite_add_protected_source)
                        .setPositiveButton(R.string.favorite_password_enable) { _, _ ->
                            startActivity(Intent(this, SettingsActivity::class.java))
                        }
                        .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> onAllowed(false) }
                        .setOnCancelListener { onAllowed(false) }
                        .show()
                }
                !allowedBySource -> {
                    toast(R.string.favorite_add_source_locked)
                    onAllowed(false)
                }
                config.isFavoritePasswordProtectionOn && !config.favoriteCredentialSnapshot().isConfigured -> {
                    toast(R.string.favorite_password_invalid)
                    onAllowed(false)
                }
                else -> onAllowed(true)
            }
        }
    }
}

/** Verify the enabled virtual-album credential; disabled mode never opens setup UI here. */
internal fun Activity.authenticateFavoritePassword(onAuthenticated: (Config.FavoriteCredentialProof?) -> Unit) {
    if (isFinishing || isDestroyed) return
    if (!config.isFavoritePasswordProtectionOn) {
        onAuthenticated(null)
        return
    }

    val expectedCredential = config.favoriteCredentialSnapshot()
    if (!config.isFavoriteCredentialCurrent(expectedCredential)) {
        onAuthenticated(null)
        return
    }
    val expectedGeneration = FavoriteAccessSessionManager.currentGeneration()
    fun complete(success: Boolean) {
        if (isFinishing || isDestroyed) return
        val valid = success && FavoriteAccessSessionManager.currentGeneration() == expectedGeneration &&
            config.isFavoriteCredentialCurrent(expectedCredential)
        onAuthenticated(expectedCredential.takeIf { valid }?.let {
            Config.FavoriteCredentialProof(it, expectedGeneration)
        })
    }

    if (expectedCredential.type != PROTECTION_FINGERPRINT) {
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
    val askForFallback: () -> Unit = fallback@{
        if (isFinishing || isDestroyed) return@fallback
        getAlertDialogBuilder()
            .setTitle(R.string.favorite_password_title)
            .setMessage(R.string.favorite_password_auth_choice)
            .setPositiveButton(R.string.favorite_password_use_fallback) { _, _ -> authenticateFallback() }
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> complete(false) }
            .setOnCancelListener { complete(false) }
            .show()
    }

    if (!isBiometricAuthSupported()) {
        askForFallback()
        return
    }
    val host = this as? FragmentActivity
    if (host == null) {
        askForFallback()
        return
    }

    // Android reports retryable mismatches separately from terminal errors. Keep the
    // system prompt open for retries and ask about the fallback only after it closes.
    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                complete(true)
            }

            override fun onAuthenticationFailed() = Unit

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                askForFallback()
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(org.fossify.commons.R.string.authenticate))
        .setNegativeButtonText(getString(org.fossify.commons.R.string.cancel))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(promptInfo)
}
