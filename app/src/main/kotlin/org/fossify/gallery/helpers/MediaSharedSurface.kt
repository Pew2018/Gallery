package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** A content-only shared element. Letterboxes belong to the canvas, never to its snapshot. */
internal class MediaSharedSurface(private val parent: RelativeLayout) {
    var currentView: ImageView? = null
        private set
    private var renderers = emptyList<View>()
    private data class RendererState(val alpha: Float, val transitionAlpha: Float)
    private val hidden = linkedMapOf<View, RendererState>()

    fun prepare(
        drawable: Drawable,
        bounds: RectF,
        vararg renderedViews: View,
        deferTakeover: Boolean = false,
    ): ImageView? {
        currentView?.let {
            if (!deferTakeover) takeOver(it)
            return it
        }
        if (!MediaTransitionGeometry.isUsable(bounds) || !parent.isAttachedToWindow) return null
        val proxy = ImageView(parent.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Android also applies this ScaleType to the grid drawable during
            // reenter. Center crop keeps even an already-cropped bitmap uniform.
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
        }
        attach(proxy, bounds, renderedViews.toList(), deferTakeover)
        return proxy
    }

    fun prepareStaticEntry(drawable: Drawable, geometry: StaticPhotoEntryGeometry, renderer: ImageView): StaticPhotoEntryView? {
        if (currentView != null || !parent.isAttachedToWindow) return null
        val copy = drawable.constantState?.newDrawable(parent.resources)?.mutate() ?: return null
        val inverse = Matrix()
        if (!StaticPhotoViewGeometry.localToScreen(parent).invert(inverse)) return null
        val bounds = geometry.target.pixels.rect()
        if (!inverse.mapRect(bounds) || !MediaTransitionGeometry.isUsable(bounds)) return null
        return StaticPhotoEntryView(parent.context, copy, geometry).also {
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            attach(it, bounds, listOf(renderer), deferTakeover = true)
        }
    }

    private fun attach(proxy: ImageView, bounds: RectF, renderedViews: List<View>, deferTakeover: Boolean) {
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
        renderers = renderedViews
        currentView = proxy
        if (deferTakeover) {
            // Leave the real renderer visible until the framework restores transitionAlpha
            // and is ready to capture the shared start state. Keep the proxy discoverable.
            proxy.alpha = 0f
        } else {
            takeOver(proxy)
        }
    }

    fun takeOver(proxy: View): Boolean {
        if (currentView !== proxy || !proxy.isAttachedToWindow) return false
        renderers.forEach { view ->
            if (view !in hidden) {
                hidden[view] = RendererState(view.alpha, if (Build.VERSION.SDK_INT >= 29) view.transitionAlpha else 1f)
                view.animate().cancel()
            }
            // A renderer can still receive asynchronous alpha updates when a tile or
            // player frame becomes ready. The proxy remains the only visible surface.
            if (Build.VERSION.SDK_INT >= 29) view.transitionAlpha = 0f
            view.alpha = 0f
        }
        proxy.alpha = 1f
        return true
    }

    fun clear() {
        val proxy = currentView
        currentView = null
        proxy?.let {
            (it as? StaticPhotoEntryView)?.dispose()
            it.transitionName = null
            it.animate().cancel()
            it.setLayerType(View.LAYER_TYPE_NONE, null)
            parent.removeView(it)
            it.setImageDrawable(null)
        }
        renderers = emptyList()
        hidden.forEach { (view, state) ->
            view.alpha = state.alpha
            if (Build.VERSION.SDK_INT >= 29) view.transitionAlpha = state.transitionAlpha
        }
        hidden.clear()
    }
}

/** Draws the existing Glide resource using explicit screen endpoints, independent of platform scaleType. */
internal class StaticPhotoEntryView(
    context: Context, private var image: Drawable?, val geometry: StaticPhotoEntryGeometry,
) : ImageView(context) {
    private val screenToLocal = Matrix()
    private val mask = Path()
    private val pixels = RectF()
    private val clip = RectF()
    private val rounded = RectF()
    private var progress = 1f
    var animationCompleted = false
        private set

    fun showFrame(progress: Float) {
        if (image == null) return
        this.progress = progress
        invalidate()
    }

    fun animateFrame(progress: Float) {
        if (image == null) return
        animationCompleted = progress >= 1f
        showFrame(progress)
    }

    fun dispose() {
        image = null
    }

    override fun onDraw(canvas: Canvas) {
        val drawable = image ?: return
        if (!StaticPhotoViewGeometry.localToScreen(this).invert(screenToLocal)) return
        pixels.set(geometry.pixelsAt(progress).rect())
        clip.set(geometry.clipAt(progress).rect())
        if (!screenToLocal.mapRect(pixels) || !screenToLocal.mapRect(clip)) return
        val save = canvas.save()
        canvas.clipRect(clip)
        val radius = geometry.radiusAt(progress)
        if (radius > 0f) {
            rounded.set(geometry.maskAt(progress).rect())
            screenToLocal.mapRect(rounded)
            val radii = floatArrayOf(radius, 0f, 0f, radius)
            screenToLocal.mapVectors(radii)
            mask.rewind()
            mask.addRoundRect(rounded, kotlin.math.abs(radii[0]), kotlin.math.abs(radii[3]), Path.Direction.CW)
            canvas.clipPath(mask)
        }
        canvas.translate(pixels.left, pixels.top)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        canvas.scale(pixels.width() / width, pixels.height() / height)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        canvas.restoreToCount(save)
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

    fun centerCrop(matrix: Matrix, width: Float, height: Float, contentWidth: Int, contentHeight: Int) {
        val scale = maxOf(width / contentWidth, height / contentHeight)
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - contentWidth * scale) / 2f, (height - contentHeight * scale) / 2f)
    }
}
