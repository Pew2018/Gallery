package org.fossify.gallery.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.gallery.R
import org.fossify.gallery.helpers.ClassicSettingsPresentation
import org.fossify.gallery.helpers.OxygenThemeTokens
import kotlin.math.roundToInt

/** Settings home geometry follows TiebaLite's PrefsListItem + small AvatarIcon. */
class TiebaSettingsCategoryRow(
    context: Context,
    icon: Int,
    title: CharSequence,
    summary: CharSequence,
    colors: OxygenThemeTokens,
    onClick: () -> Unit
) : LinearLayout(context) {
    init {
        fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        minimumHeight = dp(72)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setOnClickListener { onClick() }

        // 56dp leading slot, 36dp transparent avatar, 24dp glyph inset by 6dp.
        val leading = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(colors.interactiveAccent)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                contentDescription = null
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(36), dp(36)))
        }
        addView(leading, LayoutParams(dp(56), dp(56)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                includeFontPadding = false
                setTextColor(colors.primaryText)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = summary
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.oxygen_classic_settings_summary))
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                includeFontPadding = false
                setTextColor(colors.secondaryText)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        bindColors(colors)
    }

    /** Recolor in place on resume; preserve focus and the system ripple lifecycle. */
    fun bindColors(colors: OxygenThemeTokens) {
        val icon = (getChildAt(0) as LinearLayout).getChildAt(0) as ImageView
        icon.imageTintList = ColorStateList.valueOf(colors.interactiveAccent)
        val labels = getChildAt(1) as LinearLayout
        (labels.getChildAt(0) as TextView).setTextColor(colors.primaryText)
        (labels.getChildAt(1) as TextView).setTextColor(colors.secondaryText)
        ClassicSettingsPresentation.rowRipple(this, colors)
    }
}
