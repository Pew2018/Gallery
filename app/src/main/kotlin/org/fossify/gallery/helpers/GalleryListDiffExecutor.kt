package org.fossify.gallery.helpers

import java.util.concurrent.Executor

/** List diffs have their own strictly bounded queue, separate from media scans. */
internal object GalleryListDiffExecutor : Executor {
    private val workers = BoundedScanExecutor("gallery-list-diff", workers = 2, queued = 4)
    override fun execute(command: Runnable) = workers.execute(command)
}
