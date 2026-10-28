package org.fossify.gallery.helpers

/**
 * Missing from a scan does not mean deleted: MediaStore filters, canceled scans,
 * storage unmounts, and revoked permissions all produce incomplete results.
 *
 * Never let a background scan remove a favorite relationship. A cached media row
 * may only be pruned when its parent is still readable and the file is absent.
 */
internal object MediaCachePrunePolicy {
    fun canPrune(
        oldPath: String,
        latestPaths: Set<String>,
        isFavoritesView: Boolean,
        parentReadable: Boolean,
        fileExists: Boolean,
    ): Boolean =
        oldPath.isNotBlank() &&
            !isFavoritesView &&
            oldPath !in latestPaths &&
            parentReadable &&
            !fileExists
}
