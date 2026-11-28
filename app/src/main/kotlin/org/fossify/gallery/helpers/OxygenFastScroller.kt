package org.fossify.gallery.helpers

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlin.math.roundToInt
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Classic fast-scroller styling and picker-local scroll synchronization.
 * Gallery and its dialog pickers use RecyclerView pixel scroll metrics.
 */
object OxygenFastScroller {
    private val galleryStates = WeakHashMap<RecyclerViewFastScroller, WeakReference<GalleryDrag>>()
    private val pickerStates = WeakHashMap<RecyclerViewFastScroller, PickerScrollState>()
    private const val HIDE_DELAY_MS = 650L
    private const val FADE_DURATION_MS = 180L

    /** Keep the RecyclerView visible while its final scroll range is unknown. */
    fun setRangeAvailable(scroller: RecyclerViewFastScroller, available: Boolean) {
        scroller.visibility = View.VISIBLE
        scroller.isFastScrollEnabled = available
        // The library scales its handle to 0 after the idle timeout, which also
        // collapses its touch bounds to 0. Keep the existing 28dp handle target
        // interactive whenever the current grid has a real scroll range.
        scroller.handleVisibilityDuration = 0
        val childVisibility = if (available) View.VISIBLE else View.INVISIBLE
        scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)?.apply {
            animate().cancel()
            visibility = childVisibility
            isEnabled = available
            if (available) {
                scaleX = 1f
                scaleY = 1f
            }
        }
        scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)?.apply {
            visibility = childVisibility
            isEnabled = available
        }
        val galleryDrag = galleryStates[scroller]?.get()
        if (available) galleryDrag?.syncThumb()
        else if (galleryDrag != null) galleryDrag.endDrag()
        else FastScrollerBubble.hide(scroller.popupTextView)
    }

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

    /** Keep a horizontal Gallery thumb above the translucent navigation and app bars. */
    fun applyGalleryBottomInsets(scroller: RecyclerViewFastScroller, bottomBar: Int = 0) {
        val vertical = scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL
        val thumb = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            ?: return
        val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
            ?: return
        fun apply(insets: WindowInsetsCompat) {
            val navigation = if (vertical) 0 else
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val offset = -(navigation + if (vertical) 0 else bottomBar).toFloat()
            // The thumb is a child of trackView; translating the rail also
            // moves the handle, so a second translation would detach it.
            thumb.translationY = 0f
            track.translationY = offset
        }

        ViewCompat.setOnApplyWindowInsetsListener(scroller) { _, insets ->
            apply(insets)
            insets
        }
        ViewCompat.getRootWindowInsets(scroller)?.let(::apply)
        ViewCompat.requestApplyInsets(scroller)

        fun updateGestureExclusion() {
            val rects = if (!vertical && thumb.width > 0 && thumb.height > 0) {
                listOf(Rect(0, 0, thumb.width, thumb.height))
            } else {
                emptyList()
            }
            ViewCompat.setSystemGestureExclusionRects(thumb, rects)
        }
        updateGestureExclusion()
        thumb.post { updateGestureExclusion() }
    }

    /**
     * The bundled scroller mixes raw screen coordinates with window coordinates
     * and scales its idle thumb to zero. Gallery uses pixel scroll metrics so
     * dragging stays aligned with the visible track across system-bar insets.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun installGalleryTouchHandling(
        scroller: RecyclerViewFastScroller,
        recycler: RecyclerView,
        onDragStateChanged: (Boolean) -> Unit = {},
    ) {
        val thumb = scroller.findViewById<ImageView>(com.qtalk.recyclerviewfastscroller.R.id.thumbIV)
            ?: return
        val track = scroller.findViewById<View>(com.qtalk.recyclerviewfastscroller.R.id.trackView)
            ?: return
        val existing = galleryStates[scroller]?.get()
        if (existing?.recycler === recycler) return
        existing?.dispose()
        val state = GalleryDrag(scroller, recycler, thumb, track, onDragStateChanged)
        galleryStates[scroller] = WeakReference(state)
    }

    fun disposeGalleryTouchHandling(scroller: RecyclerViewFastScroller) {
        galleryStates.remove(scroller)?.get()?.dispose()
    }

    private class GalleryDrag(
        private val scroller: RecyclerViewFastScroller,
        val recycler: RecyclerView,
        private val thumb: View,
        private val track: View,
        private val onDragStateChanged: (Boolean) -> Unit,
    ) : View.OnTouchListener, View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val popup = scroller.popupTextView
        private val hidePopup = Runnable { hidePopup() }
        private val bindTouch = Runnable { bindTouch() }
        private val trackLocation = IntArray(2)
        private val thumbLocation = IntArray(2)
        private var activePointer = MotionEvent.INVALID_POINTER_ID
        private var grabOffset = 0f
        private var popupObserver: ViewTreeObserver? = null
        private val vertical get() = scroller.fastScrollDirection ==
            RecyclerViewFastScroller.FastScrollDirection.VERTICAL

        init {
            scroller.handleVisibilityDuration = 0
            FastScrollerBubble.hide(popup)
            scroller.addOnAttachStateChangeListener(this)
            bindTouch()
            // The library posts its initial thumb listener during inflation.
            scroller.post(bindTouch)
        }

        private fun bindTouch() {
            thumb.setOnTouchListener(this)
            // The track is the full-height edge rail. A listener there starts a
            // drag on any edge touch, including the system back swipe. Only the
            // handle itself may drag; the rail must let that gesture through.
            track.setOnTouchListener { _, _ -> false }
            track.isClickable = false
            track.isLongClickable = false
        }

        override fun onTouch(touched: View, event: MotionEvent): Boolean {
            if (touched !== thumb) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!scroller.isFastScrollEnabled || recycler.adapter == null) return false
                    activePointer = event.getPointerId(0)
                    val axis = if (vertical) 1 else 0
                    thumb.getLocationOnScreen(thumbLocation)
                    val length = if (vertical) thumb.height else thumb.width
                    grabOffset = if (touched === thumb) coordinate(event, 0) - thumbLocation[axis] - length / 2f else 0f
                    scroller.removeCallbacks(hidePopup)
                    track.parent?.requestDisallowInterceptTouchEvent(true)
                    onDragStateChanged(true)
                    recycler.stopScroll()
                    dragTo(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activePointer == MotionEvent.INVALID_POINTER_ID) return false
                    dragTo(event)
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (activePointer == MotionEvent.INVALID_POINTER_ID) return false
                    if (event.getPointerId(event.actionIndex) == activePointer) endDrag()
                }
                MotionEvent.ACTION_UP -> {
                    if (activePointer == MotionEvent.INVALID_POINTER_ID) return false
                    endDrag(delayHide = true)
                }
                MotionEvent.ACTION_CANCEL -> endDrag()
                else -> return activePointer != MotionEvent.INVALID_POINTER_ID
            }
            return true
        }

        private fun coordinate(event: MotionEvent, index: Int): Float =
            if (vertical) event.rawY + event.getY(index) - event.y
            else event.rawX + event.getX(index) - event.x

        private fun dragTo(event: MotionEvent) {
            val index = event.findPointerIndex(activePointer)
            if (index < 0) return
            val range = if (vertical) recycler.computeVerticalScrollRange() else recycler.computeHorizontalScrollRange()
            val extent = if (vertical) recycler.computeVerticalScrollExtent() else recycler.computeHorizontalScrollExtent()
            if (range <= extent) return
            track.getLocationOnScreen(trackLocation)
            val target = scrollOffsetForDrag(
                coordinate(event, index) - grabOffset,
                trackLocation[if (vertical) 1 else 0],
                if (vertical) track.height else track.width,
                if (vertical) thumb.height else thumb.width,
                range, extent,
                reverse = reverseTrack(),
            )
            if (vertical) recycler.scrollBy(0, target - recycler.computeVerticalScrollOffset())
            else recycler.scrollBy(target - recycler.computeHorizontalScrollOffset(), 0)
            syncThumb()

            val manager = recycler.layoutManager as? LinearLayoutManager ?: return
            val position = manager.findFirstVisibleItemPosition()
            val label = (recycler.adapter as? RecyclerViewFastScroller.OnPopupTextUpdate)
                ?.onChange(position) ?: return
            if (popup.text.toString() != label) popup.text = label
            FastScrollerBubble.show(popup)
            if (popupObserver == null) {
                popupObserver = scroller.viewTreeObserver.also { it.addOnPreDrawListener(this) }
            }
            FastScrollerBubble.position(popup, thumb, scroller, vertical)
        }

        private fun reverseTrack(): Boolean {
            if (vertical) return false
            val manager = recycler.layoutManager as? LinearLayoutManager ?: return false
            val rtl = recycler.layoutDirection == View.LAYOUT_DIRECTION_RTL
            return manager.reverseLayout xor rtl
        }

        fun syncThumb() {
            val range = if (vertical) recycler.computeVerticalScrollRange() else recycler.computeHorizontalScrollRange()
            val extent = if (vertical) recycler.computeVerticalScrollExtent() else recycler.computeHorizontalScrollExtent()
            val maxOffset = range - extent
            if (maxOffset <= 0) return
            val offset = if (vertical) recycler.computeVerticalScrollOffset() else recycler.computeHorizontalScrollOffset()
            val fraction = offset.coerceIn(0, maxOffset).toFloat() / maxOffset
            val display = if (reverseTrack()) 1f - fraction else fraction
            if (vertical) thumb.y = (track.height - thumb.height).coerceAtLeast(0) * display
            else thumb.x = (track.width - thumb.width).coerceAtLeast(0) * display
        }

        override fun onPreDraw(): Boolean {
            syncThumb()
            FastScrollerBubble.position(popup, thumb, scroller, vertical)
            return true
        }

        fun endDrag(delayHide: Boolean = false) {
            val wasDragging = activePointer != MotionEvent.INVALID_POINTER_ID
            activePointer = MotionEvent.INVALID_POINTER_ID
            scroller.removeCallbacks(hidePopup)
            if (delayHide) scroller.postDelayed(hidePopup, HIDE_DELAY_MS) else hidePopup()
            if (wasDragging) {
                track.parent?.requestDisallowInterceptTouchEvent(false)
                onDragStateChanged(false)
            }
        }

        private fun hidePopup() {
            FastScrollerBubble.hide(popup)
            popupObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            popupObserver = null
        }

        override fun onViewAttachedToWindow(view: View) { scroller.post(bindTouch) }

        override fun onViewDetachedFromWindow(view: View) {
            scroller.removeCallbacks(bindTouch)
            endDrag()
        }

        fun dispose() {
            scroller.removeOnAttachStateChangeListener(this)
            thumb.setOnTouchListener(null)
            track.setOnTouchListener(null)
            onViewDetachedFromWindow(scroller)
        }
    }

    /** Clamp a drag fraction to an adapter position for compact popup labels. */
    internal fun positionForPickerDrag(relative: Float, track: Int, thumb: Int, count: Int): Int {
        if (count <= 1) return 0
        val travel = (track - thumb).coerceAtLeast(1).toFloat()
        return ((relative / travel).coerceIn(0f, 1f) * (count - 1))
            .roundToInt().coerceIn(0, count - 1)
    }

    internal fun scrollOffsetForDrag(
        coordinate: Float,
        trackStart: Int,
        trackLength: Int,
        thumbLength: Int,
        range: Int,
        extent: Int,
        reverse: Boolean = false,
    ): Int {
        val maxOffset = (range - extent).coerceAtLeast(0)
        if (maxOffset == 0) return 0
        val travel = (trackLength - thumbLength).coerceAtLeast(1).toFloat()
        val fraction = ((coordinate - trackStart - thumbLength / 2f) / travel).coerceIn(0f, 1f)
        val oriented = if (reverse) 1f - fraction else fraction
        return (oriented * maxOffset).roundToInt()
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
            // dragging smooth even when picker row heights differ. Only the
            // handle may start a drag; the rail must let edge-back through.
            val listener = View.OnTouchListener { touched, event ->
                if (touched !== thumb) return@OnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!state.canScroll()) return@OnTouchListener false
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
            track.setOnTouchListener { _, _ -> false }
            track.isClickable = false
            track.isLongClickable = false
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

        fun canScroll(): Boolean {
            val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
            val metrics = metrics(vertical)
            return metrics.range > metrics.extent
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

        private fun reverseTrack(vertical: Boolean): Boolean {
            if (vertical) return false
            val manager = recycler.layoutManager as? LinearLayoutManager ?: return false
            val rtl = recycler.layoutDirection == View.LAYOUT_DIRECTION_RTL
            return manager.reverseLayout xor rtl
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
            val oriented = if (reverseTrack(vertical)) 1f - fraction else fraction
            val target = (oriented * scrollable).roundToInt().coerceIn(0, scrollable)
            val delta = target - metrics.offset
            if (vertical) recycler.scrollBy(0, delta) else recycler.scrollBy(delta, 0)
            syncPosition()
            updatePopup(oriented)
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
                else -> metric.offset.toFloat().coerceIn(0f, scrollable.toFloat()) / scrollable.toFloat()
            }
            val display = if (reverseTrack(vertical)) 1f - fraction else fraction
            val desired = (display * travel).roundToInt().coerceIn(0, travel)

            val trackLocation = IntArray(2)
            val thumbLocation = IntArray(2)
            track.getLocationOnScreen(trackLocation)
            thumb.getLocationOnScreen(thumbLocation)
            if (vertical) {
                thumb.translationY = thumb.translationY + (trackLocation[1] + desired - thumbLocation[1]).toFloat()
            } else {
                thumb.translationX = thumb.translationX + (trackLocation[0] + desired - thumbLocation[0]).toFloat()
            }
        }

        private fun updatePopup(fraction: Float? = null) {
            val count = recycler.adapter?.itemCount ?: 0
            if (count <= 0) return
            val position = (((fraction ?: run {
                val vertical = scroller.fastScrollDirection == RecyclerViewFastScroller.FastScrollDirection.VERTICAL
                val m = metrics(vertical)
                val scrollable = (m.range - m.extent).coerceAtLeast(0)
                if (scrollable == 0) 0f else m.offset.toFloat()
                    .coerceIn(0f, scrollable.toFloat()) / scrollable.toFloat()
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
            thumb.animate().alpha(0f).setDuration(FADE_DURATION_MS)
                .setInterpolator(OxygenClassicMotion.easing()).withEndAction {
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
