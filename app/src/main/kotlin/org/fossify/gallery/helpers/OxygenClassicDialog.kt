package org.fossify.gallery.helpers

import android.app.Activity
import android.view.WindowManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.EditText
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.widget.CompoundButtonCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/**
 * Finishing pass applied after Commons setupDialogStuff() has created/shown
 * an AlertDialog. This does not replace its builders or business callbacks.
 */
object OxygenClassicDialog {
    /** Build a consistent small-radius MD1 dialog independently of Commons M3. */
    fun builder(activity: Activity, title: CharSequence): AlertDialog.Builder {
        val light = ColorUtils.calculateLuminance(activity.getProperBackgroundColor()) >= 0.5
        val themeId = if (light) org.fossify.gallery.R.style.OxygenClassicAlertDialog_Light
            else org.fossify.gallery.R.style.OxygenClassicAlertDialog_Dark
        val context = ContextThemeWrapper(activity, themeId)
        val dp = activity.resources.displayMetrics.density
        val titleView = TextView(context).apply {
            text = title
            setTextColor(activity.getProperTextColor())
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24*dp).toInt(), (20*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
        }
        return AlertDialog.Builder(context).let { builder ->
            if (title.isEmpty()) builder else builder.setCustomTitle(titleView)
        }
    }

    fun show(activity: Activity, builder: AlertDialog.Builder, content: View? = null): AlertDialog {
        val dialog = builder.create()
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        dialog.show()
        apply(activity, dialog, content ?: (dialog.window?.decorView ?: activity.window.decorView))
        dialog.window?.setDimAmount(0.60f)
        return dialog
    }

    fun styleInput(activity: Activity, field: EditText) {
        val surface = activity.getProperBackgroundColor()
        val foreground = activity.getProperTextColor()
        field.setTextColor(foreground)
        field.setHintTextColor(ColorUtils.blendARGB(surface, foreground, 0.58f))
        field.backgroundTintList = ColorStateList.valueOf(activity.getProperPrimaryColor())
    }

    fun apply(activity: Activity, dialog: AlertDialog, content: View) {
        val dp = activity.resources.displayMetrics.density
        val background = activity.getProperBackgroundColor()
        val text = activity.getProperTextColor()
        val accent = activity.getProperPrimaryColor()
        val light = ColorUtils.calculateLuminance(background) >= 0.5
        val surface = if (light) Color.WHITE else ColorUtils.blendARGB(Color.BLACK, background, 0.85f)
        val inactive = ColorUtils.blendARGB(surface, text, 0.55f)
        val disabled = ColorUtils.blendARGB(surface, text, 0.25f)
        val tints = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(disabled, accent, inactive)
        )

        val shape = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 3f * dp
        }
        dialog.window?.setBackgroundDrawable(shape)
        val width = (activity.resources.displayMetrics.widthPixels - 48f * dp).toInt()
            .coerceAtMost((560f * dp).toInt())
        if (width > 0) dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        fun visit(v: View) {
            if (v is RadioButton || v is CheckBox) {
                val button = v as android.widget.CompoundButton
                CompoundButtonCompat.setButtonTintList(button, tints)
                button.minHeight = (48f * dp).toInt()
                (button as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i))
            }
        }
        visit(content)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.apply {
                setTextColor(accent)
                minHeight = (48f * dp).toInt()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
        }
    }
}
