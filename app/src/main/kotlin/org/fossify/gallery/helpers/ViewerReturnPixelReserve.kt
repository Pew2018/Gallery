package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
 * Phase 2.2 ownership rule: the reserve stores a *defensive copy* of the bitmap, not a
 * reference to the Glide-delivered drawable. Holding the drawable itself could not
 * prove ownership: the moment the source holder is recycled, its request cleared or the
 * resource evicted from Glide's memory cache, the underlying bitmap becomes eligible
 * for `BitmapPool` reuse, and a later decode could overwrite pixels the reserve still
 * displays (wrong/ corrupt thumbnails). A copy severs every link to the request, the
 * holder and the cache in one ~1-2 ms `memcpy` per entry, and makes `take()` a true
 * exclusive transfer: the returned drawable is the only handle to those pixels.
 *
 * Bounded on purpose:
 *  - capacity is a hard constant; the eldest entry leaves when a fourth arrives,
 *  - a per-entry byte cap rejects decodes too large to reserve,
 *  - a total byte budget evicts eldest entries before the reserve can grow,
 *  - entries are keyed by the Grid's request list (path, signature, type, crop, corners,
 *    animation flags), so a different file version or different crop cannot be reused,
 *  - a hit is *removed* from the reserve, so nothing is handed out twice,
 *  - [clear] runs when the return ends, the Grid goes away and on configuration change,
 *    and the owner-scoped overload keeps one Activity instance's teardown from
 *    releasing another instance's session (a configuration change creates the new
 *    Activity before the old one is destroyed),
 *  - the caller-supplied size check rejects a drawable decoded for a different grid size.
 */
internal class ViewerReturnPixelReserve(capacity: Int = DEFAULT_CAPACITY) {
    private class Entry(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val bytes: Int,
        val owner: Any?,
    )

    private val entries = object : LinkedHashMap<Any, Entry>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Entry>) =
            size > capacity || values.sumOf { entry -> entry.bytes } > MAX_TOTAL_BYTES
    }

    /**
     * Records a copy of [drawable]'s pixels as what [key] would decode to.
     *
     * Only software `BitmapDrawable` pixels can be reserved safely; animated or
     * non-bitmap drawables are skipped rather than held beyond their request's
     * lifetime. Returns false when nothing was stored (never stored: rejected key,
     * unusable drawable, oversized decode, or a copy failure).
     */
    fun remember(key: Any?, drawable: Drawable, owner: Any? = null): Boolean {
        if (key == null) return false
        val source = (drawable as? BitmapDrawable)?.bitmap ?: return false
        if (source.isRecycled) return false
        if (source.config == Bitmap.Config.HARDWARE) return false
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return false
        if (source.byteCount > MAX_ENTRY_BYTES) return false
        val copy = try {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            return false
        } ?: return false
        entries[key] = Entry(copy, width, height, copy.byteCount, owner)
        return true
    }

    /**
     * Takes the pixels for [key] if they were decoded for a [width]x[height] view.
     *
     * The size check is the one thing the request list cannot express: the Grid asks for
     * the holder's measured size, so a column-count or list/grid change must miss rather
     * than hand over a bitmap of the wrong resolution. A hit is consumed and the caller
     * becomes the sole owner of the returned drawable.
     */
    fun take(key: Any?, width: Int, height: Int): Drawable? {
        if (key == null || width <= 0 || height <= 0) return null
        val entry = entries[key] ?: return null
        if (!fits(entry, width, height)) return null
        entries.remove(key)
        return BitmapDrawable(null, entry.bitmap)
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

    /** Releases every entry. */
    fun clear() = entries.clear()

    /**
     * Releases only the entries owned by [owner]. A Grid Activity instance passes
     * itself so its teardown cannot destroy a newer session's reservation across a
     * configuration change (where the new instance is created before the old one is
     * destroyed).
     */
    fun clear(owner: Any?) {
        if (owner == null) {
            entries.clear()
            return
        }
        entries.entries.removeIf { it.value.owner === owner }
    }

    val size: Int get() = entries.size

    /** Estimated bytes currently reserved, for diagnostics and budget assertions. */
    val reservedBytes: Int get() = entries.values.sumOf { entry -> entry.bytes }

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

        /**
         * A single entry above this size cannot be reserved: the bytes would dominate
         * the budget for one thumbnail that a fresh decode reproduces cheaply.
         */
        const val MAX_ENTRY_BYTES = 12 * 1024 * 1024

        /**
         * Hard byte ceiling for the whole reserve, on top of the entry-count capacity.
         * With the default capacity and grid-sized thumbnails (~1.8 MB) the count limit
         * binds first; the byte budget only guards unusual viewports and list layouts.
         */
        const val MAX_TOTAL_BYTES = 24 * 1024 * 1024

        val shared = ViewerReturnPixelReserve()
    }
}
