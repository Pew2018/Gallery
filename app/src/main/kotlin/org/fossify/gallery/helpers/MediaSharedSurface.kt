package org.fossify.gallery.helpers

import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlin.math.roundToInt

/** A content-only shared element. Letterboxes belong to the canvas, never to its snapshot. */
internal class MediaSharedSurface(private val parent: RelativeLayout) {
    private var image: ImageView? = null
    private val hidden = linkedMapOf<View, Float>()

    fun prepare(drawable: Drawable, bounds: RectF, vararg renderedViews: View): ImageView? {
        image?.let { return it }
        if (bounds.width() < 1f || bounds.height() < 1f || !parent.isAttachedToWindow) return null
        val proxy = ImageView(parent.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
        }
        val width = bounds.width().roundToInt().coerceAtLeast(1)
        val height = bounds.height().roundToInt().coerceAtLeast(1)
        parent.addView(proxy, RelativeLayout.LayoutParams(width, height).apply {
            leftMargin = bounds.left.roundToInt()
            topMargin = bounds.top.roundToInt()
        })
        // Mapping runs during transition capture: layout must already describe the endpoint.
        proxy.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        proxy.layout(bounds.left.roundToInt(), bounds.top.roundToInt(),
            bounds.left.roundToInt() + width, bounds.top.roundToInt() + height)
        renderedViews.forEach { view -> hidden.putIfAbsent(view, view.alpha); view.alpha = 0f }
        image = proxy
        return proxy
    }

    fun clear() {
        hidden.forEach { (view, alpha) -> view.alpha = alpha }
        hidden.clear()
        image?.let { parent.removeView(it); it.setImageDrawable(null) }
        image = null
    }
}

internal object MediaTransitionGeometry {
    fun fit(width: Int, height: Int, mediaWidth: Int, mediaHeight: Int): RectF? {
        if (width <= 0 || height <= 0 || mediaWidth <= 0 || mediaHeight <= 0) return null
        val scale = minOf(width.toFloat() / mediaWidth, height.toFloat() / mediaHeight)
        val w = mediaWidth * scale
        val h = mediaHeight * scale
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }
}
