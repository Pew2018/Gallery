package org.fossify.gallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.view.menu.ListMenuItemView
import kotlin.math.roundToInt

/** Bound intrinsic menu measurement too: AppCompat otherwise permits 320dp or half a tablet. */
@SuppressLint("RestrictedApi")
class TiebaMenuItemView(context: Context, attrs: AttributeSet?) : ListMenuItemView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximum = (280 * resources.displayMetrics.density).roundToInt()
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val bounded = if (mode == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST)
        } else MeasureSpec.makeMeasureSpec(minOf(size, maximum), mode)
        super.onMeasure(bounded, heightMeasureSpec)
    }
}
