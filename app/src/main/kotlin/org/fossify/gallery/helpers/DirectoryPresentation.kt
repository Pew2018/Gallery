package org.fossify.gallery.helpers

import org.fossify.gallery.models.Directory

object DirectoryPresentation {
    /** Existing cards stay in place during a scan; newly discovered cards append once. */
    fun retainOrder(incoming: List<Directory>, displayed: List<Directory>): ArrayList<Directory> {
        val remaining = incoming.associateByTo(LinkedHashMap()) { it.path }
        val ordered = ArrayList<Directory>(incoming.size)
        displayed.forEach { remaining.remove(it.path)?.let(ordered::add) }
        ordered.addAll(remaining.values)
        return ordered
    }

    /** Adapter cards may contain aggregate statistics. Only their presentation
     * metadata can be merged back into the direct-member catalog after a mutation.
     */
    fun mergeCatalogMetadata(source: List<Directory>, cards: List<Directory>, removedPaths: Set<String>): ArrayList<Directory> {
        val updates = cards.associateBy { it.path }
        return source.filterNot { directory -> removedPaths.any { path ->
            directory.path == path || directory.path.startsWith("${path.trimEnd('/')}/")
        } }.mapTo(ArrayList()) { directory ->
            directory.snapshot().apply {
                updates[path]?.let { card -> name = card.name; tmb = card.tmb; folderStatuses = card.folderStatuses }
            }
        }
    }

    /** Room row identity is not rendered content; path remains the adapter identity. */
    fun sameContent(first: Directory, second: Directory) =
        first.copy(id = null, stats = first.stats.copy(scannedAt = 0)) ==
            second.copy(id = null, stats = second.stats.copy(scannedAt = 0)) && first.folderStatuses == second.folderStatuses

    fun sameContents(first: List<Directory>, second: List<Directory>) =
        first.size == second.size && first.indices.all { sameContent(first[it], second[it]) }
}
