package org.fossify.gallery.helpers

/** Fresh membership wins; preserve the user's existing random sequence for surviving items. */
internal object FolderViewerMediaPolicy {
    fun <T> reconcile(previous: List<T>, fresh: List<T>, random: Boolean, path: (T) -> String): List<T> {
        val byPath = fresh.associateBy(path)
        if (!random) return byPath.values.toList()
        val retained = previous.mapNotNull { byPath[path(it)] }.distinctBy(path)
        val retainedPaths = retained.map(path).toHashSet()
        return retained + byPath.values.filter { path(it) !in retainedPaths }
    }
}
