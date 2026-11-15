package org.fossify.gallery.helpers

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** A content-only shared element. Letterboxes belong to the canvas, never to its snapshot. */
internal class MediaSharedSurface(private val parent: RelativeLayout) {
    var currentView: ImageView? = null
        private set
    private val hidden = linkedMapOf<View, Float>()

    fun prepare(drawable: Drawable, bounds: RectF, vararg renderedViews: View): ImageView? {
        currentView?.let { return it }
        if (!MediaTransitionGeometry.isUsable(bounds) || !parent.isAttachedToWindow) return null
        val proxy = ImageView(parent.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Android also applies this ScaleType to the grid drawable during
            // reenter. Center crop keeps even an already-cropped bitmap uniform.
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
        }
        val left = bounds.left.roundToInt()
        val top = bounds.top.roundToInt()
        val width = bounds.right.roundToInt() - left
        val height = bounds.bottom.roundToInt() - top
        parent.addView(proxy, RelativeLayout.LayoutParams(width, height).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            leftMargin = left
            topMargin = top
        })
        // Mapping runs during transition capture: layout must already describe the endpoint.
        proxy.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        proxy.layout(left, top, left + width, top + height)
        renderedViews.forEach { view -> hidden.putIfAbsent(view, view.alpha); view.alpha = 0f }
        currentView = proxy
        return proxy
    }

    fun clear() {
        hidden.forEach { (view, alpha) -> view.alpha = alpha }
        hidden.clear()
        currentView?.let {
            it.setLayerType(View.LAYER_TYPE_NONE, null)
            parent.removeView(it)
            it.setImageDrawable(null)
        }
        currentView = null
    }
}

internal object MediaTransitionGeometry {
    fun fit(width: Int, height: Int, aspect: Float): RectF? {
        if (width <= 0 || height <= 0 || !aspect.isFinite() || aspect <= 0f) return null
        val w = minOf(width.toFloat(), height * aspect)
        val h = minOf(height.toFloat(), width / aspect)
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    fun isUsable(bounds: RectF): Boolean =
        bounds.left.isFinite() && bounds.top.isFinite() &&
            bounds.right.isFinite() && bounds.bottom.isFinite() &&
            bounds.width() >= 1f && bounds.height() >= 1f

    fun sameBounds(first: RectF, second: RectF): Boolean =
        isUsable(first) && isUsable(second) &&
            abs(first.left - second.left) <= 1f && abs(first.top - second.top) <= 1f &&
            abs(first.right - second.right) <= 1f && abs(first.bottom - second.bottom) <= 1f

    fun imageBounds(view: ImageView): RectF? {
        val drawable = view.drawable ?: return null
        val bounds = RectF(drawable.bounds)
        if (!view.imageMatrix.mapRect(bounds)) return null
        bounds.offset((view.left + view.paddingLeft).toFloat(), (view.top + view.paddingTop).toFloat())
        return bounds.takeIf(::isUsable)
    }

    fun viewportSize(from: RectF, to: RectF, fraction: Float): Pair<Int, Int> =
        (from.width() + (to.width() - from.width()) * fraction).roundToInt().coerceAtLeast(1) to
            (from.height() + (to.height() - from.height()) * fraction).roundToInt().coerceAtLeast(1)

    fun textureCrop(width: Int, height: Int, displayAspect: Float): Matrix? {
        if (width <= 0 || height <= 0 || !displayAspect.isFinite() || displayAspect <= 0f) return null
        val viewportAspect = width.toFloat() / height
        return Matrix().apply {
            // TextureView maps the buffer to the whole view. Compensate for
            // that mapping using the decoded display aspect (including PAR).
            setScale(maxOf(1f, displayAspect / viewportAspect), maxOf(1f, viewportAspect / displayAspect),
                width / 2f, height / 2f)
        }
    }
}
