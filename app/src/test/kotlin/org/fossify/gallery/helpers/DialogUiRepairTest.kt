package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogManageBottomActionsBinding
import org.fossify.gallery.databinding.DialogManageExtendedDetailsBinding
import org.fossify.gallery.databinding.DialogFilterMediaBinding
import org.fossify.gallery.databinding.DialogFolderVisibilityBinding
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

    @Test fun filterAndFolderVisibilityRowsUseOneFullRowRippleAndToggleOnce() {
        val activity = host()
        try {
            val roots = listOf(
                DialogFilterMediaBinding.inflate(activity.layoutInflater).root,
                DialogFolderVisibilityBinding.inflate(activity.layoutInflater).root
            )
            for ((index, root) in roots.withIndex()) {
                val dialog = OxygenClassicDialog.show(activity,
                    OxygenClassicDialog.builder(activity, "Options").setView(root)
                        .setNegativeButton("Cancel", null).setPositiveButton("Apply", null), root)
                val checks = descendants(root).filterIsInstance<CheckBox>()
                assertEquals(if (index == 0) 6 else 4, checks.size)
                for (check in checks) {
                    val row = check.parent as ClassicDialogCheckboxRow
                    assertNull("child control has no local ripple", check.background)
                    assertFalse("row is the only touch target", check.isClickable)
                    assertTrue("row exposes accessibility focus", row.isFocusable)
                    row.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                    var callbackCount = 0
                    check.setOnCheckedChangeListener { _, _ -> callbackCount++ }
                    val points = listOf(12f to 28f, 120f to 28f, 318f to 28f,
                        160f to (row.height - 1f), 160f to (row.height - 1f))
                    for ((index, point) in points.withIndex()) {
                        val before = checks.map { it.isChecked }
                        val now = android.os.SystemClock.uptimeMillis()
                        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN,
                            point.first, point.second, 0)
                        row.dispatchTouchEvent(down)
                        down.recycle()
                        val upTime = android.os.SystemClock.uptimeMillis()
                        val up = android.view.MotionEvent.obtain(now, upTime, android.view.MotionEvent.ACTION_UP,
                            point.first, point.second, 0)
                        row.dispatchTouchEvent(up)
                        up.recycle()
                        shadowOf(Looper.getMainLooper()).idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                        assertEquals("one callback per tap at row location $index", index + 1, callbackCount)
                        checks.forEachIndexed { otherIndex, other ->
                            assertEquals("other options stay unchanged", if (other === check) !before[otherIndex] else before[otherIndex], other.isChecked)
                        }
                        val mark = check.buttonDrawable!!
                        assertTrue("checkbox View publishes checked drawable state",
                            check.drawableState.contains(android.R.attr.state_checked) == check.isChecked)
                        assertTrue("InsetDrawable publishes checked drawable state",
                            mark.state.contains(android.R.attr.state_checked) == check.isChecked)
                        mark.jumpToCurrentState()
                        val insetChild = (mark as? android.graphics.drawable.InsetDrawable)?.drawable
                        assertTrue("wrapped selection drawable receives checked state",
                            (insetChild?.state?.contains(android.R.attr.state_checked) ?: check.isChecked) == check.isChecked)
                        var renderableMark = insetChild ?: mark
                        while (renderableMark is android.graphics.drawable.InsetDrawable) {
                            renderableMark = renderableMark.drawable ?: break
                        }
                        renderableMark.setBounds(0, 0, 24, 24)
                        val pixels = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                        renderableMark.draw(Canvas(pixels))
                        val surface = if (androidx.core.graphics.ColorUtils.calculateLuminance(
                                activity.getProperBackgroundColor()) >= 0.5) Color.WHITE else Color.rgb(48, 48, 48)
                        val accent = OxygenClassicPalette.readableAccent(activity.getProperPrimaryColor(), surface)
                        val containsAccent = (0 until pixels.height).any { y ->
                            (0 until pixels.width).any { x -> pixels.getPixel(x, y) == accent }
                        }
                        val colorCounts = (0 until pixels.height).flatMap { y ->
                            (0 until pixels.width).map { x -> pixels.getPixel(x, y) }
                        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
                        val privateField = { name: String ->
                            runCatching {
                                renderableMark.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(renderableMark)
                            }.getOrNull()
                        }
                        val paintInfo = (privateField("paint") as? android.graphics.Paint)?.let {
                            "paint(color=${it.color.toUInt().toString(16)},alpha=${it.alpha},filter=${it.colorFilter})"
                        }
                        assertEquals("rendered mark agrees with isChecked; accent=${accent.toUInt().toString(16)} " +
                            "view=${check.drawableState.contentToString()} outer=${mark.state.contentToString()} " +
                            "inner=${insetChild?.state?.contentToString()} outerBounds=${mark.bounds} " +
                            "innerType=${insetChild?.javaClass?.name} innerAlpha=${insetChild?.alpha} " +
                            "innerVisible=${insetChild?.isVisible} viewAlpha=${check.alpha} " +
                            "kind=${privateField("kind")} progress=${privateField("selectionProgress")} $paintInfo pixels=$colorCounts",
                            check.isChecked, containsAccent)
                    }
                    val ripple = row.background as RippleDrawable
                    assertEquals(Rect(0, 0, row.width, row.height), ripple.bounds)
                }
                dialog.dismiss()
            }
        } finally {
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

    @Test fun groupingSortingAndViewTypeChoiceMarksShareMeasuredTrailingAlignment() {
        val activity = host()
        val layoutIds = listOf(
            R.layout.dialog_change_grouping,
            R.layout.dialog_change_sorting,
            R.layout.dialog_change_view_type
        )
        try {
            for (layoutId in layoutIds) {
                val content = activity.layoutInflater.inflate(layoutId, null, false)
                val dialog = OxygenClassicDialog.show(activity,
                    OxygenClassicDialog.builder(activity, "Options").setView(content)
                        .setNegativeButton("Cancel", null).setPositiveButton("Apply", null), content)
                val decor = dialog.window!!.decorView
                val width = (310 * activity.resources.displayMetrics.density).toInt()
                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.AT_MOST))
                decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)

                val radios = descendants(content).filterIsInstance<RadioButton>()
                    .filter { it.visibility == View.VISIBLE }
                val checks = descendants(content).filterIsInstance<CheckBox>()
                    .filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }
                assertTrue("layout $layoutId has single-choice rows", radios.isNotEmpty())
                assertTrue("layout $layoutId has trailing checkbox rows", checks.isNotEmpty())
                val radioEndMarks = radios.mapNotNull { it.compoundDrawablesRelative[2] }
                assertEquals("layout $layoutId radio marks were placed at end", radios.size, radioEndMarks.size)
                for (check in checks) {
                    assertNull("layout $layoutId has no second button mark", check.buttonDrawable)
                    assertNotNull("layout $layoutId checkbox mark is at end", check.compoundDrawablesRelative[2])
                    assertNull("layout $layoutId has no leading checkbox mark", check.compoundDrawablesRelative[0])
                    assertEquals("layout $layoutId uses classic 24dp end inset", 24,
                        (check.paddingEnd / activity.resources.displayMetrics.density).toInt())
                }
                val allEndMarks = radioEndMarks + checks.mapNotNull { it.compoundDrawablesRelative[2] }
                assertEquals("layout $layoutId all end marks use the same drawable size",
                    1, allEndMarks.map { it.intrinsicWidth }.distinct().size)
                assertTrue("layout $layoutId labels retain normal start alignment",
                    (radios + checks).all { it.textDirection != View.TEXT_DIRECTION_RTL })
                repeat(3) { OxygenClassicDialog.apply(activity, dialog, content) }
                for (check in checks) {
                    assertEquals("layout $layoutId style reapplication does not accumulate end padding", 24,
                        (check.paddingEnd / activity.resources.displayMetrics.density).toInt())
                }
                dialog.dismiss()
            }
        } finally {
            activity.finish()
        }
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
            val pages = listOf(activity.layoutInflater.inflate(R.layout.tab_pattern, pager, false), page)
            val adapter = object : androidx.viewpager.widget.PagerAdapter() {
                override fun getCount() = 2
                override fun isViewFromObject(view: View, item: Any) = view === item
                override fun instantiateItem(container: ViewGroup, position: Int): Any {
                    val child = pages[position]
                    if (child.parent == null) container.addView(child)
                    return child
                }
                override fun destroyItem(container: ViewGroup, position: Int, item: Any) { container.removeView(item as View) }
            }
            pager.adapter = adapter
            // A detached wrap-content pager is not populated by Robolectric until
            // after measurement. Seed its real pages as in ClassicSecurityViewPagerTest.
            adapter.instantiateItem(pager, 0)
            adapter.instantiateItem(pager, 1)
            pager.currentItem = 1
            val input = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "")
                .setView(security).setNegativeButton("Cancel", null), security)
            val toolbar = ToolbarQuickActionsDialog.show(activity)
            for ((kind, dialog, maxWidth) in listOf(Triple("method", method, 380), Triple("security", input, 380), Triple("toolbar", toolbar, 400))) {
                val expected = OxygenClassicDialog.widthForContent(OxygenClassicDialog.availableWidth(activity), 1f, maxWidth)
                assertEquals(expected, dialog.window!!.attributes.width)
                val decor = dialog.window!!.decorView
                repeat(3) {
                    decor.measure(View.MeasureSpec.makeMeasureSpec(expected, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(if (land) screen else 800, View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                }
                assertEquals(expected, decor.width)
                val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val bounds = Rect(0, 0, cancel.width, cancel.height)
                (decor as ViewGroup).offsetDescendantRectToMyCoords(cancel, bounds)
                assertTrue("$kind cancel has a touch target", cancel.height >= 48)
                assertTrue("$kind cancel stays within window at $screen/$scale/$land", bounds.bottom <= decor.height)
                measurements.add("$screen,$scale,$land,$kind,${dialog.window!!.attributes.width},${decor.width},${decor.height}")
            }
            val grid = page.findViewById<View>(R.id.pin_5)
            assertTrue("real PIN key retains its 56dp target at $screen/$scale/$land (actual=${grid.measuredHeight}, page=${page.measuredHeight}, pager=${pager.measuredHeight}, selected=${pager.currentItem})", grid.measuredHeight >= 56)
            listOf(toolbar, input, method).forEach { it.dismiss() }
            activity.finish()
        }
        val report = java.io.File("build/reports/ui-regression/dialog-measurements.csv")
        report.parentFile!!.mkdirs(); report.writeText(measurements.joinToString("\n"))
    }
}
