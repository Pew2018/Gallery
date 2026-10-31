package org.fossify.gallery.helpers

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local invalidation for ordinary album return reuse. Conservative:
 * it is always safe to invalidate on an operation that may change file metadata.
 */
internal object FolderContentRevision {
    private val version = AtomicLong()
    fun current(): Long = version.get()
    fun changed(): Long = version.incrementAndGet()
}
