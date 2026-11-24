package org.fossify.gallery.helpers

import android.os.SystemClock

/**
 * Diagnostics-only timeline for the dark interval between a cache read and the
 * first real grid publication.
 *
 * These events measure worker-side stages plus the UI enqueue point. They are not
 * frame-presentation proof: the Activity separately observes the adapter commit
 * and a registered frame-commit callback. Every field is anonymous (navigation
 * token and profile through the diagnostic salt, no media paths).
 */
internal class PhotoPublicationTimeline(
    private val diagnostics: MediaPipelineDiagnostics?,
    private val requestId: Long,
    navigationToken: String?,
    profileId: String,
) {
    private val navigation = navigationToken?.let { GalleryDiagnosticLogger.anonymousId(it) } ?: "none"
    private val scopeKey = GalleryDiagnosticLogger.anonymousId("photo-profile:$profileId")
    private val requestStartedAt = SystemClock.elapsedRealtime()
    private var lastMarkAt = requestStartedAt

    val enabled: Boolean get() = diagnostics != null && GalleryDiagnosticLogger.isEnabled()

    fun mark(event: String, stage: String, count: Int, extra: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val now = SystemClock.elapsedRealtime()
        GalleryDiagnosticLogger.event(
            DiagnosticLevel.DEBUG, "PhotoPublication", event, requestId,
            fields = mapOf(
                "navigation" to navigation,
                "mediaRequestGeneration" to requestId,
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
