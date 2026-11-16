package org.fossify.gallery.helpers

/** One diagnostic record per MediaActivity request. This does not participate in admission or publication. */
internal class MediaRequestDiagnostics private constructor(
    val id: Long,
    val generation: Long,
    val scopeId: String,
    private val source: ViewerScopeType,
    private val revision: Long?,
    private val now: () -> Long,
    private val emit: (String, Long, Map<String, Any?>) -> Unit,
) {
    private val createdAt = now()
    private var admitted = false
    private var startedAt: Long? = null
    private var closed = false
    private var cancellation: String? = null

    private fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        emit(name, id, mapOf(
            "scopeId" to scopeId, "sourceType" to source.name, "scopeRevision" to (revision ?: "UNKNOWN"),
            "requestGeneration" to generation
        ) + fields)
    }

    @Synchronized fun admit() {
        if (closed || admitted) return
        admitted = true
        event("media_request_admitted")
    }

    @Synchronized fun workerStarted() {
        if (closed || startedAt != null) return
        startedAt = now()
        event("media_worker_started", mapOf("workerId" to id, "sinceRequestMs" to (now() - createdAt)))
    }

    fun workerSource(actualSource: ViewerScopeType) {
        event("media_worker_source", mapOf("workerId" to id, "resolvedSourceType" to actualSource.name))
    }

    @Synchronized fun cancellationRequested(reason: String) {
        if (closed || cancellation != null) return
        cancellation = reason
        event("media_request_cancel_requested", mapOf("cancelReason" to reason, "workerStarted" to (startedAt != null)))
    }

    @Synchronized fun skipIfUnadmitted(reason: String) {
        if (!admitted) skip(reason)
    }

    @Synchronized fun skip(reason: String) {
        if (closed || startedAt != null) return
        closed = true
        event("media_request_skipped", mapOf("terminalReason" to (cancellation ?: reason), "admitted" to admitted))
    }

    @Synchronized fun workerFinished(state: GalleryScanState, cancelled: Boolean) {
        val start = startedAt ?: return
        if (closed) return
        closed = true
        val outcome = when {
            cancelled || cancellation != null || state == GalleryScanState.CANCELLED -> "CANCELLED"
            state == GalleryScanState.COMPLETE -> "COMPLETE"
            else -> "FAILED"
        }
        event("media_worker_terminal", mapOf(
            "workerId" to id, "terminal" to outcome, "scanState" to state.name,
            "terminalReason" to (cancellation ?: state.name), "workerMs" to (now() - start)
        ))
    }

    fun published(stage: MediaPublicationStage, count: Int, version: Long, cacheStatus: String) {
        event("media_content_published", mapOf(
            "stage" to stage.name, "itemCount" to count, "snapshotVersion" to version,
            "cacheStatus" to cacheStatus, "sinceRequestMs" to (now() - createdAt)
        ))
    }

    companion object {
        fun scopeId(source: ViewerScopeType, locator: String) = GalleryDiagnosticLogger.anonymousId("${source.name}:$locator")

        fun start(
            generation: Long, source: ViewerScopeType, locator: String, revision: Long?, trigger: String,
            previousId: Long?, now: () -> Long, emit: (String, Long, Map<String, Any?>) -> Unit,
        ): MediaRequestDiagnostics =
            MediaRequestDiagnostics(
                DiagnosticSequence.next(), generation, scopeId(source, locator), source, revision, now, emit
            ).apply {
                event("media_request_created", mapOf("originTrigger" to trigger,
                    "previousRequestId" to previousId, "accessGeneration" to "UNKNOWN"))
            }
    }
}

internal enum class MediaPublicationStage { PREVIEW, PARTIAL, FINAL }

internal object DiagnosticSequence {
    private val sequence = java.util.concurrent.atomic.AtomicLong()
    fun next(): Long = sequence.incrementAndGet()
}

internal enum class FrameEvidence {
    READY, PRE_DRAW, DRAW, FRAME_COMMIT, UNKNOWN;

    val presented: String get() = "NOT_OBSERVABLE"

    companion object {
        fun gridAcknowledgement(frameCommit: Boolean) = if (frameCommit) FRAME_COMMIT else DRAW
    }
}

/** Adapter generations are local to their instance, even when a source request is shared. */
internal data class AdapterSubmission(val adapterId: Long, val generation: Int, val requestId: Long?)

internal enum class AdapterCommitKind(val visibleListChanged: Boolean) {
    NO_OP(false), APPEND(true), REORDER(true), REPLACE(true), DIFF(true)
}
