package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EdgeEffect
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.gallery.extensions.config
import java.util.WeakHashMap
import android.content.ContextWrapper
import org.fossify.commons.extensions.getProperBackgroundColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Installs themed, non-stretch classic edge feedback without touching scroll or touch listeners. */
object OxygenClassicEdgeGlow {
    private const val SCROLL_RELEASE_MS = OxygenClassicMotion.EDGE_GLOW_RELEASE_MS
    private const val SCROLL_APPEAR_MS = OxygenClassicMotion.EDGE_GLOW_APPEAR_MS

    fun install(root: View, context: Context = root.context, intensityScale: Float = 1f) {
        if (DialogOverscrollPolicy.isForbidden(root)) { DialogOverscrollPolicy.protect(root); return }
        if (root is RecyclerView) install(root, context, intensityScale)
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            install(root.getChildAt(index), context, intensityScale)
        }
    }

    fun install(recycler: RecyclerView, context: Context = recycler.context, intensityScale: Float = 1f) {
        if (DialogOverscrollPolicy.isForbidden(recycler)) { remove(recycler); return }
        val manager = recycler.layoutManager as? LinearLayoutManager
        val vertical = manager?.orientation == RecyclerView.VERTICAL
        val refreshing = hasRefreshParent(recycler)
        val installed = recycler.getTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory) as? ClassicFactory
        val scale = intensityScale.coerceIn(0f, 1f)
        if (installed?.vertical == vertical && installed.suppressTop == refreshing &&
            installed.intensityScale == scale) return
        installed?.dispose(recycler)
        val factory = ClassicFactory(
            accent = { edgeGlowTint(context) },
            vertical = vertical,
            suppressTop = refreshing,
            intensityScale = scale
        )
        recycler.edgeEffectFactory = factory
        recycler.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        recycler.setTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory, factory)
        factory.bind(recycler)
    }

    /** Dispose only our listeners/effects. Adapter, scrolling and touch listeners stay intact. */
    fun remove(recycler: RecyclerView) {
        val installed = recycler.getTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory) as? ClassicFactory
        (installed ?: (recycler.edgeEffectFactory as? ClassicFactory))?.dispose(recycler)
        if (installed != null || recycler.edgeEffectFactory is ClassicFactory) {
            recycler.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect = NoGlowEdgeEffect(view.context)
            }
            recycler.setTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory, null)
        }
        recycler.overScrollMode = View.OVER_SCROLL_NEVER
    }

    /** Accent hue with a modest surface blend; opacity is independently bounded by the painter. */
    internal fun edgeGlowTint(context: Context): Int {
        val accent = OxygenThemeEngine.snapshot(context, context.config).interactiveAccent
        val surface = context.getProperBackgroundColor()
        return ColorUtils.blendARGB(accent, surface, if (ColorUtils.calculateLuminance(surface) < 0.5) 0.12f else 0.20f)
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
        internal val suppressTop: Boolean,
        internal val intensityScale: Float
    ) : RecyclerView.EdgeEffectFactory() {
        private val effects = WeakHashMap<ClassicEdgeEffect, Unit>()
        private var lifecycle: Lifecycle? = null
        private val pauseObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_DESTROY) finishEffects()
        }
        private var detachListener: View.OnAttachStateChangeListener? = null
        private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

        fun bind(recycler: RecyclerView) {
            detachListener?.let(recycler::removeOnAttachStateChangeListener)
            layoutListener?.let { if (recycler.viewTreeObserver.isAlive) recycler.viewTreeObserver.removeOnGlobalLayoutListener(it) }
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    if (DialogOverscrollPolicy.isForbidden(view)) { remove(recycler); return }
                    bindLifecycle(recycler)
                    layoutListener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.addOnGlobalLayoutListener(it) }
                }
                override fun onViewDetachedFromWindow(view: View) {
                    layoutListener?.let { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
                    finishEffects()
                    lifecycle?.removeObserver(pauseObserver)
                    lifecycle = null
                }
            }
            detachListener = listener
            recycler.addOnAttachStateChangeListener(listener)
            if (recycler.isAttachedToWindow) bindLifecycle(recycler)
            val layoutObserver = ViewTreeObserver.OnGlobalLayoutListener {
                val current = recycler
                if (DialogOverscrollPolicy.isForbidden(current)) { remove(current); return@OnGlobalLayoutListener }
                val orientation = (current.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL
                val refreshParent = hasRefreshParent(current)
                val active = current.getTag(org.fossify.gallery.R.id.oxygen_edge_effect_factory) as? ClassicFactory
                if (active === this && (orientation != vertical || refreshParent != suppressTop)) {
                    current.post { if (current.isAttachedToWindow) install(current, current.context, intensityScale) }
                }
            }
            layoutListener = layoutObserver
            recycler.viewTreeObserver.addOnGlobalLayoutListener(layoutObserver)
        }

        private fun bindLifecycle(view: View) {
            var context = view.context
            while (context is ContextWrapper && context !is LifecycleOwner) {
                val base = context.baseContext
                if (base === context) break
                context = base
            }
            lifecycle?.removeObserver(pauseObserver)
            lifecycle = (context as? LifecycleOwner)?.lifecycle
            lifecycle?.addObserver(pauseObserver)
        }

        fun dispose(recycler: RecyclerView) {
            lifecycle?.removeObserver(pauseObserver)
            lifecycle = null
            detachListener?.let(recycler::removeOnAttachStateChangeListener)
            detachListener = null
            layoutListener?.let { if (recycler.viewTreeObserver.isAlive) recycler.viewTreeObserver.removeOnGlobalLayoutListener(it) }
            layoutListener = null
            finishEffects()
            effects.clear()
        }

        private fun finishEffects() {
            effects.keys.toList().forEach(ClassicEdgeEffect::finish)
        }

        public override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            if (DialogOverscrollPolicy.isForbidden(view) || suppressEdge(vertical, suppressTop, direction)) return NoGlowEdgeEffect(view.context)
            return ClassicEdgeEffect(view.context, view, accent, intensityScale) {
                !DialogOverscrollPolicy.isForbidden(view) && (view.adapter?.itemCount ?: 0) > 0 &&
                    (view.canScrollVertically(-1) || view.canScrollVertically(1))
            }.also { effects[it] = Unit }
        }
    }

    private class NoGlowEdgeEffect(context: Context) : EdgeEffect(context) {
        override fun onPull(deltaDistance: Float) = Unit
        override fun onPull(deltaDistance: Float, displacement: Float) = Unit
        @androidx.annotation.RequiresApi(31)
        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float = 0f
        @androidx.annotation.RequiresApi(31)
        override fun getDistance() = 0f
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
        private val intensityScale: Float,
        private val canScroll: () -> Boolean
    ) : EdgeEffect(context) {
        private val density = context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pull = 0f
        private var displacement = 0.5f
        private var effectWidth = 0
        private var effectHeight = 0
        private val motion = ClassicGlowAnimation(owner)
        private val releaseAfterAbsorb = Runnable { onRelease() }

        override fun setSize(width: Int, height: Int) {
            super.setSize(width, height)
            effectWidth = width
            effectHeight = height
        }

        override fun onPull(deltaDistance: Float) = onPull(deltaDistance, displacement)
        override fun onPull(deltaDistance: Float, displacement: Float) {
            updatePull(deltaDistance, displacement)
        }
        private fun updatePull(delta: Float, position: Float): Float {
            owner.removeCallbacks(releaseAfterAbsorb)
            if (!canScroll()) { finish(); return 0f }
            displacement = position.coerceIn(0f, 1f)
            val previous = pull
            pull = (pull + delta).coerceIn(0f, 1f)
            motion.animateTo(if (pull == 0f) 0f else (0.25f + pull).coerceAtMost(1f), SCROLL_APPEAR_MS)
            return pull - previous
        }
        @androidx.annotation.RequiresApi(31)
        override fun onPullDistance(deltaDistance: Float, displacement: Float): Float = updatePull(deltaDistance, displacement)
        @androidx.annotation.RequiresApi(31)
        override fun getDistance(): Float = pull

        override fun onAbsorb(velocity: Int) {
            if (!canScroll()) { finish(); return }
            owner.removeCallbacks(releaseAfterAbsorb)
            pull = 0f
            motion.animateTo((kotlin.math.abs(velocity.toLong()) / 12000f).coerceIn(0.3f, 1f), SCROLL_APPEAR_MS)
            owner.postDelayed(releaseAfterAbsorb, SCROLL_APPEAR_MS)
        }
        override fun onRelease() {
            owner.removeCallbacks(releaseAfterAbsorb)
            pull = 0f
            motion.animateTo(0f, SCROLL_RELEASE_MS)
        }
        override fun finish() {
            owner.removeCallbacks(releaseAfterAbsorb)
            pull = 0f
            motion.finish()
        }
        override fun isFinished(): Boolean = motion.isFinished && pull == 0f
        override fun draw(canvas: Canvas): Boolean {
            if (!canScroll() || (owner.isAttachedToWindow && owner.windowVisibility != View.VISIBLE)) { finish(); return false }
            ClassicGlowPainter.draw(canvas, paint, accent(), motion.intensity * intensityScale, displacement, density,
                effectWidth.toFloat(), effectHeight.toFloat())
            return !motion.isFinished
        }
    }
}

/** Elliptical radial support ends at transparent BEFORE the 48dp viewport cap.
 * Scaling a unit circle, instead of clipping a large circular gradient, keeps both
 * horizontal ends and the inward arc soft on phones, narrow lists and tablets.
 */
internal object ClassicGlowPainter {
    private val stops = floatArrayOf(0f, 0.25f, 0.65f, 1f)
    fun draw(canvas: Canvas, paint: Paint, color: Int, intensity: Float, displacement: Float, density: Float,
        viewportWidth: Float = canvas.width.toFloat(), viewportHeight: Float = canvas.height.toFloat()) {
        val width = viewportWidth
        val height = viewportHeight
        if (width <= 0f || height <= 0f || density <= 0f || intensity <= 0.001f) return
        val alpha = (32 * intensity.coerceIn(0f, 1f)).toInt()
        if (alpha == 0) return
        val extent = minOf(height, 48f * density, width * 0.22f)
        val centerX = width * displacement.coerceIn(0.35f, 0.65f)
        val save = canvas.save()
        try {
            canvas.clipRect(0f, 0f, width, extent)
            canvas.translate(centerX, -extent * 0.12f)
            canvas.scale(width * 0.62f, extent)
            paint.shader = RadialGradient(0f, 0f, 1f,
                intArrayOf(ColorUtils.setAlphaComponent(color, alpha), ColorUtils.setAlphaComponent(color, alpha * 2 / 3),
                    ColorUtils.setAlphaComponent(color, alpha / 6), Color.TRANSPARENT), stops, Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, 1f, paint)
        } finally {
            paint.shader = null
            canvas.restoreToCount(save)
        }
    }
}

/** One animator per effect. Repeated pulls update a live target instead of restarting every frame. */
internal class ClassicGlowAnimation(private val owner: View,
    private val animationsEnabled: () -> Boolean = { OxygenClassicMotion.enabled(owner.context) }) {
    var intensity = 0f
        private set
    private var target = 0f
    private var origin = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = OxygenClassicMotion.easing()
        addUpdateListener {
            intensity = origin + (target - origin) * (it.animatedValue as Float)
            owner.postInvalidateOnAnimation()
        }
    }
    val isFinished: Boolean get() = intensity <= 0.001f && !animator.isRunning
    fun animateTo(value: Float, duration: Long) {
        val next = value.coerceIn(0f, 1f)
        if (next == target && (animator.isRunning || intensity == next)) return
        if (next > 0f && target > 0f && animator.isRunning && animationsEnabled()) { target = next; return }
        animator.cancel()
        origin = intensity
        target = next
        if (duration == 0L || !animationsEnabled()) {
            intensity = next
            owner.invalidate()
        } else {
            animator.duration = duration
            animator.start()
        }
    }
    fun finish() {
        animator.cancel()
        target = 0f
        intensity = 0f
        owner.invalidate()
    }
}

internal class ClassicGlowPulse(
    private val owner: View,
    private val accent: () -> Int,
    private val maxIntensity: Float = 1f,
    animationsEnabled: () -> Boolean = { OxygenClassicMotion.enabled(owner.context) }
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = owner.resources.displayMetrics.density
    private val motion = ClassicGlowAnimation(owner, animationsEnabled)
    private var bottom = false
    private val decay = Runnable { release() }
    private var lifecycle: Lifecycle? = null
    private val pauseObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_DESTROY) finish()
    }
    fun attach() {
        var context = owner.context
        while (context is ContextWrapper && context !is LifecycleOwner) {
            val base = context.baseContext
            if (base === context) break
            context = base
        }
        lifecycle?.removeObserver(pauseObserver)
        lifecycle = (context as? LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(pauseObserver)
    }
    fun detach() {
        finish()
        lifecycle?.removeObserver(pauseObserver)
        lifecycle = null
    }
    fun pulse(atBottom: Boolean) {
        if (DialogOverscrollPolicy.isForbidden(owner) || (!atBottom && OxygenClassicEdgeGlow.hasRefreshParent(owner))) {
            finish(); return
        }
        if (bottom != atBottom) motion.finish()
        bottom = atBottom
        motion.animateTo(maxIntensity, OxygenClassicMotion.EDGE_GLOW_APPEAR_MS)
        // Fling can hit an edge AFTER ACTION_UP; every pulse must expire on its own.
        owner.removeCallbacks(decay)
        owner.postDelayed(decay, OxygenClassicMotion.EDGE_GLOW_APPEAR_MS + 20L)
    }
    fun release() {
        owner.removeCallbacks(decay)
        motion.animateTo(0f, OxygenClassicMotion.EDGE_GLOW_RELEASE_MS)
    }
    fun finish() {
        owner.removeCallbacks(decay)
        motion.finish()
    }
    fun draw(canvas: Canvas) {
        if (DialogOverscrollPolicy.isForbidden(owner) || (owner.isAttachedToWindow && owner.windowVisibility != View.VISIBLE)) {
            finish(); return
        }
        if (motion.isFinished) return
        val save = canvas.save()
        try {
            canvas.translate(owner.scrollX.toFloat(), owner.scrollY.toFloat())
            if (bottom) {
                canvas.translate(owner.width.toFloat(), owner.height.toFloat())
                canvas.rotate(180f)
            }
            ClassicGlowPainter.draw(canvas, paint, accent(), motion.intensity, 0.5f, density,
                owner.width.toFloat(), owner.height.toFloat())
        } finally { canvas.restoreToCount(save) }
    }
}
