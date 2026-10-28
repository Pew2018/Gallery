package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.Configuration
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/** Read through snapshot(); apply() is the explicit, idempotent Commons compatibility write. */
object OxygenThemeEngine {
    fun parseHex(input: String): Int? {
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(input)) return null
        return (0xFF000000L or input.substring(1).toLong(16)).toInt()
    }

    fun formatHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun resolvedDark(mode: String, systemDark: Boolean): Boolean =
        OxygenThemeColors.resolvedDark(mode, systemDark)

    private fun systemDark(context: Context): Boolean =
        (context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun isDark(context: Context, config: Config): Boolean = snapshot(context, config).dark

    fun accent(config: Config): Int =
        OxygenThemePresets.byId[config.oxygenThemePresetId]?.color ?: config.oxygenCustomAccent

    /** Preset > saved custom seed in classic mode; Commons' effective primary otherwise.
     * Legacy independent toolbar/text preferences remain stored for data compatibility,
     * but never override the shared accent-derived white-on-color toolbar policy.
     */
    fun snapshot(context: Context, config: Config): OxygenThemeTokens =
        OxygenThemeColors.resolve(
            classic = config.oxygenThemeActivated,
            mode = config.oxygenAppearanceMode,
            systemDark = systemDark(context),
            rawAccent = if (config.oxygenThemeActivated) accent(config) else context.getProperPrimaryColor(),
            legacyBackground = context.getProperBackgroundColor(),
            legacyText = context.getProperTextColor()
        )

    /** Never called by color getters. Preserve Config's activation backup/restore boundary. */
    fun apply(context: Context, config: Config) {
        if (!config.oxygenThemeActivated) return
        val colors = snapshot(context, config)
        if (config.isSystemThemeEnabled) config.isSystemThemeEnabled = false
        if (config.backgroundColor != colors.pageBackground) config.backgroundColor = colors.pageBackground
        if (config.textColor != colors.primaryText) config.textColor = colors.primaryText
        if (config.primaryColor != colors.rawAccent) config.primaryColor = colors.rawAccent
        if (config.accentColor != colors.rawAccent) config.accentColor = colors.rawAccent
    }

    // Retained as legacy picker defaults; runtime toolbar colors never use these constants.
    val CLASSIC_TOOLBAR: Int = 0xFF212121.toInt()
    val CLASSIC_STATUS_BAR: Int = 0xFF1E1E1E.toInt()

    @Deprecated("Use snapshot().toolbarBackground or OxygenThemeColors.toolbarBackground(seed)")
    @Suppress("UNUSED_PARAMETER")
    fun resolveToolbarSurface(
        activated: Boolean, customEnabled: Boolean, customColor: Int, legacyColor: Int
    ): Int = OxygenThemeColors.toolbarBackground(if (activated) customColor else legacyColor)

    fun contrastRatio(foreground: Int, background: Int): Double =
        OxygenThemeColors.contrastRatio(foreground, background)

    fun autoForeground(surface: Int): Int = OxygenThemeColors.autoForeground(surface)
    fun toolbarSurface(context: Context, config: Config): Int = snapshot(context, config).toolbarBackground
    @Suppress("UNUSED_PARAMETER")
    fun toolbarForeground(context: Context, config: Config): Int = OxygenThemeColors.WHITE
    fun statusBarSurface(context: Context, config: Config): Int = snapshot(context, config).statusBarBackground

    /** Historical name: this means Android's LIGHT_STATUS_BAR (dark icons), NOT light icons. */
    @Suppress("UNUSED_PARAMETER")
    fun statusBarLightIcons(context: Context, config: Config): Boolean = false
    fun pageSurface(context: Context, config: Config): Int = snapshot(context, config).pageBackground
    fun dialogSurface(context: Context, config: Config): Int = snapshot(context, config).dialogBackground
}
