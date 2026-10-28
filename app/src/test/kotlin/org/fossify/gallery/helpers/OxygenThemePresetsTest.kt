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

    @Test fun strictHexAndRoundTrip() {
        assertEquals("#42A5F5", OxygenThemeEngine.formatHex(OxygenThemeEngine.parseHex("#42A5F5")!!))
        assertNull(OxygenThemeEngine.parseHex("42A5F5"))
        assertNull(OxygenThemeEngine.parseHex("#FFF"))
        assertNull(OxygenThemeEngine.parseHex("#00000000"))
        assertNull(OxygenThemeEngine.parseHex("#GGGGGG"))
    }
}
