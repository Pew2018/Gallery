package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.fossify.gallery.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotoProfileEditUiTest {
    @Test fun nameLabelAndPageInputUseReadableClassicThemeColors() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context).inflate(
            R.layout.activity_photo_profile_edit, FrameLayout(context), false
        )
        val label = root.findViewById<TextView>(R.id.photo_profile_edit_name_label)
        val field = root.findViewById<EditText>(R.id.photo_profile_edit_name)
        val scroll = root.findViewById<ScrollView>(R.id.photo_profile_edit_scroll)
        assertNotNull(label)
        assertNotNull(field)
        assertNotNull(scroll)
        assertEquals(12f, label.textSize / context.resources.displayMetrics.scaledDensity, 0.1f)

        val accents = listOf(0xFF7DC22F.toInt(), 0xFF42A5F5.toInt(), 0xFFB0BEC5.toInt())
        for (dark in listOf(false, true)) for (accent in accents) {
            val tokens = OxygenThemeColors.resolve(
                classic = true,
                mode = if (dark) "dark" else "light",
                systemDark = dark,
                rawAccent = accent,
                legacyBackground = Color.WHITE,
                legacyText = Color.BLACK
            )
            label.setTextColor(tokens.secondaryText)
            OxygenClassicPageInput.style(field, tokens)
            assertEquals(tokens.primaryText, field.currentTextColor)
            assertEquals(tokens.secondaryText, field.currentHintTextColor)
            val tint = field.backgroundTintList!!
            val focusUnderline = tint.getColorForState(
                intArrayOf(android.R.attr.state_focused), tint.defaultColor
            )
            val idleUnderline = tint.defaultColor
            assertEquals(tokens.interactiveAccent, focusUnderline)
            assertTrue(OxygenThemeColors.contrastRatio(field.currentTextColor, tokens.pageBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(field.currentHintTextColor, tokens.pageBackground) >= 4.5)
            assertTrue(OxygenThemeColors.contrastRatio(focusUnderline, tokens.pageBackground) >= 3.0)
            assertTrue(OxygenThemeColors.contrastRatio(idleUnderline, tokens.pageBackground) >= 3.0)
        }
    }

    @Test fun legacyThemeTextIsAdjustedOnlyWhenThePagePairLacksContrast() {
        val context = RuntimeEnvironment.getApplication()
        val field = EditText(context)
        val darkPage = OxygenThemeColors.resolve(
            classic = false,
            mode = "system",
            systemDark = false,
            rawAccent = 0xFFB0BEC5.toInt(),
            legacyBackground = Color.BLACK,
            legacyText = 0xFF303030.toInt()
        )
        OxygenClassicPageInput.style(field, darkPage)
        val safeText = OxygenClassicPageInput.readableTextColor(darkPage.primaryText, darkPage)
        assertEquals(safeText, field.currentTextColor)
        assertTrue(OxygenThemeColors.contrastRatio(safeText, darkPage.pageBackground) >= 4.5)
        assertTrue(OxygenThemeColors.contrastRatio(field.currentHintTextColor, darkPage.pageBackground) >= 4.5)
    }

    @Test fun inputSemanticsAndCompactScrollableLayoutAreRetained() {
        val context = RuntimeEnvironment.getApplication()
        val root = LayoutInflater.from(context).inflate(
            R.layout.activity_photo_profile_edit, FrameLayout(context), false
        )
        val field = root.findViewById<EditText>(R.id.photo_profile_edit_name)
        val label = root.findViewById<TextView>(R.id.photo_profile_edit_name_label)
        val sourcesTitle = root.findViewById<TextView>(R.id.photo_profile_edit_sources_title)
        val addSource = root.findViewById<TextView>(R.id.photo_profile_edit_add_source)
        assertTrue(field.isSingleLine)
        assertEquals((52 * context.resources.displayMetrics.density).toInt(), field.minHeight)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, label.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, sourcesTitle.layoutParams.height)
        assertEquals((48 * context.resources.displayMetrics.density).toInt(), addSource.layoutParams.height)
        assertTrue(root.findViewById<ScrollView>(R.id.photo_profile_edit_scroll).isFillViewport)
    }
}
