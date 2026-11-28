package org.fossify.gallery.helpers

/**
 * One screen-sized decode ahead of the current viewer page.
 * This is not the grid return prefetch: it never reserves pixels and it follows
 * the next page in the last swipe direction, not the page on screen.
 *
 * At most one request is in flight. A direction change or a move away from the
 * page that armed it cancels that request. Rapid paging only re-arms the timer,
 * so frames that fly past are not decoded.
 */
internal class ViewerScreenPrefetchCoordinator(private val stabilityMs: Long = STABILITY_MS) {
    data class Change(val cancelInFlight: Boolean, val armDelayMs: Long?)

    private var nextPath: String? = null
    private var inFlightPath: String? = null
    private var preparedPath: String? = null
    private var armedAtMs = 0L

    val runningPath: String? get() = inFlightPath
    val targetPath: String? get() = nextPath

    fun onTargetChanged(nextPath: String?, nowMs: Long): Change {
        val cancel = inFlightPath != null && inFlightPath != nextPath
        if (cancel) inFlightPath = null
        if (nextPath != this.nextPath) armedAtMs = nowMs
        this.nextPath = nextPath
        val arm = nextPath != null && nextPath != preparedPath && nextPath != inFlightPath
        return Change(cancelInFlight = cancel, armDelayMs = if (arm) stabilityMs else null)
    }

    /** Returns the single path to decode once the page has stayed put for [stabilityMs]. */
    fun onStabilityElapsed(nowMs: Long): String? {
        val path = nextPath ?: return null
        if (nowMs - armedAtMs < stabilityMs) return null
        if (path == preparedPath || path == inFlightPath) return null
        return path
    }

    fun onStart(path: String) {
        inFlightPath = path
    }

    fun onReady(path: String) {
        if (inFlightPath == path) inFlightPath = null
        preparedPath = path
    }

    fun onFailed(path: String) {
        if (inFlightPath == path) inFlightPath = null
    }

    fun onCancelled(path: String?) {
        if (path != null && inFlightPath == path) inFlightPath = null
    }

    fun reset() {
        nextPath = null
        inFlightPath = null
        preparedPath = null
        armedAtMs = 0L
    }

    companion object {
        const val STABILITY_MS = 150L
    }
}
