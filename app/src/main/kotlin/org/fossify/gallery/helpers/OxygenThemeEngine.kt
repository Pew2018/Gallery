package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor

/** Resolves one theme snapshot into Commons' existing colors.
 * No preferences are rewritten by contrast derivation.
 */
object OxygenThemeEngine {
    fun parseHex(input: String): Int? {
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(input)) return null
        return (0xFF000000L or input.substring(1).toLong(16)).toInt()
    }

    fun formatHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun isDark(context: Context, config: Config): Boolean = when (config.oxygenAppearanceMode) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun accent(config: Config): Int =
        OxygenThemePresets.byId[config.oxygenThemePresetId]?.color ?: config.oxygenCustomAccent

    fun apply(context: Context, config: Config) {
        if (!config.oxygenThemeActivated) return
        val dark = isDark(context, config)
        // Avoid Commons' dynamic Material You colors overwriting explicit presets.
        config.isSystemThemeEnabled = false
        config.backgroundColor = if (dark) Color.rgb(33, 33, 33) else Color.rgb(250, 250, 250)
        config.textColor = if (dark) Color.rgb(250, 250, 250) else Color.rgb(33, 33, 33)
        val seed = accent(config)
        config.primaryColor = seed
        config.accentColor = seed
    }

    fun toolbarSurface(context: Context, config: Config): Int =
        if (config.oxygenThemeActivated && config.oxygenCustomToolbarEnabled) config.oxygenToolbarColor
        else if (config.oxygenThemeActivated) config.backgroundColor
        else context.getProperBackgroundColor()

    fun toolbarForeground(context: Context, config: Config): Int {
        val surface = toolbarSurface(context, config)
        if (config.oxygenThemeActivated && config.oxygenManualToolbarText) {
            val chosen = config.oxygenToolbarTextColor
            // Manual colors must not silently change, even when contrast is inadequate.
            return chosen
        }
        return if (ColorUtils.calculateContrast(Color.BLACK, surface) >= 4.5) Color.BLACK else Color.WHITE
    }
}
