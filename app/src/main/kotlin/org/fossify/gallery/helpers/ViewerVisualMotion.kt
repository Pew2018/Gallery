package org.fossify.gallery.helpers

/**
 * One Viewer transition, one clock: every visual layer below is a pure function of
 * the same normalized progress, so the image, scrim, chrome and system bars can never
 * drift apart and no layer needs its own Animator.
 *
 * Progress is direction-neutral: 0 is the grid thumbnail (or the fully restored
 * Viewer) and 1 is the settled Viewer. `entry*` maps 0 -> 1, `return*` consumes the
 * remainder (1 - transition progress) so both directions evaluate identical endpoints.
 * All curves are continuous, monotonic, and exact at 0 and 1; cancels and fallbacks
 * therefore restore the live endpoints without a snap.
 */
internal object ViewerVisualMotion {
    /** Chrome stays clear while the image starts expanding, then joins it. */
    const val ENTRY_CHROME_START = 0.12f
    const val ENTRY_CHROME_END = 0.88f

    /**
     * Controls leave earlier than the media on the way back: chrome is fully gone
     * once 28% of the return remains, while the scrim keeps following the image.
     */
    const val RETURN_CHROME_TAIL = 0.28f

    /**
     * The Viewer toolbar is cross-faded against the grid toolbar that the transparent
     * canvas exposes. The grid is only meaningfully visible once the black scrim has
     * dropped, so the cross-over lives in the last fifth of the scrim range instead of
     * dropping the Viewer toolbar to zero on the first gesture tick.
     */
    const val TOOLBAR_CROSSFADE_FLOOR = 0.80f

    /** Initial left/right predictive-back bias; a visual token, not a hard law. */
    const val BACK_EDGE_OFFSET_DP = 14

    /** A fresh gesture reaches its full side bias within this progress. */
    private const val EDGE_RESPONSE_END = 0.30f

    fun entryChrome(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return smoothstep((p - ENTRY_CHROME_START) / (ENTRY_CHROME_END - ENTRY_CHROME_START))
    }

    /** Remaining-fraction mapping for chrome during the shared-element return. */
    fun returnChrome(remaining: Float): Float {
        val r = remaining.coerceIn(0f, 1f)
        if (r <= RETURN_CHROME_TAIL) return 0f
        return smoothstep((r - RETURN_CHROME_TAIL) / (1f - RETURN_CHROME_TAIL))
    }

    /** Black backdrop opacity. Follows the image; it is the layer that must not lead. */
    fun scrim(progress: Float): Float = progress.coerceIn(0f, 1f)

    /**
     * Viewer toolbar multiplier while the grid toolbar may be exposed.
     * Returns exactly 1 when nothing is exposed, 0 once the grid owns the top bar,
     * and a continuous cross-fade in between.
     */
    fun viewerToolbarAlpha(scrimAlpha: Float): Float {
        if (scrimAlpha >= 1f) return 1f
        if (scrimAlpha <= TOOLBAR_CROSSFADE_FLOOR) return 0f
        return smoothstep((scrimAlpha - TOOLBAR_CROSSFADE_FLOOR) / (1f - TOOLBAR_CROSSFADE_FLOOR))
    }

    /**
     * Sideways bias for a system predictive-back gesture. Direction follows the
     * platform's own pre-commit movement (a left-edge gesture moves the closing page
     * right, a right-edge gesture moves it left), and the offset ramps from the
     * progress the gesture took over at, so a resumed gesture never jumps.
     *
     * @param edgeSign +1 for the left edge, -1 for the right edge, 0 for touch drags.
     * @param density display density used to resolve [BACK_EDGE_OFFSET_DP].
     * @param startProgress progress at which this gesture started (0 for a fresh one).
     */
    fun backEdgeOffsetPx(
        edgeSign: Float,
        density: Float,
        startProgress: Float,
        progress: Float,
    ): Float {
        if (edgeSign == 0f || density <= 0f) return 0f
        val start = startProgress.coerceIn(0f, 0.999f)
        val p = progress.coerceIn(0f, 1f)
        if (p <= start) return 0f
        val ramp = if (start <= 0.001f) {
            smoothstep(p / EDGE_RESPONSE_END)
        } else {
            smoothstep((p - start) / (1f - start))
        }
        return edgeSign * BACK_EDGE_OFFSET_DP * density * ramp
    }

    private fun smoothstep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
