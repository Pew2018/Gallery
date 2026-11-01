package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/** A fragment may only survive a catalog update when every value that affects its content is stable. */
data class ViewerMediumIdentity(
    val path: String,
    val databaseId: Long?,
    val mediaStoreId: Long,
    val name: String,
    val type: Int,
    val size: Long,
    val modified: Long,
    val taken: Long,
    val duration: Int,
    val deletedAt: Long
)

internal data class ViewerMediaUpdate(
    val currentPosition: Int,
    val survivingIdentities: Set<ViewerMediumIdentity>,
    val invalidatedIdentities: Set<ViewerMediumIdentity>
)

internal object ViewerMediaUpdatePolicy {
    fun identity(medium: Medium) = ViewerMediumIdentity(
        path = medium.path,
        databaseId = medium.id,
        mediaStoreId = medium.mediaStoreId,
        name = medium.name,
        type = medium.type,
        size = medium.size,
        modified = medium.modified,
        taken = medium.taken,
        duration = medium.videoDuration,
        deletedAt = medium.deletedTS
    )

    fun update(
        previous: List<Medium>,
        fresh: List<Medium>,
        currentPath: String,
        previousPosition: Int
    ): ViewerMediaUpdate {
        val oldIds = HashSet<ViewerMediumIdentity>(previous.size).apply {
            previous.forEach { add(identity(it)) }
        }
        val freshIds = HashSet<ViewerMediumIdentity>(fresh.size).apply {
            fresh.forEach { add(identity(it)) }
        }
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
}
