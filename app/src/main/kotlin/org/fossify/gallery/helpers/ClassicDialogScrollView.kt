package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

/** Bounds only the scrolling content, leaving title/actions available at large font scales. */
class ClassicDialogScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = Rect()
        getWindowVisibleDisplayFrame(visible)
        val available = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val limit = (available * 0.60f).toInt().coerceAtLeast(1)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}

/** Both directory pickers share a bounded viewport while retaining their CoordinatorLayout API. */
class ClassicDialogPickerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    androidx.coordinatorlayout.widget.CoordinatorLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = Rect()
        getWindowVisibleDisplayFrame(visible)
        val available = visible.height().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val limit = (available * 0.70f).toInt().coerceAtLeast(1)
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cap = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
    }
}
