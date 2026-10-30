package org.fossify.gallery.helpers

import kotlin.math.pow
import kotlin.math.roundToInt

/** Classic Oxygen-era progress: the activity-colored segment sits on a neutral content strip. */
data class OxygenProgressPalette(
    val surface: Int,
    val indicator: Int,
    val track: Int
)

/** Switch colors retain the exact chosen RGB; text-only contrast adjustment must not
 * silently darken a selected switch. Disabled variants blend into the actual page.
 */
data class OxygenSwitchPalette(
    val onThumb: Int,
    val onTrack: Int,
    val offThumb: Int,
    val offTrack: Int,
    val disabledOnThumb: Int,
    val disabledOnTrack: Int,
    val disabledOffThumb: Int,
    val disabledOffTrack: Int
)

/** Immutable semantic colors. rawAccent is the exact saved value, including alpha. */
data class OxygenThemeTokens(
    val dark: Boolean,
    val rawAccent: Int,
    val pageBackground: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val disabledText: Int,
    val interactiveAccent: Int,
    val toolbarBackground: Int,
    val toolbarForeground: Int,
    val statusBarBackground: Int,
    val lightStatusBarIcons: Boolean,
    val navigationBarBackground: Int,
    val darkNavigationBarIcons: Boolean,
    val dialogBackground: Int
)

/** Pure sRGB/WCAG math: no Android runtime, preferences, caches or mutations. */
object OxygenThemeColors {
    const val WHITE: Int = -1
    val BLACK: Int = 0xFF000000.toInt()

    private const val SWITCH_TRACK_ALPHA = 115 // round(0.45 * 255)
    private const val SWITCH_DISABLED_ALPHA = 0.38

    fun opaque(color: Int): Int = color or BLACK

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * Classic OxygenOS 7.1.1 switches use the user's selected accent on the knob,
     * not interactiveAccent (which is deliberately darkened for readable text).
     * The slender rail is only stroked by the Drawable, never filled as a pill.
     * The 45% active rail retains ARGB alpha; disabled colors composite over the page.
     */
    fun switchPalette(colors: OxygenThemeTokens): OxygenSwitchPalette {
        val page = colors.pageBackground
        val seed = opaque(colors.rawAccent)
        val onTrack = withAlpha(seed, SWITCH_TRACK_ALPHA)
        val offThumb = 0xFFE6E6E6.toInt()
        val offTrack = if (colors.dark) 0xFF5A5A5A.toInt() else 0xFFD6D6D6.toInt()
        // Match Compose ContentAlpha.disabled: first composite the disabled
        // semantic color over the actual page surface. The active rail retains
        // its 45% drawing alpha after that composition, as in SwitchDefaults.
        val disabledOn = blend(page, seed, SWITCH_DISABLED_ALPHA)
        val disabledOffThumb = blend(page, offThumb, SWITCH_DISABLED_ALPHA)
        val disabledOffTrack = blend(page, offTrack, SWITCH_DISABLED_ALPHA)
        return OxygenSwitchPalette(
            onThumb = seed,
            onTrack = onTrack,
            offThumb = offThumb,
            offTrack = offTrack,
            disabledOnThumb = disabledOn,
            disabledOnTrack = withAlpha(disabledOn, SWITCH_TRACK_ALPHA),
            disabledOffThumb = disabledOffThumb,
            disabledOffTrack = disabledOffTrack
        )
    }

    /**
     * The 2017 OxygenOS installer/file-manager references show a tinted active segment
     * over a lighter inactive line on a separate content-colored surface, not over the
     * brand-colored toolbar. Dark mode retains the same color family with more luminance.
     *
     * Resolve against both the page and the inactive track so pale and custom accents
     * remain visible, without changing the user's saved accent or toolbar color.
     */
    fun progressPalette(colors: OxygenThemeTokens): OxygenProgressPalette {
        val track = if (colors.dark) 0xFF484848.toInt() else 0xFFE0E0E0.toInt()
        val target = if (colors.dark) WHITE else BLACK
        val indicator = towardContrast(colors.interactiveAccent, target) {
            contrastRatio(it, colors.pageBackground) >= 4.5 &&
                contrastRatio(it, track) >= 3.0
        }
        return OxygenProgressPalette(
            surface = colors.pageBackground,
            indicator = indicator,
            track = track
        )
    }

    fun resolvedDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun blend(from: Int, to: Int, amount: Double): Int {
        val ratio = amount.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int = (
            ((from ushr shift) and 255) * (1.0 - ratio) +
                ((to ushr shift) and 255) * ratio
            ).roundToInt().coerceIn(0, 255)
        return BLACK or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Foreground alpha is composited; the final background must be opaque. */
    fun contrastRatio(foreground: Int, background: Int): Double {
        require(background ushr 24 == 255) { "Contrast requires an opaque background" }
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val s = ((color ushr shift) and 255) / 255.0
                return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val composite = blend(background, foreground, (foreground ushr 24) / 255.0)
        val one = luminance(composite)
        val two = luminance(background)
        return (maxOf(one, two) + 0.05) / (minOf(one, two) + 0.05)
    }

    fun autoForeground(surface: Int): Int =
        if (contrastRatio(BLACK, opaque(surface)) >= contrastRatio(WHITE, opaque(surface))) BLACK else WHITE

    /**
     * Ignore seed alpha for a solid surface, retaining its RGB identity.
     * Bounded binary search keeps the nearest passing, quantized RGB candidate.
     */
    fun toolbarBackground(rawAccent: Int): Int = towardContrast(opaque(rawAccent), BLACK) {
        contrastRatio(WHITE, it) >= 4.5
    }

    fun readableAccent(rawAccent: Int, surface: Int): Int {
        val background = opaque(surface)
        return towardContrast(opaque(rawAccent), autoForeground(background)) {
            contrastRatio(it, background) >= 4.5
        }
    }

    private inline fun towardContrast(seed: Int, target: Int, passes: (Int) -> Boolean): Int {
        if (passes(seed)) return seed
        var low = 0.0
        var high = 1.0
        var result = target
        repeat(24) {
            val middle = (low + high) / 2.0
            val candidate = blend(seed, target, middle)
            if (passes(candidate)) {
                high = middle
                result = candidate
            } else {
                low = middle
            }
        }
        return result
    }

    fun resolve(
        classic: Boolean, mode: String, systemDark: Boolean,
        rawAccent: Int, legacyBackground: Int, legacyText: Int
    ): OxygenThemeTokens {
        val dark = if (classic) resolvedDark(mode, systemDark)
            else autoForeground(legacyBackground) == WHITE
        val page = if (classic) {
            if (dark) 0xFF212121.toInt() else 0xFFFAFAFA.toInt()
        } else opaque(legacyBackground)
        val text = if (classic) {
            if (dark) 0xFFFAFAFA.toInt() else 0xFF212121.toInt()
        } else legacyText
        val toolbar = toolbarBackground(rawAccent)
        return OxygenThemeTokens(
            dark = dark, rawAccent = rawAccent, pageBackground = page,
            primaryText = text, secondaryText = blend(page, text, 0.65),
            disabledText = blend(page, text, 0.38),
            interactiveAccent = readableAccent(rawAccent, page),
            toolbarBackground = toolbar, toolbarForeground = WHITE,
            statusBarBackground = blend(toolbar, BLACK, 0.09),
            lightStatusBarIcons = true,
            navigationBarBackground = page, darkNavigationBarIcons = autoForeground(page) == BLACK,
            dialogBackground = if (classic) {
                if (dark) 0xFF303030.toInt() else WHITE
            } else page
        )
    }
}
