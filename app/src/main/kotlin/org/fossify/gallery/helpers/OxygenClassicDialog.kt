package org.fossify.gallery.helpers

import android.app.Activity
import android.view.WindowManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.EditText
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
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
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.tabs.TabLayout
import android.os.Build
import org.fossify.gallery.R
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor

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
            setTextColor(if (light) Color.rgb(33, 33, 33) else Color.WHITE)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24*dp).toInt(), (20*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
        }
        return AlertDialog.Builder(context).let { builder ->
            if (title.isEmpty()) builder else builder.setCustomTitle(titleView)
        }
    }

    fun show(activity: Activity, builder: AlertDialog.Builder, content: View? = null): AlertDialog {
        val dialog = builder.create()
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 &&
            dialog.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) == 0) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        dialog.show()
        apply(activity, dialog, dialog.window?.decorView ?: content ?: activity.window.decorView)
        ClassicCommonsHooks.onDialogShown(activity, dialog, content ?: dialog.window!!.decorView)
        return dialog
    }

    fun ripple(color: Int, density: Float, fill: Int = Color.TRANSPARENT): RippleDrawable {
        fun shape(value: Int) = GradientDrawable().apply {
            setColor(value)
            cornerRadius = 3f * density
        }
        return RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 24)),
            shape(fill), shape(Color.WHITE))
    }

    fun styleInput(activity: Activity, field: EditText) {
        val light = ColorUtils.calculateLuminance(activity.getProperBackgroundColor()) >= 0.5
        val surface = if (light) Color.WHITE else Color.rgb(48, 48, 48)
        val foreground = if (light) Color.rgb(33, 33, 33) else Color.WHITE
        val accent = OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), surface)
        val neutral = ColorUtils.blendARGB(surface, foreground, 0.55f)
        field.setTextColor(foreground)
        field.setHintTextColor(neutral)
        field.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(ColorUtils.blendARGB(surface, foreground, 0.30f), accent, neutral)
        )
        if (Build.VERSION.SDK_INT >= 29) field.textCursorDrawable?.mutate()?.setTint(accent)
    }

    fun apply(activity: Activity, dialog: AlertDialog, content: View) {
        val dp = activity.resources.displayMetrics.density
        val background = activity.getProperBackgroundColor()
        val accent = activity.getProperPrimaryColor()
        val light = ColorUtils.calculateLuminance(background) >= 0.5
        val surface = if (light) Color.WHITE else Color.rgb(48, 48, 48)
        val readableAccent = OxygenClassicPalette.readableAccent(accent, surface)
        val dialogText = if (light) Color.rgb(33, 33, 33) else Color.WHITE
        val inactive = ColorUtils.blendARGB(surface, dialogText, 0.55f)
        val disabled = ColorUtils.blendARGB(surface, dialogText, 0.38f)
        val tints = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(disabled, readableAccent, inactive)
        )

        val shape = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 3f * dp
        }
        // Commons dialogs use apply() directly; preserve host screenshot protection.
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 &&
            dialog.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) == 0) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val palette = listOf(surface, readableAccent)
        val decor = dialog.window?.decorView
        if (decor?.getTag(R.id.classic_dialog_palette) != palette) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window?.setDimAmount(0.60f)
            dialog.window?.setBackgroundDrawable(shape)
            decor?.setTag(R.id.classic_dialog_palette, palette)
        }
        val softInput = dialog.window?.attributes?.softInputMode ?: 0
        if (softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
            dialog.window?.setSoftInputMode((softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val visible = android.graphics.Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(visible)
        val safeWidth = visible.width().takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val width = (safeWidth - 48f * dp).toInt()
            .coerceAtMost((560f * dp).toInt())
        if (width > 0 && dialog.window?.attributes?.width != width) {
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Preserve semantic, disabled, link and hint text: only skin actual controls.
        fun visit(v: View) {
            val needsStyle = v.getTag(R.id.classic_control_styled) != palette
            if (needsStyle) {
                v.setTag(R.id.classic_control_styled, palette)
                if (v is TextInputLayout) {
                    // Preserve MyTextInputLayout's public API and error/validation behavior.
                    v.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
                    v.setBoxCornerRadii(0f, 0f, 0f, 0f)
                    v.defaultHintTextColor = ColorStateList.valueOf(inactive)
                    v.hintTextColor = ColorStateList.valueOf(readableAccent)
                }
                if (v is EditText) {
                    v.background = AppCompatResources.getDrawable(v.context, androidx.appcompat.R.drawable.abc_edit_text_material)
                    styleInput(activity, v)
                }
                if (v is TabLayout) {
                    v.setBackgroundColor(surface)
                    v.setTabTextColors(inactive, readableAccent)
                    v.setSelectedTabIndicatorColor(readableAccent)
                    v.setSelectedTabIndicatorHeight((2f * dp).toInt())
                    v.tabRippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(dialogText, 24))
                    v.setUnboundedRipple(false)
                }
                if (v is FloatingActionButton) {
                    v.shapeAppearanceModel = v.shapeAppearanceModel.toBuilder()
                        .setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                    v.customSize = (56f * dp).toInt()
                    v.setMaxImageSize((24f * dp).toInt())
                }
                if (v is TextView && v !is EditText && v !is android.widget.Button) {
                    when (v.id) {
                        org.fossify.commons.R.id.property_label -> {
                            v.alpha = 1f
                            v.setTextColor(ColorUtils.blendARGB(surface, dialogText, 0.65f))
                            v.textSize = 14f
                        }
                        org.fossify.commons.R.id.property_value -> {
                            v.setTextColor(dialogText)
                            v.textSize = 16f
                            v.setSingleLine(false)
                        }
                        android.R.id.message, org.fossify.commons.R.id.message -> { v.setTextColor(dialogText); v.textSize = 16f }
                        org.fossify.commons.R.id.dialog_title_textview, androidx.appcompat.R.id.alertTitle -> {
                            v.setTextColor(dialogText)
                            v.textSize = 20f
                            v.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        }
                    }
                }
            }
            if (needsStyle && (v is RadioButton || v is CheckBox)) {
                val button = v as android.widget.CompoundButton
                button.buttonDrawable = AppCompatResources.getDrawable(button.context,
                    if (v is RadioButton) androidx.appcompat.R.drawable.abc_btn_radio_material
                    else androidx.appcompat.R.drawable.abc_btn_check_material)
                CompoundButtonCompat.setButtonTintList(button, tints)
                button.background = ripple(dialogText, dp)
                button.isAllCaps = false
                button.minHeight = (48f * dp).toInt()
                (button as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                button.setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(disabled, dialogText)
                ))
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i))
            }
        }
        visit(content)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.apply {
                if (getTag(R.id.classic_dialog_action) == palette) return@apply
                setTag(R.id.classic_dialog_action, palette)
                setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(disabled, readableAccent)
                ))
                minHeight = (48f * dp).toInt()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isAllCaps = true
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                this.background = ripple(readableAccent, dp)
            }
        }
    }
}
