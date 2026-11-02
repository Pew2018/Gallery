package org.fossify.gallery.views

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.gallery.R
import org.fossify.gallery.helpers.OxygenClassicDialog

/** A label and a trailing checkmark; the adjacent reorder handle is outside this press target. */
internal class ClassicToolbarChoiceBody(
    context: Context, private val check: CheckBox, accent: Int
) : LinearLayout(context) {
    private val mark = ImageView(context).apply {
        setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.tieba_choice_check_mark)!!.mutate().apply { setTint(accent) })
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val press = OxygenClassicDialog.ripple(accent, resources.displayMetrics.density)
    private var interactive = true
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = resources.displayMetrics.density
        check.buttonDrawable = null
        check.background = null
        check.isClickable = false
        check.isFocusable = false
        check.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(check, LayoutParams(0, -2, 1f))
        addView(mark, LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
            marginStart = (12 * dp).toInt()
            marginEnd = (8 * dp).toInt()
        })
        setOnClickListener { if (interactive) check.performClick() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.text = check.text
                info.className = if (interactive) CheckBox::class.java.name else android.widget.TextView::class.java.name
                info.isCheckable = interactive
                info.isChecked = check.isChecked
            }
        }
        setInteractive(true)
    }
    fun syncChecked() { mark.visibility = if (!interactive) GONE else if (check.isChecked) VISIBLE else INVISIBLE }
    fun setInteractive(value: Boolean) {
        interactive = value
        isClickable = value
        isFocusable = value
        background = if (value) press else null
        syncChecked()
    }
}
