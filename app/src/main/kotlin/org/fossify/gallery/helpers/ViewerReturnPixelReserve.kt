package org.fossify.gallery.helpers

import android.graphics.drawable.Drawable

/**
 * The Grid's own decodable pixels for the media the Viewer is currently showing, kept
 * alive in a small, explicitly bounded reserve until the Viewer returns.
 *
 * Why this exists: the 2026-11-24 device log records return request 13 as
 *
 *     viewer_requested            23:06:48.783
 *     thumbnail_request LOW/HIGH  23:06:48.816 / .819
 *     grid_target pixels_unavailable
 *     RESOURCE_READY              23:06:49.495   (+676 ms)
 *     viewer_animator_started     23:06:49.548
 *
 * The request had already been promoted to HIGH, so despatch was not the cost: the Grid
 * had to *decode* the target again. It had decoded the same file at launch (`source_
 * validated drawable=true`), but that bitmap was only reachable through Glide's memory
 * cache, and the Viewer's own full-size photo decodes evict it. The return then sits in
 * `RETURN_WAIT_TARGET` for the whole decode.
 *
 * The reserve holds the already-decoded drawable under the *same* request identity the
 * Grid bound it with, so a rebound holder can be handed exactly the pixels it would
 * have decoded - same file version, same crop, same size - without a second decode.
 *
 * Bounded on purpose:
 *  - capacity is a hard constant; the eldest entry leaves when a fourth arrives,
 *  - entries are keyed by the Grid's request list (path, signature, type, crop, corners,
 *    animation flags), so a different file version or different crop cannot be reused,
 *  - a hit is *removed* from the reserve, so nothing is handed out twice,
 *  - [clear] runs when the return ends, the Grid goes away and on configuration change,
 *    so the pixels never outlive the transition that needs them,
 *  - the caller-supplied size check rejects a drawable decoded for a different grid size.
 */
internal class ViewerReturnPixelReserve(capacity: Int = DEFAULT_CAPACITY) {
    private class Entry(val drawable: Drawable, val width: Int, val height: Int)

    private val entries = object : LinkedHashMap<Any, Entry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Entry>) = size > capacity
    }

    /** Records [drawable] as the pixels [key] would decode to. */
    fun remember(key: Any?, drawable: Drawable) {
        if (key == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return
        entries[key] = Entry(drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
    }

    /**
     * Takes the pixels for [key] if they were decoded for a [width]x[height] view.
     *
     * The size check is the one thing the request list cannot express: the Grid asks for
     * the holder's measured size, so a column-count or list/grid change must miss rather
     * than hand over a bitmap of the wrong resolution. A hit is consumed.
     */
    fun take(key: Any?, width: Int, height: Int): Drawable? {
        if (key == null || width <= 0 || height <= 0) return null
        val entry = entries[key] ?: return null
        if (!fits(entry, width, height)) return null
        entries.remove(key)
        return entry.drawable
    }

    /** True when [key] is held for a [width]x[height] view, for diagnostics. */
    fun contains(key: Any?, width: Int, height: Int): Boolean {
        if (key == null) return false
        val entry = entries[key] ?: return false
        return fits(entry, width, height)
    }

    fun forget(key: Any?) {
        if (key == null) return
        entries.remove(key)
    }

    fun clear() = entries.clear()

    val size: Int get() = entries.size

    private fun fits(entry: Entry, width: Int, height: Int) =
        kotlin.math.abs(entry.width - width) <= SIZE_TOLERANCE_PX &&
            kotlin.math.abs(entry.height - height) <= SIZE_TOLERANCE_PX

    companion object {
        /**
         * Three entries cover the media on screen and one neighbour on either side at
         * ~670x670 ARGB_8888 (~1.8 MB each), i.e. a bounded ~5 MB peak that is released
         * as soon as the return completes.
         */
        const val DEFAULT_CAPACITY = 3

        /** Rounding between the requested size and the decoded bitmap. */
        const val SIZE_TOLERANCE_PX = 1

        val shared = ViewerReturnPixelReserve()
    }
}
