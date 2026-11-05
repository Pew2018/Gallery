package org.fossify.gallery.helpers

import java.util.concurrent.atomic.AtomicLong

/** Gives each user sorting choice its own publication identity, including A → B → A. */
internal class MediaSortOperationGate {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate(): Long = generation.incrementAndGet()

    fun isCurrent(operation: Long): Boolean = generation.get() == operation
}
