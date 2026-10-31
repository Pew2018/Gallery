package org.fossify.gallery.helpers

import android.os.SystemClock
import org.fossify.gallery.models.Medium

/** Public metadata only. Cache hits always undergo fresh path/protection validation. */
internal object PhotoProfileMediaCache {
    private const val MAX_PROFILES = 2
    private const val MAX_ITEMS = 12000
    private const val MAX_AGE_MS = 60_000L
    private data class Entry(val scope: PhotoProfileScope, val revision: Long, val at: Long, val media: List<Medium>)
    private val entries = LinkedHashMap<String, Entry>(4, 0.75f, true)

    private var registered = false
    @Synchronized fun initialize(context: android.content.Context) {
        if (registered) return
        registered = true
        context.applicationContext.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(config: android.content.res.Configuration) = Unit
            override fun onLowMemory() = invalidate()
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) invalidate()
            }
        })
    }

    @Synchronized fun offer(result: PhotoLoadResult) {
        val scope = result.context.scope
        if (result.stage != PhotoLoadStage.COMPLETE || !result.access.publicOnly || scope.sessionId != null ||
            result.media.size > MAX_ITEMS || result.context.revision != PhotoContentRevision.current()) return
        entries[scope.profileId] = Entry(scope, result.context.revision, SystemClock.elapsedRealtime(), result.media.map { it.copy() })
        while (entries.size > MAX_PROFILES || entries.values.sumOf { it.media.size } > MAX_ITEMS) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized fun read(context: PhotoProfileLoadContext): List<Medium>? {
        val entry = entries[context.scope.profileId] ?: return null
        if (entry.scope != context.scope || entry.revision != context.revision ||
            SystemClock.elapsedRealtime() - entry.at > MAX_AGE_MS) {
            entries.remove(context.scope.profileId)
            return null
        }
        return entry.media.map { it.copy() }
    }

    @Synchronized fun invalidate(profileId: String? = null) {
        if (profileId == null) entries.clear() else entries.remove(profileId)
    }

    fun preview(sorted: List<Medium>, anchorPath: String?, limit: Int = 160): List<Medium> {
        val anchor = sorted.indexOfFirst { it.path == anchorPath }.coerceAtLeast(0)
        val start = (anchor - limit / 4).coerceAtLeast(0)
        return sorted.subList(start, (start + limit).coerceAtMost(sorted.size))
    }
}
