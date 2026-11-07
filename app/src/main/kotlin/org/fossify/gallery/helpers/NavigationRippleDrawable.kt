package org.fossify.gallery.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock

/** Clock-based feedback: only values cross windows, never a View, Context or Drawable callback. */
internal class NavigationRippleDrawable(var color: Int, private val radius: Float, var durationScale: Float = 1f) : Drawable() {
    data class Pulse(val startedAt: Long, val x: Float, val y: Float, var releasedAt: Long? = null)
    var pulse: Pulse? = null
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hotspotX = 0.5f
    private var hotspotY = 0.5f
    private var pressed = false
    private var opacity = 255
    private val easing = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)

    override fun setHotspot(x: Float, y: Float) {
        hotspotX = if (bounds.width() > 0) (x / bounds.width()).coerceIn(0f, 1f) else 0.5f
        hotspotY = if (bounds.height() > 0) (y / bounds.height()).coerceIn(0f, 1f) else 0.5f
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val next = android.R.attr.state_pressed in state
        if (next == pressed) return false
        pressed = next
        val now = SystemClock.uptimeMillis()
        if (next) pulse = Pulse(now, hotspotX, hotspotY)
        else pulse?.let { if (it.releasedAt == null) it.releasedAt = now }
        invalidateSelf()
        return true
    }

    fun continuePulse(value: Pulse?) {
        pulse = value
        invalidateSelf()
    }

    internal fun progress(now: Long): Pair<Float, Float>? {
        val value = pulse ?: return null
        if (durationScale <= 0f) return if (value.releasedAt == null) 1f to 1f else null
        val expansion = ((now - value.startedAt).toFloat() / (EXPAND_MS * durationScale)).coerceIn(0f, 1f)
        val fade = value.releasedAt?.let { ((now - it).toFloat() / (RELEASE_MS * durationScale)).coerceIn(0f, 1f) } ?: 0f
        if (fade >= 1f) return null
        return easing.getInterpolation(expansion) to (1f - fade)
    }

    override fun draw(canvas: Canvas) {
        val value = pulse ?: return
        val (expansion, visibility) = progress(SystemClock.uptimeMillis()) ?: return
        paint.color = color
        paint.alpha = (Color.alpha(color) * visibility * opacity / 255f).toInt()
        val saved = canvas.save()
        canvas.clipRect(bounds)
        canvas.drawCircle(bounds.left + value.x * bounds.width(),
            bounds.top + value.y * bounds.height(), radius * expansion, paint)
        canvas.restoreToCount(saved)
        if (expansion < 1f || value.releasedAt != null) invalidateSelf()
    }

    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        const val EXPAND_MS = 225L
        const val RELEASE_MS = 180L
    }
}
