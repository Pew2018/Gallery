package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Delete-only navigation. General asynchronous catalog updates keep their existing anchor policy. */
internal object ViewerDeletionNavigationPolicy {
    /**
     * Resolve a surviving path in this order: a page the user navigated to while
     * deletion was pending, the last visited page, the old grid predecessor,
     * the old grid successor, then the local list's predecessor/successor.
     * A partial catalog returns null when it contains no known replacement.
     */
    fun resolve(
        previousMedia: List<Medium>,
        freshMedia: List<Medium>,
        deletedPath: String,
        currentPath: String?,
        lastVisitedPath: String?,
        previousPosition: Int,
        orderedPaths: List<String>? = null,
        allowFirstFreshFallback: Boolean = false
    ): Int? {
        if (freshMedia.isEmpty()) return null
        val positions = HashMap<String, Int>(freshMedia.size)
        freshMedia.forEachIndexed { index, item -> positions.putIfAbsent(item.path, index) }
        fun find(path: String?): Int? = path?.takeIf { it.isNotEmpty() && it != deletedPath }?.let(positions::get)

        find(currentPath)?.let { return it }
        find(lastVisitedPath)?.let { return it }

        val order = orderedPaths.orEmpty()
        val deletedOrderPosition = order.indexOf(deletedPath)
        if (deletedOrderPosition >= 0) {
            for (index in deletedOrderPosition - 1 downTo 0) find(order[index])?.let { return it }
            for (index in deletedOrderPosition + 1 until order.size) find(order[index])?.let { return it }
        }

        val localDeletedPosition = previousMedia.indexOfFirst { it.path == deletedPath }
            .takeIf { it >= 0 } ?: previousPosition
        if (localDeletedPosition >= 0) {
            for (index in (localDeletedPosition - 1).coerceAtMost(previousMedia.lastIndex) downTo 0) {
                find(previousMedia[index].path)?.let { return it }
            }
            for (index in (localDeletedPosition + 1).coerceAtLeast(0)..previousMedia.lastIndex) {
                find(previousMedia[index].path)?.let { return it }
            }
        }
        return if (allowFirstFreshFallback) freshMedia.indices.firstOrNull() else null
    }

    /** Only a completed, authorized catalog can prove that deletion removed the final page. */
    fun shouldFinish(isComplete: Boolean, freshMedia: List<Medium>, needsUnlock: Boolean = false): Boolean =
        isComplete && freshMedia.isEmpty() && !needsUnlock
}
