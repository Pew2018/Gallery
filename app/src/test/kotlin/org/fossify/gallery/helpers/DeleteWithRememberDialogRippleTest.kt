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

    private fun dispatchTouch(
        root: View, row: View, action: Int, x: Float, y: Float,
        downTime: Long, eventTime: Long
    ) {
        val rowLocation = IntArray(2)
        val rootLocation = IntArray(2)
        row.getLocationOnScreen(rowLocation)
        root.getLocationOnScreen(rootLocation)
        val event = MotionEvent.obtain(
            downTime, eventTime, action,
            rowLocation[0] - rootLocation[0] + x,
            rowLocation[1] - rootLocation[1] + y,
            0
        )
        root.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun tap(root: View, row: View, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        dispatchTouch(root, row, MotionEvent.ACTION_DOWN, x, y, now, now)
        shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
        dispatchTouch(root, row, MotionEvent.ACTION_UP, x, y,
            now, android.os.SystemClock.uptimeMillis())
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
            val decor = dialog.window!!.decorView
            val rows = listOf(first, second).map { it.parent as DeleteConfirmationCheckboxRow }
            assertEquals(listOf(R.id.delete_remember_checkbox_row, R.id.skip_the_recycle_bin_checkbox_row),
                rows.map { it.id })
            assertTrue(first.text.isNotEmpty() && second.text.isNotEmpty())
            assertTrue("native CheckBox click handling must remain enabled", first.isClickable)
            assertTrue("native CheckBox click handling must remain enabled", second.isClickable)
            assertTrue("native CheckBox must remain independently focusable", first.isFocusable)
            assertTrue("native CheckBox must remain independently focusable", second.isFocusable)

            rows.forEachIndexed { index, row ->
                val checkbox = if (index == 0) first else second
                assertFalse("row is a ripple host, not a competing click target", row.isClickable)
                assertNull("checkbox must not own a competing ripple", checkbox.background)
                val ripple = row.background as RippleDrawable
                assertEquals(Rect(0, 0, row.width, row.height), ripple.bounds)
                assertEquals("row wrapper must preserve checkbox height", checkbox.measuredHeight, row.measuredHeight)

                val y = row.height / 2f
                for (x in listOf(12f, row.width * .65f)) {
                    val before = checkbox.isChecked
                    val beforeOther = if (index == 0) second.isChecked else first.isChecked
                    tap(decor, row, x, y)
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

    @Test fun firstNativeTapTogglesImmediatelyAndCancelNeverToggles() {
        val activity = host(false)
        DeleteWithRememberDialog(activity, "Delete these photos?", true, callback = { _, _ -> })
        val dialog = latestDialog()
        val decor = dialog.window!!.decorView
        val checkbox = dialog.findViewById<CheckBox>(R.id.delete_remember_checkbox)!!
        val row = dialog.findViewById<DeleteConfirmationCheckboxRow>(R.id.delete_remember_checkbox_row)!!
        val x = row.width * .65f
        val y = row.height / 2f
        val downTime = android.os.SystemClock.uptimeMillis()

        dispatchTouch(decor, row, MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
        assertTrue("the row Ripple should enter pressed state on the initial down", row.isPressed)
        assertTrue("the native CheckBox should receive the same down event", checkbox.isPressed)
        dispatchTouch(decor, row, MotionEvent.ACTION_CANCEL, x, y,
            downTime, android.os.SystemClock.uptimeMillis())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("cancelled gesture must not toggle", checkbox.isChecked)
        assertFalse("cancel must release the row Ripple", row.isPressed)

        tap(decor, row, x, y)
        assertTrue("the first completed tap must toggle immediately", checkbox.isChecked)
        tap(decor, row, x, y)
        assertFalse("the next tap must toggle exactly once", checkbox.isChecked)
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
