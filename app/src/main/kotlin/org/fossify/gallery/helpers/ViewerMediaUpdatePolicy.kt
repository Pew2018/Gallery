package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** Stable identity for one viewer page. Mutable metadata and content facts live elsewhere. */
data class ViewerMediumIdentity(val path: String, val type: Int)

/** Content facts that are safe to compare. Zero means the source did not provide that fact. */
internal data class ViewerContentSignature(
    val size: Long,
    val modified: Long,
    val mediaStoreId: Long,
    val folderRevision: Long,
    val photoRevision: Long
)

internal data class ViewerMediaUpdate(
    val currentPosition: Int,
    val survivingIdentities: Set<ViewerMediumIdentity>,
    val invalidatedIdentities: Set<ViewerMediumIdentity>
)

internal object ViewerMediaUpdatePolicy {
    fun identity(medium: Medium) = ViewerMediumIdentity(medium.path, medium.type)

    fun contentSignature(medium: Medium) = ViewerContentSignature(
        medium.size.takeIf { it > 0L } ?: 0L,
        medium.modified.takeIf { it > 0L } ?: 0L,
        medium.mediaStoreId.takeIf { it > 0L } ?: 0L,
        FolderContentRevision.current(),
        PhotoContentRevision.current()
    )

    /** Unknown facts do not conflict with known facts, so provisional rows can be enriched in place. */
    fun contentChanged(
        old: ViewerContentSignature,
        fresh: ViewerContentSignature
    ): Boolean = old.folderRevision != fresh.folderRevision || old.photoRevision != fresh.photoRevision ||
        knownConflict(old.size, fresh.size) || knownConflict(old.modified, fresh.modified) ||
        knownConflict(old.mediaStoreId, fresh.mediaStoreId)

    fun mergeSignature(old: ViewerContentSignature, fresh: ViewerContentSignature) = ViewerContentSignature(
        knownValue(old.size, fresh.size),
        knownValue(old.modified, fresh.modified),
        knownValue(old.mediaStoreId, fresh.mediaStoreId),
        fresh.folderRevision,
        fresh.photoRevision
    )

    /** Keep the most complete row while taking presentation and state fields from the newest result. */
    fun mergeMetadata(old: Medium, fresh: Medium): Medium {
        if (identity(old) != identity(fresh)) return fresh.copy()
        return fresh.copy(
            id = fresh.id ?: old.id,
            name = fresh.name.ifBlank { old.name },
            parentPath = fresh.parentPath.ifBlank { old.parentPath },
            modified = knownValue(old.modified, fresh.modified),
            taken = knownValue(old.taken, fresh.taken),
            size = knownValue(old.size, fresh.size),
            videoDuration = if (fresh.videoDuration > 0) fresh.videoDuration else old.videoDuration,
            mediaStoreId = knownValue(old.mediaStoreId, fresh.mediaStoreId),
            gridPosition = fresh.gridPosition
        )
    }

    fun update(
        previous: List<Medium>,
        fresh: List<Medium>,
        currentPath: String,
        previousPosition: Int
    ): ViewerMediaUpdate {
        val oldIds = previous.mapTo(HashSet(previous.size), ::identity)
        val freshIds = fresh.mapTo(HashSet(fresh.size), ::identity)
        val currentPosition = fresh.indexOfFirst { it.path == currentPath }.let { found ->
            when {
                found >= 0 -> found
                fresh.isEmpty() || previousPosition < 0 -> -1
                else -> previousPosition.coerceIn(0, fresh.lastIndex)
            }
        }
        return ViewerMediaUpdate(
            currentPosition = currentPosition,
            survivingIdentities = oldIds intersect freshIds,
            invalidatedIdentities = oldIds - freshIds
        )
    }

    fun positionIndex(media: List<Medium>): Map<ViewerMediumIdentity, Int> =
        HashMap<ViewerMediumIdentity, Int>(media.size).apply {
            media.forEachIndexed { index, medium ->
                val key = identity(medium)
                if (!containsKey(key)) put(key, index)
            }
        }

    fun pathIndex(media: List<Medium>): Map<String, Int> = HashMap<String, Int>(media.size).apply {
        media.forEachIndexed { index, medium -> if (!containsKey(medium.path)) put(medium.path, index) }
    }

    fun sameCatalog(first: List<Medium>, second: List<Medium>): Boolean =
        first.size == second.size && first.indices.all { first[it] == second[it] }

    private fun knownConflict(old: Long, fresh: Long) = old > 0L && fresh > 0L && old != fresh
    private fun knownValue(old: Long, fresh: Long) = if (fresh > 0L) fresh else old
}
