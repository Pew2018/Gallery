package org.fossify.gallery.helpers

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local change notification for the Photos virtual album.
 * Reading a photo never increments this revision; successful media mutations do.
 */
internal object PhotoContentRevision {
    private val value = AtomicLong()
    fun current(): Long = value.get()
    fun changed(): Long {
        PhotoProfileMediaCache.invalidate()
        return value.incrementAndGet()
    }
}
