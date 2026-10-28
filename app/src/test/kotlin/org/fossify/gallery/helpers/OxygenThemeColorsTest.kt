package org.fossify.gallery.helpers

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class OxygenThemeColorsTest {
    private val seeds = listOf(
        0xFF42A5F5, 0xFF0071CD, 0xFFE6A545, 0xFF7DC22F, 0xFF01998C,
        0xFF9575CD, 0xFFF44336, 0xFFFFFFFF, 0xFF000000
    ).map { it.toInt() }

    private fun resolve(seed: Int, mode: String, systemDark: Boolean, classic: Boolean = true) =
        OxygenThemeColors.resolve(classic, mode, systemDark, seed,
            0xFFFAFAFA.toInt(), 0xFF212121.toInt())

    @Test fun allRequiredSeedsPassInBothModesWithoutChangingSeed() {
        for (seed in seeds) for (mode in listOf("light", "dark", "system")) {
            for (systemDark in listOf(false, true)) {
                val colors = resolve(seed, mode, systemDark)
                assertEquals(seed, colors.rawAccent)
                assertEquals(colors, resolve(seed, mode, systemDark))
                assertEquals(255, colors.toolbarBackground ushr 24)
                assertEquals(OxygenThemeColors.WHITE, colors.toolbarForeground)
                assertTrue(OxygenThemeColors.contrastRatio(colors.toolbarForeground,
                    colors.toolbarBackground) >= 4.5)
                assertTrue(OxygenThemeColors.contrastRatio(OxygenThemeColors.WHITE,
                    colors.statusBarBackground) >= 4.5)
                assertTrue(colors.lightStatusBarIcons)
                assertEquals(resolve(seed, "light", systemDark).toolbarBackground,
                    resolve(seed, "dark", systemDark).toolbarBackground)
                assertTrue(OxygenThemeColors.contrastRatio(colors.interactiveAccent,
                    colors.pageBackground) >= 4.5)
            }
        }
    }

    @Test fun sufficientlyDarkSeedsAreUnchangedAndLightSeedsOnlyGetDarker() {
        for (seed in seeds) {
            val result = OxygenThemeColors.toolbarBackground(seed)
            if (OxygenThemeColors.contrastRatio(OxygenThemeColors.WHITE, seed) >= 4.5) {
                assertEquals(seed, result)
            }
            for (shift in listOf(0, 8, 16)) {
                assertTrue(((result ushr shift) and 255) <= ((seed ushr shift) and 255))
            }
        }
    }

    @Test fun arbitraryRgbAndAlphaHaveDeterministicSafeOpaqueResults() {
        val random = Random(711)
        repeat(4096) {
            val raw = random.nextInt()
            val colors = resolve(raw, if (it % 2 == 0) "light" else "dark", false)
            assertEquals(raw, colors.rawAccent)
            assertEquals(255, colors.toolbarBackground ushr 24)
            assertEquals(colors.toolbarBackground, OxygenThemeColors.toolbarBackground(raw))
            assertTrue(OxygenThemeColors.contrastRatio(OxygenThemeColors.WHITE,
                colors.toolbarBackground) >= 4.5)
        }
        for (alpha in listOf(0, 1, 127, 254, 255)) for (rgb in seeds) {
            val raw = (alpha shl 24) or (rgb and 0xFFFFFF)
            assertEquals(raw, resolve(raw, "light", false).rawAccent)
            assertEquals(OxygenThemeColors.toolbarBackground(rgb),
                OxygenThemeColors.toolbarBackground(raw))
        }
    }

    @Test fun wcagReferenceAndTransparentForegroundAreComposited() {
        assertEquals(21.0, OxygenThemeColors.contrastRatio(-1, 0xFF000000.toInt()), 0.000001)
        assertEquals(1.0, OxygenThemeColors.contrastRatio(-1, -1), 0.000001)
        assertEquals(1.0, OxygenThemeColors.contrastRatio(0x00FFFFFF, 0xFF000000.toInt()), 0.000001)
        val halfWhite = OxygenThemeColors.contrastRatio(0x80FFFFFF.toInt(), 0xFF000000.toInt())
        assertTrue(halfWhite > 5.0 && halfWhite < 5.5)
        assertEquals(0xFF000000.toInt(), OxygenThemeColors.toolbarBackground(0))
        assertEquals(0xFF000000.toInt(), OxygenThemeColors.blend(-1, 0xFF000000.toInt(), 2.0))
        assertEquals(-1, OxygenThemeColors.blend(-1, 0xFF000000.toInt(), -1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun contrastRejectsUnresolvedTransparentBackgrounds() {
        OxygenThemeColors.contrastRatio(-1, 0x0042A5F5)
    }

    @Test fun systemLightDarkSystemSequenceAndUnknownModeFallback() {
        for (systemDark in listOf(false, true)) {
            val modes = listOf("system", "light", "dark", "system", "invalid")
            val expected = listOf(systemDark, false, true, systemDark, systemDark)
            assertEquals(expected, modes.map { resolve(seeds.first(), it, systemDark).dark })
            assertEquals(expected, modes.map { resolve(seeds.first(), it, systemDark).let { c ->
                !c.darkNavigationBarIcons
            } })
        }
    }

    @Test fun legacyPageColorsRemainIndependentFromToolbarPolicy() {
        val page = 0xFFEAE0C0.toInt()
        val text = 0xFF234567.toInt()
        val colors = OxygenThemeColors.resolve(false, "dark", true, seeds.first(), page, text)
        assertEquals(page, colors.pageBackground)
        assertEquals(text, colors.primaryText)
        assertEquals(page, colors.dialogBackground)
        assertEquals(seeds.first(), colors.rawAccent)
        assertEquals(-1, colors.toolbarForeground)
        assertTrue(OxygenThemeColors.contrastRatio(-1, colors.toolbarBackground) >= 4.5)
    }
}
