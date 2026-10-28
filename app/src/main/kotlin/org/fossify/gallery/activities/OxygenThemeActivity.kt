package org.fossify.gallery.activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.gallery.extensions.config
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.helpers.OxygenSystemBars
import org.fossify.gallery.helpers.OxygenClassicDialog
import org.fossify.gallery.helpers.OxygenClassicPalette
import org.fossify.gallery.helpers.OxygenThemeEngine
import org.fossify.gallery.helpers.OxygenThemePresets

/** A classic View settings screen: no Material3 or Compose widgets. */
class OxygenThemeActivity : SimpleActivity() {
    private lateinit var root: LinearLayout
    private lateinit var titleBar: LinearLayout
    private lateinit var backIcon: ImageView
    private lateinit var titleText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private val bg get() = OxygenThemeEngine.pageSurface(this, config)
    private val fg get() = OxygenThemeEngine.snapshot(this, config).primaryText
    private val secondary get() = OxygenThemeEngine.snapshot(this, config).secondaryText
    private val accent get() = OxygenThemeEngine.snapshot(this, config).rawAccent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backIcon = ImageView(this).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_arrow_left_vector)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        titleText = TextView(this).apply {
            text = "经典主题与配色"
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleBar.addView(backIcon, LinearLayout.LayoutParams(dp(48), dp(56)))
        titleBar.addView(titleText, LinearLayout.LayoutParams(0, -1, 1f).apply {
            marginStart = dp(16)
            marginEnd = dp(16)
        })
        root.addView(titleBar, LinearLayout.LayoutParams(-1, dp(56)))
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
        savedInstanceState?.getInt("classic_theme_scroll")?.let { y ->
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("classic_theme_scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
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
            // Preserve the 56dp classic row at default font scale while
            // allowing multi-line labels to expand for large text or narrow screens.
            minimumHeight = dp(56)
            setPadding(dp(16), dp(4), dp(16), dp(4))
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
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(RadioButton(this).apply {
            isChecked = selected
            isClickable = false
            isFocusable = false
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(OxygenClassicPalette.readableAccent(accent, bg), secondary))
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        row.setOnClickListener { action() }
        body.addView(row, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun chooseColor(title: String, initial: Int, defaultColor: Int, save: (Int) -> Unit) {
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
            field.setText(OxygenThemeEngine.formatHex(defaultColor))
            field.selectAll()
        }
    }

    private fun changeAndRender(action: () -> Unit) {
        val y = scroll.scrollY
        action()
        OxygenThemeEngine.apply(this, config)
        if (refreshThemeIfNeeded()) return
        render()
        scroll.post { scroll.scrollTo(0, y) }
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        val surface = bg
        val textColor = fg
        root.setBackgroundColor(surface)
        body.removeAllViews()
        body.setBackgroundColor(surface)
        val toolbarColor = OxygenThemeEngine.toolbarSurface(this, config)
        val toolbarText = OxygenThemeEngine.toolbarForeground(this, config)
        titleBar.setBackgroundColor(toolbarColor)
        backIcon.imageTintList = ColorStateList.valueOf(toolbarText)
        titleText.setTextColor(toolbarText)
        OxygenSystemBars.apply(this)

        section("外观模式")
        listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式").forEach { (mode, label) ->
            item(label, config.oxygenAppearanceMode == mode) {
                changeAndRender {
                    config.oxygenAppearanceMode = mode
                    config.oxygenThemeActivated = true
                }
            }
        }
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
        section("主题强调色与自定义配色")
        item("自定义强调色：" + OxygenThemeEngine.formatHex(accent),
            config.oxygenThemePresetId == "custom", accent) {
            chooseColor("应用强调色", accent, Color.rgb(33, 150, 243)) { color ->
                changeAndRender {
                    config.oxygenCustomAccent = color
                    config.oxygenThemePresetId = "custom"
                    config.oxygenThemeActivated = true
                }
            }
        }
        section("顶栏背景")
        item("顶栏背景：跟随强调色", !config.oxygenCustomToolbarEnabled) {
            changeAndRender {
                config.oxygenCustomToolbarEnabled = false
                config.oxygenThemeActivated = true
            }
        }
        item("顶栏背景：旧版自定义值（保留记录）", config.oxygenCustomToolbarEnabled,
            config.oxygenToolbarColor) {
            chooseColor("顶栏背景颜色", config.oxygenToolbarColor, OxygenThemeEngine.CLASSIC_TOOLBAR) { color ->
                changeAndRender {
                    config.oxygenToolbarColor = color
                    config.oxygenCustomToolbarEnabled = true
                    config.oxygenThemeActivated = true
                }
            }
        }
        section("顶栏文字与图标")
        item("顶栏文字与图标：白色（自动保证对比度）", !config.oxygenManualToolbarText) {
            changeAndRender { config.oxygenManualToolbarText = false }
        }
        item("顶栏文字与图标：旧版手动值（保留记录）", config.oxygenManualToolbarText,
            config.oxygenToolbarTextColor) {
            chooseColor("顶栏文字与图标颜色", config.oxygenToolbarTextColor, Color.WHITE) { color ->
                changeAndRender {
                    config.oxygenToolbarTextColor = color
                    config.oxygenManualToolbarText = true
                }
            }
        }
        section("恢复与兼容")
        item("恢复原有 Fossify 配色", !config.oxygenThemeActivated) {
            changeAndRender { config.oxygenThemeActivated = false }
        }
        item("打开原有 Fossify 颜色管理") {
            changeAndRender { config.oxygenThemeActivated = false }
            startCustomizationActivity()
        }
        body.addView(TextView(this).apply {
            text = "首次启用经典主题时，会备份原来的 Fossify 颜色设置；恢复后可继续用原版颜色管理页面调整。"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        })
        body.addView(TextView(this).apply {
            text = "强调色保留原值；顶栏跟随强调色并按需加深，标题与图标始终为白色。旧版独立顶栏颜色仅保留记录，不再控制显示。"
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(16), dp(12), dp(16), dp(24))
        })
    }
}
