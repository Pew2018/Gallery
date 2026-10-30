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
import java.util.WeakHashMap

/**
 * Classic fast-scroller styling and picker-local scroll synchronization.
 * Main Gallery scrollers keep the library's existing behaviour; the dialog
 * pickers use pixel scroll metrics because their row heights can vary.
 */
object OxygenFastScroller {
    private val pickerStates = WeakHashMap<RecyclerViewFastScroller, PickerScrollState>()
    private const val HIDE_DELAY_MS = 650L
    private const val FADE_DURATION_MS = 180L
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
        // drawable. The picker keeps a separate, larger transparent touch area.
        scroller.updateColors(tokens.interactiveAccent)
        scroller.handleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(tokens.interactiveAccent)
            cornerRadius = dp(2).toFloat()
            // The library's ImageView stays easy to grab while this drawable is 4dp.
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

        // Keep the narrow glyph centered inside a transparent, convenient hit area.
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

    /** Clamp a drag fraction to an adapter position for compact popup labels. */
    internal fun positionForPickerDrag(relative: Float, track: Int, thumb: Int, count: Int): Int {
        if (count <= 1) return 0
        val travel = (track - thumb).coerceAtLeast(1).toFloat()
        return ((relative / travel).coerceIn(0f, 1f) * (count - 1))
            .roundToInt().coerceIn(0, count - 1)
    }

    /** Apply only to picker dialogs, not the Gallery's main scrollers. */
    @SuppressLint("ClickableViewAccessibility")
    fun installPickerTouchHandling(scroller: RecyclerViewFastScroller, recycler: RecyclerView) {
        scroller.post {
            if (!scroller.isAttachedToWindow) return@post
            val thumb = scroller.findViewById<ImageView>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
                ?: return@post
            val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
                ?: return@post
            val state = synchronized(pickerStates) {
                pickerStates[scroller]?.takeIf { it.recycler === recycler }
                    ?: PickerScrollState(scroller, recycler, thumb, track).also {
                        pickerStates[scroller]?.dispose()
                        pickerStates[scroller] = it
                    }
            }
            state.bindAdapter()
            recycler.removeOnScrollListener(state)
            recycler.addOnScrollListener(state)
            recycler.removeOnLayoutChangeListener(state)
            recycler.addOnLayoutChangeListener(state)
            track.removeOnLayoutChangeListener(state)
            track.addOnLayoutChangeListener(state)
            state.syncAndShowIfScrolling()

            // Use one screen-coordinate system and pixel scroll offsets. This
            // avoids the library's raw-screen/window-origin mismatch and keeps
            // dragging smooth even when picker row heights differ.
            val listener = View.OnTouchListener { touched, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        state.beginDrag()
                        touched.parent?.requestDisallowInterceptTouchEvent(true)
                        scroller.parent?.requestDisallowInterceptTouchEvent(true)
                        recycler.stopScroll()
                        state.dragTo(event)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        touched.parent?.requestDisallowInterceptTouchEvent(true)
                        scroller.parent?.requestDisallowInterceptTouchEvent(true)
                        state.dragTo(event)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        state.endDrag()
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

    private class PickerScrollState(
        private val scroller: RecyclerViewFastScroller,
        val recycler: RecyclerView,
        private val thumb: ImageView,
        private val track: View
    ) : RecyclerView.OnScrollListener(), View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
        private val hideThumb = Runnable { fadeThumbOut() }
        private var dragging = false
        private var observedAdapter: RecyclerView.Adapter<*>? = null
        private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = changed()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = changed()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = changed()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = changed()
            private fun changed() { recycler.post { syncPosition(); updateVisibility() } }
        }

        init {
            scroller.addOnAttachStateChangeListener(this)
        }

        fun bindAdapter() {
            val adapter = recycler.adapter
            if (adapter === observedAdapter) return
            observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
            observedAdapter = adapter
            observedAdapter?.registerAdapterDataObserver(adapterObserver)
            recycler.post { syncPosition(); updateVisibility() }
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val current = metrics(vertical)
            if (current.range <= current.extent) {
                updateVisibility()
                return
            }
            syncPosition()
            revealThumb()
            if (dragging) updatePopup()
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (!dragging) scheduleHide()
            } else {
                revealThumb()
            }
        }

        override fun onLayoutChange(
            view: View, left: Int, top: Int, right: Int, bottom: Int,
            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
        ) {
            syncPosition()
            updateVisibility()
        }

        fun syncAndShowIfScrolling() {
            syncPosition()
            if (recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE || dragging) revealThumb()
            else updateVisibility()
        }

        fun beginDrag() {
            dragging = true
            revealThumb()
            scroller.popupTextView.visibility = View.VISIBLE
        }

        fun endDrag() {
            dragging = false
            if (recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE) scheduleHide()
            else revealThumb()
        }

        fun dragTo(event: MotionEvent) {
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val metrics = metrics(vertical)
            val trackLength = if (vertical) track.height else track.width
            val thumbLength = if (vertical) thumb.height else thumb.width
            val scrollable = (metrics.range - metrics.extent).coerceAtLeast(0)
            if (trackLength <= thumbLength || scrollable == 0) return

            val location = IntArray(2)
            track.getLocationOnScreen(location)
            val coordinate = if (vertical) event.rawY - location[1] else event.rawX - location[0]
            val fraction = ((coordinate - thumbLength / 2f) / (trackLength - thumbLength))
                .coerceIn(0f, 1f)
            val target = (fraction * scrollable).roundToInt().coerceIn(0, scrollable)
            val delta = target - metrics.offset
            if (vertical) recycler.scrollBy(0, delta) else recycler.scrollBy(delta, 0)
            syncPosition()
            updatePopup(fraction)
            revealThumb()
        }

        private data class ScrollMetrics(val range: Int, val extent: Int, val offset: Int)

        private fun metrics(vertical: Boolean): ScrollMetrics = if (vertical) {
            ScrollMetrics(recycler.computeVerticalScrollRange(), recycler.computeVerticalScrollExtent(), recycler.computeVerticalScrollOffset())
        } else {
            ScrollMetrics(recycler.computeHorizontalScrollRange(), recycler.computeHorizontalScrollExtent(), recycler.computeHorizontalScrollOffset())
        }

        private fun syncPosition() {
            if (!scroller.isAttachedToWindow || track.width <= 0 || track.height <= 0 || thumb.width <= 0 || thumb.height <= 0) return
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val metric = metrics(vertical)
            val viewport = if (vertical) track.height else track.width
            val thumbSize = if (vertical) thumb.height else thumb.width
            val travel = (viewport - thumbSize).coerceAtLeast(0)
            val scrollable = (metric.range - metric.extent).coerceAtLeast(0)
            val fraction = when {
                scrollable == 0 || (if (vertical) !recycler.canScrollVertically(-1) else !recycler.canScrollHorizontally(-1)) -> 0f
                if (vertical) !recycler.canScrollVertically(1) else !recycler.canScrollHorizontally(1) -> 1f
                else -> metric.offset.toFloat().coerceIn(0f, scrollable.toFloat()) / scrollable
            }
            val desired = (fraction * travel).roundToInt().coerceIn(0, travel)

            val trackLocation = IntArray(2)
            val thumbLocation = IntArray(2)
            track.getLocationOnScreen(trackLocation)
            thumb.getLocationOnScreen(thumbLocation)
            if (vertical) {
                thumb.translationY += trackLocation[1] + desired - thumbLocation[1]
            } else {
                thumb.translationX += trackLocation[0] + desired - thumbLocation[0]
            }
        }

        private fun updatePopup(fraction: Float? = null) {
            val count = recycler.adapter?.itemCount ?: 0
            if (count <= 0) return
            val position = (((fraction ?: run {
                val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                val m = metrics(vertical)
                val scrollable = (m.range - m.extent).coerceAtLeast(0)
                if (scrollable == 0) 0f else m.offset.toFloat().coerceIn(0, scrollable.toFloat()) / scrollable
            }) * (count - 1)).roundToInt()).coerceIn(0, count - 1)
            val label = (recycler.adapter as? RecyclerViewFastScroller.OnPopupTextUpdate)?.onChange(position)
            scroller.popupTextView.text = label ?: (position + 1).toString()
            scroller.popupTextView.visibility = View.VISIBLE
        }

        private fun updateVisibility() {
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val m = metrics(vertical)
            if (m.range > m.extent && track.width > 0 && track.height > 0) {
                thumb.visibility = View.VISIBLE
                if (thumb.alpha == 0f) thumb.alpha = 1f
            } else {
                scroller.removeCallbacks(hideThumb)
                thumb.animate().cancel()
                thumb.visibility = View.INVISIBLE
            }
        }

        private fun revealThumb() {
            scroller.removeCallbacks(hideThumb)
            thumb.animate().cancel()
            thumb.alpha = 1f
            if (thumb.visibility != View.VISIBLE) thumb.visibility = View.VISIBLE
            updatePopupIfDragging()
        }

        private fun updatePopupIfDragging() {
            if (dragging) updatePopup()
        }

        private fun scheduleHide() {
            scroller.removeCallbacks(hideThumb)
            thumb.animate().cancel()
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val metric = metrics(vertical)
            if (metric.range <= metric.extent) return
            thumb.visibility = View.VISIBLE
            thumb.alpha = 1f
            scroller.postDelayed(hideThumb, HIDE_DELAY_MS)
        }

        private fun fadeThumbOut() {
            if (dragging || recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) return
            thumb.animate().cancel()
            thumb.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction {
                if (!dragging && recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE) thumb.visibility = View.INVISIBLE
            }.start()
        }

        override fun onViewAttachedToWindow(view: View) {
            bindAdapter()
            recycler.addOnScrollListener(this)
            recycler.addOnLayoutChangeListener(this)
            syncPosition()
        }

        override fun onViewDetachedFromWindow(view: View) = dispose()

        fun dispose() {
            scroller.removeCallbacks(hideThumb)
            thumb.animate().cancel()
            recycler.removeOnScrollListener(this)
            recycler.removeOnLayoutChangeListener(this)
            track.removeOnLayoutChangeListener(this)
            observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
            observedAdapter = null
            scroller.removeOnAttachStateChangeListener(this)
            synchronized(pickerStates) { pickerStates.remove(scroller) }
        }
    }
}
