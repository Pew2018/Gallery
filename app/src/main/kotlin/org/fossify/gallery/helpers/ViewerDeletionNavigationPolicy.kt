package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/**
 * Delete-only navigation. Missing paths during a staged scan are not evidence
 * that the viewer should jump to the start of the newly scanned collection.
 */
internal object ViewerDeletionNavigationPolicy {
    private const val MAX_NEIGHBOR_DISTANCE = 8

    /** Historical positions are useful only within a bounded neighborhood of the deleted page. */
    private fun neighboringPaths(
        previousMedia: List<Medium>,
        deletedPath: String,
        previousPosition: Int,
        orderedPaths: List<String>?,
        lastMoveForward: Boolean?
    ): List<String> {
        val candidates = LinkedHashSet<String>()
        fun addNeighbors(paths: List<String>, center: Int) {
            if (center !in paths.indices) return
            fun before() {
                for (distance in 1..MAX_NEIGHBOR_DISTANCE) {
                    val index = center - distance
                    if (index < 0) break
                    if (paths[index] != deletedPath) candidates.add(paths[index])
                }
            }
            fun after() {
                for (distance in 1..MAX_NEIGHBOR_DISTANCE) {
                    val index = center + distance
                    if (index >= paths.size) break
                    if (paths[index] != deletedPath) candidates.add(paths[index])
                }
            }
            // D -> C means D is the best fallback if the actual history is lost.
            if (lastMoveForward == false) { after(); before() }
            else { before(); after() }
        }

        orderedPaths?.let { addNeighbors(it, it.indexOf(deletedPath)) }
        val localCenter = previousMedia.indexOfFirst { it.path == deletedPath }
            .takeIf { it >= 0 } ?: previousPosition
        addNeighbors(previousMedia.map { it.path }, localCenter)
        return candidates.toList()
    }

    /**
     * Only a confirmed user page change after the delete request may outrank
     * the history frozen when that request was made.
     */
    fun postDeleteUserPath(
        deletedPath: String,
        snapshotNavigationEpoch: Long,
        currentNavigationEpoch: Long,
        confirmedUserPath: String?
    ): String? = confirmedUserPath?.takeIf {
        currentNavigationEpoch > snapshotNavigationEpoch && it != deletedPath
    }

    /**
     * A provisional scan should center on an actual prior page or an old
     * neighbor, never on the deleted file. This is metadata, not access permission.
     */
    fun recoveryAnchor(
        previousMedia: List<Medium>,
        deletedPath: String,
        lastVisitedPath: String?,
        previousPosition: Int,
        orderedPaths: List<String>? = null,
        lastMoveForward: Boolean? = null
    ): String? =
        lastVisitedPath?.takeIf { it.isNotBlank() && it != deletedPath }
            ?: neighboringPaths(previousMedia, deletedPath, previousPosition,
                orderedPaths, lastMoveForward).firstOrNull()

    /**
     * Choose a surviving path in this order: a confirmed user navigation after
     * deletion, the frozen previous user page, the bounded grid neighbors, and
     * then the bounded old viewer-window neighbors. No integer-index fallback.
     * Return null while the available catalog cannot prove a nearby replacement.
     */
    fun resolve(
        previousMedia: List<Medium>,
        freshMedia: List<Medium>,
        deletedPath: String,
        currentPath: String?,
        lastVisitedPath: String?,
        previousPosition: Int,
        orderedPaths: List<String>? = null,
        lastMoveForward: Boolean? = null
    ): Int? {
        if (freshMedia.isEmpty()) return null
        val positions = HashMap<String, Int>(freshMedia.size)
        freshMedia.forEachIndexed { index, item -> positions.putIfAbsent(item.path, index) }
        fun find(path: String?): Int? = path
            ?.takeIf { it.isNotBlank() && it != deletedPath }
            ?.let(positions::get)

        find(currentPath)?.let { return it }
        find(lastVisitedPath)?.let { return it }
        return neighboringPaths(previousMedia, deletedPath, previousPosition,
            orderedPaths, lastMoveForward).firstNotNullOfOrNull(positions::get)
    }

    /** Only a completed, authorized catalog can prove that deletion removed the final page. */
    fun shouldFinish(isComplete: Boolean, freshMedia: List<Medium>, needsUnlock: Boolean = false): Boolean =
        isComplete && freshMedia.isEmpty() && !needsUnlock
}
