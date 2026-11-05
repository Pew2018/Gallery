package org.fossify.gallery.helpers

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Request-scoped, opt-in counters. A null instance is the disabled fast path. */
internal class MediaPipelineDiagnostics(
    val diagnosticRequestId: String,
    private val sourceType: String,
    private val loadReason: String,
    @Volatile private var permissionMode: String,
    private val scopeKey: String,
    private val businessRequestId: Long,
    private val startedAtElapsedMs: Long,
    private val clock: () -> Long,
    private val eventSink: (DiagnosticLevel, String, String, Any?, Map<String, Any?>) -> Unit = { level, module, name, request, fields ->
        GalleryDiagnosticLogger.event(level, module, name, request, fields)
    }
) {
    private val lock = Any()
    private val counters = linkedMapOf<String, Long>()
    private val terminal = AtomicBoolean(false)

    fun add(metric: String, value: Long = 1L) {
        if (value < 0L) return
        synchronized(lock) {
            val previous = counters[metric]?.takeIf { it >= 0L } ?: 0L
            counters[metric] = if (Long.MAX_VALUE - previous < value) Long.MAX_VALUE else previous + value
            if (value > 0L && metric == "safOperationCount" && permissionMode != "UNKNOWN") permissionMode = "MIXED"
        }
    }

    fun set(metric: String, value: Long) = synchronized(lock) { counters[metric] = countOrUnknown(value) }

    fun recordDuration(metric: String, startedAtElapsedMs: Long, amount: Long = 1L) {
        add(metric, (clock() - startedAtElapsedMs).coerceAtLeast(0L))
        if (amount >= 0L) add(metric.removeSuffix("Ms") + "Count", amount)
    }

    fun snapshot(): Map<String, Long> = synchronized(lock) { counters.toMap() }

    /** Distinguish unmeasured (-1) from measured zero throughout Phase 0 metrics. */
    private fun countOrUnknown(value: Long): Long = if (value >= 0L) value else -1L

    /** Unknown duration is -1, while clock underflow (-2 or below) is invalid. */
    private fun durationOrUnknown(value: Long): Long =
        if (value == -1L) -1L else value.coerceAtLeast(0L)

    /** Cancellation and delayed worker callbacks compete for one diagnostic terminal. */
    fun finishIfOpen(
        terminalState: String, queueWaitMs: Long, workerMs: Long, publicationWaitMs: Long,
        candidateCount: Long, acceptedCount: Long
    ) {
        if (!terminal.get()) finish(terminalState, queueWaitMs, workerMs, publicationWaitMs, candidateCount, acceptedCount)
    }

    fun finish(
        terminalState: String,
        queueWaitMs: Long,
        workerMs: Long,
        publicationWaitMs: Long,
        candidateCount: Long,
        acceptedCount: Long
    ) {
        if (!terminal.compareAndSet(false, true)) {
            eventSink(
                DiagnosticLevel.WARN, "MediaPipeline", "media_load_duplicate_terminal", diagnosticRequestId,
                mapOf("terminalState" to terminalState, "businessRequest" to businessRequestId)
            )
            return
        }
        val values = snapshot()
        val common = mapOf(
            "scopeKey" to scopeKey, "sourceType" to sourceType, "loadReason" to loadReason,
            "permissionMode" to permissionMode, "businessRequest" to businessRequestId
        )
        eventSink(
            DiagnosticLevel.INFO, "MediaPipeline", "media_scan_summary", diagnosticRequestId,
            common + mapOf(
                "queueWaitMs" to durationOrUnknown(queueWaitMs), "workerMs" to durationOrUnknown(workerMs),
                "publicationWaitMs" to durationOrUnknown(publicationWaitMs),
                "totalRequestMs" to (clock() - startedAtElapsedMs).coerceAtLeast(0L),
                "candidateCount" to countOrUnknown(candidateCount), "acceptedCount" to countOrUnknown(acceptedCount),
                "terminalState" to terminalState
            )
        )
        emit("media_sort_summary", common, values, listOf("sortCount", "sortInputItems", "sortMs", "sortMode"))
        emit("media_source_summary", common, values, listOf(
            "mediaStoreQueryCount", "mediaStoreQueryMs", "mediaStoreRows", "diskEnumerateCount",
            "diskEnumeratedItems", "diskEnumerateMs", "metadataReadCount", "metadataReadMs",
            "safOperationCount", "safOperationMs"
        ))
        emit("media_cache_db_summary", common, values, listOf(
            "cacheLookupCount", "cacheHitCount", "cacheMissCount", "cachedItemCount", "diskSupplementCount",
            "dbReadCount", "dbReadRows", "dbReadMs", "dbWriteCount", "dbWriteInputRows", "dbWriteMs"
        ))
        emit("media_ui_summary", common, values, listOf(
            "stageCount", "stagePublishCount", "adapterSubmitCount", "adapterCommitCount", "adapterDroppedCount",
            "adapterSkippedSameContentCount", "diffCount", "diffMs"
        ))
        emit("media_thumbnail_summary", common, values, listOf(
            "thumbnailBindCount", "thumbnailReloadCount", "thumbnailCancelCount", "thumbnailUnchangedBindCount"
        ))
        eventSink(
            if (terminalState == "COMPLETE") DiagnosticLevel.INFO else DiagnosticLevel.WARN,
            "MediaPipeline", "media_load_terminal", diagnosticRequestId,
            common + mapOf("terminalState" to terminalState, "elapsedMs" to (clock() - startedAtElapsedMs).coerceAtLeast(0L))
        )
    }

    private fun emit(name: String, common: Map<String, Any?>, values: Map<String, Long>, keys: List<String>) {
        val fields = LinkedHashMap<String, Any?>(common)
        keys.forEach { key -> fields[key] = countOrUnknown(values[key] ?: -1L) }
        eventSink(DiagnosticLevel.DEBUG, "MediaPipeline", name, diagnosticRequestId, fields)
    }

    companion object {
        fun create(
            rawScope: String,
            sourceType: String,
            loadReason: String,
            businessRequestId: Long,
            permissionMode: String = "UNKNOWN",
            clock: () -> Long = SystemClock::elapsedRealtime
        ): MediaPipelineDiagnostics? {
            if (!GalleryDiagnosticLogger.isEnabled()) return null
            val safeSource = sourceType.takeIf { it in setOf("HOME", "FOLDER", "CATEGORIZED", "FAVORITES", "RECYCLE_BIN", "PHOTO_PROFILE") } ?: "UNKNOWN"
            val safeReason = loadReason.takeIf { it in setOf("INITIAL", "REFRESH", "RETURN", "CONFIG_SWITCH", "EXTERNAL_CHANGE") } ?: "UNKNOWN"
            val safePermission = permissionMode.takeIf { it in setOf("ALL_FILES", "SCOPED_STORAGE", "LEGACY", "SAF", "MIXED", "UNKNOWN") } ?: "UNKNOWN"
            return MediaPipelineDiagnostics(
                "ml-${UUID.randomUUID()}", safeSource, safeReason, safePermission,
                runCatching { GalleryDiagnosticLogger.anonymousId(rawScope) }.getOrDefault("scope-unknown"),
                businessRequestId, clock(), clock
            )
        }
    }
}
