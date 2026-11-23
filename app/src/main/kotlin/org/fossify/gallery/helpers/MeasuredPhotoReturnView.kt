package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/** The destination resource is owned by the existing Grid target lease. */
internal data class StaticPhotoReturnTarget(val source: StaticPhotoSource, val drawable: Drawable)

/** Returns the existing renderer pixels to the measured Grid aperture, including partial cells. */
internal class MeasuredPhotoReturnView(context: Context) : AppCompatImageView(context) {
    private var header: StaticPhotoHeader? = null

    /** The header this surface was created with, reused by the app-owned return. */
    internal val photoHeader: StaticPhotoHeader? get() = header

    constructor(context: Context, header: StaticPhotoHeader) : this(context) {
        this.header = header
    }

    private var geometry: StaticPhotoEntryGeometry? = null
    private var gridImage: Drawable? = null
    private val gridPixels = RectF()
    private var progress = 0f
    private val inverse = Matrix()
    private val pixels = RectF()
    private val clip = RectF()
    private val rounded = RectF()
    private val mask = Path()

    fun prepare(origin: PhotoViewGeometry, destination: StaticPhotoReturnTarget): Boolean {
        val header = header ?: return false
        val source = destination.source
        if (header.fileVersion?.matchesGrid(source) != true ||
            !origin.pixels.matchesOrientedAspect(header)) return false
        val projected = source.thumbnail.project(source.view.pixels, header)
            ?.takeIf { it.matchesOrientedAspect(header) }
        val copy = destination.drawable.constantState?.newDrawable(resources)?.mutate()
            ?.takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
        if (projected == null || copy == null) return false
        gridImage = copy
        geometry = StaticPhotoEntryGeometry(source, origin, projected)
        showFrame(0f)
        return true
    }

    fun showFrame(fraction: Float) {
        progress = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val endpoints = geometry ?: return super.onDraw(canvas)
        val image = drawable ?: return
        if (!StaticPhotoViewGeometry.localToScreen(this).invert(inverse)) return
        val reverse = 1f - progress
        pixels.set(endpoints.pixelsAt(reverse).rect())
        clip.set(endpoints.clipAt(reverse).rect())
        inverse.mapRect(pixels)
        inverse.mapRect(clip)
        canvas.withClip(clip) {
            val radius = endpoints.radiusAt(reverse)
            if (radius > 0f && progress < 1f) {
                rounded.set(endpoints.maskAt(reverse).rect())
                inverse.mapRect(rounded)
                val vectors = floatArrayOf(radius, 0f, 0f, radius)
                inverse.mapVectors(vectors)
                mask.rewind()
                mask.addRoundRect(rounded, kotlin.math.abs(vectors[0]),
                    kotlin.math.abs(vectors.last()), Path.Direction.CW)
                canvas.clipPath(mask)
            }
            // The high-resolution frame and Grid buffer use one measured projection.
            // The latter covers only the portion retained by Glide's thumbnail crop.
            // No new decode, geometry curve, delay or pixel buffer is introduced.
            if (progress < 1f) drawImage(canvas, image, pixels)
            gridImage?.let { thumbnail ->
                val full = endpoints.sourcePhoto
                val retained = endpoints.source.view.pixels
                gridPixels.set(
                    pixels.left + (retained.left - full.left) / full.width * pixels.width(),
                    pixels.top + (retained.top - full.top) / full.height * pixels.height(),
                    pixels.left + (retained.right - full.left) / full.width * pixels.width(),
                    pixels.top + (retained.bottom - full.top) / full.height * pixels.height(),
                )
                val alpha = thumbnail.alpha
                try {
                    thumbnail.alpha = (progress * OPAQUE_ALPHA).roundToInt()
                    drawImage(canvas, thumbnail, gridPixels)
                } finally { thumbnail.alpha = alpha }
            }
        }
    }

    private fun drawImage(canvas: Canvas, image: Drawable, bounds: RectF) {
        canvas.withSave {
            translate(bounds.left, bounds.top)
            scale(bounds.width() / image.intrinsicWidth, bounds.height() / image.intrinsicHeight)
            image.setBounds(0, 0, image.intrinsicWidth, image.intrinsicHeight)
            image.draw(this)
        }
    }

    fun dispose() {
        geometry = null
        gridImage = null
    }

    private companion object { const val OPAQUE_ALPHA = 255 }
}
