package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** A setting label and a trailing checkbox without reversing reading direction. */
class ClassicCheckboxRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onFinishInflate() {
        super.onFinishInflate()
        val checkbox = (0 until childCount).map { getChildAt(it) }.filterIsInstance<CheckBox>().single()
        val label = (0 until childCount).map { getChildAt(it) }.filterIsInstance<TextView>()
            .first { it !== checkbox }
        checkbox.contentDescription = label.text
        label.labelFor = checkbox.id
        // The checkbox keeps its own listener; tapping the label uses the same path.
        setOnClickListener { checkbox.performClick() }
    }
}
