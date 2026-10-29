package org.fossify.gallery.helpers

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller

/** Style the library's existing viewport and popup without replacing its scroll logic. */
object OxygenFastScroller {
    fun apply(scroller: RecyclerViewFastScroller, tokens: OxygenThemeTokens, bottomBar: Int = 0) {
        val density = scroller.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        scroller.updateColors(tokens.interactiveAccent)
        scroller.popupTextView.apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.toolbarForeground)
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                setColor(tokens.toolbarBackground)
                cornerRadius = dp(3).toFloat()
            }
        }
        scroller.handleWidth = dp(12)
        scroller.handleHeight = dp(36)
        scroller.handleDrawable = GradientDrawable().apply {
            setColor(tokens.interactiveAccent)
            cornerRadius = dp(2).toFloat()
            setSize(dp(4), dp(36))
        }
        // The bottom bar overlays the RecyclerView. Shorten only the vertical
        // track so its popup and final touch position stay above that overlay.
        scroller.trackMarginEnd = if (scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL) bottomBar else 0
        scroller.requestLayout()
    }
}
