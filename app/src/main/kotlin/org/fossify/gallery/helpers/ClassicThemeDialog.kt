package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.extensions.config

/** Shared geometry, lifecycle and one-shot commit boundary for the three theme dialogs. */
internal object ClassicThemeDialog {
    fun dp(host: SimpleActivity, value: Int) = (host.resources.displayMetrics.density * value + .5f).toInt()
    fun canOpen(host: SimpleActivity): Boolean = !host.isFinishing && !host.isDestroyed &&
        host.window.decorView.getTag(R.id.classic_theme_dialog_active) == null

    fun body(host: SimpleActivity, padding: Int = 24) = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(host, padding), 0, dp(host, padding), dp(host, 8))
    }

    fun scroll(host: SimpleActivity, body: View) = ClassicDialogScrollView(host).apply {
        id = R.id.classic_theme_dialog_content
        addView(body)
    }

    fun text(host: SimpleActivity, value: CharSequence, size: Float = 16f, secondary: Boolean = false) = TextView(host).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER_VERTICAL
        val tokens = OxygenThemeEngine.snapshot(host, host.config)
        setTextColor(if (secondary) tokens.secondaryText else tokens.primaryText)
    }

    fun selection(host: SimpleActivity, view: View, selected: Boolean) {
        view.isSelected = selected
        androidx.core.view.ViewCompat.setStateDescription(view, host.getString(
            if (selected) R.string.classic_selected else R.string.classic_not_selected))
    }

    fun cellBackground(host: SimpleActivity, selected: Boolean = false): RippleDrawable {
        val tokens = OxygenThemeEngine.snapshot(host, host.config)
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(host, 4).toFloat()
            setColor(color)
        }
        val base = shape(if (selected) OxygenThemeColors.blend(tokens.dialogBackground, tokens.interactiveAccent, .08)
            else tokens.dialogBackground).apply { if (selected) setStroke(dp(host, 2), tokens.interactiveAccent) }
        return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(tokens.interactiveAccent, 28)),
            base, shape(android.graphics.Color.WHITE))
    }

    fun show(host: SimpleActivity, builder: AlertDialog.Builder, content: View): AlertDialog {
        val dialog = OxygenClassicDialog.show(host, builder, content)
        host.window.decorView.setTag(R.id.classic_theme_dialog_active, dialog)
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        host.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            host.lifecycle.removeObserver(observer)
            if (host.window.decorView.getTag(R.id.classic_theme_dialog_active) === dialog)
                host.window.decorView.setTag(R.id.classic_theme_dialog_active, null)
        }
        return dialog
    }

    /** Dismiss the old window before theme updates can recreate its host. */
    fun commit(host: SimpleActivity, dialog: AlertDialog, action: () -> Unit) {
        if (!dialog.isShowing || host.isFinishing || host.isDestroyed) return
        dialog.dismiss()
        action()
    }
}
