package org.fossify.gallery.helpers

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.gallery.R

/** Keeps the library's drag state machine and popup callbacks while applying the classic Gallery style. */
object OxygenFastScroller {
    fun apply(
        scroller: RecyclerViewFastScroller,
        recycler: RecyclerView,
        tokens: OxygenThemeTokens,
        bottomBar: Int = 0
    ) {
        val density = scroller.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()

        scroller.updateColors(tokens.interactiveAccent)
        scroller.popupTextView.apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(tokens.toolbarForeground)
            minWidth = 0
            minHeight = 0
            val availableWidth = scroller.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            maxWidth = (availableWidth - dp(32)).coerceAtLeast(dp(48))
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tokens.toolbarBackground)
                cornerRadius = dp(3).toFloat()
            }
        }

        val vertical = scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL
        // The ImageView is the full 48dp hit target; its centered drawable stays 4dp wide.
        // Horizontal mode uses a 48dp cross-track target and a thin horizontal thumb.
        scroller.handleWidth = if (vertical) dp(48) else dp(36)
        scroller.handleHeight = if (vertical) dp(36) else dp(48)
        val thumb = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
        thumb.setPadding(0, 0, 0, 0)
        scroller.handleDrawable = GradientDrawable().apply {
            setColor(tokens.interactiveAccent)
            cornerRadius = dp(2).toFloat()
            setSize(if (vertical) dp(4) else dp(36), if (vertical) dp(36) else dp(4))
        }

        // The bottom bar overlays the RecyclerView; keep the track inside the usable viewport.
        scroller.trackMarginEnd = if (vertical) bottomBar else 0
        scroller.requestLayout()

        val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
        // This routes track presses through the library's handle listener. The library owns the
        // only drag state machine, scroll calculation, popup update and release/cancel callbacks.
        track.setOnTouchListener { _, event ->
            val forwarded = MotionEvent.obtain(event)
            val trackLocation = IntArray(2)
            val thumbLocation = IntArray(2)
            track.getLocationInWindow(trackLocation)
            thumb.getLocationInWindow(thumbLocation)
            forwarded.offsetLocation(
                (trackLocation[0] - thumbLocation[0]).toFloat(),
                (trackLocation[1] - thumbLocation[1]).toFloat()
            )
            try {
                thumb.dispatchTouchEvent(forwarded)
            } finally {
                forwarded.recycle()
            }
        }

        fun updateAvailability() {
            val canScroll = if (scroller.fastScrollDirection ==
                RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            ) {
                recycler.canScrollVertically(-1) || recycler.canScrollVertically(1)
            } else {
                recycler.canScrollHorizontally(-1) || recycler.canScrollHorizontally(1)
            }
            scroller.isFastScrollEnabled = canScroll
            track.isEnabled = canScroll
            thumb.isEnabled = canScroll
            thumb.isClickable = canScroll
            thumb.isFocusable = canScroll
            val scale = if (canScroll) 1f else 0f
            thumb.scaleX = scale
            thumb.scaleY = scale
        }

        // Register one observer set per view instance; apply() is called again on resume/theme changes.
        if (scroller.getTag(R.id.classic_fast_scroll_observer) != true) {
            scroller.setTag(R.id.classic_fast_scroll_observer, true)
            recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateAvailability()
            }
            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateAvailability()
                }
            })
        }
        scroller.post { updateAvailability() }

        // The dependency estimates positions from item counts, which can stop at a partial grid row.
        // Correct only an actual endpoint release, using measured remaining scroll range.
        var endpoint = 0
        scroller.setHandleStateListener(object : RecyclerViewFastScroller.HandleStateListener {
            override fun onEngaged() {
                endpoint = 0
            }

            override fun onDragged(offset: Float, position: Int) {
                val length = if (scroller.fastScrollDirection ==
                    RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                ) track.height else track.width
                val handle = if (scroller.fastScrollDirection ==
                    RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                ) scroller.handleHeight else scroller.handleWidth
                endpoint = when {
                    length <= handle -> 0
                    offset <= dp(2) -> -1
                    offset >= length - handle - dp(2) -> 1
                    else -> 0
                }
            }

            override fun onReleased() {
                val edge = endpoint
                endpoint = 0
                if (edge == 0) return
                val adapter = recycler.adapter ?: return
                if (adapter.itemCount == 0) return
                recycler.scrollToPosition(if (edge < 0) 0 else adapter.itemCount - 1)
                recycler.post {
                    if (!recycler.isAttachedToWindow || recycler.adapter !== adapter) return@post
                    val verticalNow = scroller.fastScrollDirection ==
                        RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                    val range = if (verticalNow) recycler.computeVerticalScrollRange()
                        else recycler.computeHorizontalScrollRange()
                    val extent = if (verticalNow) recycler.computeVerticalScrollExtent()
                        else recycler.computeHorizontalScrollExtent()
                    val offset = if (verticalNow) recycler.computeVerticalScrollOffset()
                        else recycler.computeHorizontalScrollOffset()
                    val distance = if (edge < 0) -offset else (range - extent - offset).coerceAtLeast(0)
                    if (verticalNow) recycler.scrollBy(0, distance) else recycler.scrollBy(distance, 0)
                }
            }
        })
    }
}
