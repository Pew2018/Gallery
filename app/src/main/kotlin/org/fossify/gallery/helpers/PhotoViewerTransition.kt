package org.fossify.gallery.helpers

/** Intent and timing contract for a photo grid to full-screen shared element. */
object PhotoViewerTransition {
    const val EXTRA_STATIC_PHOTO = "org.fossify.gallery.extra.STATIC_PHOTO_ENTRY"
    const val EXTRA_PATH = "org.fossify.gallery.extra.PHOTO_VIEWER_TRANSITION_PATH"
    /**
     * The Viewer draws its own photo entry from the geometry the grid published,
     * instead of relying on ActivityOptions.makeSceneTransitionAnimation.
     *
     * A scene-transition launch stamps `ActivityRecord.mHasSceneTransition` for the
     * whole Activity lifetime; BackNavigationController then forces TYPE_CALLBACK and
     * the platform never runs the cross-Activity return. The Viewer window is also
     * opaque now, so the shared-element platform transition could not be used anyway.
     */
    const val EXTRA_APP_OWNED_ENTRY = "org.fossify.gallery.extra.APP_OWNED_VIEWER_ENTRY"
    internal const val EXTRA_ENTRY_ID = "org.fossify.gallery.extra.MEDIA_ENTRY_ID"
    const val EXTRA_FADE_ONLY = "org.fossify.gallery.extra.PHOTO_VIEWER_FADE_ONLY"
    const val ELEMENT_NAME = "gallery_photo_content"
    const val DURATION_MS = OxygenClassicMotion.MEDIA_SHARED_ELEMENT_MS
    const val FADE_DURATION_MS = OxygenClassicMotion.MEDIA_SCRIM_FADE_MS
    const val READY_TIMEOUT_MS = 1_200L
    const val REQUEST_CODE = 9_071
    internal const val EXTRA_LAUNCH = "org.fossify.gallery.extra.VIEWER_LAUNCH"
    const val EXTRA_RETURN_PATH = "org.fossify.gallery.extra.MEDIA_RETURN_PATH"
    /** Paths whose deletion succeeded during this Viewer session. */
    const val EXTRA_DELETED_PATHS = "org.fossify.gallery.extra.MEDIA_DELETED_PATHS"
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

/** One instance per Activity entry. Only captured shared elements can drive the shared clock. */
internal class PhotoTransitionCompletionGate {
    enum class Stage { WAITING, RELEASE_REQUESTED, RELEASED, MAPPED, REJECTED, CAPTURED, SHARED, FALLBACK, COMPLETE, CANCELLED }

    var stage = Stage.WAITING
        private set
    val active get() = stage != Stage.COMPLETE && stage != Stage.CANCELLED
    val awaitingRelease get() = stage == Stage.WAITING || stage == Stage.RELEASE_REQUESTED

    fun requestRelease() = advance(Stage.WAITING, Stage.RELEASE_REQUESTED)
    fun release() = awaitingRelease && advance(stage, Stage.RELEASED)
    fun map() = advance(Stage.RELEASED, Stage.MAPPED)
    fun reject() = (stage == Stage.RELEASED || stage == Stage.MAPPED) && advance(stage, Stage.REJECTED)
    fun capture() = advance(Stage.MAPPED, Stage.CAPTURED)
    fun startMotion() = advance(Stage.CAPTURED, Stage.SHARED)
    fun startFallback() = active && !awaitingRelease && stage != Stage.FALLBACK && advance(stage, Stage.FALLBACK)
    fun complete() = (stage == Stage.SHARED || stage == Stage.FALLBACK) && advance(stage, Stage.COMPLETE)
    fun cancel() = active && advance(stage, Stage.CANCELLED)

    private fun advance(from: Stage, to: Stage): Boolean {
        if (stage != from) return false
        stage = to
        return true
    }
}
