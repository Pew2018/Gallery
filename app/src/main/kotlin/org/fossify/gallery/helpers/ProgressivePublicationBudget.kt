package org.fossify.gallery.helpers

/** Worker-local admission before cumulative copy, validation and sort. COMPLETE bypasses this budget. */
internal class ProgressivePublicationBudget(private val intervalMs: Long = 500L) {
    private var lastAt: Long? = null
    private var lastVersion = -1L
    fun admit(nowMs: Long, version: Long): Boolean {
        if (version == lastVersion) return false
        val last = lastAt
        if (last != null && nowMs - last < intervalMs) return false
        lastAt = nowMs
        lastVersion = version
        return true
    }
}
