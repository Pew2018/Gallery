package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Shared 2017-style Material linear loading indicator for folder and media screens.
 * The 3dp strip uses the page surface, with an accent-colored moving segment and
 * an independent neutral track; the colored toolbar remains untouched.
 */
object OxygenProgressStyle {
    private const val TRACK_THICKNESS_DP = 3
    internal const val SHOW_DELAY_MS = 120L
    internal const val MIN_VISIBLE_MS = 180L
    private class VisibilityState {
        var wanted = false
        var since = 0L
        var pending: Runnable? = null
    }
    private val visibilityStates = java.util.WeakHashMap<LinearProgressIndicator, VisibilityState>()

    /**
     * A brief cache hit does not flash a 3dp spinner. Once shown, preserve a
     * minimum visible interval; a new request cancels any pending hide.
     */
    fun setLoading(indicator: LinearProgressIndicator, loading: Boolean) {
        val state = visibilityStates.getOrPut(indicator) { VisibilityState() }
        state.wanted = loading
        state.pending?.let(indicator::removeCallbacks)
        state.pending = null
        if (!OxygenClassicMotion.enabled(indicator.context)) {
            indicator.visibility = if (loading) View.VISIBLE else View.GONE
            state.since = if (loading) SystemClock.uptimeMillis() else 0L
            return
        }
        if (loading && indicator.visibility == View.VISIBLE) return
        val elapsed = SystemClock.uptimeMillis() - state.since
        val delay = when {
            loading -> SHOW_DELAY_MS
            indicator.visibility != View.VISIBLE -> 0L
            else -> (MIN_VISIBLE_MS - elapsed).coerceAtLeast(0L)
        }
        if (delay == 0L) {
            indicator.visibility = if (loading) View.VISIBLE else View.GONE
            state.since = if (loading) SystemClock.uptimeMillis() else 0L
            return
        }
        val weak = WeakReference(indicator)
        val task = Runnable {
            val view = weak.get() ?: return@Runnable
            state.pending = null
            if (state.wanted == loading) {
                view.visibility = if (loading) View.VISIBLE else View.GONE
                state.since = if (loading) SystemClock.uptimeMillis() else 0L
            }
        }
        state.pending = task
        indicator.postDelayed(task, delay)
    }

    fun thicknessPx(context: Context): Int =
        (TRACK_THICKNESS_DP * context.resources.displayMetrics.density + 0.5f).toInt()

    fun apply(context: Context, config: Config, indicator: LinearProgressIndicator) {
        val colors = OxygenThemeEngine.snapshot(context, config)
        val palette = OxygenThemeColors.progressPalette(colors)
        indicator.setBackgroundColor(palette.surface)
        indicator.setIndicatorColor(palette.indicator)
        indicator.setTrackColor(palette.track)
        indicator.trackThickness = thicknessPx(context)
        indicator.trackCornerRadius = 0
    }
}
