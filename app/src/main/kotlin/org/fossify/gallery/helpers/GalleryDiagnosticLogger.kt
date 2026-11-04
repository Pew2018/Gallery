package org.fossify.gallery.helpers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.gallery.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR }

internal data class DiagnosticLoggingState(val enabled: Boolean, val expiresAtMillis: Long) {
    fun isActive(nowMillis: Long): Boolean = enabled && expiresAtMillis > nowMillis
    companion object {
        fun toggle(enabled: Boolean, nowMillis: Long): DiagnosticLoggingState = DiagnosticLoggingState(
            enabled, if (enabled) nowMillis + TimeUnit.HOURS.toMillis(24) else 0L
        )
    }
}

/** Private, bounded, opt-in diagnostics. Events contain only explicitly selected metadata. */
internal object GalleryDiagnosticLogger {
    const val ENABLED_KEY = "gallery_debug_logging_enabled"
    const val EXPIRES_KEY = "gallery_debug_logging_expires_at"
    private const val PREFS_LIMIT = 5L * 1024 * 1024
    private const val ROTATE_AT = 1024L * 1024
    private const val MAX_FILES = 5
    private const val MAX_QUEUE = 1024
    private const val MAX_CRASH_BYTES = 16 * 1024
    private const val DIRECTORY = "diagnostics"

    @Volatile private var enabled = false
    @Volatile private var expiresAtMillis = 0L
    @Volatile private var sessionId = UUID.randomUUID().toString().take(8)
    @Volatile private var hashSalt = UUID.randomUUID().toString()
    @Volatile private var directory: File? = null
    @Volatile private var prefs: SharedPreferences? = null
    private val dropped = AtomicLong()
    private val lock = Any()
    private val io = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUE), { runnable -> Thread(runnable, "gallery-diagnostics").apply { isDaemon = true } },
        { _, _ -> dropped.incrementAndGet() })
    private val callbackHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun initialize(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPrefs()
        directory = File(app.filesDir, DIRECTORY).apply { mkdirs() }
        sessionId = UUID.randomUUID().toString().take(8)
        hashSalt = UUID.randomUUID().toString()
        syncEnabledFromPreferences()
        installCrashHandler()
        recordExitInfo(app)
    }

    fun isEnabled(): Boolean {
        if (enabled && expiresAtMillis <= System.currentTimeMillis()) enabled = false
        return enabled
    }

    fun setEnabled(context: Context, value: Boolean) {
        val appPrefs = context.applicationContext.getSharedPrefs()
        val state = DiagnosticLoggingState.toggle(value, System.currentTimeMillis())
        appPrefs.edit().putBoolean(ENABLED_KEY, state.enabled).putLong(EXPIRES_KEY, state.expiresAtMillis).apply()
        prefs = appPrefs
        enabled = value
        expiresAtMillis = state.expiresAtMillis
        if (value) event(DiagnosticLevel.INFO, "Diagnostics", "logging_enabled", fields = mapOf("expiresInHours" to 24))
    }

    fun syncEnabledFromPreferences(): Boolean {
        val p = prefs ?: return false
        val active = DiagnosticLoggingState(p.getBoolean(ENABLED_KEY, false), p.getLong(EXPIRES_KEY, 0L))
            .isActive(System.currentTimeMillis())
        if (!active && p.getBoolean(ENABLED_KEY, false)) p.edit().putBoolean(ENABLED_KEY, false).apply()
        enabled = active
        expiresAtMillis = p.getLong(EXPIRES_KEY, 0L)
        return active
    }

    fun event(
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        module: String,
        name: String,
        requestId: Any? = null,
        fields: Map<String, Any?> = emptyMap()
    ) {
        if (!isEnabled()) return
        val safeModule = DiagnosticPrivacy.token(module)
        val safeName = DiagnosticPrivacy.token(name)
        val safeFields = fields.entries.asSequence().take(16).mapNotNull { (key, value) ->
            if (DiagnosticPrivacy.isSensitiveKey(key) || value == null) null
            else "${DiagnosticPrivacy.token(key)}=${DiagnosticPrivacy.value(value)}"
        }.joinToString(" ")
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val record = "${timestamp(wall)} ${level.name} module=$safeModule event=$safeName session=$sessionId" +
            (requestId?.let { " request=${DiagnosticPrivacy.value(it)}" } ?: "") +
            " elapsedRealtimeMs=$elapsed" + if (safeFields.isEmpty()) "" else " $safeFields"
        if (io.queue.size >= MAX_QUEUE - 1 && level == DiagnosticLevel.DEBUG) {
            dropped.incrementAndGet()
            return
        }
        try {
            io.execute {
                try {
                    val missed = dropped.getAndSet(0)
                    if (missed > 0) appendLine("${timestamp(System.currentTimeMillis())} WARN module=Diagnostics event=events_dropped session=$sessionId count=$missed")
                    appendLine(record)
                    if (level == DiagnosticLevel.ERROR) Log.e("GalleryDiagnostics", "$safeModule/$safeName")
                } catch (error: Exception) {
                    Log.w("GalleryDiagnostics", "Could not persist diagnostic event", error)
                }
            }
        } catch (_: RuntimeException) { dropped.incrementAndGet() }
    }

    fun snapshot(callback: (String, String?) -> Unit) {
        executeIo {
            val text = runCatching { logFiles().joinToString("\n") { it.readText(StandardCharsets.UTF_8).trimEnd() } }.getOrDefault("")
            val crash = runCatching { File(directory, "crash.txt").takeIf { it.exists() }?.readText(StandardCharsets.UTF_8) }.getOrNull()
            callbackHandler.post { callback(text, crash) }
        }
    }

    fun clear(callback: () -> Unit) {
        executeIo {
            synchronized(lock) {
                directory?.listFiles()?.filter { it.isFile && (it.name.startsWith("diagnostic") || it.name == "crash.txt") }
                    ?.forEach { runCatching { it.delete() } }
            }
            callbackHandler.post { callback() }
        }
    }

    fun stats(callback: (Long, String?) -> Unit) {
        executeIo {
            val files = logFiles() + listOfNotNull(File(directory, "crash.txt").takeIf { it.exists() })
            val last = files.maxByOrNull { it.lastModified() }?.let { timestamp(it.lastModified()) }
            callbackHandler.post { callback(files.sumOf { it.length() }, last) }
        }
    }

    fun writeExport(output: OutputStream, snapshot: String, crash: String?, context: Context) {
        val app = context.applicationContext
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val content = buildString {
            appendLine("Gallery diagnostics")
            appendLine("App version: ${packageInfo.versionName ?: "unknown"} ($versionCode)")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${DiagnosticPrivacy.value(Build.MANUFACTURER)} ${DiagnosticPrivacy.value(Build.MODEL)}")
            appendLine("Logging enabled: ${isEnabled()}")
            appendLine("Session: $sessionId")
            appendLine("Log started: ${snapshot.lineSequence().firstOrNull()?.take(23) ?: "no events"}")
            appendLine("Exported: ${timestamp(System.currentTimeMillis())}")
            appendLine("\n--- Events ---")
            appendLine(snapshot)
            if (!crash.isNullOrBlank()) { appendLine("\n--- Previous crash summary ---"); appendLine(DiagnosticPrivacy.sanitize(crash)) }
        }
        output.use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
    }

    fun session(): String = sessionId

    fun anonymousId(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("$hashSalt:$value".toByteArray(StandardCharsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    private fun executeIo(block: () -> Unit) {
        try { io.execute { runCatching(block).onFailure { Log.w("GalleryDiagnostics", "Diagnostic I/O failed", it) } } }
        catch (_: RuntimeException) { }
    }

    private fun appendLine(line: String) = synchronized(lock) {
        val dir = directory ?: return@synchronized
        dir.mkdirs()
        var active = File(dir, "diagnostic-0.log")
        if (active.exists() && active.length() + line.toByteArray(StandardCharsets.UTF_8).size + 1 > ROTATE_AT) {
            for (index in MAX_FILES - 1 downTo 1) {
                val from = File(dir, "diagnostic-${index - 1}.log")
                val to = File(dir, "diagnostic-$index.log")
                if (to.exists()) to.delete()
                if (from.exists()) from.renameTo(to)
            }
            active = File(dir, "diagnostic-0.log")
        }
        FileOutputStream(active, true).bufferedWriter(StandardCharsets.UTF_8).use { it.append(line).append('\n') }
        enforceLimit(dir)
    }

    private fun enforceLimit(dir: File) {
        val logs = logFiles().sortedBy { it.lastModified() }.toMutableList()
        var size = logs.sumOf { it.length() }
        while (size > PREFS_LIMIT && logs.isNotEmpty()) {
            val oldest = logs.removeAt(0)
            size -= oldest.length()
            oldest.delete()
        }
    }

    private fun logFiles(): List<File> = directory?.listFiles()?.filter { it.isFile && it.name.matches(Regex("diagnostic-[0-4]\\.log")) }
        ?.sortedByDescending { it.name.substringAfter("diagnostic-").substringBefore('.').toIntOrNull() ?: 0 } ?: emptyList()

    private fun timestamp(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))

    private fun installCrashHandler() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is GalleryDiagnosticExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(GalleryDiagnosticExceptionHandler(current) { thread, error ->
            if (!isEnabled()) return@GalleryDiagnosticExceptionHandler
            try {
                val limited = buildString {
                    appendLine("${timestamp(System.currentTimeMillis())} FATAL session=$sessionId thread=${DiagnosticPrivacy.value(thread.name)}")
                    appendLine("type=${error.javaClass.name}")
                    error.stackTrace.take(100).forEach { appendLine(DiagnosticPrivacy.sanitize(" at $it")) }
                    error.cause?.let { appendLine("cause=${it.javaClass.name}: ${DiagnosticPrivacy.sanitize(it.message.orEmpty()).take(512)}") }
                }.take(MAX_CRASH_BYTES)
                synchronized(lock) { File(directory, "crash.txt").writeText(limited, StandardCharsets.UTF_8) }
            } catch (_: Throwable) { /* crash recording must never replace the original failure */ }
        })
    }

    private fun recordExitInfo(context: Context) {
        if (Build.VERSION.SDK_INT < 30 || !enabled) return
        executeIo {
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val info = manager.getHistoricalProcessExitReasons(context.packageName, 0, 4)
                info.take(3).forEach { exit ->
                    event(DiagnosticLevel.WARN, "Process", "previous_exit", fields = mapOf(
                        "reason" to exit.reason, "status" to exit.status, "importance" to exit.importance,
                        "timestamp" to timestamp(exit.timestamp)
                    ))
                }
            } catch (error: Exception) { event(DiagnosticLevel.WARN, "Process", "exit_info_unavailable", fields = mapOf("type" to error.javaClass.simpleName)) }
        }
    }
}

internal class GalleryDiagnosticExceptionHandler(
    private val delegate: Thread.UncaughtExceptionHandler?,
    private val record: (Thread, Throwable) -> Unit
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { record(thread, error) } catch (_: Throwable) { }
        delegate?.uncaughtException(thread, error)
    }
}

internal object DiagnosticPrivacy {
    private val pathRegex = Regex("(?i)(?:file://|content://|/storage/|/sdcard/|/data/user/|/data/data/)[^\\s,;]+")
    private val uriRegex = Regex("(?i)content://[^\\s,;]+")
    private val filenameRegex = Regex("""(?i)\b[^/\\\s]+\.(?:jpe?g|png|webp|heic|gif|mp4|mkv|mov|3gp)\b""")
    fun isSensitiveKey(key: String) = key.contains("path", true) || key.contains("uri", true) || key.contains("password", true) ||
        key.contains("pin", true) || key.contains("credential", true) || key.contains("token", true) || key.contains("gps", true) || key.contains("location", true)
    fun token(value: String) = value.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(48)
    fun value(value: Any): String = sanitize(value.toString()).replace(Regex("\\s+"), "_").take(180)
    fun sanitize(value: String): String = value.replace(pathRegex, "[REDACTED_PATH]").replace(uriRegex, "[REDACTED_URI]").replace(filenameRegex, "[REDACTED_FILE]")
}
