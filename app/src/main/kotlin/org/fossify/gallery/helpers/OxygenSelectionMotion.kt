package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.widget.ImageView
import org.fossify.gallery.R
import java.lang.ref.WeakReference

/**
 * Shrinks the thumbnail (and a folder lock overlay) and reveals the existing check.
 * The cell, name and count are not scaled. A different item, or one that is not
 * currently on screen, snaps to the end so select-all does not animate off-screen rows.
 */
object OxygenSelectionMotion {
    const val CONTENT_SCALE = 0.85f
    const val CHECK_START_SCALE = 0.5f

    private class State {
        var key: String? = null
        var selected = false
        var animator: ValueAnimator? = null
        var contents: List<WeakReference<View>> = emptyList()
        var check: WeakReference<ImageView>? = null
    }

    internal fun animator(content: View) =
        (content.getTag(R.id.selection_motion) as? State)?.animator?.takeIf { it.isRunning }

    fun onScreen(view: View): Boolean {
        if (!view.isShown || view.width == 0 || view.height == 0) return false
        val visible = Rect()
        return view.getLocalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0
    }

    fun bind(contents: List<View>, check: ImageView, key: String, selected: Boolean, play: Boolean) {
        val primary = contents.first()
        val state = primary.getTag(R.id.selection_motion) as? State ?: State().also {
            primary.setTag(R.id.selection_motion, it)
        }
        val sameItem = state.key == key
        val changed = sameItem && state.selected != selected
        state.contents.forEach { ref ->
            val previous = ref.get() ?: return@forEach
            if (previous !in contents) resetScale(previous)
        }
        state.key = key
        state.selected = selected
        state.contents = contents.map { WeakReference(it) }
        state.check = WeakReference(check)
        val animate = changed && play && OxygenClassicMotion.enabled(primary.context)
        if (!animate) {
            hold(state, changed, sameItem, selected)
            return
        }
        play(state, primary, selected)
    }

    private fun hold(state: State, changed: Boolean, sameItem: Boolean, selected: Boolean) {
        if (changed || !sameItem || state.animator?.isRunning != true) {
            state.animator?.cancel()
            state.animator = null
            apply(state, if (selected) 1f else 0f)
        }
    }

    private fun play(state: State, primary: View, selected: Boolean) {
        val from = fractionOf(primary)
        state.animator?.cancel()
        val animator = ValueAnimator.ofFloat(from, if (selected) 1f else 0f).apply {
            duration = OxygenClassicMotion.SELECTION_CONTENT_MS
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener { apply(state, it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || state.animator !== animation) return
                    state.animator = null
                    apply(state, if (state.selected) 1f else 0f)
                }
            })
        }
        state.animator = animator
        animator.start()
    }

    /** Drops a recycled holder back to an unscaled, unchecked cell before the next item binds. */
    fun release(content: ImageView) {
        val state = content.getTag(R.id.selection_motion) as? State ?: run {
            resetScale(content)
            return
        }
        state.animator?.cancel()
        state.animator = null
        state.selected = false
        state.key = null
        apply(state, 0f)
        content.setTag(R.id.selection_motion, null)
    }

    /**
     * Completes the unselected resting scale immediately. Opening a photo reads the
     * thumbnail matrix, so a tap during the restore must not wait for the clock.
     */
    fun settleForSource(content: ImageView) {
        val state = content.getTag(R.id.selection_motion) as? State
        state?.animator?.cancel()
        if (state != null) {
            state.animator = null
            state.selected = false
            apply(state, 0f)
        }
        resetScale(content)
    }

    private fun apply(state: State, fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        val scale = 1f + (CONTENT_SCALE - 1f) * t
        state.contents.forEach { ref ->
            ref.get()?.let { view ->
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.scaleX = scale
                view.scaleY = scale
            }
        }
        val mark = state.check?.get() ?: return
        mark.pivotX = mark.width / 2f
        mark.pivotY = mark.height / 2f
        val markScale = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * t
        mark.scaleX = markScale
        mark.scaleY = markScale
        mark.alpha = t
        mark.visibility = if (t <= 0f && !state.selected) View.GONE else View.VISIBLE
    }

    private fun fractionOf(view: View): Float {
        val span = 1f - CONTENT_SCALE
        if (span <= 0f) return 0f
        return ((1f - view.scaleX) / span).coerceIn(0f, 1f)
    }

    private fun resetScale(view: View) {
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
