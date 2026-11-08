package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
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
        // Never leave a previous animation frame visible after a first tap or reversal.
        buttonDrawable?.jumpToCurrentState()
        invalidate()
    }
}
