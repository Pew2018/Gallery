package org.fossify.gallery.views

import android.content.Context
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
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun performClick(): Boolean {
        val row = parent as? View ?: return false
        if (!isEnabled || !row.isEnabled) return false

        // Honor View's accessibility click contract. CompoundButton's base
        // implementation also toggles its own checked state, but this setting
        // row is the sole owner of authorization and persistence. Restore the
        // transient native toggle before forwarding exactly one row click.
        val previousChecked = isChecked
        val accessibilityHandled = super.performClick()
        if (isChecked != previousChecked) isChecked = previousChecked
        return row.performClick() || accessibilityHandled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || (parent as? View)?.isEnabled == false) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) downX = event.x
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX
            // Cancel the native UP commit, then use the same gated action as a row tap.
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel)
            cancel.recycle()
            val dragged = abs(dx) > slop
            val requested = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) dx < 0 else dx > 0
            if (!dragged || requested != isChecked) performClick()
            return true
        }
        return super.onTouchEvent(event)
    }
}
