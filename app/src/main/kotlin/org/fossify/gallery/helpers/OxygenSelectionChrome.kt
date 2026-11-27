package org.fossify.gallery.helpers

import android.app.Activity
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.fossify.gallery.extensions.config
import java.lang.ref.WeakReference

/**
 * Keeps the toolbar and status bar on the live theme during selection.
 * The CAB still hosts the count, close control and actions; only the old
 * Grey 600/700 (and accent-darkened) recolor path is retired so enter, drag
 * and exit never retarget chrome colors.
 */
object OxygenSelectionChrome {
    private class State {
        var progress = 0f
        var target = 0f
        var animator: android.animation.ValueAnimator? = null
        var mode: WeakReference<ActionMode>? = null
    }

    private val states = java.util.WeakHashMap<Activity, State>()

    internal fun progress(activity: Activity) = states[activity]?.progress ?: 0f

    internal fun animator(activity: Activity) = states[activity]?.animator?.takeIf { it.isRunning }

    /** Live theme surfaces — selection no longer substitutes a fixed grey CAB. */
    fun targets(activity: Activity): Targets {
        val colors = OxygenThemeEngine.snapshot(activity, activity.config)
        return Targets(
            toolbar = OxygenThemeColors.opaque(colors.toolbarBackground),
            status = OxygenThemeColors.opaque(colors.statusBarBackground),
            foreground = OxygenThemeColors.opaque(colors.toolbarForeground),
        )
    }

    /** Toolbar, status and foreground stay on the live theme. Progress is always 0. */
    fun frame(activity: Activity): Frame {
        val target = targets(activity)
        return Frame(
            toolbar = target.toolbar,
            status = target.status,
            foreground = target.foreground,
            progress = progress(activity),
        )
    }

    fun attach(activity: Activity, mode: ActionMode) {
        states.getOrPut(activity) { State() }.mode = WeakReference(mode)
    }

    /**
     * Selection no longer blends chrome colors. Progress is forced to 0 so status
     * bars keep the ordinary theme polarity path; paint still runs so AppCompat's
     * primary-colored CAB is overwritten with the live theme.
     */
    internal fun move(activity: Activity, target: Float) {
        val state = states.getOrPut(activity) { State() }
        state.animator?.cancel()
        state.animator = null
        state.target = 0f
        state.progress = 0f
        paint(activity)
    }

    private fun paint(activity: Activity) {
        val presented = frame(activity)
        OxygenToolbarStyle.recolorSelection(activity, presented.toolbar, presented.foreground)
        val mode = states[activity]?.mode?.get()
        val title = mode?.customView as? TextView
        title?.setTextColor(presented.foreground)
        val bar = (mode?.customView as? View)?.let(::contextBar)
            ?: activity.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
        bar?.setBackgroundColor(presented.toolbar)
        if (bar != null) tintImages(bar, presented.foreground)
        OxygenToolbarStyle.tintMenu(mode?.menu, presented.foreground)
        OxygenSystemBars.apply(activity)
    }

    private fun contextBar(view: View): View? {
        var parent: View? = view
        while (parent != null) {
            if (parent is androidx.appcompat.widget.ActionBarContextView) return parent
            parent = parent.parent as? View
        }
        return null
    }

    private fun tintImages(root: View, foreground: Int) {
        if (root is ImageView) {
            root.clearColorFilter()
            root.drawable?.mutate()?.clearColorFilter()
            root.imageTintList = android.content.res.ColorStateList.valueOf(foreground)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) tintImages(root.getChildAt(index), foreground)
        }
    }

    data class Targets(val toolbar: Int, val status: Int, val foreground: Int)
    data class Frame(val toolbar: Int, val status: Int, val foreground: Int, val progress: Float)
}
