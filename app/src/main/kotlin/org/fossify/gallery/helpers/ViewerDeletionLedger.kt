package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Main-thread mutation barriers. Only a later, verified source read can retire a barrier. */
internal class ViewerDeletionLedger {
    private var lastLaunch = 0L
    private val delivered = LinkedHashSet<String>()
    private val blockedThrough = HashMap<String, Long>()

    fun hasNew(launch: Long, paths: Set<String>): Boolean =
        paths.isNotEmpty() && launch >= lastLaunch && (launch > lastLaunch || paths.any { it !in delivered })

    fun confirm(launch: Long, paths: Set<String>, request: Long): Set<String> {
        if (!hasNew(launch, paths)) return emptySet()
        if (launch != lastLaunch) {
            lastLaunch = launch
            delivered.clear()
        }
        return paths.filterTo(LinkedHashSet()) { path ->
            delivered.add(path).also { fresh -> if (fresh) blockedThrough[path] = request }
        }
    }

    fun restorationCandidates(items: List<ThumbnailItem>, request: Long): Set<String> {
        if (blockedThrough.isEmpty()) return emptySet()
        return items.asSequence().filterIsInstance<Medium>().map { it.path }
            .filter { path -> blockedThrough[path]?.let { request > it } == true }.toSet()
    }

    fun filter(items: List<ThumbnailItem>, request: Long, verifiedPaths: Set<String>): ArrayList<ThumbnailItem> {
        for (path in verifiedPaths) {
            if (blockedThrough[path]?.let { request > it } == true) blockedThrough.remove(path)
        }
        return MediaUpdatePolicy.withoutDeletedPaths(items, blockedThrough.keys)
    }
}
