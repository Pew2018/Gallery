package org.fossify.gallery.helpers

import kotlin.math.abs
import kotlin.math.min

/**
 * Owns the gesture renderer's fit baseline across renderer resource changes.
 *
 * Why this exists: the 2026-11-25 device log shows an entry whose shared element
 * animation completed, then `viewer_transition_photo_unavailable count=4`
 * (`gestureImageIsAtFit() == false`) at the very first predictive-back attempt
 * (00:20:56.470, entryMapped=true, hasSelection=true). The entry photo was loaded
 * through the Phase 2.1 proportional preview: Glide first delivered a half-size
 * preview, then upgraded to the full decode. Both drawables share the same aspect,
 * so the gestures library patched the zoom by exactly
 * `min(oldWidth/newWidth, oldHeight/newHeight)` and the *visual* state stayed
 * fitted - but the absolute zoom moved from the preview's fit zoom (e.g. 0.5 for a
 * 2000px preview in a 1000px viewport) to the full decode's fit zoom (0.25 for a
 * 4000px original). The baseline captured from the preview silently went stale, and
 * the at-fit check compared the new fit against the old one and rejected a
 * legitimate shared return.
 *
 * The fix must never compare the current zoom against a baseline from another
 * renderer resource, and it must never treat a zoom delta caused by the library's
 * own resource patch as user input. The listener therefore feeds every state change
 * here together with the renderer's image size:
 *
 *  - when the image size is unchanged, any zoom delta is user (or programmatic)
 *    input and only updates [currentZoom];
 *  - when the image size changes while the user was at the fitted scale, the
 *    incoming state zoom *is* the new resource's fit zoom (a proportional patch of
 *    the old fit, or a reset) and the baseline is recaptured from it;
 *  - when the image size changes while the user had zoomed in, the user's zoom
 *    survives the upgrade scaled by the same patch, so the baseline maps through
 *    the patch instead of recapturing the (zoomed) state. The user's zoomed state
 *    is thereby never mistaken for fit, and pinching back to the minimum zoom
 *    correctly restores shared-return eligibility;
 *  - [invalidate] drops the baseline when the fragment starts a new request, which
 *    keeps paths that already reset state (rotation, portrait stripe, media switch)
 *    on their existing recapture-on-first-callback behaviour.
 */
internal class GestureZoomBaseline(private val tolerance: Float) {

    /** What the last state change did to the baseline. */
    sealed interface Update {
        /** Same renderer resource; only the current zoom moved. */
        data object None : Update

        /**
         * A fresh baseline was captured from the incoming state zoom: either the
         * first fitted frame of a new request, or a resource upgrade observed while
         * the user was at the fitted scale.
         */
        data object Rebased : Update

        /**
         * A resource upgrade observed while the user had zoomed: the baseline was
         * mapped into the new resource's coordinate system by [scale], and the
         * user's zoom was preserved.
         */
        data class Scaled(val scale: Float) : Update
    }

    /** The fit zoom of the renderer resource [baselineWidth]x[baselineHeight]. */
    var initialZoom = 1f
        private set

    /** True once a baseline has been captured for the current renderer resource. */
    var hasBaseline = false
        private set

    /** The most recent state zoom, whatever produced it. */
    var currentZoom = 1f
        private set

    private var baselineWidth = 0f
    private var baselineHeight = 0f

    /** Drops the baseline; the next fitted state change recaptures it. */
    fun invalidate() {
        hasBaseline = false
    }

    /**
     * Records one gesture-controller state change.
     *
     * [imageWidth]/[imageHeight] are the renderer's current image size in the same
     * units the library patches zoom with, [hasSizes] reports whether the controller
     * has both an image and a viewport size, and [stateZoom] is the incoming state.
     */
    fun onStateChanged(
        imageWidth: Float,
        imageHeight: Float,
        hasSizes: Boolean,
        stateZoom: Float,
    ): Update {
        var update: Update = Update.None
        if (hasSizes && imageWidth > 0f && imageHeight > 0f) {
            if (hasBaseline && (imageWidth != baselineWidth || imageHeight != baselineHeight)) {
                // The renderer's resource changed. The zoom delta carried by this
                // callback is the library's proportional patch (or a state reset),
                // never user input: no gesture can swap the drawable.
                if (abs(currentZoom - initialZoom) <= tolerance) {
                    // The user was at the fitted scale, so the incoming state zoom is
                    // exactly the new resource's fit zoom - recapture it.
                    hasBaseline = false
                    update = Update.Rebased
                } else {
                    // The user had zoomed in. Their zoom survives the upgrade scaled
                    // by the same patch, so map the baseline through the patch and
                    // keep both their state and the shared-return rejection intact.
                    val scale = min(baselineWidth / imageWidth, baselineHeight / imageHeight)
                    initialZoom *= scale
                    baselineWidth = imageWidth
                    baselineHeight = imageHeight
                    update = Update.Scaled(scale)
                }
            }
            if (!hasBaseline) {
                initialZoom = stateZoom
                baselineWidth = imageWidth
                baselineHeight = imageHeight
                hasBaseline = true
                if (update == Update.None) update = Update.Rebased
            }
        }
        currentZoom = stateZoom
        return update
    }

    /**
     * True when the photo is at its fitted scale. Requires a baseline, matching the
     * shared-proxy gate the device log exercised.
     */
    fun isAtFit(): Boolean =
        SubsamplingHandoffPolicy.isAtFit(currentZoom, initialZoom, hasBaseline, tolerance)

    /**
     * The bare zoom comparison the horizontal paging and dismiss gates have always
     * used. Unlike [isAtFit] it defaults to "at fit" before any baseline exists,
     * because paging an image whose first frame has not landed yet must stay
     * possible.
     */
    fun zoomMatchesBaseline(): Boolean = abs(currentZoom - initialZoom) <= tolerance
}
