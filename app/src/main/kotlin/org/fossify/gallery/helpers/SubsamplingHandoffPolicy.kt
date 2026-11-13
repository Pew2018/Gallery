package org.fossify.gallery.helpers

import kotlin.math.abs

/** A default-fit deep-zoom renderer may take over only from the same fitted Glide geometry. */
internal object SubsamplingHandoffPolicy {
    fun isAtFit(currentZoom: Float, initialZoom: Float, initialZoomReady: Boolean,
                tolerance: Float = MAX_ZOOM_EQUALITY_TOLERANCE.toFloat()): Boolean =
        initialZoomReady && currentZoom.isFinite() && initialZoom.isFinite() && initialZoom > 0f &&
            abs(currentZoom - initialZoom) <= tolerance
}
