package org.fossify.gallery.activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.OxygenClassicPalette
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenThemePresets

/** A classic View settings screen: no Material3 or Compose widgets. */
class OxygenThemeActivity : SimpleActivity() {
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private val bg get() = if (OxygenThemeEngine.isDark(this, config)) Color.rgb(33, 33, 33) else Color.rgb(250, 250, 250)
    private val fg get() = if (OxygenThemeEngine.isDark(this, config)) Color.WHITE else Color.rgb(33, 33, 33)
    private val secondary get() = ColorUtils.blendARGB(bg, fg, 0.65f)
    private val accent get() = if (config.oxygenThemeActivated) OxygenThemeEngine.accent(config)
        else config.primaryColor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, system.top, 0, system.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        render()
    }

    private fun section(label: String) {
        body.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(OxygenClassicPalette.readableAccent(accent, bg))
            gravity = Gravity.BOTTOM
            setPadding(dp(16), dp(24), dp(16), dp(8))
        }, LinearLayout.LayoutParams(-1, dp(54)))
    }

    private fun item(textValue: String, selected: Boolean = false, color: Int? = null, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            isFocusable = true
        }
        if (color != null) {
            row.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), secondary)
                }
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(16) })
        }
        row.addView(TextView(this).apply {
            text = textValue
            textSize = 16f
            setTextColor(fg)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(RadioButton(this).apply {
            isChecked = selected
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(OxygenClassicPalette.readableAccent(accent, bg), secondary))
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        row.setOnClickListener { action() }
        body.addView(row, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun chooseColor(title: String, initial: Int, save: (Int) -> Unit) {
        val field = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(OxygenThemeEngine.formatHex(initial))
            selectAll()
            setPadding(dp(24), dp(8), dp(24), dp(8))
            setTextColor(fg)
            setHintTextColor(secondary)
        }
        val builder = OxygenClassicDialog.builder(this, title)
            .setView(field)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .setNeutralButton("恢复默认", null)
        val dialog = OxygenClassicDialog.show(this, builder, field)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val parsed = OxygenThemeEngine.parseHex(field.text.toString().trim())
            if (parsed == null) field.error = "请输入 #RRGGBB 格式的颜色"
            else {
                save(parsed)
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            field.setText("#42A5F5")
            field.selectAll()
        }
    }

    private fun changeAndRender(action: () -> Unit) {
        val y = scroll.scrollY
        action()
        OxygenThemeEngine.apply(this, config)
        render()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun render() {
        val surface = bg
        val textColor = fg
        root.setBackgroundColor(surface)
        body.removeAllViews()
        body.setBackgroundColor(surface)
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(OxygenThemeEngine.toolbarSurface(this@OxygenThemeActivity, config))
        }
        val barColor = OxygenThemeEngine.toolbarForeground(this, config)
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            setTextColor(barColor)
            gravity = Gravity.CENTER
            contentDescription = "返回"
            setOnClickListener { finish() }
        }
        titleBar.addView(back, LinearLayout.LayoutParams(dp(56), -1))
        titleBar.addView(TextView(this).apply {
            text = "经典主题与配色"
            textSize = 20f
            setTextColor(barColor)
            gravity = Gravity.CENTER_VERTICAL
        })
        root.findViewWithTag<View>("oxygen_theme_toolbar")?.let(root::removeView)
        titleBar.tag = "oxygen_theme_toolbar"
        root.addView(titleBar, 0, LinearLayout.LayoutParams(-1, dp(56)))
        window.statusBarColor = OxygenThemeEngine.toolbarSurface(this, config)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(
                OxygenThemeEngine.toolbarSurface(this, config)) >= 0.5

        section("主题实时预览")
        val preview = TextView(this).apply {
            text = "OxygenOS Classic     ●   □   ✓"
            textSize = 16f
            setTextColor(OxygenClassicPalette.readableAccent(accent, surface))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(54)))
        section("经典配色")
        OxygenThemePresets.all.filter { it.group == "classic" }.forEach { preset ->
            item(preset.label, config.oxygenThemePresetId == preset.id, preset.color) {
                changeAndRender {
                    config.oxygenThemePresetId = preset.id
                    config.oxygenThemeActivated = true
                }
            }
        }
        section("Material Design 配色")
        OxygenThemePresets.all.filter { it.group == "material" }.forEach { preset ->
            item(preset.label, config.oxygenThemePresetId == preset.id, preset.color) {
                changeAndRender {
                    config.oxygenThemePresetId = preset.id
                    config.oxygenThemeActivated = true
                }
            }
        }
        section("外观模式")
        listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式").forEach { (mode, label) ->
            item(label, config.oxygenAppearanceMode == mode) {
                changeAndRender {
                    config.oxygenAppearanceMode = mode
                    config.oxygenThemeActivated = true
                }
            }
        }
        section("自定义强调色和顶栏配色")
        item("自定义强调色：" + OxygenThemeEngine.formatHex(accent),
            config.oxygenThemePresetId == "custom", accent) {
            chooseColor("应用强调色", accent) { color ->
                changeAndRender {
                    config.oxygenCustomAccent = color
                    config.oxygenThemePresetId = "custom"
                    config.oxygenThemeActivated = true
                }
            }
        }
        item("顶栏背景：跟随主题", !config.oxygenCustomToolbarEnabled) {
            changeAndRender {
                config.oxygenCustomToolbarEnabled = false
                config.oxygenThemeActivated = true
            }
        }
        item("顶栏背景：自定义", config.oxygenCustomToolbarEnabled,
            config.oxygenToolbarColor) {
            chooseColor("顶栏背景颜色", config.oxygenToolbarColor) { color ->
                changeAndRender {
                    config.oxygenToolbarColor = color
                    config.oxygenCustomToolbarEnabled = true
                    config.oxygenThemeActivated = true
                }
            }
        }
        item("顶栏文字与图标：自动选择", !config.oxygenManualToolbarText) {
            changeAndRender { config.oxygenManualToolbarText = false }
        }
        item("顶栏文字与图标：手动颜色", config.oxygenManualToolbarText,
            config.oxygenToolbarTextColor) {
            chooseColor("顶栏文字与图标颜色", config.oxygenToolbarTextColor) { color ->
                changeAndRender {
                    config.oxygenToolbarTextColor = color
                    config.oxygenManualToolbarText = true
                }
            }
        }
        body.addView(TextView(this).apply {
            text = "主题会保留颜色原值。自动文字颜色仅根据背景选择黑色或白色，不改变已保存的配色。"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(16), dp(12), dp(16), dp(24))
        })
    }
}
