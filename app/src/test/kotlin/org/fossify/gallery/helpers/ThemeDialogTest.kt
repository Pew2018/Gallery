package org.fossify.gallery.helpers

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAppIconColors
import org.fossify.gallery.R
import org.fossify.gallery.activities.OxygenThemeActivity
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.extensions.config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ThemeDialogTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun content(dialog: AlertDialog) = dialog.findViewById<ClassicDialogScrollView>(R.id.classic_theme_dialog_content)!!
    private fun rows(dialog: AlertDialog) = descendants(content(dialog)).filter { it.isClickable && it.contentDescription != null }
    private fun noGlow(dialog: AlertDialog) {
        dialog.window!!.decorView.viewTreeObserver.dispatchOnGlobalLayout()
        descendants(dialog.window!!.decorView).forEach { assertEquals(it.javaClass.name, View.OVER_SCROLL_NEVER, it.overScrollMode) }
        assertEquals(R.style.OxygenClassicDialogWindow, dialog.window!!.attributes.windowAnimations)
    }

    @Test fun materialHasExactOrderPassiveRadiosSingleCommitAndSafeCancel() {
        val controller = Robolectric.buildActivity(OxygenThemeActivity::class.java).setup()
        val host = controller.get()
        try {
            host.config.oxygenThemeActivated = true
            host.config.oxygenThemePresetId = "oxygen_blue"
            var commits = 0
            val dialog = MaterialColorPickerDialog.show(host, { it.id }) { commits++; host.config.oxygenThemePresetId = it.id }!!
            val options = rows(dialog)
            assertEquals(OxygenThemePresets.group("material").map { it.id }, options.map { it.contentDescription.toString() })
            assertEquals(12, options.size)
            assertTrue(options.none { it.isSelected })
            descendants(content(dialog)).filterIsInstance<RadioButton>().forEach { assertFalse(it.isClickable); assertFalse(it.isFocusable) }
            noGlow(dialog)
            assertNull(MaterialColorPickerDialog.show(host, { it.id }) { commits++ })
            options[3].performClick(); options[3].performClick()
            assertEquals(1, commits)
            assertEquals("material_red", host.config.oxygenThemePresetId)
            assertFalse(host.isFinishing)
            val again = MaterialColorPickerDialog.show(host, { it.id }) { commits++ }!!
            assertEquals(listOf("material_red"), rows(again).filter { it.isSelected }.map { it.contentDescription.toString() })
            again.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertEquals(1, commits)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun iconPreviewCancelApplyAndHostDestroyRespectCommitBoundary() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val host = controller.get()
        val colors = host.getAppIconColors()
        host.config.appIconColor = colors[0]
        try {
            val dialog = AppIconPickerDialog.show(host)!!
            assertEquals(host.getAppIconIDs().size, rows(dialog).size)
            noGlow(dialog)
            val ripple = rows(dialog)[1].background
            rows(dialog)[1].performClick(); rows(dialog)[2].performClick()
            assertSame("Selection must retain the running bounded ripple", ripple, rows(dialog)[1].background)
            assertEquals(colors[0], host.config.appIconColor)
            assertTrue(descendants(content(dialog)).filterIsInstance<TextView>().any { it.text == OxygenThemeEngine.formatHex(colors[2]) })
            dialog.cancel()
            assertEquals(colors[0], host.config.appIconColor)
            val apply = AppIconPickerDialog.show(host)!!
            rows(apply)[1].performClick()
            apply.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(colors[1], host.config.appIconColor)
            val abandoned = AppIconPickerDialog.show(host)!!
            rows(abandoned)[2].performClick()
            controller.pause().stop().destroy()
            assertFalse(abandoned.isShowing)
            assertEquals(colors[1], host.config.appIconColor)
        } finally {
            if (!host.isDestroyed) controller.pause().stop().destroy()
        }
    }

    @Test fun customUsesEffectiveColorValidatesEveryEditAndOnlyCommitsOnSave() {
        val controller = Robolectric.buildActivity(OxygenThemeActivity::class.java).setup()
        val host = controller.get()
        try {
            for (preset in listOf("oxygen_jade_green", "material_red", "custom", "system")) {
                host.config.oxygenThemeActivated = preset != "system"
                host.config.oxygenThemePresetId = preset
                host.config.oxygenCustomAccent = 0xFF123456.toInt()
                val effective = OxygenThemeEngine.snapshot(host, host.config).rawAccent
                var saved: Int? = null
                val dialog = ClassicCustomColorDialog.show(host) { saved = it }!!
                val all = descendants(content(dialog))
                val field = all.filterIsInstance<EditText>().single()
                val currentHex = all.filterIsInstance<TextView>().first { it !== field && it.text == OxygenThemeEngine.formatHex(effective) }
                assertEquals(OxygenThemeEngine.formatHex(effective), field.text.toString())
                noGlow(dialog)
                val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                for (invalid in listOf("", "#", "#12345", "#GGGGGG", "#12345678", "123456", "#fff")) {
                    field.setText(invalid)
                    assertFalse(save.isEnabled)
                    assertNotNull(field.error)
                    save.performClick()
                    assertNull(saved)
                    assertEquals(OxygenThemeEngine.formatHex(effective), currentHex.text)
                    assertTrue(all.filterIsInstance<TextView>().any { it.text == "—" })
                }
                field.setText("  #aBc123  ")
                assertTrue(save.isEnabled)
                assertNull(field.error)
                assertTrue(all.filterIsInstance<TextView>().any { it.text == "#ABC123" })
                assertEquals(OxygenThemeEngine.formatHex(effective), currentHex.text)
                save.performClick(); save.performClick()
                assertEquals(0xFFABC123.toInt(), saved)
                assertFalse(dialog.isShowing)
                val cancelled = ClassicCustomColorDialog.show(host) { fail("Cancel must not save") }!!
                descendants(content(cancelled)).filterIsInstance<EditText>().single().setText("#FFFFFF")
                cancelled.cancel()
                assertEquals(0xFF123456.toInt(), host.config.oxygenCustomAccent)
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun settingsIconEntryDoesNotNavigateAndGeneralDiagnosticsRemainsRegistered() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val host = controller.get()
        try {
            val shadow = shadowOf(host)
            while (shadow.nextStartedActivity != null) { }
            host.findViewById<View>(R.id.settings_app_icon_holder).performClick()
            assertNull(shadow.nextStartedActivity)
            assertTrue((host.window.decorView.getTag(R.id.classic_theme_dialog_active) as AlertDialog).isShowing)
            assertNotNull(host.findViewById<View>(R.id.settings_diagnostics_logs_holder))
            assertEquals(0, host.resources.getIdentifier("settings_refresh_diagnostics_holder", "id", host.packageName))
        } finally { controller.pause().stop().destroy() }
    }
    @Test @Config(sdk = [26, 31], qualifiers = "w360dp-h640dp")
    fun themeDialogsHaveOwnWidthProfileAndRenderWithoutOverscrollInBothModes() {
        val controller = Robolectric.buildActivity(OxygenThemeActivity::class.java).setup()
        val host = controller.get()
        try {
            for (mode in listOf("light", "dark")) {
                host.config.oxygenThemeActivated = true
                host.config.oxygenAppearanceMode = mode
                OxygenThemeEngine.apply(host, host.config)
                val factories = listOf<() -> AlertDialog>(
                    { MaterialColorPickerDialog.show(host, { it.label }) { }!! },
                    { AppIconPickerDialog.show(host)!! },
                    { ClassicCustomColorDialog.show(host) { }!! })
                factories.forEachIndexed { index, open ->
                    val dialog = open()
                    val decor = dialog.window!!.decorView
                    val width = OxygenClassicDialog.widthForContent(OxygenClassicDialog.availableWidth(host),
                        host.resources.displayMetrics.density, 400)
                    assertEquals(width, OxygenClassicDialog.prepareWindow(host, dialog).width)
                    val height = OxygenClassicDialog.availableHeight(host)
                    repeat(2) {
                        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    }
                    noGlow(dialog)
                    assertTrue(content(dialog).height > 0)
                    val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
                    decor.draw(android.graphics.Canvas(bitmap))
                    val output = java.io.File("build/reports/ui-regression/theme-dialogs").apply { mkdirs() }
                    java.io.File(output, "theme-$index-$mode-${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    dialog.dismiss()
                }
                val compact = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "Existing compact"))
                assertEquals(OxygenClassicDialog.widthFor(OxygenClassicDialog.availableWidth(host), host.resources.displayMetrics.density),
                    OxygenClassicDialog.prepareWindow(host, compact).width)
                compact.dismiss()
            }
        } finally { controller.pause().stop().destroy() }
    }

}
