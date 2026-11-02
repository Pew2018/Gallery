package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TargetedCheckboxContrastTest {
    @Test fun dateTimeCheckboxPaintsClassicSwitchUsingDynamicReadableAccent() {
        for (raw in listOf(0xFFFFF59D, 0xFF2196F3, 0xFF311B92)) {
            val surface = 0xFFFFFFFF.toInt()
            val accent = OxygenClassicPalette.readableAccent(raw.toInt(), surface)
            val tokens = OxygenThemeColors.resolve(true, "light", false, raw.toInt(), surface, 0xFF212121.toInt())
                .copy(pageBackground = surface, rawAccent = accent, interactiveAccent = accent)
            val toggle = OxygenClassicControls.dateTimeSwitchDrawable(1f, tokens)
            assertEquals(48, toggle.intrinsicWidth)
            assertEquals(28, toggle.intrinsicHeight)
            toggle.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            toggle.setBounds(0, 0, 48, 28)
            val bitmap = Bitmap.createBitmap(48, 28, Bitmap.Config.ARGB_8888)
            toggle.draw(Canvas(bitmap))
            assertEquals(accent, bitmap.getPixel(34, 14))
            assertTrue(OxygenThemeColors.contrastRatio(accent, surface) >= 4.5)
        }
    }

    @Test fun sixAccentsUseSurfaceReadableFillAndContrastingCheckInBothModes() {
        val accents = listOf(0xFFFFF59D, 0xFFF44336, 0xFF4CAF50, 0xFF2196F3, 0xFF311B92, 0xFFFFEEEE).map { it.toInt() }
        for (dark in listOf(false, true)) for (raw in accents) {
            val surface = if (dark) 0xFF303030.toInt() else 0xFFFFFFFF.toInt()
            val accent = OxygenClassicPalette.readableAccent(raw, surface)
            val tokens = OxygenThemeColors.resolve(true, if (dark) "dark" else "light", dark, raw, surface, 0xFF212121.toInt())
                .copy(pageBackground = surface, interactiveAccent = accent)
            val mark = OxygenClassicControls.selectionDrawable(false, 1f, tokens, surfaceAccent = true)
            mark.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
            mark.setBounds(0, 0, 24, 24)
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            mark.draw(Canvas(bitmap))
            assertEquals(accent, bitmap.getPixel(4, 4))
            assertTrue(OxygenThemeColors.contrastRatio(accent, surface) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(OxygenThemeColors.autoForeground(accent), accent) >= 4.5)
            assertEquals(raw, tokens.rawAccent)
        }
    }
}
