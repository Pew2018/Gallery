package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRadioButton
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config

internal object MaterialColorPickerDialog {
    fun show(host: SimpleActivity, label: (OxygenPreset) -> String, onSelected: (OxygenPreset) -> Unit): AlertDialog? {
        if (!ClassicThemeDialog.canOpen(host)) return null
        val tokens = OxygenThemeEngine.snapshot(host, host.config)
        val body = ClassicThemeDialog.body(host, 0)
        val scroll = ClassicThemeDialog.scroll(host, body)
        var dialog: AlertDialog? = null
        OxygenThemePresets.group("material").forEach { preset ->
            val row = LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ClassicThemeDialog.dp(host, 56)
                setPadding(ClassicThemeDialog.dp(host, 24), ClassicThemeDialog.dp(host, 8),
                    ClassicThemeDialog.dp(host, 24), ClassicThemeDialog.dp(host, 8))
                isFocusable = true
                contentDescription = label(preset)
                background = ClassicThemeDialog.cellBackground(host)
                setOnClickListener { dialog?.let { ClassicThemeDialog.commit(host, it) { onSelected(preset) } } }
            }
            val swatch = View(host).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(preset.color) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(swatch, LinearLayout.LayoutParams(ClassicThemeDialog.dp(host, 28), ClassicThemeDialog.dp(host, 28)).apply {
                marginEnd = ClassicThemeDialog.dp(host, 16)
            })
            row.addView(ClassicThemeDialog.text(host, label(preset)).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val selected = OxygenThemePresets.isSelected(host.config.oxygenThemeActivated, host.config.oxygenThemePresetId, preset.id)
            row.addView(AppCompatRadioButton(host).apply {
                isChecked = selected
                isClickable = false; isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, 0, 0, 0); minWidth = 0; minHeight = 0
                buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(tokens.interactiveAccent, tokens.secondaryText))
            }, LinearLayout.LayoutParams(ClassicThemeDialog.dp(host, 24), ClassicThemeDialog.dp(host, 24)))
            ClassicThemeDialog.selection(host, row, selected)
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        dialog = ClassicThemeDialog.show(host, OxygenClassicDialog.builder(host, host.getString(R.string.classic_more_colors))
            .setView(scroll).setNegativeButton(R.string.classic_cancel, null), scroll)
        return dialog
    }
}
