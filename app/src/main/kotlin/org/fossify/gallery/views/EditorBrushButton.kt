package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.fossify.gallery.R

/** Compact Oxygen Classic circular brush selector with a semantic tool preview. */
class EditorBrushButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var brushMode = EditorBrushMode.MOSAIC
    private var accent = Color.WHITE

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumWidth = resources.getDimensionPixelSize(R.dimen.bottom_editor_color_picker_size)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.bottom_editor_color_picker_size)
    }

    fun setBrushMode(mode: EditorBrushMode, description: String) {
        brushMode = mode
        contentDescription = description
        invalidate()
    }

    fun setAccentColor(color: Int) { accent = color; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.265f
        val iconBounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val iconMask = android.graphics.Path().apply {
            addCircle(cx, cy, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(iconMask)
        when (brushMode) {
            EditorBrushMode.MOSAIC -> drawMosaic(canvas, iconBounds)
            EditorBrushMode.BLUR -> drawBlur(canvas, cx, cy, radius)
            EditorBrushMode.DRAW -> Unit
        }
        canvas.restore()
        if (isSelected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * 2f
            paint.color = accent
            canvas.drawCircle(cx, cy, minOf(width, height) * 0.42f, paint)
        }
    }

    private fun drawMosaic(canvas: Canvas, bounds: RectF) {
        val colors = intArrayOf(0xFF252525.toInt(), 0xFF747474.toInt(), 0xFFB7B7B7.toInt(), 0xFF4A4A4A.toInt())
        val size = (bounds.width() / 4f)
        for (row in 0..3) for (column in 0..3) {
            paint.style = Paint.Style.FILL
            paint.color = colors[(row * 3 + column * 2) % colors.size]
            canvas.drawRect(bounds.left + column * size, bounds.top + row * size,
                bounds.left + (column + 1) * size, bounds.top + (row + 1) * size, paint)
        }
    }

    private fun drawBlur(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0xFFE1E1E1.toInt(), 0xFF858585.toInt(), 0xFF303030.toInt()),
            floatArrayOf(0f, 0.64f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }
}
