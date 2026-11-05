package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.commons.extensions.getSharedPrefs

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

/** Opt-in, bounded in-memory diagnostics. Full paths and file names are never written to logs. */
internal object FolderRefreshTrace {
    const val ENABLED_KEY = "folder_refresh_diagnostics"
    const val TARGET_KEY = "folder_refresh_target_hash"
    @Volatile var enabled = false
    @Volatile var targetPath: String? = null
    @Volatile private var targetHash = ""
    private val records = java.util.ArrayDeque<String>()
    fun configure(context: android.content.Context) {
        val prefs = context.getSharedPrefs()
        enabled = prefs.getBoolean(ENABLED_KEY, false)
        targetHash = prefs.getString(TARGET_KEY, "").orEmpty()
    }
    fun pathId(path: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(path.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    fun event(request: Long, stage: String, paths: Collection<String> = emptyList(), detail: String = "") {
        val persistentDiagnostics = GalleryDiagnosticLogger.isEnabled()
        val persistentStage = stage !in setOf("enumerated", "type_passed", "metadata_passed")
        if (!enabled && (!persistentDiagnostics || !persistentStage)) return
        val hash = targetPath?.let(::pathId) ?: targetHash
        val hit = hash.isNotEmpty() && paths.any { pathId(it) == hash }
        // Single-file stages identify the target without printing its private path.
        val id = if (paths.size == 1) pathId(paths.first()) else "-"
        val record = "t=${android.os.SystemClock.elapsedRealtime()} request=$request stage=$stage count=${paths.size} id=$id target=$hit $detail"
        if (enabled) synchronized(records) { if (records.size >= 1500) records.removeFirst(); records.addLast(record) }
        if (persistentDiagnostics && persistentStage) GalleryDiagnosticLogger.event(
            if (stage.contains("fail", true) || stage.contains("error", true)) DiagnosticLevel.ERROR else DiagnosticLevel.INFO,
            "FolderRefresh", stage, request,
            mapOf("count" to paths.size, "anonymousId" to paths.singleOrNull()?.let(GalleryDiagnosticLogger::anonymousId), "targetMatch" to hit, "detail" to detail)
        )
        if (enabled) android.util.Log.d("GalleryFolderRefresh", record)
    }
    fun dump(): String = synchronized(records) { records.joinToString("\n") }
    fun clear() = synchronized(records) { records.clear() }
    inline fun <T> measure(request: Long, stage: String, block: () -> T): T {
        // Per-file counters/time are aggregated by MediaPipelineDiagnostics.
        // Keep the opt-in target trace, but never persist two events per file.
        if (stage == "file_metadata") return block()
        if (!enabled && !GalleryDiagnosticLogger.isEnabled()) return block()
        val start = android.os.SystemClock.elapsedRealtime()
        event(request, stage + "_start")
        return try { block() } finally {
            event(request, stage + "_end", detail = "elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}")
        }
    }
}
