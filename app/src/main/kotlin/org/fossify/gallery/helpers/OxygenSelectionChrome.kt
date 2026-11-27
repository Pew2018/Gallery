package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.extensions.config
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Blends the ordinary toolbar and status bar into the selection chrome.
 * The saved accent is only read, never written. A later count update paints the
 * current frame and does not start another blend.
 */
object OxygenSelectionChrome {
    const val TOOLBAR_COLOR = 0xFF757575.toInt()
    const val STATUS_COLOR = 0xFF616161.toInt()
    const val FOREGROUND_COLOR = Color.WHITE

    private const val ENDPOINT = 0.001f

    private class State {
        var progress = 0f
        var target = 0f
        var animator: ValueAnimator? = null
        var mode: WeakReference<ActionMode>? = null
    }

    private val states = java.util.WeakHashMap<Activity, State>()

    internal fun progress(activity: Activity) = states[activity]?.progress ?: 0f

    internal fun animator(activity: Activity) = states[activity]?.animator?.takeIf { it.isRunning }

    /** Toolbar, foreground and blend position. Progress 0 is the live theme. */
    fun frame(activity: Activity): Frame {
        val themeBar = OxygenThemeEngine.toolbarSurface(activity, activity.config)
        val themeText = OxygenThemeEngine.toolbarForeground(activity, activity.config)
        val progress = progress(activity)
        return Frame(
            toolbar = OxygenThemeColors.opaque(ColorUtils.blendARGB(themeBar, TOOLBAR_COLOR, progress)),
            foreground = OxygenThemeColors.opaque(ColorUtils.blendARGB(themeText, FOREGROUND_COLOR, progress)),
            progress = progress,
        )
    }

    fun attach(activity: Activity, mode: ActionMode) {
        states.getOrPut(activity) { State() }.mode = WeakReference(mode)
    }

    internal fun move(activity: Activity, target: Float) {
        val state = states.getOrPut(activity) { State() }
        val running = state.animator?.takeIf { it.isRunning }
        val settled = abs(state.progress - target) <= ENDPOINT
        if (settled && (running == null || state.target == target)) {
            state.target = target
            paint(activity)
            return
        }
        if (!OxygenClassicMotion.enabled(activity)) {
            running?.cancel()
            state.animator = null
            state.target = target
            state.progress = target
            paint(activity)
            return
        }
        if (running != null && state.target == target) {
            paint(activity)
            return
        }
        running?.cancel()
        state.animator = null
        state.target = target
        start(activity, state, target)
    }

    private fun start(activity: Activity, state: State, target: Float) {
        val animator = ValueAnimator.ofFloat(state.progress, target).apply {
            duration = OxygenClassicMotion.SELECTION_CHROME_MS
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener {
                if (activity.isDestroyed) {
                    cancel()
                    return@addUpdateListener
                }
                state.progress = it.animatedValue as Float
                paint(activity)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || state.animator !== animation) return
                    state.animator = null
                }
            })
        }
        state.animator = animator
        animator.start()
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

    data class Frame(val toolbar: Int, val foreground: Int, val progress: Float)
}
