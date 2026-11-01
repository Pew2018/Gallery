package org.fossify.gallery.helpers

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import java.util.UUID

/**
 * One-time in-process metadata handoff for a complete, public ordinary folder.
 * The list is prepared off the click path and is never an authorization token.
 */
internal object FolderViewerSnapshotStore {
    const val EXTRA_ID = "org.fossify.gallery.FOLDER_VIEWER_SNAPSHOT"
    private const val MAX_AGE_MS = 60_000L

    private data class Entry(
        val id: String,
        val folder: String,
        val fingerprint: String,
        val checkpoint: FolderReturnCheckpoint,
        val createdAt: Long,
        val items: ArrayList<Medium>
    )
    data class Catalog(val items: ArrayList<Medium>, val checkpoint: FolderReturnCheckpoint)

    @Volatile private var prepared: Entry? = null
    private val expiryHandler = Handler(Looper.getMainLooper())
    private var expiryCallback: Runnable? = null
    private var callbacksRegistered = false

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

    private fun allowed(context: Context, folder: String, selected: String?, items: List<Medium>): Boolean {
        val cfg = context.config
        val protected = FolderUnlockSessionManager.findProtectedAncestor(folder, cfg::isFolderProtected) != null
        val excluded = FavoriteFolderRules.isExcluded(folder, cfg.excludedFolders)
        return items.isNotEmpty() && items.all { it.deletedTS == 0L && it.mediaStoreId > 0L } &&
            FolderViewerSnapshotPolicy.isEligible(
                folder, selected ?: items.first().path, items.map { it.path }, protected, excluded
            ) && (selected == null || items.any { it.path == selected })
    }

    /** Called after a successful complete scan, from a worker. */
    @Synchronized
    fun prepare(
        context: Context,
        folder: String,
        scanStart: FolderReturnCheckpoint?,
        scanEnd: FolderReturnCheckpoint?,
        items: List<Medium>,
        stillCurrent: () -> Boolean = { true }
    ): String? {
        prepared = null
        expiryCallback?.let(expiryHandler::removeCallbacks)
        expiryCallback = null
        if (!FolderReturnCheckpoint.sameSnapshot(scanStart, scanEnd) ||
            !allowed(context, folder, null, items)) return null

        // One detached metadata copy happens after the scan, never on photo tap.
        val id = UUID.randomUUID().toString()
        val entry = Entry(id, folder, fingerprint(context, folder), scanEnd!!,
            SystemClock.elapsedRealtime(), ArrayList(items.map { it.copy() }))
        if (!stillCurrent() || !FolderReturnCheckpoint.isCurrent(context, folder, scanEnd)) return null
        prepared = entry
        registerMemoryCallbacks(context)
        expiryCallback = Runnable { synchronized(this) { if (prepared?.id == id) prepared = null } }.also {
            expiryHandler.postDelayed(it, MAX_AGE_MS)
        }
        return id
    }

    /** Constant-time intent handoff; freshness and selected-path checks happen on the viewer worker. */
    @Synchronized
    fun handoffId(context: Context, folder: String): String? {
        val entry = prepared ?: return null
        if (SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS ||
            entry.folder != folder || entry.fingerprint != fingerprint(context, folder)) {
            prepared = null
            expiryCallback?.let(expiryHandler::removeCallbacks)
            expiryCallback = null
            return null
        }
        return entry.id
    }

    @Synchronized
    fun handoffCount(id: String?): Int? = prepared?.takeIf { id != null && it.id == id }?.items?.size

    @Synchronized
    fun take(context: Context, id: String?, folder: String, selected: String): Catalog? {
        val entry = prepared
        if (entry == null || id == null || id != entry.id) return null
        prepared = null
        expiryCallback?.let(expiryHandler::removeCallbacks)
        expiryCallback = null
        if (folder != entry.folder || SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS ||
            entry.fingerprint != fingerprint(context, folder) ||
            entry.items.none { it.path == selected } || !allowed(context, folder, selected, entry.items) ||
            !FolderReturnCheckpoint.isCurrent(context, folder, entry.checkpoint)) return null
        // The deep copy was done while preparing the catalog. This shallow list
        // wrapper is cheap and the returned medium instances are now viewer-owned.
        return Catalog(ArrayList(entry.items), entry.checkpoint)
    }

    @Synchronized private fun clear() {
        prepared = null
        expiryCallback?.let(expiryHandler::removeCallbacks)
        expiryCallback = null
    }
}
