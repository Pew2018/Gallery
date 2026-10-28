package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import kotlin.math.pow

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
    val CLASSIC_TOOLBAR: Int = 0xFF212121.toInt()
    val CLASSIC_STATUS_BAR: Int = 0xFF1E1E1E.toInt()

    fun resolveToolbarSurface(
        activated: Boolean, customEnabled: Boolean, customColor: Int, legacyColor: Int
    ): Int = when {
        !activated -> legacyColor
        customEnabled -> customColor
        else -> CLASSIC_TOOLBAR
    }

    /** Pure sRGB contrast math, including JVM unit tests without Robolectric. */
    fun contrastRatio(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val s = ((color ushr shift) and 0xFF) / 255.0
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val one = luminance(foreground)
        val two = luminance(background)
        return (maxOf(one, two) + 0.05) / (minOf(one, two) + 0.05)
    }

    fun autoForeground(surface: Int): Int =
        if (contrastRatio(Color.BLACK, surface) >= contrastRatio(Color.WHITE, surface))
            Color.BLACK else Color.WHITE

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
