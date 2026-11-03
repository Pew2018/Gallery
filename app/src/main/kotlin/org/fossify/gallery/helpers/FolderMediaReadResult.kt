package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

internal enum class FolderReadStatus { COMPLETE, PARTIAL, ACCESS_REQUIRED, FAILED }

/** COMPLETE describes the selected access method, never an independent proof of physical completeness. */
internal data class FolderMediaReadResult(
    val media: ArrayList<Medium>,
    val status: FolderReadStatus,
    val method: String,
    val enumerated: Int,
    val reasons: Map<String, Int>,
    val error: Exception? = null,
    val accessCurrent: () -> Boolean = { true }
) {
    val accepted get() = media.size
    val confirmedPaths get() = media.mapTo(hashSetOf()) { it.path }
    fun presentation(old: List<Medium>, allowed: (String) -> Boolean): ArrayList<Medium> {
        if (!accessCurrent()) throw SecurityException("Directory access changed during refresh")
        val fresh = media.filter { allowed(it.path) }
        return ArrayList(when (status) {
            FolderReadStatus.COMPLETE -> fresh
            FolderReadStatus.PARTIAL -> (fresh + old.filter { allowed(it.path) }).distinctBy { it.path }
            else -> old.filter { allowed(it.path) }
        })
    }
}

internal class FolderReadObservation {
    var status = FolderReadStatus.COMPLETE
    var method = "direct"
    var enumerated = 0
    var accessCurrent: () -> Boolean = { true }
    val reasons = linkedMapOf<String, Int>()
    fun count(reason: String) { reasons[reason] = (reasons[reason] ?: 0) + 1 }
    fun partial(reason: String) { status = FolderReadStatus.PARTIAL; count(reason) }
}

/** Disabled by default, including release builds. Use hashes for a device-selected target, never private paths. */
internal object FolderRefreshTrace {
    @Volatile var enabled = false
    @Volatile var targetPath: String? = null
    fun event(request: Long, stage: String, paths: Collection<String> = emptyList(), detail: String = "") {
        if (!enabled) return
        val target = targetPath
        android.util.Log.d("GalleryFolderRefresh", "request=$request stage=$stage count=${paths.size} target=${target != null && target in paths} $detail")
    }
}
