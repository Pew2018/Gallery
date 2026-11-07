package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.gallery.extensions.config
import java.util.WeakHashMap
import kotlin.math.max

/** Installs themed, non-stretch classic edge feedback without touching scroll or touch listeners. */
object OxygenClassicEdgeGlow {
    private const val SCROLL_RELEASE_MS = OxygenClassicMotion.EDGE_GLOW_RELEASE_MS
    private const val SCROLL_APPEAR_MS = OxygenClassicMotion.EDGE_GLOW_APPEAR_MS

    fun install(root: View, context: Context = root.context) {
        if (root is RecyclerView) install(root, context)
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            install(root.getChildAt(index), context)
        }
    }

    fun install(recycler: RecyclerView, context: Context = recycler.context) {
        val manager = recycler.layoutManager as? LinearLayoutManager
        val vertical = manager?.orientation == RecyclerView.VERTICAL
        val refreshing = hasRefreshParent(recycler)
        val installed = recycler.getTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory) as? ClassicFactory
        if (installed?.vertical == vertical && installed.suppressTop == refreshing) return
        installed?.dispose(recycler)
        val factory = ClassicFactory(
            accent = { OxygenThemeEngine.snapshot(context, context.config).interactiveAccent },
            vertical = vertical,
            suppressTop = refreshing
        )
        recycler.edgeEffectFactory = factory
        recycler.overScrollMode = View.OVER_SCROLL_ALWAYS
        recycler.setTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory, factory)
        factory.bind(recycler)
    }

    internal fun hasRefreshParent(view: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent is SwipeRefreshLayout) return true
            parent = parent.parent
        }
        return false
    }

    internal class ClassicFactory(
        private val accent: () -> Int,
        internal val vertical: Boolean,
        internal val suppressTop: Boolean
    ) : RecyclerView.EdgeEffectFactory() {
        private val effects = WeakHashMap<ClassicEdgeEffect, Unit>()
        private var detachListener: View.OnAttachStateChangeListener? = null

        fun bind(recycler: RecyclerView) {
            detachListener?.let(recycler::removeOnAttachStateChangeListener)
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) = finishEffects()
            }
            detachListener = listener
            recycler.addOnAttachStateChangeListener(listener)
        }

        fun dispose(recycler: RecyclerView) {
            detachListener?.let(recycler::removeOnAttachStateChangeListener)
            detachListener = null
            finishEffects()
        }

        private fun finishEffects() {
            effects.keys.toList().forEach(ClassicEdgeEffect::finish)
            effects.clear()
        }

        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            if (suppressEdge(vertical, suppressTop, direction)) return NoGlowEdgeEffect(view.context)
            return ClassicEdgeEffect(view.context, view, accent) {
                (view.adapter?.itemCount ?: 0) > 0 &&
                    (view.canScrollVertically(-1) || view.canScrollVertically(1))
            }.also { effects[it] = Unit }
        }
    }

    private class NoGlowEdgeEffect(context: Context) : EdgeEffect(context) {
        override fun onPull(deltaDistance: Float) = Unit
        override fun onPull(deltaDistance: Float, displacement: Float) = Unit
        @androidx.annotation.RequiresApi(31)
        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float = 0f
        override fun onAbsorb(velocity: Int) = Unit
        override fun onRelease() = Unit
        override fun finish() = Unit
        override fun isFinished() = true
        override fun draw(canvas: Canvas) = false
    }

    internal fun suppressEdge(vertical: Boolean, suppressTop: Boolean, direction: Int): Boolean =
        !vertical || (suppressTop && direction == RecyclerView.EdgeEffectFactory.DIRECTION_TOP)

    private class ClassicEdgeEffect(
        context: Context,
        private val owner: View,
        private val accent: () -> Int,
        private val canScroll: () -> Boolean
    ) : EdgeEffect(context) {
        private val density = context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pull = 0f
        private var intensity = 0f
        private var displacement = 0.5f
        private var animator: ValueAnimator? = null

        override fun onPull(deltaDistance: Float) = onPull(deltaDistance, displacement)

        override fun onPull(deltaDistance: Float, displacement: Float) {
            if (!canScroll()) return
            this.displacement = displacement.coerceIn(0f, 1f)
            pull = (pull + deltaDistance.coerceAtLeast(0f)).coerceIn(0f, 0.62f)
            animateTo((0.20f + pull * 0.72f).coerceAtMost(0.62f), SCROLL_APPEAR_MS)
        }

        @androidx.annotation.RequiresApi(31)
        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
            if (!canScroll()) return 0f
            this.displacement = displacement.coerceIn(0f, 1f)
            val old = pull
            pull = (pull + deltaDistance).coerceIn(0f, 0.62f)
            animateTo(if (pull == 0f) 0f else (0.20f + pull * 0.72f).coerceAtMost(0.62f), SCROLL_APPEAR_MS)
            return pull - old
        }

        override fun onAbsorb(velocity: Int) {
            if (!canScroll()) return
            pull = (velocity / 12000f).coerceIn(0.18f, 0.62f)
            animateTo(pull, SCROLL_APPEAR_MS)
            owner.postOnAnimation {
                if (intensity > 0f) onRelease()
            }
        }

        override fun onRelease() {
            pull = 0f
            animateTo(0f, SCROLL_RELEASE_MS)
        }

        override fun finish() {
            animator?.cancel()
            animator = null
            pull = 0f
            intensity = 0f
            owner.invalidate()
        }

        override fun isFinished(): Boolean = intensity <= 0.001f && (animator?.isRunning != true)

        override fun draw(canvas: Canvas): Boolean {
            ClassicGlowPainter.draw(canvas, paint, accent(), intensity, displacement, density)
            return animator?.isRunning == true
        }

        private fun animateTo(target: Float, duration: Long) {
            animator?.cancel()
            val start = intensity
            if (duration == 0L || !OxygenClassicMotion.enabled(owner.context)) {
                intensity = target
                animator = null
                owner.invalidate()
                return
            }
            animator = ValueAnimator.ofFloat(start, target).apply {
                this.duration = duration
                interpolator = OxygenClassicMotion.easing()
                addUpdateListener {
                    intensity = it.animatedValue as Float
                    owner.postInvalidateOnAnimation()
                }
                start()
            }
        }
    }
}

/** Shared classic soft arc used by RecyclerView EdgeEffects and legacy scroll containers. */
internal object ClassicGlowPainter {
    fun draw(canvas: Canvas, paint: Paint, color: Int, intensity: Float, displacement: Float, density: Float) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        if (width <= 0f || height <= 0f || intensity <= 0.001f) return
        val alpha = (ColorUtils.alpha(color) * intensity * 0.72f).toInt().coerceIn(0, 160)
        val centerX = (width * displacement).coerceIn(0f, width)
        val extent = minOf(height, max(32f * density, height * 0.24f))
        paint.shader = RadialGradient(
            centerX, 0f, max(width * 0.78f, extent),
            intArrayOf(ColorUtils.setAlphaComponent(color, alpha),
                ColorUtils.setAlphaComponent(color, alpha / 3), ColorUtils.setAlphaComponent(color, 0)),
            floatArrayOf(0f, 0.50f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, extent, paint)
        paint.shader = null
    }
}

internal class ClassicGlowPulse(private val owner: View, private val accent: () -> Int) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = owner.resources.displayMetrics.density
    private var intensity = 0f
    private var animator: ValueAnimator? = null
    private var bottom = false

    fun pulse(atBottom: Boolean) {
        bottom = atBottom
        animateTo(0.55f, OxygenClassicMotion.EDGE_GLOW_APPEAR_MS)
    }

    fun release() = animateTo(0f, OxygenClassicMotion.EDGE_GLOW_RELEASE_MS)

    fun finish() {
        animator?.cancel()
        animator = null
        intensity = 0f
        owner.invalidate()
    }

    fun draw(canvas: Canvas) {
        if (intensity <= 0.001f) return
        val save = canvas.save()
        if (bottom) {
            canvas.translate(canvas.width.toFloat(), canvas.height.toFloat())
            canvas.rotate(180f)
        }
        ClassicGlowPainter.draw(canvas, paint, accent(), intensity, 0.5f, density)
        canvas.restoreToCount(save)
    }

    private fun animateTo(target: Float, duration: Long) {
        animator?.cancel()
        val start = intensity
        if (duration == 0L || !OxygenClassicMotion.enabled(owner.context)) {
            intensity = target
            animator = null
            owner.invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener {
                intensity = it.animatedValue as Float
                owner.postInvalidateOnAnimation()
            }
            start()
        }
    }
}
