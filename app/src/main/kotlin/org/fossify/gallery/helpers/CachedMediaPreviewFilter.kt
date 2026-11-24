package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import java.io.File

/** Hides confirmed missing files from cache previews without changing durable state. */
internal object CachedMediaPreviewFilter {
    fun removeConfirmedMissing(media: List<Medium>): ArrayList<Medium> =
        media.filter(::isVisible).toCollection(ArrayList())

    /** One bounded existence check. Inaccessible parents stay eligible for later stages. */
    fun isVisible(item: Medium): Boolean {
        val file = runCatching { File(item.path) }.getOrNull() ?: return true
        val parent = file.parentFile ?: return true
        val readableParent = runCatching { parent.isDirectory && parent.canRead() }.getOrDefault(false)
        if (!readableParent) return true
        return runCatching { file.isFile }.getOrDefault(true)
    }
}
