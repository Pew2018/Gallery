package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R

/** An opaque loading surface containing geometry only, never media or thumbnail references. */
internal class FavoritesReturnCover(context: Context) : View(context) {
    private val viewport = RectF()
    private val cells = ArrayList<RectF>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var capturedWidth = 0
    private var capturedHeight = 0
    private var columns = 3
    private var backgroundColorValue = 0
    private var shade = 0
    private var textColorValue = 0
    private var radius = 0f
    private var epoch = 0L
    private var revealing = false
    private var afterLayoutCleanup: (() -> Unit)? = null
    private var timeout: Runnable? = null
    private var retry: (() -> Unit)? = null
    var covering = false
        private set
    var failed = false
        private set

    init { visibility = GONE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun prepare(host: ViewGroup, grid: RecyclerView, isMedia: (Int) -> Boolean) {
        if (covering || host.width == 0 || grid.width == 0 || grid.height == 0) return
        capturedWidth = host.width
        capturedHeight = host.height
        val rect = Rect(0, 0, grid.width, grid.height - grid.paddingBottom)
        host.offsetDescendantRectToMyCoords(grid, rect)
        rect.intersect(0, 0, host.width, host.height)
        viewport.set(rect)
        cells.clear()
        columns = (grid.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount ?: 1
        for (index in 0 until grid.childCount) {
            val child = grid.getChildAt(index)
            if (!isMedia(grid.getChildAdapterPosition(child))) continue
            val cell = Rect(0, 0, child.width, child.height)
            host.offsetDescendantRectToMyCoords(child, cell)
            if (RectF.intersects(RectF(cell), viewport)) cells.add(RectF(cell))
        }
    }

    fun show(background: Int, foreground: Int, cornerRadius: Float) {
        pause()
        backgroundColorValue = background
        textColorValue = foreground
        shade = if (ColorUtils.calculateLuminance(background) >= 0.5) 0xffebebeb.toInt() else 0xff393939.toInt()
        radius = cornerRadius
        failed = false
        covering = true
        visibility = VISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        invalidate()
    }

    fun armTimeout(isCurrent: () -> Boolean, onRetry: () -> Unit) {
        timeout?.let(::removeCallbacks)
        retry = onRetry
        timeout = Runnable { if (covering && isCurrent()) showError(onRetry) }.also {
            postDelayed(it, 10_000L)
        }
    }

    fun showError(onRetry: () -> Unit) {
        if (!covering) return
        pause()
        retry = onRetry
        failed = true
        contentDescription = context.getString(R.string.favorites_return_retry)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        invalidate()
    }

    fun reveal(isCurrent: () -> Boolean) {
        if (!covering || revealing || !isCurrent()) return
        timeout?.let(::removeCallbacks)
        timeout = null
        failed = false
        revealing = true
        val generation = ++epoch
        animate().alpha(0f).setDuration(100L).withEndAction {
            if (generation == epoch && isCurrent()) dismiss()
            else if (generation == epoch) pause()
        }.start()
    }

    fun revealAfterLayout(isCurrent: () -> Boolean) {
        afterLayoutCleanup?.invoke()
        val observer = viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!isCurrent()) { afterLayoutCleanup?.invoke(); return true }
                if ((parent as? View)?.isLayoutRequested == true) return true
                afterLayoutCleanup?.invoke()
                reveal(isCurrent)
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
        afterLayoutCleanup = {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            else viewTreeObserver.removeOnPreDrawListener(listener)
            afterLayoutCleanup = null
        }
        invalidate()
    }

    fun pause() {
        epoch++
        animate().cancel()
        alpha = 1f
        revealing = false
        afterLayoutCleanup?.invoke()
        timeout?.let(::removeCallbacks)
        timeout = null
    }

    fun dismiss() {
        pause()
        covering = false
        failed = false
        retry = null
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun drawingViewport(): RectF {
        if (capturedWidth == 0 || capturedHeight == 0) {
            val density = resources.displayMetrics.density
            return RectF(0f, 56 * density, width.toFloat(), (height - 56 * density).coerceAtLeast(56 * density))
        }
        return RectF(viewport.left * width / capturedWidth, viewport.top * height / capturedHeight,
            viewport.right * width / capturedWidth, viewport.bottom * height / capturedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val area = drawingViewport()
        val checkpoint = canvas.save()
        canvas.clipRect(area)
        paint.color = backgroundColorValue
        canvas.drawRect(area, paint)
        if (failed) {
            paint.color = textColorValue
            paint.textSize = 14 * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.favorites_return_retry), area.centerX(), area.centerY(), paint)
            paint.textAlign = Paint.Align.LEFT
        } else {
            paint.color = shade
            if (capturedWidth == width && capturedHeight == height && cells.isNotEmpty()) {
                cells.forEach { canvas.drawRoundRect(it, radius, radius, paint) }
            } else {
                val gap = 2 * resources.displayMetrics.density
                val count = columns.coerceAtLeast(1)
                val size = (area.width() / count).coerceAtLeast(1f)
                var top = area.top
                while (top < area.bottom) {
                    repeat(count) { column ->
                        val left = area.left + column * size
                        canvas.drawRoundRect(RectF(left + gap, top + gap, left + size - gap, top + size - gap), radius, radius, paint)
                    }
                    top += size
                }
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!covering || !drawingViewport().contains(event.x, event.y)) return false
        if (event.action == MotionEvent.ACTION_UP && failed) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (failed) retry?.invoke()
        return true
    }

    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }
}
