package org.fossify.gallery.helpers

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.models.Medium
import java.util.Collections
import java.util.UUID

/**
 * Process-only complete Photos catalog, shared between the grid and internal viewer.
 * A handoff ID is a locator, NEVER an authorization token: the viewer must first
 * validate the selected file and each newly requested viewing window.
 */
internal object PhotoSessionViewerCatalogStore {
    const val EXTRA_ID = "org.fossify.gallery.PHOTO_SESSION_VIEWER_CATALOG"
    private const val MAX_ITEMS = 20_000
    private const val MAX_AGE_MS = 30 * 60_000L

    data class Catalog(
        val media: List<Medium>,
        val pathIndex: Map<String, Int>,
        val revision: Long
    )

    private data class Entry(
        val id: String,
        val request: PhotoProfileLoadContext,
        val createdAt: Long,
        val catalog: Catalog
    )

    @Volatile private var prepared: Entry? = null
    private var memoryCallbackRegistered = false

    @Synchronized
    private fun registerMemoryCallback(context: Context) {
        if (memoryCallbackRegistered) return
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = invalidate()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) invalidate()
            }
        })
        memoryCallbackRegistered = true
    }

    /** Worker only. Called after COMPLETE has validated all authorized sources. */
    fun prepare(context: Context, result: PhotoLoadResult): String? {
        if (result.stage != PhotoLoadStage.COMPLETE || result.access.needsUnlock ||
            result.media.isEmpty() || result.media.size > MAX_ITEMS ||
            !result.context.isCurrent(context)) return null
        val started = GalleryPerfTrace.begin()
        // Exactly one copy on the catalog-preparation worker, never during a tap.
        val media = Collections.unmodifiableList(result.media.map { it.copy() })
        val index = HashMap<String, Int>(media.size * 4 / 3 + 1)
        media.forEachIndexed { pos, item ->
            if (index.put(item.path, pos) != null) return null
        }
        val catalog = Catalog(media, Collections.unmodifiableMap(index), result.context.revision)
        val candidate = Entry(UUID.randomUUID().toString(), result.context,
            SystemClock.elapsedRealtime(), catalog)
        if (!result.context.isCurrent(context)) return null
        registerMemoryCallback(context)
        synchronized(this) {
            prepared = candidate
        }
        GalleryPerfTrace.end("photo_catalog_ready", started, media.size)
        return candidate.id
    }

    private fun valid(context: Context, entry: Entry, profileId: String?, sessionId: String?): Boolean {
        if (entry.request.scope.profileId != profileId || entry.request.scope.selectedId != profileId ||
            entry.request.scope.sessionId != sessionId ||
            SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS ||
            !entry.request.isCurrent(context)) return false
        // Room favorites are not included in the SharedPreferences scope version.
        if (entry.request.scope.sources.any { it.type == PhotoSourceType.FAVORITES } &&
            context.getFavoritePaths().toSet() != entry.request.favorites) return false
        return true
    }

    /** Worker only: scope/favorites checks must never run on the click UI thread. */
    fun handoffId(context: Context, profileId: String?, sessionId: String?): String? {
        val entry = prepared ?: return null
        if (!valid(context, entry, profileId, sessionId)) return null
        return entry.id.takeIf { prepared === entry }
    }

    /** Worker only. Returns shared metadata, not cloned Medium objects or permission. */
    fun take(
        context: Context, id: String?, profileId: String?,
        sessionId: String?, selectedPath: String
    ): Catalog? {
        val entry = prepared ?: return null
        if (id == null || id != entry.id || !entry.catalog.pathIndex.containsKey(selectedPath) ||
            !valid(context, entry, profileId, sessionId) || prepared !== entry) return null
        return entry.catalog
    }

    @Synchronized fun invalidate(profileId: String? = null) {
        if (profileId == null || prepared?.request?.scope?.profileId == profileId) prepared = null
    }
}
