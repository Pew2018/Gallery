package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Full width on phones; continuous centered width retaining 522dp at 600 and 75% at 840. */
class TiebaSettingsContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val dpWidth = width / resources.displayMetrics.density
        // Old 600/840 breakpoints suddenly removed 78/100dp of content width.
        // A continuous cap keeps both established wide-window reference widths.
        val contentWidth = minOf(dpWidth, maxOf(600f * 0.87f, dpWidth * 0.75f))
        super.onMeasure(MeasureSpec.makeMeasureSpec(
            (contentWidth * resources.displayMetrics.density).roundToInt(),
            MeasureSpec.getMode(widthMeasureSpec)
        ), heightMeasureSpec)
    }
}
