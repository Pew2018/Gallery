package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
/** Small presentation helpers shared by XML settings and the programmatic theme page. */
object ClassicSettingsPresentation {
    fun rowRippleColor(colors: OxygenThemeTokens): Int =
        OxygenThemeColors.pressedStateLayer(colors.primaryText)

    fun rowRipple(view: View, colors: OxygenThemeTokens) {
        val tint = ColorStateList.valueOf(rowRippleColor(colors))
        val current = view.background
        if (current is RippleDrawable) current.setColor(tint)
        else view.background = RippleDrawable(tint, ColorDrawable(Color.TRANSPARENT), ColorDrawable(Color.WHITE))
    }
}
