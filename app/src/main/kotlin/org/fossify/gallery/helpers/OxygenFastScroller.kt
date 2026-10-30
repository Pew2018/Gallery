package org.fossify.gallery.helpers

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import kotlin.math.roundToInt
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

        // Keep the narrow glyph centered inside a modest hit area. The previous
        // 40dp thumb overlapped the rightmost album cards and shifted track math.
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
        val hitWidth = dp(if (vertical) 28 else 36)
        val hitHeight = dp(if (vertical) 36 else 28)
        val thumb = scroller.findViewById<ImageView>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            ?: return
        // CENTER prevents Android's default FIT_CENTER scaling the 4dp glyph
        // up to the 40dp touch area. The entire View still receives drag events.
        thumb.scaleType = ImageView.ScaleType.CENTER
        if (scroller.handleWidth != hitWidth) scroller.handleWidth = hitWidth
        if (scroller.handleHeight != hitHeight) scroller.handleHeight = hitHeight
    }

    /** The library mixes MotionEvent.rawY (screen) with getLocationInWindow()
     * (window), producing a status-bar-sized jump. Picker rails use one
     * screen-coordinate system and never forward the drag to folder cards. */
    internal fun positionForPickerDrag(relative: Float, track: Int, thumb: Int, count: Int): Int {
        if (count <= 1) return 0
        val length = (track - thumb).coerceAtLeast(1).toFloat()
        return ((relative / length).coerceIn(0f, 1f) * (count - 1))
            .roundToInt().coerceIn(0, count - 1)
    }

    /** Apply only to the two picker dialogs, not the Gallery's main scrollers. */
    @SuppressLint("ClickableViewAccessibility")
    fun installPickerTouchHandling(scroller: RecyclerViewFastScroller, recycler: RecyclerView) {
        scroller.post {
            if (!scroller.isAttachedToWindow) return@post
            val thumb = scroller.findViewById<ImageView>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
                ?: return@post
            val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
                ?: return@post
            // Override the library listeners only on picker instances: the
            // upstream listener uses raw screen coordinates against window origin.
            val listener = View.OnTouchListener { touched, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val vertical = scroller.fastScrollDirection ==
                            RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                        val location = IntArray(2)
                        track.getLocationOnScreen(location)
                        val thumbLength = if (vertical) thumb.height else thumb.width
                        val trackLength = if (vertical) track.height else track.width
                        val relative = (if (vertical) event.rawY - location[1] else event.rawX - location[0]) -
                            thumbLength / 2f
                        val count = recycler.adapter?.itemCount ?: 0
                        if (trackLength > thumbLength && count > 1) {
                            touched.parent?.requestDisallowInterceptTouchEvent(true)
                            scroller.parent?.requestDisallowInterceptTouchEvent(true)
                            recycler.stopScroll()
                            val index = positionForPickerDrag(relative, trackLength, thumbLength, count)
                            recycler.scrollToPosition(index)
                            val popupLabel =
                                (recycler.adapter as? RecyclerViewFastScroller.OnPopupTextUpdate)
                                    ?.onChange(index)
                            scroller.popupTextView.apply {
                                text = popupLabel ?: (index + 1).toString()
                                visibility = View.VISIBLE
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        scroller.popupTextView.visibility = View.INVISIBLE
                        true
                    }
                    else -> false
                }
            }
            thumb.setOnTouchListener(listener)
            track.setOnTouchListener(listener)
        }
    }
}
