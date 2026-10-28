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

    /** Pure appearance decision; tests do not require an Android device. */
    fun resolvedDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun isDark(context: Context, config: Config): Boolean = resolvedDark(
        config.oxygenAppearanceMode,
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    )

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

    // The 2017 Settings-derived toolbar is a dedicated dark surface, NOT the
    // light page surface. Keep legacy Fossify and all saved custom colors intact.
    const val CLASSIC_TOOLBAR: Int = 0xFF212121.toInt()
    const val CLASSIC_STATUS_BAR: Int = 0xFF1E1E1E.toInt()

    fun resolveToolbarSurface(
        activated: Boolean, customEnabled: Boolean, customColor: Int, legacyColor: Int
    ): Int = when {
        !activated -> legacyColor
        customEnabled -> customColor
        else -> CLASSIC_TOOLBAR
    }

    fun autoForeground(surface: Int): Int {
        val opaque = ColorUtils.setAlphaComponent(surface, 255)
        return if (ColorUtils.calculateContrast(Color.BLACK, opaque) >= 4.5)
            Color.BLACK else Color.WHITE
    }

    fun toolbarSurface(context: Context, config: Config): Int =
        resolveToolbarSurface(config.oxygenThemeActivated, config.oxygenCustomToolbarEnabled,
            config.oxygenToolbarColor, context.getProperBackgroundColor())

    fun toolbarForeground(context: Context, config: Config): Int =
        if (config.oxygenThemeActivated && config.oxygenManualToolbarText)
            config.oxygenToolbarTextColor
        else autoForeground(toolbarSurface(context, config))

    fun statusBarSurface(context: Context, config: Config): Int = when {
        !config.oxygenThemeActivated -> toolbarSurface(context, config)
        !config.oxygenCustomToolbarEnabled -> CLASSIC_STATUS_BAR
        else -> ColorUtils.blendARGB(toolbarSurface(context, config), Color.BLACK, 0.09f)
    }

    fun statusBarLightIcons(context: Context, config: Config): Boolean =
        autoForeground(statusBarSurface(context, config)) == Color.BLACK

    fun pageSurface(context: Context, config: Config): Int =
        if (config.oxygenThemeActivated) {
            if (isDark(context, config)) Color.rgb(33, 33, 33) else Color.rgb(250, 250, 250)
        } else context.getProperBackgroundColor()

    fun dialogSurface(context: Context, config: Config): Int =
        if (config.oxygenThemeActivated && isDark(context, config)) Color.rgb(48, 48, 48)
        else if (config.oxygenThemeActivated) Color.WHITE
        else context.getProperBackgroundColor()

}
