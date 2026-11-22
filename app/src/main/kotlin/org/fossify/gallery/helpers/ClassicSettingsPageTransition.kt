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
import java.util.WeakHashMap

/** One visual snapshot plus the existing, interactive settings views. */
// The one-shot run and the gesture-held step share one snapshot/overlay lifetime and the
// same frame maths, so they stay in one place rather than crossing files through internals.
@Suppress("TooManyFunctions")
object ClassicSettingsPageTransition {
    const val DURATION_MS = 180L
    const val DISTANCE_DP = 8

    /** A single-owner viewport image; never captures a Toolbar or system bar. */
    class Snapshot internal constructor(internal val bitmap: Bitmap) {
        fun dispose() { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    fun direction(forward: Boolean, view: View): Int =
        (if (forward) 1 else -1) * (if ((if (view.isLayoutDirectionResolved) view.layoutDirection
            else view.resources.configuration.layoutDirection) == View.LAYOUT_DIRECTION_RTL) -1 else 1)

    fun capture(container: View): Snapshot? {
        if (!container.isAttachedToWindow || container.width <= 0 || container.height <= 0) return null
        var bitmap: Bitmap? = null
        return try {
            bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
            container.draw(Canvas(bitmap))
            Snapshot(bitmap)
        } catch (_: RuntimeException) {
            bitmap?.recycle()
            null
        } catch (_: OutOfMemoryError) {
            bitmap?.recycle()
            null
        }
    }

    internal data class Run(
        val incoming: View,
        val outgoing: SnapshotDrawable,
        val onTitleCommit: () -> Unit,
        val onFinished: () -> Unit,
        var animator: ValueAnimator? = null,
        var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null,
        var titleCommitted: Boolean = false,
        var distance: Float = 0f,
        var progress: Float = 0f,
        var startAlpha: Float = 1f,
        var interactive: Boolean = false,
        var detachListener: View.OnAttachStateChangeListener? = null
    )

    /**
     * A page change held at the finger's progress instead of playing on release.
     *
     * The caller renders the destination as soon as the gesture starts, then feeds the
     * system's progress in. Nothing about the page's own state is committed here: on
     * cancel the caller restores it through the lambda it passed in, on commit the
     * remaining motion finishes.
     */
    class Step internal constructor(private val container: View, private val run: Run) {
        val isActive: Boolean get() = runs[container] === run

        fun render(progress: Float) {
            if (!isActive) return
            applyProgress(container, run, progress)
        }

        fun cancel(onDone: () -> Unit = {}) {
            if (!isActive) { onDone(); return }
            animateTo(container, run, 0f) {
                // The snapshot still covers the container at 0, so the restored page can
                // lay itself out underneath without the user seeing a wrong frame.
                onDone()
                val observer = container.viewTreeObserver
                if (!observer.isAlive || !container.isAttachedToWindow) {
                    finishRun(container, run, commitTitle = false, invokeFinished = true)
                    return@animateTo
                }
                val listener = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (observer.isAlive) observer.removeOnPreDrawListener(this)
                        if (runs[container] === run) {
                            runs.remove(container)
                            finishRun(container, run, commitTitle = false, invokeFinished = true)
                        }
                        return true
                    }
                }
                observer.addOnPreDrawListener(listener)
            }
        }

        fun commit(onDone: () -> Unit = {}) {
            if (!isActive) { onDone(); return }
            animateTo(container, run, 1f) {
                if (runs[container] === run) runs.remove(container)
                finishRun(container, run, commitTitle = true, invokeFinished = true)
                onDone()
            }
        }
    }

    private val runs = WeakHashMap<View, Run>()

    /** Finishes the remaining motion from wherever the finger left the page. */
    private fun animateTo(container: View, run: Run, target: Float, onEnd: () -> Unit) {
        val from = run.progress.coerceIn(0f, 1f)
        val remaining = kotlin.math.abs(target - from)
        if (remaining < 0.001f || !container.isAttachedToWindow) {
            applyProgress(container, run, target)
            onEnd()
            return
        }
        val animator = ValueAnimator.ofFloat(from, target).apply {
            duration = (DURATION_MS * remaining).toLong().coerceAtLeast(1L)
            interpolator = OxygenClassicMotion.easing()
            addUpdateListener { frame -> applyProgress(container, run, frame.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (run.animator === animation) run.animator = null
                    onEnd()
                }
            })
        }
        run.animator = animator
        animator.start()
    }

    /**
     * Starts an interactive page change. The caller switches to the destination page in
     * [prepareTarget]; cancelling is the caller's job, because only it knows the state
     * that was replaced.
     */
    fun begin(
        container: View,
        incoming: View,
        incomingDirection: Int,
        animationsEnabled: Boolean,
        prepareTarget: () -> Unit,
        onTitleCommit: () -> Unit = {},
        distanceDp: Int = DISTANCE_DP,
        incomingStartAlpha: Float = 1f
    ): Step? {
        if (!animationsEnabled) return null
        cancel(container, incoming)
        incoming.alpha = 1f
        incoming.translationX = 0f
        val image = capture(container) ?: return null
        val outgoing = SnapshotDrawable(image.bitmap).apply {
            setBounds(0, 0, container.width, container.height)
        }
        val run = Run(incoming, outgoing, onTitleCommit, {}, interactive = true)
        run.distance = dp(container, distanceDp) * incomingDirection
        run.startAlpha = incomingStartAlpha.coerceIn(0f, 1f)
        container.overlay.add(outgoing)
        runs[container] = run
        run.detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { cancel(container, incoming) }
        }.also { container.addOnAttachStateChangeListener(it) }
        prepareTarget()
        container.requestLayout()
        applyProgress(container, run, 0f)
        return Step(container, run)
    }

    internal class SnapshotDrawable(private val bitmap: Bitmap) : Drawable() {
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
        fun pixelForTest(x: Int, y: Int): Int = bitmap.getPixel(x, y)
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
        onFinished: () -> Unit = {},
        snapshot: Snapshot? = null
    ) {
        cancel(container, incoming)
        incoming.alpha = 1f
        incoming.translationX = 0f
        if (!animationsEnabled || (snapshot == null &&
                (!container.isAttachedToWindow || container.width <= 0 || container.height <= 0))) {
            snapshot?.dispose()
            prepareTarget()
            onTitleCommit()
            onFinished()
            return
        }
        // A transferred snapshot may arrive before the destination's first layout.
        val image = snapshot ?: capture(container)
        if (image == null) {
            prepareTarget()
            onTitleCommit()
            onFinished()
            return
        }
        val bitmap = image.bitmap
        val outgoing = SnapshotDrawable(bitmap).apply { setBounds(0, 0, container.width, container.height) }
        val run = Run(incoming, outgoing, onTitleCommit, onFinished)
        container.overlay.add(outgoing)
        runs[container] = run
        run.detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { cancel(container, incoming) }
        }.also { container.addOnAttachStateChangeListener(it) }

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
                    outgoing.setBounds(0, 0, container.width, container.height)
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
        // An interrupted gesture still shows the page it started from, so its title must
        // not be committed on the way out.
        if (run != null) finishRun(container, run, commitTitle = !run.interactive, invokeFinished = false)
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
            interpolator = OxygenClassicMotion.easing()
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

    internal fun snapshotPixelForTest(container: View, x: Int, y: Int): Int? =
        runs[container]?.outgoing?.pixelForTest(x, y)

    internal fun snapshotAlphaForTest(container: View): Int? = runs[container]?.outgoing?.alpha

    private fun applyProgress(container: View, run: Run, progress: Float) {
        if (runs[container] !== run) return
        val frameProgress = progress.coerceIn(0f, 1f)
        run.progress = frameProgress
        run.incoming.alpha = run.startAlpha + (1f - run.startAlpha) * frameProgress
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
        run.detachListener?.let { container.removeOnAttachStateChangeListener(it) }
        run.detachListener = null
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
