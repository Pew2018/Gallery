package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputLayout
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE, application = Application::class,
    qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicDialogLayoutRegressionTest {
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(R.style.OxygenClassicAlertDialog_Light)
        baseConfig.backgroundColor = Color.WHITE
        baseConfig.textColor = Color.BLACK
        baseConfig.primaryColor = Color.GREEN
        baseConfig.isSystemThemeEnabled = false
    }

    private fun measure(dialog: AlertDialog) {
        val root = dialog.window!!.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(dialog.window!!.attributes.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    @Test fun existingTextInputFormsKeepOneClassicOutlineAcrossAllAuditedLayouts() {
        val host = activity()
        val tokens = OxygenThemeEngine.snapshot(host, host.config)
        val layouts = listOf(
            R.layout.dialog_create_new_folder,
            R.layout.dialog_custom_aspect_ratio,
            R.layout.dialog_export_favorites,
            R.layout.dialog_export_settings,
            R.layout.dialog_resize_image,
            R.layout.dialog_resize_image_with_path,
            R.layout.dialog_resize_multiple_images,
            R.layout.dialog_save_as,
            R.layout.dialog_slideshow
        )
        for (layoutId in layouts) {
            val root = LayoutInflater.from(host).inflate(layoutId, null, false)
            fun visit(view: View) {
                if (view is TextInputLayout) {
                    OxygenClassicPageInput.style(view, tokens, tokens.dialogBackground)
                    assertEquals("layout $layoutId", TextInputLayout.BOX_BACKGROUND_OUTLINE, view.boxBackgroundMode)
                    assertEquals("layout $layoutId", null, view.editText?.background)
                    assertTrue("layout $layoutId", (view.editText?.minimumHeight ?: 0) >= 56)
                }
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(root)
            assertTrue("layout $layoutId contains at least one form field", root.findViewById<View>(R.id.folder_path_hint) != null ||
                root.findViewById<View>(R.id.aspect_ratio_width_hint) != null ||
                root.findViewById<View>(R.id.export_favorites_hint) != null ||
                root.findViewById<View>(R.id.export_settings_hint) != null ||
                root.findViewById<View>(R.id.resize_image_width_hint) != null ||
                root.findViewById<View>(R.id.resize_factor_input_layout) != null ||
                root.findViewById<View>(R.id.folder_hint) != null ||
                root.findViewById<View>(R.id.interval_hint) != null)
        }
        host.finish()
    }

    @Test fun confirmationsKeepCallbacksAndATrailingAccessibleActionGroupAfterRepeatedStyling() {
        val host = activity()
        var accepted = 0
        val dialog = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "")
            .setMessage("Delete this file?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> accepted++ })
        val root = dialog.window!!.decorView
        repeat(3) { OxygenClassicDialog.apply(host, dialog, root); measure(dialog) }
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val row = ok.parent as LinearLayout
        assertEquals(Gravity.END, row.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertTrue(ok.height >= 48 && cancel.height >= 48)
        assertEquals(Gravity.CENTER, ok.gravity)
        val rect = Rect(0, 0, ok.width, ok.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(ok, rect)
        assertTrue("action remains inside the surface", rect.right <= root.width - 16)
        assertTrue("positive action is at the trailing edge", root.width - rect.right <= 32)
        assertTrue(cancel.right <= ok.left)
        ok.performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, accepted)
        assertFalse(dialog.isShowing)
        host.finish()
    }

    @Test fun commonsAndGalleryExportFormsShareWidthAndKeepBindingCompatibleInputs() {
        val host = activity()
        val commons = org.fossify.commons.databinding.DialogExportSettingsBinding.inflate(host.layoutInflater)
        val gallery = org.fossify.gallery.databinding.DialogExportFavoritesBinding.inflate(host.layoutInflater)
        fun show(view: View) = OxygenClassicDialog.show(host,
            OxygenClassicDialog.builder(host, "Export").setView(view)
                .setNegativeButton("Cancel", null).setPositiveButton("OK", null), view)
        val first = show(commons.root)
        val expected = first.window!!.attributes.width
        assertTrue(commons.root is ClassicDialogScrollView)
        assertTrue(commons.exportSettingsFilename.maxLines == 1)
        first.dismiss()
        val second = show(gallery.root)
        assertEquals(expected, second.window!!.attributes.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, second.window!!.attributes.height)
        assertTrue(gallery.exportFavoritesFilename.maxLines == 1)
        second.dismiss()
        host.finish()
    }

    @Test fun dateSettingLabelTogglesOriginalCheckboxWithoutReversingReadingDirection() {
        val host = activity()
        val root = LayoutInflater.from(host).inflate(R.layout.dialog_change_date_time_format, null)
        val check = root.findViewById<CheckBox>(R.id.change_date_time_dialog_24_hour)
        val row = check.parent as LinearLayout
        assertEquals(View.LAYOUT_DIRECTION_LTR, row.layoutDirection)
        var changes = 0
        check.setOnCheckedChangeListener { _, _ -> changes++ }
        val original = check.isChecked
        row.performClick()
        assertEquals(!original, check.isChecked)
        assertEquals(1, changes)
        check.performClick()
        assertEquals(original, check.isChecked)
        assertEquals(2, changes)
        host.finish()
    }

    @Test fun dragGlyphHasExactlyTwoHorizontalBands() {
        val host = activity()
        val drawable = AppCompatResources.getDrawable(host, R.drawable.oxygen_classic_drag_handle)!!
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 24, 24)
        drawable.draw(Canvas(bitmap))
        val rows = (0 until 24).filter { y -> (0 until 24).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 } }
        assertEquals(2, rows.withIndex().count { (index, value) -> index == 0 || value != rows[index - 1] + 1 })
        host.finish()
    }

    @Test fun realSecurityPagesKeepSquarePatternAndUsablePinTargetsAtLargeFont() {
        val host = activity()
        val inflater = LayoutInflater.from(host)
        for (fontScale in listOf(1f, 1.3f, 1.5f)) {
            val config = android.content.res.Configuration(host.resources.configuration).apply {
                this.fontScale = fontScale
            }
            @Suppress("DEPRECATION")
            host.resources.updateConfiguration(config, host.resources.displayMetrics)
            for (width in listOf(288, 328, 379, 380)) {
                val pattern = inflater.inflate(R.layout.tab_pattern, null)
                val pin = inflater.inflate(R.layout.tab_pin, null)
                for (page in listOf(pattern, pin)) {
                    page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    page.layout(0, 0, page.measuredWidth, page.measuredHeight)
                }
                val grid = pattern.findViewById<View>(R.id.pattern_lock_view)
                assertEquals("pattern must stay square", grid.width, grid.height)
                assertTrue("pattern must not collapse", grid.width >= 240)
                for (id in listOf(R.id.pin_1, R.id.pin_5, R.id.pin_9, R.id.pin_c, R.id.pin_0, R.id.pin_ok)) {
                    val key = pin.findViewById<View>(id)
                    assertTrue("PIN target must be usable", key.height >= 56 && key.width >= 48)
                    assertTrue("PIN target must stay in its page", key.bottom <= pin.height)
                }
            }
        }
        host.finish()
    }

    @Test fun nestedConfirmationUsesLighterScrimAndLeavesParentGeometryUntouched() {
        val host = activity()
        val parent = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "Properties")
            .setView(TextView(host).apply { id = R.id.properties_scrollview; text = "Photo details" })
            .setPositiveButton("OK", null))
        val width = parent.window!!.attributes.width
        val child = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "")
            .setMessage("Remove EXIF?").setNegativeButton("Cancel", null).setPositiveButton("OK", null))
        assertEquals(0.20f, child.window!!.attributes.dimAmount, 0.001f)
        child.cancel()
        assertTrue(parent.isShowing)
        assertEquals(width, parent.window!!.attributes.width)
        assertEquals(0.45f, parent.window!!.attributes.dimAmount, 0.001f)
        parent.dismiss()
        host.finish()
    }

    @Test fun singleCancelDoesNotKeepAlertControllersCenteringWeight() {
        val host = activity()
        val dialog = OxygenClassicDialog.show(host, OxygenClassicDialog.builder(host, "")
            .setView(TextView(host).apply { text = "Security input" })
            .setNegativeButton("Cancel", null))
        measure(dialog)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val lp = cancel.layoutParams as LinearLayout.LayoutParams
        assertEquals(0f, lp.weight, 0f)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lp.width)
        assertEquals(Gravity.END, (cancel.parent as LinearLayout).gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        dialog.dismiss()
        host.finish()
    }
}
