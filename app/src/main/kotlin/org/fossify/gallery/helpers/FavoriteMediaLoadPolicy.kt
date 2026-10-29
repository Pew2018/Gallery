package org.fossify.gallery.helpers

/**
 * Non-destructive favorite loading rules. Paths are compared exactly:
 * Android file paths are case-sensitive and can contain meaningful punctuation.
 */
internal object FavoriteMediaLoadPolicy {
    fun distinctSavedPaths(paths: List<String>): ArrayList<String> =
        paths.filter { it.isNotBlank() }.distinct().toCollection(ArrayList())

    /** Prefer MediaStore metadata when it is available, retaining file-only favorites. */
    fun <T> mergeByPath(files: List<T>, indexed: List<T>, pathOf: (T) -> String): ArrayList<T> {
        val merged = LinkedHashMap<String, T>(files.size + indexed.size)
        files.forEach { merged[pathOf(it)] = it }
        indexed.forEach { merged[pathOf(it)] = it }
        return ArrayList(merged.values)
    }
}

/** UI-owned request generations also cover late cache callbacks, not only scan callbacks. */
internal class FavoriteMediaLoadRequests {
    @Volatile private var generation = 0L

    fun begin(): Long = ++generation

    fun isCurrent(request: Long): Boolean = request == generation

    fun invalidate() {
        generation++
    }
}
