package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import org.fossify.gallery.R
import org.fossify.gallery.views.ClassicSettingsSwitch

/** Geometry shared by the SwitchCompat drawable and density-rounding tests. */
internal data class OxygenClassicSwitchGeometry(val density: Float) {
    val width = 48f * density
    val height = 28f * density
    val trackWidth = 36f * density
    val trackHeight = 16f * density
    val strokeWidth = 2f * density
    val thumbDiameter = 24f * density
    val trackLeft = (width - trackWidth + strokeWidth) / 2f
    val trackTop = (height - trackHeight + strokeWidth) / 2f
    val trackPathWidth = trackWidth - strokeWidth
    val trackPathHeight = trackHeight - strokeWidth
    val trackRadius = trackPathHeight / 2f
    val thumbStart = trackLeft + trackRadius - thumbDiameter / 2f
    val thumbEnd = trackLeft + trackPathWidth - trackRadius - thumbDiameter / 2f
}

/** Shared control skin. No listeners or preference writes; each view owns its drawables. */
object OxygenClassicControls {
    fun applySwitches(root: View, colors: OxygenThemeTokens) {
        val density = root.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val animationsEnabled = runCatching {
            Settings.Global.getFloat(
                root.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) > 0f
        }.getOrDefault(true)
        // Re-skin if mode, selected accent, page tokens, density or animation scale changes.
        val key = listOf(colors, density.toBits(), animationsEnabled)
        val labelColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.disabledText, colors.primaryText)
        )
        fun skin(kind: String) = ClassicDrawable(kind, density, colors, animationsEnabled)
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
                    view.switchMinWidth = dp(48)
                    if (changed) {
                        view.thumbDrawable = skin("thumb")
                        view.trackDrawable = skin("track")
                        (view as? ClassicSettingsSwitch)?.useClassicSwitchMeasurement()
                    }
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

    /** SwitchCompat owns checked state, gesture semantics, RTL placement and thumb travel.
     * A 48x28 track box plus a 28dp thumb box yields a 20dp native travel range;
     * the thumb drawable paints only a centered 24dp circle inside its optical box.
     */
    private class ClassicDrawable(
        private val kind: String, private val density: Float, private val colors: OxygenThemeTokens,
        private val animationsEnabled: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val geometry = OxygenClassicSwitchGeometry(density)
        private val switchPalette = OxygenThemeColors.switchPalette(colors)
        private var opacity = 255
        private var animatedColor: Int? = null
        private var colorAnimator: ValueAnimator? = null
        private var lastChecked: Boolean? = null
        private var lastEnabled: Boolean? = null
        override fun getIntrinsicWidth() = ((when (kind) {
            "track" -> 48
            "thumb" -> 28
            else -> 24
        }) * density + 0.5f).toInt()
        override fun getIntrinsicHeight() = ((if (kind == "track" || kind == "thumb") 28 else 24) * density + 0.5f).toInt()
        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean {
            if (kind == "track" || kind == "thumb") {
                val target = stateColor(state)
                val current = animatedColor
                val checked = state.contains(android.R.attr.state_checked)
                val enabled = state.contains(android.R.attr.state_enabled)
                val animateStateChange = lastChecked != null &&
                    (lastChecked != checked || lastEnabled != enabled)
                lastChecked = checked
                lastEnabled = enabled
                if (!animateStateChange || current == null || !animationsEnabled || current == target) {
                    colorAnimator?.cancel()
                    animatedColor = target
                } else {
                    colorAnimator?.cancel()
                    val animator = ValueAnimator.ofArgb(current, target).apply {
                        duration = 180L
                        addUpdateListener { valueAnimator ->
                            animatedColor = valueAnimator.animatedValue as Int
                            invalidateSelf()
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (colorAnimator === animation) colorAnimator = null
                            }
                        })
                    }
                    colorAnimator = animator
                    animator.start()
                }
            }
            invalidateSelf()
            return true
        }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun getAlpha(): Int = opacity
        override fun jumpToCurrentState() {
            colorAnimator?.cancel()
            colorAnimator = null
            animatedColor = stateColor(state)
            invalidateSelf()
        }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        private fun stateColor(state: IntArray): Int {
            val enabled = state.contains(android.R.attr.state_enabled)
            val checked = state.contains(android.R.attr.state_checked)
            val base = when (kind) {
                "track" -> when {
                    !enabled && checked -> switchPalette.disabledOnTrack
                    !enabled -> switchPalette.disabledOffTrack
                    checked -> switchPalette.onTrack
                    else -> switchPalette.offTrack
                }
                "thumb" -> when {
                    !enabled && checked -> switchPalette.disabledOnThumb
                    !enabled -> switchPalette.disabledOffThumb
                    checked -> switchPalette.onThumb
                    else -> switchPalette.offThumb
                }
                else -> colors.primaryText
            }
            return base
        }
        override fun draw(canvas: Canvas) {
            val enabled = state.contains(android.R.attr.state_enabled)
            val checked = state.contains(android.R.attr.state_checked)
            val pressed = state.contains(android.R.attr.state_pressed)
            val bg = colors.pageBackground
            val fg = colors.primaryText
            val accent = colors.interactiveAccent
            // Other classic controls retain their existing page/text neutral blend.
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
                    color(animatedColor ?: stateColor(state))
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = geometry.strokeWidth
                    // The 34x14dp center path plus a 2dp stroke yields a 36x16dp outline.
                    canvas.drawRoundRect(
                        cx - geometry.trackPathWidth / 2f,
                        cy - geometry.trackPathHeight / 2f,
                        cx + geometry.trackPathWidth / 2f,
                        cy + geometry.trackPathHeight / 2f,
                        geometry.trackRadius, geometry.trackRadius, paint
                    )
                }
                "thumb" -> {
                    // Soft radial shadow stays inside the 28dp optical box; its depth
                    // follows the classic 2dp resting / 3dp pressed elevation cue.
                    val shadowAlpha = when {
                        !enabled -> 18
                        pressed -> 52
                        else -> 32
                    }
                    val shadowRadius = if (pressed) 13.4f else 12.8f
                    paint.shader = RadialGradient(
                        cx, cy + 0.5f * density, shadowRadius * density,
                        intArrayOf(Color.argb(shadowAlpha, 0, 0, 0), Color.TRANSPARENT),
                        floatArrayOf(0.82f, 1f), Shader.TileMode.CLAMP
                    )
                    paint.alpha = opacity
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy + 0.5f * density, shadowRadius * density, paint)
                    paint.shader = null
                    val face = animatedColor ?: stateColor(state)
                    circle(12f, face)
                    // A fine neutral rim protects the 24dp circle on either surface,
                    // including custom accents whose RGB matches the page background.
                    val lowContrast = OxygenThemeColors.contrastRatio(face, bg) < 1.5
                    color(OxygenThemeColors.blend(face, fg,
                        when {
                            !enabled -> 0.06
                            !checked -> if (colors.dark) 0.10 else 0.11
                            lowContrast -> 0.35
                            colors.dark -> 0.10
                            else -> 0.08
                        }))
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.35f * density
                    canvas.drawCircle(cx, cy, 11.65f * density, paint)
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
