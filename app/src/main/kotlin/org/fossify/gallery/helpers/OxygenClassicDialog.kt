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
        val dialog = ClassicCommonsHooks.prepareBuilder(builder).create()
        ClassicCommonsHooks.beforeDialogShow(activity, dialog, content)
        dialog.show()
        // Immediate control availability is retained for callers configuring actions.
        // This pass never changes window width or soft-input mode.
        apply(activity, dialog, dialog.window!!.decorView)
        ClassicCommonsHooks.onDialogShown(activity, dialog, content)
        return dialog
    }

    data class StyleResult(val windowChanged: Boolean = false, val layoutRequested: Boolean = false)
    data class WindowPreparation(val width: Int, val geometryChanged: Boolean)
    private data class SurfaceState(val colors: List<Int>, val drawable: android.graphics.drawable.Drawable?)

    internal fun widthFor(availableWidth: Int, density: Float): Int =
        (availableWidth - 48f * density).toInt().coerceAtLeast(1)
            .coerceAtMost((560f * density).toInt().coerceAtLeast(1))

    /** Window bounds/system insets exclude IME geometry, including in multi-window mode. */
    internal fun availableWidth(activity: Activity): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = activity.windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            return (metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1)
        }
        val host = activity.window.decorView
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(host)?.getInsetsIgnoringVisibility(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        val width = host.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        return (width - (insets?.left ?: 0) - (insets?.right ?: 0)).coerceAtLeast(1)
    }

    /** Called before WindowManager.addView; later only for actual host geometry changes. */
    fun prepareWindow(activity: Activity, dialog: AlertDialog): WindowPreparation {
        val window = requireNotNull(dialog.window)
        val softInput = window.attributes.softInputMode
        if (softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
            window.setSoftInputMode((softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val width = widthFor(availableWidth(activity), activity.resources.displayMetrics.density)
        val changed = window.attributes.width != width
        if (changed) window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        applySurface(activity, dialog)
        return WindowPreparation(width, changed)
    }

    private fun applySurface(activity: Activity, dialog: AlertDialog): Boolean {
        val window = dialog.window ?: return false
        // Also enforce screenshot protection on already-shown dialogs. Some callers
        // enter through apply() rather than prepareWindow(), so doing this in the
        // shared surface pass closes that gap without changing window geometry.
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val decor = window.decorView
        val light = ColorUtils.calculateLuminance(activity.getProperBackgroundColor()) >= 0.5
        val surface = if (light) Color.WHITE else Color.rgb(48, 48, 48)
        val accent = OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), surface)
        val palette = listOf(surface, accent)
        val state = decor.getTag(R.id.classic_dialog_palette) as? SurfaceState
        if (state != null && state.colors == palette && state.drawable === decor.background) return false
        val shape = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 3f * activity.resources.displayMetrics.density
        }
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        if (window.attributes.dimAmount != 0.60f) window.setDimAmount(0.60f)
        window.setBackgroundDrawable(shape)
        // Record the actual decor drawable; platforms may wrap the supplied background.
        decor.setTag(R.id.classic_dialog_palette, SurfaceState(palette, decor.background))
        return true
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

    fun apply(activity: Activity, dialog: AlertDialog, content: View): StyleResult {
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

        val windowChanged = applySurface(activity, dialog)
        val palette = listOf(surface, readableAccent)

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
        return StyleResult(windowChanged, content.isLayoutRequested)
    }
}
