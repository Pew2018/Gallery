package org.fossify.gallery.helpers

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator

/**
 * OxygenOS 7.1.1 Classic motion tokens. Android owns RippleDrawable's lifecycle;
 * other motion is brief, unscaled, and observes the global animator switch.
 */
object OxygenClassicMotion {
    // Viewer timings are application choices, not measured ROM resource values.
    const val VIEWER_CHROME_MS = 170L
    // The supplied Google Photos reference settles its folder/photo geometry
    // in roughly 50–120 ms. Keep the shared image and the follow-on content
    // fade inside that observed window instead of carrying the older 300 ms
    // page-transition timing into media navigation.
    const val MEDIA_SHARED_ELEMENT_MS = 120L
    const val MEDIA_SCRIM_FADE_MS = 120L
    const val VIEWER_CHROME_ENTER_MS = 225L
    const val VIEWER_CHROME_EXIT_MS = 195L
    const val VIEWER_LOADING_DELAY_MS = 120L
    const val VIEWER_LOADING_MIN_VISIBLE_MS = 180L
    const val VIEWER_LOADING_FADE_MS = 140L

    const val CHECKBOX_MS = 140L
    const val SEARCH_MS = 160L
    const val EDGE_GLOW_APPEAR_MS = 70L
    const val EDGE_GLOW_RELEASE_MS = 220L
    const val TAB_MS = 150L
    const val SWITCH_MS = 180L
    const val TOGGLE_CHECKBOX_MS = 200L
    /** Initial selection chrome and thumbnail motion. Phase 7 owns the final timing. */
    const val SELECTION_CHROME_MS = 180L
    const val SELECTION_CONTENT_MS = 180L
    const val LIST_MOVE_MS = 170L
    const val SETTINGS_ENTER_MS = ClassicSettingsPageTransition.DURATION_MS
    const val SETTINGS_DISTANCE_DP = ClassicSettingsPageTransition.DISTANCE_DP

    /**
     * Ordinary page entry. Owned by the Activity window transition on every API level:
     * Android 14+ drives it through the platform Shell (activityOpenEnterAnimation on
     * the page window style), below that through AppTransition. Nothing in a page may
     * fade or translate itself on top of it.
     */
    const val PAGE_ENTRY_MS = 180L
    const val PAGE_ENTRY_DISTANCE_DP = 8
    const val PAGE_ENTRY_START_ALPHA = 0.96f

    /**
     * Nested folder level change. Owned by the grid container inside one page, so it
     * carries the same weight as the page entry but on its own clock and without any
     * window motion.
     */
    const val NESTED_FOLDER_MS = 170L
    const val NESTED_FOLDER_DISTANCE_DP = 8
    const val NESTED_FOLDER_START_ALPHA = 0.96f

    fun enabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
    }.getOrDefault(true)

    /**
     * Physical X sign of a forward/backward page change: +1 enters from the right in
     * LTR and -1 enters from the left in RTL. Callers multiply their start offset by
     * this so both navigation semantics stay inline-start relative.
     */
    fun horizontalDirection(forward: Boolean, view: View): Int =
        (if (forward) 1 else -1) * (if (resolvesRtl(view)) -1 else 1)

    private fun resolvesRtl(view: View): Boolean {
        val direction = if (view.isLayoutDirectionResolved) view.layoutDirection
        else view.resources.configuration.layoutDirection
        return direction == View.LAYOUT_DIRECTION_RTL
    }

    fun easing() = PathInterpolator(0.4f, 0f, 0.2f, 1f)
}
