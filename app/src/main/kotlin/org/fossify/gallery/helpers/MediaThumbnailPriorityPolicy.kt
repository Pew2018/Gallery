package org.fossify.gallery.helpers

import com.bumptech.glide.Priority

/** Keeps visible media ahead of prefetch and offscreen thumbnail work. */
internal object MediaThumbnailPriorityPolicy {
    fun priority(
        position: Int,
        firstVisible: Int,
        lastVisible: Int,
        initialViewportSize: Int = 24
    ): Priority {
        if (firstVisible < 0 || lastVisible < 0) {
            val viewportSize = initialViewportSize.coerceAtLeast(1)
            return when {
                position in 0 until viewportSize -> Priority.HIGH
                position < viewportSize * 2 -> Priority.NORMAL
                else -> Priority.LOW
            }
        }
        val start = minOf(firstVisible, lastVisible)
        val end = maxOf(firstVisible, lastVisible)
        val viewportSize = (end - start + 1).coerceAtLeast(1)

        return when {
            position in start..end -> Priority.HIGH
            position > end && position <= end + viewportSize -> Priority.NORMAL
            position < start && position >= start - viewportSize -> Priority.NORMAL
            else -> Priority.LOW
        }
    }
}
