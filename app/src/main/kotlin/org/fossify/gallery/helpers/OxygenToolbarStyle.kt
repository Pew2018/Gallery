package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MySearchMenu

/**
 * Undo the over-rounded Material-style search pill without replacing the
 * existing searchable toolbar or breaking its menu and permission actions.
 * Applied after MySearchMenu.updateColors(), which otherwise tints the pill.
 */
object OxygenToolbarStyle {
    fun apply(activity: Activity, searchMenu: MySearchMenu) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val foreground = activity.getProperTextColor()
        val primary = activity.getProperPrimaryColor()
        val background = activity.getProperBackgroundColor()
        val outline = Color.argb(48, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        val subtleTint = Color.argb(10, Color.red(primary), Color.green(primary), Color.blue(primary))
        searchMenu.findViewById<View>(org.fossify.commons.R.id.search_bar_container)?.apply {
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setBackgroundColor(background)
        }
        searchMenu.findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.apply {
            this.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(background)
                setStroke(dp(1), outline)
            }
            elevation = 0f
        }
    }
}
