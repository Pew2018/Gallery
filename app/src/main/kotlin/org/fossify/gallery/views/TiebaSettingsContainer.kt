package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Full width on phones; continuous centered width retaining 522dp at 600 and 75% at 840. */
class TiebaSettingsContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var dividerColor: Int = 0xFFE6E6E6.toInt()
    private val dividerInset get() = 16f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
    }

    /** Set by the settings screen from its semantic theme tokens. */
    fun setSettingsDividerColor(color: Int) {
        if (dividerColor == color) return
        dividerColor = color
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        dividerPaint.color = dividerColor
        // A 0.5dp stroke stays subtle across density buckets. Draw only
        // between currently visible rows; section headings and hidden rows are skipped.
        dividerPaint.strokeWidth = maxOf(1f, resources.displayMetrics.density * 0.5f)
        var previous: android.view.View? = null
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != VISIBLE || child.height <= 0) continue
            val prior = previous
            if (prior != null && !isSection(prior) && !isSection(child)) {
                val y = prior.bottom.toFloat() - 0.5f
                val inset = dividerInset
                canvas.drawLine(inset, y, width - inset, y, dividerPaint)
            }
            previous = child
        }
    }

    private fun isSection(view: android.view.View): Boolean = view.tag == "classic_section"

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
