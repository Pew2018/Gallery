package org.fossify.gallery.helpers

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.fossify.gallery.models.Medium
import java.util.UUID

/** Short-lived, one-use metadata handoff. It never stores or grants a favorite capability. */
internal object FavoriteViewerCatalogStore {
    const val EXTRA_ID = "org.fossify.gallery.FAVORITE_VIEWER_CATALOG"
    private const val MAX_AGE_MS = 30_000L
    private const val MAX_ITEMS = 12_000

    data class Catalog(val items: List<Medium>, val favoritePaths: Set<String>)
    private data class Entry(val id: String, val createdAt: Long, val catalog: Catalog)
    @Volatile private var prepared: Entry? = null
    private val handler = Handler(Looper.getMainLooper())
    private var expiry: Runnable? = null
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

    /** Called after the list Activity's final authorization publication, on a worker. */
    @Synchronized
    fun prepare(
        context: Context,
        items: List<Medium>,
        favoritePaths: Set<String>,
        stillCurrent: () -> Boolean
    ): String? {
        clear()
        if (items.isEmpty() || items.size > MAX_ITEMS || !stillCurrent()) return null
        val id = UUID.randomUUID().toString()
        prepared = Entry(
            id, SystemClock.elapsedRealtime(),
            Catalog(ArrayList(items.map { it.copy() }), favoritePaths.toSet())
        )
        if (!stillCurrent()) {
            clear()
            return null
        }
        registerMemoryCallbacks(context)
        expiry = Runnable { synchronized(this) { if (prepared?.id == id) prepared = null } }.also {
            handler.postDelayed(it, MAX_AGE_MS)
        }
        return id
    }

    /** Constant-time lookup for the internal intent; not an authorization check. */
    @Synchronized fun handoffId(): String? {
        val entry = prepared ?: return null
        if (SystemClock.elapsedRealtime() - entry.createdAt !in 0..MAX_AGE_MS) {
            clear()
            return null
        }
        return entry.id
    }

    @Synchronized fun handoffCount(id: String?): Int? = prepared?.takeIf { id != null && it.id == id }?.catalog?.items?.size

    /** Single-use metadata take; fresh session/file checks must still run before publication. */
    @Synchronized fun take(id: String?): Catalog? {
        val entry = prepared
        if (entry == null || id == null || id != entry.id) return null
        clear()
        return entry.catalog
    }

    @Synchronized private fun clear() {
        prepared = null
        expiry?.let { handler.removeCallbacks(it) }
        expiry = null
    }
}
