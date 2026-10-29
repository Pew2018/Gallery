package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

/** Finish after Commons' type-specific updateTextColors, including plain SwitchCompat text. */
object ClassicSettingsText {
    fun apply(root: View, tokens: OxygenThemeTokens) {
        fun visit(view: View) {
            if (view is TextView) {
                val normal = when (view.tag) {
                    "classic_section", "classic_action" -> tokens.interactiveAccent
                    "classic_secondary" -> tokens.secondaryText
                    else -> tokens.primaryText
                }
                view.alpha = 1f // semantic tokens are already composited against the surface
                view.setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(tokens.disabledText, normal)
                ))
                if (view is EditText) {
                    view.setHintTextColor(tokens.secondaryText)
                    view.backgroundTintList = ColorStateList.valueOf(tokens.interactiveAccent)
                }
            }
            // View.generateViewId() IDs (e.g. dynamic RadioGroups) are not
            // names in the compiled resource table. Do not crash the skin pass
            // when such controls appear in dialogs or dynamically built pages.
            val isDivider = view.id != View.NO_ID && try {
                view.resources.getResourceEntryName(view.id).endsWith("_divider")
            } catch (_: android.content.res.Resources.NotFoundException) {
                false
            }
            if (isDivider) {
                view.alpha = 1f
                view.setBackgroundColor(OxygenThemeColors.blend(tokens.pageBackground, tokens.primaryText, 0.10))
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
