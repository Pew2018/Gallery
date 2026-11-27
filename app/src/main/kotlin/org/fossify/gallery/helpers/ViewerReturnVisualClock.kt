package org.fossify.gallery.helpers

/**
 * Single-clock visual remainder for one Viewer shared-element return.
 *
 * The shared element transition already renders progress 1 as the final frame, so
 * the terminal callback must not force the same visual state a second time. The
 * terminal write is still needed for an interrupted transition, therefore it stays
 * available but is admitted at most once and only while the return has not already
 * reached its destination. A cancelled terminal never rewrites the live frame: the
 * caller restores or fades that frame through its own recovery path.
 */
internal class ViewerReturnVisualClock {
    var remainder = 1f
        private set
    var terminalApplied = false
        private set

    /** Arms a new return at progress 0. Old terminals are no longer admissible. */
    fun begin() {
        remainder = 1f
        terminalApplied = false
    }

    /** Returns the remainder that should be rendered for a transition progress. */
    fun progress(progress: Float): Float {
        if (terminalApplied) return remainder
        remainder = (1f - progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        return remainder
    }

    /** True only when the terminal frame still needs to be committed. */
    fun terminal(cancelled: Boolean): Boolean {
        if (terminalApplied) return false
        terminalApplied = true
        if (cancelled || remainder <= 0f) return false
        remainder = 0f
        return true
    }
}
