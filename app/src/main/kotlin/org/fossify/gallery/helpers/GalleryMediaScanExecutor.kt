package org.fossify.gallery.helpers

import java.util.concurrent.Executors
import java.util.concurrent.Executor

/** Bounded media workers, separate from AsyncTask's process-global serial queue. */
internal object GalleryMediaScanExecutor : Executor {
    private val workers = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "gallery-media-scan").apply { isDaemon = true }
    }
    override fun execute(command: Runnable) = workers.execute(command)
}
