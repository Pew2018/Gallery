package org.fossify.gallery.helpers

/** Fair, event-driven retry queue. Each wake takes a finite snapshot; failures never self-schedule. */
internal class MetadataRetryQueue(private val clock: () -> Long, private val cooldownMs: Long = 30_000L) {
    private val paths = linkedSetOf<String>()
    private val retryAfter = hashMapOf<String, Long>()
    @Synchronized fun add(path: String) { paths.add(path) }
    @Synchronized fun remove(path: String) { paths.remove(path); retryAfter.remove(path) }
    @Synchronized fun snapshot(): Set<String> = paths.toSet()
    @Synchronized fun ready(): List<String> = paths.filter { (retryAfter[it] ?: 0L) <= clock() }
    @Synchronized fun failed(path: String) {
        if (path !in paths) return
        paths.remove(path); paths.add(path)
        retryAfter[path] = clock() + cooldownMs
    }
}
