package org.fossify.gallery.helpers.backup

import android.content.Context
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.FAVORITE_PASSWORD_ENABLED
import org.fossify.gallery.helpers.FAVORITE_PASSWORD_HASH
import org.fossify.gallery.helpers.FAVORITE_PROTECTION_TYPE
import org.fossify.gallery.helpers.FAVORITE_FALLBACK_PASSWORD_HASH
import org.fossify.gallery.helpers.FAVORITE_FALLBACK_PROTECTION_TYPE
import org.fossify.gallery.helpers.FAVORITE_BIOMETRIC_PRIMARY
import org.fossify.gallery.helpers.FavoriteAccessSessionManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One in-memory operation lease. No hashes, proof, biometric evidence or session tokens are stored. */
internal class GalleryRestoreAuthorization private constructor(
    val operationId: String,
    private val planId: String,
    private val localToken: String,
    private val source: GalleryBackupSnapshot,
    val mode: GalleryRestoreMode,
    private val generation: Long,
    private val credentialVersion: String
) {
    private val active = AtomicBoolean(true)
    fun invalidate() { active.set(false) }
    fun current(context: Context, plan: GalleryRestorePlan, mode: GalleryRestoreMode): Boolean =
        active.get() && this.planId == plan.id && source === plan.incoming && localToken == plan.localToken && this.mode == mode &&
            generation == FavoriteAccessSessionManager.currentGeneration() && credentialVersion == securityVersion(context) &&
            context.config.let { !it.isFolderProtected(FAVORITES) && it.isFavoriteCredentialCurrent(it.favoriteCredentialSnapshot()) }

    companion object {
        fun securityVersion(context: Context): String = BackupDigest.sha256(
            BackupDigest.preferences(context.getSharedPrefs().all.filterKeys {
                it in setOf(FAVORITE_PASSWORD_ENABLED, FAVORITE_PASSWORD_HASH, FAVORITE_PROTECTION_TYPE,
                    FAVORITE_FALLBACK_PASSWORD_HASH, FAVORITE_FALLBACK_PROTECTION_TYPE, FAVORITE_BIOMETRIC_PRIMARY)
            }).toByteArray(Charsets.UTF_8))

        fun issue(context: Context, plan: GalleryRestorePlan, mode: GalleryRestoreMode,
                  operationId: String, proof: Config.FavoriteCredentialProof?, localToken: String = plan.localToken): GalleryRestoreAuthorization {
            val config = context.config
            if (config.isFolderProtected(FAVORITES)) throw RestoreAuthorizationRequired(RestoreAuthorizationReason.LEGACY_MIGRATION)
            if (proof == null || !config.isFavoriteCredentialCurrent(proof.credential) ||
                proof.sessionGeneration != plan.sessionGeneration || proof.sessionGeneration != FavoriteAccessSessionManager.currentGeneration() ||
                plan.securityVersion != securityVersion(context)) throw RestoreAuthorizationRequired(RestoreAuthorizationReason.INVALID_PROOF)
            return GalleryRestoreAuthorization(operationId, plan.id, localToken, plan.incoming, mode, proof.sessionGeneration, plan.securityVersion)
        }
    }
}

/** UI/worker coordination; independent of the global backup mutex, and never persisted. */
internal class GalleryRestoreOperation(private val ownerAlive: () -> Boolean = { true }) {
    val id: String = UUID.randomUUID().toString()
    private val active = AtomicBoolean(true)
    private val advancing = AtomicBoolean(false)
    @Volatile var authorization: GalleryRestoreAuthorization? = null
        private set
    fun isActive() = active.get() && ownerAlive()
    fun advance(): Boolean = isActive() && advancing.compareAndSet(false, true)
    fun awaitConfirmation() { advancing.set(false) }
    @Synchronized fun attach(value: GalleryRestoreAuthorization): Boolean {
        if (!isActive() || value.operationId != id) { value.invalidate(); return false }
        authorization?.invalidate()
        authorization = value
        return true
    }
    @Synchronized fun close() { active.set(false); authorization?.invalidate(); advancing.set(true) }
}

internal enum class RestoreAuthorizationReason { REQUIRED, INVALID_PROOF, LEGACY_MIGRATION }
internal class RestoreAuthorizationRequired(val reason: RestoreAuthorizationReason) : IllegalStateException("Restore authorization is unavailable")
internal enum class RestoreStaleReason { LOCAL_STATE, ACCESS, AUTHORIZATION }
