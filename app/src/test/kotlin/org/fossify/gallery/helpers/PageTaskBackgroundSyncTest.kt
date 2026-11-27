package org.fossify.gallery.helpers

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract between the theme token that Android 16 writes into the Activity
 * TaskDescription (android:colorBackground, resolved in onApplyThemeResource) and
 * the runtime page surface the user actually sees (OxygenThemeColors pageBackground).
 *
 * The Shell cross-Activity back animation paints its reveal background from
 * enteringTarget.taskInfo.taskDescription.backgroundColor, so any drift between the
 * resource below and the runtime constant reproduces the "near-black surround" on a
 * light page. This test pins the two to the same value for every appearance mode.
 *
 * The plumbing itself (SimpleActivity.onApplyThemeResource injecting
 * R.style.OxygenClassicTaskBackground) is verified by construction and by the
 * resource assertions here; the resulting on-device TaskDescription value is read
 * back by PredictiveBackDiagnostics when Gallery diagnostics are enabled.
 */
class PageTaskBackgroundSyncTest {
    private val values = File("src/main/res/values/oxygenos_classic.xml").readText()
    private val nightValues = File("src/main/res/values-night/classic_dialog_colors.xml").readText()

    private fun pageColor(xml: String): Int {
        val match = Regex("<color name=\"oxygen_classic_page\">(#[0-9A-Fa-f]{6})</color>").find(xml)
        assertTrue("oxygen_classic_page must be declared", match != null)
        return OxygenThemeEngine.parseHex(match!!.groupValues[1])!!
    }

    private fun runtimePage(dark: Boolean): Int = OxygenThemeColors.resolve(
        classic = true,
        mode = if (dark) "dark" else "light",
        systemDark = dark,
        rawAccent = 0xFF2196F3.toInt(),
        legacyBackground = 0,
        legacyText = 0,
    ).pageBackground

    @Test
    fun lightTaskBackgroundResourceMatchesRuntimePageSurface() {
        assertEquals(runtimePage(dark = false), pageColor(values))
    }

    @Test
    fun nightTaskBackgroundResourceMatchesRuntimePageSurface() {
        assertEquals(runtimePage(dark = true), pageColor(nightValues))
    }

    @Test
    fun taskBackgroundTokenDeclaresColorBackgroundFromPageColor() {
        val style = Regex("<style name=\"OxygenClassicTaskBackground\">(.*?)</style>", RegexOption.DOT_MATCHES_ALL)
            .find(values)
        assertTrue("OxygenClassicTaskBackground must be declared", style != null)
        assertTrue(
            "the token must resolve android:colorBackground from oxygen_classic_page",
            style!!.groupValues[1].contains("<item name=\"android:colorBackground\">@color/oxygen_classic_page</item>")
        )
    }

    @Test
    fun windowBackgroundUsesTheSamePageColor() {
        val styles = File("src/main/res/values/styles.xml").readText()
        val appTheme = Regex("<style name=\"AppTheme\"[^>]*>(.*?)</style>", RegexOption.DOT_MATCHES_ALL)
            .find(styles)
        assertTrue(appTheme != null)
        assertTrue(
            "AppTheme.windowBackground must stay on the page color",
            appTheme!!.groupValues[1].contains("<item name=\"android:windowBackground\">@color/oxygen_classic_page</item>")
        )
    }
}
