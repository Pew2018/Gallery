package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import kotlin.math.abs

/**
 * The grid thumbnail, moved on the same geometry clock as the full-resolution
 * [StaticPhotoEntryView] that replaces it.
 *
 * The Viewer is opaque and the Grid releases its decoded thumbnail before the
 * full-size image is ready. Waiting for that decode made the window and the
 * photo expansion two independent clocks: the window had already faded in while
 * the thumbnail sat still. This view lets the entry start on the first frame,
 * so the window alpha, scrim, chrome and geometry always share one progress.
 *
 * A cropped grid thumbnail only contains the centre of the photo. The correctly
 * projected crop is therefore drawn on top of the same bitmap stretched across
 * the whole interpolated photo box. At progress 0 the stretched copy is outside
 * the source clip and invisible, so the first frame matches the Grid exactly;
 * as the clip opens the stretched copy only fills the bands the crop never had.
 * The real image takes over on the same frame the [StaticPhotoEntryView] proxy
 * can be mapped, so no temporary pixels are treated as final.
 */
internal class GridPhotoEntryPreview(
    context: Context,
    private val image: Drawable,
    private val geometry: StaticPhotoEntryGeometry,
) : View(context) {
    private val inverse = Matrix()
    private val pixels = RectF()
    private val retained = RectF()
    private val clip = RectF()
    private val rounded = RectF()
    private val mask = Path()
    private var progress = 0f

    /** The measured fullscreen endpoint this pre-decode preview animates toward. */
    internal val entryTarget: PhotoViewGeometry get() = geometry.target

    fun showFrame(frame: Float) {
        progress = frame.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!StaticPhotoViewGeometry.localToScreen(this).invert(inverse)) return
        val frame = progress
        val framePixels = geometry.pixelsAt(frame)
        pixels.set(framePixels.rect())
        clip.set(geometry.clipAt(frame).rect())
        retained.set(retainedScreenBox(framePixels, geometry.sourcePhoto, geometry.source.view.pixels).rect())
        if (!inverse.mapRect(pixels) || !inverse.mapRect(retained) || !inverse.mapRect(clip)) return
        if (!MediaTransitionGeometry.isUsable(pixels) || !MediaTransitionGeometry.isUsable(clip)) return
        canvas.withClip(clip) {
            val radius = geometry.radiusAt(frame)
            if (radius > 0f) {
                rounded.set(geometry.maskAt(frame).rect())
                inverse.mapRect(rounded)
                val vectors = floatArrayOf(radius, 0f, 0f, radius)
                inverse.mapVectors(vectors)
                mask.rewind()
                mask.addRoundRect(rounded, abs(vectors[0]), abs(vectors.last()), Path.Direction.CW)
                canvas.clipPath(mask)
            }
            drawImage(canvas, image, pixels)
            drawImage(canvas, image, retained)
        }
    }

    private fun drawImage(canvas: Canvas, drawable: Drawable, bounds: RectF) {
        if (!MediaTransitionGeometry.isUsable(bounds)) return
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        canvas.withSave {
            translate(bounds.left, bounds.top)
            scale(bounds.width() / width, bounds.height() / height)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
    }
}

/**
 * Screen box, inside an interpolated full-photo [pixels] box, that a cropped
 * thumbnail actually retains. [retained] is the same full-photo projection the
 * Grid recorded at capture time. The mapping is pure so the no-blank-band
 * contract can be checked without a rendered surface.
 */
internal fun retainedScreenBox(pixels: PhotoBox, full: PhotoBox, retained: PhotoBox): PhotoBox {
    if (full.width <= 0f || full.height <= 0f) return PhotoBox(0f, 0f, 0f, 0f)
    return PhotoBox(
        pixels.left + (retained.left - full.left) / full.width * pixels.width,
        pixels.top + (retained.top - full.top) / full.height * pixels.height,
        pixels.left + (retained.right - full.left) / full.width * pixels.width,
        pixels.top + (retained.bottom - full.top) / full.height * pixels.height,
    )
}
