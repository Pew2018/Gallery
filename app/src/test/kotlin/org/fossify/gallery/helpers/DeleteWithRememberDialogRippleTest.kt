package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.views.DeleteConfirmationCheckboxRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DeleteWithRememberDialogRippleTest {
    private fun host(dark: Boolean) =
        Robolectric.buildActivity(org.fossify.gallery.activities.SimpleActivity::class.java).setup().get().apply {
            setTheme(if (dark) R.style.OxygenClassicAlertDialog_Dark else R.style.OxygenClassicAlertDialog_Light)
            baseConfig.isSystemThemeEnabled = false
            baseConfig.backgroundColor = if (dark) Color.BLACK else Color.WHITE
            baseConfig.textColor = if (dark) Color.WHITE else Color.BLACK
            baseConfig.primaryColor = Color.MAGENTA
        }

    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun layoutRow(row: View, width: Int = 320) {
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        row.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(
            row.width, row.height, android.graphics.Bitmap.Config.ARGB_8888
        )))
    }

    private fun tap(row: View, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        row.dispatchTouchEvent(down)
        down.recycle()
        shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
        val up = MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP, x, y, 0)
        row.dispatchTouchEvent(up)
        up.recycle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun bothRowsOwnBoundedRipplesAndClicksToggleExactlyOnceInLightAndDarkThemes() {
        for (dark in listOf(false, true)) {
            val activity = host(dark)
            var result: Pair<Boolean, Boolean>? = null
            DeleteWithRememberDialog(activity, "Delete these photos?", true, callback = { remember, skip ->
                result = remember to skip
            })
            val dialog = latestDialog()
            val first = dialog.findViewById<CheckBox>(R.id.delete_remember_checkbox)!!
            val second = dialog.findViewById<CheckBox>(R.id.skip_the_recycle_bin_checkbox)!!
            val rows = listOf(first, second).map { it.parent as DeleteConfirmationCheckboxRow }
            assertEquals(listOf(R.id.delete_remember_checkbox_row, R.id.skip_the_recycle_bin_checkbox_row),
                rows.map { it.id })
            assertTrue(first.text.isNotEmpty() && second.text.isNotEmpty())
            assertFalse(first.isClickable)
            assertFalse(second.isClickable)
            assertFalse(first.isFocusable)
            assertFalse(second.isFocusable)

            rows.forEachIndexed { index, row ->
                val checkbox = if (index == 0) first else second
                layoutRow(row)
                assertNull("checkbox must not own a competing ripple", checkbox.background)
                val ripple = row.background as RippleDrawable
                assertEquals(Rect(0, 0, row.width, row.height), ripple.bounds)
                val expectedAccent = OxygenClassicPalette.readableAccent(
                    Color.MAGENTA, if (dark) Color.rgb(48, 48, 48) else Color.WHITE
                )
                assertEquals(ColorUtils.setAlphaComponent(expectedAccent, 24), ripple.getColor()!!.defaultColor)
                assertEquals("row wrapper must preserve checkbox height", checkbox.measuredHeight, row.measuredHeight)

                val y = row.height / 2f
                for (x in listOf(12f, row.width * .65f)) {
                    val before = checkbox.isChecked
                    val beforeOther = if (index == 0) second.isChecked else first.isChecked
                    tap(row, x, y)
                    assertEquals(!before, checkbox.isChecked)
                    assertEquals(beforeOther, if (index == 0) second.isChecked else first.isChecked)
                }
                assertFalse("two taps toggle twice", checkbox.isChecked)
                repeat(3) { OxygenClassicDialog.apply(activity, dialog, dialog.window!!.decorView) }
                assertSame(ripple, row.background)
                assertNull(checkbox.background)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(false to false, result)
            activity.finish()
        }
    }

    @Test fun hiddenSkipChoiceRemovesItsWholeRowAndLeavesNoMeasuredGap() {
        val activity = host(false)
        DeleteWithRememberDialog(activity, "Delete these photos?", false, callback = { _, _ -> })
        val dialog = latestDialog()
        val row = dialog.findViewById<View>(R.id.skip_the_recycle_bin_checkbox_row)!!
        val checkbox = dialog.findViewById<CheckBox>(R.id.skip_the_recycle_bin_checkbox)!!
        assertEquals(View.GONE, row.visibility)
        assertEquals(0, row.measuredHeight)
        assertEquals(View.VISIBLE, checkbox.visibility)
        dialog.dismiss()
        activity.finish()
    }

    @Test fun confirmPassesBothCheckedValuesOnceAndCancelNeverRunsDeleteCallback() {
        val activity = host(false)
        var calls = 0
        var result: Pair<Boolean, Boolean>? = null
        DeleteWithRememberDialog(activity, "Delete these photos?", true, callback = { remember, skip ->
            calls++
            result = remember to skip
        })
        var dialog = latestDialog()
        (dialog.findViewById<View>(R.id.delete_remember_checkbox_row)!!).performClick()
        (dialog.findViewById<View>(R.id.skip_the_recycle_bin_checkbox_row)!!).performClick()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        assertEquals(true to true, result)

        result = null
        DeleteWithRememberDialog(activity, "Delete these photos?", true, callback = { _, _ -> calls++ })
        dialog = latestDialog()
        dialog.findViewById<View>(R.id.delete_remember_checkbox_row)!!.performClick()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        assertNull(result)
        activity.finish()
    }
}
