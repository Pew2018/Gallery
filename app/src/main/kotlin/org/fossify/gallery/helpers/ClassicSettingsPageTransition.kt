package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.view.View
import android.view.animation.PathInterpolator
import java.util.WeakHashMap

/**
 * Small View-system page transition shared by settings hosts. The outgoing frame is
 * snapshotted before the caller prepares the target page, so both pages move together
 * without recreating business views or exposing a replaced-content frame.
 */
object ClassicSettingsPageTransition {
    const val DURATION_MS = 180L
    const val DISTANCE_DP = 8
    const val PAGE_ALPHA_FLOOR = 0.92f
    private val running = WeakHashMap<View, ValueAnimator>()
    private data class Pending(val task: Runnable, val discard: () -> Unit)
    private val pending = WeakHashMap<View, Pending>()

    fun cancel(container: View, incoming: View) {
        pending.remove(container)?.let {
            container.removeCallbacks(it.task)
            it.discard()
        }
        running.remove(container)?.cancel()
        incoming.animate().cancel()
        incoming.translationX = 0f
        incoming.alpha = 1f
    }

    private class SnapshotDrawable(private val bitmap: Bitmap) : Drawable() {
        var offsetX: Float = 0f
        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            canvas.translate(offsetX, 0f)
            canvas.drawBitmap(bitmap, null, bounds, paint)
            canvas.restoreToCount(save)
        }
        private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun getAlpha() = paint.alpha
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        fun dispose() { if (!bitmap.isRecycled) bitmap.recycle() }
    }

    /** incomingDirection is the physical X sign from which the target enters. */
    fun run(
        container: View,
        incoming: View,
        incomingDirection: Int,
        animationsEnabled: Boolean,
        prepareTarget: () -> Unit,
        onFinished: () -> Unit = {}
    ) {
        cancel(container, incoming)
        incoming.alpha = 1f
        incoming.translationX = 0f
        if (!animationsEnabled || container.width <= 0 || container.height <= 0) {
            prepareTarget()
            incoming.alpha = 1f
            incoming.translationX = 0f
            onFinished()
            return
        }

        val bitmap = Bitmap.createBitmap(container.width, container.height, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(bitmap))
        val outgoing = SnapshotDrawable(bitmap).apply { setBounds(0, 0, container.width, container.height) }
        container.overlay.add(outgoing)
        prepareTarget()
        incoming.alpha = PAGE_ALPHA_FLOOR
        incoming.translationX = dp(container, DISTANCE_DP) * incomingDirection

        // Let visibility and target layout resolve before the first animated frame.
        container.requestLayout()
        val start = Runnable {
            pending.remove(container)
            if (!container.isAttachedToWindow) {
                container.overlay.remove(outgoing)
                outgoing.dispose()
                incoming.translationX = 0f
                incoming.alpha = 1f
                return@Runnable
            }
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURATION_MS
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { frame ->
                    val progress = frame.animatedValue as Float
                    incoming.translationX = dp(container, DISTANCE_DP) * incomingDirection * (1f - progress)
                    incoming.alpha = PAGE_ALPHA_FLOOR + (1f - PAGE_ALPHA_FLOOR) * progress
                    outgoing.offsetX = -dp(container, DISTANCE_DP) * incomingDirection * progress
                    outgoing.alpha = (255f * (1f - progress * (1f - PAGE_ALPHA_FLOOR))).toInt()
                    container.invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        running.remove(container)
                        container.overlay.remove(outgoing)
                        outgoing.dispose()
                        incoming.translationX = 0f
                        incoming.alpha = 1f
                        onFinished()
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        running.remove(container)
                        container.overlay.remove(outgoing)
                        outgoing.dispose()
                        incoming.translationX = 0f
                        incoming.alpha = 1f
                    }
                })
            }
            running[container] = animator
            animator.start()
        }
        pending[container] = Pending(start) {
            container.overlay.remove(outgoing)
            outgoing.dispose()
        }
        container.post(start)
    }

    private fun dp(view: View, value: Int): Float = value * view.resources.displayMetrics.density
}
