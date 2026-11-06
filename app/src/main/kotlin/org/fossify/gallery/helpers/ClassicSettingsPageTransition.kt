package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import java.util.WeakHashMap

/** One visual snapshot plus the existing, interactive settings views. */
object ClassicSettingsPageTransition {
    const val DURATION_MS = 180L
    const val DISTANCE_DP = 8

    private data class Run(
        val incoming: View,
        val outgoing: SnapshotDrawable,
        val onTitleCommit: () -> Unit,
        val onFinished: () -> Unit,
        var animator: ValueAnimator? = null,
        var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null,
        var titleCommitted: Boolean = false,
        var distance: Float = 0f
    )

    private val runs = WeakHashMap<View, Run>()

    private class SnapshotDrawable(private val bitmap: Bitmap) : Drawable() {
        var offsetX = 0f
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            canvas.translate(offsetX, 0f)
            canvas.drawBitmap(bitmap, null, bounds, paint)
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun getAlpha() = paint.alpha
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        fun dispose() { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    /** incomingDirection is the physical X sign from which the target enters. */
    fun run(
        container: View,
        incoming: View,
        incomingDirection: Int,
        animationsEnabled: Boolean,
        prepareTarget: () -> Unit,
        onTitleCommit: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        cancel(container, incoming)
        incoming.alpha = 1f
        incoming.translationX = 0f
        if (!animationsEnabled || !container.isAttachedToWindow || container.width <= 0 || container.height <= 0) {
            prepareTarget()
            incoming.alpha = 1f
            incoming.translationX = 0f
            onTitleCommit()
            onFinished()
            return
        }

        // ARGB_8888 costs width * height * 4 bytes: at 1080 x 2000 this is ~8.2 MiB.
        // Allocate only one viewport snapshot per transition and release it on every exit path.
        val bitmap = try {
            Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888).also {
                container.draw(Canvas(it))
            }
        } catch (_: RuntimeException) {
            prepareTarget()
            onTitleCommit()
            onFinished()
            return
        } catch (_: OutOfMemoryError) {
            prepareTarget()
            onTitleCommit()
            onFinished()
            return
        }
        val outgoing = SnapshotDrawable(bitmap).apply { setBounds(0, 0, container.width, container.height) }
        val run = Run(incoming, outgoing, onTitleCommit, onFinished)
        container.overlay.add(outgoing)
        runs[container] = run

        try {
            prepareTarget()
            container.requestLayout()
            val preDraw = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val observer = container.viewTreeObserver
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    run.preDrawListener = null
                    if (runs[container] !== run) return true
                    if (!container.isAttachedToWindow || container.width <= 0 || container.height <= 0) {
                        runs.remove(container)
                        finishRun(container, run, commitTitle = true, invokeFinished = true)
                        return true
                    }
                    start(container, run, incomingDirection)
                    return true
                }
            }
            run.preDrawListener = preDraw
            container.viewTreeObserver.addOnPreDrawListener(preDraw)
            container.invalidate()
        } catch (failure: Throwable) {
            if (runs[container] === run) runs.remove(container)
            finishRun(container, run, commitTitle = true, invokeFinished = true)
            throw failure
        }
    }

    fun cancel(container: View, incoming: View) {
        val run = runs.remove(container)
        if (run != null) finishRun(container, run, commitTitle = true, invokeFinished = false)
        incoming.animate().cancel()
        incoming.alpha = 1f
        incoming.translationX = 0f
    }

    private fun start(container: View, run: Run, direction: Int) {
        val distance = dp(container, DISTANCE_DP) * direction
        run.distance = distance
        run.incoming.alpha = 1f
        run.incoming.translationX = distance
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { frame ->
                applyProgress(container, run, frame.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (runs[container] !== run) return
                    runs.remove(container)
                    finishRun(container, run, commitTitle = true, invokeFinished = true)
                }
            })
        }
        run.animator = animator
        animator.start()
    }

    /** Drives the same frame update used by ValueAnimator for deterministic rendered-frame tests. */
    internal fun applyProgressForTest(container: View, progress: Float) {
        val run = runs[container] ?: return
        applyProgress(container, run, progress)
    }

    /** Robolectric's software View.draw() omits ViewOverlay, so compose its actual drawable here. */
    internal fun drawCompositeForTest(container: View, canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(0, 0, container.width, container.height)
        container.draw(canvas)
        runs[container]?.outgoing?.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun applyProgress(container: View, run: Run, progress: Float) {
        if (runs[container] !== run) return
        val frameProgress = progress.coerceIn(0f, 1f)
        run.incoming.translationX = run.distance * (1f - frameProgress)
        // The incoming page remains opaque; the outgoing snapshot exits and fades
        // over it. This keeps the page surface covered and avoids a flash to the parent.
        run.outgoing.offsetX = -run.distance * frameProgress
        run.outgoing.alpha = ((1f - frameProgress) * 255f).toInt()
        if (frameProgress >= 0.78f) commitTitle(run)
        container.invalidate()
    }

    private fun commitTitle(run: Run) {
        if (run.titleCommitted) return
        run.titleCommitted = true
        run.onTitleCommit()
    }

    private fun finishRun(container: View, run: Run, commitTitle: Boolean, invokeFinished: Boolean) {
        run.preDrawListener?.let { listener ->
            val observer = container.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            run.preDrawListener = null
        }
        val animator = run.animator
        run.animator = null
        if (animator != null && (animator.isRunning || animator.isStarted)) animator.cancel()
        container.overlay.remove(run.outgoing)
        run.outgoing.dispose()
        run.incoming.alpha = 1f
        run.incoming.translationX = 0f
        if (commitTitle) commitTitle(run)
        container.invalidate()
        if (invokeFinished) run.onFinished()
    }

    private fun dp(view: View, value: Int): Float = value * view.resources.displayMetrics.density
}
