package org.fossify.gallery.helpers

/** Source completeness is independent of whether a worker returned or a preview has rows. */
internal enum class GalleryScanState { COMPLETE, PARTIAL, FAILED, ACCESS_REQUIRED, TIMED_OUT, CANCELLED }

internal data class GalleryScanResult(
    val state: GalleryScanState,
    val failedPaths: Set<String> = emptySet(),
    val cause: Exception? = null
) {
    val complete get() = state == GalleryScanState.COMPLETE
    companion object {
        fun sources(successfulSources: Int, failures: Map<String, Exception>, enumerationComplete: Boolean = true): GalleryScanResult {
            val state = when {
                failures.isEmpty() && enumerationComplete -> GalleryScanState.COMPLETE
                successfulSources > 0 -> GalleryScanState.PARTIAL
                else -> GalleryScanState.FAILED
            }
            return GalleryScanResult(state, failures.keys.toSet(), failures.values.firstOrNull())
        }
    }
}

/** A timeout, cancellation and worker completion compete for exactly one terminal transition. */
internal class GalleryScanTerminalGate {
    private var request = -1L
    private var terminal: GalleryScanResult? = null
    @Synchronized fun begin(id: Long) { request = id; terminal = null }
    @Synchronized fun finish(id: Long, result: GalleryScanResult): Boolean {
        if (request != id || terminal != null) return false
        terminal = result
        return true
    }
    @Synchronized fun result(id: Long): GalleryScanResult? = terminal.takeIf { request == id }
}
