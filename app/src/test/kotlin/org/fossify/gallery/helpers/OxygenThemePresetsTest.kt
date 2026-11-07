package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OxygenThemePresetsTest {
    @Test fun materialPaletteKeepsExactStableIdentityNameHexAndOrder() {
        val expected = listOf(
            Triple("material_blue", "蓝", "#2196F3"), Triple("material_teal", "青绿", "#009688"),
            Triple("material_green", "绿", "#4CAF50"), Triple("material_red", "红", "#F44336"),
            Triple("material_orange", "橙", "#FF9800"), Triple("material_purple", "紫", "#9C27B0"),
            Triple("material_cyan", "青", "#00BCD4"), Triple("material_indigo", "靛蓝", "#3F51B5"),
            Triple("material_pink", "粉", "#E91E63"), Triple("material_blue_grey", "蓝灰", "#607D8B"),
            Triple("material_deep_orange", "深橙", "#FF5722"), Triple("material_light_green", "浅绿", "#8BC34A"))
        assertEquals(expected, OxygenThemePresets.group("material").map { Triple(it.id, it.label, it.hex) })
    }

    @Test fun all23PresetsHaveStableUniqueIds() {
        assertEquals(23, OxygenThemePresets.all.size)
        assertEquals(23, OxygenThemePresets.byId.size)
        assertEquals(11, OxygenThemePresets.all.count { it.group == "classic" })
        assertEquals(12, OxygenThemePresets.all.count { it.group == "material" })
        assertTrue(OxygenThemePresets.all.all { it.hex.matches(Regex("^#[0-9A-Fa-f]{6}$")) })
    }
    @Test fun featuredGridAndBothPaletteSectionsKeepStableSelections() {
        assertEquals(7, OxygenThemePresets.featured.size)
        assertEquals(11, OxygenThemePresets.group("classic").size)
        assertEquals(12, OxygenThemePresets.group("material").size)
        assertTrue(OxygenThemePresets.featured.all { it in OxygenThemePresets.all })
        assertEquals(OxygenThemePresets.all,
            OxygenThemePresets.group("classic") + OxygenThemePresets.group("material"))
        assertTrue(OxygenThemePresets.byId.containsKey(OxygenThemePresets.DEFAULT_ID))
        for (candidate in OxygenThemePresets.all) {
            assertTrue(OxygenThemePresets.isSelected(true, candidate.id, candidate.id))
            assertEquals(1, OxygenThemePresets.all.count {
                OxygenThemePresets.isSelected(true, candidate.id, it.id)
            })
            assertTrue(OxygenThemePresets.all.none {
                OxygenThemePresets.isSelected(false, candidate.id, it.id)
            })
        }
        assertTrue(OxygenThemePresets.all.none {
            OxygenThemePresets.isSelected(true, "custom", it.id)
        })
        assertEquals(4, OxygenThemePresets.columnsForWidth(360, 1f))
        assertTrue(OxygenThemePresets.columnsForWidth(360, 1.5f) < 4)
        assertEquals(1, OxygenThemePresets.columnsForWidth(100, 2f))
    }

    @Test fun appearanceModeIsIndependentFromSystemAndAccent() {
        assertTrue(OxygenThemeEngine.resolvedDark("dark", false))
        assertTrue(OxygenThemeEngine.resolvedDark("dark", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("light", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("light", false))
        assertTrue(OxygenThemeEngine.resolvedDark("system", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("system", false))
    }

    @Suppress("DEPRECATION")
    @Test fun legacyToolbarResolverRetainsSignatureButUsesSafeWhiteBackground() {
        val legacy = 0xFFFAFAFA.toInt()
        val custom = 0xFF0071CD.toInt()
        for (activated in listOf(false, true)) for (customEnabled in listOf(false, true)) {
            val result = OxygenThemeEngine.resolveToolbarSurface(activated, customEnabled, custom, legacy)
            assertEquals(OxygenThemeColors.toolbarBackground(if (activated) custom else legacy), result)
            assertTrue(OxygenThemeEngine.contrastRatio(-1, result) >= 4.5)
        }
    }

    @Test fun autoToolbarForegroundPassesContrastReferenceSurfaces() {
        val surfaces = listOf("#212121", "#FFFFFF", "#FAFAFA", "#000000",
            "#0071CD", "#2196F3", "#E6A545", "#7DC22F")
        for (hex in surfaces) {
            val color = OxygenThemeEngine.parseHex(hex)!!
            val foreground = OxygenThemeEngine.autoForeground(color)
            assertTrue("Unexpected foreground for $hex", foreground == 0xFF000000.toInt() ||
                foreground == 0xFFFFFFFF.toInt())
            assertTrue("Insufficient contrast on $hex",
                OxygenThemeEngine.contrastRatio(foreground, color) >= 4.5)
        }
    }

    @Test fun strictHexAndRoundTrip() {
        assertEquals("#42A5F5", OxygenThemeEngine.formatHex(OxygenThemeEngine.parseHex("#42A5F5")!!))
        assertNull(OxygenThemeEngine.parseHex("42A5F5"))
        assertNull(OxygenThemeEngine.parseHex("#FFF"))
        assertNull(OxygenThemeEngine.parseHex("#00000000"))
        assertNull(OxygenThemeEngine.parseHex("#GGGGGG"))
    }
    @Test fun autoForegroundChoosesTheHigherContrastOfBlackAndWhite() {
        val surfaces = listOf("#212121", "#FFFFFF", "#FAFAFA", "#000000",
            "#0071CD", "#2196F3", "#E6A545", "#7DC22F", "#777777")
        for (hex in surfaces) {
            val background = OxygenThemeEngine.parseHex(hex)!!
            val blackContrast = OxygenThemeEngine.contrastRatio(
                android.graphics.Color.BLACK, background)
            val whiteContrast = OxygenThemeEngine.contrastRatio(
                android.graphics.Color.WHITE, background)
            val chosen = OxygenThemeEngine.autoForeground(background)
            val actual = OxygenThemeEngine.contrastRatio(chosen, background)
            assertEquals("Contrast choice for $hex", maxOf(blackContrast, whiteContrast), actual, 0.00001)
        }
    }

}
