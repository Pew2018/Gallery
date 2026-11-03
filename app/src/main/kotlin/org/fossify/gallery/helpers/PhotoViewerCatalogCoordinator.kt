package org.fossify.gallery.helpers

/** Navigation state is separate from both the five-file access window and the adapter. */
internal class PhotoViewerCatalogCoordinator {
    enum class Boundary { UNKNOWN, HAS_MORE, END }
    enum class State { LOADING, PARTIAL, COMPLETE, INVALID }
    data class Pending(val anchor: String, val forward: Boolean)

    var state = State.LOADING
        private set
    var before = Boundary.UNKNOWN
        private set
    var after = Boundary.UNKNOWN
        private set
    var pending: Pending? = null
        private set

    fun publish(complete: Boolean, before: Boundary = Boundary.UNKNOWN, after: Boundary = Boundary.UNKNOWN) {
        state = if (complete) State.COMPLETE else State.PARTIAL
        this.before = if (complete) Boundary.END else before
        this.after = if (complete) Boundary.END else after
    }

    fun request(anchor: String, forward: Boolean): Boolean {
        if (state == State.INVALID || (if (forward) after else before) == Boundary.END) return false
        pending = Pending(anchor, forward)
        return true
    }

    /** Resolve against the user's live selection, never against the original click. */
    fun destination(paths: List<String>, livePath: String): Int? {
        val wait = pending ?: return null
        if (wait.anchor != livePath) { pending = null; return null }
        val anchor = paths.indexOf(wait.anchor)
        val target = anchor + if (wait.forward) 1 else -1
        if (anchor >= 0 && target in paths.indices) { pending = null; return target }
        if ((if (wait.forward) after else before) == Boundary.END) pending = null
        return null
    }

    fun cancel() { pending = null }
    fun invalidate() {
        state = State.INVALID
        before = Boundary.UNKNOWN
        after = Boundary.UNKNOWN
        pending = null
    }
}
