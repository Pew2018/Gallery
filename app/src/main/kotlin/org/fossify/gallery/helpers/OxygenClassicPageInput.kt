package org.fossify.gallery.helpers

import android.os.Build
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout

/** Shared Material 2 outlined field tokens for OxygenOS Classic forms. */
object OxygenClassicPageInput {
    fun readableTextColor(color: Int, tokens: OxygenThemeTokens): Int =
        if (OxygenThemeColors.contrastRatio(color, tokens.pageBackground) >= 4.5) color
        else OxygenThemeColors.readableAccent(color, tokens.pageBackground)

    fun style(field: EditText, tokens: OxygenThemeTokens) {
        val primary = readableTextColor(tokens.primaryText, tokens)
        val secondary = readableTextColor(tokens.secondaryText, tokens)
        field.setTextColor(primary)
        field.setHintTextColor(secondary)
        if (Build.VERSION.SDK_INT >= 29) {
            field.textCursorDrawable?.mutate()?.setTint(tokens.interactiveAccent)
        }
    }

    fun style(layout: TextInputLayout, tokens: OxygenThemeTokens, surface: Int = tokens.pageBackground) {
        val dp = layout.resources.displayMetrics.density
        val text = readableTextColor(tokens.primaryText, tokens.copy(pageBackground = surface))
        val secondary = readableTextColor(tokens.secondaryText, tokens.copy(pageBackground = surface))
        val neutral = OxygenThemeColors.readableAccent(secondary, surface)
        val disabled = OxygenThemeColors.blend(surface, text, 0.24)
        val accent = OxygenThemeColors.readableAccent(tokens.interactiveAccent, surface)
        val focused = intArrayOf(android.R.attr.state_focused, android.R.attr.state_enabled)
        val error = intArrayOf(android.R.attr.state_activated)
        val normal = intArrayOf()
        layout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        layout.setBoxCornerRadii(4f * dp, 4f * dp, 4f * dp, 4f * dp)
        layout.boxStrokeWidth = dp.toInt().coerceAtLeast(1)
        layout.boxStrokeWidthFocused = (2f * dp).toInt().coerceAtLeast(2)
        layout.setBoxStrokeColorStateList(android.content.res.ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), focused, error, normal),
            intArrayOf(disabled, accent, OxygenClassicPalette.readableAccent(android.graphics.Color.RED, surface), neutral)
        ))
        layout.setBoxStrokeErrorColor(android.content.res.ColorStateList.valueOf(
            OxygenClassicPalette.readableAccent(android.graphics.Color.RED, surface)
        ))
        layout.defaultHintTextColor = android.content.res.ColorStateList.valueOf(secondary)
        layout.hintTextColor = android.content.res.ColorStateList.valueOf(accent)
        layout.editText?.let { field ->
            field.minimumHeight = (56f * dp).toInt()
            style(field, tokens.copy(pageBackground = surface, interactiveAccent = accent))
            field.background = null
        }
    }
}
