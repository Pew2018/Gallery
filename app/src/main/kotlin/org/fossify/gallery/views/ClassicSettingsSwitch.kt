package org.fossify.gallery.views

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.abs

/** Route touch, drag, keyboard and accessibility clicks through the existing row action.
 * Permission/password-gated rows remain the single owner of toggle + persistence.
 * No checked-change listener is installed, so binding and state restore never save twice.
 */
class ClassicSettingsSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SwitchCompat(context, attrs) {
    private var downX = 0f
    private var downY = 0f
    private var gestureActive = false
    private var gestureCancelled = false
    private var moved = false
    private var rippleRow: View? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var dispatchingAccessibleClick = false

    /** Use the SwitchCompat API that lets the drawable define the exact 48dp switch width. */
    internal fun useClassicSwitchMeasurement() {
        setEnforceSwitchWidth(false)
        requestLayout()
    }

    override fun toggle() {
        // Suppress only CompoundButton.performClick's implicit toggle.
        // The permission-aware parent row remains the sole state/persistence owner.
        if (!dispatchingAccessibleClick) super.toggle()
    }

    override fun performClick(): Boolean {
        val row = parent as? View ?: return false
        if (!isEnabled || !row.isEnabled) return false

        // Call the platform click contract for keyboard and accessibility.
        // A guarded toggle() avoids the transient native checked state and
        // duplicate animation before the parent row authorizes the real change.
        dispatchingAccessibleClick = true
        val accessibilityHandled = try {
            super.performClick()
        } finally {
            dispatchingAccessibleClick = false
        }
        return row.performClick() || accessibilityHandled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || (parent as? View)?.isEnabled == false) {
            cancelGesture()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture()
                downX = event.x
                downY = event.y
                gestureCancelled = false
                moved = false
                gestureActive = true
                rippleRow = parent as? View
                updateRowRipple(true, event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureActive) return false
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                moved = moved || dx > slop || dy > slop
                // A scroll/out-of-bounds gesture cannot turn back into a tap.
                gestureCancelled = gestureCancelled || (dy > slop && dy > dx) ||
                    event.y < -slop || event.y > height + slop ||
                    event.x < -slop || event.x > width + slop
                if (gestureCancelled) {
                    // SwitchCompat otherwise treats vertical thumb movement as a drag
                    // and prevents the surrounding NestedScrollView from intercepting.
                    cancelGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                updateRowRipple(!moved && !gestureCancelled, event)
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                val dx = event.x - downX
                val dy = abs(event.y - downY)
                val commit = !gestureCancelled && !(dy > slop && dy > abs(dx)) &&
                    event.x >= 0 && event.x < width && event.y >= 0 && event.y < height
                // Native CANCEL restores the thumb without CompoundButton committing.
                cancelGesture()
                val dragged = abs(dx) > slop
                val requested = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) dx < 0 else dx > 0
                if (commit && (!dragged || requested != isChecked)) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                cancelGesture()
                return true
            }
            else -> if (!gestureActive) return false
        }
        return super.onTouchEvent(event)
    }

    private fun updateRowRipple(pressed: Boolean, event: MotionEvent? = null) {
        val row = rippleRow ?: return
        event?.let {
            row.drawableHotspotChanged(
                (left + translationX + it.x - row.scrollX).coerceIn(0f, row.width.toFloat()),
                (top + translationY + it.y - row.scrollY).coerceIn(0f, row.height.toFloat())
            )
        }
        row.isPressed = pressed
    }

    private fun cancelGesture() {
        updateRowRipple(false)
        rippleRow = null
        if (gestureActive) {
            gestureActive = false
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try {
                super.onTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) cancelGesture()
        super.setEnabled(enabled)
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.VISIBLE) cancelGesture()
        super.onWindowVisibilityChanged(visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        if (visibility != View.VISIBLE) cancelGesture()
        super.onVisibilityChanged(changedView, visibility)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelGesture()
        super.onWindowFocusChanged(hasWindowFocus)
    }
}
