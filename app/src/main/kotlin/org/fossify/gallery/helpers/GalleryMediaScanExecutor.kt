package org.fossify.gallery.helpers

import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Canceled AsyncTask FutureTasks are removed before the next request is admitted. */
internal class BoundedScanExecutor(name: String, workers: Int = 2, queued: Int = 4) : Executor {
    private val pool = ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(queued), { task -> Thread(task, name).apply { isDaemon = true } })
    override fun execute(command: Runnable) { pool.purge(); pool.execute(command) }
    internal fun shutdown() = pool.shutdownNow()
}

/** Album discovery cannot consume foreground folder workers. Both lanes remain strictly bounded. */
internal object GalleryMediaScanExecutor : Executor {
    private val workers = BoundedScanExecutor("gallery-media-scan")
    override fun execute(command: Runnable) = workers.execute(command)
}
internal object GalleryAlbumScanExecutor : Executor {
    private val workers = BoundedScanExecutor("gallery-album-scan")
    override fun execute(command: Runnable) = workers.execute(command)
}

/**
 * Deferred single-folder metadata retries must not occupy the two full-album
 * workers. A single bounded worker prevents many pending paths from flooding CI
 * or normal UI scanning; retryRunning coalesces concurrent requests.
 */
internal object GalleryDirectoryMetadataExecutor : Executor {
    private val worker = BoundedScanExecutor("gallery-metadata-retry", workers = 1, queued = 1)
    override fun execute(command: Runnable) = worker.execute(command)
}
