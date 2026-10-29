package org.fossify.gallery.helpers

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded worker pool for viewer authorization batches. */
internal object ViewerAccessCoordinator {
    private val executor = ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(8),
        { runnable -> Thread(runnable, "gallery-viewer-access").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    fun submit(task: () -> Unit): Future<*> = executor.submit { task() }

    fun cancel(task: Future<*>?) {
        task?.cancel(true)
        executor.purge()
    }
}
