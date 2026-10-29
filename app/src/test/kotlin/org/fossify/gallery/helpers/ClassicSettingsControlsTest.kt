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
    private fun tokens(dark: Boolean, accent: Int = 0xFF42A5F5.toInt()) =
        OxygenThemeColors.resolve(true, if (dark) "dark" else "light",
            false, accent, -1, 0xFF212121.toInt())
    private fun skin(view: View, dark: Boolean) {
        val t = tokens(dark)
        OxygenClassicControls.applySwitches(view, t)
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
            val bitmap = Bitmap.createBitmap(holder.width, holder.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(t.pageBackground)
            holder.draw(canvas)
            val output = File("build/classic-control-renders").apply { mkdirs() }
            File(output, "settings-${android.os.Build.VERSION.SDK_INT}-${if (dark) "dark" else "light"}-$scale.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    @Test fun brightAndDarkCustomSeedsRemainVisibleAndPressedAndDisabledAreDistinct() {
        val control = ClassicSettingsSwitch(context()).apply { isChecked = true }
        for (dark in listOf(false, true)) for (seed in listOf(-1, 0xFF000000.toInt(), 0xFF7DC22F.toInt())) {
            val t = tokens(dark, seed)
            OxygenClassicControls.applySwitches(control, t)
            val drawable = control.thumbDrawable
            fun pixel(vararg states: Int): Int {
                drawable.state = states
                drawable.setBounds(0, 0, 40, 48)
                val bitmap = Bitmap.createBitmap(40, 48, Bitmap.Config.ARGB_8888)
                drawable.draw(Canvas(bitmap))
                return bitmap.getPixel(20, 24)
            }
            val normal = pixel(android.R.attr.state_enabled, android.R.attr.state_checked)
            // Knob keeps the user's exact RGB; it is not adjusted as if it were text.
            assertEquals(OxygenThemeColors.opaque(seed), normal)
            assertNotEquals(normal, pixel(android.R.attr.state_enabled, android.R.attr.state_checked, android.R.attr.state_pressed))
            assertNotEquals(normal, pixel(android.R.attr.state_checked))
            assertNotEquals(pixel(android.R.attr.state_checked), pixel())
        }
    }

    @Test fun themePageSelectsInPlaceKeepsAllPresetsAndRejectsInvalidCustomColor() {
        val app = RuntimeEnvironment.getApplication()
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
        // More colors is now a bounded 23-swatch grid, not 23 RadioButtons.
        val palette = children(root).filterIsInstance<LinearLayout>().filter {
            it.isClickable && it.contentDescription != null
        }
        assertEquals(23, palette.size)
        assertEquals(0, children(root).filterIsInstance<RadioButton>().size)
        assertEquals(1, palette.count { it.isSelected })
        assertEquals(activity.getString(R.string.theme_oxygen_jade_green),
            palette.single { it.isSelected }.contentDescription.toString())
        palette.single {
            it.contentDescription.toString() == activity.getString(R.string.theme_material_red)
        }.performClick()
        assertEquals("material_red", config.oxygenThemePresetId)
        assertEquals(1, palette.count { it.isSelected })
        assertEquals(activity.getString(R.string.theme_material_red),
            palette.single { it.isSelected }.contentDescription.toString())
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

    @Test fun offTrackAndThumbShadowUseRealRasterColorsAndTransparency() {
        val ctx = context()
        val density = ctx.resources.displayMetrics.density
        val t = tokens(false)
        val control = ClassicSettingsSwitch(ctx)
        skin(control, false)
        val track = control.trackDrawable
        val width = (40f * density + 0.5f).toInt()
        val height = (24f * density + 0.5f).toInt()
        track.setBounds(0, 0, width, height)
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        track.state = intArrayOf(android.R.attr.state_enabled)
        track.draw(Canvas(image))
        val expected = OxygenThemeColors.blend(t.pageBackground, t.primaryText, 0.125)
        assertEquals("OxygenOS outlined track must not fill the inside", 0,
            android.graphics.Color.alpha(image.getPixel(width / 2, height / 2)))
        val edge = (0 until height).map { image.getPixel(width / 2, it) }
            .first { android.graphics.Color.alpha(it) >= 128 }
        assertTrue("Off rail should be near tuned #DFDFDF",
            kotlin.math.abs(android.graphics.Color.red(edge) - android.graphics.Color.red(expected)) <= 18)
        track.state = intArrayOf()
        image.eraseColor(android.graphics.Color.TRANSPARENT)
        track.draw(Canvas(image))
        val disabledEdge = (0 until height).map { image.getPixel(width / 2, it) }
            .first { android.graphics.Color.alpha(it) >= 128 }
        assertNotEquals(edge, disabledEdge)

        val thumb = control.thumbDrawable
        val thumbWidth = (24f * density + 0.5f).toInt()
        thumb.setBounds(0, 0, thumbWidth, thumbWidth)
        val shadow = Bitmap.createBitmap(thumbWidth, thumbWidth, Bitmap.Config.ARGB_8888)
        thumb.state = intArrayOf(android.R.attr.state_enabled)
        thumb.draw(Canvas(shadow))
        val haloY = (thumbWidth / 2f + 10f * density).toInt().coerceAtMost(thumbWidth - 1)
        val shadowAlpha = android.graphics.Color.alpha(shadow.getPixel(thumbWidth / 2, haloY))
        assertTrue("Thumb shadow must not be opaque: $shadowAlpha", shadowAlpha < 96)
        assertTrue("Thumb face is not opaque",
            android.graphics.Color.alpha(shadow.getPixel(thumbWidth / 2, thumbWidth / 2)) >= 250)
    }

    @Test fun switchedThemeReplacesDrawablesWithoutTouchingCheckedEnabledOrCallbacks() {
        val ctx = context()
        val row = LinearLayout(ctx)
        val control = ClassicSettingsSwitch(ctx).apply {
            text = "Automatic playback"; isChecked = true; isEnabled = false
        }
        row.addView(control)
        var callbackCount = 0
        row.setOnClickListener { callbackCount++ }
        OxygenClassicControls.applySwitches(row, tokens(false, 0xFF0071CD.toInt()))
        val oldThumb = control.thumbDrawable
        val oldTrack = control.trackDrawable
        OxygenClassicControls.applySwitches(row, tokens(false, 0xFF7DC22F.toInt()))
        assertNotSame(oldThumb, control.thumbDrawable)
        assertNotSame(oldTrack, control.trackDrawable)
        assertTrue(control.isChecked)
        assertFalse(control.isEnabled)
        assertEquals(0, callbackCount)
        val nextThumb = control.thumbDrawable
        OxygenClassicControls.applySwitches(row, tokens(false, 0xFF7DC22F.toInt()))
        assertSame(nextThumb, control.thumbDrawable)
        control.performClick()
        assertEquals(0, callbackCount)
    }

    @Test fun checkboxAndRadioShareStableGeometryAcrossStateAndTheme() {
        val ctx = context()
        val panel = LinearLayout(ctx)
        val checkbox = AppCompatCheckBox(ctx)
        val radio = AppCompatRadioButton(ctx)
        panel.addView(checkbox)
        panel.addView(radio)
        for (dark in listOf(false, true)) {
            skin(panel, dark)
            val checkDrawable = checkbox.buttonDrawable!!
            val radioDrawable = radio.buttonDrawable!!
            assertEquals((24f * ctx.resources.displayMetrics.density + 0.5f).toInt(),
                checkDrawable.intrinsicWidth)
            assertEquals(checkDrawable.intrinsicWidth, radioDrawable.intrinsicWidth)
            for (enabled in listOf(true, false)) for (checked in listOf(true, false)) {
                checkbox.isEnabled = enabled
                checkbox.isChecked = checked
                radio.isEnabled = enabled
                radio.isChecked = checked
                assertEquals(checkDrawable.intrinsicWidth, checkbox.buttonDrawable!!.intrinsicWidth)
                assertEquals(radioDrawable.intrinsicWidth, radio.buttonDrawable!!.intrinsicWidth)
                assertEquals(checked, checkbox.isChecked)
                assertEquals(checked, radio.isChecked)
            }
        }
    }


    @Test fun seekbarKeepsValuesAndCallbackWhenReskinned() {
        val ctx = context()
        val bar = SeekBar(ctx).apply { max = 137; progress = 42 }
        var updates = 0
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { updates++ }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        skin(bar, false)
        assertEquals(137, bar.max)
        assertEquals(42, bar.progress)
        val originalDrawable = bar.progressDrawable
        bar.progress = 0
        assertEquals(0, bar.progress)
        bar.progress = 137
        assertEquals(137, bar.progress)
        assertTrue(updates >= 2)
        skin(bar, false)
        assertSame(originalDrawable, bar.progressDrawable)
        assertEquals(137, bar.progress)
        skin(bar, true)
        assertNotSame(originalDrawable, bar.progressDrawable)
        assertEquals(137, bar.max)
        assertEquals(137, bar.progress)
        assertTrue(bar.thumb.intrinsicWidth <=
            (24 * ctx.resources.displayMetrics.density + 0.5f).toInt())
    }

    @Test fun classicRadioGroupKeepsSingleSelectionAndLabelClick() {
        val ctx = context()
        val group = RadioGroup(ctx)
        val first = AppCompatRadioButton(ctx).apply { id = View.generateViewId(); text = "Light" }
        val second = AppCompatRadioButton(ctx).apply { id = View.generateViewId(); text = "Dark" }
        group.addView(first)
        group.addView(second)
        skin(group, false)
        first.performClick()
        assertTrue(first.isChecked); assertFalse(second.isChecked)
        second.performClick()
        assertFalse(first.isChecked); assertTrue(second.isChecked)
        skin(group, true)
        assertFalse(first.isChecked); assertTrue(second.isChecked)
        second.isEnabled = false
        val now = SystemClock.uptimeMillis()
        // Programmatic performClick() on Android CompoundButton can bypass
        // isEnabled; test a disabled user's touch instead.
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
        val up = MotionEvent.obtain(now, now + 40L, MotionEvent.ACTION_UP, 5f, 5f, 0)
        second.onTouchEvent(down)
        second.onTouchEvent(up)
        down.recycle()
        up.recycle()
        assertTrue(second.isChecked)
    }

    @Test fun offFaceRimRailAndShadowAreSoftWithoutChangingOnGeometryOrSeed() {
        val ctx = context()
        val d = ctx.resources.displayMetrics.density
        val scale = 8
        for (dark in listOf(false, true)) for (seed in listOf(0xFF0071CD.toInt(),
            0xFF7DC22F.toInt(), 0xFF9575CD.toInt(), 0xFFFF9800.toInt(), -1, 0xFF123456.toInt())) {
            val control = ClassicSettingsSwitch(ctx)
            val t = tokens(dark, seed)
            OxygenClassicControls.applySwitches(control, t)
            val palette = OxygenThemeColors.switchPalette(t)
            val thumb = control.thumbDrawable
            assertEquals((20 * d + 0.5f).toInt(), thumb.intrinsicWidth)
            assertEquals((40 * d + 0.5f).toInt(), control.trackDrawable.intrinsicWidth)
            fun render(checked: Boolean, enabled: Boolean): Bitmap {
                thumb.state = listOfNotNull(android.R.attr.state_checked.takeIf { checked },
                    android.R.attr.state_enabled.takeIf { enabled }).toIntArray()
                thumb.jumpToCurrentState()
                val box = (24 * d + 0.5f).toInt()
                thumb.setBounds(0, 0, box, box)
                return Bitmap.createBitmap(box * scale, box * scale, Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    canvas.scale(scale.toFloat(), scale.toFloat())
                    thumb.draw(canvas)
                }
            }
            val off = render(false, true)
            val on = render(true, true)
            val disabled = render(false, false)
            val c = off.width / 2
            assertEquals(palette.offThumb, off.getPixel(c, c))
            assertEquals(OxygenThemeColors.opaque(seed), on.getPixel(c, c))
            assertNotEquals(off.getPixel(c, c), disabled.getPixel(c, c))
            // Supersample the 0.35dp hairline rather than depending on one mdpi edge pixel.
            val rimX = (c + 9.45f * d * scale).toInt()
            val rim = off.getPixel(rimX, c)
            val expected = OxygenThemeColors.blend(palette.offThumb, t.primaryText,
                if (dark) 0.10 else 0.11)
            assertTrue("Soft neutral rim: dark=$dark pixel=$rim",
                kotlin.math.abs(android.graphics.Color.red(expected) - android.graphics.Color.red(rim)) <= 5)
            val shadowY = (c + 10.4f * d * scale).toInt()
            assertEquals(16, android.graphics.Color.alpha(off.getPixel(c, shadowY)))
            assertEquals(24, android.graphics.Color.alpha(on.getPixel(c, shadowY)))
            assertEquals(8, android.graphics.Color.alpha(disabled.getPixel(c, shadowY)))
            val output = File("build/classic-control-renders").apply { mkdirs() }
            if (seed == 0xFF0071CD.toInt()) {
                File(output, "off-thumb-${android.os.Build.VERSION.SDK_INT}-$dark.png").outputStream().use {
                    off.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    @Test fun realLayoutRippleReceivesTouchStateAndReleasesWithoutDuplicateWrites() {
        val ctx = context()
        for (dark in listOf(false, true)) for (rtl in listOf(false, true)) {
            val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
            val page = LayoutInflater.from(ctx).inflate(R.layout.activity_settings, null)
            // Ancestor visibility/lifecycle dispatch requires a real attached hierarchy.
            controller.get().setContentView(page)
            val row = page.findViewById<ViewGroup>(R.id.settings_show_hidden_items_holder)
            val control = page.findViewById<ClassicSettingsSwitch>(R.id.settings_show_hidden_items)
            row.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            control.layoutDirection = row.layoutDirection
            skin(row, dark)
            measure(row, (360 * ctx.resources.displayMetrics.density).toInt())
            assertSame(row, control.parent)
            assertTrue("Lifecycle test needs an attached row", row.isAttachedToWindow)
            val ripple = row.background
            assertTrue("Runtime row must own a RippleDrawable", ripple is android.graphics.drawable.RippleDrawable)
            assertFalse(control.background is android.graphics.drawable.RippleDrawable)
            var saves = 0
            var allowed = true
            row.setOnClickListener { if (allowed) { control.toggle(); saves++ } }
            fun event(action: Int, x: Float, y: Float = control.height / 2f) {
                val now = SystemClock.uptimeMillis()
                val e = MotionEvent.obtain(now, now, action, x, y, 0)
                // Dispatch through the real parent/child hierarchy, not performClick.
                e.offsetLocation(control.left.toFloat(), control.top.toFloat())
                row.dispatchTouchEvent(e)
                e.recycle()
            }
            fun assertReleased() {
                assertFalse(row.isPressed)
                assertFalse(ripple.state.contains(android.R.attr.state_pressed))
            }
            for (x in listOf(control.width * 0.15f, control.width * 0.90f)) {
                repeat(3) {
                    val before = saves
                    event(MotionEvent.ACTION_DOWN, x)
                    assertTrue(row.isPressed)
                    assertTrue(ripple.state.contains(android.R.attr.state_pressed))
                    event(MotionEvent.ACTION_UP, x)
                    assertEquals(before + 1, saves)
                    assertReleased()
                }
            }
            control.isChecked = false
            val from = control.width * if (rtl) 0.8f else 0.2f
            val to = control.width * if (rtl) 0.2f else 0.8f
            val beforeDrag = saves
            event(MotionEvent.ACTION_DOWN, from)
            assertTrue(row.isPressed)
            event(MotionEvent.ACTION_MOVE, to)
            assertReleased()
            event(MotionEvent.ACTION_UP, to)
            assertTrue(control.isChecked)
            assertEquals(beforeDrag + 1, saves)
            val before = saves
            event(MotionEvent.ACTION_DOWN, 20f)
            event(MotionEvent.ACTION_CANCEL, 20f)
            event(MotionEvent.ACTION_UP, 20f)
            assertEquals(before, saves); assertReleased()
            event(MotionEvent.ACTION_DOWN, 20f)
            event(MotionEvent.ACTION_MOVE, 20f, control.height * 0.95f)
            event(MotionEvent.ACTION_UP, 20f, control.height * 0.95f)
            assertEquals(before, saves); assertReleased()
            event(MotionEvent.ACTION_DOWN, 20f)
            control.isEnabled = false
            assertReleased()
            event(MotionEvent.ACTION_UP, 20f)
            assertEquals(before, saves)
            control.isEnabled = true
            allowed = false
            event(MotionEvent.ACTION_DOWN, 20f)
            event(MotionEvent.ACTION_UP, 20f)
            assertEquals(before, saves); assertReleased()
            // TalkBack ACTION_CLICK routes through the same authorization gate.
            control.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null)
            assertEquals(before, saves)
            allowed = true
            control.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null)
            assertEquals(before + 1, saves)
            event(MotionEvent.ACTION_DOWN, 20f)
            control.onWindowFocusChanged(false)
            assertReleased()
            event(MotionEvent.ACTION_UP, 20f)
            assertEquals(before + 1, saves)
            event(MotionEvent.ACTION_DOWN, 20f)
            control.dispatchWindowVisibilityChanged(View.GONE)
            assertReleased()
            control.dispatchWindowVisibilityChanged(View.VISIBLE)
            val holder = row.parent as ViewGroup
            val rowIndex = holder.indexOfChild(row)
            event(MotionEvent.ACTION_DOWN, 20f)
            holder.removeView(row)
            assertReleased()
            holder.addView(row, rowIndex)
            measure(row, (360 * ctx.resources.displayMetrics.density).toInt())
            event(MotionEvent.ACTION_DOWN, 20f)
            row.visibility = View.GONE
            assertReleased()
            controller.pause().stop().destroy()
        }
    }

    // Newer Android patterned ripples require a hardware Canvas. API 28's
    // real RippleDrawable supports this native Bitmap Canvas pixel assertion;
    // the hierarchy/state test above still runs on both API 28 and API 35.
    @Config(sdk = [28])
    @Test fun pressedRuntimeRippleDrawsVisibleFeedbackOnTheWholeRow() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val page = LayoutInflater.from(context()).inflate(R.layout.activity_settings, null)
        val row = page.findViewById<ViewGroup>(R.id.settings_show_hidden_items_holder)
        val control = page.findViewById<ClassicSettingsSwitch>(R.id.settings_show_hidden_items)
        (row.parent as ViewGroup).removeView(row)
        activity.setContentView(row)
        row.setOnClickListener { control.toggle() }
        skin(row, false)
        measure(row, 360)
        fun raster(): Bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.drawColor(tokens(false).pageBackground)
            row.draw(canvas)
        }
        val before = raster()
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 60f, row.height / 2f, 0)
        row.dispatchTouchEvent(down)
        down.recycle()
        raster() // Start the real drawable before advancing its animation clock.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(100))
        val pressed = raster()
        // Compare outside the thumb/text so only the parent's rectangular ripple can explain it.
        assertTrue("Runtime ripple must paint visible row feedback", (0 until row.width).any { x ->
            before.getPixel(x, 2) != pressed.getPixel(x, 2)
        })
        val output = File("build/classic-control-renders").apply { mkdirs() }
        File(output, "row-ripple-${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
            pressed.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val cancel = MotionEvent.obtain(now, now + 120, MotionEvent.ACTION_CANCEL, 60f, row.height / 2f, 0)
        row.dispatchTouchEvent(cancel)
        cancel.recycle()
        assertFalse(row.isPressed)
        controller.pause().stop().destroy()
    }

}
