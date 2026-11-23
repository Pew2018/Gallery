package org.fossify.gallery.helpers

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class PhotoBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    constructor(rect: RectF) : this(rect.left, rect.top, rect.right, rect.bottom)

    val width get() = right - left
    val height get() = bottom - top
    fun rect() = RectF(left, top, right, bottom)
    fun matchesOrientedAspect(header: StaticPhotoHeader): Boolean {
        val aspect = header.orientedWidth.toFloat() / header.orientedHeight
        // Decode and layout can each round by one pixel. Compare along the shorter dimension.
        return if (aspect >= 1f) abs(height - width / aspect) <= 2f else abs(width - height * aspect) <= 2f
    }
    fun near(other: PhotoBox) = abs(left - other.left) <= 1f && abs(top - other.top) <= 1f &&
        abs(right - other.right) <= 1f && abs(bottom - other.bottom) <= 1f
    fun mix(other: PhotoBox, progress: Float) = PhotoBox(
        left + (other.left - left) * progress, top + (other.top - top) * progress,
        right + (other.right - right) * progress, bottom + (other.bottom - bottom) * progress,
    )
}

internal data class StaticPhotoFileVersion(val size: Long, val modified: Long, val invalidation: Long) {
    fun matchesGrid(source: StaticPhotoSource): Boolean =
        size > 0 && source.size == size && modified > 0L && source.modified > 0L &&
            source.modified / 1_000L == modified / 1_000L && source.invalidation == invalidation

    companion object {
        fun read(path: String): StaticPhotoFileVersion {
            val file = File(path)
            return StaticPhotoFileVersion(file.length(), file.lastModified(), ViewerContentInvalidationRegistry.current(path))
        }
    }
}

internal data class StaticPhotoHeader(
    val width: Int, val height: Int, val orientation: Int, val jpeg: Boolean,
    val fileVersion: StaticPhotoFileVersion? = null,
) {
    // Glide's BitmapFactory decoder applies all EXIF orientations, including mirrors.
    val orientedWidth get() = if (orientation in 5..8) height else width
    val orientedHeight get() = if (orientation in 5..8) width else height

    companion object {
        fun isCandidate(path: String): Boolean = when (path.substringBefore('?').substringAfterLast('.').lowercase()) {
            "jpeg", "jpg", "png" -> true
            else -> false
        }

        fun read(path: String, orientation: Int): StaticPhotoHeader? {
            if (!isCandidate(path)) return null
            val version = StaticPhotoFileVersion.read(path)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            val jpeg = options.outMimeType == "image/jpeg"
            if ((!jpeg && options.outMimeType != "image/png") || options.outWidth <= 0 || options.outHeight <= 0) return null
            if (version != StaticPhotoFileVersion.read(path)) return null
            return StaticPhotoHeader(options.outWidth, options.outHeight, orientation, jpeg, version)
        }
    }
}

/**
 * Reads the oriented header off the main thread for the Viewer's first frame.
 *
 * The Grid already decoded this file, so this is a bounds-only read of the same
 * local file. It never gates the full-size decode; it only supplies the aspect
 * that the pre-decode entry preview needs. Content URIs are not static-photo
 * candidates and fall through to the existing wait-for-renderer path.
 */
internal object StaticPhotoHeaderReader {
    fun read(path: String): StaticPhotoHeader? {
        if (!path.startsWith("/")) return null
        val orientation = runCatching {
            androidx.exifinterface.media.ExifInterface(path)
                .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, -1)
        }.getOrDefault(-1)
        return StaticPhotoHeader.read(path, orientation)
    }
}

internal data class StaticPhotoThumbnail(
    val width: Int, val height: Int, val requestWidth: Int, val requestHeight: Int,
    val cropped: Boolean, val radius: Int,
) {
    /** The screen projection of the full oriented photo, including pixels cropped out of this bitmap. */
    fun project(bitmap: PhotoBox, header: StaticPhotoHeader): PhotoBox? {
        if (!cropped) return bitmap
        if (requestWidth != width || requestHeight != height || width <= 0 || height <= 0) return null
        val (decodedWidth, decodedHeight) = sampledSize(header) ?: return null
        if (decodedWidth == width && decodedHeight == height) return bitmap
        val scale = maxOf(width.toFloat() / decodedWidth, height.toFloat() / decodedHeight)
        // Glide TransformationUtils.centerCrop truncates (offset + 0.5f) toward zero.
        val dx = ((width - decodedWidth * scale) / 2f + 0.5f).toInt()
        val dy = ((height - decodedHeight * scale) / 2f + 0.5f).toInt()
        val sx = bitmap.width / width
        val sy = bitmap.height / height
        val left = bitmap.left + dx * sx
        val top = bitmap.top + dy * sy
        return PhotoBox(left, top, left + decodedWidth * scale * sx, top + decodedHeight * scale * sy)
    }

    // Glide 5 BitmapFactory Downsampler, CENTER_OUTSIDE with QUALITY rounding (API 26+).
    private fun sampledSize(header: StaticPhotoHeader): Pair<Int, Int>? {
        val w = header.orientedWidth
        val h = header.orientedHeight
        if (w <= 0 || h <= 0) return null
        val scale = maxOf(width.toFloat() / w, height.toFloat() / h)
        val outW = (w * scale).roundToInt()
        val outH = (h * scale).roundToInt()
        if (outW == 0 || outH == 0) return null
        val sample = Integer.highestOneBit(minOf(w / outW, h / outH)).coerceAtLeast(1)
        fun sampleDimension(size: Int): Int = if (header.jpeg) {
            ceil(size.toDouble() / minOf(sample, 8)).toInt() / (sample / 8).coerceAtLeast(1)
        } else size / sample
        val sw = sampleDimension(w)
        val sh = sampleDimension(h)
        if (sw == 0 || sh == 0) return null
        val densityScale = maxOf(width.toFloat() / sw, height.toFloat() / sh)
        return (sw * densityScale).roundToInt() to (sh * densityScale).roundToInt()
    }

    companion object {
        fun read(resource: BitmapDrawable, requestWidth: Int, requestHeight: Int, cropped: Boolean, radius: Int) =
            StaticPhotoThumbnail(resource.bitmap.width, resource.bitmap.height, requestWidth, requestHeight, cropped, radius)
    }
}

internal data class PhotoWindowGeometry(val visible: PhotoBox, val insetEdges: List<Int>)
internal data class PhotoViewGeometry(
    val layout: PhotoBox, val pixels: PhotoBox, val visible: PhotoBox, val window: PhotoWindowGeometry,
) {
    fun near(other: PhotoViewGeometry) = layout.near(other.layout) && pixels.near(other.pixels) &&
        visible.near(other.visible) && window == other.window
}

internal data class StaticPhotoSource(
    val path: String,
    val signature: String,
    val adapterVersion: Long,
    val thumbnail: StaticPhotoThumbnail,
    val view: PhotoViewGeometry,
    val size: Long = 0L,
    val modified: Long = 0L,
    val invalidation: Long = 0L,
) {
    fun near(other: StaticPhotoSource) = path == other.path && signature == other.signature &&
        size == other.size && modified == other.modified && invalidation == other.invalidation &&
        thumbnail == other.thumbnail && view.near(other.view)
}

internal data class StaticPhotoEntryGeometry(
    val source: StaticPhotoSource, val target: PhotoViewGeometry, val sourcePhoto: PhotoBox,
) {
    fun pixelsAt(progress: Float) = sourcePhoto.mix(target.pixels, progress)
    fun clipAt(progress: Float) = source.view.visible.mix(target.visible, progress)
    fun maskAt(progress: Float) = source.view.pixels.mix(target.pixels, progress)
    fun radiusAt(progress: Float) =
        source.thumbnail.radius * source.view.pixels.width / source.thumbnail.width * (1f - progress)
}

/** Screen coordinates use the root origin once. Insets are layout evidence, not a second offset. */
internal object StaticPhotoViewGeometry {
    fun localToScreen(view: View): Matrix {
        val parent = view.parent as? View
        if (parent == null) {
            val origin = IntArray(2).also(view::getLocationOnScreen)
            return Matrix(view.matrix).apply {
                val corner = floatArrayOf(0f, 0f)
                mapPoints(corner)
                postTranslate(origin[0] - corner[0], origin[1] - corner[1])
            }
        }
        return localToScreen(parent).apply {
            preTranslate((view.left - parent.scrollX).toFloat(), (view.top - parent.scrollY).toFloat())
            preConcat(view.matrix)
        }
    }

    fun layout(view: View): PhotoBox? =
        screenRect(view, RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()))?.let(::PhotoBox)

    fun read(
        view: ImageView,
        topOcclusion: Int? = null,
        onRejected: (String) -> Unit = {},
    ): PhotoViewGeometry? {
        fun reject(reason: String): PhotoViewGeometry? {
            onRejected(reason)
            return null
        }

        val drawable = view.drawable ?: return reject("drawable")
        val layout = layout(view) ?: return reject("layout")
        val matrix = localToScreen(view)
        val pixels = RectF(drawable.bounds)
        if (!view.imageMatrix.mapRect(pixels)) return reject("image_matrix")
        pixels.offset(view.paddingLeft.toFloat(), view.paddingTop.toFloat())
        if (!matrix.mapRect(pixels) || !MediaTransitionGeometry.isUsable(pixels)) return reject("pixel_bounds")

        // Android's child-visible traversal applies ancestor clipChildren, clipToPadding and clipBounds.
        // The actual image and its own padding still need to be intersected with that aperture.
        val visible = RectF(pixels)
        val localVisible = Rect()
        if (!view.getLocalVisibleRect(localVisible)) return reject("not_visible")
        val screenVisible = screenRect(view, RectF(localVisible)) ?: return reject("visible_matrix")
        if (!visible.intersect(screenVisible) || !visible.intersect(layout.rect())) return reject("view_aperture")
        if (view.cropToPadding) {
            val padded = screenRect(view, RectF(
                view.paddingLeft.toFloat(), view.paddingTop.toFloat(),
                (view.width - view.paddingRight).toFloat(), (view.height - view.paddingBottom).toFloat(),
            )) ?: return reject("padding")
            if (!visible.intersect(padded)) return reject("padded_aperture")
        }
        view.clipBounds?.let {
            if (!visible.intersect(screenRect(view, RectF(it)) ?: return reject("clip_bounds"))) return reject("clip_aperture")
        }

        val windowBounds = Rect().also(view::getWindowVisibleDisplayFrame)
        if (topOcclusion != null) visible.top = maxOf(visible.top, topOcclusion.toFloat())
        if (!MediaTransitionGeometry.isUsable(visible)) return reject("occluded")
        @Suppress("DEPRECATION")
        val insetEdges = view.rootWindowInsets?.let {
            listOf(it.systemWindowInsetLeft, it.systemWindowInsetTop, it.systemWindowInsetRight, it.systemWindowInsetBottom)
        }.orEmpty()
        return PhotoViewGeometry(layout, PhotoBox(pixels), PhotoBox(visible), PhotoWindowGeometry(PhotoBox(RectF(windowBounds)), insetEdges))
    }

    private fun screenRect(view: View, rect: RectF): RectF? {
        val matrix = localToScreen(view)
        val values = FloatArray(9).also(matrix::getValues)
        if (!matrix.isAffine || values[Matrix.MSCALE_X] <= 0f || values[Matrix.MSCALE_Y] <= 0f ||
            abs(values[Matrix.MSKEW_X]) > 0.0001f || abs(values[Matrix.MSKEW_Y]) > 0.0001f) return null
        return rect.takeIf { matrix.mapRect(it) && MediaTransitionGeometry.isUsable(it) }
    }
}
