package org.fossify.gallery.helpers

import android.content.Context
import java.io.File
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isThisOrParentExcluded
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/** First publication is sized for the grid. Later snapshots grow geometrically and are throttled. */
internal class MediaPreviewCadence(private val viewportItems: Int, startedAt: Long) {
    private var lastSize = 0
    private var lastAt = startedAt

    fun shouldPublish(size: Int, now: Long): Boolean {
        if (size <= lastSize) return false
        val ready = when {
            lastSize == 0 -> true
            lastSize < viewportItems -> size >= viewportItems || now - lastAt >= 150L
            else -> now - lastAt >= 300L && size - lastSize >= lastSize
        }
        if (ready) {
            lastSize = size
            lastAt = now
        }
        return ready
    }
}

/** Main-thread, request-local presentation. Discovery order is provisional until final sorting. */
internal class MediaPagePreview {
    private val byPath = LinkedHashMap<String, Medium>()
    private var stage: DisplayedSourceStage? = null
    fun add(next: DisplayedSourceStage, rows: List<Medium>, displayed: List<ThumbnailItem> = emptyList()): ArrayList<Medium>? {
        if (next.ordinal < (stage?.ordinal ?: 0)) return null
        val before = byPath.size
        stage = next
        rows.forEach { byPath.putIfAbsent(it.path, it) }
        if (byPath.size == before) return null
        val remaining = LinkedHashMap(byPath)
        return ArrayList<Medium>(byPath.size).apply {
            displayed.forEach { item ->
                if (item is Medium) remaining.remove(item.path)?.let(::add)
            }
            addAll(remaining.values)
        }
    }
}

/** Indexed and cached rows must obey the same folder visibility rules as the grid. */
internal class MediaPageVisibility(
    private val context: Context,
    private val showAll: Boolean,
    private val authorized: (String) -> Boolean
) {
    private val hidden = context.config.shouldShowHidden
    private val included = context.config.includedFolders
    private val excluded = context.config.let { if (it.temporarilyShowExcluded) hashSetOf() else it.excludedFolders }
    private val unrestrictedFolders = HashMap<String, Boolean>()
    private val visibleFolders = HashMap<String, Boolean>()
    private val markers = HashMap<String, Boolean>()

    fun allows(path: String): Boolean {
        val folder = File(path).parent ?: return false
        if (!hidden && path.contains("/.")) return false
        return visibleFolders.getOrPut(folder) {
            authorized(path) && if (showAll) {
                !folder.equals("/storage/emulated/legacy", ignoreCase = true) &&
                    folder.shouldFolderBeVisible(excluded, included, hidden, markers) { key, present ->
                        markers[key] = present
                    }
            } else {
                folder.isThisOrParentIncluded(included) || !folder.isThisOrParentExcluded(excluded)
            }
        }
    }

    fun allowsIndexedFile(path: String): Boolean {
        val file = File(path)
        val folder = file.parent ?: return false
        return allows(path) && unrestrictedFolders.getOrPut(folder) {
            !context.isRestrictedWithSAFSdk30(folder)
        } && runCatching {
            val attributes = java.nio.file.Files.readAttributes(
                file.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java,
                java.nio.file.LinkOption.NOFOLLOW_LINKS
            )
            attributes.isRegularFile && attributes.size() > 0 && file.canRead()
        }.getOrDefault(false)
    }
}

internal data class MediaPageBatch(
    val stage: DisplayedSourceStage,
    val rows: List<Medium>,
    val accessCurrent: () -> Boolean
)
