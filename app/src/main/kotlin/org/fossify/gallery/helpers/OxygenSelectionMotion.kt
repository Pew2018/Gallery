package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import org.fossify.gallery.R
import java.lang.ref.WeakReference

/**
 * Shrinks the thumbnail (and a folder lock overlay) and reveals the existing check.
 * The cell, name and count are not scaled. A different item, or one that is not
 * currently on screen, snaps to the end so select-all does not animate off-screen rows.
 * While selection mode is on, unselected cells keep a faint hollow mark at the start corner.
 */
object OxygenSelectionMotion {
    /** In-cell shrink; keep ≥ 0.90 on two-column grids so the grey reveal stays thin. */
    const val CONTENT_SCALE = 0.91f
    /** Check grows with the shrink (no delayed second-half fade). */
    const val CHECK_START_SCALE = 0.9f
    const val CHECK_FADE_START = 0f
    /** Light grey cell floor under a shrunk thumb — not a stroke. */
    const val CELL_BACKDROP = 0xFFE8E8E8.toInt()
    private const val HOLLOW_STROKE_DP = 1.5f
    private const val HOLLOW_ALPHA = 0.42f

    private class State {
        var key: String? = null
        var selected = false
        var inSelectionMode = false
        var animator: ValueAnimator? = null
        var contents: List<WeakReference<View>> = emptyList()
        var check: WeakReference<ImageView>? = null
        var backdrop: WeakReference<View>? = null
        var accent = Color.TRANSPARENT
        var contrast = Color.WHITE
        var hollow: GradientDrawable? = null
        var checkSrc: Int = org.fossify.commons.R.drawable.ic_check_vector
        var hadDrawable = false
    }

    internal fun animator(content: View) =
        (content.getTag(R.id.selection_motion) as? State)?.animator?.takeIf { it.isRunning }

    fun onScreen(view: View): Boolean {
        if (!view.isShown || view.width == 0 || view.height == 0) return false
        val visible = Rect()
        return view.getLocalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0
    }

    fun bind(
        contents: List<View>,
        check: ImageView,
        key: String,
        selected: Boolean,
        play: Boolean,
        brief: Boolean = false,
        inSelectionMode: Boolean = selected,
        backdrop: View? = null,
        accent: Int = Color.TRANSPARENT,
        contrast: Int = Color.WHITE,
    ) {
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
        state.inSelectionMode = inSelectionMode
        state.contents = contents.map { WeakReference(it) }
        state.check = WeakReference(check)
        state.backdrop = backdrop?.let { WeakReference(it) }
        if (accent != Color.TRANSPARENT) state.accent = accent
        state.contrast = contrast
        if (check.drawable != null) state.hadDrawable = true
        paintBackdrop(state, inSelectionMode)
        val animate = changed && play && OxygenClassicMotion.enabled(primary.context)
        if (!animate) {
            hold(state, changed, sameItem, selected)
            return
        }
        play(state, primary, selected, brief)
    }

    private fun hold(state: State, changed: Boolean, sameItem: Boolean, selected: Boolean) {
        if (changed || !sameItem || state.animator?.isRunning != true) {
            state.animator?.cancel()
            state.animator = null
            apply(state, if (selected) 1f else 0f)
        } else if (state.inSelectionMode && !selected) {
            // Selection-mode hollow can appear without interrupting a running restore.
            apply(state, fractionOf(state.contents.firstOrNull()?.get() ?: return))
        }
    }

    private fun play(state: State, primary: View, selected: Boolean, brief: Boolean) {
        val from = fractionOf(primary)
        state.animator?.cancel()
        val animator = ValueAnimator.ofFloat(from, if (selected) 1f else 0f).apply {
            duration = if (brief) OxygenClassicMotion.SELECTION_SWIPE_MS
            else OxygenClassicMotion.SELECTION_CONTENT_MS
            interpolator = OxygenClassicMotion.selectionContentEasing()
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
        state.inSelectionMode = false
        state.key = null
        apply(state, 0f)
        paintBackdrop(state, false)
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

    internal fun checkProgress(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (CHECK_FADE_START <= 0f) return t
        if (t <= CHECK_FADE_START) return 0f
        return ((t - CHECK_FADE_START) / (1f - CHECK_FADE_START)).coerceIn(0f, 1f)
    }

    private fun apply(state: State, fraction: Float) {
        val t = fraction.coerceIn(0f, 1f)
        val scale = 1f + (CONTENT_SCALE - 1f) * t
        var width = 0
        var height = 0
        state.contents.forEach { ref ->
            ref.get()?.let { view ->
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.scaleX = scale
                view.scaleY = scale
                if (view.width > width) width = view.width
                if (view.height > height) height = view.height
            }
        }
        val mark = state.check?.get() ?: return
        if (state.inSelectionMode && !state.selected && t <= 0f) {
            paintHollow(mark, state)
            mark.pivotX = mark.width / 2f
            mark.pivotY = mark.height / 2f
            mark.scaleX = 1f
            mark.scaleY = 1f
            mark.alpha = HOLLOW_ALPHA
            mark.translationX = 0f
            mark.translationY = 0f
            mark.visibility = View.VISIBLE
            return
        }
        paintSolid(mark, state)
        val markT = checkProgress(t)
        mark.pivotX = mark.width / 2f
        mark.pivotY = mark.height / 2f
        val markScale = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * markT
        mark.scaleX = markScale
        mark.scaleY = markScale
        mark.alpha = markT
        val insetX = width * (1f - scale) / 2f
        val insetY = height * (1f - scale) / 2f
        val towardStart = alignsStart(mark)
        val rtl = mark.layoutDirection == View.LAYOUT_DIRECTION_RTL
        mark.translationX = when {
            towardStart && !rtl -> insetX
            towardStart && rtl -> -insetX
            !towardStart && !rtl -> -insetX
            else -> insetX
        }
        mark.translationY = insetY
        when {
            t > 0f || state.selected -> mark.visibility = View.VISIBLE
            state.inSelectionMode -> {
                paintHollow(mark, state)
                mark.alpha = HOLLOW_ALPHA
                mark.scaleX = 1f
                mark.scaleY = 1f
                mark.translationX = 0f
                mark.translationY = 0f
                mark.visibility = View.VISIBLE
            }
            else -> {
                mark.translationX = 0f
                mark.translationY = 0f
                mark.scaleX = 1f
                mark.scaleY = 1f
                mark.alpha = 0f
                mark.visibility = View.GONE
            }
        }
    }

    private fun alignsStart(mark: ImageView): Boolean {
        val params = mark.layoutParams as? RelativeLayout.LayoutParams ?: return false
        return params.rules[RelativeLayout.ALIGN_PARENT_START] == RelativeLayout.TRUE ||
            params.rules[RelativeLayout.ALIGN_PARENT_LEFT] == RelativeLayout.TRUE
    }

    private fun paintHollow(mark: ImageView, state: State) {
        val stroke = (HOLLOW_STROKE_DP * mark.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val hollow = state.hollow ?: GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            state.hollow = it
        }
        hollow.setColor(Color.TRANSPARENT)
        hollow.setStroke(stroke, Color.argb(180, 255, 255, 255))
        mark.background = hollow
        mark.setImageDrawable(null)
        mark.clearColorFilter()
        mark.imageTintList = null
    }

    private fun paintSolid(mark: ImageView, state: State) {
        if (mark.background is GradientDrawable && state.hollow === mark.background) {
            mark.setBackgroundResource(R.drawable.circle_background)
        }
        if (mark.drawable == null && state.hadDrawable) {
            mark.setImageResource(state.checkSrc)
        }
        if (state.accent != Color.TRANSPARENT) {
            mark.background?.mutate()?.setTint(state.accent)
        }
        mark.imageTintList = android.content.res.ColorStateList.valueOf(state.contrast)
    }

    private fun paintBackdrop(state: State, inSelectionMode: Boolean) {
        val view = state.backdrop?.get() ?: return
        if (inSelectionMode) view.setBackgroundColor(CELL_BACKDROP)
        else view.background = null
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
