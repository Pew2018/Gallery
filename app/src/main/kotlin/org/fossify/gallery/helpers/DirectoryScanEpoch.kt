package org.fossify.gallery.helpers

import java.util.concurrent.atomic.AtomicLong

/** Retired requests cannot update directories after navigation or a newer load. */
internal class DirectoryScanEpoch {
    private val generation = AtomicLong(0)
    fun begin(): Long = generation.incrementAndGet()
    fun retire(): Long = generation.incrementAndGet()
    fun isCurrent(request: Long): Boolean = generation.get() == request
}
