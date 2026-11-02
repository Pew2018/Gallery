package org.fossify.gallery.dialogs

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.helpers.ClassicDialogScrollView
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.photoDialogColors

/** Presentation only: callers retain credential types, request leases and authentication. */
internal object ClassicPasswordMethodDialog {
    fun show(
        activity: Activity,
        @StringRes title: Int,
        labels: List<String>,
        @StringRes description: Int? = null,
        canChoose: () -> Boolean = { true },
        onCancel: () -> Unit,
        onChoice: (Int) -> Unit
    ): AlertDialog {
        val palette = photoDialogColors(activity)
        val builder = OxygenClassicDialog.builder(activity, activity.getString(title))
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + .5f).toInt()
        val body = LinearLayout(builder.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        description?.let { resource ->
            body.addView(TextView(builder.context).apply {
                setText(resource)
                textSize = 14f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(8))
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        lateinit var dialog: AlertDialog
        var delivered = false
        fun cancel() {
            if (!delivered) {
                delivered = true
                onCancel()
            }
        }
        labels.forEachIndexed { index, label ->
            body.addView(TextView(builder.context).apply {
                text = label
                textSize = 16f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = dp(48)
                setPadding(0, dp(8), 0, dp(8))
                setTextColor(palette.text)
                background = OxygenClassicDialog.ripple(palette.accent, density)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (delivered || !canChoose()) return@setOnClickListener
                    delivered = true
                    dialog.dismiss()
                    onChoice(index)
                }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val scroll = ClassicDialogScrollView(builder.context).apply { addView(body) }
        builder.setView(scroll).setOnCancelListener { cancel() }
        // Initial selection retains its original outside/back cancellation; fallback also has Cancel.
        if (description != null) builder.setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> cancel() }
        dialog = OxygenClassicDialog.show(activity, builder, scroll)
        return dialog
    }
}
