package org.fossify.gallery.helpers

import java.lang.ref.WeakReference
import java.util.UUID

/** Process-only lease shared by photo setup, Settings verification and source binding.
 * No credential or scope is persisted, and only one owner can challenge at a time. */
internal class PhotoAuthRequests {
    class Request internal constructor(owner: Any, generation: Long) {
        var generation = generation
            private set
        private var ownCredentialSaveFrom: Long? = null
        fun acceptsEvidenceGeneration(value: Long): Boolean = !closed &&
            (value == generation || value == ownCredentialSaveFrom)
        fun credentialSaved(expected: Long, next: Long): Boolean {
            if (closed || generation != expected || ownCredentialSaveFrom != null || next <= expected) return false
            ownCredentialSaveFrom = expected
            generation = next
            return true
        }
        val id: String = UUID.randomUUID().toString()
        private val owner = WeakReference(owner)
        internal var closed = false
        private var step = 0L
        fun owns(candidate: Any) = owner.get() === candidate
        fun ownerAlive() = owner.get() != null
        /** Each asynchronous/dialog transition accepts at most one terminal callback. */
        fun callback(): () -> Boolean {
            val expected = ++step
            var consumed = false
            return {
                if (closed || consumed || step != expected) false else { consumed = true; true }
            }
        }
    }
    private var active: Request? = null
    @Synchronized fun begin(owner: Any, generation: Long): Request? {
        active?.takeIf { it.generation != generation }?.let(::finish)
        if (active?.let { !it.closed && it.ownerAlive() } == true) return null
        return Request(owner, generation).also { active = it }
    }
    @Synchronized fun current(request: Request, owner: Any): Boolean =
        active === request && !request.closed && request.owns(owner)
    @Synchronized fun finish(request: Request) {
        request.closed = true
        if (active === request) active = null
    }
    @Synchronized fun activeId(id: String) = active?.let { !it.closed && it.id == id && it.ownerAlive() } == true
    @Synchronized fun cancelAll() { active?.let(::finish) }
    @Synchronized fun cancelOwner(owner: Any) { active?.takeIf { it.owns(owner) }?.let(::finish) }
}

/** Only the credential actually verified can be compared. A biometric result never
 * proves knowledge of its recovery PIN, and Commons/Reprint folder locks are not
 * assumed compatible with AndroidX BiometricPrompt. */
internal data class PhotoVerifiedCredential(val type: Int, private val hash: String) {
    fun matches(type: Int, hash: String): Boolean =
        this.type in 0..1 && this.hash.isNotBlank() && this.type == type && this.hash == hash
    override fun toString() = "PhotoVerifiedCredential(redacted)"
}

internal object PhotoAuthentication {
    val requests = PhotoAuthRequests()
}

/** A real AndroidX biometric callback, scoped by consent before the prompt. */
internal class PhotoBiometricEvidence(
    val requestId: String,
    val generation: Long,
    private val scope: String,
    private val clock: () -> Long = System::nanoTime
) {
    private val issuedAt = clock()
    fun authorizes(request: PhotoAuthRequests.Request, currentScope: String, currentGeneration: Long): Boolean =
        !request.closed && request.ownerAlive() && request.generation == currentGeneration && request.id == requestId && request.acceptsEvidenceGeneration(generation) &&
            scope == currentScope && clock() - issuedAt in 0..120_000_000_000L
}

/** Folder fingerprint locks all use Commons/Reprint. Their explicit consent group
 * remains separate from AndroidX biometric evidence, even when hashes are empty. */
internal fun photoSourceChallengeGroups(
    locks: Map<String, FavoriteAccessSessionManager.Protection>
): List<List<Map.Entry<String, FavoriteAccessSessionManager.Protection>>> = locks.entries.groupBy {
    when (it.value.type) {
        org.fossify.commons.helpers.PROTECTION_PIN, org.fossify.commons.helpers.PROTECTION_PATTERN -> it.value.type to it.value.hash
        org.fossify.commons.helpers.PROTECTION_FINGERPRINT -> "commons-reprint-fingerprint"
        else -> it.key
    }
}.values.toList()
