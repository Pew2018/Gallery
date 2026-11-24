package org.fossify.gallery.helpers

import android.os.SystemClock

/**
 * One derivation for every photo-profile diagnostic field.
 *
 * A profile had four different anonymous keys in the Phase 1 export
 * (`photo-profile:<id>`, the raw id, `PHOTO_PROFILE:<id>` and the bare path), so no
 * single click could be followed from the bottom bar into the worker and back into
 * the grid. Everything that names this scope now goes through [scopeKey].
 */
internal object PhotoDiagnosticIdentity {
    fun scopeKey(profileId: String?): String =
        GalleryDiagnosticLogger.anonymousId("photo-profile:${profileId.orEmpty()}")

    fun navigation(token: String?): String =
        token?.let { GalleryDiagnosticLogger.anonymousId(it) } ?: "none"
}

/**
 * Diagnostics-only timeline for the dark interval between a cache read and the
 * first real grid publication.
 *
 * These events measure worker-side stages plus the UI enqueue point. They are not
 * frame-presentation proof: the Activity separately observes the adapter commit
 * and a registered frame-commit callback.
 *
 * Correlation fields:
 *  - `requestId` is process-unique and is also the log line's `request=` field, so
 *    every event of one bottom-bar tap shares one value.
 *  - `businessRequest` is the Activity-local generation. It restarts at 1 in each
 *    Activity instance and must never be treated as globally unique.
 *  - `navigation` is the root-transition token, which is what links the tap to the
 *    destination.
 *  - every duration uses one monotonic clock ([clock]); wall-clock, uptime and
 *    elapsedRealtime values are never subtracted from each other.
 */
internal class PhotoPublicationTimeline(
    private val diagnostics: MediaPipelineDiagnostics?,
    /** Activity-local request generation. Distinct from [requestId] on purpose. */
    businessRequest: Long,
    /** Process-unique diagnostic id shared with the grid-side events. */
    diagnosticRequestId: String,
    navigationToken: String?,
    profileId: String,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val navigation = PhotoDiagnosticIdentity.navigation(navigationToken)
    private val scopeKey = PhotoDiagnosticIdentity.scopeKey(profileId)
    private val requestId = diagnosticRequestId
    private val businessRequest = businessRequest
    private val requestStartedAt = clock()
    private var lastMarkAt = requestStartedAt

    val enabled: Boolean get() = diagnostics != null && GalleryDiagnosticLogger.isEnabled()

    fun mark(event: String, stage: String, count: Int, extra: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val now = clock()
        GalleryDiagnosticLogger.event(
            DiagnosticLevel.DEBUG, "PhotoPublication", event, requestId,
            fields = mapOf(
                "navigation" to navigation,
                "requestId" to requestId,
                "businessRequest" to businessRequest,
                "mediaRequestGeneration" to businessRequest,
                "scopeKey" to scopeKey,
                "stage" to stage,
                "mediaCount" to count,
                "durationMs" to (now - lastMarkAt).coerceAtLeast(0L),
                "sinceRequestMs" to (now - requestStartedAt).coerceAtLeast(0L),
            ) + extra
        )
        lastMarkAt = now
    }
}
