package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.CompoundButton
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils

/**
 * 2017-era thin-track SwitchCompat. The thumb contains a separate shadow layer
 * which must NOT receive the thumb tint (it would make the shadow green).
 *
 * Gallery settings use whole-row click listeners; changing this appearance
 * must never add a second checked-change handler or a second toggle.
 */
object OxygenClassicControls {
    fun applySwitches(root: View, accent: Int, text: Int, surface: Int) {
        val context = root.context
        val d = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * d + 0.5f).toInt()
        val light = ColorUtils.calculateLuminance(surface) >= 0.5
        val offThumb = if (light) Color.rgb(238, 238, 238) else Color.rgb(200, 200, 200)
        val offTrack = if (light) Color.rgb(215, 215, 215) else Color.rgb(105, 105, 105)
        val onTrack = ColorUtils.blendARGB(surface, accent, if (light) 0.38f else 0.52f)
        val disabledThumb = ColorUtils.blendARGB(surface, text, if (light) 0.13f else 0.30f)
        val disabledTrack = ColorUtils.blendARGB(surface, text, 0.12f)

        fun circle(diameter: Int, color: Int, outline: Boolean = false) =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setSize(dp(diameter), dp(diameter))
                if (outline) setStroke(dp(1).coerceAtLeast(1),
                    ColorUtils.blendARGB(color, if (light) Color.BLACK else Color.WHITE, 0.10f))
            }

        fun thumb(color: Int): LayerDrawable {
            val shadow = circle(24, if (light) Color.argb(25, 0, 0, 0)
                else Color.argb(45, 0, 0, 0))
            val face = circle(20, color, true)
            return LayerDrawable(arrayOf(shadow, face)).apply {
                setLayerInset(1, dp(2), dp(2), dp(2), dp(2))
            }
        }

        fun track(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(5).toFloat()
            setSize(dp(36), dp(10))
        }

        fun newThumbState() = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), thumb(disabledThumb))
            addState(intArrayOf(android.R.attr.state_checked), thumb(accent))
            addState(intArrayOf(), thumb(offThumb))
        }
        fun newTrackState() = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), track(disabledTrack))
            addState(intArrayOf(android.R.attr.state_checked), track(onTrack))
            addState(intArrayOf(), track(offTrack))
        }

        fun apply(view: View) {
            if (view is SwitchCompat) {
                // Commons updateTextColors() may re-tint an otherwise unchanged
                // drawable. Re-apply every Settings resume, not every scroll.
                view.thumbTintList = null
                view.trackTintList = null
                // Each switch owns its own stateful drawables: no cross-row state leak.
                view.thumbDrawable = newThumbState().mutate()
                view.trackDrawable = newTrackState().mutate()
                view.switchMinWidth = dp(42)
                view.showText = false
                view.splitTrack = false
                view.setSwitchPadding(0)
                view.setThumbTextPadding(0)
            }
            if (view is CheckBox || view is RadioButton) {
                val checkedColor = OxygenClassicPalette.readableAccent(accent, surface)
                val unchecked = ColorUtils.blendARGB(surface, text, if (light) 0.54f else 0.72f)
                val disabled = ColorUtils.blendARGB(surface, text, 0.25f)
                (view as CompoundButton).buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_enabled),
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf()
                    ),
                    intArrayOf(disabled, checkedColor, unchecked)
                )
                view.minHeight = maxOf(view.minHeight, dp(48))
            }
            if (view is SeekBar) {
                val readable = OxygenClassicPalette.readableAccent(accent, surface)
                view.progressTintList = ColorStateList.valueOf(readable)
                view.thumbTintList = ColorStateList.valueOf(readable)
                view.progressBackgroundTintList = ColorStateList.valueOf(
                    ColorUtils.blendARGB(surface, text, 0.22f)
                )
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) apply(view.getChildAt(i))
            }
        }
        apply(root)
    }
}
