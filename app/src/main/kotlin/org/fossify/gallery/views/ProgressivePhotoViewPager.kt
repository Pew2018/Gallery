package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import org.fossify.commons.views.MyViewPager
import kotlin.math.abs

/** Outward swipes at provisional edges are navigation requests, not confirmed EOF.
 * Real media positions remain unchanged; loading lives outside the media adapter.
 */
class ProgressivePhotoViewPager(context: Context, attrs: AttributeSet) : MyViewPager(context, attrs) {
    var onUnknownBoundary: ((Boolean) -> Boolean)? = null
    var canPageHorizontallyFromCurrentPhoto: (() -> Boolean)? = null
    var transitionInputLocked = false
    private var downX = 0f
    private var downY = 0f
    private var startItem = -1
    private var singlePointer = true
    private var horizontalPhotoGesture = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (horizontalPhotoGesture) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (transitionInputLocked) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startItem = currentItem
                singlePointer = true
                horizontalPhotoGesture = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> singlePointer = false
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (singlePointer && !horizontalPhotoGesture &&
                    abs(dx) > ViewConfiguration.get(context).scaledTouchSlop &&
                    abs(dx) > abs(dy) * 1.2f &&
                    canPageHorizontallyFromCurrentPhoto?.invoke() == true) {
                    // The zoom widget can disallow parent interception before
                    // ViewPager gets a chance to recognize a page swipe. At fit
                    // scale, take the stream here; zoomed photos keep panning.
                    horizontalPhotoGesture = true
                    requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val forward = (dx < 0) != (layoutDirection == LAYOUT_DIRECTION_RTL)
                val edge = if (forward) currentItem == (adapter?.count ?: 0) - 1 else currentItem == 0
                if (singlePointer && startItem == currentItem && edge &&
                    abs(dx) > ViewConfiguration.get(context).scaledTouchSlop * 3 && abs(dx) > abs(dy) * 1.5f) {
                    // Respect a zoomed child's horizontal pan; only handle an edge it cannot scroll past.
                    if (!canScroll(this, false, -dx.toInt(), event.x.toInt(), event.y.toInt()) &&
                        onUnknownBoundary?.invoke(forward) == true) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        return true
                    }
                }
            }
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            horizontalPhotoGesture = false
        }
        return result
    }
}
