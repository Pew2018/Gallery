package org.fossify.gallery.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/** A square viewport whose dimensions follow the measured grid column width. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec)
            else -> suggestedMinimumWidth
        }.coerceAtLeast(0)
        val exact = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
        setMeasuredDimension(width, width)
    }
}
