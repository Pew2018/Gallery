package org.fossify.gallery.helpers

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.gallery.R

/** Style the library's existing viewport and popup without replacing its scroll logic. */
object OxygenFastScroller {
    fun apply(
        scroller: RecyclerViewFastScroller, recycler: RecyclerView,
        tokens: OxygenThemeTokens, bottomBar: Int = 0
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
            maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(48))
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
        if (scroller.getTag(R.id.classic_fast_scroll_observer) != true) {
            scroller.setTag(R.id.classic_fast_scroll_observer, true)
            fun updateAvailability() {
                val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                val canScroll = if (vertical) recycler.canScrollVertically(-1) || recycler.canScrollVertically(1)
                    else recycler.canScrollHorizontally(-1) || recycler.canScrollHorizontally(1)
                scroller.isFastScrollEnabled = canScroll
                if (!canScroll) {
                    scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)?.apply {
                        scaleX = 0f
                        scaleY = 0f
                    }
                    scroller.popupTextView.scaleX = 0f
                    scroller.popupTextView.scaleY = 0f
                }
            }
            recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateAvailability() }
            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateAvailability()
                }
            })
            recycler.post { updateAvailability() }
        }
        // The library's last-position estimate can stop one row early in a grid.
        // Correct only an actual endpoint release, leaving ordinary drags alone.
        var endpoint = 0
        scroller.setHandleStateListener(object : RecyclerViewFastScroller.HandleStateListener {
            override fun onEngaged() { endpoint = 0 }

            override fun onDragged(offset: Float, position: Int) {
                val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
                val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                val length = if (vertical) track.height else track.width
                val handle = if (vertical) scroller.handleHeight else scroller.handleWidth
                if (length <= handle) { endpoint = 0; return }
                endpoint = when {
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
                val count = adapter.itemCount
                if (count == 0) return
                recycler.scrollToPosition(if (edge < 0) 0 else count - 1)
                recycler.post {
                    if (recycler.adapter !== adapter || !recycler.isAttachedToWindow) return@post
                    val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                    val distance = if (vertical) recycler.computeVerticalScrollRange()
                        else recycler.computeHorizontalScrollRange()
                    if (vertical) recycler.scrollBy(0, if (edge < 0) -distance else distance)
                    else recycler.scrollBy(if (edge < 0) -distance else distance, 0)
                }
            }
        })
    }
}
