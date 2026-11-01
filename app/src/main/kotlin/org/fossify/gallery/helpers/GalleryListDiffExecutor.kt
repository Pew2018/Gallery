package org.fossify.gallery.helpers

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** UI list reconciliation has its own bounded worker count, separate from media scans. */
internal object GalleryListDiffExecutor : Executor {
    private val workers = Executors.newFixedThreadPool(2) { work ->
        Thread(work, "gallery-list-diff").apply { isDaemon = true }
    }
    override fun execute(command: Runnable) = workers.execute(command)
}
