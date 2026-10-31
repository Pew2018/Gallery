package org.fossify.gallery.helpers

/** A public physical folder may supply its already-finished media list to the viewer. */
internal object FolderViewerSnapshotPolicy {
    fun isEligible(
        folder: String,
        selectedPath: String,
        paths: List<String>,
        isProtected: Boolean,
        isExcluded: Boolean
    ): Boolean = folder.startsWith('/') && folder.length > 1 &&
        !folder.contains("/.") && !isProtected && !isExcluded &&
        paths.isNotEmpty() && selectedPath in paths &&
        paths.all { it.substringBeforeLast('/', "") == folder }
}
