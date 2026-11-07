package org.fossify.gallery.helpers

import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config

internal object AppIconPickerDialog {
    fun show(host: SimpleActivity): AlertDialog? {
        if (!ClassicThemeDialog.canOpen(host)) return null
        val ids = host.getAppIconIDs()
        val colors = host.getAppIconColors()
        val initial = colors.indexOf(host.config.appIconColor).coerceAtLeast(0)
        var selected = initial
        val body = ClassicThemeDialog.body(host, 16)
        val preview = ImageView(host).apply {
            contentDescription = host.getString(R.string.classic_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        body.addView(preview, LinearLayout.LayoutParams(ClassicThemeDialog.dp(host, 80), ClassicThemeDialog.dp(host, 80)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        val hex = ClassicThemeDialog.text(host, "", 14f, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, ClassicThemeDialog.dp(host, 8), 0, ClassicThemeDialog.dp(host, 16))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(hex, LinearLayout.LayoutParams(-1, -2))
        val grid = GridLayout(host)
        val cells = mutableListOf<View>()
        fun bind() {
            preview.setImageResource(ids[selected])
            hex.text = OxygenThemeEngine.formatHex(colors[selected])
            cells.forEachIndexed { index, cell ->
                ClassicThemeDialog.selection(host, cell, index == selected)
                cell.background = ClassicThemeDialog.cellBackground(host, index == selected)
            }
        }
        ids.forEachIndexed { index, resource ->
            val cell = FrameLayout(host).apply {
                isFocusable = true
                contentDescription = host.getString(R.string.classic_icon_option, index + 1) + ", " + OxygenThemeEngine.formatHex(colors[index])
                setOnClickListener { selected = index; bind() }
            }
            cell.addView(ImageView(host).apply {
                setImageResource(resource)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(ClassicThemeDialog.dp(host, 48), ClassicThemeDialog.dp(host, 48), Gravity.CENTER))
            cells.add(cell)
            grid.addView(cell)
        }
        var lastColumns = 0
        grid.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val columns = ((right - left) / ClassicThemeDialog.dp(host, 64)).coerceIn(1, 5)
            if (columns != lastColumns) {
                lastColumns = columns
                // Clear old specs before reducing the column count on a narrower window.
                grid.removeAllViews()
                grid.columnCount = columns
                cells.forEachIndexed { index, cell ->
                    cell.layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
                        width = 0; height = ClassicThemeDialog.dp(host, 64)
                        setMargins(ClassicThemeDialog.dp(host, 2), ClassicThemeDialog.dp(host, 2),
                            ClassicThemeDialog.dp(host, 2), ClassicThemeDialog.dp(host, 2))
                        setGravity(Gravity.FILL)
                    }
                    grid.addView(cell)
                }
            }
        }
        // Set initial specs before first measurement; final columns use the measured content width.
        grid.columnCount = 4
        cells.forEachIndexed { index, cell ->
            cell.layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / 4), GridLayout.spec(index % 4, 1f)).apply {
                width = 0; height = ClassicThemeDialog.dp(host, 64)
            }
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        bind()
        val scroll = ClassicThemeDialog.scroll(host, body)
        val dialog = ClassicThemeDialog.show(host, OxygenClassicDialog.builder(host, host.getString(R.string.classic_app_icon))
            .setView(scroll).setNegativeButton(R.string.classic_cancel, null)
            .setPositiveButton(R.string.theme_dialog_apply, null), scroll)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            ClassicThemeDialog.commit(host, dialog) {
                val color = colors[selected]
                if (host.config.appIconColor != color) {
                    host.config.appIconColor = color
                    host.checkAppIconColor()
                }
            }
        }
        return dialog
    }
}
