package org.fossify.gallery.extensions

import android.app.Activity
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.extensions.isBiometricAuthSupported
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.OxygenClassicDialog

internal fun Activity.showPhotoPasswordDialog(@StringRes title: Int, build: AlertDialog.Builder.() -> Unit): AlertDialog =
    OxygenClassicDialog.show(this, OxygenClassicDialog.builder(this, getString(title)).apply(build))

internal fun Activity.authenticatePhotoPassword(onAuthenticated: (Config.PhotoCredentialProof?) -> Unit) =
    authenticatePreparedPhotoPassword(onAuthenticated)

private object PhotoPasswordSetupRequests {
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
internal fun Activity.ensurePhotoPasswordConfigured(onReady: (Config.PhotoCredentialSnapshot?) -> Unit) {
    if (isFinishing || isDestroyed) return
    val existing = config.photoCredentialSnapshot()
    if (config.isPhotoPasswordProtectionOn) {
        onReady(existing.takeIf { config.isPhotoCredentialCurrent(it) })
        return
    }

    if (!PhotoPasswordSetupRequests.begin(this)) {
        onReady(null)
        return
    }
    configurePhotoPassword { ready ->
        PhotoPasswordSetupRequests.finish(this)
        onReady(ready)
    }
}

/** Let the user choose the primary mode explicitly; fallback setup cannot change it. */
internal fun Activity.configurePhotoPassword(onReady: (Config.PhotoCredentialSnapshot?) -> Unit) {
    if (isFinishing || isDestroyed) return

    val modes = mutableListOf(PROTECTION_PATTERN, PROTECTION_PIN)
    val labels = mutableListOf(
        getString(org.fossify.commons.R.string.pattern),
        getString(org.fossify.commons.R.string.pin)
    )
    if (isBiometricAuthSupported()) {
        modes.add(PROTECTION_FINGERPRINT)
        labels.add(getString(R.string.photo_password_biometric))
    }

    showPhotoPasswordDialog(R.string.photo_password_title) {
        setItems(labels.toTypedArray()) { _, index ->
            val type = modes.getOrNull(index) ?: run {
                onReady(null)
                return@setItems
            }
            when (type) {
                PROTECTION_PATTERN, PROTECTION_PIN -> configurePhotoPasswordType(type, onReady)
                PROTECTION_FINGERPRINT -> configurePhotoBiometricPassword(onReady)
                else -> onReady(null)
            }
        }
        setOnCancelListener { onReady(null) }
    }
}

private fun Activity.configurePhotoPasswordType(
    type: Int,
    onReady: (Config.PhotoCredentialSnapshot?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    SecurityDialog(this, "", type) { hash, confirmedType, success ->
        if (isFinishing || isDestroyed) return@SecurityDialog
        if (!success || hash.isBlank() || confirmedType != type ||
            config.photoCredentialSnapshot() != expectedCredential) {
            onReady(null)
            return@SecurityDialog
        }
        config.savePhotoCredential(hash, type)
        PhotoProfileAccessSessionManager.revokeAll()
        val credential = config.photoCredentialSnapshot()
        onReady(credential.takeIf {
            it.type == type && config.isPhotoCredentialCurrent(it)
        })
    }
}

private fun Activity.configurePhotoBiometricPassword(
    onReady: (Config.PhotoCredentialSnapshot?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    val host = this as? FragmentActivity
    if (host == null || !isBiometricAuthSupported()) {
        onReady(null)
        return
    }

    val prompt = BiometricPrompt(
        host,
        ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (isFinishing || isDestroyed) return
                choosePhotoFallbackCredential(expectedCredential, onReady)
            }

            override fun onAuthenticationFailed() = Unit

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!isFinishing && !isDestroyed) {
                    toast(errString.toString())
                    onReady(null)
                }
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

private fun Activity.choosePhotoFallbackCredential(
    expectedCredential: Config.PhotoCredentialSnapshot,
    onReady: (Config.PhotoCredentialSnapshot?) -> Unit
) {
    if (isFinishing || isDestroyed) return
    fun configureFallback(fallbackType: Int) {
        SecurityDialog(this, "", fallbackType) { fallbackHash, confirmedType, success ->
            if (isFinishing || isDestroyed) return@SecurityDialog
            if (!success || fallbackHash.isBlank() || confirmedType != fallbackType ||
                config.photoCredentialSnapshot() != expectedCredential) {
                onReady(null)
                return@SecurityDialog
            }
            val credential = config.savePhotoBiometricCredential(fallbackHash, confirmedType)
            if (credential != null) PhotoProfileAccessSessionManager.revokeAll()
            onReady(credential)
        }
    }

    showPhotoPasswordDialog(R.string.photo_password_title) {
        setMessage(R.string.photo_password_fallback_setup)
        // AlertDialog displays its message instead of its items list. Use buttons
        // so both recovery choices remain reachable while showing the explanation.
        setPositiveButton(org.fossify.commons.R.string.pin) { _, _ ->
            configureFallback(PROTECTION_PIN)
        }
        setNeutralButton(org.fossify.commons.R.string.pattern) { _, _ ->
            configureFallback(PROTECTION_PATTERN)
        }
        setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> onReady(null) }
        setOnCancelListener { onReady(null) }
    }
}

private fun Activity.authenticatePreparedPhotoPassword(onAuthenticated: (Config.PhotoCredentialProof?) -> Unit) {
    if (isFinishing || isDestroyed) return
    if (!config.isPhotoPasswordProtectionOn) {
        onAuthenticated(null)
        return
    }

    val expectedCredential = config.photoCredentialSnapshot()
    if (!config.isPhotoCredentialCurrent(expectedCredential)) {
        onAuthenticated(null)
        return
    }
    val expectedGeneration = PhotoProfileAccessSessionManager.currentGeneration()
    fun complete(success: Boolean) {
        if (isFinishing || isDestroyed) return
        val valid = success && PhotoProfileAccessSessionManager.currentGeneration() == expectedGeneration &&
            config.isPhotoCredentialCurrent(expectedCredential)
        onAuthenticated(expectedCredential.takeIf { valid }?.let {
            Config.PhotoCredentialProof(it, expectedGeneration)
        })
    }

    if (!expectedCredential.biometricPrimary) {
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
        showPhotoPasswordDialog(R.string.photo_password_title) {
            setMessage(R.string.photo_password_auth_choice)
            setPositiveButton(R.string.photo_password_use_fallback) { _, _ -> authenticateFallback() }
            setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> complete(false) }
            setOnCancelListener { complete(false) }
        }
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
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> authenticateFallback()
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> complete(false)
                    else -> askForFallback()
                }
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(org.fossify.commons.R.string.authenticate))
        .setNegativeButtonText(getString(R.string.photo_password_use_fallback))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(promptInfo)
}


/** Bind original lock identities only after their owners have authenticated them. */
internal fun Activity.unlockPhotoProfile(profileId: String?, completed: () -> Unit) {
    val profile = org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profileId } ?: return
    fun bind(proof: Config.PhotoCredentialProof) {
        val expectedCredential = config.photoCredentialSnapshot()
        ensureBackgroundThread {
            val locks = runCatching { PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile) }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || locks == null || config.photoCredentialSnapshot() != expectedCredential) return@runOnUiThread
                val pending = locks.filter { (root, lock) ->
                    config.photoSourceBindings[root] != PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                }.entries.toList()
                val approved = mutableMapOf<String, String>()
                fun finishBinding() {
                    if (isFinishing || isDestroyed ||
                        org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profile.id } != profile ||
                        !config.isPhotoCredentialCurrent(expectedCredential) ||
                        proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration()) return
                    // Recheck every challenged identity before persisting the explicit relationship.
                    if (locks.any { (root, lock) -> applicationContext.favoriteProtection(root) != lock }) return
                    config.photoSourceBindings = config.photoSourceBindings + approved
                    PhotoProfileAccessSessionManager.create(applicationContext, proof)
                    completed()
                }
                fun bindFavorites() {
                    if (profile.sources.none { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } ||
                        !config.isFavoritePasswordProtectionOn ||
                        config.photoSourceBindings[FAVORITES] == PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)) {
                        finishBinding(); return
                    }
                    authenticateFavoritePassword { favoriteProof ->
                        if (favoriteProof != null && config.isFavoriteCredentialCurrent(favoriteProof.credential)) {
                            approved[FAVORITES] = PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)
                            finishBinding()
                        }
                    }
                }
                fun next(index: Int) {
                    if (isFinishing || isDestroyed) return
                    if (index == pending.size) { bindFavorites(); return }
                    val (root, lock) = pending[index]
                    if (lock.type !in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_FINGERPRINT) ||
                        (lock.type != PROTECTION_FINGERPRINT && lock.hash.isBlank())) return
                    showPhotoPasswordDialog(R.string.photo_password_bind_title) {
                        setMessage(getString(R.string.photo_password_bind_message, root))
                        setNegativeButton(org.fossify.commons.R.string.cancel, null)
                        setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                            SecurityDialog(this@unlockPhotoProfile, lock.hash, lock.type) { _, _, success ->
                                if (success) {
                                    approved[root] = PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                                    next(index + 1)
                                }
                            }
                        }
                    }
                }
                next(0)
            }
        }
    }
    if (config.photoCredentialSnapshot().enabled) authenticatePhotoPassword { it?.let(::bind) }
    else configurePhotoPassword { credential ->
        if (credential != null) authenticatePhotoPassword { it?.let(::bind) }
    }
}
