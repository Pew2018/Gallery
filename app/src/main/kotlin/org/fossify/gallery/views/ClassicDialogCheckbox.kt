package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.graphics.drawable.InsetDrawable
import org.fossify.commons.views.MyAppCompatCheckbox

/**
 * A classic-dialog checkbox whose mark immediately matches its native checked state.
 * The parent ClassicDialogCheckboxRow remains the single full-width Ripple target.
 */
class ClassicDialogCheckbox : MyAppCompatCheckbox {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        // The dialog factory wraps the 24dp mark in an InsetDrawable for text spacing.
        // Some API levels do not propagate jumpToCurrentState() to that inner Drawable.
        // Synchronize each level without touching the parent row's independent Ripple.
        var mark = buttonDrawable
        while (mark != null) {
            mark.jumpToCurrentState()
            mark = (mark as? InsetDrawable)?.drawable
        }
        invalidate()
    }
}
