package org.fossify.gallery.helpers

import java.util.concurrent.atomic.AtomicLong

/** Process-local epoch for Room membership changes in the virtual Recycle Bin. */
internal object RecycleBinMembershipRevision {
    private val revision = AtomicLong()
    fun current(): Long = revision.get()
    fun changed(): Long = revision.incrementAndGet()
}
