package org.fossify.gallery.helpers

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable

/** 20dp visual glyph; the CheckBox owning it retains a separate 48dp hit area. */
internal class PhotoSourceCheckDrawable(resources: Resources, private val accent: Int, private val outline: Int) : Drawable() {
    private val size = (20 * resources.displayMetrics.density + 0.5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
    override fun draw(canvas: Canvas) {
        val checked = state.contains(android.R.attr.state_checked)
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 20f, bounds.height() / 20f)
        paint.strokeWidth = 1.5f
        paint.style = if (checked) Paint.Style.FILL else Paint.Style.STROKE
        paint.color = if (checked) accent else outline
        canvas.drawRoundRect(1.5f, 1.5f, 18.5f, 18.5f, 1f, 1f, paint)
        if (checked) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            val tick = Path().apply { moveTo(5f, 10f); lineTo(8.5f, 13.5f); lineTo(15f, 6.5f) }
            canvas.drawPath(tick, paint)
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** CompoundButton anchors its button drawable at start; padding does not center it.
 * Center the 20dp glyph inside a 48dp drawable, matching the independent hit target. */
internal class PhotoSourceCheckTargetDrawable(resources: Resources, accent: Int, outline: Int) :
    android.graphics.drawable.InsetDrawable(PhotoSourceCheckDrawable(resources, accent, outline),
        (14 * resources.displayMetrics.density + .5f).toInt())
