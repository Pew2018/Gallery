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
import org.fossify.gallery.helpers.PhotoAuthentication
import org.fossify.gallery.helpers.PhotoAuthRequests
import org.fossify.gallery.helpers.PhotoVerifiedCredential
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
import org.fossify.gallery.R
import org.fossify.gallery.helpers.PhotoBindingTransaction
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.PhotoProfileAccessSessionManager
import org.fossify.gallery.helpers.OxygenClassicDialog

internal fun Activity.showPhotoPasswordDialog(@StringRes title: Int, build: AlertDialog.Builder.() -> Unit): AlertDialog =
    OxygenClassicDialog.show(this, OxygenClassicDialog.builder(this, getString(title)).apply(build))

/** All public photo-password entrances acquire the same process-only request lease. */
private fun Activity.photoRequest(block: (PhotoAuthRequests.Request) -> Unit) {
    if (isFinishing || isDestroyed) return
    val request = PhotoAuthentication.requests.begin(this, PhotoProfileAccessSessionManager.currentGeneration()) ?: return
    Log.d("PhotoProfileAuth", "request_started")
    block(request)
}

private fun Activity.requestCurrent(request: PhotoAuthRequests.Request): Boolean =
    !isFinishing && !isDestroyed && PhotoAuthentication.requests.current(request, this) &&
        request.generation == PhotoProfileAccessSessionManager.currentGeneration()

internal fun Activity.authenticatePhotoPassword(onAuthenticated: (Config.PhotoCredentialProof?) -> Unit) = photoRequest { request ->
    authenticatePreparedPhotoPassword(request) { proof ->
        if (!requestCurrent(request)) return@authenticatePreparedPhotoPassword
        PhotoAuthentication.requests.finish(request)
        onAuthenticated(proof)
    }
}

internal fun Activity.ensurePhotoPasswordConfigured(onReady: (Config.PhotoCredentialSnapshot?) -> Unit) {
    val existing = config.photoCredentialSnapshot()
    if (config.isPhotoCredentialCurrent(existing)) onReady(existing) else configurePhotoPassword(onReady)
}

/** Let the user choose the primary mode explicitly; fallback setup cannot change it. */
internal fun Activity.configurePhotoPassword(onReady: (Config.PhotoCredentialSnapshot?) -> Unit) = photoRequest { request ->
    configurePhotoPasswordWithProof(request) { credential, _ ->
        if (!requestCurrent(request)) return@configurePhotoPasswordWithProof
        PhotoAuthentication.requests.finish(request)
        onReady(credential)
    }
}

/** Returns a setup proof only to the flow that performed and verified the setup itself. */
private fun Activity.configurePhotoPasswordWithProof(
    request: PhotoAuthRequests.Request,
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
    if (!requestCurrent(request)) return

    val modeChoice = request.callback()
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
            if (!requestCurrent(request) || !modeChoice()) return@setItems
            val type = modes.getOrNull(index) ?: run {
                onReady(null, null)
                return@setItems
            }
            when (type) {
                PROTECTION_PATTERN, PROTECTION_PIN -> configurePhotoPasswordType(request, type, onReady)
                PROTECTION_FINGERPRINT -> configurePhotoBiometricPassword(request, onReady)
                else -> onReady(null, null)
            }
        }
        setOnCancelListener { if (requestCurrent(request) && modeChoice()) onReady(null, null) }
    }
}

private fun Activity.configurePhotoPasswordType(
    request: PhotoAuthRequests.Request,
    type: Int,
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    val setupGeneration = PhotoProfileAccessSessionManager.currentGeneration()
    val terminal = request.callback()
    SecurityDialog(this, "", type) { hash, confirmedType, success ->
        if (!requestCurrent(request) || !terminal()) return@SecurityDialog
        if (setupGeneration != PhotoProfileAccessSessionManager.currentGeneration() || !success || hash.isBlank() || confirmedType != type ||
            config.photoCredentialSnapshot() != expectedCredential) {
            onReady(null, null)
            return@SecurityDialog
        }
        config.savePhotoCredential(hash, type)
        PhotoProfileAccessSessionManager.revokeAll("photo_credential_changed")
        request.credentialSaved(setupGeneration, PhotoProfileAccessSessionManager.currentGeneration())
        val credential = config.photoCredentialSnapshot()
        val saved = credential.takeIf {
            it.type == type && config.isPhotoCredentialCurrent(it)
        }
        val proof = saved?.let { Config.PhotoCredentialProof(it, PhotoProfileAccessSessionManager.currentGeneration(), request.id, PhotoVerifiedCredential(type, hash)) }
        onReady(saved, proof)
    }
}

private fun Activity.configurePhotoBiometricPassword(
    request: PhotoAuthRequests.Request,
    onReady: (Config.PhotoCredentialSnapshot?, Config.PhotoCredentialProof?) -> Unit
) {
    val expectedCredential = config.photoCredentialSnapshot()
    val setupGeneration = PhotoProfileAccessSessionManager.currentGeneration()
    val biometricTerminal = request.callback()
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
                if (!requestCurrent(request) || !biometricTerminal() ||
                    setupGeneration != PhotoProfileAccessSessionManager.currentGeneration()) { onReady(null, null); return }
                choosePhotoFallbackCredential(request, expectedCredential) { saved ->
                    if (saved != null && config.isPhotoCredentialCurrent(saved)) {
                        onReady(saved, Config.PhotoCredentialProof(saved, PhotoProfileAccessSessionManager.currentGeneration(), request.id, null, biometricSucceeded = true))
                    } else onReady(null, null)
                }
            }

            override fun onAuthenticationFailed() { Log.d("PhotoProfileAuth", "biometric_retry") }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (requestCurrent(request) && biometricTerminal()) {
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
    request: PhotoAuthRequests.Request,
    expectedCredential: Config.PhotoCredentialSnapshot,
    onReady: (Config.PhotoCredentialSnapshot?) -> Unit
) {
    if (!requestCurrent(request)) return
    val fallbackGeneration = PhotoProfileAccessSessionManager.currentGeneration()
    val fallbackChoice = request.callback()
    val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
    fun finish(credential: Config.PhotoCredentialSnapshot?) {
        if (delivered.compareAndSet(false, true) && !isFinishing && !isDestroyed) onReady(credential)
    }
    fun configureFallback(fallbackType: Int) {
        SecurityDialog(this, "", fallbackType) { fallbackHash, confirmedType, success ->
            if (!requestCurrent(request) || delivered.get()) return@SecurityDialog
            if (fallbackGeneration != PhotoProfileAccessSessionManager.currentGeneration() || !success || fallbackHash.isBlank() || confirmedType != fallbackType ||
                config.photoCredentialSnapshot() != expectedCredential) {
                finish(null)
                return@SecurityDialog
            }
            val credential = config.savePhotoBiometricCredential(fallbackHash, confirmedType)
            if (credential != null) {
                PhotoProfileAccessSessionManager.revokeAll("photo_fallback_changed")
                request.credentialSaved(fallbackGeneration, PhotoProfileAccessSessionManager.currentGeneration())
            }
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
                if (delivered.get() || !requestCurrent(request) || !fallbackChoice()) return@setOnClickListener
                dialog.dismiss()
                configureFallback(type)
            }
        })
    }
    addChoice(R.string.photo_password_choose_pin, PROTECTION_PIN)
    addChoice(R.string.photo_password_choose_pattern, PROTECTION_PATTERN)
    dialog = OxygenClassicDialog.show(this, OxygenClassicDialog.builder(this, getString(R.string.photo_password_fallback_title))
        .setView(body)
        .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> if (fallbackChoice()) finish(null) })
    dialog.setOnCancelListener { if (fallbackChoice()) finish(null) }
}

private fun Activity.authenticatePreparedPhotoPassword(request: PhotoAuthRequests.Request, onAuthenticated: (Config.PhotoCredentialProof?) -> Unit) {
    if (!requestCurrent(request)) return
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
    val terminal = request.callback()
    fun complete(success: Boolean, verified: PhotoVerifiedCredential? = null, biometric: Boolean = false) {
        if (!requestCurrent(request) || !terminal()) return
        if (!requestCurrent(request)) return
        val valid = success && PhotoProfileAccessSessionManager.currentGeneration() == expectedGeneration &&
            config.isPhotoCredentialCurrent(expectedCredential)
        Log.d("PhotoProfileAuth", if (valid) "photo_challenge_succeeded" else "photo_challenge_cancelled_or_stale")
        onAuthenticated(expectedCredential.takeIf { valid }?.let {
            Config.PhotoCredentialProof(it, expectedGeneration, request.id, verified, biometric)
        })
    }

    if (!expectedCredential.biometricPrimary) {
        SecurityDialog(this, expectedCredential.hash, expectedCredential.type) { _, _, success ->
            complete(success, PhotoVerifiedCredential(expectedCredential.type, expectedCredential.hash))
        }
        return
    }

    val authenticateFallback: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            SecurityDialog(this, expectedCredential.fallbackHash, expectedCredential.fallbackType) { _, _, success ->
                complete(success, PhotoVerifiedCredential(expectedCredential.fallbackType, expectedCredential.fallbackHash))
            }
        }
    }
    val askForFallback: () -> Unit = fallback@{
        if (!requestCurrent(request)) return@fallback
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
    val biometricTransition = AtomicBoolean(false)
    val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (biometricTransition.compareAndSet(false, true)) complete(true, biometric = true)
            }

            override fun onAuthenticationFailed() { Log.d("PhotoProfileAuth", "biometric_retry") }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!requestCurrent(request) || !biometricTransition.compareAndSet(false, true)) return
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
internal fun Activity.unlockPhotoProfile(profileId: String?, completed: () -> Unit) = photoRequest { request ->
    val delivered = AtomicBoolean(false)
    fun finish(reason: String) {
        if (delivered.compareAndSet(false, true)) {
            val notify = requestCurrent(request)
            Log.d("PhotoProfileAuth", "finish=$reason")
            PhotoAuthentication.requests.finish(request)
            if (notify) completed()
        }
    }
    val profile = org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profileId }
    if (profile == null) { finish("profile_missing"); return@photoRequest }
    fun profileStillCurrent(): Boolean =
        org.fossify.gallery.helpers.PhotoProfileRepository.of(this).state().profiles.firstOrNull { it.id == profile.id } == profile

    fun bind(proof: Config.PhotoCredentialProof) {
        if (delivered.get() || !requestCurrent(request)) return
        Log.d("PhotoProfileAuth", "stage=BIND_SOURCES")
        val expectedCredential = proof.credential
        ensureBackgroundThread {
            val locks = runCatching { PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile) }.getOrNull()
            runOnUiThread {
                if (delivered.get() || !requestCurrent(request) || locks == null || !profileStillCurrent() ||
                    config.photoCredentialSnapshot() != expectedCredential ||
                    proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration() || proof.requestId != request.id) {
                    finish("stale_before_binding"); return@runOnUiThread
                }
                val favoriteCredentialAtStart = config.favoriteCredentialSnapshot()
                val pending = locks.filter { (root, lock) ->
                    config.photoSourceBindings[root] != PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                }.entries.groupBy {
                    // Reprint has no per-folder comparable biometric secret.
                    if (it.value.type in listOf(PROTECTION_PIN, PROTECTION_PATTERN)) it.value.type to it.value.hash
                    else it.key
                }.values.toList()
                val previousBindings = config.photoSourceBindings
                fun scope(currentLocks: Map<String, org.fossify.gallery.helpers.FavoriteAccessSessionManager.Protection>): Map<String, String> =
                    currentLocks.mapValues { (_, lock) -> PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash) } +
                        if (profile.sources.any { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } && config.isFavoritePasswordProtectionOn)
                            mapOf(PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING to PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext))
                        else emptyMap()
                val transaction = PhotoBindingTransaction(scope(locks), previousBindings)
                fun finishBinding() {
                    if (delivered.get() || !requestCurrent(request) || !profileStillCurrent() ||
                        !config.isPhotoCredentialCurrent(expectedCredential) ||
                        proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration() || proof.requestId != request.id) {
                        finish("stale_after_binding_check"); return
                    }
                    if (config.favoriteCredentialSnapshot() != favoriteCredentialAtStart || locks.any { (root, lock) -> applicationContext.favoriteProtection(root) != lock }) {
                        finish("protection_changed_during_binding"); return
                    }
                    // Re-capture every ancestor on the worker, including newly added
                    // locks, before the single main-thread binding/token commit.
                    ensureBackgroundThread {
                        val currentLocks = runCatching { PhotoProfileAccessSessionManager.profileLocks(applicationContext, profile) }.getOrNull()
                        runOnUiThread {
                            val valid = requestCurrent(request) && !delivered.get() && profileStillCurrent() &&
                                config.isPhotoCredentialCurrent(expectedCredential) &&
                                config.favoriteCredentialSnapshot() == favoriteCredentialAtStart &&
                                proof.sessionGeneration == PhotoProfileAccessSessionManager.currentGeneration() &&
                                config.photoSourceBindings == previousBindings
                            val committedBindings = currentLocks?.let { transaction.complete(scope(it), valid) }
                            if (committedBindings == null) { finish("binding_scope_changed_or_incomplete"); return@runOnUiThread }
                            config.photoSourceBindings = committedBindings
                            val token = PhotoProfileAccessSessionManager.create(applicationContext, proof)
                            if (token == null && config.photoSourceBindings == committedBindings) config.photoSourceBindings = previousBindings
                            finish(if (token == null) "proof_rejected" else "authorized")
                        }
                    }
                }
                fun bindFavorites() {
                    if (profile.sources.none { it.type == org.fossify.gallery.helpers.PhotoSourceType.FAVORITES } ||
                        !config.isFavoritePasswordProtectionOn ||
                        config.photoSourceBindings[PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING] == PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)) {
                        finishBinding(); return
                    }
                    val favoriteCredential = config.favoriteCredentialSnapshot()
                    val sameCredentialWasVerified = !favoriteCredential.biometricPrimary &&
                        favoriteCredential.type in listOf(PROTECTION_PATTERN, PROTECTION_PIN) &&
                        favoriteCredential.isConfigured && (proof.verified?.matches(favoriteCredential.type, favoriteCredential.hash) == true || pending.any { group ->
                            group.first().value.type == favoriteCredential.type && group.first().value.hash == favoriteCredential.hash &&
                                group.all { (root, lock) -> transaction.isApproved(root, PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)) }
                        })
                    if (sameCredentialWasVerified && config.isFavoriteCredentialCurrent(favoriteCredential)) {
                        transaction.approve(mapOf(PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING to PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)))
                        finishBinding(); return
                    }
                    val favoriteCallback = request.callback()
                    authenticateFavoritePassword(request) { favoriteProof ->
                        if (!favoriteCallback()) return@authenticateFavoritePassword
                        if (delivered.get() || !requestCurrent(request)) return@authenticateFavoritePassword
                        if (favoriteProof != null && favoriteProof.credential == favoriteCredentialAtStart &&
                            config.isFavoriteCredentialCurrent(favoriteProof.credential) &&
                            proof.sessionGeneration == PhotoProfileAccessSessionManager.currentGeneration()) {
                            transaction.approve(mapOf(PhotoProfileAccessSessionManager.FAVORITE_CREDENTIAL_BINDING to PhotoProfileAccessSessionManager.favoriteIdentity(applicationContext)))
                            finishBinding()
                        } else finish("favorite_auth_cancelled_or_failed")
                    }
                }
                fun next(index: Int) {
                    if (delivered.get() || !requestCurrent(request)) return
                    if (index == pending.size) { bindFavorites(); return }
                    val group = pending[index]
                    val type = group.first().value.type
                    val hash = group.first().value.hash
                    if (type !in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_FINGERPRINT) ||
                        (type != PROTECTION_FINGERPRINT && hash.isBlank())) {
                        finish("unsupported_source_protection"); return
                    }
                    fun approveGroup() {
                        transaction.approve(group.associate { (root, lock) ->
                            root to PhotoProfileAccessSessionManager.fingerprint(lock.type, lock.hash)
                        })
                        next(index + 1)
                    }
                    if (proof.verified?.matches(type, hash) == true) {
                        Log.d("PhotoProfileAuth", "source_proof_reused count=${group.size}")
                        approveGroup()
                        return
                    }
                    val sourceCallback = request.callback()
                    showPhotoPasswordDialog(R.string.photo_password_bind_title) {
                        setMessage(getString(if (type == PROTECTION_FINGERPRINT)
                            R.string.photo_password_bind_biometric_separate else R.string.photo_password_bind_group_message, group.size))
                        setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> finish("binding_cancelled") }
                        setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                            if (!requestCurrent(request) || delivered.get() || !profileStillCurrent() ||
                                proof.sessionGeneration != PhotoProfileAccessSessionManager.currentGeneration()) {
                                finish("stale_source_dialog"); return@setPositiveButton
                            }
                            SecurityDialog(this@unlockPhotoProfile, hash, type) { _, _, success ->
                                if (delivered.get() || !requestCurrent(request) || !sourceCallback()) return@SecurityDialog
                                if (success && config.isPhotoCredentialCurrent(expectedCredential) &&
                                    proof.sessionGeneration == PhotoProfileAccessSessionManager.currentGeneration() &&
                                    group.all { applicationContext.favoriteProtection(it.key) == it.value }) {
                                    approveGroup()
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
        if (delivered.get() || !requestCurrent(request)) return
        if (config.photoCredentialSnapshot().enabled) {
            Log.d("PhotoProfileAuth", "stage=VERIFY_PHOTO_PASSWORD")
            authenticatePreparedPhotoPassword(request) { proof ->
                if (proof != null) bind(proof) else finish("photo_auth_cancelled_or_failed")
            }
        } else {
            Log.d("PhotoProfileAuth", "stage=SETUP_CREDENTIAL")
            configurePhotoPasswordWithProof(request) { credential, proof ->
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
                if (delivered.get() || !requestCurrent(request)) return@runOnUiThread
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
