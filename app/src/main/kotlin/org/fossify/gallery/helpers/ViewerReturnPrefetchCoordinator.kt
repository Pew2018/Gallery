package org.fossify.gallery.helpers

/**
 * Bookkeeping for the Viewer return prefetch (Phase 2.2 P1-A), split out of the Grid
 * Activity so the decisions are deterministic and testable without a device.
 *
 * It owns exactly three facts - which media the Viewer is showing, which media has a
 * request in flight, and which media is already prepared - and answers two questions:
 * what to do when the page changes, and what to start once the page has settled.
 * Side effects (posting the stability window, clearing a Glide target, copying pixels)
 * stay in the caller; the clock is injected so a test can drive §11.2's latency table.
 *
 * Invariants pinned by [ViewerReturnPrefetchCoordinatorTest]:
 *  - at most one request is in flight, and a page change away from it cancels it;
 *  - an unchanged page never re-arms, and a settled page starts at most one request
 *    (no duplicate work while a request is already running or prepared);
 *  - a cancelled or failed preparation is not remembered as prepared, so returning to
 *    that media prepares it again;
 *  - [reset] drops everything with the Viewer session.
 */
internal class ViewerReturnPrefetchCoordinator(private val stabilityMs: Long = STABILITY_MS) {

    /** What the caller must do right after a page change. */
    data class PageChange(
        /** The in-flight request belongs to the previous page and must be cleared. */
        val cancelInFlight: Boolean,
        /** Delay before [onStabilityElapsed] should be invoked; null = don't arm. */
        val armDelayMs: Long?,
    )

    private var currentPath: String? = null
    private var inFlightPath: String? = null
    private var preparedPath: String? = null
    private var selectedAtMs = 0L

    val pendingPath: String? get() = currentPath
    val runningPath: String? get() = inFlightPath
    val readyPath: String? get() = preparedPath

    fun onPageChanged(path: String?, nowMs: Long): PageChange {
        val cancel = inFlightPath != null && inFlightPath != path
        if (cancel) inFlightPath = null
        if (path != currentPath) selectedAtMs = nowMs
        currentPath = path
        // Prepare when there is media to prepare and this page has neither pixels
        // reserved already (a reserve hit is what the return will use) nor a request
        // still running for it (paging back to the page being prepared).
        val arm = path != null && path != preparedPath && path != inFlightPath
        return PageChange(cancelInFlight = cancel, armDelayMs = if (arm) stabilityMs else null)
    }

    /**
     * The stability window elapsed. Returns the path a request should be started for,
     * or null when the page moved on, nothing is selected, it is already prepared or a
     * request for it is still running.
     */
    fun onStabilityElapsed(nowMs: Long): String? {
        val path = currentPath ?: return null
        // A callback armed for an earlier page must not shorten the current page's
        // window: the caller cancels those, but a late one can still arrive.
        if (nowMs - selectedAtMs < stabilityMs) return null
        if (path == preparedPath || path == inFlightPath) return null
        lastSettledAtMs = nowMs
        return path
    }

    /** A request for [path] has been handed to Glide. */
    fun onStart(path: String) {
        inFlightPath = path
    }

    /** The request for [path] completed; [stored] reports whether pixels were reserved. */
    fun onFinished(path: String, stored: Boolean) {
        if (inFlightPath == path) inFlightPath = null
        if (stored) preparedPath = path
    }

    /** The request for [path] failed; a later visit to it may try again. */
    fun onFailed(path: String) {
        if (inFlightPath == path) inFlightPath = null
    }

    /** The caller cancelled the in-flight request (page change, session end, supersede). */
    fun onCancelled(path: String?) {
        if (path != null && inFlightPath == path) inFlightPath = null
    }

    fun reset() {
        currentPath = null
        inFlightPath = null
        preparedPath = null
        selectedAtMs = 0L
        lastSettledAtMs = 0L
    }

    /** Monotonic time of the last settled page, for diagnostics only. */
    var lastSettledAtMs = 0L
        private set

    companion object {
        /**
         * Short stability window, armed from the Viewer's own page-settle events. It
         * only debounces rapid paging - the session still owns cancellation and release.
         */
        const val STABILITY_MS = 150L
    }
}
