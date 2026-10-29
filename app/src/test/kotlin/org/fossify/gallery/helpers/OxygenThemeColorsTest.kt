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

    @Test fun switchKnobPreservesOriginalThemeRgbAcrossPresetsAndCustomColors() {
        val accents = seeds + OxygenThemePresets.all.map { it.color } +
            listOf("#FFFFFF", "#000000", "#FFFF00", "#00FFFF", "#FF5722").map {
                OxygenThemeEngine.parseHex(it)!!
            }
        for (seed in accents) for (mode in listOf("light", "dark", "system")) {
            for (systemDark in listOf(false, true)) {
                val tokens = resolve(seed, mode, systemDark)
                val palette = OxygenThemeColors.switchPalette(tokens)
                assertEquals("switch on thumb must keep theme RGB", OxygenThemeColors.opaque(seed), palette.onThumb)
                assertEquals(seed, tokens.rawAccent)
                for (color in listOf(palette.onThumb, palette.onTrack, palette.offThumb,
                    palette.offTrack, palette.disabledOnThumb, palette.disabledOnTrack,
                    palette.disabledOffThumb, palette.disabledOffTrack)) {
                    assertEquals(255, color ushr 24)
                }
                assertNotEquals(palette.onTrack, palette.offTrack)
                assertNotEquals(palette.onThumb, palette.disabledOnThumb)
            }
        }
    }

    @Test fun switchUsesThemeSeedRatherThanTextContrastAdjustment() {
        val brightBlue = 0xFF42A5F5.toInt()
        val green = 0xFF7DC22F.toInt()
        val blueTokens = resolve(brightBlue, "light", false)
        val greenTokens = resolve(green, "light", false)
        assertEquals(brightBlue, OxygenThemeColors.switchPalette(blueTokens).onThumb)
        assertEquals(green, OxygenThemeColors.switchPalette(greenTokens).onThumb)
        assertNotEquals(blueTokens.interactiveAccent,
            OxygenThemeColors.switchPalette(blueTokens).onThumb)
        assertNotEquals(OxygenThemeColors.switchPalette(blueTokens).onTrack,
            OxygenThemeColors.switchPalette(greenTokens).onTrack)
        assertEquals(OxygenThemeColors.switchPalette(blueTokens).offTrack,
            OxygenThemeColors.switchPalette(greenTokens).offTrack)
    }

    @Test fun progressPaletteMatchesContentAndThemeInEveryAppearanceMode() {
        val accents = seeds + OxygenThemePresets.all.map { it.color } +
            listOf("#00FFFF", "#FFFF00", "#FFFFFF", "#000000").map {
                OxygenThemeEngine.parseHex(it)!!
            }
        for (seed in accents) for (mode in listOf("light", "dark", "system")) {
            for (systemDark in listOf(false, true)) {
                val colors = resolve(seed, mode, systemDark)
                val progress = OxygenThemeColors.progressPalette(colors)
                assertEquals(colors.pageBackground, progress.surface)
                assertEquals(255, progress.indicator ushr 24)
                assertEquals(255, progress.track ushr 24)
                assertEquals(if (colors.dark) 0xFF484848.toInt() else 0xFFE0E0E0.toInt(),
                    progress.track)
                assertTrue("indicator on page: seed=$seed mode=$mode",
                    OxygenThemeColors.contrastRatio(progress.indicator, progress.surface) >= 4.5)
                assertTrue("indicator on track: seed=$seed mode=$mode",
                    OxygenThemeColors.contrastRatio(progress.indicator, progress.track) >= 3.0)
                assertTrue(progress.indicator != progress.track)
                assertEquals(seed, colors.rawAccent)
            }
        }
    }

    @Test fun progressColorsFollowBlueAndTealThemesRatherThanToolbarWhite() {
        for (seed in listOf(0xFF0071CD.toInt(), 0xFF01998C.toInt())) {
            val light = OxygenThemeColors.progressPalette(resolve(seed, "light", false))
            val dark = OxygenThemeColors.progressPalette(resolve(seed, "dark", false))
            assertEquals(0xFFE0E0E0.toInt(), light.track)
            assertEquals(0xFF484848.toInt(), dark.track)
            assertNotEquals(OxygenThemeColors.WHITE, light.indicator)
            assertNotEquals(OxygenThemeColors.WHITE, dark.indicator)
            assertNotEquals(light.indicator, dark.indicator)
        }
    }

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

    @Test fun all23PresetsAndRequiredCustomColorsMeetContrastAndStatusHierarchy() {
        val custom = listOf("#FFFFFF", "#000000", "#FFFF00", "#00FF00", "#00FFFF",
            "#FF00FF", "#808080", "#42A5F5", "#0071CD")
        val all = OxygenThemePresets.all.map { it.color } + custom.map {
            OxygenThemeEngine.parseHex(it)!!
        }
        assertEquals(32, all.size)
        for (seed in all) for (dark in listOf(false, true)) {
            val tokens = resolve(seed, if (dark) "dark" else "light", dark)
            assertTrue(OxygenThemeColors.contrastRatio(OxygenThemeColors.WHITE,
                tokens.toolbarBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(tokens.interactiveAccent,
                tokens.pageBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(OxygenThemeColors.WHITE,
                tokens.statusBarBackground) >= OxygenThemeColors.contrastRatio(
                OxygenThemeColors.WHITE, tokens.toolbarBackground))
            assertTrue(tokens.lightStatusBarIcons)
            assertEquals(!dark, tokens.darkNavigationBarIcons)
            assertEquals(seed, tokens.rawAccent)
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
    @Test fun offSwitchPaletteIsNeutralAndSeparatelyWeakenedInBothAppearances() {
        for (mode in listOf("light", "dark")) {
            val palettes = seeds.map { OxygenThemeColors.switchPalette(resolve(it, mode, false)) }
            assertEquals(1, palettes.map { it.offThumb }.distinct().size)
            assertEquals(1, palettes.map { it.offTrack }.distinct().size)
            assertEquals(1, palettes.map { it.disabledOffThumb }.distinct().size)
            val p = palettes.first()
            assertEquals(if (mode == "light") 0xFFEEEEEE.toInt() else 0xFFCCCCCC.toInt(), p.offThumb)
            assertEquals(if (mode == "light") 0xFFDFDFDF.toInt() else 0xFF737373.toInt(), p.offTrack)
            assertNotEquals(p.offThumb, p.disabledOffThumb)
            assertNotEquals(p.offTrack, p.disabledOffTrack)
        }
    }

}
