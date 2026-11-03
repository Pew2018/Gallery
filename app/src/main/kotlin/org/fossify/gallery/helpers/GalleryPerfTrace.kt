package org.fossify.gallery.helpers

import android.os.SystemClock
import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * Path-free loading measurements. Release builds retain only viewer counters
 * and durations so device boundary reports can be diagnosed without media paths.
 */
internal object GalleryPerfTrace {
    fun begin(): Long = SystemClock.elapsedRealtimeNanos()

    fun event(name: String, count: Int? = null) {
        if (BuildConfig.DEBUG || name.startsWith("viewer_")) Log.d("GalleryPerf", "event=$name count=${count ?: -1}")
    }

    fun end(stage: String, startedNanos: Long, count: Int? = null) {
        if (startedNanos == 0L || (!BuildConfig.DEBUG && !stage.startsWith("viewer_"))) return
        val millis = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0
        Log.d("GalleryPerf", "stage=$stage ms=${"%.2f".format(java.util.Locale.US, millis)} count=${count ?: -1}")
    }
}
