package org.fossify.gallery.helpers

import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** Progress from a grid folder scan. Subscribing never cancels that scan. */
internal interface FolderScanSubscriber {
    fun onPreview(stage: DisplayedSourceStage, rows: List<Medium>)
    fun onComplete(media: List<ThumbnailItem>, result: GalleryScanResult)
    fun onFailure(error: Exception)
    fun onCancelled()
}

/**
 * The one folder scan the grid has already submitted. The viewer may subscribe;
 * it must not cancel or replace this task.
 */
internal object RunningFolderScan {
    @Volatile private var task: GetMediaAsynctask? = null

    fun publish(candidate: GetMediaAsynctask) {
        if (!candidate.sharesFolderScan) return
        task = candidate
    }

    fun clear(candidate: GetMediaAsynctask) {
        if (task === candidate) task = null
    }

    fun current(folder: String, session: String?): GetMediaAsynctask? =
        task?.takeIf { it.matchesSharedFolder(folder, session) }
}
