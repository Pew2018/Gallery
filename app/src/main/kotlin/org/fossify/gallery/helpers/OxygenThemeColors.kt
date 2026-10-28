package org.fossify.gallery.helpers

import kotlin.math.pow
import kotlin.math.roundToInt

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

    fun opaque(color: Int): Int = color or BLACK

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
