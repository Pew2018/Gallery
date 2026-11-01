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
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.graphics.drawable.InsetDrawable
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
import org.fossify.gallery.extensions.config
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
            setPadding((24*dp).toInt(), (24*dp).toInt(), (24*dp).toInt(), (16*dp).toInt())
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
        // TiebaLite caps the outer layout at 310dp, then applies 16dp on each side
        // BEFORE painting the surface: the visible panel is at most 278dp.
        (minOf(availableWidth.toFloat(), 310f * density) - 32f * density).toInt().coerceAtLeast(1)

    /** Classify dialog widths by their content and scrolling needs. */
    private enum class WidthProfile(val maxDp: Int) {
        COMPACT(278), REORDER(340), OPTIONS(360), SECURITY(360), FORM(400), DETAILS(480), PICKER(560)
    }

    internal fun widthForContent(availableWidth: Int, density: Float, maxWidthDp: Int): Int =
        (availableWidth - 32f * density).toInt()
            .coerceAtMost((maxWidthDp * density).toInt())
            .coerceAtLeast(1)

    /** Dialog.create() inflates the content before prepareWindow() inspects its IDs. */
    private fun widthProfile(dialog: AlertDialog): WidthProfile {
        fun has(vararg ids: Int) = ids.any { dialog.findViewById<View>(it) != null }
        return when {
            // Commons security dialogs need a 250dp minimum pattern/PIN grid.
            has(org.fossify.commons.R.id.dialog_tab_view_pager) -> WidthProfile.SECURITY

            has(
                R.id.filepicker_coordinator,
                R.id.directories_holder,
                R.id.media_grid_holder
            ) -> WidthProfile.PICKER

            has(R.id.properties_scrollview) -> WidthProfile.DETAILS

            has(
                R.id.dialog_folder_sample_holder,
                R.id.dialog_file_style_spacing_holder,
                R.id.aspect_ratio_holder,
                R.id.resize_image_holder,
                R.id.resize_image_with_path_holder,
                R.id.save_as_holder,
                R.id.folder_path_hint,
                R.id.resize_factor_input_layout,
                R.id.export_favorites_wrapper,
                R.id.slideshow_scrollview,
                R.id.playback_speed_holder,
                R.id.exclude_folder_scrollview,
                R.id.classic_dialog_toolbar_content
            ) -> WidthProfile.FORM

            has(
                R.id.sorting_dialog_scrollview,
                R.id.grouping_dialog_scrollview,
                R.id.change_date_time_dialog_scrollview,
                R.id.change_view_type_dialog_scrollview,
                R.id.other_aspect_ratio_dialog_scrollview,
                R.id.filter_media_dialog_holder,
                R.id.manage_bottom_actions_scrollview,
                R.id.manage_extended_details_scrollview
            ) || (dialog.findViewById<android.widget.RadioGroup>(R.id.dialog_radio_group)?.childCount ?: 0) >= 7 -> WidthProfile.OPTIONS

            has(R.id.classic_dialog_navigation_content) -> WidthProfile.REORDER

            else -> WidthProfile.COMPACT
        }
    }

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

    /** Available host height after stable system bars and display cutouts. */
    internal fun availableHeight(activity: Activity): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = activity.windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            val imeBottom = if (metrics.windowInsets.isVisible(android.view.WindowInsets.Type.ime()))
                metrics.windowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom else 0
            return (metrics.bounds.height() - insets.top - maxOf(insets.bottom, imeBottom)).coerceAtLeast(1)
        }
        val host = activity.window.decorView
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(host)?.getInsetsIgnoringVisibility(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        val height = host.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val imeBottom = androidx.core.view.ViewCompat.getRootWindowInsets(host)
            ?.takeIf { it.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) }
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
        return (height - (insets?.top ?: 0) - maxOf(insets?.bottom ?: 0, imeBottom)).coerceAtLeast(1)
    }

    /** Viewport left for scrollable dialog content after fixed title, tabs and actions. */
    internal fun availableContentHeight(activity: Activity, reservedHeightPx: Int): Int {
        return (availableHeight(activity) - reservedHeightPx).coerceAtLeast(0)
    }

    /** Called before WindowManager.addView; later only for actual host geometry changes. */
    fun prepareWindow(activity: Activity, dialog: AlertDialog): WindowPreparation {
        val window = requireNotNull(dialog.window)
        val softInput = window.attributes.softInputMode
        if (softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) {
            window.setSoftInputMode((softInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val available = availableWidth(activity)
        val density = activity.resources.displayMetrics.density
        val profile = widthProfile(dialog)
        val width = if (profile == WidthProfile.COMPACT) widthFor(available, density)
            else widthForContent(available, density, profile.maxDp)
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
            cornerRadius = 4f * activity.resources.displayMetrics.density
        }
        if (window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        if (window.attributes.dimAmount != 0.45f) window.setDimAmount(0.45f)
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

    private fun styleBiometricAction(
        button: Button,
        dp: Float,
        surface: Int,
        dialogText: Int,
        readableAccent: Int
    ) {
        // Cache vertical padding before replacing the platform background.
        val originalTopPadding = button.paddingTop
        val originalBottomPadding = button.paddingBottom
        val originalBackground = button.background
        val originalMinimumWidth = maxOf(
            button.minimumWidth,
            originalBackground?.minimumWidth ?: 0,
            button.measuredWidth
        )
        val originalMinimumHeight = maxOf(
            button.minimumHeight,
            originalBackground?.minimumHeight ?: 0,
            button.measuredHeight
        )

        val outlineColor = ColorUtils.blendARGB(surface, dialogText, 0.34f)
        val outline = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 3f * dp
            setStroke(dp.toInt().coerceAtLeast(1), outlineColor)
        }
        val rippleMask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 3f * dp
        }

        button.backgroundTintList = null
        button.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(readableAccent, 26)),
            outline,
            rippleMask
        )
        // Preserve the size contribution of the original themed button background.
        button.minimumWidth = originalMinimumWidth
        button.minimumHeight = originalMinimumHeight

        button.setText(R.string.favorite_password_use_biometric)
        button.setTextColor(dialogText)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.isAllCaps = false
        button.gravity = Gravity.CENTER

        val icon = AppCompatResources.getDrawable(
            button.context,
            org.fossify.commons.R.drawable.ic_fingerprint_vector
        )?.mutate()
        icon?.let {
            val iconSize = (20f * dp).toInt().coerceAtLeast(1)
            it.setTint(readableAccent)
            it.setBounds(0, 0, iconSize, iconSize)
            button.setCompoundDrawablesRelative(it, null, null, null)
            button.compoundDrawablePadding = (8f * dp).toInt()
        }

        val horizontalPadding = (16f * dp).toInt()
        button.setPadding(
            horizontalPadding,
            originalTopPadding,
            horizontalPadding,
            originalBottomPadding
        )

        button.elevation = 0f
        button.translationZ = 0f
        button.stateListAnimator = null
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
                when (v.id) {
                    androidx.appcompat.R.id.topPanel -> v.setPadding(0, 0, 0, 0)
                    androidx.appcompat.R.id.title_template -> v.setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
                    androidx.appcompat.R.id.buttonPanel -> v.setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), (12 * dp).toInt())
                }
                if (v.id == org.fossify.commons.R.id.open_biometric_dialog && v is Button) {
                    styleBiometricAction(
                        button = v,
                        dp = dp,
                        surface = surface,
                        dialogText = dialogText,
                        readableAccent = readableAccent
                    )
                }
                if (v is TextInputLayout) {
                    // Preserve MyTextInputLayout's public API and error/validation behavior.
                    v.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    v.setBoxCornerRadii(4 * dp, 4 * dp, 4 * dp, 4 * dp)
                    v.boxStrokeWidth = dp.toInt().coerceAtLeast(1)
                    v.boxStrokeWidthFocused = (2 * dp).toInt()
                    v.boxStrokeColor = readableAccent
                    v.defaultHintTextColor = ColorStateList.valueOf(inactive)
                    v.hintTextColor = ColorStateList.valueOf(readableAccent)
                }
                if (v is EditText && v.id != org.fossify.commons.R.id.top_toolbar_search) {
                    // The picker search owns a single OxygenOS underline; the
                    // global EditText skin would paint a second, competing rule.
                    v.background = AppCompatResources.getDrawable(v.context, androidx.appcompat.R.drawable.abc_edit_text_material)
                    styleInput(activity, v)
                    v.textSize = 16f
                    val input = (v.parent as? View)?.parent as? TextInputLayout
                    if (input != null) {
                        v.background = null
                        v.minimumHeight = (56 * dp).toInt()
                    }
                }
                if (v is android.widget.SeekBar) {
                    OxygenClassicControls.applySwitches(v, OxygenThemeEngine.snapshot(activity, activity.config))
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
                if (v is android.widget.CheckedTextView) {
                    val list = v.parent as? android.widget.AbsListView
                    val multiple = list != null && list.choiceMode != android.widget.AbsListView.CHOICE_MODE_SINGLE
                    val mark = if (multiple) OxygenClassicControls.selectionDrawable(false, dp, OxygenThemeEngine.snapshot(activity, activity.config))
                        else AppCompatResources.getDrawable(v.context, R.drawable.tieba_choice_check)!!.mutate().apply { setTint(readableAccent) }
                    v.checkMarkDrawable = mark
                    v.checkMarkTintList = if (multiple) tints else ColorStateList.valueOf(readableAccent)
                    v.textSize = 16f
                    v.typeface = Typeface.create("sans-serif", if (multiple) Typeface.NORMAL else Typeface.BOLD)
                    v.minHeight = (56 * dp).toInt()
                    v.setSingleLine(false)
                    v.setPaddingRelative((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
                    v.setTextColor(ColorStateList(
                        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(disabled, readableAccent, dialogText)
                    ))
                    v.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(readableAccent, 24)),
                        TiebaDialogChoices.selectedBackground(readableAccent, surface), android.graphics.drawable.ColorDrawable(Color.WHITE))
                }
            }
            if (needsStyle && (v is RadioButton || v is CheckBox)) {
                val button = v as android.widget.CompoundButton
                button.buttonDrawable = OxygenClassicControls.selectionDrawable(
                    v is RadioButton, dp, org.fossify.gallery.helpers.OxygenThemeEngine.snapshot(activity, activity.config)
                )
                CompoundButtonCompat.setButtonTintList(button, tints)
                if (v.id == R.id.classic_toolbar_action_checkbox) {
                    // Reserve genuine space between checkbox and its text.
                    button.buttonDrawable = InsetDrawable(button.buttonDrawable, 0, 0, (12 * dp).toInt(), 0)
                }
                button.background = ripple(dialogText, dp)
                button.isAllCaps = false
                button.minHeight = (48f * dp).toInt()
                button.compoundDrawablePadding = (16 * dp).toInt()
                if (v is CheckBox) {
                    val insets = TiebaDialogChoices.remainingInsets(v, (24 * dp).toInt())
                    v.setPaddingRelative(insets.first, v.paddingTop, insets.second, v.paddingBottom)
                }
                (button as TextView).setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                button.setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(disabled, dialogText)
                ))
                if (v is RadioButton && v.parent is android.widget.RadioGroup) {
                    TiebaDialogChoices.apply(v, readableAccent, dialogText, surface)
                }
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
                    intArrayOf(disabled, if (which == AlertDialog.BUTTON_POSITIVE) readableAccent else dialogText)
                ))
                minHeight = (48f * dp).toInt()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isAllCaps = false
                minWidth = (64 * dp).toInt()
                setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                this.background = ripple(readableAccent, dp)
            }
        }
        // AppCompat places a flex spacer after the neutral button. Properties
        // should keep Remove EXIF and OK together at the trailing edge.
        val propertiesActions = dialog.findViewById<View>(R.id.properties_scrollview) != null &&
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null
        val exifMessage = dialog.findViewById<TextView>(org.fossify.commons.R.id.message)
        val exifConfirmation = exifMessage?.text?.toString() ==
            activity.getString(org.fossify.commons.R.string.remove_exif_confirmation)
        if (propertiesActions || exifConfirmation) {
            // AppCompat's buttonPanel can be a wrapper. The actual horizontal
            // ButtonBarLayout is the parent of the neutral/negative action.
            val leadingAction = dialog.getButton(
                if (propertiesActions) AlertDialog.BUTTON_NEUTRAL else AlertDialog.BUTTON_NEGATIVE
            )
            val actionRow = leadingAction?.parent as? LinearLayout
            actionRow?.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            actionRow?.findViewById<View>(androidx.appcompat.R.id.spacer)?.visibility = View.GONE
            if (exifConfirmation) {
                listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE).forEach { which ->
                    dialog.getButton(which)?.setPadding(
                        (12 * dp).toInt(), (12 * dp).toInt(),
                        (12 * dp).toInt(), (12 * dp).toInt()
                    )
                }
            }
        }
        return StyleResult(windowChanged, content.isLayoutRequested)
    }
}
