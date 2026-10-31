package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Medium
import java.util.UUID

/**
 * Single-use, process-only handoff for an internal Photos viewer.
 *
 * No bitmap, private profile capability or protected media is cached here.
 * Protected profiles keep the existing fresh authorized loader instead.
 */
internal object PhotoViewerSnapshotStore {
    const val EXTRA_ID = "org.fossify.gallery.PHOTO_VIEWER_SNAPSHOT"
    private const val MAX_AGE_MS = 30_000L

    private data class Entry(
        val id: String,
        val profileId: String,
        val signature: String,
        val createdAt: Long,
        val media: ArrayList<Medium>
    )

    private var pending: Entry? = null

    private fun signature(context: Context, profileId: String): String? {
        val profile = PhotoProfileRepository.of(context).state().current
            ?.takeIf { it.id == profileId } ?: return null
        val config = context.config
        return listOf(
            profile, config.photoMediaFilter, config.getFolderSorting(PhotoProfileMediaLoader.PATH),
            config.photoSourceBindings, config.excludedFolders, config.shouldShowHidden,
            PhotoProfileAccessSessionManager.currentEventVersion(), PhotoContentRevision.current()
        ).joinToString("|")
    }

    @Synchronized
    fun offer(context: Context, profileId: String?, sessionId: String?, media: List<Medium>): String? {
        pending = null
        if (profileId == null || sessionId != null || media.isEmpty() ||
            PhotoProfileAccessSessionManager.currentToken(context) != null) return null
        val signature = signature(context, profileId) ?: return null
        val id = UUID.randomUUID().toString()
        pending = Entry(id, profileId, signature, SystemClock.elapsedRealtime(), ArrayList(media))
        return id
    }

    @Synchronized
    fun take(context: Context, id: String?, profileId: String?, sessionId: String?, path: String): ArrayList<Medium>? {
        val entry = pending
        pending = null
        if (entry == null || id == null || id != entry.id || profileId != entry.profileId ||
            sessionId != null || PhotoProfileAccessSessionManager.currentToken(context) != null ||
            SystemClock.elapsedRealtime() - entry.createdAt > MAX_AGE_MS ||
            entry.signature != signature(context, entry.profileId) ||
            entry.media.none { it.path == path }) return null
        return ArrayList(entry.media)
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}
