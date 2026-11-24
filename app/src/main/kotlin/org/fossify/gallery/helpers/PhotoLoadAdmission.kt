package org.fossify.gallery.helpers

/** The request currently owning the Photos grid. Diagnostic ids stay with it. */
internal data class ActivePhotoLoad(
    val request: Long,
    val reason: PhotoLoadReason,
    val scopeKey: String,
    val profileId: String?,
    val revision: Long,
    val accessVersion: Long,
    val running: Boolean,
    /** Still the newest generation; a superseded worker may never absorb a request. */
    val current: Boolean,
)

internal enum class PhotoLoadDecision(val token: String) {
    /** No equivalent worker exists; the caller must create one. */
    NEW("new"),
    /** A live worker already satisfies this intent: keep its generation and its UI binding. */
    REUSE("reused"),
    /** The same scope is already refreshing; only remember that another pass is wanted. */
    COALESCE_REFRESH("coalesced"),
}

/**
 * Admission for a Photos load request. It runs *before* the request generation is
 * bumped, the previous worker is cancelled and the displayed set is cleared, because
 * every one of those side effects makes a duplicate unrecoverable.
 *
 * The device log shows the cost of getting this wrong: a configuration switch created
 * a SCOPE_CHANGED worker, `onResume` immediately created an ENTER worker for the same
 * scope/revision, and both read 10 968 cached rows, sorted them and validated 160
 * candidates before one of them was cancelled.
 *
 * Two intents are genuinely equivalent - "show this scope" and "this scope changed to
 * what it already is". A manual refresh is not: it asks for a fresh disk read, which a
 * cache-satisfied ENTER does not provide. Repeated pulls are coalesced rather than
 * dropped, so a file created between them is still picked up.
 */
internal object PhotoLoadAdmission {
    fun decide(
        reason: PhotoLoadReason,
        scopeKey: String,
        profileId: String?,
        revision: Long,
        accessVersion: Long,
        active: ActivePhotoLoad?,
    ): PhotoLoadDecision {
        if (active == null || !active.current || !active.running) return PhotoLoadDecision.NEW
        // Never reuse across a scope, profile, content revision or access change. A
        // profile id alone is not an identity: two profiles can share every source
        // and still differ in filter, sorting or protection state.
        if (active.scopeKey != scopeKey || active.profileId != profileId ||
            active.revision != revision || active.accessVersion != accessVersion) {
            return PhotoLoadDecision.NEW
        }
        return when (reason) {
            PhotoLoadReason.ENTER, PhotoLoadReason.SCOPE_CHANGED -> PhotoLoadDecision.REUSE
            PhotoLoadReason.REFRESH -> if (active.reason == PhotoLoadReason.REFRESH) {
                PhotoLoadDecision.COALESCE_REFRESH
            } else {
                PhotoLoadDecision.NEW
            }
        }
    }
}
