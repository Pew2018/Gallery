package org.fossify.gallery.helpers

import kotlin.math.pow

/**
 * Icon polarity for the status and navigation bars, with hysteresis.
 *
 * The Viewer return blends its bar surfaces from the page colour to the viewer
 * surface on the transition clock, so the contrast between the two candidate icon
 * colours passes through the point where neither is clearly better. Deciding on the
 * exact equality point makes the polarity a function of progress at a single
 * threshold: any jitter in the progress (gesture restarts, a cancel that rewinds the
 * same frames) flips the icons back and forth, which is visible as the status bar
 * blinking.
 *
 * [useDarkIcons] therefore keeps the current decision until the alternative is clearly
 * better. Away from the crossing point the margin is far above [SWITCH_MARGIN], so the
 * steady states (a light page surface or the near-black viewer surface) always resolve
 * to the correct polarity and keep a high contrast ratio; only the ambiguous mid-grey
 * band, where both choices are close to 3:1, is held.
 *
 * Pure Kotlin so the contract is testable without Robolectric.
 */
internal object SystemBarIconContrast {
    /** Required contrast-ratio advantage before the icon polarity may change. */
    internal const val SWITCH_MARGIN = 1.0

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    // WCAG 2.x relative-luminance constants.
    private const val CHANNEL_MAX = 255.0
    private const val GAMMA_THRESHOLD = 0.03928
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA_EXPONENT = 2.4
    private const val LINEAR_SCALE = 12.92
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    /** WCAG's flare term, added to both luminances before the ratio. */
    private const val CONTRAST_OFFSET = 0.05
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val BYTE_MASK = 0xFF

    /** WCAG relative luminance of an opaque ARGB colour. */
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and BYTE_MASK) / CHANNEL_MAX
            return if (value <= GAMMA_THRESHOLD) value / LINEAR_SCALE
            else ((value + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA_EXPONENT)
        }
        return RED_WEIGHT * channel(RED_SHIFT) +
            GREEN_WEIGHT * channel(GREEN_SHIFT) +
            BLUE_WEIGHT * channel(BLUE_SHIFT)
    }

    /** WCAG contrast ratio between two opaque colours, in 1..21. */
    fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    /**
     * True when dark icons belong on [background].
     *
     * [previous] is the polarity currently on screen, or null when there is no
     * previous decision (then the better option wins immediately).
     */
    fun useDarkIcons(background: Int, previous: Boolean? = null): Boolean {
        val dark = contrast(BLACK, background)
        val light = contrast(WHITE, background)
        if (previous != null && kotlin.math.abs(dark - light) < SWITCH_MARGIN) return previous
        return dark > light
    }
}
