package org.fossify.gallery.helpers

/** Refresh stage bridge for the independent Diagnostics and logs feature.
 * No target paths, preferences, private buffer, Logcat output or manual controls.
 */
internal object FolderRefreshDiagnostics {
    fun event(request: Long, stage: String, paths: Collection<String> = emptyList(), detail: String = "") {
        if (!GalleryDiagnosticLogger.isEnabled()) return
        GalleryDiagnosticLogger.event(
            if (stage.contains("fail", true) || stage.contains("error", true)) DiagnosticLevel.ERROR else DiagnosticLevel.INFO,
            "FolderRefresh", stage, request,
            mapOf("count" to paths.size, "anonymousId" to paths.singleOrNull()?.let(GalleryDiagnosticLogger::anonymousId), "detail" to detail)
        )
    }
    inline fun <T> measure(request: Long, stage: String, block: () -> T): T {
        // Per-file counters/time remain aggregated by MediaPipelineDiagnostics.
        if (stage == "file_metadata" || !GalleryDiagnosticLogger.isEnabled()) return block()
        val start = android.os.SystemClock.elapsedRealtime()
        event(request, stage + "_start")
        return try { block() } finally {
            event(request, stage + "_end", detail = "elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}")
        }
    }
}
