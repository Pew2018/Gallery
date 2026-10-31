package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
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

    @Test fun biometricActionGetsClassicSkinWithoutChangingWindowGeometryOrClickBehavior() {
        var clicks = 0
        val biometric = Button(activity).apply {
            id = org.fossify.commons.R.id.open_biometric_dialog
            text = "Use biometrics"
            setOnClickListener { clicks++ }
        }
        val unrelated = Button(activity).apply { text = "Leave unchanged" }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(biometric)
            addView(unrelated)
        }
        val unrelatedBackground = unrelated.background
        val dialog = AlertDialog.Builder(activity).setView(content).create()
        OxygenClassicDialog.prepareWindow(activity, dialog)
        dialog.show()
        layoutFrame(dialog)
        val decor = dialog.window!!.decorView
        val originalWidth = decor.width
        val originalHeight = decor.height
        val originalLayoutWidth = biometric.layoutParams.width
        val originalLayoutHeight = biometric.layoutParams.height
        val originalTopPadding = biometric.paddingTop
        val originalBottomPadding = biometric.paddingBottom

        OxygenClassicDialog.apply(activity, dialog, content)
        layoutFrame(dialog)

        assertEquals(originalWidth, decor.width)
        assertEquals(originalHeight, decor.height)
        assertEquals(originalLayoutWidth, biometric.layoutParams.width)
        assertEquals(originalLayoutHeight, biometric.layoutParams.height)
        assertEquals(originalTopPadding, biometric.paddingTop)
        assertEquals(originalBottomPadding, biometric.paddingBottom)
        assertTrue(biometric.background is RippleDrawable)
        assertTrue(ColorUtils.calculateContrast(biometric.currentTextColor, Color.WHITE) >= 4.5)
        assertEquals(activity.getString(R.string.favorite_password_use_biometric), biometric.text.toString())

        val icon = biometric.compoundDrawablesRelative[0]
        assertNotNull(icon)
        assertEquals((20f * activity.resources.displayMetrics.density).toInt(), icon!!.bounds.width())
        val readableAccent = OxygenClassicPalette.readableAccent(activity.baseConfig.primaryColor, Color.WHITE)
        assertTrue(ColorUtils.calculateContrast(readableAccent, Color.WHITE) >= 4.5)

        val styledBackground = biometric.background
        OxygenClassicDialog.apply(activity, dialog, content)
        assertSame(styledBackground, biometric.background)
        assertSame(unrelatedBackground, unrelated.background)
        assertEquals("Leave unchanged", unrelated.text.toString())
        biometric.performClick()
        assertEquals(1, clicks)
        dialog.dismiss()
    }

    private fun layoutFrame(dialog: AlertDialog): Boolean {
        val decor = dialog.window!!.decorView
        val width = dialog.window!!.attributes.width.coerceAtLeast(1)
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, width, decor.measuredHeight)
        decor.viewTreeObserver.dispatchOnGlobalLayout()
        // ViewTreeObserver reports cancellation, the inverse of listener.onPreDraw().
        return !decor.viewTreeObserver.dispatchOnPreDraw()
    }

    @Test fun windowGeometryIsFinalBeforeShowAndInputModePreservesStateBits() {
        val dialog = AlertDialog.Builder(activity).setMessage("Body").setPositiveButton("OK", null).create()
        dialog.window!!.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        ClassicCommonsHooks.beforeDialogShow(activity, dialog, null)
        assertFalse(dialog.isShowing)
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE))
        val width = dialog.window!!.attributes.width
        assertEquals(OxygenClassicDialog.widthFor(OxygenClassicDialog.availableWidth(activity),
            activity.resources.displayMetrics.density), width)
        val mode = dialog.window!!.attributes.softInputMode
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
            mode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
        assertEquals(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        dialog.show()
        ClassicCommonsHooks.onDialogShown(activity, dialog, null)
        layoutFrame(dialog); layoutFrame(dialog)
        assertEquals(width, dialog.window!!.attributes.width)
        dialog.dismiss()
    }

    @Test fun lateCommonsSurfaceCancelsUnreadyDrawThenReleasesAStableFrame() {
        val dialog = AlertDialog.Builder(activity).setMessage("Body").setPositiveButton("OK", null).create()
        ClassicCommonsHooks.beforeDialogShow(activity, dialog, null)
        val width = dialog.window!!.attributes.width
        dialog.show()
        ClassicCommonsHooks.onDialogShown(activity, dialog, null)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.MAGENTA))
        assertFalse(layoutFrame(dialog))
        assertTrue(dialog.window!!.decorView.background is GradientDrawable)
        assertTrue(layoutFrame(dialog))
        val measuredWidth = dialog.window!!.decorView.width
        val measuredHeight = dialog.window!!.decorView.height
        assertTrue(layoutFrame(dialog))
        assertEquals(width, dialog.window!!.attributes.width)
        assertEquals(measuredWidth, dialog.window!!.decorView.width)
        assertEquals(measuredHeight, dialog.window!!.decorView.height)
        dialog.dismiss()
    }

    @Test fun appearanceAndRepeatedContentLayoutsNeverRewriteWindowWidth() {
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Title").setMessage("Body"))
        layoutFrame(dialog); layoutFrame(dialog)
        val sentinel = dialog.window!!.attributes.width - 5
        dialog.window!!.setLayout(sentinel, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        repeat(4) {
            OxygenClassicDialog.apply(activity, dialog, dialog.window!!.decorView)
            layoutFrame(dialog)
            assertEquals(sentinel, dialog.window!!.attributes.width)
        }
        dialog.dismiss()
    }

    @Test fun directBuilderWrapperRetainsShowAndDismissListeners() {
        var shown = 0
        var dismissed = 0
        // AlertDialog.Builder preserves its original onDismiss listener through create().
        val builder = AlertDialog.Builder(activity).setMessage("Body").setOnDismissListener { dismissed++ }
        val dialog = ClassicCommonsHooks.showClassicBuilder(builder)
        assertTrue(dialog.isShowing)
        assertTrue(dialog.window!!.attributes.width > 0)
        assertNotNull(dialog.window!!.decorView.getTag(R.id.classic_dialog_observer))
        val second = builder.create()
        second.setOnShowListener { shown++ }
        ClassicCommonsHooks.beforeDialogShow(activity, second, null)
        second.show()
        ClassicCommonsHooks.onDialogShown(activity, second, null)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, shown)
        dialog.dismiss(); second.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(2, dismissed)
    }

    @Test fun hostWidthChangeResizesOnceButHeightOnlyChangeDoesNot() {
        val dp = activity.resources.displayMetrics.density
        val host = activity.window.decorView
        host.layout(0, 0, (400 * dp).toInt(), 1000)
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Title").setMessage("Body"))
        layoutFrame(dialog); layoutFrame(dialog)
        val initial = dialog.window!!.attributes.width
        host.layout(0, 0, (400 * dp).toInt(), 700)
        assertEquals(initial, dialog.window!!.attributes.width)
        host.layout(0, 0, (300 * dp).toInt(), 700)
        assertEquals(OxygenClassicDialog.widthFor((300 * dp).toInt(), dp), dialog.window!!.attributes.width)
        assertFalse(layoutFrame(dialog))
        assertTrue(layoutFrame(dialog))
        dialog.dismiss()
    }

    @Test fun dismissBeforeFirstStableFrameRemovesHostAndDrawingObservers() {
        val dialog = AlertDialog.Builder(activity).setMessage("Body").create()
        ClassicCommonsHooks.beforeDialogShow(activity, dialog, null)
        val decor = dialog.window!!.decorView
        val initial = dialog.window!!.attributes.width
        dialog.show()
        dialog.dismiss()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(decor.getTag(R.id.classic_dialog_observer))
        activity.window.decorView.layout(0, 0, 100, 200)
        assertEquals(initial, dialog.window!!.attributes.width)
    }

    @Test fun asyncPropertyAppendAfterFirstFrameKeepsWindowGeometryAndObserverUnique() {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Properties").setView(content))
        layoutFrame(dialog); layoutFrame(dialog)
        val width = dialog.window!!.attributes.width
        val observer = dialog.window!!.decorView.getTag(R.id.classic_dialog_observer)
        val value = TextView(activity).apply { id = org.fossify.commons.R.id.property_value; text = "A long EXIF value" }
        content.addView(value)
        ClassicCommonsHooks.onDialogShown(activity, dialog, content)
        assertTrue(layoutFrame(dialog))
        assertEquals(width, dialog.window!!.attributes.width)
        assertSame(observer, dialog.window!!.decorView.getTag(R.id.classic_dialog_observer))
        assertEquals(16f * activity.resources.displayMetrics.scaledDensity, value.textSize, 0.1f)
        dialog.dismiss()
    }

    @Test fun widthPolicyRetainsClassicMarginsAndTabletCapWithoutInvalidSizes() {
        assertEquals(312, OxygenClassicDialog.widthFor(360, 1f))
        assertEquals(560, OxygenClassicDialog.widthFor(1200, 1f))
        assertEquals(624, OxygenClassicDialog.widthFor(720, 2f))
        assertEquals(1, OxygenClassicDialog.widthFor(20, 1f))
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
        leaf.isActivated = true
        ClassicCommonsHooks.styleBreadcrumbs(scroll)
        assertTrue(ColorUtils.calculateContrast(leaf.currentTextColor, Color.WHITE) >= 4.5)
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

    @Test fun sourcePickerRecycledHolderClearsSelectionLockAndThumbnail() {
        val locked = org.fossify.gallery.models.Directory().apply { path = "/private"; name = "Private" }
        val plain = org.fossify.gallery.models.Directory().apply { path = "/public"; name = "Public" }
        val draft = PhotoSourceSelectionDraft(listOf(locked.path))
        val adapter = org.fossify.gallery.adapters.PhotoSourceDirectoryAdapter(activity,
            listOf(locked, plain), draft, setOf(locked.path), {}, {})
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
        adapter.onBindViewHolder(holder, 0)
        assertTrue(holder.binding.sourceCheck.isChecked)
        assertEquals(View.VISIBLE, holder.binding.sourceLock.visibility)
        assertNull(holder.binding.sourceThumbnail.tag)
        // Simulate an old completed thumbnail before recycling onto an unselected row.
        holder.binding.sourceThumbnail.tag = "old-uri"
        holder.binding.sourceThumbnail.setImageDrawable(ColorDrawable(Color.RED))
        adapter.onBindViewHolder(holder, 1)
        assertFalse(holder.binding.sourceCheck.isChecked)
        assertEquals(View.GONE, holder.binding.sourceLock.visibility)
        assertNull(holder.binding.sourceThumbnail.tag)
        assertEquals("Public", holder.binding.sourceName.text.toString())
        assertTrue(holder.binding.sourceCheck.buttonDrawable is PhotoSourceCheckDrawable)
        assertTrue(holder.binding.sourceCheck.background is RippleDrawable)
        val glyph = holder.binding.sourceCheck.buttonDrawable
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "Sources").setView(holder.itemView))
        OxygenClassicDialog.apply(activity, dialog, holder.itemView)
        assertSame(glyph, holder.binding.sourceCheck.buttonDrawable)
        assertNull(holder.binding.sourceCheck.buttonTintList)
        dialog.dismiss()
        val density = activity.resources.displayMetrics.density
        assertEquals((48 * density + 0.5f).toInt(), holder.binding.sourceCheck.layoutParams.width)
        assertEquals((20 * density + 0.5f).toInt(), holder.binding.sourceCheck.buttonDrawable.intrinsicWidth)
        holder.binding.sourceCheck.performClick()
        assertTrue(draft.contains(plain.path))
        assertEquals(2, draft.snapshot().size)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun classicSourceCheckboxHasIndependentUncheckedAndCheckedRendering() {
        val glyph = PhotoSourceCheckDrawable(activity.resources, Color.BLUE, Color.GRAY)
        glyph.setBounds(0, 0, 20, 20)
        val unchecked = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        glyph.state = intArrayOf()
        glyph.draw(android.graphics.Canvas(unchecked))
        assertEquals(Color.TRANSPARENT, unchecked.getPixel(10, 10))
        val checked = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        glyph.state = intArrayOf(android.R.attr.state_checked)
        glyph.draw(android.graphics.Canvas(checked))
        assertEquals(Color.BLUE, checked.getPixel(10, 4))
        assertNotEquals(unchecked.getPixel(10, 4), checked.getPixel(10, 4))
    }

}
