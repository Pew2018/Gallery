package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText

/** Classic underline input colors resolved against the page's actual theme surface. */
object OxygenClassicPageInput {
    fun readableTextColor(color: Int, tokens: OxygenThemeTokens): Int =
        if (OxygenThemeColors.contrastRatio(color, tokens.pageBackground) >= 4.5) color
        else OxygenThemeColors.readableAccent(color, tokens.pageBackground)

    fun style(field: EditText, tokens: OxygenThemeTokens) {
        val primary = readableTextColor(tokens.primaryText, tokens)
        val secondary = readableTextColor(tokens.secondaryText, tokens)
        val neutral = OxygenThemeColors.readableAccent(secondary, tokens.pageBackground)
        field.setTextColor(primary)
        field.setHintTextColor(secondary)
        field.backgroundTintList = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(tokens.disabledText, tokens.interactiveAccent, neutral)
        )
        if (Build.VERSION.SDK_INT >= 29) {
            field.textCursorDrawable?.mutate()?.setTint(tokens.interactiveAccent)
        }
    }
}
