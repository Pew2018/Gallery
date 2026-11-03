package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** Optional metadata never holds the base media list or its loading indicator open. */
internal object VideoDurationEnricher {
    private val worker: Executor = BoundedScanExecutor("gallery-video-details", workers = 1, queued = 1)
    fun enrich(media: List<Medium>, current: () -> Boolean, read: (String) -> Int,
        publish: (ArrayList<Medium>) -> Unit, executor: Executor = worker) {
        val snapshot = media.map { it.copy() }
        try { executor.execute {
            val result = arrayListOf<Medium>()
            var changed = false
            var lastPublish = -250L
            for (item in snapshot) {
                if (!current()) return@execute
                var duration = item.videoDuration
                if (item.type == TYPE_VIDEOS && duration == 0) {
                    duration = try { read(item.path) } catch (_: Exception) { 0 }
                    if (!current()) return@execute
                    changed = changed || duration != 0
                }
                result.add(item.copy(videoDuration = duration))
                // Deliver each confirmed detail; one later damaged video cannot hide prior updates.
                val now = android.os.SystemClock.elapsedRealtime()
                if (changed && now - lastPublish >= 250 && current()) {
                    publish(ArrayList(result + snapshot.drop(result.size))); changed = false; lastPublish = now
                }
            }
            if (changed && current()) publish(result)
        } } catch (_: RejectedExecutionException) {
            // A busy optional lane leaves duration unknown; a base refresh has already completed.
        }
    }
}
