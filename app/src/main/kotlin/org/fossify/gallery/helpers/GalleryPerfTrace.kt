package org.fossify.gallery.helpers

import android.util.Log
import org.fossify.gallery.BuildConfig

/**
 * Path-free loading measurements. Release builds retain viewer/grid/album counters
 * and durations so device boundary reports can be diagnosed without media paths.
 */
internal object GalleryPerfTrace {
    private fun enabled(name: String) = BuildConfig.DEBUG || name.startsWith("viewer_") ||
        name.startsWith("photo_grid_") || name.startsWith("album_")

    fun begin(): Long = System.nanoTime()

    fun event(name: String, count: Int? = null, revision: Long? = null) {
        val version = revision?.let { " revision=$it" }.orEmpty()
        if (enabled(name)) Log.d("GalleryPerf", "event=$name count=${count ?: -1}$version")
    }

    fun end(stage: String, startedNanos: Long, count: Int? = null) {
        if (startedNanos == 0L || !enabled(stage)) return
        val millis = (System.nanoTime() - startedNanos) / 1_000_000.0
        Log.d("GalleryPerf", "stage=$stage ms=${"%.2f".format(java.util.Locale.US, millis)} count=${count ?: -1}")
    }
}
