package org.fossify.gallery.helpers

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

/** One physical pixel between reorder rows; no trailing rule or added row height. */
internal class ClassicReorderDecoration(color: Int, private val inset: Int) : RecyclerView.ItemDecoration() {
    private val paint = Paint().apply { this.color = color }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val last = (parent.adapter?.itemCount ?: 0) - 1
        for (i in 0 until parent.childCount) {
            val row = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(row)
            if (position == RecyclerView.NO_POSITION || position >= last) continue
            val y = row.bottom + row.translationY - 1f
            canvas.drawRect((parent.paddingLeft + inset).toFloat(), y,
                (parent.width - parent.paddingRight - inset).toFloat(), y + 1f, paint)
        }
    }
}
