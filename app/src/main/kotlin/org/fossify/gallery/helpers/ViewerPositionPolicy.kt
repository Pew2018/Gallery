package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Keeps the live page anchored when a staged viewer list is replaced asynchronously. */
internal object ViewerPositionPolicy {
    fun resolve(pathIndex: Map<String, Int>, currentPath: String, previousPosition: Int, mediaSize: Int): Int {
        val current = pathIndex[currentPath]
        return when {
            current != null -> current
            mediaSize == 0 || previousPosition < 0 -> -1
            else -> previousPosition.coerceIn(0, mediaSize - 1)
        }
    }

    fun resolve(media: List<Medium>, currentPath: String, previousPosition: Int): Int {
        var current = -1
        media.forEachIndexed { index, item ->
            if (current < 0 && currentPath.isNotEmpty() && item.path == currentPath) current = index
        }
        return when {
            current >= 0 -> current
            previousPosition >= 0 && media.isNotEmpty() -> previousPosition.coerceIn(0, media.lastIndex)
            else -> -1
        }
    }
}
