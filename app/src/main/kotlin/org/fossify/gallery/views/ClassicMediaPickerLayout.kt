package org.fossify.gallery.views

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import org.fossify.gallery.helpers.OxygenClassicDialog

/** Preserve the picker hierarchy and fast scroller; bound only its viewport. */
class ClassicMediaPickerLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity && owner.baseContext !== owner) owner = owner.baseContext
        val activity = owner as? Activity
        val available = activity?.let(OxygenClassicDialog::availableHeight) ?: resources.displayMetrics.heightPixels
        val limit = OxygenClassicDialog.scrollViewportHeight(this, available)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit
            else minOf(limit, View.MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
