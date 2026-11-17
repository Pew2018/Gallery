package org.fossify.gallery.helpers

/** One handoff at a time. Worker checks are advisory until main consumes the ticket. */
internal class ViewerLaunchGate {
    private var generation = 0L
    @Volatile private var pending: Long? = null

    fun begin(returnOrViewerActive: Boolean): Long? {
        if (returnOrViewerActive || pending != null) return null
        return (++generation).also { pending = it }
    }

    fun isCurrent(ticket: Long): Boolean = pending == ticket

    fun consume(ticket: Long): Boolean {
        if (!isCurrent(ticket)) return false
        pending = null
        return true
    }

    fun cancel() { pending = null }
}
