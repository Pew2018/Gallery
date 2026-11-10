package org.fossify.gallery.helpers

import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * Path-free loading measurements. Release builds retain viewer/grid/album counters
 * and durations so device boundary reports can be diagnosed without media paths.
 */
internal object GalleryPerfTrace {
    private fun logcatEnabled(name: String, debug: Boolean = BuildConfig.DEBUG) = debug || name.startsWith("viewer_") ||
        name.startsWith("photo_grid_") || name.startsWith("album_")

    internal fun shouldPersist(name: String, diagnosticsEnabled: Boolean) = diagnosticsEnabled

    internal fun shouldMeasure(name: String, debug: Boolean, diagnosticsEnabled: Boolean) =
        logcatEnabled(name, debug) || shouldPersist(name, diagnosticsEnabled)

    fun begin(): Long = System.nanoTime()

    fun event(name: String, count: Int? = null, revision: Long? = null) {
        val diagnosticsEnabled = GalleryDiagnosticLogger.isEnabled()
        val writeLogcat = logcatEnabled(name)
        if (!shouldMeasure(name, BuildConfig.DEBUG, diagnosticsEnabled)) return
        if (diagnosticsEnabled) GalleryDiagnosticLogger.event(
            DiagnosticLevel.DEBUG, "Performance", name,
            fields = mapOf("count" to count, "revision" to revision)
        )
        if (writeLogcat) {
            val version = revision?.let { " revision=$it" }.orEmpty()
            Log.d("GalleryPerf", "event=$name count=${count ?: -1}$version")
        }
    }

    fun end(stage: String, startedNanos: Long, count: Int? = null) {
        if (startedNanos == 0L) return
        duration(stage, System.nanoTime() - startedNanos, count)
    }

    /** Record a stage whose exact elapsed time was measured inside a pure helper. */
    fun duration(stage: String, elapsedNanos: Long, count: Int? = null) {
        val diagnosticsEnabled = GalleryDiagnosticLogger.isEnabled()
        val writeLogcat = logcatEnabled(stage)
        if (!shouldMeasure(stage, BuildConfig.DEBUG, diagnosticsEnabled)) return
        val millis = elapsedNanos.coerceAtLeast(0L) / 1_000_000.0
        if (diagnosticsEnabled) GalleryDiagnosticLogger.event(
            DiagnosticLevel.DEBUG, "Performance", stage,
            fields = mapOf("durationMs" to millis, "count" to count)
        )
        if (writeLogcat) Log.d("GalleryPerf", "stage=$stage ms=${"%.2f".format(java.util.Locale.US, millis)} count=${count ?: -1}")
    }
}
