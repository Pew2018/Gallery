package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout

/** Keep width : height fields inside the narrower dialog, including large-font separators. */
class TiebaDimensionFieldsLayout(context: Context, attrs: AttributeSet?) : RelativeLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val children = (0 until childCount).map { getChildAt(it) }
        val fields = children.filterIsInstance<TextInputLayout>().take(2)
        val separator = children.filterIsInstance<TextView>().firstOrNull { it.text.toString() == ":" }
        if (fields.size == 2 && separator != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            separator.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
            val margins = separator.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
                separator.measuredWidth - margins.marginStart - margins.marginEnd
            fields.forEach { it.layoutParams.width = (available / 2).coerceAtLeast(1) }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
