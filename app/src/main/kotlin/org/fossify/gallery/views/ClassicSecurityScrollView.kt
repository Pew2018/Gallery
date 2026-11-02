package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.fossify.commons.views.MyScrollView
import org.fossify.gallery.helpers.OxygenClassicDialog

/** Only security content scrolls; AlertDialog actions remain outside. */
class ClassicSecurityScrollView(context: Context, attrs: AttributeSet) : MyScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var owner = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) {
            val base = owner.baseContext
            if (base === owner) break
            owner = base
        }
        val available = (owner as? android.app.Activity)?.let(OxygenClassicDialog::availableHeight)
            ?: resources.displayMetrics.heightPixels
        val limit = OxygenClassicDialog.scrollViewportHeight(this, available)
        val cap = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) limit
            else minOf(limit, View.MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
