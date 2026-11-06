package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.FrameLayout

/**
 * Full-row touch and ripple owner for the two delete confirmation choices.
 * Unlike the 56dp options rows, this wrapper adds no minimum height or padding.
 */
class DeleteConfirmationCheckboxRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    /**
     * The CheckBox remains the native click and accessibility target. Mirror its
     * pressed state to this wrapper so its full-row Ripple follows the same real
     * touch sequence without manually forwarding performClick() to the child.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            drawableHotspotChanged(event.x, event.y)
        }

        val handled = super.dispatchTouchEvent(event)
        val checkbox = getChildAt(0) as? CheckBox
        isPressed = checkbox?.let { isEnabled && it.isEnabled && it.isPressed } == true

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            isPressed = false
        }
        return handled
    }
}
