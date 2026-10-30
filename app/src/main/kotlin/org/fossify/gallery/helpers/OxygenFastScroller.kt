package org.fossify.gallery.helpers

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
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
                if (vertical) dp(4) else dp(32),
                if (vertical) dp(32) else dp(4)
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

        // Keep the narrow 4dp blue glyph, but expand the receiving thumb/track
        // from the library's 18dp default to a usable 40dp hit area.
        ensureTouchableHandle(scroller)

        // Only a viewport inset: retain the feature branch's bottom-navigation
        // clearance without changing scroll percentages, endpoints or gestures.
        scroller.trackMarginEnd = if (vertical) bottomBar else 0
        scroller.requestLayout()
    }

    /**
     * Quiph's scroller installs its drag listener on thumbIV and trackView,
     * not on the drawable. A 4dp drawable alone cannot increase the hit box.
     * This runs for Gallery's grid and for Commons' file picker.
     */
    fun ensureTouchableHandle(scroller: RecyclerViewFastScroller) {
        val density = scroller.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val vertical = scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL
        val hitWidth = dp(if (vertical) 40 else 44)
        val hitHeight = dp(if (vertical) 44 else 40)
        val thumb = scroller.findViewById<ImageView>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            ?: return
        // CENTER prevents Android's default FIT_CENTER scaling the 4dp glyph
        // up to the 40dp touch area. The entire View still receives drag events.
        thumb.scaleType = ImageView.ScaleType.CENTER
        if (scroller.handleWidth != hitWidth) scroller.handleWidth = hitWidth
        if (scroller.handleHeight != hitHeight) scroller.handleHeight = hitHeight
    }
}
