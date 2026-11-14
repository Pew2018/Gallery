package org.fossify.gallery.helpers

import android.content.Context
import java.io.File
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isThisOrParentExcluded
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.models.Medium
import org.fossify.commons.helpers.SORT_BY_RANDOM
import kotlin.random.Random

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

/** Main-thread, request-local accumulation. Newer sources can correct cached sort keys. */
internal class MediaPagePreview {
    private val byPath = LinkedHashMap<String, Medium>()
    private var stage: DisplayedSourceStage? = null

    fun add(next: DisplayedSourceStage, rows: List<Medium>): ArrayList<Medium>? {
        if (next.ordinal < (stage?.ordinal ?: 0)) return null
        stage = next
        var changed = false
        rows.forEach { row ->
            val previous = byPath.put(row.path, row)
            if (previous == null || !MediaUpdatePolicy.sameContent(previous, row)) changed = true
        }
        if (!changed) return null
        return ArrayList(byPath.values)
    }
}

/** A seeded path rank keeps the relative order of known rows as discovery continues. */
internal class StagedMediaOrder(private val seed: Long = Random.nextLong()) {
    fun sort(media: ArrayList<Medium>, sorting: Int, fetcher: MediaFetcher) {
        if (sorting and SORT_BY_RANDOM != 0) {
            val ranks = media.associate { it.path to Random(seed xor it.path.hashCode().toLong()).nextLong() }
            media.sortWith(compareBy<Medium> { ranks.getValue(it.path) }.thenBy { it.path })
        } else {
            fetcher.sortMedia(media, sorting)
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
