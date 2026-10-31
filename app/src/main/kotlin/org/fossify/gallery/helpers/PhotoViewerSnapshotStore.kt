package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
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
        if (PhotoProfileRepository.of(context).state().current?.id != profileId) return null
        val scope = PhotoProfileScope.capture(context, profileId, null) ?: return null
        return "$scope|${PhotoContentRevision.current()}"
    }

    @Synchronized
    fun offer(context: Context, profileId: String?, sessionId: String?, media: List<Medium>): String? {
        pending = null
        if (profileId == null || sessionId != null || media.isEmpty() ||
            PhotoProfileAccessSessionManager.currentToken(context) != null) return null
        val signature = signature(context, profileId) ?: return null
        val id = UUID.randomUUID().toString()
        pending = Entry(id, profileId, signature, SystemClock.elapsedRealtime(), media.map { it.copy() }.toCollection(ArrayList()))
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
        return entry.media.map { it.copy() }.toCollection(ArrayList())
    }

    /** Worker-only first frame: validate just the requested file, never enumerate the profile. */
    fun firstFrame(
        context: Context, profileId: String?, sessionId: String?, item: Medium,
        cancelled: () -> Boolean = { false }
    ): PhotoLoadResult? {
        val request = PhotoProfileLoadContext.capture(context, profileId, sessionId) ?: return null
        if (request.scope.selectedId != profileId || cancelled()) return null
        val (safe, access) = PhotoAccessValidationBatch(context, request, cancelled,
            directVisibilityOnly = request.scope.allFiles || android.os.Build.VERSION.SDK_INT < 30
        ).validate(listOf(item))
        if (safe.size != 1 || cancelled() || !request.isCurrent(context)) return null
        // Prove readability on the worker before publishing. Failure falls back to
        // the normal indexed loader, preserving scoped-storage compatibility.
        if (!runCatching { java.io.File(item.path).inputStream().use { it.read() }; true }.getOrDefault(false)) return null
        if (cancelled() || !request.isCurrent(context)) return null
        return PhotoLoadResult(request, PhotoLoadStage.INDEXED, safe, access)
    }

    /** Worker-only: taking a metadata snapshot is not fresh file authorization. */
    fun validate(
        context: Context, profileId: String?, media: List<Medium>, clickedPath: String,
        cancelled: () -> Boolean = { false }
    ): PhotoLoadResult? {
        val request = PhotoProfileLoadContext.capture(context, profileId, null) ?: return null
        if (request.scope.selectedId != profileId || cancelled()) return null
        val candidates = if (request.scope.allFiles || android.os.Build.VERSION.SDK_INT < 30) {
            media.filter { java.io.File(it.path).isFile }
        } else media
        val (safe, access) = PhotoAccessValidationBatch(context, request, cancelled).validate(candidates)
        if (cancelled() || !request.isCurrent(context) || safe.none { it.path == clickedPath }) return null
        return PhotoLoadResult(request, PhotoLoadStage.COMPLETE, safe, access)
    }

    @Synchronized
    fun clear() {
        pending = null
    }
}

