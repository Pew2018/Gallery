package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import java.util.UUID

/**
 * One-time in-process metadata handoff for *public* ordinary folders.
 * Never stores bitmaps or authentication capabilities, and never bypasses
 * the normal ViewPagerActivity authentication flow.
 */
internal object FolderViewerSnapshotStore {
    const val EXTRA_ID = "org.fossify.gallery.FOLDER_VIEWER_SNAPSHOT"
    private const val MAX_AGE_MS = 12_000L

    private data class Entry(
        val id: String,
        val folder: String,
        val selectedPath: String,
        val fingerprint: String,
        val createdAt: Long,
        val items: ArrayList<Medium>
    )
    private var pending: Entry? = null

    private fun fingerprint(context: Context, folder: String): String {
        val cfg = context.config
        return listOf(
            cfg.getFolderSorting(folder), cfg.getFolderGrouping(folder),
            cfg.filterMedia, cfg.shouldShowHidden, cfg.excludedFolders,
            cfg.includedFolders, cfg.fileLoadingPriority
        ).joinToString("|")
    }

    private fun allowed(context: Context, folder: String, selected: String, items: List<Medium>): Boolean {
        val cfg = context.config
        val protected = FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) != null
        val excluded = FavoriteFolderRules.isExcluded(folder, cfg.excludedFolders)
        return FolderViewerSnapshotPolicy.isEligible(
            folder, selected, items.map { it.path }, protected, excluded
        )
    }

    @Synchronized
    fun offer(context: Context, folder: String, selected: String, items: List<Medium>): String? {
        pending = null
        if (!allowed(context, folder, selected, items)) return null
        val id = UUID.randomUUID().toString()
        pending = Entry(
            id, folder, selected, fingerprint(context, folder),
            SystemClock.elapsedRealtime(), ArrayList(items.map { it.copy() })
        )
        return id
    }

    @Synchronized
    fun take(context: Context, id: String?, folder: String, selected: String): ArrayList<Medium>? {
        val entry = pending
        pending = null
        if (entry == null || id == null || id != entry.id || folder != entry.folder ||
            selected != entry.selectedPath || SystemClock.elapsedRealtime() - entry.createdAt > MAX_AGE_MS ||
            entry.fingerprint != fingerprint(context, folder) ||
            !allowed(context, folder, selected, entry.items)) return null
        return ArrayList(entry.items.map { it.copy() })
    }
}
