package org.fossify.gallery.helpers

/**
 * The first swipeable viewer list for an internal open.
 * Caps are constant: a folder or favorites window is [ViewerCommittedWindow]'s
 * radius, and a Photos cache preview never exceeds [CachedMediaPreviewOrder.LIMIT].
 * A list larger than the preview limit is not scanned and is not installed.
 */
internal object ViewerOpeningWindow {
    fun <T> committed(items: List<T>, anchor: String, path: (T) -> String): List<T> =
        ViewerCommittedWindow.around(items, anchor, path)

    /** Already validated cache preview. Over-limit input is refused without a walk. */
    fun <T> photoPreview(items: List<T>?, anchor: String, path: (T) -> String): List<T>? {
        if (items == null || items.isEmpty() || items.size > CachedMediaPreviewOrder.LIMIT) return null
        if (items.none { path(it) == anchor }) return null
        return items
    }
}
