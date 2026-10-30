package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ClassicDialogTest {
    private lateinit var activity: Activity
    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.OxygenClassicAlertDialog_Light)
        activity.baseConfig.isSystemThemeEnabled = false
        activity.baseConfig.backgroundColor = Color.WHITE
        activity.baseConfig.textColor = Color.BLACK
        activity.baseConfig.primaryColor = Color.YELLOW
    }

    @Test fun materialBuilderInsetsMatchTheFinalClassicTouchBounds() {
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
        assertSame(builder, ClassicCommonsHooks.prepareBuilder(builder))
        val dialog = builder.setMessage("Message").create()
        dialog.show()
        val padding = android.graphics.Rect()
        dialog.window!!.decorView.background.getPadding(padding)
        assertEquals(android.graphics.Rect(0, 0, 0, 0), padding)
        dialog.dismiss()
    }

    @Test fun firstDrawWinsOverLateCommonsBackgroundAndObserverIsRemoved() {
        val dialog = AlertDialog.Builder(activity).setMessage("message").create()
        dialog.show()
        ClassicCommonsHooks.onDialogShown(activity, dialog, dialog.window!!.decorView)
        val decor = dialog.window!!.decorView
        val observer = decor.getTag(R.id.classic_dialog_observer)
        ClassicCommonsHooks.onDialogShown(activity, dialog, decor)
        assertSame(observer, decor.getTag(R.id.classic_dialog_observer))
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.MAGENTA))
        decor.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(decor.background is GradientDrawable)
        dialog.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(decor.getTag(R.id.classic_dialog_observer))
    }

    @Test fun asyncPropertiesAreStyledOnTheNextLayoutWithoutReplacingExistingRipples() {
        val content = LinearLayout(activity)
        val radio = RadioButton(activity).apply { text = "Option" }
        content.addView(radio)
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Title").setView(content))
        val firstBackground = radio.background
        val label = TextView(activity).apply { id = org.fossify.commons.R.id.property_label; text = "Path" }
        val value = TextView(activity).apply { id = org.fossify.commons.R.id.property_value; text = "/long/path" }
        content.addView(label); content.addView(value)
        val decor = dialog.window!!.decorView
        decor.viewTreeObserver.dispatchOnGlobalLayout()
        decor.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(14f * activity.resources.displayMetrics.scaledDensity, label.textSize, 0.1f)
        assertEquals(16f * activity.resources.displayMetrics.scaledDensity, value.textSize, 0.1f)
        assertSame(firstBackground, radio.background)
        dialog.dismiss()
    }

    @Test fun actionColorsRemainReadableForPaleAccentAndDisabledStatesAreDistinct() {
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Title")
            .setPositiveButton("OK", null).setNegativeButton("Cancel", null))
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(ColorUtils.calculateContrast(button.currentTextColor, Color.WHITE) >= 4.5)
        val enabled = button.currentTextColor
        button.isEnabled = false
        assertNotEquals(enabled, button.currentTextColor)
        assertTrue(button.background is RippleDrawable)
        dialog.dismiss()
    }

    @Test fun darkSurfaceKeepsTextAndActionsReadable() {
        activity.baseConfig.backgroundColor = Color.rgb(33, 33, 33)
        activity.baseConfig.textColor = Color.WHITE
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Title")
            .setMessage("Body").setPositiveButton("OK", null))
        val surface = Color.rgb(48, 48, 48)
        assertTrue(ColorUtils.calculateContrast(dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor, surface) >= 4.5)
        dialog.dismiss()
    }

    @Test fun rootPositionUsesChildCoordinatesRatherThanScrollViewPosition() {
        val scroll = FrameLayout(activity)
        val items = LinearLayout(activity)
        val root = TextView(activity)
        scroll.addView(items); items.addView(root)
        scroll.layout(37, 0, 400, 100)
        root.layout(12, 0, 200, 48)
        assertEquals(12, ClassicCommonsHooks.breadcrumbRootLeft(scroll))
        assertTrue(ClassicCommonsHooks.hasBreadcrumbItems(scroll))
        items.removeAllViews()
        assertFalse(ClassicCommonsHooks.hasBreadcrumbItems(scroll))
        assertEquals(0, ClassicCommonsHooks.breadcrumbRootLeft(scroll))
    }

    @Test fun stickyRootTracksTheViewportInBothLayoutDirections() {
        val scroll = FrameLayout(activity)
        val items = LinearLayout(activity)
        val root = TextView(activity)
        scroll.addView(items); items.addView(root)
        scroll.layout(37, 0, 337, 100)
        items.layout(0, 0, 600, 100)
        root.layout(0, 0, 100, 48)
        ClassicCommonsHooks.positionBreadcrumbRoot(scroll, 80)
        assertEquals(80f, root.translationX, 0f)
        ClassicCommonsHooks.positionBreadcrumbRoot(scroll, 0)
        assertEquals(0f, root.translationX, 0f)
        scroll.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.layout(500, 0, 600, 48)
        ClassicCommonsHooks.positionBreadcrumbRoot(scroll, 300)
        assertEquals(0f, root.translationX, 0f)
        ClassicCommonsHooks.positionBreadcrumbRoot(scroll, 200)
        assertEquals(-100f, root.translationX, 0f)
    }

    @Test fun rebuildAndScrollClearOldPressAndEachItemHasItsOwnBoundedMask() {
        val scroll = FrameLayout(activity)
        val items = LinearLayout(activity)
        val root = TextView(activity)
        val leaf = TextView(activity)
        scroll.addView(items); items.addView(root); items.addView(leaf)
        ClassicCommonsHooks.styleBreadcrumbs(scroll)
        assertNotSame(root.background, leaf.background)
        val ripple = leaf.background as RippleDrawable
        assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask))
        root.isPressed = true; leaf.isPressed = true
        ClassicCommonsHooks.clearBreadcrumbPresses(scroll)
        assertFalse(root.isPressed); assertFalse(leaf.isPressed)
        assertTrue(leaf.background is RippleDrawable)
    }

    @Test fun cancellationNeverInvokesConfirmationAndPositiveClickInvokesItOnce() {
        var confirmed = 0
        fun show() = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Confirm")
            .setPositiveButton("Yes") { _, _ -> confirmed++ }.setNegativeButton("No", null))
        show().cancel()
        assertEquals(0, confirmed)
        val dialog = show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        android.os.Looper.getMainLooper().let { org.robolectric.Shadows.shadowOf(it).idle() }
        assertEquals(1, confirmed)
    }

    @Test fun dateFormatCancellationDoesNotPersistButConfirmationDoes() {
        activity.baseConfig.dateFormat = org.fossify.commons.helpers.DATE_FORMAT_TWO
        activity.baseConfig.use24HourFormat = false
        var callbacks = 0
        fun open(): AlertDialog {
            org.fossify.commons.dialogs.ChangeDateTimeFormatDialog(activity) { callbacks++ }
            return org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
        }
        val cancelled = open()
        cancelled.findViewById<RadioButton>(org.fossify.commons.R.id.change_date_time_dialog_radio_one)!!.performClick()
        cancelled.findViewById<android.widget.CheckBox>(org.fossify.commons.R.id.change_date_time_dialog_24_hour)!!.isChecked = true
        cancelled.cancel()
        assertEquals(org.fossify.commons.helpers.DATE_FORMAT_TWO, activity.baseConfig.dateFormat)
        assertFalse(activity.baseConfig.use24HourFormat)
        assertEquals(0, callbacks)
        val confirmed = open()
        confirmed.findViewById<RadioButton>(org.fossify.commons.R.id.change_date_time_dialog_radio_one)!!.performClick()
        confirmed.findViewById<android.widget.CheckBox>(org.fossify.commons.R.id.change_date_time_dialog_24_hour)!!.isChecked = true
        confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(org.fossify.commons.helpers.DATE_FORMAT_ONE, activity.baseConfig.dateFormat)
        assertTrue(activity.baseConfig.use24HourFormat)
        assertEquals(1, callbacks)
    }

    @Test fun allNineteenColumnOptionsAndImmediateSelectionArePreserved() {
        val items = ArrayList((1..19).map { org.fossify.commons.models.RadioItem(it, it.toString()) })
        var selected: Any? = null
        var callbacks = 0
        org.fossify.commons.dialogs.RadioGroupDialog(activity, items, 19) { selected = it; callbacks++ }
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
        val group = dialog.findViewById<android.widget.RadioGroup>(org.fossify.commons.R.id.dialog_radio_group)!!
        assertEquals(19, group.childCount)
        assertTrue((group.getChildAt(18) as RadioButton).isChecked)
        group.getChildAt(0).performClick()
        assertEquals(1, selected)
        assertEquals(1, callbacks)
        assertFalse(dialog.isShowing)
    }

    @Test fun longContentHasABoundedScrollingViewport() {
        val scroll = ClassicDialogScrollView(activity)
        scroll.addView(TextView(activity).apply { text = (1..100).joinToString("\n") { "Long item $it" } })
        scroll.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue(scroll.measuredHeight > 0)
        assertTrue(scroll.measuredHeight <= activity.resources.displayMetrics.heightPixels * 0.60f)
        assertTrue(scroll.getChildAt(0).measuredHeight > scroll.measuredHeight)
    }
}
