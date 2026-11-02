package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** TiebaLite Container: full width on phones, centered 87% / 75% on larger windows. */
class TiebaSettingsContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val dpWidth = width / resources.displayMetrics.density
        val fraction = when {
            dpWidth >= 840 -> 0.75f
            dpWidth >= 600 -> 0.87f
            else -> 1f
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec((width * fraction).roundToInt(), MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec)
    }
}
