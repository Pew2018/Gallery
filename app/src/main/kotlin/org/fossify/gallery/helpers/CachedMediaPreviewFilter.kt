package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import java.io.File

/** Hides confirmed missing files from cache previews without changing durable state. */
internal object CachedMediaPreviewFilter {
    fun removeConfirmedMissing(media: List<Medium>): ArrayList<Medium> = media.filter { item ->
        val file = runCatching { File(item.path) }.getOrNull() ?: return@filter true
        val parent = file.parentFile ?: return@filter true
        val readableParent = runCatching { parent.isDirectory && parent.canRead() }.getOrDefault(false)
        if (!readableParent) return@filter true
        runCatching { file.isFile }.getOrDefault(true)
    }.toCollection(ArrayList())
}
