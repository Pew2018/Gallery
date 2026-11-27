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
    /**
     * How far apart the two candidate icon colours are on [background], as a contrast
     * ratio difference. 0 near the crossing point, ~20 on black or white.
     */
    fun advantage(background: Int): Double =
        kotlin.math.abs(contrast(BLACK, background) - contrast(WHITE, background))

    fun useDarkIcons(background: Int, previous: Boolean? = null): Boolean {
        val dark = contrast(BLACK, background)
        val light = contrast(WHITE, background)
        if (previous != null && kotlin.math.abs(dark - light) < SWITCH_MARGIN) return previous
        return dark > light
    }
}

/**
 * One window's icon polarity, decided by the surfaces *this app* actually painted.
 *
 * Two defects motivated this class, both visible in the exported Pixel 8 Pro log of
 * the 2026-11-24 run during a single monotonic Viewer entry
 * (`progress=0.0 LIGHT -> 0.010795873 DARK -> 0.1496258 LIGHT`):
 *
 *  1. The decision was taken against `InsetsController.isAppearanceLightStatusBars`,
 *     i.e. against whatever the last writer left there. The Viewer window has more
 *     than one writer (`BaseViewerActivity` blends, `SimpleActivity.onPostResume` and
 *     `OxygenSystemBars.apply` paint the page), so a page write between two Viewer
 *     frames became the "previous" polarity of the next Viewer frame and flipped it.
 *  2. Nothing tied a decision to the surface it was made for. Re-evaluating the same
 *     colour could therefore produce a different answer.
 *
 * [decide] removes both: an identical surface returns the same polarity without any
 * re-evaluation, and a change has to keep a contrast advantage *and* stay the better
 * option for [holdMs] before it is applied. Genuine surface changes (a theme switch, a
 * different page underneath) still resolve, they just cannot chatter.
 */
internal class IconPolarityLatch(
    private val holdMs: Long = DEFAULT_HOLD_MS,
) {
    private var decision: Boolean? = null
    private var decidedSurface: Int? = null
    private var candidate: Boolean? = null
    private var candidateSince = 0L

    /** Polarity for [surface], stable across repeated frames of the same colour. */
    fun decide(surface: Int, nowMs: Long): Boolean {
        // An identical surface is only answered from memory once no change is pending;
        // otherwise a candidate that is still waiting out [holdMs] would never resolve,
        // because every frame of the same colour would short-circuit it.
        if (decision != null && decidedSurface == surface && candidate == null) return decision!!
        decidedSurface = surface
        val current = decision
        val best = SystemBarIconContrast.useDarkIcons(surface, current)
        if (current == null) {
            decision = best
            candidate = null
            return best
        }
        if (best == current) {
            candidate = null
            return current
        }
        // Holding a polarity only makes sense while both options are close. Once one of
        // them is clearly better (a theme switch, a jump to the viewer surface) keeping
        // the old icons would leave them unreadable for the duration of the hold.
        if (SystemBarIconContrast.advantage(surface) >= DECISIVE_MARGIN) {
            decision = best
            candidate = null
            return best
        }
        if (candidate != best) {
            candidate = best
            candidateSince = nowMs
            return current
        }
        if (nowMs - candidateSince >= holdMs) {
            decision = best
            candidate = null
            return best
        }
        return current
    }

    /** Takes over a polarity another writer established (the page's own appearance). */
    fun adopt(polarity: Boolean) {
        decision = polarity
        decidedSurface = null
        candidate = null
    }

    /**
     * Applies the polarity an *endpoint* frame is required to show.
     *
     * Hysteresis exists to keep the mid-blend band from chattering; it must never decide
     * an endpoint. The exported Pixel 8 Pro log of the 2026-11-24 run starts a Viewer
     * entry at `progress=0.0 iconAppearance=DARK` while the page underneath is
     * `lightStatusBarIcons=true`: the toolbar colour (`0xffd2471c`) puts black and white
     * icons within 0.12 contrast ratio of each other, so a fresh latch with no previous
     * decision resolved that near-tie to black and the icons had to flip back to white at
     * `progress=0.30`. The page's own declaration is the only correct answer at its
     * endpoint, so it is [align]ed instead of re-derived.
     *
     * Any pending candidate is dropped: it was computed for a blend that no longer
     * describes the frame. [surface] is recorded so a repeated identical frame is
     * answered from memory without re-evaluating.
     */
    fun align(polarity: Boolean, surface: Int) {
        decision = polarity
        decidedSurface = surface
        candidate = null
    }

    /** The window is gone; the next window starts from its own first surface. */
    fun reset() {
        decision = null
        decidedSurface = null
        candidate = null
    }

    val current: Boolean? get() = decision

    private companion object {
        /** Long enough to span a chattering pair of frames, short enough to be invisible. */
        const val DEFAULT_HOLD_MS = 48L

        /**
         * Contrast-ratio distance at which the choice is no longer ambiguous. Above it the
         * alternative icon colour is decisively better and the change is applied at once;
         * below it (the mid-blend band) the on-screen polarity is held for [DEFAULT_HOLD_MS].
         */
        const val DECISIVE_MARGIN = 3.0
    }
}
