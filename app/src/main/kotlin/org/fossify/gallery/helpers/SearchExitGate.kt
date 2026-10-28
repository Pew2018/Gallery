package org.fossify.gallery.helpers

/** Prevent repeated exits and stale posted focus requests. */
internal class SearchExitGate {
    var exiting: Boolean = false
        private set

    fun beginExit(): Boolean {
        if (exiting) return false
        exiting = true
        return true
    }
}
