package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.models.Medium
import java.io.File

/** Worker-only enrichment of the displayed favorites, never a second collection scan/cache. */
internal object FavoriteSortMetadata {
    fun requiresRead(sorting: Int, grouping: Int): Boolean =
        sorting and (SORT_BY_DATE_TAKEN or SORT_BY_DATE_MODIFIED or SORT_BY_SIZE) != 0 ||
            grouping and (GROUP_BY_DATE_TAKEN_DAILY or GROUP_BY_DATE_TAKEN_MONTHLY or
                GROUP_BY_LAST_MODIFIED_DAILY or GROUP_BY_LAST_MODIFIED_MONTHLY) != 0

    fun prepare(context: Context, source: List<Medium>, sorting: Int, grouping: Int,
                current: () -> Boolean): ArrayList<Medium>? {
        val date = sorting and SORT_BY_DATE_TAKEN != 0 ||
            grouping and (GROUP_BY_DATE_TAKEN_DAILY or GROUP_BY_DATE_TAKEN_MONTHLY) != 0
        val modified = sorting and SORT_BY_DATE_MODIFIED != 0 ||
            grouping and (GROUP_BY_LAST_MODIFIED_DAILY or GROUP_BY_LAST_MODIFIED_MONTHLY) != 0
        val size = sorting and SORT_BY_SIZE != 0
        if (!current()) return null
        // Read only selected paths, in existing query-plan batches. This also obtains
        // user date overrides. Non-indexed files use the scanner's modified fallback.
        val indexed = if (date) MediaFetcher(context).queryProfileIndexedMedia(
            emptySet(), source.mapTo(LinkedHashSet()) { it.path }, arrayListOf()
        ) else null
        val result = ArrayList<Medium>(source.size)
        for (item in source) {
            if (!current() || Thread.currentThread().isInterrupted) return null
            val copy = item.copy()
            if (size || modified || date) {
                val file = File(item.path)
                // Explicitly resolved unknowns retain the existing path tie-break,
                // rather than treating an unrequested cache field as authoritative.
                if (size) copy.size = file.length().coerceAtLeast(0L)
                if (modified || date) copy.modified = file.lastModified().coerceAtLeast(0L)
                if (date) copy.taken = indexed?.customDatesByPath?.get(item.path)
                    ?: indexed?.mediaByPath?.get(item.path)?.taken?.takeIf { it > 0L }
                    ?: copy.modified
            }
            result.add(copy)
        }
        return result.takeIf { current() }
    }
}
