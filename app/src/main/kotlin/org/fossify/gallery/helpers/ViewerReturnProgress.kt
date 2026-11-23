package org.fossify.gallery.helpers

/**
 * Maps the platform's predictive-back gesture onto the Viewer return envelope.
 *
 * `BackEventCompat.progress` is normalised to 0..1 and restarts at zero for every
 * new gesture. When a gesture starts while a cancelled return is still settling,
 * the live frame is inherited as the base. The new gesture must then consume the
 * *remaining* interval: adding raw progress would reach 1 before the finger reaches
 * the edge, so the envelope would saturate and the rest of the drag would be a dead
 * zone. The first progressed event still lands exactly on the inherited frame, so
 * takeover cannot jump.
 */
internal object ViewerReturnProgress {
    fun resume(base: Float, gestureProgress: Float): Float {
        if (!base.isFinite()) return 1f
        val start = base.coerceIn(0f, 1f)
        if (!gestureProgress.isFinite()) return start
        val progress = gestureProgress.coerceIn(0f, 1f)
        return (start + (1f - start) * progress).coerceIn(0f, 1f)
    }

    /**
     * Rebound duration for a cancelled drag. The rebound always starts from the
     * live progress and follows the shared easing; a near-complete gesture is the
     * only case that reaches the maximum, so the common drag settles quickly.
     */
    fun settleDurationMs(progress: Float): Long {
        if (!progress.isFinite()) return SETTLE_BASE_MS
        val start = progress.coerceIn(0f, 1f)
        return (SETTLE_BASE_MS + SETTLE_RANGE_MS * start)
            .toLong()
            .coerceIn(SETTLE_BASE_MS, SETTLE_MAX_MS)
    }

    private const val SETTLE_BASE_MS = 140L
    private const val SETTLE_RANGE_MS = 220L
    private const val SETTLE_MAX_MS = 360L
}
