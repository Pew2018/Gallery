package org.fossify.gallery.activities

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*

/** Flat classic settings. Rebind colors/selection in place; rebuild only on page navigation. */
class OxygenThemeActivity : SimpleActivity() {
    companion object {
        const val EXTRA_ICON_COLORS = "org.fossify.gallery.extra.ICON_COLORS"
    }

    private var iconColorPage = false
    private lateinit var root: LinearLayout
    private lateinit var titleBar: LinearLayout
    private lateinit var backIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private val binders = mutableListOf<() -> Unit>()
    private var moreColors = false
    private var mainScroll = 0
    private val colors get() = OxygenThemeEngine.snapshot(this, config)
    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun label(preset: OxygenPreset): String {
        val id = when (preset.id) {
            "oxygen_blue" -> R.string.theme_oxygen_blue
            "oxygen_deep_sea_blue" -> R.string.theme_oxygen_deep_sea_blue
            "oxygen_golden" -> R.string.theme_oxygen_golden
            "oxygen_lemon_yellow" -> R.string.theme_oxygen_lemon_yellow
            "oxygen_grass_green" -> R.string.theme_oxygen_grass_green
            "oxygen_jade_green" -> R.string.theme_oxygen_jade_green
            "oxygen_charm_purple" -> R.string.theme_oxygen_charm_purple
            "oxygen_indigo_blue" -> R.string.theme_oxygen_indigo_blue
            "oxygen_sky_blue" -> R.string.theme_oxygen_sky_blue
            "oxygen_vigour_red" -> R.string.theme_oxygen_vigour_red
            "oxygen_fashion_pink" -> R.string.theme_oxygen_fashion_pink
            "material_blue" -> R.string.theme_material_blue
            "material_teal" -> R.string.theme_material_teal
            "material_green" -> R.string.theme_material_green
            "material_red" -> R.string.theme_material_red
            "material_orange" -> R.string.theme_material_orange
            "material_purple" -> R.string.theme_material_purple
            "material_cyan" -> R.string.theme_material_cyan
            "material_indigo" -> R.string.theme_material_indigo
            "material_pink" -> R.string.theme_material_pink
            "material_blue_grey" -> R.string.theme_material_blue_grey
            "material_deep_orange" -> R.string.theme_material_deep_orange
            "material_light_green" -> R.string.theme_material_light_green
            else -> return preset.label
        }
        return getString(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iconColorPage = intent.getBooleanExtra(EXTRA_ICON_COLORS, false)
        moreColors = savedInstanceState?.getBoolean("theme_more") ?: false
        mainScroll = savedInstanceState?.getInt("theme_main_scroll") ?: 0
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        backIcon = ImageView(this).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_arrow_left_vector)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.classic_back)
            isFocusable = true
            setOnClickListener { goBack() }
        }
        titleText = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleBar.addView(backIcon, LinearLayout.LayoutParams(dp(48), dp(56)))
        titleBar.addView(titleText, LinearLayout.LayoutParams(0, -1, 1f).apply {
            marginStart = dp(16); marginEnd = dp(16)
        })
        root.addView(titleBar, LinearLayout.LayoutParams(-1, dp(56)))
        scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(system.left, system.top, system.right, system.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        buildPage()
        val y = savedInstanceState?.getInt("classic_theme_scroll") ?: 0
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun goBack() {
        if (!moreColors) { finish(); return }
        navigateColors(false)
    }

    private fun navigateColors(open: Boolean) {
        if (moreColors == open) return
        // Back must be available even while the entrance animation is running.
        scroll.animate().cancel()
        if (open) mainScroll = scroll.scrollY
        moreColors = open
        buildPage()
        scroll.scrollTo(0, if (open) 0 else mainScroll)
        // A short content transition keeps the toolbar fixed and respects disabled animations.
        val animationsEnabled = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
        if (!animationsEnabled) {
            scroll.translationX = 0f
            scroll.alpha = 1f
            return
        }
        val distance = dp(12).toFloat() * if (open) 1 else -1
        scroll.translationX = distance
        scroll.alpha = 0.72f
        scroll.animate().translationX(0f).alpha(1f).setDuration(150L).start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("theme_more", moreColors)
        outState.putInt("theme_main_scroll", mainScroll)
        outState.putInt("classic_theme_scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) bindPage()
    }

    private fun change(action: () -> Unit) {
        action()
        // refreshThemeIfNeeded applies Commons colors exactly once and recreates only
        // when the appearance resources change. Accent-only edits stay in place.
        if (!refreshThemeIfNeeded()) bindPage()
    }

    private fun activatePreset(preset: OxygenPreset) = change {
        config.oxygenThemePresetId = preset.id
        config.oxygenThemeActivated = true
    }

    private fun text(value: CharSequence, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; gravity = Gravity.CENTER_VERTICAL
    }

    private fun section(id: Int) {
        val label = text(getString(id), 12f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }
        body.addView(label, LinearLayout.LayoutParams(-1, -2))
        binders.add { label.setTextColor(colors.interactiveAccent) }
    }

    private fun divider() {
        val line = View(this)
        body.addView(line, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8) })
        binders.add { line.setBackgroundColor(OxygenThemeColors.blend(colors.pageBackground, colors.primaryText, 0.10)) }
    }

    /** A masked, bounded ripple: never an unbounded halo behind color swatches. */
    private fun ripple(view: View) {
        val tint = ColorUtils.setAlphaComponent(colors.interactiveAccent, 28)
        view.background = RippleDrawable(
            ColorStateList.valueOf(tint),
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
            android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        )
    }

    private fun actionRow(id: Int, action: () -> Unit) {
        val row = text(getString(id)).apply {
            minimumHeight = dp(56)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            setOnClickListener { action() }
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
        binders.add { row.setTextColor(colors.primaryText); ripple(row) }
    }

    private fun radioRow(label: String, selected: () -> Boolean, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(4), dp(16), dp(4))
            isClickable = true
            setOnClickListener { action() }
        }
        val radio = AppCompatRadioButton(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { action() }
        }
        row.addView(radio, LinearLayout.LayoutParams(-1, -2))
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
        binders.add {
            radio.isChecked = selected()
            radio.setTextColor(colors.primaryText)
            ripple(row)
        }
    }

    /** Same cells, dimensions and selection semantics on both palette pages. */
    private fun colorGrid(presets: List<OxygenPreset>, showCurrentColor: Boolean = false) {
        val columns = OxygenThemePresets.columnsForWidth(
            resources.configuration.screenWidthDp.coerceAtLeast(240),
            resources.configuration.fontScale
        )
        val grid = GridLayout(this).apply {
            columnCount = columns
            setPadding(dp(16), 0, dp(16), 0)
        }
        presets.forEachIndexed { index, preset ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(88)
                isClickable = true
                isFocusable = true
                setPadding(dp(3), dp(8), dp(3), dp(8))
                contentDescription = label(preset)
                setOnClickListener { activatePreset(preset) }
            }
            val swatch = text("", 18f).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)))
            val name = text(label(preset), 11f).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(name, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(5)
            })
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = -2
                setGravity(Gravity.FILL)
            })
            binders.add {
                val tokens = colors
                val selected = OxygenThemePresets.isSelected(
                    config.oxygenThemeActivated, config.oxygenThemePresetId, preset.id
                )
                cell.isSelected = selected
                ViewCompat.setStateDescription(cell, getString(
                    if (selected) R.string.classic_selected else R.string.classic_not_selected
                ))
                swatch.text = if (selected) "✓" else ""
                swatch.setTextColor(OxygenThemeColors.autoForeground(preset.color))
                swatch.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.color)
                    setStroke(
                        dp(if (selected) 2 else 1),
                        if (selected) tokens.primaryText else
                            OxygenThemeColors.blend(preset.color, tokens.primaryText, 0.23)
                    )
                }
                name.setTextColor(tokens.primaryText)
                ripple(cell)
            }
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        if (showCurrentColor) {
            val current = text("", 12f).apply {
                setPadding(dp(16), dp(6), dp(16), dp(8))
            }
            body.addView(current)
            binders.add {
                val active = config.oxygenThemeActivated
                val preset = OxygenThemePresets.byId[config.oxygenThemePresetId]
                val currentName = when {
                    !active -> getString(R.string.classic_original_colors)
                    preset != null -> label(preset)
                    else -> getString(R.string.classic_custom_color) +
                        " (" + OxygenThemeEngine.formatHex(config.oxygenCustomAccent) + ")"
                }
                current.text = getString(R.string.classic_current_color, currentName)
                current.setTextColor(colors.secondaryText)
            }
        }
    }

    private fun buildPage() {
        binders.clear()
        body.removeAllViews()
        titleText.setText(when {
            iconColorPage -> R.string.classic_app_icon
            moreColors -> R.string.classic_more_colors
            else -> R.string.classic_theme_title
        })
        if (iconColorPage) {
            buildIconColorPage()
        } else if (moreColors) {
            section(R.string.classic_palette_classic)
            colorGrid(OxygenThemePresets.group("classic"))
            divider()
            section(R.string.classic_palette_material)
            colorGrid(OxygenThemePresets.group("material"))
        } else {
            section(R.string.classic_appearance)
            listOf("system" to R.string.classic_system, "light" to R.string.classic_light,
                "dark" to R.string.classic_dark).forEach { (mode, id) ->
                radioRow(getString(id), { config.oxygenThemeActivated &&
                    config.oxygenAppearanceMode == mode }) {
                    change { config.oxygenAppearanceMode = mode; config.oxygenThemeActivated = true }
                }
            }
            divider()
            section(R.string.classic_accent)
            colorGrid(OxygenThemePresets.featured, showCurrentColor = true)
            divider()
            section(R.string.classic_other)
            actionRow(R.string.classic_more_colors) { navigateColors(true) }
            actionRow(R.string.classic_custom_color) { chooseColor() }
            actionRow(R.string.classic_restore_default) {
                activatePreset(OxygenThemePresets.byId.getValue(OxygenThemePresets.DEFAULT_ID))
            }
        }
        bindPage()
    }

    private fun buildIconColorPage() {
        section(R.string.classic_app_icon)

        val iconIds = getAppIconIDs()
        val iconColors = getAppIconColors()
        val currentIndex = iconColors.indexOf(config.appIconColor).coerceAtLeast(0)
        val preview = ImageView(this).apply {
            setImageResource(iconIds.getOrElse(currentIndex) { iconIds.first() })
            contentDescription = getString(R.string.classic_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        body.addView(preview, LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        val hex = text(OxygenThemeEngine.formatHex(config.appIconColor), 12f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(12))
        }
        body.addView(hex, LinearLayout.LayoutParams(-1, -2))
        binders.add { hex.setTextColor(colors.secondaryText) }

        val columns = ((resources.configuration.screenWidthDp - 48) / 68).coerceIn(3, 5)
        val grid = GridLayout(this).apply {
            columnCount = columns
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        iconIds.forEachIndexed { index, iconId ->
            val color = iconColors.getOrNull(index) ?: return@forEachIndexed
            val cell = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.classic_icon_option, index + 1)
                setOnClickListener {
                    config.appIconColor = color
                    checkAppIconColor()
                    buildPage()
                }
            }
            val icon = ImageView(this).apply {
                setImageResource(iconId)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            cell.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = dp(64)
                setMargins(dp(2), dp(2), dp(2), dp(2))
                setGravity(Gravity.FILL)
            })
            binders.add {
                val tokens = colors
                val selected = config.appIconColor == color
                cell.isSelected = selected
                ViewCompat.setStateDescription(cell, getString(
                    if (selected) R.string.classic_selected else R.string.classic_not_selected
                ))
                cell.background = GradientDrawable().apply {
                    setColor(if (selected) OxygenThemeColors.blend(
                        tokens.pageBackground, tokens.interactiveAccent, 0.08
                    ) else tokens.pageBackground)
                    cornerRadius = dp(3).toFloat()
                    if (selected) setStroke(dp(2), tokens.interactiveAccent)
                }
                cell.foreground = RippleDrawable(
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(tokens.interactiveAccent, 28)),
                    null,
                    GradientDrawable().apply {
                        setColor(android.graphics.Color.WHITE)
                        cornerRadius = dp(3).toFloat()
                    }
                )
            }
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
    }

    private fun bindPage() {
        val tokens = colors
        root.setBackgroundColor(tokens.pageBackground)
        body.setBackgroundColor(tokens.pageBackground)
        OxygenToolbarStyle.applyStandaloneToolbar(this, titleBar, backIcon, titleText)
        binders.forEach { it() }
        OxygenClassicControls.applySwitches(body, tokens)
        OxygenSystemBars.apply(this)
    }

    private fun chooseColor() {
        val tokens = colors
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val preview = View(this)
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            hint = "#RRGGBB"
            contentDescription = getString(R.string.classic_custom_color)
            setText(OxygenThemeEngine.formatHex(config.oxygenCustomAccent)); selectAll()
            setTextColor(tokens.primaryText); setHintTextColor(tokens.secondaryText)
            backgroundTintList = ColorStateList.valueOf(tokens.interactiveAccent)
        }
        container.addView(preview, LinearLayout.LayoutParams(-1, dp(32)))
        container.addView(field, LinearLayout.LayoutParams(-1, -2))
        preview.setBackgroundColor(config.oxygenCustomAccent)
        field.doAfterTextChanged {
            OxygenThemeEngine.parseHex(it.toString().trim())?.let { color -> preview.setBackgroundColor(color) }
        }
        val builder = OxygenClassicDialog.builder(this, getString(R.string.classic_custom_color))
            .setView(container).setNegativeButton(R.string.classic_cancel, null)
            .setPositiveButton(R.string.classic_save, null).setNeutralButton(R.string.classic_restore_default, null)
        val dialog = OxygenClassicDialog.show(this, builder, container)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val parsed = OxygenThemeEngine.parseHex(field.text.toString().trim())
            if (parsed == null) field.error = getString(R.string.classic_invalid_color)
            else {
                change {
                    config.oxygenCustomAccent = parsed; config.oxygenThemePresetId = "custom"
                    config.oxygenThemeActivated = true
                }
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            field.setText(OxygenThemePresets.byId.getValue(OxygenThemePresets.DEFAULT_ID).hex); field.selectAll()
        }
    }
}
