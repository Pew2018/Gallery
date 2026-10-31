package org.fossify.gallery.helpers

import android.os.SystemClock
import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * Debug-only, path-free loading measurements. Production builds do not log
 * directory names, media paths or protected content.
 */
internal object GalleryPerfTrace {
    fun begin(): Long = if (BuildConfig.DEBUG) SystemClock.elapsedRealtimeNanos() else 0L

    fun end(stage: String, startedNanos: Long, count: Int? = null) {
        if (startedNanos == 0L) return
        val millis = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0
        Log.d("GalleryPerf", "stage=$stage ms=${"%.2f".format(java.util.Locale.US, millis)} count=${count ?: -1}")
    }
}
