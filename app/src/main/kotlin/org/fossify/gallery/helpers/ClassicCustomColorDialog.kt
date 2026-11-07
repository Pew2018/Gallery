package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config

internal object ClassicCustomColorDialog {
    fun show(host: SimpleActivity, onSaved: (Int) -> Unit): AlertDialog? {
        if (!ClassicThemeDialog.canOpen(host)) return null
        val tokens = OxygenThemeEngine.snapshot(host, host.config)
        val current = tokens.rawAccent
        val body = ClassicThemeDialog.body(host)
        val comparisons = LinearLayout(host)
        fun preview(title: Int): Pair<View, android.widget.TextView> {
            val column = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            column.addView(ClassicThemeDialog.text(host, host.getString(title), 14f, true), LinearLayout.LayoutParams(-1, -2))
            val swatch = View(host).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            column.addView(swatch, LinearLayout.LayoutParams(-1, ClassicThemeDialog.dp(host, 60)).apply {
                topMargin = ClassicThemeDialog.dp(host, 8); bottomMargin = ClassicThemeDialog.dp(host, 4)
            })
            val hex = ClassicThemeDialog.text(host, "", 14f, true)
            column.addView(hex, LinearLayout.LayoutParams(-1, -2))
            comparisons.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (comparisons.childCount > 0) marginStart = ClassicThemeDialog.dp(host, 16)
            })
            return swatch to hex
        }
        fun paint(view: View, color: Int) {
            view.background = GradientDrawable().apply { cornerRadius = ClassicThemeDialog.dp(host, 4).toFloat(); setColor(color) }
        }
        val old = preview(R.string.theme_dialog_current)
        val fresh = preview(R.string.theme_dialog_preview)
        paint(old.first, current)
        old.second.text = OxygenThemeEngine.formatHex(current)
        body.addView(comparisons, LinearLayout.LayoutParams(-1, -2))
        val label = ClassicThemeDialog.text(host, host.getString(R.string.theme_dialog_hex), 16f).apply {
            setPadding(0, ClassicThemeDialog.dp(host, 20), 0, ClassicThemeDialog.dp(host, 4))
        }
        body.addView(label)
        val field = EditText(host).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(android.text.InputFilter.AllCaps())
            isSingleLine = true
            minimumHeight = ClassicThemeDialog.dp(host, 48)
            hint = "#RRGGBB"
            setTextColor(tokens.primaryText); setHintTextColor(tokens.secondaryText)
            backgroundTintList = ColorStateList.valueOf(tokens.interactiveAccent)
            setText(OxygenThemeEngine.formatHex(current)); selectAll()
        }
        label.labelFor = field.id
        body.addView(field, LinearLayout.LayoutParams(-1, -2))
        // Inline Material supporting text, rather than EditText's floating error popup.
        // Reserve its line so validation does not move the preview or dialog buttons.
        val errorColor = photoDialogColors(host).error
        val errorText = ClassicThemeDialog.text(host, host.getString(R.string.classic_invalid_color), 12f).apply {
            labelFor = field.id
            setTextColor(errorColor)
            setPadding(field.paddingLeft, ClassicThemeDialog.dp(host, 4), field.paddingRight, 0)
            visibility = View.INVISIBLE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(errorText, LinearLayout.LayoutParams(-1, -2))
        ViewCompat.setAccessibilityDelegate(field, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isContentInvalid = errorText.visibility == View.VISIBLE
                info.error = if (info.isContentInvalid) errorText.text else null
            }
        })
        // The container takes initial focus; entering the dialog never opens the keyboard.
        body.isFocusableInTouchMode = true
        body.requestFocus()
        val scroll = ClassicThemeDialog.scroll(host, body)
        val dialog = ClassicThemeDialog.show(host, OxygenClassicDialog.builder(host, host.getString(R.string.classic_custom_color))
            .setView(scroll).setNegativeButton(R.string.classic_cancel, null)
            .setPositiveButton(R.string.classic_save, null), scroll)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fun validate(): Int? {
            val color = OxygenThemeEngine.parseHex(field.text.toString().trim())
            save.isEnabled = color != null
            errorText.visibility = if (color == null) View.VISIBLE else View.INVISIBLE
            field.backgroundTintList = ColorStateList.valueOf(if (color == null) errorColor else tokens.interactiveAccent)
            paint(fresh.first, color ?: OxygenThemeColors.blend(tokens.dialogBackground, tokens.secondaryText, .12))
            fresh.second.text = color?.let(OxygenThemeEngine::formatHex) ?: "—"
            return color
        }
        field.doAfterTextChanged { validate() }
        validate()
        save.setOnClickListener {
            validate()?.let { color -> ClassicThemeDialog.commit(host, dialog) { onSaved(color) } }
        }
        return dialog
    }
}
