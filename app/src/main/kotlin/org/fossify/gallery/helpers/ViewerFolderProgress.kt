package org.fossify.gallery.helpers

/** UI-thread owner of one retryable folder producer; late callbacks cannot retire a retry. */
internal class ViewerFolderScanGate {
    private var sequence = 0L
    private var active: Long? = null
    val running get() = active != null
    fun begin(): Long? = if (running) null else (++sequence).also { active = it }
    fun current(attempt: Long) = active == attempt
    fun finish(attempt: Long): Boolean {
        if (!current(attempt)) return false
        active = null
        return true
    }
    fun cancel() { active = null; sequence++ }
}

/** A bounded run from committed display evidence. It is neither authorization nor proof of END. */
internal object ViewerCommittedWindow {
    const val RADIUS = 64
    fun <T> around(items: List<T>, anchor: String, path: (T) -> String): List<T> {
        val index = items.indexOfFirst { path(it) == anchor }
        if (index < 0) return emptyList()
        return items.subList((index - RADIUS).coerceAtLeast(0),
            (index + RADIUS + 1).coerceAtMost(items.size))
    }

    /** Read the known neighborhood first, but still enumerate every remaining file exactly once. */
    fun <T> prioritize(files: List<T>, order: List<String>?, path: (T) -> String): List<T> {
        if (order.isNullOrEmpty()) return files
        val byPath = files.associateBy(path).toMutableMap()
        val result = ArrayList<T>(files.size)
        order.forEach { byPath.remove(it)?.let(result::add) }
        files.forEach { if (byPath.remove(path(it)) != null) result.add(it) }
        return result
    }
}
