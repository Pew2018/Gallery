package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import java.util.WeakHashMap

/** A bounded, one-view snapshot that grows into or collapses out of a nested directory page. */
object DirectoryPageHeroTransition {
    const val DURATION_MS = 250L
    private const val MAX_CAPTURE_EDGE = 720f

    class Snapshot internal constructor(
        internal val bitmap: Bitmap,
        internal val startBounds: RectF
    ) {
        fun dispose() {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private data class Run(
        val incoming: View,
        val drawable: SnapshotDrawable,
        val snapshot: Snapshot,
        var animator: ValueAnimator? = null,
        var preDraw: ViewTreeObserver.OnPreDrawListener? = null,
        var detach: View.OnAttachStateChangeListener? = null
    )

    private val runs = WeakHashMap<View, Run>()

    private class SnapshotDrawable(bitmap: Bitmap) : Drawable() {
        private val bitmap = bitmap
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            canvas.drawBitmap(bitmap, null, bounds, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun getAlpha() = paint.alpha
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated by Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    fun capture(view: View, container: View): Snapshot? {
        if (!view.isAttachedToWindow || !container.isAttachedToWindow ||
            view.width <= 0 || view.height <= 0 || container.width <= 0 || container.height <= 0) return null
        var bitmap: Bitmap? = null
        return try {
            val scale = minOf(1f, MAX_CAPTURE_EDGE / maxOf(view.width, view.height))
            val width = (view.width * scale).toInt().coerceAtLeast(1)
            val height = (view.height * scale).toInt().coerceAtLeast(1)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                scale(scale, scale)
                view.draw(this)
            }
            Snapshot(bitmap, boundsIn(container, view))
        } catch (_: RuntimeException) {
            bitmap?.recycle()
            null
        } catch (_: OutOfMemoryError) {
            bitmap?.recycle()
            null
        }
    }

    fun capturePage(container: View): Snapshot? = capture(container, container)

    /** Expands the captured item to the child grid, or contracts a page into its parent card. */
    fun run(
        container: View,
        incoming: View,
        snapshot: Snapshot?,
        resolveTarget: () -> View?
    ) {
        cancel(container)
        if (snapshot == null) {
            incoming.alpha = 1f
            return
        }
        if (!OxygenClassicMotion.enabled(container.context) || !container.isAttachedToWindow) {
            snapshot.dispose()
            incoming.alpha = 1f
            return
        }

        val drawable = SnapshotDrawable(snapshot.bitmap).apply {
            bounds = Rect().also { snapshot.startBounds.roundOut(it) }
        }
        val run = Run(incoming, drawable, snapshot)
        runs[container] = run
        incoming.alpha = 0.76f
        container.overlay.add(drawable)
        run.detach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = cancel(container)
        }.also { container.addOnAttachStateChangeListener(it) }
        val observer = container.viewTreeObserver
        val preDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                run.preDraw = null
                if (runs[container] !== run) return true
                val target = resolveTarget()
                if (target == null || !target.isAttachedToWindow || target.width <= 0 || target.height <= 0) {
                    runs.remove(container)
                    finish(container, run)
                } else {
                    start(container, run, boundsIn(container, target))
                }
                return true
            }
        }
        run.preDraw = preDraw
        observer.addOnPreDrawListener(preDraw)
        container.invalidate()
    }

    fun cancel(container: View) {
        val run = runs.remove(container) ?: return
        finish(container, run)
    }

    private fun start(container: View, run: Run, target: RectF) {
        val from = run.snapshot.startBounds
        val to = target
        run.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener { frame ->
                if (runs[container] !== run) return@addUpdateListener
                val p = (frame.animatedValue as Float).coerceIn(0f, 1f)
                val bounds = RectF(
                    from.left + (to.left - from.left) * p,
                    from.top + (to.top - from.top) * p,
                    from.right + (to.right - from.right) * p,
                    from.bottom + (to.bottom - from.bottom) * p
                )
                run.drawable.bounds = Rect().also { bounds.roundOut(it) }
                run.drawable.alpha = ((1f - ((p - 0.72f) / 0.28f).coerceIn(0f, 1f)) * 255f).toInt()
                run.incoming.alpha = 0.76f + 0.24f * p
                container.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (runs.remove(container) === run) finish(container, run)
                }
            })
        }.also { it.start() }
    }

    private fun finish(container: View, run: Run) {
        run.detach?.let { container.removeOnAttachStateChangeListener(it) }
        run.detach = null
        run.preDraw?.let { listener ->
            val observer = container.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
        run.preDraw = null
        run.animator?.let { if (it.isRunning || it.isStarted) it.cancel() }
        run.animator = null
        container.overlay.remove(run.drawable)
        run.snapshot.dispose()
        run.incoming.alpha = 1f
        container.invalidate()
    }

    private fun boundsIn(container: View, view: View): RectF {
        val containerLocation = IntArray(2)
        val viewLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        view.getLocationOnScreen(viewLocation)
        val left = (viewLocation[0] - containerLocation[0]).toFloat()
        val top = (viewLocation[1] - containerLocation[1]).toFloat()
        return RectF(left, top, left + view.width, top + view.height)
    }
}
