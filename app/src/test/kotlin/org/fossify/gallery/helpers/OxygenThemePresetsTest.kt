package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OxygenThemePresetsTest {
    @Test fun all23PresetsHaveStableUniqueIds() {
        assertEquals(23, OxygenThemePresets.all.size)
        assertEquals(23, OxygenThemePresets.byId.size)
        assertEquals(11, OxygenThemePresets.all.count { it.group == "classic" })
        assertEquals(12, OxygenThemePresets.all.count { it.group == "material" })
        assertTrue(OxygenThemePresets.all.all { it.hex.matches(Regex("^#[0-9A-Fa-f]{6}$")) })
    }
    @Test fun appearanceModeIsIndependentFromSystemAndAccent() {
        assertTrue(OxygenThemeEngine.resolvedDark("dark", false))
        assertTrue(OxygenThemeEngine.resolvedDark("dark", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("light", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("light", false))
        assertTrue(OxygenThemeEngine.resolvedDark("system", true))
        org.junit.Assert.assertFalse(OxygenThemeEngine.resolvedDark("system", false))
    }

    @Test fun classicToolbarKeepsUserSelectionAndLegacyMode() {
        val legacy = 0xFFFAFAFA.toInt()
        val custom = 0xFF0071CD.toInt()
        assertEquals(legacy, OxygenThemeEngine.resolveToolbarSurface(false, false, custom, legacy))
        assertEquals(legacy, OxygenThemeEngine.resolveToolbarSurface(false, true, custom, legacy))
        assertEquals(0xFF212121.toInt(), OxygenThemeEngine.resolveToolbarSurface(true, false, custom, legacy))
        assertEquals(custom, OxygenThemeEngine.resolveToolbarSurface(true, true, custom, legacy))
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
                androidx.core.graphics.ColorUtils.calculateContrast(foreground, color) >= 4.5)
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
            val blackContrast = androidx.core.graphics.ColorUtils.calculateContrast(
                android.graphics.Color.BLACK, background)
            val whiteContrast = androidx.core.graphics.ColorUtils.calculateContrast(
                android.graphics.Color.WHITE, background)
            val chosen = OxygenThemeEngine.autoForeground(background)
            val actual = androidx.core.graphics.ColorUtils.calculateContrast(chosen, background)
            assertEquals("Contrast choice for $hex", maxOf(blackContrast, whiteContrast), actual, 0.00001)
        }
    }

}
