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

    /**
     * Validate only enough sorted cache rows to fill the initial grid window.
     * Missing rows are backfilled from the following sorted candidates.
     */
    fun initialWindow(
        media: List<Medium>, anchorPath: String?, limit: Int = PhotoProfileMediaCache.PREVIEW_LIMIT
    ): ArrayList<Medium> {
        if (media.isEmpty() || limit <= 0) return arrayListOf()

        val anchor = media.indexOfFirst { it.path == anchorPath }.coerceAtLeast(0)
        val start = (anchor - limit / 4).coerceAtLeast(0)
        val result = ArrayList<Medium>(minOf(limit, media.size - start))
        val readableParents = HashMap<String, Boolean>()

        var index = start
        while (index < media.size && result.size < limit) {
            val item = media[index++]
            val file = runCatching { File(item.path) }.getOrNull()
            if (file == null) {
                result.add(item)
                continue
            }

            val parent = file.parentFile
            if (parent == null) {
                result.add(item)
                continue
            }

            val readable = readableParents.getOrPut(parent.path) {
                runCatching { parent.isDirectory && parent.canRead() }.getOrDefault(false)
            }
            if (!readable || runCatching { file.isFile }.getOrDefault(true)) result.add(item)
        }
        return result
    }
}
