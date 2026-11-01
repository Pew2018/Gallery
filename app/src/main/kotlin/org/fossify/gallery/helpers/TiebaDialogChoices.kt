package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.RadioButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.R

/** TiebaLite ListSinglePicker presentation; RadioGroup still owns selection and callbacks. */
internal object TiebaDialogChoices {
    fun selectedBackground(accent: Int, surface: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_checked), GradientDrawable().apply {
            setColor(ColorUtils.blendARGB(surface, accent, 0.1f))
        })
        addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }

    fun remainingInsets(view: android.view.View, target: Int): Pair<Int, Int> {
        var start = 0
        var end = 0
        var ancestor = view.parent as? android.view.View
        while (ancestor != null) {
            start += ancestor.paddingStart
            end += ancestor.paddingEnd
            ancestor = ancestor.parent as? android.view.View
        }
        return (target - start).coerceAtLeast(0) to (target - end).coerceAtLeast(0)
    }

    fun apply(button: RadioButton, accent: Int, text: Int, surface: Int) {
        val density = button.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        button.buttonDrawable = null
        val check = AppCompatResources.getDrawable(button.context, R.drawable.tieba_choice_check)!!.mutate()
        check.setTint(accent)
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, check, null)
        button.compoundDrawablePadding = dp(16)
        button.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val insets = remainingInsets(button, dp(24))
        button.minHeight = dp(48)
        button.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        button.setSingleLine(false)
        button.setPaddingRelative(insets.first, dp(8), insets.second, dp(8))
        button.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 24)),
            selectedBackground(accent, surface), android.graphics.drawable.ColorDrawable(Color.WHITE))
        button.setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ColorUtils.blendARGB(surface, text, 0.38f), accent, text)
        ))
    }
}
