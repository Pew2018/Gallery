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

    /** Room row identity is not rendered content; path remains the adapter identity. */
    fun sameContent(first: Directory, second: Directory) =
        first.copy(id = null) == second.copy(id = null) && first.folderStatuses == second.folderStatuses

    fun sameContents(first: List<Directory>, second: List<Directory>) =
        first.size == second.size && first.indices.all { sameContent(first[it], second[it]) }
}
