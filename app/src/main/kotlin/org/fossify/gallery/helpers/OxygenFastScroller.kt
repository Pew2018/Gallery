package org.fossify.gallery.helpers

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller

/**
 * Classic fast-scroller visuals only.
 *
 * Keep RecyclerViewFastScroller's own touch listeners, visibility decisions,
 * popup lifecycle and adapter position calculations unchanged.
 */
object OxygenFastScroller {
    // Keep the existing call signature so no unrelated Activity code has to change.
    @Suppress("UNUSED_PARAMETER")
    fun apply(
        scroller: RecyclerViewFastScroller,
        recycler: RecyclerView,
        tokens: OxygenThemeTokens,
        bottomBar: Int = 0
    ) {
        val density = scroller.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()

        val vertical = scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL

        // Existing Commons color handling runs first; only replace the visible
        // drawable, never the library's handle View size or touch target.
        scroller.updateColors(tokens.interactiveAccent)
        scroller.handleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(tokens.interactiveAccent)
            cornerRadius = dp(2).toFloat()
            // A narrow image inside the library's original 18dp handle bounds.
            setSize(
                if (vertical) dp(4) else dp(18),
                if (vertical) dp(18) else dp(4)
            )
        }

        scroller.popupDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.rgb(33, 33, 33))
            cornerRadius = dp(3).toFloat()
        }
        scroller.popupTextView.apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            minWidth = 0
            minHeight = 0
            maxWidth = (resources.displayMetrics.widthPixels - dp(32))
                .coerceAtLeast(dp(48))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }

        // Only a viewport inset: retain the feature branch's bottom-navigation
        // clearance without changing scroll percentages, endpoints or gestures.
        scroller.trackMarginEnd = if (vertical) bottomBar else 0
        scroller.requestLayout()
    }
}
