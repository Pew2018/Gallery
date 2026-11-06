package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.views.MyAppCompatCheckbox

/** Native click/accessibility semantics with synchronous confirmation-choice rendering. */
class DeleteConfirmationCheckbox : MyAppCompatCheckbox {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // A destructive-action choice must show the value that confirmation will read,
        // including the first tap and reversals before an animation's first frame.
        // Jump only the mark, never the row Ripple. Invalidate the View itself as well
        // as the Drawable so hardware display lists cannot retain the previous mark.
        buttonDrawable?.jumpToCurrentState()
        invalidate()
        (parent as? DeleteConfirmationCheckboxRow)?.isPressed = isEnabled && isPressed
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        (parent as? DeleteConfirmationCheckboxRow)?.drawableHotspotChanged(x + left, y + top)
    }
}
