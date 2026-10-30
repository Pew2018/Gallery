package org.fossify.gallery.extensions

import android.app.Activity
import android.view.Gravity
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getProperPrimaryColor
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

private object PhotoUnlockCoordinator {
    enum class Stage { SETUP_CREDENTIAL, VERIFY_PHOTO_PASSWORD, BIND_SOURCES, LOAD_CONTENT }
    private val active = WeakHashMap<Activity, Stage>()
    fun begin(activity: Activity): Boolean = synchronized(active) {
        if (active.containsKey(activity)) false else {
            active[activity] = Stage.SETUP_CREDENTIAL
            Log.d("PhotoProfileAuth", "stage=SETUP_CREDENTIAL")
            true
        }
    }
    fun stage(activity: Activity, stage: Stage) = synchronized(active) {
        if (active.containsKey(activity)) {
            active[activity] = stage
            Log.d("PhotoProfileAuth", "stage=$stage")
        }
    }
    fun finish(activity: Activity, reason: String) = synchronized(active) {
        if (active.remove(activity) != null) Log.d("PhotoProfileAuth", "finish=$reason")
    }
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
    configurePhotoPasswordWithProof { ready, _ ->
        PhotoPasswordSetupRequests.finish(this)
        onReady(ready)
    }
}

/** Let the user choose the primary mode explicitly; fallback setup cannot change it. */
internal fun Activity.configurePhotoPassword(onReady: (Config.PhotoCredentialSnapshot?) -> Unit) {
    if (isFinishing || isDestroyed) return
    if (!PhotoPasswordSetupRequests.begin(this)) {
        onReady(null)
        return
    }
    configurePhotoPasswordWithProof { credential, _ ->
        PhotoPasswordSetupRequests.finish(this)
        onReady(credential)
    }
}

/** Returns a setup proof only to the flow that performed and verified the setup itself. */
private fun Activity.configurePhotoPasswordWithProof(
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
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
                onReady(null, null)
                return@setItems
            }
            when (type) {
                PROTECTION_PATTERN, PROTECTION_PIN -> configurePhotoPasswordType(type, onReady)
                PROTECTION_FINGERPRINT -> configurePhotoBiometricPassword(onReady)
                else -> onReady(null, null)
            }
        }
        setOnCancelListener { onReady(null, null) }
    }
}

private fun Activity.configurePhotoPasswordType(
    type: Int,
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    SecurityDialog(this, "", type) { hash, confirmedType, success ->
        if (isFinishing || isDestroyed) return@SecurityDialog
        if (!success || hash.isBlank() || confirmedType != type ||
            config.photoCredentialSnapshot() != expectedCredential) {
            onReady(null, null)
            return@SecurityDialog
        }
        config.savePhotoCredential(hash, type)
        PhotoProfileAccessSessionManager.revokeAll("photo_credential_changed")
        val credential = config.photoCredentialSnapshot()
        val saved = credential.takeIf {
            it.type == type && config.isPhotoCredentialCurrent(it)
        }
        val proof = saved?.let { Config.PhotoCredentialProof(it, PhotoProfileAccessSessionManager.currentGeneration()) }
        onReady(saved, proof)
    }
}

private fun Activity.configurePhotoBiometricPassword(
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    val host = this as? FragmentActivity
    if (host == null || !isBiometricAuthSupported()) {
        onReady(null, null)
        return
    }

    val prompt = BiometricPrompt(
        host,
        ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (isFinishing || isDestroyed) return
                choosePhotoFallbackCredential(expectedCredential) { saved ->
                    if (saved != null && config.isPhotoCredentialCurrent(saved)) {
                        onReady(saved, Config.PhotoCredentialProof(saved, PhotoProfileAccessSessionManager.currentGeneration()))
                    } else onReady(null, null)
                }
            }

            override fun onAuthenticationFailed() = Unit

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!isFinishing && !isDestroyed) {
                    toast(errString.toString())
                    onReady(null, null)
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
    val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
    fun finish(credential: Config.PhotoCredentialSnapshot?) {
        if (delivered.compareAndSet(false, true) && !isFinishing && !isDestroyed) onReady(credential)
    }
    fun configureFallback(fallbackType: Int) {
        SecurityDialog(this, "", fallbackType) { fallbackHash, confirmedType, success ->
            if (isFinishing || isDestroyed) return@SecurityDialog
            if (!success || fallbackHash.isBlank() || confirmedType != fallbackType ||
                config.photoCredentialSnapshot() != expectedCredential) {
                finish(null)
                return@SecurityDialog
            }
            val credential = config.savePhotoBiometricCredential(fallbackHash, confirmedType)
            if (credential != null) PhotoProfileAccessSessionManager.revokeAll("photo_fallback_changed")
            finish(credential)
        }
    }
    val context = OxygenClassicDialog.builder(this, "").context
    val density = resources.displayMetrics.density
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), (8 * density).toInt())
    }
    body.addView(TextView(context).apply {
        setText(R.string.photo_password_fallback_description)
        textSize = 14f
        setTextColor(getProperTextColor())
        setPadding(0, 0, 0, (8 * density).toInt())
    })
    lateinit var dialog: AlertDialog
    fun addChoice(label: Int, type: Int) {
        body.addView(TextView(context).apply {
            setText(label)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (48 * density).toInt()
            setTextColor(getProperTextColor())
            background = OxygenClassicDialog.ripple(getProperPrimaryColor(), density)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (delivered.get()) return@setOnClickListener
                dialog.dismiss()
                configureFallback(type)
            }
        })
    }
    addChoice(R.string.photo_password_choose_pin, PROTECTION_PIN)
    addChoice(R.string.photo_password_choose_pattern, PROTECTION_PATTERN)
    dialog = OxygenClassicDialog.show(this, OxygenClassicDialog.builder(this, getString(R.string.photo_password_fallback_title))
        .setView(body)
        .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> finish(null) })
    dialog.setOnCancelListener { finish(null) }
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
    if (isFinishing || isDestroyed || !PhotoUnlockCoordinator.begin(this)) return
    val delivered = AtomicBoolean(false)
    fun finish(reason: String) {
        if (delivered.compareAndSet(false, true)) {
            PhotoUnlockCoordinator.stage(this, PhotoUnlockCoordinator.Stage.LOAD_CONTENT)
            PhotoUnlockCoordinator.finish(this, reason)
            if (!isFinishing && !isDestroyed) completed()
        }
    }
    val profile = org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profileId }
    if (profile == null) { finish("profile_missing"); return }
    fun profileStillCurrent(): Boolean =
        org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profile.id } == profile

    fun bind(proof: Config.PhotoCredentialProof) {
        if (delivered.get()) return
        PhotoUnlockCoordinator.stage(this, PhotoUnlockCoordinator.Stage.BIND_SOURCES)
        val expectedCredential = proof.credential
        ensureBackgroundThread {
            val locks = runCatching { PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile) }.getOrNull()
            runOnUiThread {
                if (delivered.get() || isFinishing || isDestroyed || locks == null || !profileStillCurrent() ||
                    config.photoCredentialSnapshot() != expectedCredential ||
                    proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration()) {
                    finish("stale_before_binding"); return@runOnUiThread
                }
                val pending = locks.filter { (root, lock) ->
                    config.photoSourceBindings[root] != PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                }.entries.groupBy { it.value.type to it.value.hash }.values.toList()
                val approved = mutableMapOf<String, String>()
                fun finishBinding() {
                    if (delivered.get() || isFinishing || isDestroyed || !profileStillCurrent() ||
                        !config.isPhotoCredentialCurrent(expectedCredential) ||
                        proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration()) {
                        finish("stale_after_binding_check"); return
                    }
                    if (locks.any { (root, lock) -> applicationContext.favoriteProtection(root) != lock }) {
                        finish("protection_changed_during_binding"); return
                    }
                    val previousBindings = config.photoSourceBindings
                    val committedBindings = previousBindings + approved
                    config.photoSourceBindings = committedBindings
                    val token = PhotoProfileAccessSessionManager.create(applicationContext, proof)
                    if (token == null && config.photoSourceBindings == committedBindings) {
                        config.photoSourceBindings = previousBindings
                    }
                    finish(if (token == null) "proof_rejected" else "authorized")
                }
                fun bindFavorites() {
                    if (profile.sources.none { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } ||
                        !config.isFavoritePasswordProtectionOn ||
                        config.photoSourceBindings[FAVORITES] == PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)) {
                        finishBinding(); return
                    }
                    val favoriteCredential = config.favoriteCredentialSnapshot()
                    val sameCredentialWasVerified = !favoriteCredential.biometricPrimary &&
                        favoriteCredential.type in listOf(PROTECTION_PATTERN, PROTECTION_PIN) &&
                        favoriteCredential.isConfigured && pending.any { group ->
                            group.first().value.type == favoriteCredential.type && group.first().value.hash == favoriteCredential.hash &&
                                group.all { (root, lock) -> approved[root] == PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash) }
                        }
                    if (sameCredentialWasVerified && config.isFavoriteCredentialCurrent(favoriteCredential)) {
                        approved[FAVORITES] = PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)
                        finishBinding(); return
                    }
                    authenticateFavoritePassword { favoriteProof ->
                        if (delivered.get()) return@authenticateFavoritePassword
                        if (favoriteProof != null && config.isFavoriteCredentialCurrent(favoriteProof.credential)) {
                            approved[FAVORITES] = PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)
                            finishBinding()
                        } else finish("favorite_auth_cancelled_or_failed")
                    }
                }
                fun next(index: Int) {
                    if (delivered.get() || isFinishing || isDestroyed) return
                    if (index == pending.size) { bindFavorites(); return }
                    val group = pending[index]
                    val type = group.first().value.type
                    val hash = group.first().value.hash
                    if (type !in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_FINGERPRINT) ||
                        (type != PROTECTION_FINGERPRINT && hash.isBlank())) {
                        finish("unsupported_source_protection"); return
                    }
                    showPhotoPasswordDialog(R.string.photo_password_bind_title) {
                        setMessage(getString(R.string.photo_password_bind_group_message, group.size))
                        setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> finish("binding_cancelled") }
                        setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                            SecurityDialog(this@unlockPhotoProfile, hash, type) { _, _, success ->
                                if (delivered.get()) return@SecurityDialog
                                if (success && config.isPhotoCredentialCurrent(expectedCredential) &&
                                    proof.sessionGeneration == PhotoProfileAccessSessionManager.currentGeneration() &&
                                    group.all { applicationContext.favoriteProtection(it.key) == it.value }) {
                                    group.forEach { (root, lock) ->
                                        approved[root] = PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                                    }
                                    next(index + 1)
                                } else finish("source_auth_failed_or_changed")
                            }
                        }
                        setOnCancelListener { finish("binding_cancelled") }
                    }
                }
                next(0)
            }
        }
    }

    fun authenticate() {
        if (delivered.get()) return
        if (config.photoCredentialSnapshot().enabled) {
            PhotoUnlockCoordinator.stage(this, PhotoUnlockCoordinator.Stage.VERIFY_PHOTO_PASSWORD)
            authenticatePhotoPassword { proof ->
                if (proof != null) bind(proof) else finish("photo_auth_cancelled_or_failed")
            }
        } else {
            PhotoUnlockCoordinator.stage(this, PhotoUnlockCoordinator.Stage.SETUP_CREDENTIAL)
            configurePhotoPasswordWithProof { credential, proof ->
                if (credential != null && proof != null && config.isPhotoCredentialCurrent(credential)) bind(proof)
                else finish("credential_setup_cancelled_or_failed")
            }
        }
    }

    val existingToken = PhotoProfileAccessSessionManager.currentToken(applicationContext)
    if (existingToken != null) {
        ensureBackgroundThread {
            val locks = runCatching { PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile) }.getOrNull()
            runOnUiThread {
                if (delivered.get()) return@runOnUiThread
                val valid = locks != null && profileStillCurrent() &&
                    PhotoProfileAccessSessionManager.bindingsAuthorize(locks, config.photoSourceBindings,
                        PhotoProfileAccessSessionManager.isActive(applicationContext, existingToken)) &&
                    (!profile.sources.any { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } ||
                        PhotoProfileAccessSessionManager.favoritesAuthorized(applicationContext, existingToken))
                if (valid) finish("active_session_reused") else authenticate()
            }
        }
    } else authenticate()

}
