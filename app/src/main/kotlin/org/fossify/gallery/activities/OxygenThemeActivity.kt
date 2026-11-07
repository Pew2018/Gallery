package org.fossify.gallery.activities

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*

/** Flat classic theme page. Color edits rebind in place; additional editors use shared dialogs. */
class OxygenThemeActivity : SimpleActivity() {
    private var initialNavigation = false
    private var returningToSettings = false
    private val transitionSession get() = intent.getStringExtra(ClassicSettingsActivityTransition.EXTRA_SESSION)
    private lateinit var root: LinearLayout
    private lateinit var titleBar: LinearLayout
    private lateinit var backIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var body: org.fossify.gallery.views.TiebaSettingsContainer
    private val binders = mutableListOf<() -> Unit>()
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
        initialNavigation = savedInstanceState == null
        if (transitionSession != null) ClassicSettingsActivityTransition.suppressWindowAnimation(this)
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
        scroll = org.fossify.gallery.views.ClassicGlowScrollView(this).apply { isFillViewport = true }
        body = org.fossify.gallery.views.TiebaSettingsContainer(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(16)) }
        scroll.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL))
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
        scroll.doOnLayout { scroll.scrollTo(0, y) }
    }

    private fun goBack() {
        if (returningToSettings) return
        returningToSettings = true
        ClassicSettingsPageTransition.cancel(scroll, body)
        transitionSession?.let { session ->
            ClassicSettingsActivityTransition.stage(
                "settings:$session", scroll, titleText.text, false, OxygenClassicMotion.enabled(this)
            )
        }
        finish()
        if (transitionSession != null) ClassicSettingsActivityTransition.suppressWindowAnimation(this)
    }

    override fun onPause() {
        ClassicSettingsPageTransition.cancel(scroll, body)
        super.onPause()
    }

    override fun onDestroy() {
        if (::scroll.isInitialized) {
            ClassicSettingsPageTransition.cancel(scroll, body)
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("classic_theme_scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) bindPage()
        if (initialNavigation) {
            initialNavigation = false
            transitionSession?.let { session ->
                ClassicSettingsActivityTransition.receive(
                    "theme:$session", scroll, body, OxygenClassicMotion.enabled(this),
                    { titleText.text = it }, titleText.text
                )
            }
        }
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
        val label = TextView(this, null, 0, R.style.OxygenClassicSettingsSection).apply {
            setText(id)
        }
        body.addView(label, LinearLayout.LayoutParams(-1, -2))
        binders.add { label.setTextColor(colors.interactiveAccent) }
    }

    /** A masked, bounded ripple: never an unbounded halo behind color swatches. */
    private fun ripple(view: View) {
        ClassicSettingsPresentation.rowRipple(view, colors)
    }

    private fun actionRow(id: Int, action: () -> Unit) {
        val row = text(getString(id)).apply {
            minimumHeight = dp(56)
            setPadding(dp(16), dp(8), dp(16), dp(8))
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
            isFocusable = true
            setOnClickListener { action() }
        }
        val name = text(label)
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val radio = AppCompatRadioButton(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, 0, 0)
            minWidth = dp(24)
            minHeight = dp(24)
        }
        row.contentDescription = label
        row.addView(radio, LinearLayout.LayoutParams(dp(24), dp(24)))
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
        binders.add {
            radio.isChecked = selected()
            row.isSelected = radio.isChecked
            name.setTextColor(colors.primaryText)
            ViewCompat.setStateDescription(row, getString(
                if (radio.isChecked) R.string.classic_selected else R.string.classic_not_selected
            ))
            ripple(row)
        }
    }

    /** TiebaLite ThemePresetRow: 28dp swatch, 16dp gap and one trailing 24dp radio. */
    private fun colorGrid(presets: List<OxygenPreset>, showCurrentColor: Boolean = false) {
        presets.forEach { preset ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                isClickable = true
                isFocusable = true
                contentDescription = label(preset)
                setOnClickListener { activatePreset(preset) }
            }
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.color)
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(swatch, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(16) })
            val name = text(label(preset))
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val radio = AppCompatRadioButton(this).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, 0, 0, 0)
                minWidth = dp(24)
                minHeight = dp(24)
            }
            row.addView(radio, LinearLayout.LayoutParams(dp(24), dp(24)))
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
            binders.add {
                val selected = OxygenThemePresets.isSelected(
                    config.oxygenThemeActivated, config.oxygenThemePresetId, preset.id
                )
                row.isSelected = selected
                radio.isChecked = selected
                ViewCompat.setStateDescription(row, getString(
                    if (selected) R.string.classic_selected else R.string.classic_not_selected
                ))
                name.setTextColor(colors.primaryText)
                ripple(row)
            }
        }
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

    private fun updatePageTitle() {
        titleText.setText(R.string.classic_theme_title)
    }

    private fun buildPage() {
        binders.clear()
        body.removeAllViews()
        updatePageTitle()
        section(R.string.classic_appearance)
        listOf("system" to R.string.classic_system, "light" to R.string.classic_light,
            "dark" to R.string.classic_dark).forEach { (mode, id) ->
            radioRow(getString(id), { config.oxygenThemeActivated &&
                config.oxygenAppearanceMode == mode }) {
                change { config.oxygenAppearanceMode = mode; config.oxygenThemeActivated = true }
            }
        }
        section(R.string.classic_accent)
        colorGrid(OxygenThemePresets.group("classic"), showCurrentColor = true)
        section(R.string.classic_other)
        actionRow(R.string.classic_more_colors) {
            MaterialColorPickerDialog.show(this, ::label, ::activatePreset)
        }
        actionRow(R.string.classic_custom_color) { chooseColor() }
        actionRow(R.string.classic_restore_default) {
            activatePreset(OxygenThemePresets.byId.getValue(OxygenThemePresets.DEFAULT_ID))
        }
        bindPage()
    }

    private fun bindPage() {
        val tokens = colors
        root.setBackgroundColor(tokens.pageBackground)
        body.setBackgroundColor(tokens.pageBackground)
        body.setSettingsDividerColor(OxygenThemeColors.blend(tokens.pageBackground, tokens.primaryText, 0.10))
        OxygenToolbarStyle.applyStandaloneToolbar(this, titleBar, backIcon, titleText)
        binders.forEach { it() }
        OxygenClassicControls.applySwitches(body, tokens)
        OxygenSystemBars.apply(this)
    }

    private fun chooseColor() {
        ClassicCustomColorDialog.show(this) { parsed ->
            change {
                config.oxygenCustomAccent = parsed
                config.oxygenThemePresetId = "custom"
                config.oxygenThemeActivated = true
            }
        }
    }
}
