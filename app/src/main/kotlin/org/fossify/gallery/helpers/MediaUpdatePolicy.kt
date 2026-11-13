package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

enum class MediaUpdateReason { SCAN, USER_SORT, USER_SEARCH, DELETE_RESULT }

/** Layout/database identity is not rendered thumbnail content. */
internal object MediaUpdatePolicy {
    private const val LARGE_EDIT_WINDOW = 4096
    /** Compare ordered rows by rendered content, ignoring persistence/layout-only fields. */
    fun sameDisplayedContent(before: List<ThumbnailItem>, after: List<ThumbnailItem>): Boolean =
        before.size == after.size && before.indices.all { index ->
            val oldItem = before[index]
            val newItem = after[index]
            when {
                oldItem is Medium && newItem is Medium ->
                    oldItem.path == newItem.path && sameContent(oldItem, newItem)
                oldItem is ThumbnailSection && newItem is ThumbnailSection -> oldItem == newItem
                else -> oldItem == newItem
            }
        }

    fun sameContent(before: Medium, after: Medium): Boolean =
        before.path == after.path && before.name == after.name && before.parentPath == after.parentPath &&
            before.modified == after.modified && before.taken == after.taken && before.size == after.size &&
            before.type == after.type && before.videoDuration == after.videoDuration &&
            before.isFavorite == after.isFavorite && before.deletedTS == after.deletedTS &&
            before.takenIsFallback == after.takenIsFallback

    /** Appending an unchanged prefix preserves RecyclerView's current anchor and bindings. */
    fun isContentAppend(before: List<ThumbnailItem>, after: List<ThumbnailItem>): Boolean =
        after.size > before.size && sameDisplayedContent(before, after.subList(0, before.size))

    /**
     * Sorting a plain media list changes positions without changing membership or
     * rendered content. RecyclerView only needs visible rows rebound at their new
     * adapter positions; running a full edit-distance diff for a large permutation
     * can take seconds and let subsequent user updates continually supersede it.
     * Grouped rows deliberately stay on the normal DiffUtil path.
     */
    fun isPureMediaReorder(before: List<ThumbnailItem>, after: List<ThumbnailItem>): Boolean {
        if (before.size != after.size || before.isEmpty() || before.size < 2 ||
            before.any { it !is Medium } || after.any { it !is Medium }) return false

        val oldByPath = HashMap<String, Medium>(before.size)
        for (item in before) {
            val medium = item as Medium
            if (oldByPath.put(medium.path, medium) != null) return false
        }

        var orderChanged = false
        for (index in after.indices) {
            val old = oldByPath.remove((after[index] as Medium).path) ?: return false
            val new = after[index] as Medium
            if (!MediaUpdatePolicy.sameContent(old, new)) return false
            if (before[index].let { (it as Medium).path } != new.path) orderChanged = true
        }
        return oldByPath.isEmpty() && orderChanged
    }

    /** Bound edit-distance work, not simply list size. Equal identities/metadata-only
     * refreshes and small edits retain granular notifications even on huge lists. */
    fun shouldReplace(before: List<ThumbnailItem>, after: List<ThumbnailItem>,
                      reason: MediaUpdateReason, diffRunning: Boolean): Boolean {
        if (diffRunning && reason in setOf(MediaUpdateReason.USER_SORT, MediaUpdateReason.USER_SEARCH)) return true
        if (maxOf(before.size, after.size) < LARGE_EDIT_WINDOW) return false
        fun identity(item: ThumbnailItem): String = when (item) {
            is Medium -> "m:${item.path}"
            is ThumbnailSection -> "s:${item.key}"
            else -> error("Unknown thumbnail item")
        }
        // A linear upper bound on the differing identity window. Do not run Myers
        // over a large permutation or a search that removes/restores most rows.
        var prefix = 0
        val common = minOf(before.size, after.size)
        while (prefix < common && identity(before[prefix]) == identity(after[prefix])) prefix++
        var suffix = 0
        while (suffix < common - prefix &&
            identity(before[before.lastIndex - suffix]) == identity(after[after.lastIndex - suffix])) suffix++
        return maxOf(before.size, after.size) - prefix - suffix >= LARGE_EDIT_WINDOW
    }

    fun durationOnly(before: Medium, after: Medium): Boolean =
        before.videoDuration != after.videoDuration &&
            sameContent(before.copy(videoDuration = after.videoDuration), after)
}
