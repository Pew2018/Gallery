package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogManageBottomActionsBinding
import org.fossify.gallery.databinding.DialogManageExtendedDetailsBinding
import org.fossify.gallery.dialogs.ClassicPasswordMethodDialog
import org.fossify.gallery.views.ClassicDialogCheckboxRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    qualifiers = "w411dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class DialogUiRepairTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun host(dark: Boolean = false) = Robolectric.buildActivity(Activity::class.java).setup().get().apply {
        setTheme(if (dark) R.style.OxygenClassicAlertDialog_Dark else R.style.OxygenClassicAlertDialog_Light)
        baseConfig.isSystemThemeEnabled = false
        baseConfig.backgroundColor = if (dark) Color.BLACK else Color.WHITE
        baseConfig.textColor = if (dark) Color.WHITE else Color.BLACK
        baseConfig.primaryColor = Color.MAGENTA
    }

    @Test fun all24BindingCheckboxesUseOneBoundedRowRippleAndOriginalClickPath() {
        for (dark in listOf(false, true)) {
            val activity = host(dark)
            val roots = listOf(DialogManageExtendedDetailsBinding.inflate(activity.layoutInflater).root,
                DialogManageBottomActionsBinding.inflate(activity.layoutInflater).root)
            for ((index, root) in roots.withIndex()) {
                val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Options")
                    .setView(root).setNegativeButton("Cancel", null).setPositiveButton("Save", null), root)
                val checks = descendants(root).filterIsInstance<CheckBox>()
                assertEquals(if (index == 0) 9 else 15, checks.size)
                val rows = checks.map { it.parent as ClassicDialogCheckboxRow }
                for (check in checks) {
                    val row = check.parent as ClassicDialogCheckboxRow
                    row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    row.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(row.width, row.height, android.graphics.Bitmap.Config.ARGB_8888)))
                    assertTrue(row.height >= 56)
                    assertNull(check.background)
                    assertFalse(check.isClickable)
                    val ripple = row.background as RippleDrawable
                    assertEquals(Rect(0, 0, row.width, row.height), ripple.bounds)
                    assertEquals(24, (check.buttonDrawable as android.graphics.drawable.InsetDrawable).drawable!!.intrinsicWidth)
                    assertEquals(16f, check.textSize / activity.resources.displayMetrics.scaledDensity, .01f)
                    var changes = 0
                    check.setOnCheckedChangeListener { _, _ -> changes++ }
                    // Icon, text, trailing blank and the last pixel inside this row.
                    for ((x, y) in listOf(12f to 28f, 120f to 28f, 318f to 28f, 160f to (row.height - 1f))) {
                        val before = checks.map { it.isChecked }
                        val oldChanges = changes
                        val now = android.os.SystemClock.uptimeMillis()
                        val down = android.view.MotionEvent.obtain(now, now, 0, x, y, 0)
                        row.dispatchTouchEvent(down); down.recycle()
                        shadowOf(Looper.getMainLooper()).idleFor(120, java.util.concurrent.TimeUnit.MILLISECONDS)
                        val up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), 1, x, y, 0)
                        row.dispatchTouchEvent(up); up.recycle()
                        shadowOf(Looper.getMainLooper()).idle()
                        assertEquals(oldChanges + 1, changes)
                        checks.forEachIndexed { i, other -> assertEquals(if (other === check) !before[i] else before[i], other.isChecked) }
                    }
                    repeat(5) { row.performClick() }
                    assertEquals(9, changes)
                    check.performClick()
                    assertEquals(10, changes)
                    repeat(3) { OxygenClassicDialog.apply(activity, dialog, root) }
                    assertSame(ripple, row.background)
                    assertNull(check.background)
                }
                assertEquals(checks.size, rows.map { it.background }.distinct().size)
                dialog.dismiss()
            }
            activity.finish()
        }
    }

    @Test fun realDialogsSaveOriginalMasksAndCancelWithoutChangingPreferences() {
        val controller = Robolectric.buildActivity(org.fossify.gallery.activities.SimpleActivity::class.java).setup()
        val activity = controller.get()
        activity.config.extendedDetails = EXT_NAME or EXT_PATH
        activity.config.visibleBottomActions = BOTTOM_ACTION_EDIT or BOTTOM_ACTION_SHARE
        var result = -1
        fun latest() = org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
        try {
            org.fossify.gallery.dialogs.ManageExtendedDetailsDialog(activity) { result = it }
            var dialog = latest()
            val name = dialog.findViewById<CheckBox>(R.id.manage_extended_details_name)!!
            (name.parent as View).performClick()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(-1, result)
            assertEquals(EXT_NAME or EXT_PATH, activity.config.extendedDetails)
            org.fossify.gallery.dialogs.ManageExtendedDetailsDialog(activity) { result = it }
            dialog = latest()
            (dialog.findViewById<CheckBox>(R.id.manage_extended_details_name)!!.parent as View).performClick()
            (dialog.findViewById<CheckBox>(R.id.manage_extended_details_size)!!.parent as View).performClick()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(EXT_PATH or EXT_SIZE, result)
            assertEquals(result, activity.config.extendedDetails)
            result = -1
            org.fossify.gallery.dialogs.ManageBottomActionsDialog(activity) { result = it }
            dialog = latest()
            (dialog.findViewById<CheckBox>(R.id.manage_bottom_actions_edit)!!.parent as View).performClick()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(-1, result)
            assertEquals(BOTTOM_ACTION_EDIT or BOTTOM_ACTION_SHARE, activity.config.visibleBottomActions)
            org.fossify.gallery.dialogs.ManageBottomActionsDialog(activity) { result = it }
            dialog = latest()
            (dialog.findViewById<CheckBox>(R.id.manage_bottom_actions_edit)!!.parent as View).performClick()
            (dialog.findViewById<CheckBox>(R.id.manage_bottom_actions_delete)!!.parent as View).performClick()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(BOTTOM_ACTION_SHARE or BOTTOM_ACTION_DELETE, result)
            assertEquals(result, activity.config.visibleBottomActions)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun functionalWidthProfilesMeasureWindowsAndKeepActionsOutsideScrollAtEverySize() {
        val measurements = mutableListOf<String>("screenDp,fontScale,orientation,kind,windowPx,decorPx,decorHeightPx")
        for (screen in listOf(320, 360, 411, 600, 840)) for (scale in listOf(1f, 1.3f, 1.5f)) for (land in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (land) "w800dp-h${screen}dp-land-mdpi" else "w${screen}dp-h800dp-port-mdpi")
            val activity = host()
            val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = scale }
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            val method = ClassicPasswordMethodDialog.show(activity, R.string.photo_password_title,
                listOf("Pattern", "PIN", "Biometric"), onCancel = {}, onChoice = {})
            val security = activity.layoutInflater.inflate(R.layout.dialog_security, null)
            // Real Commons PIN page inside the actual Gallery security viewport.
            val pager = security.findViewById<org.fossify.gallery.views.ClassicSecurityViewPager>(R.id.dialog_tab_view_pager)
            val page = activity.layoutInflater.inflate(R.layout.tab_pin, pager, false)
            page.id = R.id.pin_lock_holder
            pager.adapter = object : androidx.viewpager.widget.PagerAdapter() {
                override fun getCount() = 2
                override fun isViewFromObject(view: View, item: Any) = view === item
                override fun instantiateItem(container: ViewGroup, position: Int): Any {
                    val child = if (position == 1) page else activity.layoutInflater.inflate(R.layout.tab_pattern, container, false)
                    if (child.parent == null) container.addView(child)
                    return child
                }
                override fun destroyItem(container: ViewGroup, position: Int, item: Any) { container.removeView(item as View) }
            }
            pager.currentItem = 1
            val input = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "")
                .setView(security).setNegativeButton("Cancel", null), security)
            val toolbar = ToolbarQuickActionsDialog.show(activity)
            for ((kind, dialog, maxWidth) in listOf(Triple("method", method, 380), Triple("security", input, 380), Triple("toolbar", toolbar, 400))) {
                val expected = OxygenClassicDialog.widthForContent(OxygenClassicDialog.availableWidth(activity), 1f, maxWidth)
                assertEquals(expected, dialog.window!!.attributes.width)
                val decor = dialog.window!!.decorView
                decor.measure(View.MeasureSpec.makeMeasureSpec(expected, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(if (land) screen else 800, View.MeasureSpec.AT_MOST))
                decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                assertEquals(expected, decor.width)
                val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val bounds = Rect(0, 0, cancel.width, cancel.height)
                (decor as ViewGroup).offsetDescendantRectToMyCoords(cancel, bounds)
                assertTrue("$kind cancel has a touch target", cancel.height >= 48)
                assertTrue("$kind cancel stays within window at $screen/$scale/$land", bounds.bottom <= decor.height)
                measurements.add("$screen,$scale,$land,$kind,${dialog.window!!.attributes.width},${decor.width},${decor.height}")
            }
            val grid = page.findViewById<View>(R.id.pin_5)
            assertTrue("real PIN key retains its 56dp target", grid.measuredHeight >= 56)
            listOf(toolbar, input, method).forEach { it.dismiss() }
            activity.finish()
        }
        val report = java.io.File("build/reports/ui-regression/dialog-measurements.csv")
        report.parentFile.mkdirs(); report.writeText(measurements.joinToString("\n"))
    }
}
