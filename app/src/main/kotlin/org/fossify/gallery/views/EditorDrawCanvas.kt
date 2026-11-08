package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BitmapShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class EditorBrushMode { DRAW, MOSAIC, BLUR }

/** Stores committed paths in the fitted preview bitmap's pixel coordinates. */
internal data class EditorStroke(
    val mode: EditorBrushMode,
    val path: Path,
    val width: Float,
    val color: Int,
    val mosaicBlockSize: Float = MOSAIC_BLOCK_SIZE,
    val blurRadius: Float = BLUR_RADIUS
)

private const val MOSAIC_BLOCK_SIZE = 14f
private const val BLUR_RADIUS = 18f
private const val BLUR_DOWNSAMPLE = 2

class EditorDrawCanvas(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val strokes = mutableListOf<EditorStroke>()
    private var activePath = Path()
    private var activeStartX = 0f
    private var activeStartY = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var active = false
    private var rejectedGesture = false
    private var mode = EditorBrushMode.DRAW
    private var backgroundBitmap: Bitmap? = null
    private var cachedPreview: Bitmap? = null
    private var cacheRevision = -1
    private var revision = 0
    private var currentColor = context.getProperPrimaryColor()
    private var currentWidth = resources.getDimension(R.dimen.full_brush_size) * 0.5f
    var onUndoAvailabilityChanged: ((Boolean) -> Unit)? = null

    fun setMode(newMode: EditorBrushMode) { mode = newMode }
    fun getMode(): EditorBrushMode = mode
    fun canUndo(): Boolean = strokes.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val background = backgroundBitmap ?: return
        val result = when {
            active -> render(cachedPreview ?: background, includeActive = true, includeHistory = cachedPreview == null)
            cacheRevision == revision && cachedPreview != null -> cachedPreview!!
            else -> render(background, includeActive = false)
        }
        canvas.drawBitmap(result, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                rejectedGesture = false
                active = true
                activeStartX = event.x
                activeStartY = event.y
                previousX = event.x
                previousY = event.y
                activePath = Path().apply { moveTo(event.x, event.y) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1 || rejectedGesture) return true
                for (i in 0 until event.historySize) addPoint(event.getHistoricalX(i), event.getHistoricalY(i))
                addPoint(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                rejectedGesture = true
                active = false
                activePath.reset()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (active && !rejectedGesture) {
                    addPoint(event.x, event.y)
                    if (activeStartX == event.x && activeStartY == event.y) {
                        activePath.lineTo(event.x + 0.1f, event.y + 0.1f)
                    }
                    strokes += EditorStroke(mode, Path(activePath), currentWidth, currentColor)
                    revision++
                    renderAndCache()
                    onUndoAvailabilityChanged?.invoke(true)
                }
                active = false
                activePath.reset()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                active = false
                activePath.reset()
                invalidate()
            }
        }
        return true
    }

    private fun addPoint(x: Float, y: Float) {
        activePath.quadTo(previousX, previousY, (previousX + x) / 2f, (previousY + y) / 2f)
        previousX = x
        previousY = y
    }

    fun updateColor(newColor: Int) { currentColor = newColor }
    fun updateBrushSize(newBrushSize: Int) {
        currentWidth = resources.getDimension(R.dimen.full_brush_size) * (newBrushSize.coerceIn(0, 100) / 100f)
    }

    fun updateBackgroundBitmap(bitmap: Bitmap) {
        backgroundBitmap = bitmap
        revision++
        cachedPreview?.recycle()
        cachedPreview = null
        cacheRevision = -1
        renderAndCache()
        invalidate()
    }

    private fun renderAndCache() {
        val background = backgroundBitmap ?: return
        cachedPreview?.recycle()
        cachedPreview = render(background, includeActive = false)
        cacheRevision = revision
    }

    /** Render all brush operations in commit order onto a copy of [source]. */
    private fun render(
        source: Bitmap,
        includeActive: Boolean,
        includeHistory: Boolean = true,
        strokeSnapshot: List<EditorStroke> = strokes
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val outputCanvas = Canvas(output)
        val scaleX = output.width / (backgroundBitmap?.width?.toFloat() ?: output.width.toFloat())
        val scaleY = output.height / (backgroundBitmap?.height?.toFloat() ?: output.height.toFloat())
        if (includeHistory) strokeSnapshot.forEach { stroke -> applyStroke(output, outputCanvas, stroke, scaleX, scaleY) }
        if (includeActive && active && !rejectedGesture) {
            applyStroke(output, outputCanvas, EditorStroke(mode, Path(activePath), currentWidth, currentColor), scaleX, scaleY)
        }
        return output
    }

    private fun applyStroke(target: Bitmap, canvas: Canvas, stroke: EditorStroke, sx: Float, sy: Float) {
        val path = Path(stroke.path).apply { transform(android.graphics.Matrix().apply { setScale(sx, sy) }) }
        val diameter = max(1f, stroke.width * (sx + sy) / 2f)
        when (stroke.mode) {
            EditorBrushMode.DRAW -> {
                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    color = stroke.color
                    strokeWidth = diameter
                }
                canvas.drawPath(path, drawPaint)
            }
            EditorBrushMode.MOSAIC -> {
                val block = max(2, (stroke.mosaicBlockSize * (sx + sy) / 2f).roundToInt())
                val smallWidth = max(1, (target.width.toFloat() / block).roundToInt())
                val smallHeight = max(1, (target.height.toFloat() / block).roundToInt())
                val small = Bitmap.createScaledBitmap(target, smallWidth, smallHeight, false)
                val maskPaint = effectPaint().apply {
                    strokeWidth = diameter
                    shader = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(android.graphics.Matrix().apply {
                            setScale(target.width.toFloat() / smallWidth, target.height.toFloat() / smallHeight)
                        })
                    }
                }
                canvas.drawPath(path, maskPaint)
                small.recycle()
            }
            EditorBrushMode.BLUR -> {
                val scale = max(1, BLUR_DOWNSAMPLE * 2)
                val lowWidth = max(1, target.width / scale)
                val lowHeight = max(1, target.height / scale)
                val blurred = Bitmap.createScaledBitmap(target, lowWidth, lowHeight, true)
                val blurRadius = max(1, (stroke.blurRadius * (sx + sy) / 2f / scale).roundToInt())
                // Three box convolutions closely approximate a Gaussian at a fraction of full resolution.
                repeat(3) { boxBlur(blurred, blurRadius) }
                // A wider blurred stroke softens the boundary while keeping the core local.
                val radius = max(1f, diameter * 0.12f)
                val maskPaint = effectPaint().apply {
                    strokeWidth = diameter + radius * 2f
                    shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(android.graphics.Matrix().apply {
                            setScale(target.width.toFloat() / lowWidth, target.height.toFloat() / lowHeight)
                        })
                    }
                }
                canvas.drawPath(path, maskPaint)
                blurred.recycle()
            }
        }
    }

    private fun effectPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // The shader samples a snapshot of the already-composited image.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        if (radius <= 0) return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val temp = IntArray(pixels.size)
        blurPass(pixels, temp, w, h, radius, horizontal = true)
        blurPass(temp, pixels, w, h, radius, horizontal = false)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val major = if (horizontal) h else w
        val minor = if (horizontal) w else h
        for (line in 0 until major) {
            var a = 0; var r = 0; var g = 0; var b = 0
            val initial = min(minor - 1, radius)
            for (i in 0..initial) {
                val c = src[index(i, line, w, horizontal)]
                a += Color.alpha(c); r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            }
            for (pos in 0 until minor) {
                val left = max(0, pos - radius)
                val right = min(minor - 1, pos + radius)
                val count = right - left + 1
                dst[index(pos, line, w, horizontal)] = Color.argb(a / count, r / count, g / count, b / count)
                val removeAt = pos - radius
                val addAt = pos + radius + 1
                if (removeAt >= 0) {
                    val c = src[index(removeAt, line, w, horizontal)]
                    a -= Color.alpha(c); r -= Color.red(c); g -= Color.green(c); b -= Color.blue(c)
                }
                if (addAt < minor) {
                    val c = src[index(addAt, line, w, horizontal)]
                    a += Color.alpha(c); r += Color.red(c); g += Color.green(c); b += Color.blue(c)
                }
            }
        }
    }

    private fun index(pos: Int, line: Int, width: Int, horizontal: Boolean) = if (horizontal) line * width + pos else pos * width + line

    /** Export at the input image's dimensions; paths scale from the fitted preview coordinate space. */
    internal fun snapshotForExport(): List<EditorStroke> = strokes.map { it.copy(path = Path(it.path)) }

    fun getBitmap(): Bitmap = getBitmap(backgroundBitmap, snapshotForExport())

    fun getBitmap(source: Bitmap): Bitmap = getBitmap(source, snapshotForExport())

    internal fun getBitmap(source: Bitmap?, strokeSnapshot: List<EditorStroke>): Bitmap {
        requireNotNull(source) { "Editor background is not ready" }
        return render(source, includeActive = false, strokeSnapshot = strokeSnapshot)
    }

    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        revision++
        renderAndCache()
        invalidate()
        onUndoAvailabilityChanged?.invoke(strokes.isNotEmpty())
    }

    override fun onDetachedFromWindow() {
        cachedPreview?.recycle()
        cachedPreview = null
        cacheRevision = -1
        super.onDetachedFromWindow()
    }
}
