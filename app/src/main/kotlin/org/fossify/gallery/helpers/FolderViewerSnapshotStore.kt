package org.fossify.gallery.helpers

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import java.util.UUID

/**
 * In-process metadata handoff for a complete, public ordinary folder.
 * The list is prepared off the click path and is never an authorization token.
 */
internal object FolderViewerSnapshotStore {
    const val EXTRA_ID = "org.fossify.gallery.FOLDER_VIEWER_SNAPSHOT"
    private const val MAX_ITEMS = 50_000

    private data class Entry(
        val id: String,
        val folder: String,
        val fingerprint: String,
        val freshness: Freshness,
        val items: List<Medium>
    )

    class Freshness(val checkpoint: FolderReturnCheckpoint, private val observation: FolderScanObservation?) {
        val observing get() = observation?.unchanged
        fun isCurrent(context: Context, folder: String): Boolean {
            if (checkpoint.access == null) return FolderReturnCheckpoint.isCurrent(context, folder, checkpoint)
            return observing == true &&
                FolderReturnCheckpoint.sameSnapshot(checkpoint, FolderReturnCheckpoint.captureForViewerScan(context, folder)) &&
                observing == true
        }
    }

    data class Catalog(val items: List<Medium>, val freshness: Freshness)

    @Volatile private var prepared: Entry? = null
    private var callbacksRegistered = false
    private var prepareGeneration = 0L

    @Synchronized private fun registerMemoryCallbacks(context: Context) {
        if (callbacksRegistered) return
        callbacksRegistered = true
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = clear()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) clear()
            }
        })
    }

    private fun fingerprint(context: Context, folder: String): String {
        val cfg = context.config
        return listOf(
            cfg.getFolderSorting(folder), cfg.getFolderGrouping(folder), cfg.getFolderViewType(folder),
            cfg.filterMedia, cfg.shouldShowHidden, cfg.excludedFolders, cfg.includedFolders,
            cfg.fileLoadingPriority, cfg.temporarilyShowHidden, cfg.temporarilyShowExcluded
        ).joinToString("|")
    }

    private fun allowed(context: Context, folder: String, items: List<Medium>, directAccess: Boolean): Boolean {
        val cfg = context.config
        val protected = FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) != null
        val excluded = FavoriteFolderRules.isExcluded(folder, cfg.excludedFolders)
        return items.size in 1..MAX_ITEMS &&
            items.all { it.deletedTS == 0L && (directAccess || it.mediaStoreId > 0L) } &&
            FolderViewerSnapshotPolicy.isEligible(folder, items.first().path, items.map { it.path }, protected, excluded)
    }

    /** Provider freshness checks and metadata copies must not hold the photo-tap monitor. */
    fun prepare(
        context: Context,
        folder: String,
        scanStart: FolderReturnCheckpoint?,
        scanEnd: FolderReturnCheckpoint?,
        items: List<Medium>,
        observation: FolderScanObservation? = null,
        stillCurrent: () -> Boolean = { true }
    ): String? {
        val generation = synchronized(this) {
            if (!stillCurrent()) return null
            prepared = null
            ++prepareGeneration
        }
        if (scanEnd == null || !FolderReturnCheckpoint.sameSnapshot(scanStart, scanEnd) ||
            !allowed(context, folder, items, scanEnd.access?.manager == true)) return null

        val id = UUID.randomUUID().toString()
        val freshness = Freshness(scanEnd, observation)
        val entry = Entry(id, folder, fingerprint(context, folder), freshness, items.map { it.copy() })
        if (!stillCurrent() || !freshness.isCurrent(context, folder)) return null
        synchronized(this) {
            if (generation != prepareGeneration || !stillCurrent()) return null
            prepared = entry
            registerMemoryCallbacks(context)
        }
        return id
    }

    /** Constant-time intent handoff; freshness and selected-path checks happen on the viewer worker. */
    @Synchronized
    fun handoffId(context: Context, folder: String): String? {
        val entry = prepared ?: return null
        if (entry.folder != folder) return null
        if (entry.fingerprint != fingerprint(context, folder) || entry.freshness.observing == false) {
            prepared = null
            return null
        }
        return entry.id
    }

    @Synchronized
    fun handoffCount(id: String?): Int? = prepared?.takeIf { id != null && it.id == id }?.items?.size

    fun copyForViewer(context: Context, id: String, folder: String, selected: String): Catalog? {
        val entry = prepared?.takeIf { it.id == id && it.folder == folder } ?: return null
        if (entry.fingerprint != fingerprint(context, folder) ||
            entry.items.none { it.path == selected } || !entry.freshness.isCurrent(context, folder)) return null
        // Viewers mutate their media models. Each launch owns a separate copy.
        return Catalog(entry.items.map { it.copy() }, entry.freshness)
    }

    @Synchronized private fun clear() {
        prepareGeneration++
        prepared = null
    }
}
