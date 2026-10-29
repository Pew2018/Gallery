package org.fossify.gallery.helpers

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import org.fossify.gallery.R
import org.fossify.gallery.views.ClassicSettingsSwitch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicSettingsControlsTest {
    private fun context(): Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
    private fun tokens(dark: Boolean) = OxygenThemeColors.resolve(true, if (dark) "dark" else "light",
        false, 0xFF42A5F5.toInt(), -1, 0xFF212121.toInt())
    private fun skin(view: View, dark: Boolean) {
        val t = tokens(dark)
        OxygenClassicControls.applySwitches(view, t.rawAccent, t.primaryText, t.pageBackground)
        ClassicSettingsText.apply(view, t)
    }
    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
    }
    private fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()

    @Test fun entireSettingsLayoutHasReadableTextAtNarrowWidthAndLargeFont() {
        for (dark in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            val ctx = context()
            val configuration = android.content.res.Configuration(ctx.resources.configuration).apply { fontScale = scale }
            val scaled = ContextThemeWrapper(ctx.createConfigurationContext(configuration), R.style.AppTheme)
            val page = LayoutInflater.from(scaled).inflate(R.layout.activity_settings, null)
            val holder = page.findViewById<LinearLayout>(R.id.settings_holder)
            skin(holder, dark)
            measure(holder, (320 * ctx.resources.displayMetrics.density).toInt())
            val t = tokens(dark)
            val labels = children(holder).filterIsInstance<TextView>().filter { it.text.isNotEmpty() }
            assertTrue(labels.size > 50)
            labels.filter { it.isEnabled }.forEach {
                assertEquals(1f, it.alpha)
                assertTrue("Unreadable ${it.id}",
                    OxygenThemeColors.contrastRatio(it.currentTextColor, t.pageBackground) >= 4.5)
            }
            val switches = children(holder).filterIsInstance<SwitchCompat>()
            assertTrue(switches.size > 30)
            switches.forEach {
                assertTrue(it.measuredHeight >= (48 * ctx.resources.displayMetrics.density).toInt())
                assertTrue(it.compoundPaddingRight >= it.switchMinWidth || it.compoundPaddingLeft >= it.switchMinWidth)
            }
        }
    }

    @Test fun clickDragRtlAndDisabledUseOneGatedRowCallback() {
        val ctx = context()
        val row = LinearLayout(ctx)
        val control = ClassicSettingsSwitch(ctx).apply { isClickable = true; text = "Switch label" }
        row.addView(control, LinearLayout.LayoutParams(-1, -2))
        var saves = 0
        var allowed = true
        row.setOnClickListener { if (allowed) { control.toggle(); saves++ } }
        skin(row, false)
        measure(row, 400)
        assertTrue(control.performClick()); assertTrue(control.isChecked); assertEquals(1, saves)
        row.performClick(); assertFalse(control.isChecked); assertEquals(2, saves)
        allowed = false
        control.performClick(); assertFalse(control.isChecked); assertEquals(2, saves)
        allowed = true
        fun swipe(from: Float, to: Float) {
            val time = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN to from, MotionEvent.ACTION_MOVE to to,
                MotionEvent.ACTION_UP to to).forEachIndexed { index, (action, x) ->
                val event = MotionEvent.obtain(time, time + index * 30L, action, x, 20f, 0)
                control.onTouchEvent(event); event.recycle()
            }
        }
        swipe(20f, 200f); assertTrue(control.isChecked); assertEquals(3, saves)
        swipe(20f, 200f); assertEquals(3, saves)
        control.layoutDirection = View.LAYOUT_DIRECTION_RTL
        swipe(20f, 200f); assertFalse(control.isChecked); assertEquals(4, saves)
        control.isEnabled = false
        control.performClick(); swipe(200f, 20f); assertFalse(control.isChecked); assertEquals(4, saves)
        skin(row, true); assertEquals(4, saves)
    }

    @Test fun skinIsIdempotentAndStatefulAndRendersThinTracks() {
        val ctx = context()
        val density = ctx.resources.displayMetrics.density
        val output = File("build/classic-control-renders").apply { mkdirs() }
        for (dark in listOf(false, true)) {
            val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(tokens(dark).pageBackground) }
            for (enabled in listOf(true, false)) for (checked in listOf(true, false)) {
                panel.addView(ClassicSettingsSwitch(ctx).apply {
                    text = "enabled=$enabled checked=$checked"; isEnabled = enabled; isChecked = checked
                }, LinearLayout.LayoutParams(-1, -2))
                panel.addView(AppCompatRadioButton(ctx).apply { text = "Radio"; isChecked = checked; isEnabled = enabled })
                panel.addView(AppCompatCheckBox(ctx).apply { text = "Check"; isChecked = checked; isEnabled = enabled })
            }
            panel.addView(SeekBar(ctx).apply { progress = 60 })
            skin(panel, dark)
            val first = panel.getChildAt(0) as SwitchCompat
            val thumb = first.thumbDrawable
            skin(panel, dark)
            assertSame(thumb, first.thumbDrawable)
            assertEquals((20 * density + 0.5f).toInt(), thumb.intrinsicWidth)
            assertEquals((40 * density + 0.5f).toInt(), first.trackDrawable.intrinsicWidth)
            // Raster test uses deliberately tall bounds to expose the old track-stretch bug.
            val track = first.trackDrawable
            track.setBounds(0, 0, 40, 100)
            val raster = Bitmap.createBitmap(40, 100, Bitmap.Config.ARGB_8888)
            track.draw(Canvas(raster))
            val occupied = (0 until 100).count { (raster.getPixel(20, it) ushr 24) != 0 }
            assertTrue("Track stretched to $occupied px", occupied <= 12 * density)
            measure(panel, (360 * density).toInt())
            val bitmap = Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888)
            panel.draw(Canvas(bitmap))
            File(output, "controls-${android.os.Build.VERSION.SDK_INT}-${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun themePageSelectsInPlaceKeepsAllPresetsAndRejectsInvalidCustomColor() {
        val app = RuntimeEnvironment.getApplication<Application>()
        val config = org.fossify.gallery.helpers.Config.newInstance(app)
        config.oxygenAppearanceMode = "light"
        config.oxygenThemeActivated = true
        val controller = org.robolectric.Robolectric.buildActivity(
            org.fossify.gallery.activities.OxygenThemeActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        fun label(id: Int) = children(root).filterIsInstance<TextView>().first { it.text.toString() == activity.getString(id) }
        val original = label(R.string.classic_more_colors)
        val color = children(root).first { it.contentDescription?.toString() == activity.getString(R.string.theme_oxygen_jade_green) }
        color.performClick()
        assertEquals("oxygen_jade_green", config.oxygenThemePresetId)
        assertSame(original, label(R.string.classic_more_colors))
        assertEquals(3, children(root).filterIsInstance<RadioButton>().size)
        label(R.string.classic_more_colors).performClick()
        assertEquals(23, children(root).filterIsInstance<RadioButton>().size)
        label(R.string.theme_material_red).performClick()
        assertEquals("material_red", config.oxygenThemePresetId)
        assertEquals(1, children(root).filterIsInstance<RadioButton>().count { it.isChecked })
        activity.onBackPressedDispatcher.onBackPressed()
        label(R.string.classic_custom_color).performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val field = children(dialog.window!!.decorView).filterIsInstance<EditText>().single()
        field.setText("#INVALID")
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertNotNull(field.error)
        assertEquals("material_red", config.oxygenThemePresetId)
        field.setText("#123456")
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertFalse(dialog.isShowing)
        assertEquals("custom", config.oxygenThemePresetId)
        assertEquals(0xFF123456.toInt(), config.oxygenCustomAccent)
        label(R.string.classic_restore_default).performClick()
        assertEquals(OxygenThemePresets.DEFAULT_ID, config.oxygenThemePresetId)
        val page = root.getChildAt(0)
        measure(page, (360 * activity.resources.displayMetrics.density).toInt())
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.draw(Canvas(bitmap))
        val output = File("build/classic-control-renders").apply { mkdirs() }
        File(output, "theme-${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        controller.pause().stop().destroy()
    }

    @Test fun presetAndCustomConfigurationSurviveRecreationAndLegacyRoundTrip() {
        val ctx = context()
        val config = org.fossify.gallery.helpers.Config.newInstance(ctx)
        config.oxygenThemeActivated = false
        config.primaryColor = 0xFF123456.toInt()
        config.textColor = 0xFF212121.toInt()
        config.backgroundColor = 0xFFFAFAFA.toInt()
        config.oxygenThemePresetId = "future_unknown_preset"
        config.oxygenCustomAccent = 0xFF654321.toInt()
        config.oxygenThemeActivated = true
        OxygenThemeEngine.apply(ctx, config)
        val restored = org.fossify.gallery.helpers.Config.newInstance(ctx)
        assertEquals("future_unknown_preset", restored.oxygenThemePresetId)
        assertEquals(0xFF654321.toInt(), OxygenThemeEngine.accent(restored))
        restored.oxygenThemeActivated = false
        assertEquals(0xFF123456.toInt(), restored.primaryColor)
        assertEquals(7, OxygenThemePresets.featured.size)
        assertEquals(23, OxygenThemePresets.all.size)
        for (preset in OxygenThemePresets.all) {
            restored.oxygenThemePresetId = preset.id
            assertEquals(preset.color, OxygenThemeEngine.accent(org.fossify.gallery.helpers.Config.newInstance(ctx)))
        }
    }
}
