package org.fossify.gallery.helpers

/** Intent and timing contract for a photo grid to full-screen shared element. */
object PhotoViewerTransition {
    const val EXTRA_PATH = "org.fossify.gallery.extra.PHOTO_VIEWER_TRANSITION_PATH"
    const val EXTRA_FADE_ONLY = "org.fossify.gallery.extra.PHOTO_VIEWER_FADE_ONLY"
    const val ELEMENT_NAME = "gallery_photo_content"
    const val DURATION_MS = OxygenClassicMotion.MEDIA_SHARED_ELEMENT_MS
    const val FADE_DURATION_MS = OxygenClassicMotion.MEDIA_SCRIM_FADE_MS
    const val READY_TIMEOUT_MS = 1_200L
    const val REQUEST_CODE = 9_071
    const val EXTRA_RETURN_PATH = "org.fossify.gallery.extra.MEDIA_RETURN_PATH"
}

/** A return thumbnail is usable only while its bound media identity and pixels agree. */
internal object PhotoViewerReturnTarget {
    fun isLoadedFor(path: String, boundPath: String?, requestKey: Any?, loadedKey: Any?): Boolean =
        path.isNotEmpty() && boundPath == path && requestKey != null && loadedKey == requestKey

    /**
     * A loaded holder can still be a poor shared-element target when RecyclerView clips
     * all but a few pixels of it. Require a meaningful visible intersection before the
     * return transition is mapped; callers can first scroll the stable media identity
     * into view and retry.
     */
    fun hasUsableVisibleArea(
        imageWidth: Int,
        imageHeight: Int,
        visibleWidth: Int,
        visibleHeight: Int,
    ): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0 || visibleWidth <= 0 || visibleHeight <= 0) return false
        val imageArea = imageWidth.toLong() * imageHeight
        val visibleArea = visibleWidth.toLong() * visibleHeight
        return visibleArea * MIN_VISIBLE_AREA_FRACTION_DENOMINATOR >=
            imageArea * MIN_VISIBLE_AREA_FRACTION_NUMERATOR
    }

    private const val MIN_VISIBLE_AREA_FRACTION_NUMERATOR = 1L
    private const val MIN_VISIBLE_AREA_FRACTION_DENOMINATOR = 3L
}

/** Allows a single no-animation completion until shared motion starts or entry is abandoned. */
internal class PhotoTransitionCompletionGate {
    private var generation = 0L
    private var pendingGeneration: Long? = null

    fun begin(): Long? {
        if (pendingGeneration != null) return null
        return (++generation).also { pendingGeneration = it }
    }

    fun cancel() {
        generation++
        pendingGeneration = null
    }

    fun complete(token: Long): Boolean {
        if (pendingGeneration != token) return false
        pendingGeneration = null
        return true
    }
}
