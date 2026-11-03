package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import android.os.FileObserver
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.gallery.extensions.getFavoritePaths
import java.io.File

/**
 * Worker-only source probe. MediaStore generations alone miss unindexed
 * .nomedia children, so check direct directory entries as well.
 * Unknown/unreadable inputs fail open to reconciliation, never to access.
 */
internal object PhotoProfileReturnProbe {
    private const val MAX_UNVERIFIED_AGE_MS = 30_000L
    private const val SMALL_FOLDER_METADATA_LIMIT = 256
    private const val MAX_WATCHED_FOLDERS = 32
    private data class FolderState(
        val modified: Long, val namesHash: Long, val count: Int, val detailHash: Long?
    )
    private data class Fingerprint(
        val folders: Map<String, FolderState>,
        val favorites: Set<String>
    )
    private data class Entry(
        val scope: PhotoProfileScope,
        val revision: Long,
        val takenAt: Long,
        val fingerprint: Fingerprint
    )
    @Volatile private var entry: Entry? = null
    private val changedByObserver = AtomicBoolean(false)
    private var observers: List<FileObserver> = emptyList()

    private fun capture(context: Context, scope: PhotoProfileScope): Fingerprint? {
        val folders = LinkedHashMap<String, FolderState>()
        for (source in scope.sources) {
            if (source.type != PhotoSourceType.FOLDER) continue
            val path = source.path ?: return null
            val folder = File(path)
            // NamesHash is order-independent; a rename with constant count
            // is also detected even if the filesystem directory mtime is stale.
            val names = folder.list() ?: return null
            var hash = 0L
            var detail = 0L
            for (name in names) {
                hash += name.hashCode().toLong() * 0x9e3779b1L
                if (names.size <= SMALL_FOLDER_METADATA_LIMIT) {
                    val file = File(folder, name)
                    detail += file.lastModified() * 31L + file.length() * 131L
                }
            }
            folders[path] = FolderState(folder.lastModified(), hash, names.size,
                detail.takeIf { names.size <= SMALL_FOLDER_METADATA_LIMIT })
        }
        val favorites = if (scope.sources.any { it.type == PhotoSourceType.FAVORITES }) {
            context.getFavoritePaths().toSet()
        } else emptySet()
        return Fingerprint(folders, favorites)
    }

    /** Called only after a fully authorized COMPLETE result, off the main thread. */
    fun record(context: Context, result: PhotoLoadResult) {
        if (result.stage != PhotoLoadStage.COMPLETE || result.access.needsUnlock ||
            !result.context.isCurrent(context)) return
        val fingerprint = capture(context, result.context.scope) ?: return
        if (result.context.isCurrent(context)) {
            synchronized(this) {
                observers.forEach { it.stopWatching() }
                observers = emptyList()
                changedByObserver.set(false)
                val roots = result.context.scope.sources
                    .filter { it.type == PhotoSourceType.FOLDER }
                    .mapNotNull { it.path }
                if (roots.size <= MAX_WATCHED_FOLDERS) {
                    observers = roots.mapNotNull { path -> runCatching {
                        object : FileObserver(path, FileObserver.CREATE or FileObserver.DELETE or
                            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
                            FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or
                            FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) {
                            override fun onEvent(event: Int, path: String?) {
                                changedByObserver.set(true)
                            }
                        }.also { it.startWatching() }
                    }.getOrNull() }
                }
                entry = Entry(result.context.scope, result.context.revision,
                    SystemClock.elapsedRealtime(), fingerprint)
            }
        }
    }

    /** Worker only. True means reconcile in the background while keeping the grid visible. */
    fun needsReconcile(context: Context, scope: PhotoProfileScope): Boolean {
        val current = entry ?: return true
        if (changedByObserver.get()) return true
        if (scope != current.scope || current.revision != PhotoContentRevision.current() ||
            SystemClock.elapsedRealtime() - current.takenAt !in 0..MAX_UNVERIFIED_AGE_MS) return true
        return capture(context, scope) != current.fingerprint || !scope.isCurrent(context)
    }

    @Synchronized fun clear() {
        entry = null
        observers.forEach { it.stopWatching() }
        observers = emptyList()
        changedByObserver.set(false)
    }
}
