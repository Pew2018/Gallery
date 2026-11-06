package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.FrameLayout

/**
 * Full-row touch and ripple owner for the two delete confirmation choices.
 * Unlike the 56dp options rows, this wrapper adds no minimum height or padding.
 */
class DeleteConfirmationCheckboxRow @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    override fun onFinishInflate() {
        super.onFinishInflate()
        val checkbox = getChildAt(0) as CheckBox
        checkbox.isClickable = false
        checkbox.isFocusable = false
        checkbox.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = true
        setOnClickListener { if (checkbox.isEnabled) checkbox.performClick() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = CheckBox::class.java.name
                info.text = checkbox.text
                info.isCheckable = true
                info.isChecked = checkbox.isChecked
                info.isEnabled = checkbox.isEnabled
            }
        }
    }
}
