package org.fossify.gallery.helpers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.views.TiebaSettingsContainer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TiebaStyleMigrationTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun measure(view: View, width: Int, height: Int = 800) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun realLongMenuIsBoundedAndItsCheckedActionStillDispatches() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.config.oxygenThemeActivated = true
        val host = FrameLayout(activity)
        val anchor = View(activity)
        host.addView(anchor, FrameLayout.LayoutParams(48, 48, android.view.Gravity.END))
        activity.setContentView(host)
        measure(host, 360)
        val popup = OxygenClassicMenus.createPopup(activity, anchor)
        val longTitle = "始终显示所有已排除项目及其文件夹 / Always display excluded folders and files"
        val item = popup.menu.add(0, 42, 0, longTitle).apply { isCheckable = true; isChecked = true }
        var clicks = 0
        popup.setOnMenuItemClickListener { it.isChecked = !it.isChecked; clicks++; true }
        popup.show()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val window = Shadows.shadowOf(activity.application).latestPopupWindow!!
        assertTrue(window.width in 112..280)
        val list = descendants(window.contentView).filterIsInstance<ListView>().single()
        measure(window.contentView.rootView, window.width, 700)
        val label = descendants(list).filterIsInstance<TextView>().first { it.text.toString() == longTitle }
        assertTrue(label.lineCount > 2)
        assertTrue(label.layout.height <= label.height)
        assertTrue(list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)))
        assertEquals(1, clicks)
        assertFalse(item.isChecked)
        popup.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun dimensionFormsFitNarrowPanelsAndCanScrollWithLargeText() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.5f }
        val ctx = android.view.ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.AppTheme)
        for (layout in listOf(R.layout.dialog_resize_image, R.layout.dialog_resize_image_with_path, R.layout.dialog_custom_aspect_ratio)) {
            for (rtl in listOf(false, true)) {
                val form = LayoutInflater.from(ctx).inflate(layout, null)
                descendants(form).forEach { it.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR }
                measure(form, 278, 180)
                assertTrue(form is ScrollView)
                val fields = descendants(form).filterIsInstance<TextInputLayout>().take(2)
                fields.forEach {
                    val parent = it.parent as ViewGroup
                    assertTrue("Input escapes panel in layout $layout", it.left >= parent.paddingLeft && it.right <= parent.width - parent.paddingRight)
                    assertTrue(it.width > 40)
                }
                assertEquals(fields[0].width, fields[1].width)
                assertTrue(form.height <= 180)
            }
        }
        controller.pause().stop().destroy()
    }

    @Test fun pickerKeepsRadioGroupSelectionAndDialogButtonsKeepCallbacks() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.config.oxygenThemeActivated = true
        val group = RadioGroup(activity)
        val first = RadioButton(activity).apply { id = View.generateViewId(); text = "第一项 / First" }
        val second = RadioButton(activity).apply { id = View.generateViewId(); text = "第二项 / Second" }
        group.addView(first); group.addView(second)
        group.check(first.id)
        var selected = 0
        group.setOnCheckedChangeListener { _, _ -> selected++ }
        var cancels = 0
        val dialog = OxygenClassicDialog.show(activity, OxygenClassicDialog.builder(activity, "选择显示方式")
            .setView(group).setPositiveButton("确定", null).setNegativeButton("取消") { _, _ -> cancels++ })
        assertEquals(278, dialog.window!!.attributes.width)
        assertEquals(0.45f, dialog.window!!.attributes.dimAmount, 0.001f)
        second.performClick()
        assertFalse(first.isChecked)
        assertTrue(second.isChecked)
        assertEquals(1, selected)
        assertNull(second.buttonDrawable)
        assertNotNull(second.compoundDrawablesRelative[2])
        assertFalse(second.typeface.isBold)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val decor = dialog.window!!.decorView
        measure(decor, 278)
        decor.viewTreeObserver.dispatchOnPreDraw()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        measure(decor, 278)
        // API 28's detached AlertController scroll-indicator views cannot draw
        // to a software Canvas; interaction assertions still run on both SDKs.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val output = File("build/reports/photo-source-previews").apply { mkdirs() }
            File(output, "tieba-choice-dialog-${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, cancels)
        assertFalse(dialog.isShowing)
        controller.pause().stop().destroy()
    }

    @Test fun settingsContainerUsesHostWidthWithoutCompoundingItsFraction() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val view = TiebaSettingsContainer(controller.get())
        for ((width, expected) in listOf(360 to 360, 700 to 609, 1000 to 750, 360 to 360)) {
            repeat(2) { measure(view, width); assertEquals(expected, view.measuredWidth) }
        }
        controller.pause().stop().destroy()
    }

    @Test fun playbackSpeedUsesTheSharedDialogAndRetainsItsLiveCallback() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.config.playbackSpeed = 1f
        activity.config.playbackSpeedProgress = -1
        var changes = 0
        val fragment = org.fossify.gallery.fragments.PlaybackSpeedFragment()
        fragment.setListener(object : org.fossify.gallery.interfaces.PlaybackSpeedListener {
            override fun updatePlaybackSpeed(speed: Float) { changes++ }
        })
        fragment.showNow(activity.supportFragmentManager, "speed")
        val dialog = fragment.requireDialog() as AlertDialog
        assertEquals(
            OxygenClassicDialog.widthForContent(
                OxygenClassicDialog.availableWidth(activity), activity.resources.displayMetrics.density, 400
            ),
            dialog.window!!.attributes.width
        )
        dialog.findViewById<View>(R.id.playback_speed_fast)!!.performClick()
        assertTrue(activity.config.playbackSpeed > 1f)
        assertTrue(changes > 0)
        fragment.dismissNow()
        controller.pause().stop().destroy()
    }
}
