package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import org.fossify.gallery.R

/** Shared control skin. No listeners or preference writes; each view owns its drawables. */
object OxygenClassicControls {
    fun applySwitches(root: View, accent: Int, text: Int, surface: Int) {
        val key = listOf(accent, text, surface, root.resources.displayMetrics.density.toBits())
        val density = root.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val colors = OxygenThemeColors.resolve(false, "system", false, accent, surface, text)
        val labelColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.disabledText, colors.primaryText)
        )
        fun skin(kind: String) = ClassicDrawable(kind, density, colors)
        fun visit(view: View) {
            val changed = view.getTag(R.id.classic_control_skin) != key
            if (view is CompoundButton) {
                view.setTextColor(labelColors)
                view.minHeight = maxOf(view.minHeight, dp(48))
            }
            when (view) {
                is SwitchCompat -> {
                    // Commons may have re-tinted an unchanged drawable on resume.
                    view.thumbTintList = null
                    view.trackTintList = null
                    if (changed) {
                        view.thumbDrawable = skin("thumb")
                        view.trackDrawable = skin("track")
                    }
                    view.switchMinWidth = dp(40)
                    view.showText = false
                    view.splitTrack = false
                    view.switchPadding = dp(16)
                    view.thumbTextPadding = 0
                }
                is RadioButton, is CheckBox -> {
                    val button = view as CompoundButton
                    button.buttonTintList = null
                    if (button is androidx.core.widget.TintableCompoundButton) button.supportButtonTintList = null
                    if (changed) button.buttonDrawable = skin(if (view is RadioButton) "radio" else "check")
                }
                is SeekBar -> {
                    view.thumbTintList = null
                    view.progressTintList = null
                    view.progressBackgroundTintList = null
                    view.secondaryProgressTintList = null
                    if (changed) {
                        view.thumb = skin("seekThumb")
                        view.progressDrawable = LayerDrawable(arrayOf(
                            skin("seekBackground"),
                            ClipDrawable(skin("seekProgress"), Gravity.START, ClipDrawable.HORIZONTAL)
                        )).apply {
                            setId(0, android.R.id.background)
                            setId(1, android.R.id.progress)
                        }
                        view.splitTrack = false
                    }
                    view.minimumHeight = dp(48)
                }
            }
            view.setTag(R.id.classic_control_skin, key)
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }

    /** Paint geometry is centered in the assigned bounds, never stretched vertically by SwitchCompat.
     * A 40x24 layout box contains a 36x10 track and 20dp thumb. Native SwitchCompat
     * still owns measurement, RTL placement, checked state and thumb animation.
     */
    private class ClassicDrawable(
        private val kind: String, private val density: Float, private val colors: OxygenThemeTokens
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var opacity = 255
        override fun getIntrinsicWidth() = ((when (kind) {
            "track" -> 40
            "thumb" -> 20
            else -> 24
        }) * density + 0.5f).toInt()
        override fun getIntrinsicHeight() = (24 * density + 0.5f).toInt()
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return true }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun getAlpha(): Int = opacity
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun draw(canvas: Canvas) {
            val enabled = state.contains(android.R.attr.state_enabled)
            val checked = state.contains(android.R.attr.state_checked)
            val pressed = state.contains(android.R.attr.state_pressed)
            val bg = colors.pageBackground
            val fg = colors.primaryText
            val accent = colors.interactiveAccent
            // Light-mode off track: #FAFAFA -> #212121 at 16% is #D7D7D7.
            // Disabled is a separate semantic state, not the same as unchecked.
            val off = OxygenThemeColors.blend(bg, fg, if (colors.dark) 0.38 else 0.16)
            val active = if (enabled) accent else OxygenThemeColors.blend(bg, accent, 0.30)
            val inactive = if (enabled) off else OxygenThemeColors.blend(bg, fg, if (colors.dark) 0.19 else 0.09)
            val outline = OxygenThemeColors.blend(bg, fg,
                if (!enabled) 0.22 else if (colors.dark) 0.58 else 0.46)
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            fun color(value: Int) {
                paint.color = value
                // Keep ARGB alpha: resetting Paint.alpha to 255 made the
                // intended translucent thumb shadow render as opaque black.
                paint.alpha = (Color.alpha(value) * opacity + 127) / 255
                paint.style = Paint.Style.FILL
            }
            fun circle(radius: Float, value: Int) { color(value); canvas.drawCircle(cx, cy, radius * density, paint) }
            when (kind) {
                "track" -> {
                    // The original OxygenOS 4.5.15 reference (205249 and
                    // 205801) has a fine outlined rail, not an opaque capsule.
                    // Keep its center transparent so the actual row surface shows.
                    color(if (checked) OxygenThemeColors.blend(bg, accent,
                        if (enabled) 0.52 else 0.17) else inactive)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f * density
                    // Intrinsic sizes alone do not prevent SwitchCompat from
                    // assigning stretched bounds; cap visible rail to 36x10dp.
                    val halfRail = minOf(18f * density,
                        (bounds.width() / 2f - 2f * density).coerceAtLeast(0f))
                    val inset = paint.strokeWidth / 2f
                    canvas.drawRoundRect(cx - halfRail + inset, cy - 5 * density + inset,
                        cx + halfRail - inset, cy + 5 * density - inset,
                        5 * density - inset, 5 * density - inset, paint)
                }
                "thumb" -> {
                    // Soft under-shadow within the 24dp optical box; never
                    // replace its ARGB alpha with full opacity.
                    color(0x18000000)
                    canvas.drawCircle(cx, cy + 1.25f * density, 10 * density, paint)
                    val face = if (checked) active else if (enabled) {
                        if (colors.dark) 0xFFCCCCCC.toInt() else 0xFFEEEEEE.toInt()
                    } else OxygenThemeColors.blend(bg, fg, if (colors.dark) 0.29 else 0.20)
                    circle(9.7f, if (pressed && enabled) OxygenThemeColors.blend(face, bg, 0.07) else face)
                    color(OxygenThemeColors.blend(face, fg, if (colors.dark) 0.10 else 0.08))
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.35f * density
                    canvas.drawCircle(cx, cy, 9.45f * density, paint)
                }
                "radio" -> {
                    color(if (checked) active else outline)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
                    canvas.drawCircle(cx, cy, 9 * density, paint)
                    if (checked) circle(5f, active)
                }
                "check" -> {
                    // One consistent 18dp box in every state, not two
                    // differently sized nested squares on selection.
                    val fill = if (enabled) OxygenThemeColors.toolbarBackground(colors.rawAccent)
                        else OxygenThemeColors.blend(bg, fg, 0.49)
                    color(if (checked) fill else outline)
                    paint.style = if (checked) Paint.Style.FILL else Paint.Style.STROKE
                    paint.strokeWidth = 2 * density
                    canvas.drawRoundRect(cx - 9 * density, cy - 9 * density,
                        cx + 9 * density, cy + 9 * density, density, density, paint)
                    if (checked) {
                        color(Color.WHITE); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
                        paint.strokeCap = Paint.Cap.ROUND
                        paint.strokeJoin = Paint.Join.ROUND
                        val path = Path().apply {
                            moveTo(cx - 5 * density, cy); lineTo(cx - density, cy + 4 * density)
                            lineTo(cx + 6 * density, cy - 4 * density)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                "seekThumb" -> circle(if (pressed) 7f else 6f, active)
                else -> {
                    color(if (kind == "seekProgress") active else inactive)
                    canvas.drawRect(bounds.left.toFloat(), cy - density,
                        bounds.right.toFloat(), cy + density, paint)
                }
            }
        }
    }
}
